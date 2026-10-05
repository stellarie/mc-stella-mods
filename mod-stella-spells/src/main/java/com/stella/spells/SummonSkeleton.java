package com.stella.spells;

import net.fabricmc.loader.api.FabricLoader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Summon Skeleton, a spellbook of its own that no scroll copies and no weapon takes. It raises one skeleton
 * that serves the caster: it never despawns, fights the hostiles within eight blocks of its master, follows
 * them, and gathers the loot it walks over into its own pack. The master's use on it opens its gear, its
 * pack and a name slot. Casting again while it lives calls it to the caster's side, and costs no cooldown.
 */
public final class SummonSkeleton {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_skeleton");
	public static final String SUMMON_TAG = "mod-stella-spells.summoned";
	public static final String OWNER_TAG_PREFIX = "mod-stella-spells.summon_owner.";
	public static final int COOLDOWN_TICKS = 6000;
	public static final double GUARD_RADIUS = 8.0;
	public static final double FOLLOW_DISTANCE = 6.0;
	public static final double TELEPORT_DISTANCE = 24.0;
	public static final double LOOT_REACH = 1.5;
	public static final int PACK_SIZE = 18;
	public static final double BASE_HEALTH = 30.0;
	/** The share of its master's maximum health a servant adds to its own, when the RPG mod scales spells. */
	public static final double MASTER_HEALTH_SHARE = 0.1;
	/** A servant's hit is this percent of its master's MATK. */
	public static final double HIT_SKILL_PERCENT = 100.0;
	public static final String HEALTH_SCALE_SHARE = RpgBridge.HEALTH_SCALE_SHARE;
	public static final Identifier THREAT_HEALTH_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_threat_health");
	private static final String NO_SPAWN_ROLL_TAG = "mod-stella-mobs.no_spawn_roll";
	public static final AttachmentType<List<ItemStack>> PACK = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_pack"), ItemStack.OPTIONAL_CODEC.listOf()
	);
	/**
	 * The one servant a master holds, saved with the master. A servant left in an unloaded chunk cannot be called
	 * back, so a new cast raises another and moves the bond; the old one is retired when it loads again.
	 */
	public static final AttachmentType<UUID> BOND = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_bond"),
		builder -> builder.persistent(UUIDUtil.CODEC).copyOnDeath()
	);
	/** Each master's skeleton, by the skeleton's id; the skeleton's own tag is the truth, this only finds it. */
	private static final Map<UUID, UUID> SUMMONED = new ConcurrentHashMap<>();
	/** Servants whose master bonded another, retired after the tick rather than inside a load or a goal. */
	private static final Set<Skeleton> RETIRING = ConcurrentHashMap.newKeySet();

	private SummonSkeleton() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SUMMONED.clear();
			RETIRING.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Skeleton skeleton && skeleton.entityTags().contains(SUMMON_TAG)) {
				if (stale(skeleton)) {
					RETIRING.add(skeleton);
					return;
				}
				ownerOf(skeleton).ifPresent(owner -> SUMMONED.put(owner, skeleton.getUUID()));
				if (!SummonInventoryStore.restore(skeleton)) {
					SummonInventoryStore.save(skeleton);
				}
				for (EquipmentSlot slot : SkeletonMenu.GEAR) {
					skeleton.setDropChance(slot, 0.0F);
				}
				refreshHealth(skeleton, false);
				install(skeleton);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (UUID ownerId : List.copyOf(SUMMONED.keySet())) {
					Skeleton skeleton = find(server, ownerId);
					ServerPlayer master = server.getPlayerList().getPlayer(ownerId);
					if (skeleton == null || master == null) {
						continue;
					}
					if (skeleton.level() != master.level()) {
						recall(skeleton, master);
					} else {
						refreshHealth(skeleton, false);
					}
				}
			}
			if (!RETIRING.isEmpty()) {
				for (Skeleton skeleton : List.copyOf(RETIRING)) {
					RETIRING.remove(skeleton);
					retire(skeleton);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Skeleton skeleton && skeleton.entityTags().contains(SUMMON_TAG) && skeleton.level() instanceof ServerLevel level) {
				SummonInventoryStore.save(skeleton);
				skeleton.setAttached(PACK, List.of());
				ownerOf(skeleton).ifPresent(owner -> SUMMONED.remove(owner, skeleton.getUUID()));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Skeleton skeleton && skeleton.entityTags().contains(SUMMON_TAG) && !stale(skeleton)) {
				SummonInventoryStore.save(skeleton);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer master) || player.isShiftKeyDown()
				|| !(entity instanceof Skeleton skeleton) || !isServant(skeleton, master)) {
				return InteractionResult.PASS;
			}
			if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
				master.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new SkeletonMenu(id, inventory, skeleton), skeleton.getDisplayName()));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The spell: a new servant, or the old one called to the caster's side. */
	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		Skeleton existing = find(caster.level().getServer(), caster.getUUID());
		if (existing != null) {
			SpellCasting.skipCooldown();
			return recall(existing, caster);
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(caster.level(), EntitySpawnReason.MOB_SUMMONED);
		if (skeleton == null) {
			return false;
		}
		skeleton.addTag(SUMMON_TAG);
		skeleton.addTag(OWNER_TAG_PREFIX + caster.getUUID());
		skeleton.addTag(MobSpells.NO_BEHAVIOR_TAG);
		skeleton.addTag(NO_SPAWN_ROLL_TAG);
		skeleton.setPersistenceRequired();
		skeleton.setCanPickUpLoot(false);
		skeleton.setCustomName(Component.literal(caster.getScoreboardName() + "'s Skeleton"));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		for (EquipmentSlot slot : SkeletonMenu.GEAR) {
			skeleton.setDropChance(slot, 0.0F);
		}
		refreshHealth(skeleton, true);
		Vec3 at = beside(caster);
		skeleton.snapTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
		// The bond moves first: the load event fires inside the add, and must see this servant as the one held.
		UUID previous = caster.getAttached(BOND);
		caster.setAttached(BOND, skeleton.getUUID());
		if (!caster.level().addFreshEntity(skeleton)) {
			caster.setAttached(BOND, previous);
			return false;
		}
		SUMMONED.put(caster.getUUID(), skeleton.getUUID());
		CastHits.touch(caster);
		caster.level().sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 30, 0.4, 0.8, 0.4, 0.05);
		caster.level().playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, caster.getSoundSource(), 0.8F, 0.8F);
		return true;
	}

	/** Saves the servant's equipped gear for its next life. */
	public static void equip(final Skeleton skeleton, final EquipmentSlot slot, final ItemStack stack) {
		skeleton.setItemSlot(slot, stack);
		skeleton.setDropChance(slot, 0.0F);
		SummonInventoryStore.save(skeleton);
	}

	private static void refreshHealth(final Skeleton skeleton, final boolean full) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		AttributeInstance health = skeleton.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		int peak = FabricLoader.getInstance().isModLoaded("mod-stella-mobs")
			? SummonThreatBridge.peak(level.getServer()) : 0;
		double bonus = FabricLoader.getInstance().isModLoaded("mod-stella-mobs")
			? SummonThreatBridge.healthBonus(peak) : 0.0;
		double base = baseHealth(skeleton, health, full);
		AttributeModifier previous = health.getModifier(THREAT_HEALTH_ID);
		if (!full && health.getBaseValue() == base && previous != null && previous.amount() == bonus) {
			return;
		}
		float oldMaximum = skeleton.getMaxHealth();
		float oldHealth = skeleton.getHealth();
		health.setBaseValue(base);
		if (previous != null) {
			health.removeModifier(THREAT_HEALTH_ID);
		}
		health.addPermanentModifier(new AttributeModifier(THREAT_HEALTH_ID, bonus,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		float fraction = oldMaximum <= 0.0F ? 1.0F : oldHealth / oldMaximum;
		skeleton.setHealth(full ? skeleton.getMaxHealth()
			: Math.max(1.0F, Math.min(skeleton.getMaxHealth(), fraction * skeleton.getMaxHealth())));
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_HEALTH servant={} peak={} max={}",
			skeleton.getUUID(), peak, skeleton.getMaxHealth());
	}

	// 30 plus 10% of the master's health.
	// The RPG mod multiplies health by K, so divide.
	private static double baseHealth(final Skeleton skeleton, final AttributeInstance health, final boolean full) {
		if (!RpgBridge.present()) {
			return BASE_HEALTH;
		}
		ServerPlayer master = owner(skeleton);
		if (master == null) {
			return full ? BASE_HEALTH : Math.max(BASE_HEALTH, health.getBaseValue());
		}
		return BASE_HEALTH + MASTER_HEALTH_SHARE * master.getMaxHealth() / healthScale();
	}

	static double healthScale() {
		return RpgBridge.k();
	}

	/** What a blow from this servant is worth: its master's MATK at {@link #HIT_SKILL_PERCENT}; NaN when it is not a servant's blow. */
	public static double hit(final DamageSource source) {
		if (!(source.getEntity() instanceof Skeleton skeleton) || !skeleton.entityTags().contains(SUMMON_TAG)) {
			return Double.NaN;
		}
		boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
		if (!arrow && !source.is(DamageTypes.MOB_ATTACK)) {
			return Double.NaN;
		}
		ServerPlayer master = owner(skeleton);
		return master == null ? Double.NaN : RpgBridge.spellDamage(master, HIT_SKILL_PERCENT);
	}

	private static boolean recall(final Skeleton skeleton, final ServerPlayer caster) {
		SummonInventoryStore.save(skeleton);
		Vec3 at = beside(caster);
		Entity moved = skeleton.level() == caster.level()
			? skeleton
			: skeleton.teleport(new TeleportTransition(caster.level(), at, Vec3.ZERO, caster.getYRot(), 0.0F, TeleportTransition.DO_NOTHING));
		if (moved == null) {
			return false;
		}
		moved.teleportTo(at.x, at.y, at.z);
		if (moved instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			SUMMONED.put(caster.getUUID(), mob.getUUID());
			caster.setAttached(BOND, mob.getUUID());
			if (mob instanceof Skeleton servant) {
				SummonInventoryStore.restore(servant);
				refreshHealth(servant, false);
			}
		}
		caster.level().sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 20, 0.4, 0.8, 0.4, 0.05);
		return true;
	}

	private static Vec3 beside(final ServerPlayer caster) {
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
		Vec3 at = caster.position().add(flat.scale(1.5));
		return CastingSpots.standing(caster.level(), at) ? at : caster.position();
	}

	/** The living servant of this master, wherever it is loaded; null for none. */
	public static @Nullable Skeleton find(final MinecraftServer server, final UUID owner) {
		UUID id = SUMMONED.get(owner);
		if (id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Skeleton skeleton && skeleton.isAlive() && skeleton.entityTags().contains(SUMMON_TAG)) {
				return skeleton;
			}
		}
		return null;
	}

	/** A servant whose master, when online, is bonded to another; a master with no bond yet adopts this one. */
	static boolean stale(final Skeleton skeleton) {
		ServerPlayer master = owner(skeleton);
		if (master == null) {
			return false;
		}
		UUID bond = master.getAttached(BOND);
		if (bond == null) {
			master.setAttached(BOND, skeleton.getUUID());
			return false;
		}
		return !bond.equals(skeleton.getUUID());
	}

	/** Retires an older servant without duplicating the owner's saved inventory. */
	private static void retire(final Skeleton skeleton) {
		if (skeleton.isRemoved() || !(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		skeleton.setAttached(PACK, List.of());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			skeleton.setItemSlot(slot, ItemStack.EMPTY);
		}
		ownerOf(skeleton).ifPresent(owner -> SUMMONED.remove(owner, skeleton.getUUID()));
		level.sendParticles(ParticleTypes.SOUL, skeleton.getX(), skeleton.getY() + 1.0, skeleton.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_RETIRED servant={} owner={}", skeleton.getUUID(), ownerOf(skeleton).map(UUID::toString).orElse("none"));
		skeleton.discard();
	}

	public static Optional<UUID> ownerOf(final Mob mob) {
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(OWNER_TAG_PREFIX)) {
				try {
					return Optional.of(UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length())));
				} catch (IllegalArgumentException malformed) {
					return Optional.empty();
				}
			}
		}
		return Optional.empty();
	}

	public static boolean isServant(final Mob mob, final Player player) {
		return mob.entityTags().contains(SUMMON_TAG) && ownerOf(mob).filter(player.getUUID()::equals).isPresent();
	}

	static @Nullable ServerPlayer owner(final Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		return ownerOf(mob).map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
	}

	/** The servant's pack, always {@link #PACK_SIZE} slots. */
	public static List<ItemStack> pack(final Mob skeleton) {
		List<ItemStack> saved = skeleton.getAttached(PACK);
		List<ItemStack> pack = new ArrayList<>(saved == null ? List.of() : saved);
		while (pack.size() < PACK_SIZE) {
			pack.add(ItemStack.EMPTY);
		}
		return pack.subList(0, PACK_SIZE);
	}

	public static void setPack(final Mob skeleton, final List<ItemStack> pack) {
		skeleton.setAttached(PACK, List.copyOf(pack));
		if (skeleton instanceof Skeleton servant) {
			SummonInventoryStore.save(servant);
		}
	}

	/** Adds as much of the stack to the pack as fits; the rest is left in the stack. */
	public static void stow(final Mob skeleton, final ItemStack stack) {
		List<ItemStack> pack = new ArrayList<>(pack(skeleton));
		for (int i = 0; i < pack.size() && !stack.isEmpty(); i++) {
			ItemStack slot = pack.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
				slot.grow(moved);
				stack.shrink(moved);
			}
		}
		for (int i = 0; i < pack.size() && !stack.isEmpty(); i++) {
			if (pack.get(i).isEmpty()) {
				pack.set(i, stack.copy());
				stack.setCount(0);
			}
		}
		setPack(skeleton, pack);
	}

	// The servant's mind: only its master's foes, its master's side, and the loot underfoot.
	private static void install(final Skeleton skeleton) {
		var targets = ((com.stella.spells.mixin.MobGoalsAccessor) skeleton).stellaSpells$targetSelector();
		targets.removeAllGoals(goal -> true);
		targets.addGoal(1, new AnswerBlowGoal(skeleton));
		targets.addGoal(2, new GuardGoal(skeleton));
		skeleton.getGoalSelector().addGoal(2, new FollowGoal(skeleton));
		skeleton.getGoalSelector().addGoal(1, new LootGoal(skeleton));
		skeleton.getGoalSelector().addGoal(1, new PackCasterGoal(skeleton));
	}

	/** Casts combat and support books from the servant's pack without consuming them. */
	static final class PackCasterGoal extends Goal {
		private final Skeleton skeleton;
		private final Map<String, Integer> readyAt = new java.util.HashMap<>();
		private int ticks;

		PackCasterGoal(final Skeleton skeleton) {
			this.skeleton = skeleton;
		}

		@Override
		public boolean canUse() {
			return this.skeleton.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (++this.ticks % MobSpells.THINK_INTERVAL_TICKS != 0) {
				return;
			}
			LivingEntity target = this.skeleton.getTarget();
			for (ItemStack stack : pack(this.skeleton)) {
				if (!(stack.getItem() instanceof SpellbookItem book)) {
					continue;
				}
				String spell = book.definition().id().getPath();
				if (this.ticks < this.readyAt.getOrDefault(spell, 0)) {
					continue;
				}
				boolean cast = target != null && target.isAlive()
					&& this.skeleton.distanceToSqr(target) <= MobSpells.CAST_RANGE * MobSpells.CAST_RANGE
					&& this.skeleton.getSensing().hasLineOfSight(target)
					&& MobSpells.castAt(this.skeleton, spell, target);
				if (!cast && MobSpells.PACK_SUPPORT.contains(spell)
					&& (target != null || MobSpells.sideIsHurt(this.skeleton, MoreSpells.SUPPORT_RADIUS)
						|| "purify".equals(spell))) {
					cast = MobSpells.support(this.skeleton, spell);
				}
				if (cast) {
					this.readyAt.put(spell, this.ticks + MobSpells.cooldownOf(spell));
					Spellbooks.LOGGER.info("[SPELL] event=SUMMON_CAST servant={} spell={} target={}",
						this.skeleton.getUUID(), spell, target == null ? "support" : target.getUUID());
					break;
				}
			}
		}
	}

	/** Answers whoever strikes the servant, except its own master: a sweep or a stray spell is forgiven. */
	static final class AnswerBlowGoal extends HurtByTargetGoal {
		private final Skeleton skeleton;

		AnswerBlowGoal(final Skeleton skeleton) {
			super(skeleton);
			this.skeleton = skeleton;
		}

		@Override
		public boolean canUse() {
			return !(this.skeleton.getLastHurtByMob() instanceof Player striker && isServant(this.skeleton, striker)) && super.canUse();
		}
	}

	/** The nearest hostile within the guard radius of the master. */
	static final class GuardGoal extends TargetGoal {
		private final Skeleton skeleton;
		private @Nullable LivingEntity found;

		GuardGoal(final Skeleton skeleton) {
			super(skeleton, false);
			this.skeleton = skeleton;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			ServerPlayer master = owner(this.skeleton);
			if (master == null || master.level() != this.skeleton.level()) {
				return false;
			}
			this.found = null;
			double best = Double.MAX_VALUE;
			for (Mob mob : master.level().getEntitiesOfClass(Mob.class, master.getBoundingBox().inflate(GUARD_RADIUS),
				other -> other.isAlive() && Friends.isFoe(other) && other.distanceToSqr(master) <= GUARD_RADIUS * GUARD_RADIUS)) {
				double distance = mob.distanceToSqr(this.skeleton);
				if (distance < best) {
					best = distance;
					this.found = mob;
				}
			}
			return this.found != null;
		}

		@Override
		public void start() {
			this.skeleton.setTarget(this.found);
			super.start();
		}
	}

	/** Keeps near the master, and catches up by teleport when left far behind. */
	static final class FollowGoal extends Goal {
		private final Skeleton skeleton;

		FollowGoal(final Skeleton skeleton) {
			this.skeleton = skeleton;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			// A master who was away when this servant loaded may since have bonded another.
			if (stale(this.skeleton)) {
				RETIRING.add(this.skeleton);
				return false;
			}
			ServerPlayer master = owner(this.skeleton);
			return master != null && master.level() == this.skeleton.level() && this.skeleton.getTarget() == null
				&& this.skeleton.distanceToSqr(master) > FOLLOW_DISTANCE * FOLLOW_DISTANCE;
		}

		@Override
		public void tick() {
			ServerPlayer master = owner(this.skeleton);
			if (master == null) {
				return;
			}
			if (this.skeleton.distanceToSqr(master) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
				Vec3 at = beside(master);
				this.skeleton.teleportTo(at.x, at.y, at.z);
				this.skeleton.getNavigation().stop();
			} else if (this.skeleton.tickCount % 10 == 0) {
				this.skeleton.getNavigation().moveTo(master, 1.1);
			}
		}
	}

	/** Gathers the items it stands over into its pack. Flagless. */
	static final class LootGoal extends Goal {
		private final Skeleton skeleton;

		LootGoal(final Skeleton skeleton) {
			this.skeleton = skeleton;
		}

		private int checks;

		// Counted on its own: the selector does not ask on every tick, so a tick-count beat could never land.
		@Override
		public boolean canUse() {
			return this.skeleton.isAlive() && ++this.checks % 5 == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			for (ItemEntity item : this.skeleton.level().getEntitiesOfClass(ItemEntity.class, this.skeleton.getBoundingBox().inflate(LOOT_REACH),
				entity -> entity.isAlive() && !entity.hasPickUpDelay())) {
				ItemStack stack = item.getItem().copy();
				int before = stack.getCount();
				stow(this.skeleton, stack);
				if (stack.getCount() < before) {
					this.skeleton.take(item, before - stack.getCount());
					if (stack.isEmpty()) {
						item.discard();
					} else {
						item.setItem(stack);
					}
				}
			}
		}
	}
}
