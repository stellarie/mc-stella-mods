package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a loot table gives in the open raid nether, from mobs, blocks and chests alike, comes more
 * plentifully: {@link #NORMAL_MULTIPLIER} in normal and {@link #HARD_MULTIPLIER} in hard. The fraction past a
 * whole count is rolled, so a single drop at x1.15 becomes two about one time in seven.
 */
public final class RaidLoot {
	public static final double NORMAL_MULTIPLIER = 1.15;
	public static final double HARD_MULTIPLIER = 1.5;

	private RaidLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ServerLevel level = context.getLevel();
			if (!level.dimension().equals(RaidTravel.RAID)) {
				return;
			}
			// The loot belongs to the raid where it fell.
			net.minecraft.world.phys.Vec3 origin = context.getOptional(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
			if (origin == null) {
				return;
			}
			RaidState state = RaidInstance.stateAt(level, net.minecraft.core.BlockPos.containing(origin));
			if (!state.active()) {
				return;
			}
			multiply(drops, state.mode() == RaidMode.HARD ? HARD_MULTIPLIER : NORMAL_MULTIPLIER, context.getRandom());
		});
	}

	static void multiply(final List<ItemStack> drops, final double multiplier, final RandomSource random) {
		List<ItemStack> extra = new ArrayList<>();
		for (ItemStack stack : drops) {
			if (stack.isEmpty()) {
				continue;
			}
			double scaled = stack.getCount() * multiplier;
			int count = (int) Math.floor(scaled);
			if (random.nextDouble() < scaled - count) {
				count++;
			}
			// Past a full stack, the rest follows as further stacks.
			int max = stack.getMaxStackSize();
			stack.setCount(Math.min(count, max));
			for (int left = count - max; left > 0; left -= max) {
				extra.add(stack.copyWithCount(Math.min(left, max)));
			}
		}
		drops.addAll(extra);
	}
}
