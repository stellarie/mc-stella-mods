package com.stella.spells;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
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
	public static final String HEALTH_SCALE_SHARE = "mod-stella-rpg:health-scale";
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

		/** What the caster's Intelligence multiplies every spell effect duration by; 1 when the RPG mod gives none. */
		default double durationMultiplier(final LivingEntity caster) {
			return 1.0;
		}

		/** The lowest MATK roll in K units; NaN when the RPG mod gives none. */
		default double matkMin(final LivingEntity caster) {
			return Double.NaN;
		}

		/** The highest MATK roll in K units; NaN when the RPG mod gives none. */
		default double matkMax(final LivingEntity caster) {
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
			MethodHandle matkMin = optionalMatk(lookup, rpg, "matkMin");
			MethodHandle matkMax = optionalMatk(lookup, rpg, "matkMax");
			MethodHandle duration = optionalMatk(lookup, rpg, "durationMultiplier");
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

				@Override
				public double durationMultiplier(final LivingEntity caster) {
					try {
						return duration == null ? 1.0 : (double) duration.invokeExact(caster);
					} catch (Throwable e) {
						return 1.0;
					}
				}

				@Override
				public double matkMin(final LivingEntity caster) {
					try {
						return matkMin == null ? Double.NaN : (double) matkMin.invokeExact(caster);
					} catch (Throwable e) {
						return Double.NaN;
					}
				}

				@Override
				public double matkMax(final LivingEntity caster) {
					try {
						return matkMax == null ? Double.NaN : (double) matkMax.invokeExact(caster);
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

	private static MethodHandle optionalMatk(final MethodHandles.Lookup lookup, final Class<?> rpg, final String name) {
		try {
			return lookup.findStatic(rpg, name, MethodType.methodType(double.class, LivingEntity.class));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Whether the RPG mod, or a stand-in for it, is answering. */
	static boolean present() {
		return stats() != NONE;
	}

	static SpellModifiers.CastModifiers grow(final Player caster, final SpellModifiers.CastModifiers base) {
		return grow(caster, "", base);
	}

	/** A cast's modifiers once the caster's Intelligence has had its say: power by its multiplier, cooldown by its own. */
	static SpellModifiers.CastModifiers grow(final Player caster, final String spellId, final SpellModifiers.CastModifiers base) {
		Stats rpg = stats();
		double power = rpg.power(caster);
		// Fixed-cooldown spells keep their base cooldown.
		double cooldown = SpellbookCatalog.FIXED_COOLDOWN.contains(spellId) ? 1.0 : rpg.cooldown(caster);
		double duration = durationMultiplier(caster);
		if (!positive(power) || !positive(cooldown) || (power == 1.0 && cooldown == 1.0 && duration == 1.0)) {
			return base;
		}
		// Durations only: the cooldown keeps its own multiplier.
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * power, base.radiusMultiplier(), base.durationMultiplier() * duration,
			base.defensiveDurationMultiplier() * duration, base.targetRangeBlocks(), Math.max(20, (int) Math.round(base.cooldownTicks() * cooldown)),
			base.awakened()
		);
	}

	/** The caster's duration multiplier from the RPG mod; 1 when it gives none or a number that is not usable. */
	static double durationMultiplier(final LivingEntity caster) {
		double multiplier = stats().durationMultiplier(caster);
		return positive(multiplier) ? multiplier : 1.0;
	}

	/** The lowest MATK roll of the caster in K units, or NaN when the RPG mod gives none. */
	static double matkMin(final LivingEntity caster) {
		return stats().matkMin(caster);
	}

	/** The highest MATK roll of the caster in K units, or NaN when the RPG mod gives none. */
	static double matkMax(final LivingEntity caster) {
		return stats().matkMax(caster);
	}

	/** The RPG health scale K, read on each call so a late share counts; 1 when the RPG mod is absent. */
	static double k() {
		Object shared = FabricLoader.getInstance().getObjectShare().get(HEALTH_SCALE_SHARE);
		return shared instanceof Double scale && positive(scale) ? scale : 1.0;
	}

	/** Real health points as the vanilla number that {@code heal} or {@code hurtServer} scales back by K. */
	static float vanilla(final float realHealth) {
		return (float) (realHealth / k());
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
