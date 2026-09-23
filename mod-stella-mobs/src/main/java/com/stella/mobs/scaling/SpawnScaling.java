package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * R12: a mob grows tougher with its distance from the world spawn and, more slowly, with its depth.
 * Two permanent modifiers carry it, so the numbers survive a save and load, and a raised maximum is
 * always paired with a heal because a raised maximum does not heal on its own.
 */
public final class SpawnScaling {
	public static final double DISTANCE_CAP = 1024.0;
	public static final double DEPTH_CAP = 256.0;
	public static final double HEALTH_DISTANCE_WEIGHT = 0.5;
	public static final double HEALTH_DEPTH_WEIGHT = 0.25;
	public static final double DAMAGE_DISTANCE_WEIGHT = 0.25;
	public static final double DAMAGE_DEPTH_WEIGHT = 0.125;
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_health");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_damage");
	public static final int LEVEL_DISTANCE_POINTS = 10;
	public static final int LEVEL_DEPTH_POINTS = 5;
	/** R12's own distance weight divided by its own level count, so ten levels are exactly that weight. */
	public static final double LEVEL_HEALTH_BONUS = HEALTH_DISTANCE_WEIGHT / LEVEL_DISTANCE_POINTS;
	public static final double LEVEL_DAMAGE_BONUS = DAMAGE_DISTANCE_WEIGHT / LEVEL_DISTANCE_POINTS;
	// A scaled boss breaks an encounter budget, so the four mobs with a scripted fight stay vanilla.
	private static final Set<EntityType<?>> EXEMPT = Set.of(
		EntityTypes.WARDEN, EntityTypes.WITHER, EntityTypes.ELDER_GUARDIAN, EntityTypes.ENDER_DRAGON
	);

	private SpawnScaling() {
	}

	public static double healthMultiplier(final double distance, final double depth) {
		return 1.0 + share(distance, DISTANCE_CAP) * HEALTH_DISTANCE_WEIGHT
			+ share(depth, DEPTH_CAP) * HEALTH_DEPTH_WEIGHT;
	}

	public static double damageMultiplier(final double distance, final double depth) {
		return 1.0 + share(distance, DISTANCE_CAP) * DAMAGE_DISTANCE_WEIGHT
			+ share(depth, DEPTH_CAP) * DAMAGE_DEPTH_WEIGHT;
	}

	/**
	 * The area level a boss is measured against, 0 to 15, derived from the same shares R12 scales by so
	 * the two systems cannot disagree about how far out a mob is.
	 */
	public static int areaLevel(final Mob mob) {
		double distance = distanceFromSpawn(mob);
		double depth = Math.max(0.0, mob.level().getSeaLevel() - mob.getY());
		return (int) Math.floor(
			share(distance, DISTANCE_CAP) * LEVEL_DISTANCE_POINTS + share(depth, DEPTH_CAP) * LEVEL_DEPTH_POINTS
		);
	}

	/** Applies both multipliers once. A second call replaces rather than stacks, so a reload is safe. */
	public static void apply(final Mob mob) {
		if (EXEMPT.contains(mob.getType())) {
			return;
		}

		double distance = distanceFromSpawn(mob);
		double depth = Math.max(0.0, mob.level().getSeaLevel() - mob.getY());

		if (!applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, healthMultiplier(distance, depth) - 1.0)) {
			return;
		}

		applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damageMultiplier(distance, depth) - 1.0);
		// A raised maximum does not heal, and the mob was built at its old maximum.
		mob.setHealth(mob.getMaxHealth());
	}

	private static boolean applyModifier(
		final LivingEntity mob, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return false;
		}

		AttributeModifier modifier = instance.getModifier(id);
		if (modifier != null) {
			if (modifier.amount() == amount) {
				return true;
			}

			instance.removeModifier(id);
		}

		instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		return true;
	}

	/** The health multiplier this mob already carries, or 1.0 when the spawn path never scaled it. */
	public static double healthMultiplierOf(final LivingEntity mob) {
		AttributeInstance instance = mob.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return 1.0;
		}

		AttributeModifier modifier = instance.getModifier(HEALTH_ID);
		return modifier == null ? 1.0 : 1.0 + modifier.amount();
	}

	private static double distanceFromSpawn(final Mob mob) {		if (!mob.level().dimension().equals(mob.level().getRespawnData().dimension())) {
			return 0.0;
		}

		double dx = mob.getX() - mob.level().getRespawnData().pos().getX();
		double dz = mob.getZ() - mob.level().getRespawnData().pos().getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double share(final double value, final double cap) {
		return Math.min(1.0, Math.max(0.0, value) / cap);
	}
}
