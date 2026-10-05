package com.stella.spells;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Endless Tower's growth, reached by name when that mod is installed and a plain 1.0 when it is not. A rune
 * or ascension on the book, scroll or imbued piece a spell is cast from multiplies its power, a rune of the
 * spell's own element resonates with it, and stored Lightning charges shorten its cooldown.
 */
public final class TowerBridge {
	/** What the tower gives a spell: the same three calls its {@code TowerGrowth} makes. */
	public interface Growth {
		double flat(ItemStack focus);

		double resonance(ItemStack focus, String spellId);

		int cooldown(Player caster, int ticks);
	}

	private static final Growth NONE = new Growth() {
		@Override
		public double flat(final ItemStack focus) {
			return 1.0;
		}

		@Override
		public double resonance(final ItemStack focus, final String spellId) {
			return 1.0;
		}

		@Override
		public int cooldown(final Player caster, final int ticks) {
			return ticks;
		}
	};

	private static volatile Growth growth;

	private TowerBridge() {
	}

	/** The test seam: a stand-in for the tower, installed once and inert for whatever it does not mark. */
	static void setGrowth(final Growth stand) {
		growth = stand;
	}

	private static Growth growth() {
		Growth found = growth;
		if (found == null) {
			found = find();
			growth = found;
		}
		return found;
	}

	private static Growth find() {
		try {
			Class<?> tower = Class.forName("com.stella.tower.TowerGrowth", true, TowerBridge.class.getClassLoader());
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle flat = lookup.findStatic(tower, "flat", MethodType.methodType(double.class, ItemStack.class));
			MethodHandle resonance = lookup.findStatic(tower, "resonance", MethodType.methodType(double.class, ItemStack.class, String.class));
			MethodHandle cooldown = lookup.findStatic(tower, "cooldown", MethodType.methodType(int.class, Player.class, int.class));
			return new Growth() {
				@Override
				public double flat(final ItemStack focus) {
					try {
						return (double) flat.invokeExact(focus);
					} catch (Throwable e) {
						return 1.0;
					}
				}

				@Override
				public double resonance(final ItemStack focus, final String spellId) {
					try {
						return (double) resonance.invokeExact(focus, spellId);
					} catch (Throwable e) {
						return 1.0;
					}
				}

				@Override
				public int cooldown(final Player caster, final int ticks) {
					try {
						return (int) cooldown.invokeExact(caster, ticks);
					} catch (Throwable e) {
						return ticks;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	/** A cast's modifiers once the tower's growth on the focus has had its say: power by flat and resonance, durations by resonance. */
	static SpellModifiers.CastModifiers grow(final ItemStack focus, final String spellId, final SpellModifiers.CastModifiers base) {
		Growth tower = growth();
		double flat = tower.flat(focus);
		double resonance = tower.resonance(focus, spellId);
		if (!(flat > 0.0) || !(resonance > 0.0) || (flat == 1.0 && resonance == 1.0)) {
			return base;
		}
		return new SpellModifiers.CastModifiers(
			base.powerMultiplier() * flat * resonance, base.radiusMultiplier(), base.durationMultiplier() * resonance,
			base.defensiveDurationMultiplier() * resonance, base.targetRangeBlocks(), base.cooldownTicks(),
			base.awakened()
		);
	}

	/** A cast's cooldown once the caster's stored Lightning charges have shortened it, which spends them. */
	static int cooldown(final Player caster, final int ticks) {
		return Math.max(1, growth().cooldown(caster, ticks));
	}
}
