package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Traits;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.WorldRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * One durable threat roll sets health and damage. The Overworld climbs one level per 100 blocks from 0,0, the Nether
 * and End eight, from their own base levels. On top of the level, an Overworld mob takes a health and damage
 * multiplier from its altitude: none at y=0, more below it than above it. The Nether and End take none.
 */
public final class SpawnScaling {
	/** Blocks from 0,0 per step of distance. */
	public static final int BLOCKS_PER_LEVEL = 100;
	/** Levels the Nether and End gain per step of distance; the Overworld gains one. */
	public static final int DIMENSION_LEVELS_PER_STEP = 8;
	/** Blocks below y=0 where the depth multiplier peaks: the floor of the world. */
	public static final double DEPTH_CAP = 64.0;
	/** Blocks above y=0 where the height multiplier peaks: the top of the build limit. */
	public static final double HEIGHT_CAP = 320.0;
	public static final double HEALTH_DEPTH_WEIGHT = 0.50;
	public static final double DAMAGE_DEPTH_WEIGHT = 0.40;
	public static final double HEALTH_HEIGHT_WEIGHT = 0.20;
	public static final double DAMAGE_HEIGHT_WEIGHT = 0.16;
	public static final double LEVEL_HEALTH_BONUS = 0.05;
	public static final double LEVEL_DAMAGE_BONUS = 0.04;
	/**
	 * A test rig sets this system property to {@code worldSpawn} so distance is measured from the level's spawn: the game
	 * test arenas sit about ten million blocks from 0,0, which would make every test mob a level-100,000 mob. Nothing in
	 * the shipped game sets it, so play always measures from 0,0.
	 */
	public static final String ORIGIN_PROPERTY = "stella.mobs.scalingOrigin";
	public static final String ORIGIN_WORLD_SPAWN = "worldSpawn";
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_health");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_damage");

	private SpawnScaling() {
	}

	/** The levels a distance from 0,0 adds in a dimension. A dimension the mod does not know adds none. */
	public static int distanceLevels(final ResourceKey<Level> dimension, final double distance) {
		long steps = (long) Math.floor(Math.max(0.0, distance) / BLOCKS_PER_LEVEL);
		long perStep;
		if (Level.OVERWORLD.equals(dimension)) {
			perStep = 1L;
		} else if (Level.NETHER.equals(dimension) || Level.END.equals(dimension)) {
			perStep = DIMENSION_LEVELS_PER_STEP;
		} else {
			return 0;
		}
		return (int) Math.min(Integer.MAX_VALUE, steps * perStep);
	}

	/** The flat levels the mob's biome adds, from the config's exact biome entries. Zero when the file lists none. */
	public static int biomeOffset(final Mob mob) {
		WorldRules rules = GearConfigStore.get().world();
		if (rules.biomeLevels().isEmpty()) {
			return 0;
		}

		return mob.level().getBiome(mob.blockPosition()).unwrapKey()
			.map(key -> rules.biomeLevel(key.identifier())).orElse(0);
	}

	/** True where the depth and height multiplier applies. The Nether and End take none. */
	public static boolean usesAltitude(final ResourceKey<Level> dimension) {
		return !Level.NETHER.equals(dimension) && !Level.END.equals(dimension);
	}

	/** The health the altitude adds: nothing at y=0, the depth weight at the world floor, the height weight at the build limit. */
	public static double altitudeHealth(final double y) {
		return y < 0.0 ? share(-y, DEPTH_CAP) * HEALTH_DEPTH_WEIGHT : share(y, HEIGHT_CAP) * HEALTH_HEIGHT_WEIGHT;
	}

	/** The damage the altitude adds, on the same curve as the health. */
	public static double altitudeDamage(final double y) {
		return y < 0.0 ? share(-y, DEPTH_CAP) * DAMAGE_DEPTH_WEIGHT : share(y, HEIGHT_CAP) * DAMAGE_HEIGHT_WEIGHT;
	}

	/**
	 * The absolute area level where the mob stands: the dimension's base, the levels its distance from 0,0 adds, and the
	 * flat levels of its biome, never below zero. A dimension the mod does not know has none.
	 */
	public static int areaLevel(final Mob mob) {
		ResourceKey<Level> dimension = mob.level().dimension();
		int base;
		if (Level.OVERWORLD.equals(dimension)) {
			base = 0;
		} else if (Level.NETHER.equals(dimension)) {
			base = ThreatState.NETHER_MINIMUM;
		} else if (Level.END.equals(dimension)) {
			base = ThreatState.END_MINIMUM;
		} else {
			return 0;
		}

		long area = (long) base + distanceLevels(dimension, distanceFromOrigin(mob)) + biomeOffset(mob);
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, area));
	}

	/** Blocks from 0,0 in the mob's own dimension, or from the level's spawn when a test rig asks for it. */
	public static double distanceFromOrigin(final Mob mob) {
		double originX = 0.0;
		double originZ = 0.0;
		if (ORIGIN_WORLD_SPAWN.equals(System.getProperty(ORIGIN_PROPERTY))) {
			BlockPos spawn = mob.level().getRespawnData().pos();
			originX = spawn.getX();
			originZ = spawn.getZ();
		}

		return Math.hypot(mob.getX() - originX, mob.getZ() - originZ);
	}

	/** Health for a threat level at a height, where the height is a block y and counts only where the dimension uses it. */
	public static double healthMultiplier(final int threatLevel, final double y) {
		return 1.0 + Math.max(0, threatLevel) * LEVEL_HEALTH_BONUS + altitudeHealth(y);
	}

	public static double damageMultiplier(final int threatLevel, final double y) {
		return 1.0 + Math.max(0, threatLevel) * LEVEL_DAMAGE_BONUS + altitudeDamage(y);
	}

	/** Fresh spawns and deliberate rank promotions heal to their scaled maximum. */
	public static void apply(final Mob mob) {
		apply(mob, false);
	}

	/** Saved mobs keep their health percentage while old modifiers migrate. */
	public static void reconcileLoaded(final Mob mob) {
		apply(mob, true);
	}

	private static void apply(final Mob mob, final boolean preserveHealth) {
		if (mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)) {
			return;
		}
		int threatLevel = ThreatState.level(mob);
		double y = usesAltitude(mob.level().dimension()) ? mob.getY() : 0.0;
		float oldHealth = mob.getHealth();
		float oldMaximum = mob.getMaxHealth();
		boolean removedOldHealth = removeModifier(mob, Attributes.MAX_HEALTH, Bosses.LEVEL_HEALTH_ID);
		removeModifier(mob, Attributes.ATTACK_DAMAGE, Bosses.LEVEL_DAMAGE_ID);
		boolean changedHealth = applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID,
			healthMultiplier(threatLevel, y) - 1.0);
		applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
			damageMultiplier(threatLevel, y) - 1.0);
		boolean changedTrait = Traits.refreshScaling(mob);
		if (!preserveHealth) {
			mob.setHealth(mob.getMaxHealth());
		} else if (removedOldHealth || changedHealth || changedTrait) {
			float fraction = oldMaximum <= 0.0F ? 1.0F : oldHealth / oldMaximum;
			mob.setHealth(Math.max(1.0F, Math.min(mob.getMaxHealth(), fraction * mob.getMaxHealth())));
		}
	}

	private static boolean removeModifier(
		final LivingEntity mob, final Holder<Attribute> attribute, final Identifier id
	) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null || !instance.hasModifier(id)) {
			return false;
		}
		instance.removeModifier(id);
		return true;
	}

	private static boolean applyModifier(
		final LivingEntity mob, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return false;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (existing != null) {
			if (existing.amount() == amount) {
				return false;
			}
			instance.removeModifier(id);
		}
		instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		return true;
	}

	public static double healthMultiplierOf(final LivingEntity mob) {
		AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return 1.0;
		}
		AttributeModifier modifier = instance.getModifier(HEALTH_ID);
		return modifier == null ? 1.0 : 1.0 + modifier.amount();
	}

	private static double share(final double value, final double cap) {
		return Math.min(1.0, Math.max(0.0, value) / cap);
	}
}
