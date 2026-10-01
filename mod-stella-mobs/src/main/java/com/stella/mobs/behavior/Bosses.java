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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
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
	/** Legacy boss modifiers remain named so saved mobs can remove them during threat migration. */
	public static final Identifier LEVEL_HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_level_health");
	public static final Identifier LEVEL_DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "boss_level_damage");
	/** The bar radius in blocks: a player further away than this is taken off the bar. */
	public static final int AURA_INTERVAL = 4;
	public static final int AURA_SOULS = 2;
	public static final int AURA_FLAMES = 3;
	private static volatile AuraRecorder auraRecorder = mob -> { };
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
		BossLook.mark(mob);
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

	/** B1: drops the watch entry, which ends the aura. Runs on death, on unload, and on a lost boss. */
	public static void unwatch(final Mob mob) {
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

	/** B1: the aura sweep. The cost is one entry per watched boss, and nothing at all with no boss. */
	public static void tick(final ServerLevel level) {
		Set<Mob> watched = ((AttachmentTarget) level).getAttached(WATCHED);
		if (watched == null || watched.isEmpty()) {
			return;
		}

		for (Mob mob : List.copyOf(watched)) {
			if (mob.isAlive() && !mob.isRemoved()) {
				emitAura(level, mob);
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
		// The mark scales the bare base. Threat scaling applies one level modifier after the boss flag.
		// Boss flag first: the elite kit then skips a boss.
		EliteState.setBoss(mob, true);
		Elite.mark(mob, GearTier.NETHERITE, healthMultiplier);
		applyKit(mob, registry);
		SpawnScaling.apply(mob);
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

		// A skeleton boss takes the bow, not a sword.
		CombatRole role = mob.getType() == EntityTypes.SKELETON ? CombatRole.RANGED : configuredRole(mob);
		Item weapon = switch (role) {
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

	/** Soul and small flame particles around the boss, sized to its drawn body, every few ticks. */
	public static void emitAura(final ServerLevel level, final Mob mob) {
		if (level.getGameTime() % AURA_INTERVAL != 0) {
			return;
		}
		float factor = BossLook.renderFactor();
		double width = mob.getBbWidth() * factor * 0.5;
		double height = mob.getBbHeight() * factor;
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + height * 0.5, mob.getZ(), AURA_SOULS,
			width, height * 0.4, width, 0.02);
		level.sendParticles(ParticleTypes.SMALL_FLAME, mob.getX(), mob.getY() + height * 0.3, mob.getZ(), AURA_FLAMES,
			width, height * 0.3, width, 0.01);
		auraRecorder.emitted(mob);
	}

	/** Test seam: each aura burst. */
	@FunctionalInterface
	public interface AuraRecorder {
		void emitted(Mob mob);
	}

	public static void setAuraRecorder(final AuraRecorder recorder) {
		auraRecorder = recorder == null ? mob -> { } : recorder;
	}

	private static CombatRole configuredRole(final Mob mob) {
		RoleSpec configured = GearConfigStore.get().roleFor(typeOf(mob));
		return configured != null ? configured.role() : CombatRole.MELEE;
	}

	private static Identifier typeOf(final Mob mob) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
	}
}
