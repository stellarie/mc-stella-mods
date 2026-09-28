package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What a tower mob pays for its floor: its drops come a thousandth more often per floor, and its experience
 * a hundredth more per floor, wherever it dies.
 */
public final class TowerRewards {
	public static final double DROPS_PER_FLOOR = 0.001;
	public static final double EXPERIENCE_PER_FLOOR = 0.01;

	private TowerRewards() {
	}

	public static void register() {
		// Only a tower mob's own loot grows: the loot a killed mob drops names that mob as its entity.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof LivingEntity mob) {
				TowerMobs.State state = TowerMobs.stateOf(mob);
				if (state != null) {
					scale(drops, dropFactor(state.floor()), context.getRandom());
				}
			}
		});
	}

	public static double dropFactor(final int floor) {
		return 1.0 + DROPS_PER_FLOOR * Math.max(0, floor);
	}

	public static double experienceFactor(final int floor) {
		return 1.0 + EXPERIENCE_PER_FLOOR * Math.max(0, floor);
	}

	/** A tower mob's experience, grown by its floor; any other mob's, as it is. */
	public static int experience(final LivingEntity mob, final int base) {
		TowerMobs.State state = TowerMobs.stateOf(mob);
		return state == null ? base : (int) Math.round(base * experienceFactor(state.floor()));
	}

	/** Grows each dropped stack by the factor; a part of an item is dropped whole by its chance. */
	public static void scale(final List<ItemStack> drops, final double factor, final RandomSource random) {
		if (factor <= 1.0) {
			return;
		}
		List<ItemStack> grown = new ArrayList<>();
		for (ItemStack stack : drops) {
			if (stack.isEmpty()) {
				continue;
			}
			double want = stack.getCount() * factor;
			int count = (int) want;
			if (random.nextDouble() < want - count) {
				count++;
			}
			// Past a full stack, the rest comes as further stacks.
			while (count > 0) {
				int part = Math.min(count, stack.getMaxStackSize());
				grown.add(stack.copyWithCount(part));
				count -= part;
			}
		}
		drops.clear();
		drops.addAll(grown);
	}
}
