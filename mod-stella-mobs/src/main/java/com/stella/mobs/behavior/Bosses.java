package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RoleSpec;
import com.stella.mobs.gear.EnchantRoller;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * The boss class, B01 to B08: a rare spawn marked elite at twice an elite's health, carrying the ten
 * level bonus, in fully enchanted netherite, dropping its kit at 80 percent, with one elite companion
 * of its own kind and a boss bar.
 */
public final class Bosses {
	/** B03: the boss base is twice an elite's, and an elite's multiplier is the shared 2.0. */
	public static final double ELITE_HEALTH_MULTIPLIER = 2.0;
	/** B08: the boss drop rate. */
	public static final float DROP_CHANCE = 0.80F;
	/**
	 * B03: the ten level step, as a constant. Ten levels above the area is a difference of ten levels,
	 * and R12's scaling is linear in level, so the difference is ten times R12's own per level share:
	 * {@link SpawnScaling#LEVEL_HEALTH_BONUS} times {@link SpawnScaling#LEVEL_DISTANCE_POINTS} is the
	 * 0.5 below. The area level itself is already paid for by {@code spawn_scale_health}, which both the
	 * boss and the elite beside it carry exactly once, so no area factor belongs in the step.
	 */
	public static final double LEVEL_HEALTH_STEP =
		SpawnScaling.LEVEL_HEALTH_BONUS * SpawnScaling.LEVEL_DISTANCE_POINTS;
	public static final double LEVEL_DAMAGE_STEP =
		SpawnScaling.LEVEL_DAMAGE_BONUS * SpawnScaling.LEVEL_DISTANCE_POINTS;
	public static final Identifier LEVEL_HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_level_health");
	public static final Identifier LEVEL_DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_level_damage");
	/** The bar radius in blocks: a player further away than this is taken off the bar. */
	public static final double BAR_RADIUS = 64.0;
	private static final double BAR_RADIUS_SQR = BAR_RADIUS * BAR_RADIUS;
	/** B1: the bosses whose bar is live. One entry per boss, so the sweep never walks the level. */
	private static final AttachmentType<Set<Mob>> WATCHED = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_bar_watchers"), HashSet::new
	);
	/** B04: the mobs that may be bosses. */
	private static final Set<EntityType<?>> ELIGIBLE = Set.of(
		EntityTypes.SKELETON,
		EntityTypes.SPIDER,
		EntityTypes.PIGLIN_BRUTE,
		EntityTypes.BLAZE,
		EntityTypes.WITHER_SKELETON,
		EntityTypes.ENDERMAN,
		EntityTypes.EVOKER
	);

	private Bosses() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the boss bar watch list {}", WATCHED.identifier());
	}

	/** B1: adds a boss to its level's watch list, so the sweep visits bosses and not every entity. */
	public static void watch(final Mob mob) {
		if (mob.level() instanceof ServerLevel level) {
			Set<Mob> watched = ((AttachmentTarget) level).getAttachedOrCreate(WATCHED);
			if (watched.add(mob)) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=bossBarWatch t={} WATCH uuid={} type={} watched={}",
					level.getGameTime(), mob.getUUID(), typeOf(mob), watched.size()
				);
			}
		}
	}

	/** B1: drops the watch entry and clears the bar. Runs on death, on unload, and on a lost boss. */
	public static void unwatch(final Mob mob) {
		ReactiveState state = ReactiveState.get(mob);
		ServerBossEvent bar = state.bossBar();
		if (bar != null) {
			// removeAllPlayers sends one remove packet per player; the bar is then dropped so the
			// next sweep cannot rebuild it and hand a ghost bar back.
			bar.removeAllPlayers();
			bar.setVisible(false);
			state.setBossBar(null);
		}

		if (mob.level() instanceof ServerLevel level) {
			Set<Mob> watched = ((AttachmentTarget) level).getAttached(WATCHED);
			if (watched != null && watched.remove(mob)) {
				StellaMobs.LOGGER.info(
					"[BEHAVIOR] test=bossBarWatch t={} UNWATCH uuid={} type={} watched={}",
					level.getGameTime(), mob.getUUID(), typeOf(mob), watched.size()
				);
			}
		}
	}

	/** B1: the bar sweep. The cost is one entry per watched boss, and nothing at all with no boss. */
	public static void tick(final ServerLevel level) {
		Set<Mob> watched = ((AttachmentTarget) level).getAttached(WATCHED);
		if (watched == null || watched.isEmpty()) {
			return;
		}

		for (Mob mob : List.copyOf(watched)) {
			if (mob.isAlive() && !mob.isRemoved()) {
				updateBossBar(level, mob);
			} else {
				unwatch(mob);
			}
		}
	}

	public static boolean canBeBoss(final Mob mob) {
		return ELIGIBLE.contains(mob.getType());
	}

	public static boolean isBoss(final Mob mob) {
		return EliteState.isBoss(mob);
	}

	/** The registered base health of a mob's kind, which the elite and boss multipliers act on. */
	public static double baseHealth(final Mob mob) {
		var instance = mob.getAttribute(Attributes.MAX_HEALTH);
		return instance == null ? 0.0 : instance.getAttribute().value().getDefaultValue();
	}

	/** B01 and B02: the roll, the chunk guard, the mark, the kit, the companion, and the bar. */
	public static void rollAtSpawn(final Mob mob, final ServerLevel level, final double chance) {
		if (EliteState.isSummoned(mob) || EliteState.isBoss(mob) || !canBeBoss(mob)) {
			return;
		}

		// M1: the roll runs first, so the chunk query and the chunk load it forces are paid by the one
		// eligible spawn in a hundred that can become a boss, and not by the ninety-nine that cannot.
		if (!Elite.roll(mob.getRandom(), chance) || chunkHasBoss(mob)) {
			return;
		}

		blessAsBoss(mob, level, chance);
		BossCompanion.queue(mob);
	}

	/** B03 to B08 for one mob: the mark, the kit, the ten level bonus, the bar, and the watch entry. */
	public static void blessAsBoss(final Mob mob, final ServerLevel level, final double chance) {
		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		double healthMultiplier = GearConfigStore.get().rollFor(typeOf(mob)).bossHealthMultiplier();
		// The mark runs before the elite roll, which then returns at its own isElite guard. The plain
		// mark is correct here: it scales the bare base and leaves R12's area modifier in place, so the
		// area multiplier is applied exactly once by R12 itself.
		Elite.mark(mob, GearTier.NETHERITE, healthMultiplier);
		EliteState.setBoss(mob, true);
		applyKit(mob, registry);
		applyLevelBonus(mob, registry);
		updateBossBar(level, mob);
		watch(mob);
		// The ability slot is filled here as well as from the installer, so a boss promoted by hand and a
		// boss promoted by its own roll end in the same state. The second call adds nothing.
		BossAbilities.install(mob);

		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=bossSpawn t={} BOSS type={} chance={} health={} areaLevel={} levelBonus={} companion={}",
			level.getGameTime(),
			typeOf(mob),
			chance,
			mob.getHealth(),
			SpawnScaling.areaLevel(mob),
			GearConfigStore.get().rollFor(typeOf(mob)).bossLevelBonus(),
			BossCompanion.COUNT
		);
	}

	/** The fully enchanted netherite kit: armour, a weapon, and the 0.80 drop on every equipped slot. */
	public static void applyKit(final Mob mob, final Registry<Enchantment> registry) {
		for (EquipmentSlot slot : GearTier.armorSlots()) {
			mob.setItemSlot(slot, fullyEnchant(new ItemStack(GearTier.NETHERITE.armorFor(slot)), registry));
		}

		Item weapon = switch (configuredRole(mob)) {
			case RANGED -> GearTier.NETHERITE.rangedWeapon();
			case MELEE, MAGIC -> GearTier.NETHERITE.meleeWeapon();
		};
		mob.setItemSlot(EquipmentSlot.MAINHAND, fullyEnchant(new ItemStack(weapon), registry));

		// The decay boss fights behind a shield, and the installer that runs after the kit sees it and adds
		// ShieldUseGoal. A shield is not netherite, which is the one deliberate exception to the kit's grade.
		if (mob instanceof WitherSkeleton) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}

		// A boss promoted by hand reaches this after the installer, so the shield goal is invited here too.
		BehaviorInstaller.installShield(mob);

		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!mob.getItemBySlot(slot).isEmpty()) {
				mob.setDropChance(slot, DROP_CHANCE);
			}
		}
	}

	/**
	 * Enchant every enchantment the item accepts, each at its own maximum, skipping any candidate that
	 * is incompatible with what the item already carries: the filter {@code canEnchant} does not apply.
	 */
	public static ItemStack fullyEnchant(final ItemStack stack, final Registry<Enchantment> registry) {
		if (stack.isEmpty() || stack.get(DataComponents.ENCHANTABLE) == null) {
			return stack;
		}

		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);

		for (Holder<Enchantment> candidate : EnchantRoller.poolFor(stack, registry)) {
			// I2: a curse carries no exclusive set, so this walk always landed it, and a vanishing
			// piece never drops because Mob's drop path reads PREVENT_EQUIPMENT_DROP.
			if (candidate.is(EnchantmentTags.CURSE)) {
				continue;
			}

			boolean compatible = mutable.keySet().stream().allMatch(held -> Enchantment.areCompatible(held, candidate));
			if (compatible) {
				mutable.upgrade(candidate, candidate.value().getMaxLevel());
			}
		}

		stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
		return stack;
	}

	/** B03: one guarded permanent modifier per attribute, holding the ten level step. */
	public static void applyLevelBonus(final Mob mob, final Registry<Enchantment> registry) {
		double bonus = GearConfigStore.get().rollFor(typeOf(mob)).bossLevelBonus();
		applyModifier(mob, Attributes.MAX_HEALTH, LEVEL_HEALTH_ID, LEVEL_HEALTH_STEP * bonus);
		applyModifier(mob, Attributes.ATTACK_DAMAGE, LEVEL_DAMAGE_ID, LEVEL_DAMAGE_STEP * bonus);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void applyModifier(
		final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		var instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}

		var existing = instance.getModifier(id);
		if (existing != null) {
			if (existing.amount() == amount) {
				return;
			}

			instance.removeModifier(id);
		}

		// ADD_MULTIPLIED_TOTAL, not ADD_MULTIPLIED_BASE: the spec reads sequentially, "the area
		// multiplier, then the 1.5 from the ten levels", so the step multiplies the already scaled
		// maximum. As a base modifier the two would add instead, and a capped boss would read
		// base x 2.25 where the spec says base x 1.75 x 1.5 = 2.625.
		instance.addPermanentModifier(
			new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
		);
	}

	/** B02: the guard reads the mob's own chunk and excludes the mob itself. */
	public static boolean chunkHasBoss(final Mob mob) {
		return !bossesInChunk(mob).isEmpty();
	}

	public static List<Mob> bossesInChunk(final Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return List.of();
		}

		var chunk = level.getChunkAt(mob.blockPosition()).getPos();
		AABB box = new AABB(
			chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
			chunk.getMaxBlockX() + 1.0, level.getMaxY() + 1.0, chunk.getMaxBlockZ() + 1.0
		);
		// A discarded boss still answers an entity query for a tick, so it must not hold the chunk.
		return level.getEntities(
			EntityTypeTest.forClass(Mob.class), box,
			candidate -> candidate != mob && !candidate.isRemoved() && EliteState.isBoss(candidate)
		);
	}

	/** The bar follows the health fraction and follows the boss's own players. */
	public static void updateBossBar(final ServerLevel level, final Mob mob) {
		ReactiveState state = ReactiveState.get(mob);
		ServerBossEvent bar = state.bossBar();
		if (bar == null) {
			bar = new ServerBossEvent(
				mob.getUUID(), mob.getName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS
			);
			state.setBossBar(bar);
		}

		float progress = mob.getMaxHealth() <= 0.0F ? 0.0F : mob.getHealth() / mob.getMaxHealth();
		bar.setProgress(progress);
		bar.setVisible(true);

		// Removal runs first, over the bar's own player set: a player who changed level, left the
		// server, or walked out of range is absent from level.players(), so the loop below misses them.
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!level.players().contains(player) || player.distanceToSqr(mob) > BAR_RADIUS_SQR) {
				bar.removePlayer(player);
			}
		}

		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(mob) <= BAR_RADIUS_SQR) {
				bar.addPlayer(player);
			}
		}
	}

	private static CombatRole configuredRole(final Mob mob) {
		RoleSpec configured = GearConfigStore.get().roleFor(typeOf(mob));
		return configured != null ? configured.role() : CombatRole.MELEE;
	}

	private static Identifier typeOf(final Mob mob) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
	}
}
