package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * T03 to T06: the four location traits. Each is taken once, at spawn, from where the mob appeared and
 * which dimension it appeared in, and recorded durably, so a reload can neither lose one nor roll it a
 * second time. Every number is a named constant, so a test can force a chance to certainty instead of
 * hoping for it.
 */
public final class Traits {
	/** T03: a mob spawned below this height is hardened. */
	public static final int HARDENED_BELOW_Y = 0;
	/** T04: a mob spawned above this height is windy. */
	public static final int WINDY_ABOVE_Y = 128;
	public static final double HARDENED_SPEED_BONUS = 0.10;
	public static final double HARDENED_HEALTH_MULTIPLIER = 1.1;
	/** The hardened melee cadence: a tenth faster is the vanilla interval divided by this. */
	public static final double HARDENED_ATTACK_FACTOR = 1.10;
	public static final Identifier HARDENED_SPEED_ID =
		Identifier.fromNamespaceAndPath("mod-stella-mobs", "trait_hardened_speed");
	public static final Identifier HARDENED_HEALTH_ID =
		Identifier.fromNamespaceAndPath("mod-stella-mobs", "trait_hardened_health");

	/** T04: the windy shove, smaller than the brute's smash, along the line from the attacker. */
	public static final double WINDY_PUSH = 0.2;
	/** T05: the heat ignites for this many seconds. */
	public static final float HEAT_BURN_SECONDS = 4.0F;
	/** The chance a heat mob ignites what it hits. Named, so a test pins it to certainty. */
	public static final double HEAT_CHANCE = 0.25;
	/** T06: the chance an end mob negates a hit entirely, shown by the teleport tell. */
	public static final double END_NEGATE_CHANCE = 0.25;
	/** T06: the tell is the enderman teleport sound and the portal particles, at the mob. */
	public static final float END_TELL_VOLUME = 1.0F;
	public static final int END_TELL_PARTICLES = 64;
	public static final double END_TELL_SPREAD = 0.5;
	private static double heatRoll = HEAT_CHANCE;
	private static double endRoll = END_NEGATE_CHANCE;
	private static long endTellCount;
	private static @Nullable SoundEvent lastEndTellSound;
	private static @Nullable ParticleOptions lastEndTellParticle;
	private static double lastEndTellX;
	private static double lastEndTellY;
	private static double lastEndTellZ;

	/**
	 * Pins the heat chance, so a chance clause is never reached by luck. A test sets this and restores the
	 * shipped value in a finally, which is the same shape the global config store already uses.
	 */
	public static void pinHeatRoll(final double chance) {
		heatRoll = chance;
	}

	public static double heatRoll() {
		return heatRoll;
	}

	/** Pins the end negation chance, the same shape as the heat pin. */
	public static void pinEndRoll(final double chance) {
		endRoll = chance;
	}

	public static double endRoll() {
		return endRoll;
	}

	/** The tells the negation has played, as a record, so a test reads what the ability produced. */
	public static long endTellCount() {
		return endTellCount;
	}

	public static @Nullable SoundEvent lastEndTellSound() {
		return lastEndTellSound;
	}

	public static @Nullable ParticleOptions lastEndTellParticle() {
		return lastEndTellParticle;
	}

	public static double lastEndTellX() {
		return lastEndTellX;
	}

	public static double lastEndTellY() {
		return lastEndTellY;
	}

	public static double lastEndTellZ() {
		return lastEndTellZ;
	}

	private Traits() {
	}

	/** The trait names a mob takes at this position, in this dimension. Two conditions can overlap. */
	public static List<String> at(final Mob mob) {
		List<String> taken = new ArrayList<>();

		if (mob.getY() < HARDENED_BELOW_Y) {
			taken.add("hardened");
		}

		if (mob.getY() > WINDY_ABOVE_Y) {
			taken.add("windy");
		}

		if (mob.level().dimension().equals(Level.NETHER)) {
			taken.add("heat");
		}

		if (mob.level().dimension().equals(Level.END)) {
			taken.add("end");
		}

		return List.copyOf(taken);
	}

	/**
	 * Applies the traits once, at spawn. The recorded names are the guard: a mob that already carries
	 * them is left alone, so a reload cannot stack the modifiers, and the read is a set so a trait the
	 * mob's own dimension gave it is not applied twice.
	 */
	public static void apply(final Mob mob) {
		List<String> already = EliteState.traits(mob);

		if (!already.isEmpty()) {
			return;
		}

		List<String> taken = at(mob);

		if (taken.isEmpty()) {
			return;
		}

		EliteState.setTraits(mob, taken);

		if (taken.contains("hardened")) {
			harden(mob);
		}
	}

