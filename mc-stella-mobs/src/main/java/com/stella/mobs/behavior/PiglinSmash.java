package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

/**
 * R24 for the elite piglin brute: a smash of about six times vanilla's shove, and a permanent thirty
 * percent of base movement. The shove rides vanilla's own knockback, which is where the grounded pop
 * ceiling of 0.4 already lives, so the rule is the power it is handed and nothing else. The movement is an
 * attribute rather than an effect, because an attribute is saved with the entity and milk cannot clear it.
 */
public final class PiglinSmash {
	/** Vanilla's own melee shove, the number its knockback callers pass. */
	public static final double VANILLA_PUSH = 0.4;
	public static final double SMASH_MULTIPLIER = 6.0;
	public static final double SMASH_POWER = VANILLA_PUSH * SMASH_MULTIPLIER;
	/** Vanilla's grounded pop ceiling, named here so a test can assert it without reading the source. */
	public static final double POP_CEILING = 0.4;
	public static final double SPEED_BONUS = 0.30;
	public static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "brute_smash_speed");

	private PiglinSmash() {
	}

	/** Movement only: a brute is brain driven, so an attack speed change would need a mixin. */
	public static void install(final Mob mob) {
		if (!(mob instanceof PiglinBrute brute) || !Elite.isElite(brute)) {
			return;
		}

		AttributeInstance speed = brute.getAttribute(Attributes.MOVEMENT_SPEED);

		if (speed != null && !speed.hasModifier(SPEED_ID)) {
			speed.addPermanentModifier(
				new AttributeModifier(SPEED_ID, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
			);
		}
	}

	/**
	 * The smash. Vanilla's knockback subtracts the vector it is handed, so the vector points from the victim
	 * back to the brute and the victim leaves along the line from the brute to the victim.
	 */
	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float baseDamageTaken,
		final float damageTaken,
		final boolean blocked
	) {
		if (blocked || !(source.getDirectEntity() instanceof PiglinBrute brute) || !Elite.isElite(brute)) {
			return;
		}

		double xd = brute.getX() - entity.getX();
		double zd = brute.getZ() - entity.getZ();
		entity.knockback(SMASH_POWER, xd, zd, source, damageTaken);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=bruteSmash t={} SMASH power={} victim={} delta={} grounded={}",
			brute.level().getGameTime(), SMASH_POWER, entity.getType(), entity.getDeltaMovement(), entity.onGround()
		);
	}
}
