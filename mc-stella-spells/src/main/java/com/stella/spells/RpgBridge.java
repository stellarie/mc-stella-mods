package com.stella.spells;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.player.Player;

/**
 * The caster's Intelligence from the RPG mod, reached by name when that mod is installed and a plain 1.0 when it
 * is not. It multiplies a player spell's power and shortens its cooldown.
 */
public final class RpgBridge {
	/** What the RPG mod gives a spell: the same two calls its {@code RpgSpells} makes. */
	public interface Stats {
		double power(Player player);

		double cooldown(Player player);
	}

	private static final Stats NONE = new Stats() {
		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}
	};

	private static volatile Stats stats;

	private RpgBridge() {
	}

	/** The test seam: a stand-in for the RPG mod; null restores the lookup. */
	static void setStats(final Stats stub) {
		stats = stub;
	}

	private static Stats stats() {
		Stats found = stats;
		if (found == null) {
			found = find();
			stats = found;
		}
		return found;
	}

	private static Stats find() {
		try {
			Class<?> rpg = Class.forName("com.stella.rpg.RpgSpells", true, RpgBridge.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle power = lookup.findStatic(rpg, "power", MethodType.methodType(double.class, Player.class));
			MethodHandle cooldown = lookup.findStatic(rpg, "cooldown", MethodType.methodType(double.class, Player.class));
			return new Stats() {
				@Override
				public double power(final Player player) {
					try {
						return (double) power.invokeExact(player);
					} catch (Throwable e) {
						return 1.0;
					}
				}

				@Override
				public double cooldown(final Player player) {
					try {
						return (double) cooldown.invokeExact(player);
					} catch (Throwable e) {
						return 1.0;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	/** A cast's modifiers once the caster's Intelligence has had its say: power by its multiplier, cooldown by its own. */
	static SpellModifiers.CastModifiers grow(final Player caster, final SpellModifiers.CastModifiers base) {
		Stats rpg = stats();
		double power = rpg.power(caster);
		double cooldown = rpg.cooldown(caster);
		if (!positive(power) || !positive(cooldown) || (power == 1.0 && cooldown == 1.0)) {
			return base;
		}
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * power, base.radiusMultiplier(), base.durationMultiplier(),
			base.defensiveDurationMultiplier(), base.targetRangeBlocks(), Math.max(20, (int) Math.round(base.cooldownTicks() * cooldown))
		);
	}

	private static boolean positive(final double value) {
		return value > 0.0 && Double.isFinite(value);
	}
}
