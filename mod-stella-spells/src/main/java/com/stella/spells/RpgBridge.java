package com.stella.spells;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The caster's Intelligence from the RPG mod, reached by name when that mod is installed and a plain 1.0 when it
 * is not. It shortens a player spell's cooldown, and its MATK sets what a damage spell or a heal is worth. A
 * result of NaN means the RPG mod gave no number, and the spell keeps its own flat one.
 */
public final class RpgBridge {
	/** A vanilla flat damage of 1 is this many percent of MATK. */
	public static final double SKILL_PERCENT_PER_FLAT = 50.0;
	/** A vanilla flat heal of 1 is this heal coefficient. */
	public static final double HEAL_COEFFICIENT_PER_FLAT = 0.5;
	static final String RPG_SPELLS_CLASS = "com.stella.rpg.RpgSpells";

	/** What the RPG mod gives a spell: the calls its {@code RpgSpells} makes. */
	public interface Stats {
		double power(Player player);

		double cooldown(Player player);

		/** Vanilla-unit damage for a skill percent, 150 being 150%; NaN when the RPG mod gives none. */
		default double spellDamage(final LivingEntity caster, final double skillPercent) {
			return Double.NaN;
		}

		/** Vanilla-unit heal for a coefficient; NaN when the RPG mod gives none. */
		default double spellHeal(final LivingEntity caster, final double coefficient) {
			return Double.NaN;
		}
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

	static Stats find() {
		return find(RPG_SPELLS_CLASS);
	}

	static Stats find(final String className) {
		try {
			Class<?> rpg = Class.forName(className, true, RpgBridge.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle power = lookup.findStatic(rpg, "power", MethodType.methodType(double.class, Player.class));
			MethodHandle cooldown = lookup.findStatic(rpg, "cooldown", MethodType.methodType(double.class, Player.class));
			MethodHandle damage = optional(lookup, rpg, "spellDamage");
			MethodHandle heal = optional(lookup, rpg, "spellHeal");
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

				@Override
				public double spellDamage(final LivingEntity caster, final double skillPercent) {
					try {
						return damage == null ? Double.NaN : (double) damage.invokeExact(caster, skillPercent);
					} catch (Throwable e) {
						return Double.NaN;
					}
				}

				@Override
				public double spellHeal(final LivingEntity caster, final double coefficient) {
					try {
						return heal == null ? Double.NaN : (double) heal.invokeExact(caster, coefficient);
					} catch (Throwable e) {
						return Double.NaN;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	// An older RPG mod may lack it: null is fine.
	private static MethodHandle optional(final MethodHandles.Lookup lookup, final Class<?> rpg, final String name) {
		try {
			return lookup.findStatic(rpg, name, MethodType.methodType(double.class, LivingEntity.class, double.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Whether the RPG mod, or a stand-in for it, is answering. */
	static boolean present() {
		return stats() != NONE;
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

	/** The RPG mod's own damage for a skill percent, or NaN when it gives none. */
	static double spellDamage(final LivingEntity caster, final double skillPercent) {
		return stats().spellDamage(caster, skillPercent);
	}

	/** A damage spell's hit: the caster's MATK at the flat number x 50 percent, else the flat number; times the cast's power once. */
	static float damage(final LivingEntity caster, final float flat, final double power) {
		return (float) (usable(stats().spellDamage(caster, flat * SKILL_PERCENT_PER_FLAT), flat) * power);
	}

	/** A heal: the caster's level and Intelligence at half the flat number, else the flat number; times the cast's power once. */
	static float heal(final LivingEntity caster, final float flat, final double power) {
		return (float) (usable(stats().spellHeal(caster, flat * HEAL_COEFFICIENT_PER_FLAT), flat) * power);
	}

	private static double usable(final double fromRpg, final double flat) {
		return Double.isFinite(fromRpg) && fromRpg >= 0.0 ? fromRpg : flat;
	}

	private static boolean positive(final double value) {
		return value > 0.0 && Double.isFinite(value);
	}
}