	/** T03: movement and attack speed at a tenth faster, and health at 1.1 times what it already carries. */
	private static void harden(final Mob mob) {
		applyModifier(mob, Attributes.MOVEMENT_SPEED, HARDENED_SPEED_ID, HARDENED_SPEED_BONUS);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			// The multiplier is read off what the mob already carries, so it stacks with the elite double
			// and with threat scaling rather than replacing either.
			double current = SpawnScaling.healthMultiplierOf(mob);
			applyModifier(
				mob, Attributes.MAX_HEALTH, HARDENED_HEALTH_ID, HARDENED_HEALTH_MULTIPLIER * current - current
			);
		}

		FasterMeleeGoal.install(mob, HARDENED_ATTACK_FACTOR);
	}

	/** Recomputes a saved hardened modifier after threat scaling or rank changes. */
	public static boolean refreshScaling(final Mob mob) {
		if (!has(mob, "hardened")) {
			return false;
		}
		double scaled = SpawnScaling.healthMultiplierOf(mob);
		double amount = HARDENED_HEALTH_MULTIPLIER * scaled - scaled;
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return false;
		}
		AttributeModifier current = health.getModifier(HARDENED_HEALTH_ID);
		if (current != null && current.amount() == amount) {
			return false;
		}
		applyModifier(mob, Attributes.MAX_HEALTH, HARDENED_HEALTH_ID, amount);
		return true;
	}

	private static void applyModifier(
		final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double amount
	) {
		AttributeInstance instance = mob.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		if (instance.getModifier(id) != null) {
			instance.removeModifier(id);
		}

		instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	/** Whether this mob took the named trait at spawn. */
	public static boolean has(final Mob mob, final String trait) {
		return EliteState.traits(mob).contains(trait);
	}

	/**
	 * T05: the heat trait sets what it hits alight. The roll is a named constant a test pins, so the
	 * clause is reached by a pinned certainty rather than by luck, and the fire is read as fire ticks.
	 */
	public static void afterDamage(
		final LivingEntity entity,
		final DamageSource source,
		final float baseDamageTaken,
		final float damageTaken,
		final boolean blocked
	) {
		if (blocked || !(source.getEntity() instanceof Mob hitter)) {
			return;
		}

		if (hitter == entity) {
			return;
		}

		if (has(hitter, "windy")) {
			windyPush(entity, source, damageTaken, hitter);
		}

		if (has(hitter, "heat") && entity.getRandom().nextDouble() < heatRoll) {
			entity.igniteForSeconds(HEAT_BURN_SECONDS);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=traitHeat t={} IGNITE seconds={} victim={} fireTicks={}",
				hitter.level().getGameTime(), HEAT_BURN_SECONDS, entity.getType(), entity.getRemainingFireTicks()
			);
		}
	}

	private static void windyPush(
		final LivingEntity entity, final DamageSource source, final float damageTaken, final Mob hitter
	) {
		double xd = hitter.getX() - entity.getX();
		double zd = hitter.getZ() - entity.getZ();
		entity.knockback(WINDY_PUSH, xd, zd, source, damageTaken);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=traitWindy t={} PUSH power={} victim={} delta={} grounded={}",
			hitter.level().getGameTime(), WINDY_PUSH, entity.getType(), entity.getDeltaMovement(), entity.onGround()
		);
	}

	/**
	 * T06: a mob that took the end trait can negate a hit. The roll is a named constant a test pins to
	 * certainty or to never, so the clause is not reached by luck. A negation plays the tell: the enderman
	 * teleport sound and the portal particles at the mob, and the tell is recorded so a clause reads it.
	 */
	public static boolean allowDamage(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof Mob mob) || !has(mob, "end")) {
			return true;
		}

		if (mob.getRandom().nextDouble() >= endRoll) {
			return true;
		}

		tell(mob);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=traitEnd t={} NEGATE amount={} roll={} sound={} particle={} at={}/{}/{}",
			mob.level().getGameTime(), amount, endRoll, SoundEvents.ENDERMAN_TELEPORT.location(), ParticleTypes.PORTAL,
			mob.getX(), mob.getY(), mob.getZ()
		);
		return false;
	}

	/** The tell itself: the sound first, then the particles, then the record of what was played. */
	private static void tell(final Mob mob) {
		SoundEvent sound = SoundEvents.ENDERMAN_TELEPORT;
		ParticleOptions particle = ParticleTypes.PORTAL;
		double x = mob.getX();
		double y = mob.getY() + mob.getBbHeight() * 0.5;
		double z = mob.getZ();
		mob.level().playSound(null, x, mob.getY(), z, sound, SoundSource.HOSTILE, END_TELL_VOLUME, 1.0F);

		if (mob.level() instanceof ServerLevel server) {
			server.sendParticles(
				particle, x, y, z, END_TELL_PARTICLES, END_TELL_SPREAD, END_TELL_SPREAD, END_TELL_SPREAD, 0.0
			);
		}

		endTellCount++;
		lastEndTellSound = sound;
		lastEndTellParticle = particle;
		lastEndTellX = x;
		lastEndTellY = y;
		lastEndTellZ = z;
	}
}
