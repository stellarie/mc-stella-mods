package com.stella.equips.enchant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.item.ItemStack;

/**
 * The Endless Tower's growth, reached by name when that mod is installed and a plain 1.0 when it is not. A rune
 * or ascension on a piece multiplies the flat numbers of the enchantments written in code, and a rune of an
 * enchantment's own element resonates with it. Enchantments that data files define keep their level.
 */
public final class TowerBridge {
	/** What the tower gives an enchantment: the same two calls its {@code TowerGrowth} makes. */
	public interface Growth {
		double flat(ItemStack piece);

		double resonance(ItemStack piece, String enchantment);
	}

	private static final Growth NONE = new Growth() {
		@Override
		public double flat(final ItemStack piece) {
			return 1.0;
		}

		@Override
		public double resonance(final ItemStack piece, final String enchantment) {
			return 1.0;
		}
	};

	private static volatile Growth growth;

	private TowerBridge() {
	}

	/** The test seam: a stand-in for the tower, installed once and inert for whatever it does not mark. */
	public static void setGrowth(final Growth stand) {
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
			return new Growth() {
				@Override
				public double flat(final ItemStack piece) {
					try {
						return (double) flat.invokeExact(piece);
					} catch (Throwable e) {
						return 1.0;
					}
				}

				@Override
				public double resonance(final ItemStack piece, final String enchantment) {
					try {
						return (double) resonance.invokeExact(piece, enchantment);
					} catch (Throwable e) {
						return 1.0;
					}
				}
			};
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return NONE;
		}
	}

	/** What a piece's growth makes of an enchantment's flat number: it times flat times resonance, never below what it was. */
	public static float grow(final float number, final ItemStack piece, final String enchantment) {
		if (piece.isEmpty()) {
			return number;
		}
		double factor = growth().flat(piece) * growth().resonance(piece, enchantment);
		return factor > 0.0 && Double.isFinite(factor) ? (float) (number * factor) : number;
	}

	/** What a piece's resonance makes of an enchantment's duration in ticks. */
	public static int stretch(final int ticks, final ItemStack piece, final String enchantment) {
		if (piece.isEmpty()) {
			return ticks;
		}
		double resonance = growth().resonance(piece, enchantment);
		return resonance > 0.0 && Double.isFinite(resonance) ? (int) Math.round(ticks * resonance) : ticks;
	}
}
