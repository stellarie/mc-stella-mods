package com.stella.spells;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
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
	/** The same marks a wild friendly patrol wears, so a village guard, its duke and a golem spare the servant. */
	public static final String[] FRIEND_TAGS = {"mc-stella-villages.patrol", "mod-stella-mobs.friendly_patrol"};
	/** Thirty seconds of rest between summons. */
	public static final int COOLDOWN_TICKS = 600;
	/** How far around its master the servant seeks a foe; the screen shows this same reach. */
	public static final double GUARD_RADIUS = 24.0;
	/**
	 * The reach around its master the servant clears before it returns to following. A foe inside it is a
	 * priority the servant hunts; only with none left does it follow again.
	 */
	public static final double CLEAR_RADIUS = 16.0;
	/** The distance at which the servant looses an arrow; vanilla's bow gives up at fifteen. */
	public static final double RANGED_ATTACK_RANGE = GUARD_RADIUS;
	public static final double FOLLOW_DISTANCE = 6.0;
	/** The factor the follow goal walks at. */
	static final double FOLLOW_SPEED = 1.1;
	/** A player's forward input. */
	static final double PLAYER_INPUT = 0.98;
	/** The vanilla sprint boost on a living body's move speed. */
	static final double SPRINT_BOOST = 1.3;
	public static final double TELEPORT_DISTANCE = 24.0;
	public static final double LOOT_REACH = 1.5;
	public static final int PACK_SIZE = 18;
	public static final double BASE_HEALTH = 30.0;
	/** The share of its own maximum health a living servant recovers when its master casts the spell again. */
	public static final double RECALL_HEAL_FRACTION = 0.25;
	/** The whole fraction of a killed foe's experience the servant takes, at its own slower curve. */
	public static final double KILL_XP_SHARE = 1.0;
	/** The share of the servant's own gain its master collects. */
	public static final double XP_SHARE = 0.25;
	/** An awakened servant's own mount. It is the servant's kit, so its armor and pack save with the servant's. */
	public static final double HORSE_MAX_HEALTH = 24000.0;
	public static final int HORSE_PACK_SIZE = 27;
	public static final String HORSE_TAG = "mod-stella-spells.summon_horse";
	public static final int STAT_COUNT = 3;
	public static final double STAT_HEALTH = 2.0;
	public static final double STAT_ARMOR = 0.5;
	public static final double STAT_KNOCKBACK = 0.1;
	/** The share of its master's maximum health a servant adds to its own, when the RPG mod scales spells. */
	public static final double MASTER_HEALTH_SHARE = 0.1;
	/** A servant's hit is this percent of its master's MATK. */
	public static final double HIT_SKILL_PERCENT = 100.0;
	public static final String HEALTH_SCALE_SHARE = RpgBridge.HEALTH_SCALE_SHARE;
	public static final Identifier THREAT_HEALTH_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_threat_health");
	public static final Identifier STAT_HEALTH_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_stat_health");
	public static final Identifier STAT_ARMOR_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_stat_armor");
	public static final Identifier STAT_KNOCKBACK_ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_stat_knockback");
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
	/**
	 * A servant's own growth: its level, the experience toward the next one, and the stat points it has rolled.
	 * It rides on the servant, and {@link SummonInventoryStore} saves the same numbers with the owner's kit.
	 */
	public static final AttachmentType<Growth> GROWTH = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_growth"),
		builder -> builder.persistent(Growth.CODEC)
	);
	public static final Growth FRESH = new Growth(0, 0, List.of());
	/**
	 * The servant's live level, under {@code mod-stella-spells:servant-level} as a
	 * {@code Function<Entity, Integer>} that answers its true level or -1 for any other entity. A reader
	 * needs no class from this mod, and reads the level as it stands now, not as it was at the spawn.
	 */
	public static final String LEVEL_SHARE_KEY = "mod-stella-spells:servant-level";
	/** Each master's skeleton, by the skeleton's id; the skeleton's own tag is the truth, this only finds it. */
	private static final Map<UUID, UUID> SUMMONED = new ConcurrentHashMap<>();
	/** The target mode each master last chose: search the ground, or mirror the master's own target. */
	private static final Map<UUID, Mode> MODES = new ConcurrentHashMap<>();
	/** Set while a servant pays its master, so the payment is not read as a fresh gain. */
	private static final ThreadLocal<Boolean> SHARING = new ThreadLocal<>();
	/** Servants whose master bonded another, retired after the tick rather than inside a load or a goal. */
	private static final Set<Skeleton> RETIRING = ConcurrentHashMap.newKeySet();

	private SummonSkeleton() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SUMMONED.clear();
			MODES.clear();
			RETIRING.clear();
			SHARING.remove();
		});
		FabricLoader.getInstance().getObjectShare().put(LEVEL_SHARE_KEY, (java.util.function.Function<net.minecraft.world.entity.Entity, Integer>) SummonSkeleton::sharedLevel);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Skeleton skeleton && skeleton.entityTags().contains(SUMMON_TAG)) {
				if (stale(skeleton)) {
					RETIRING.add(skeleton);
					return;
				}
				ownerOf(skeleton).ifPresent(owner -> SUMMONED.put(owner, skeleton.getUUID()));
				if (!SummonInventoryStore.restore(skeleton)) {
					servantGrow(skeleton);
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
			for (UUID ownerId : SUMMONED.keySet()) {
				Skeleton skeleton = find(server, ownerId);
				ServerPlayer master = server.getPlayerList().getPlayer(ownerId);
				if (skeleton != null && master != null) {
					pace(skeleton, master);
				}
			}
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
				dismissHorse(skeleton);
				skeleton.setAttached(PACK, List.of());
				ownerOf(skeleton).ifPresent(owner -> SUMMONED.remove(owner, skeleton.getUUID()));
			} else if (entity instanceof Mob fallen && entity.level() instanceof ServerLevel) {
				shareKill(fallen);
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

	/** The spell: a new servant, or the old one called to the caster's side and healed on the way. */
	static boolean apply(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		Skeleton existing = find(caster.level().getServer(), caster.getUUID());
		if (existing != null) {
			boolean recalled = recall(existing, caster);
			if (recalled) {
				healOnRecall(existing);
				if (modifiers.awakened()) {
					mount(existing);
				}
			}
			return recalled;
		}
		Skeleton skeleton = EntityTypes.SKELETON.create(caster.level(), EntitySpawnReason.MOB_SUMMONED);
		if (skeleton == null) {
			return false;
		}
		skeleton.addTag(SUMMON_TAG);
		skeleton.addTag(OWNER_TAG_PREFIX + caster.getUUID());
		for (String friend : FRIEND_TAGS) {
			skeleton.addTag(friend);
		}
		skeleton.addTag(MobSpells.NO_BEHAVIOR_TAG);
		skeleton.addTag(NO_SPAWN_ROLL_TAG);
		skeleton.setPersistenceRequired();
		skeleton.setCanPickUpLoot(false);
		// The servant reaches as far as it seeks, so a foe it takes is a foe it can fight.
		skeleton.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(GUARD_RADIUS);
		servantGrow(skeleton);
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
		if (modifiers.awakened()) {
			mount(skeleton);
		}
		CastHits.touch(caster);
		caster.level().sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0, at.z, 30, 0.4, 0.8, 0.4, 0.05);
		caster.level().playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, caster.getSoundSource(), 0.8F, 0.8F);
		return true;
	}

	/** The servant's own mount, or null for a servant a plain cast raised. */
	public static @Nullable SkeletonHorse horse(final Mob servant) {
		return servant.getVehicle() instanceof SkeletonHorse mount && mount.entityTags().contains(HORSE_TAG) ? mount : null;
	}

	/**
	 * Puts an awakened servant on its own skeleton horse. The horse is raised once and kept: a servant that
	 * already rides one keeps it, and the saved armor and pack are put back on the mount it arrives with.
	 */
	static @Nullable SkeletonHorse mount(final Skeleton servant) {
		SkeletonHorse existing = horse(servant);
		if (existing != null) {
			return existing;
		}
		if (!(servant.level() instanceof ServerLevel level)) {
			return null;
		}
		SkeletonHorse raised = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (raised == null) {
			return null;
		}
		raised.addTag(HORSE_TAG);
		raised.addTag(SUMMON_TAG);
		ownerOf(servant).ifPresent(owner -> raised.addTag(OWNER_TAG_PREFIX + owner));
		for (String friend : FRIEND_TAGS) {
			raised.addTag(friend);
		}
		raised.addTag(MobSpells.NO_BEHAVIOR_TAG);
		raised.addTag(NO_SPAWN_ROLL_TAG);
		raised.setPersistenceRequired();
		raised.setTamed(true);
		raised.setHealth((float) HORSE_MAX_HEALTH);
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_HORSE servant={} horse={} maxHealth={} health={} riding={}",
			servant.getUUID(), raised.getUUID(), raised.getMaxHealth(), raised.getHealth(), servant.isPassenger());
		raised.snapTo(servant.getX(), servant.getY(), servant.getZ(), servant.getYRot(), 0.0F);
		if (!level.addFreshEntity(raised)) {
			return null;
		}
		SummonInventoryStore.restoreHorse(raised);
		servant.startRiding(raised);
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_HORSE servant={} horse={} maxHealth={} riding={}",
			servant.getUUID(), raised.getUUID(), raised.getMaxHealth(), servant.isPassenger());
		return raised;
	}

	/**
	 * The mount's own maximum health. Vanilla's {@code minecraft:max_health} attribute is capped at 1024, so the
	 * awakened mount answers {@link #HORSE_MAX_HEALTH} here instead, and the health it carries is read the same way.
	 */
	public static boolean isMount(final Mob mob) {
		return mob.entityTags().contains(HORSE_TAG);
	}

	/** Sends the servant's mount away with it, so a retired or dead servant leaves no horse behind. */
	static void dismissHorse(final Mob servant) {
		SkeletonHorse mount = horse(servant);
		if (mount != null) {
			SummonInventoryStore.saveHorse(mount);
			mount.discard();
		}
	}

	/** Saves the servant's equipped gear for its next life. */
	public static void equip(final Skeleton skeleton, final EquipmentSlot slot, final ItemStack stack) {
		skeleton.setItemSlot(slot, stack);
		skeleton.setDropChance(slot, 0.0F);
		SummonInventoryStore.save(skeleton);
	}

	/**
	 * A recast mends the servant it called back by {@link #RECALL_HEAL_FRACTION} of its maximum health. A servant
	 * already at full health takes nothing, and one wound past the mend keeps the rest of its wound.
	 */
	static float healOnRecall(final Skeleton servant) {
		float before = servant.getHealth();
		float mend = (float) (servant.getMaxHealth() * RECALL_HEAL_FRACTION);
		servant.setHealth(Math.min(servant.getMaxHealth(), before + mend));
		float gained = servant.getHealth() - before;
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_RECALL_HEAL servant={} fraction={} before={} after={} gained={}",
			servant.getUUID(), RECALL_HEAL_FRACTION, before, servant.getHealth(), gained);
		return gained;
	}

	/** True when this entity is a summoned servant, the one kind of undead that takes healing as healing. */
	public static boolean isServant(final Entity entity) {
		return entity.entityTags().contains(SUMMON_TAG);
	}

	/** A servant that carries no growth yet starts at level zero with no experience. */
	private static void servantGrow(final Skeleton skeleton) {
		if (skeleton.getAttached(GROWTH) == null) {
			restoreGrowth(skeleton, 0, 0, List.of());
		}
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

	/** The mode this master chose: search the ground around it, or mirror its own target. */
	public static Mode targetMode(final ServerPlayer caster) {
		return MODES.getOrDefault(caster.getUUID(), Mode.SEARCH);
	}

	/**
	 * The reach the screen tells the player about. It is read from the one constant the search uses, so
	 * the number a player reads and the number the servant seeks with cannot drift apart.
	 */
	public static int screenReach() {
		return (int) GUARD_RADIUS;
	}

	/** Sets the mode, and drops a target the new mode no longer covers. */
	public static void setTargetMode(final ServerPlayer caster, final Mode mode) {
		MODES.put(caster.getUUID(), mode);
		Skeleton servant = find(caster.level().getServer(), caster.getUUID());
		if (servant != null && targetFor(servant, mode) != servant.getTarget()) {
			servant.setTarget(null);
		}
	}

	/** The foe this servant should hold in this mode; null when the mode has none. */
	static @Nullable Mob targetFor(final Skeleton skeleton, final Mode mode) {
		ServerPlayer master = owner(skeleton);
		if (master == null || master.level() != skeleton.level()) {
			return null;
		}
		if (mode == Mode.MIRROR) {
			return master.getLastHurtMob() instanceof Mob foe && Friends.isFoe(foe) && foe.isAlive() ? foe : null;
		}
		// The clear radius leads: the ground around the master comes first, and only a master whose ground is
		// clear lets the servant reach out to its wider seek reach.
		Mob inTheClear = nearestFoe(skeleton, master, CLEAR_RADIUS);
		return inTheClear != null ? inTheClear : nearestFoe(skeleton, master, GUARD_RADIUS);
	}

	/**
	 * The nearest foe the servant can sight within {@code reach} blocks of its master, or null. A foe closer
	 * to the servant wins, so the one it can fall on first is the one it takes.
	 */
	static @Nullable Mob nearestFoe(final Skeleton skeleton, final ServerPlayer master, final double reach) {
		Mob nearest = null;
		double best = Double.MAX_VALUE;
		for (Mob mob : master.level().getEntitiesOfClass(Mob.class, master.getBoundingBox().inflate(reach),
			other -> other.isAlive() && Friends.isFoe(other) && other.distanceToSqr(master) <= reach * reach)) {
			if (!sighted(skeleton, master, mob)) {
				continue;
			}
			double distance = mob.distanceToSqr(skeleton);
			if (distance < best) {
				best = distance;
				nearest = mob;
			}
		}
		return nearest;
	}

	/** True when a foe of the master stands inside the reach the servant clears before it follows again. */
	static boolean clearing(final Skeleton skeleton, final ServerPlayer master) {
		return nearestFoe(skeleton, master, CLEAR_RADIUS) != null;
	}

	/**
	 * Whether the servant may take this foe. Its own line of sight is not the only one: a servant shares
	 * its master's eyes, so a foe the master sees is one the servant sees, wall or no wall.
	 */
	static boolean sighted(final Skeleton skeleton, final ServerPlayer master, final Mob foe) {
		return skeleton.hasLineOfSight(foe) || master.hasLineOfSight(foe);
	}

	/** A servant's live level for a reader outside this mod; -1 for anything that is not a servant. */
	static int sharedLevel(final net.minecraft.world.entity.Entity entity) {
		return entity instanceof Skeleton skeleton && skeleton.entityTags().contains(SUMMON_TAG) ? level(skeleton) : -1;
	}

	/** Every stat point the servant has rolled, one per level. */
	public static int[] stats(final Skeleton skeleton) {
		int[] rolled = new int[STAT_COUNT];
		List<Integer> growth = growth(skeleton).stats();
		for (int i = 0; i < rolled.length && i < growth.size(); i++) {
			rolled[i] = Math.max(0, growth.get(i));
		}
		return rolled;
	}

	public static int level(final Skeleton skeleton) {
		return growth(skeleton).level();
	}

	/** The servant's own experience toward its next level. */
	public static int xp(final Skeleton skeleton) {
		return growth(skeleton).xp();
	}

	public static Growth growth(final Skeleton skeleton) {
		Growth grown = skeleton.getAttached(GROWTH);
		return grown == null ? FRESH : grown;
	}

	/** The levels, experience and stat points a restored servant wears again. */
	static void restoreGrowth(final Skeleton skeleton, final int level, final int xp, final List<Integer> stats) {
		List<Integer> rolled = new ArrayList<>();
		for (int stat : stats) {
			rolled.add(Math.max(0, stat));
		}
		while (rolled.size() < STAT_COUNT) {
			rolled.add(0);
		}
		skeleton.setAttached(GROWTH, new Growth(Math.max(0, level), Math.max(0, xp), List.copyOf(rolled)));
		applyStats(skeleton, rolled);
	}

	/** What the servant needs for its next level: half of what a player of that level needs. */
	public static int xpNeeded(final Skeleton skeleton) {
		return xpStep(level(skeleton));
	}

	/**
	 * The servant's own curve, at half the size of a player's: half of the RPG mod's curve when that mod
	 * publishes one, half of vanilla's hundred a level otherwise. The share is read on each call.
	 */
	static int xpStep(final int level) {
		return RpgBridge.xpNeededFor(level) / 2;
	}

	/** Adds the serving share of a fallen foe's experience to the servant that struck it down. */
	private static void shareKill(final Mob fallen) {
		LivingEntity killer = fallen.getKillCredit();
		if (!(killer instanceof Skeleton servant) || !servant.entityTags().contains(SUMMON_TAG) || !servant.isAlive()) {
			return;
		}
		ServerPlayer master = owner(servant);
		if (master == null) {
			return;
		}
		gain(servant, master, Math.max(1, fallen.getExperienceReward((ServerLevel) fallen.level(), master)));
	}

	/**
	 * The master's own gain reaches the servant at the same size. The servant pays its master back from
	 * inside {@link #gain}, so a master's gain never pays itself twice.
	 */
	public static void shareGain(final ServerPlayer master, final int amount) {
		if (amount <= 0 || Boolean.TRUE.equals(SHARING.get())) {
			return;
		}
		Skeleton servant = find(master.level().getServer(), master.getUUID());
		if (servant == null) {
			return;
		}
		gain(servant, master, amount);
	}

	/** Adds experience to a servant, levels it when it passes the step, and pays its master the share. */
	public static void gain(final Skeleton servant, final ServerPlayer master, final int amount) {
		if (amount <= 0 || Boolean.TRUE.equals(SHARING.get())) {
			return;
		}
		SHARING.set(Boolean.TRUE);
		try {
			grow(servant, master, amount);
		} finally {
			SHARING.remove();
		}
	}

	private static void grow(final Skeleton servant, final ServerPlayer master, final int amount) {
		Growth growth = growth(servant);
		int xp = growth.xp() + amount;
		int level = growth.level();
		List<Integer> rolled = new ArrayList<>(growth.stats());
		while (rolled.size() < STAT_COUNT) {
			rolled.add(0);
		}
		int levels = 0;
		while (xp >= xpStep(level)) {
			xp -= xpStep(level);
			level++;
			levels++;
			int stat = servant.getRandom().nextInt(STAT_COUNT);
			rolled.set(stat, rolled.get(stat) + 1);
		}
		servant.setAttached(GROWTH, new Growth(level, xp, List.copyOf(rolled)));
		if (levels > 0) {
			applyStats(servant, rolled);
			refreshHealth(servant, false);
			Spellbooks.LOGGER.info("[SPELL] event=SUMMON_LEVEL servant={} levels={} level={} stats={}",
				servant.getUUID(), levels, level, rolled);
		}
		int shared = (int) Math.floor(amount * XP_SHARE);
		if (shared > 0) {
			master.giveExperiencePoints(shared);
		}
		SummonInventoryStore.save(servant);
		Spellbooks.LOGGER.info("[SPELL] event=SUMMON_XP servant={} amount={} shared={} level={} xp={}",
			servant.getUUID(), amount, shared, level, xp);
	}

	/**
	 * Keeps the servant at its master's pace: it walks as fast as the master walks and sprints when the master sprints.
	 * A mob's ground speed grows with the square of its move speed, a player's with the speed alone, so the servant's
	 * speed is the root of the master's, over the follow goal's own factor.
	 */
	static void pace(final Skeleton servant, final ServerPlayer master) {
		double playerSpeed = master.getAttributeValue(Attributes.MOVEMENT_SPEED);
		servant.setSprinting(master.isSprinting());
		// The servant's own sprint adds its vanilla boost, so the base leaves room for it.
		double own = master.isSprinting() ? SPRINT_BOOST : 1.0;
		servant.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(Math.sqrt(playerSpeed * PLAYER_INPUT) / FOLLOW_SPEED / own);
	}

	// Stats never wear off, so each rolled point becomes a permanent modifier.
	private static void applyStats(final Skeleton servant, final List<Integer> rolled) {
		setModifier(servant, Attributes.MAX_HEALTH, STAT_HEALTH_ID,
			stat(rolled, 0) * STAT_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		setModifier(servant, Attributes.ARMOR, STAT_ARMOR_ID,
			stat(rolled, 1) * STAT_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		setModifier(servant, Attributes.KNOCKBACK_RESISTANCE, STAT_KNOCKBACK_ID,
			stat(rolled, 2) * STAT_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
	}

	private static int stat(final List<Integer> rolled, final int index) {
		return index < rolled.size() ? Math.max(0, rolled.get(index)) : 0;
	}

	private static void setModifier(
		final Skeleton servant, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
		final Identifier id, final double amount, final AttributeModifier.Operation operation
	) {
		AttributeInstance instance = servant.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier previous = instance.getModifier(id);
		if (previous != null) {
			instance.removeModifier(id);
		}
		if (amount != 0.0) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Search the ground around the master, or take the master's own target. */
	public enum Mode {
		SEARCH,
		MIRROR
	}

	/** A servant's own growth, saved beside its gear: its level, its experience and its rolled stat points. */
	public record Growth(int level, int xp, List<Integer> stats) {
		public static final Codec<Growth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(Growth::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(Growth::xp),
			Codec.INT.listOf().optionalFieldOf("stats", List.of()).forGetter(Growth::stats)
		).apply(instance, Growth::new));
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
		dismissHorse(skeleton);
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
		widenBowRange(skeleton);
		skeleton.getGoalSelector().addGoal(2, new FollowGoal(skeleton));
		skeleton.getGoalSelector().addGoal(3, new TrotGoal(skeleton));
		skeleton.getGoalSelector().addGoal(1, new LootGoal(skeleton));
		skeleton.getGoalSelector().addGoal(1, new PackCasterGoal(skeleton));
	}

	/** Swaps vanilla's fifteen-block bow goal for one that looses at the servant's own reach. */
	private static void widenBowRange(final Skeleton skeleton) {
		for (var wrapped : List.copyOf(skeleton.getGoalSelector().getAvailableGoals())) {
			if (wrapped.getGoal() instanceof RangedBowAttackGoal<?>) {
				skeleton.getGoalSelector().removeGoal(wrapped.getGoal());
			}
		}
		skeleton.getGoalSelector().addGoal(2, new RangedBowAttackGoal<>(skeleton, 1.0, 20, (float) RANGED_ATTACK_RANGE));
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

	/** Answers whoever strikes the servant, except its own master and its master's friends. */
	static final class AnswerBlowGoal extends HurtByTargetGoal {
		private final Skeleton skeleton;

		AnswerBlowGoal(final Skeleton skeleton) {
			super(skeleton);
			this.skeleton = skeleton;
		}

		@Override
		public boolean canUse() {
			if (answerable(this.skeleton.getLastHurtByMob())) {
				return super.canUse();
			}
			this.skeleton.setLastHurtByMob(null);
			return false;
		}

		private boolean answerable(final @Nullable LivingEntity striker) {
			if (striker == null) {
				return false;
			}
			if (striker instanceof Player player && isServant(this.skeleton, player)) {
				return false;
			}
			return !Friends.isFriend(striker);
		}
	}

	/** The foe the servant holds: the nearest one near its master, or the one its master is fighting. */
	static final class GuardGoal extends TargetGoal {
		private final Skeleton skeleton;
		private @Nullable Mob found;

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
			this.found = targetFor(this.skeleton, targetMode(master));
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
			if (master == null || master.level() != this.skeleton.level()) {
				return false;
			}
			// Walking home would break the servant off a foe it is still fighting, so it waits its turn.
			LivingEntity quarry = this.skeleton.getTarget();
			if (quarry != null && quarry.isAlive() && this.skeleton.distanceToSqr(quarry) <= GUARD_RADIUS * GUARD_RADIUS) {
				return false;
			}
			// A foe inside the clear radius of the master is the servant's work before it walks home.
			if (clearing(this.skeleton, master)) {
				return false;
			}
			return this.skeleton.distanceToSqr(master) > FOLLOW_DISTANCE * FOLLOW_DISTANCE;
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
				this.skeleton.getNavigation().moveTo(master, FOLLOW_SPEED);
			}
		}
	}

	/** Walks the servant toward its master, re-planning the path while it lags past the follow distance. */
	static final class TrotGoal extends Goal {
		private final Skeleton skeleton;

		TrotGoal(final Skeleton skeleton) {
			this.skeleton = skeleton;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			ServerPlayer master = owner(this.skeleton);
			if (master == null || master.level() != this.skeleton.level()) {
				return false;
			}
			// The same work the follow goal waits on: the servant walks home only with its master's ground clear.
			if (clearing(this.skeleton, master)) {
				return false;
			}
			return this.skeleton.distanceToSqr(master) > FOLLOW_DISTANCE * FOLLOW_DISTANCE;
		}

		@Override
		public void tick() {
			ServerPlayer master = owner(this.skeleton);
			if (master == null) {
				return;
			}
			this.skeleton.getNavigation().moveTo(master, FOLLOW_SPEED);
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
