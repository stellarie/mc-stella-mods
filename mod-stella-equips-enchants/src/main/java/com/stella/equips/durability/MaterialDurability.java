package com.stella.equips.durability;

import com.stella.equips.guardian.SkeletonCrown;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Wear by material. A durability point of a vanilla tool, weapon or armor piece is charged with a chance that
 * falls as the material gets better. Unbreaking rolls after this chance, on the points that were charged.
 * Materials that are not in the table, and every mod item, keep the full chance.
 */
public final class MaterialDurability {
	public static final double NETHERITE = 0.10;
	/** Vanilla id prefix of a material to the chance that one point of wear is charged. */
	private static final Map<String, Double> BY_PREFIX = new LinkedHashMap<>();

	static {
		BY_PREFIX.put("leather_", 1.0);
		BY_PREFIX.put("wooden_", 1.0);
		BY_PREFIX.put("golden_", 0.80);
		BY_PREFIX.put("stone_", 0.60);
		BY_PREFIX.put("chainmail_", 0.60);
		BY_PREFIX.put("iron_", 0.40);
		BY_PREFIX.put("diamond_", 0.20);
		BY_PREFIX.put("netherite_", NETHERITE);
	}

	private MaterialDurability() {
	}

	/** The chance that one point of wear is charged to the stack. */
	public static double chance(final ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return 1.0;
		}
		if (SkeletonCrown.is(stack)) {
			return NETHERITE;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
			return 1.0;
		}
		for (Map.Entry<String, Double> entry : BY_PREFIX.entrySet()) {
			if (id.getPath().startsWith(entry.getKey())) {
				return entry.getValue();
			}
		}
		return 1.0;
	}

	/** The points of wear left of {@code points} after each one is rolled against the chance of the material. */
	public static int charge(final ItemStack stack, final int points, final RandomSource random) {
		if (points <= 0) {
			return points;
		}
		double chance = chance(stack);
		if (chance >= 1.0) {
			return points;
		}
		int charged = 0;
		for (int i = 0; i < points; i++) {
			if (random.nextDouble() < chance) {
				charged++;
			}
		}
		return charged;
	}
}
