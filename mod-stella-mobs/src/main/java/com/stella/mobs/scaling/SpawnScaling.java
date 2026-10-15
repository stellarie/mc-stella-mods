package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.Traits;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.ScalingConfig;
import com.stella.mobs.config.ScalingConfigStore;
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
 * and End eight, from their own base levels. Altitude adds levels in the Overworld (see {@link AltitudeLevels}).
 * Health grows linearly with the level. Damage compounds up to {@code tail_start}, then follows the tower's S-curve.
 */
public final class SpawnScaling {
	/** Blocks from 0,0 per step of distance. */
	public static final int BLOCKS_PER_LEVEL = 100;
	/** Levels the Nether and End gain per step of distance; the Overworld gains one. */
	public static final int DIMENSION_LEVELS_PER_STEP = 8;
	/**
	 * The default health gained per level. {@code mod-stella-spells} reads this field by name, so keep it public.
	 * The live rate comes from {@code scaling.json}.
	 */
	public static final double LEVEL_HEALTH_BONUS = 0.20;
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

	// Huge tower floors overflow the tier power.
	private static final double MAX_HEALTH_MULTIPLIER = 1.0e9;

	public static double healthMultiplier(final int threatLevel) {
		ScalingConfig config = ScalingConfigStore.get();
		int level = Math.max(0, threatLevel);
		int tiers = level / Math.max(1, config.healthTierStep());
		double tiered = Math.pow(config.healthTierFactor(), tiers);
		return Math.min(MAX_HEALTH_MULTIPLIER, (1.0 + level * config.healthPerLevel()) * tiered);
	}

	/** Compound growth to the tail start, then the tower S-curve on top of it. */
	public static double damageMultiplier(final int threatLevel) {
		ScalingConfig config = ScalingConfigStore.get();
		int level = Math.max(0, threatLevel);
		if (level <= config.tailStart()) {
			return Math.pow(config.damageGrowth(), level);
		}
		double base = Math.pow(config.damageGrowth(), config.tailStart());
		return base * (1.0 + config.tailMax() * tail(level - config.tailStart(), config));
	}

	/** Flat damage past the tail start: the tower's one heart per 100 floors. */
	public static double tailFlat(final int threatLevel) {
		ScalingConfig config = ScalingConfigStore.get();
		return Math.max(0, threatLevel - config.tailStart()) * config.tailFlatPerLevel();
	}

	/** The tower S-curve: 0 at 0, 0.5 at the midpoint, 1 at the end and past it. */
	public static double tail(final double levels, final ScalingConfig config) {
		if (levels <= 0.0) {
			return 0.0;
		}
		double mid = config.tailMidpoint();
		double width = config.tailWidth();
		double low = sigma(-mid / width);
		double high = sigma((config.tailEnd() - mid) / width);
		return Math.min(1.0, (sigma((levels - mid) / width) - low) / (high - low));
	}

	private static double sigma(final double x) {
		return 1.0 / (1.0 + Math.exp(-x));
	}

	/** Fresh spawns and deliberate rank promotions heal to their scaled maximum. */
	public static void apply(final Mob mob) {
		apply(mob, false);
	}

	/** Saved mobs keep their health percentage while old modifiers migrate. */
	public static void reconcileLoaded(final Mob mob) {
		apply(mob, true);
	}

	/** Tags a mob whose level another mod fixed, so it is never fixed twice. */
	public static final String LEVEL_APPLIED_TAG = "mod-stella-mobs.level_applied";
	/** The highest level a caller may fix. A larger value is a mistake, not a level. */
	public static final int MAX_FIXED_LEVEL = 100_000;

	/**
	 * Fixes the threat level of a mob another mod owns and scales it as a natural spawn of that level is: health, and
	 * melee damage when its hits do not already follow the level path. It rolls no elite, boss or variation. Returns
	 * false, and changes nothing, for a bad level, a mob without the no-roll tag, an elite or boss, or a mob already fixed.
	 */
	public static boolean applyFixedLevel(final Mob mob, final int level) {
		if (mob == null || level < 0 || level > MAX_FIXED_LEVEL || !mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)
			|| mob.entityTags().contains(LEVEL_APPLIED_TAG) || EliteState.hasEliteFlag(mob) || EliteState.hasBossFlag(mob)) {
			return false;
		}
		mob.addTag(LEVEL_APPLIED_TAG);
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(level, 0, 0, 0));
		applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, healthMultiplier(level) - 1.0);
		// A self-scaled mob's hits follow the level in the hurt hook already, so its attribute stays as its owner set it.
		if (!SelfScaled.owned(mob)) {
			applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damageMultiplier(level) - 1.0);
		}
		mob.setHealth(mob.getMaxHealth());
		return true;
	}

	/**
	 * Sets the level of a mob another mod owns, again and again as its level moves. The scaling is the one of
	 * {@link #applyFixedLevel}, and a change keeps the health fraction. Returns false, and changes nothing, for a bad
	 * level, a mob without the no-roll tag, a boss, or a mob already at that level.
	 */
	public static boolean setLevel(final Mob mob, final int level) {
		if (mob == null || level < 0 || level > MAX_FIXED_LEVEL || !mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)
			|| EliteState.hasBossFlag(mob)) {
			return false;
		}
		ThreatState target = new ThreatState(level, 0, 0, 0);
		if (target.equals(ThreatState.get(mob))) {
			return false;
		}
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		boolean scaledBefore = health != null && health.hasModifier(HEALTH_ID);
		float fraction = mob.getMaxHealth() <= 0.0F ? 1.0F : mob.getHealth() / mob.getMaxHealth();
		mob.addTag(LEVEL_APPLIED_TAG);
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) mob).setAttached(ThreatState.TYPE, target);
		applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, healthMultiplier(level) - 1.0);
		if (!SelfScaled.owned(mob)) {
			applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damageMultiplier(level) - 1.0);
		}
		float restored = scaledBefore ? Math.max(1.0F, Math.min(mob.getMaxHealth(), fraction * mob.getMaxHealth())) : mob.getMaxHealth();
		mob.setHealth(restored);
		return true;
	}

	private static void apply(final Mob mob, final boolean preserveHealth) {
		if (mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG)) {
			return;
		}
		int threatLevel = ThreatState.level(mob);
		float oldHealth = mob.getHealth();
		float oldMaximum = mob.getMaxHealth();
		boolean removedOldHealth = removeModifier(mob, Attributes.MAX_HEALTH, Bosses.LEVEL_HEALTH_ID);
		removeModifier(mob, Attributes.ATTACK_DAMAGE, Bosses.LEVEL_DAMAGE_ID);
		boolean changedHealth = applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID,
			healthMultiplier(threatLevel) - 1.0);
		applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
			damageMultiplier(threatLevel) - 1.0);
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

}
