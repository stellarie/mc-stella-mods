package com.stella.mobs.scaling;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Traits;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** One durable threat roll sets health and damage. Depth keeps its prior contribution. */
public final class SpawnScaling {
	public static final int BLOCKS_PER_LEVEL = 500;
	public static final double DEPTH_CAP = 256.0;
	public static final double HEALTH_DEPTH_WEIGHT = 0.25;
	public static final double DAMAGE_DEPTH_WEIGHT = 0.125;
	public static final double LEVEL_HEALTH_BONUS = 0.05;
	public static final double LEVEL_DAMAGE_BONUS = 0.025;
	public static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_health");
	public static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "spawn_scale_damage");

	private SpawnScaling() {
	}

	public static int areaLevel(final Mob mob) {
		return (int) Math.floor(distanceFromSpawn(mob) / BLOCKS_PER_LEVEL);
	}

	public static double healthMultiplier(final int threatLevel, final double depth) {
		return 1.0 + Math.max(0, threatLevel) * LEVEL_HEALTH_BONUS
			+ depthShare(depth) * HEALTH_DEPTH_WEIGHT;
	}

	public static double damageMultiplier(final int threatLevel, final double depth) {
		return 1.0 + Math.max(0, threatLevel) * LEVEL_DAMAGE_BONUS
			+ depthShare(depth) * DAMAGE_DEPTH_WEIGHT;
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
		double depth = Math.max(0.0, mob.level().getSeaLevel() - mob.getY());
		float oldHealth = mob.getHealth();
		float oldMaximum = mob.getMaxHealth();
		boolean removedOldHealth = removeModifier(mob, Attributes.MAX_HEALTH, Bosses.LEVEL_HEALTH_ID);
		removeModifier(mob, Attributes.ATTACK_DAMAGE, Bosses.LEVEL_DAMAGE_ID);
		boolean changedHealth = applyModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID,
			healthMultiplier(threatLevel, depth) - 1.0);
		applyModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
			damageMultiplier(threatLevel, depth) - 1.0);
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

	private static double distanceFromSpawn(final Mob mob) {
		var dimension = mob.level().dimension();
		var respawn = mob.level().getRespawnData();
		double originX;
		double originZ;
		if (dimension.equals(respawn.dimension())) {
			originX = respawn.pos().getX();
			originZ = respawn.pos().getZ();
		} else if (dimension.equals(Level.NETHER)) {
			originX = respawn.pos().getX() / 8.0;
			originZ = respawn.pos().getZ() / 8.0;
		} else if (dimension.equals(Level.END)) {
			originX = 0.0;
			originZ = 0.0;
		} else {
			return 0.0;
		}
		double dx = mob.getX() - originX;
		double dz = mob.getZ() - originZ;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double depthShare(final double depth) {
		return Math.min(1.0, Math.max(0.0, depth) / DEPTH_CAP);
	}
}
