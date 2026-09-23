package com.stella.mobs.behavior;

import com.stella.mobs.scaling.SpawnScaling;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

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
			// and with R12 rather than replacing either. That arithmetic has been wrong twice already.
			double current = SpawnScaling.healthMultiplierOf(mob);
			applyModifier(
				mob, Attributes.MAX_HEALTH, HARDENED_HEALTH_ID, HARDENED_HEALTH_MULTIPLIER * current - current
			);
		}

		FasterMeleeGoal.install(mob, HARDENED_ATTACK_FACTOR);
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
}
