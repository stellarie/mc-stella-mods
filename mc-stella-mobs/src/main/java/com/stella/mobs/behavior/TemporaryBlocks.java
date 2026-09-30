package com.stella.mobs.behavior;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks a mob casts for a while, a web or a patch of powder snow, which clear themselves when their time
 * is up so a fight never litters the world. A block someone has replaced since is left alone. A block that
 * is still there when the server stops stays, because the list is not saved.
 */
public final class TemporaryBlocks {
	private record Placed(ServerLevel level, BlockPos pos, Block block, int until) {
	}

	private static final List<Placed> PLACED = new ArrayList<>();
	/** The test seam: spots a test declares unloaded, so a chunk's absence can be shown without unloading one. */
	private static final Set<BlockPos> UNLOADED_FOR_TEST = ConcurrentHashMap.newKeySet();

	private TemporaryBlocks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TemporaryBlocks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			synchronized (PLACED) {
				PLACED.clear();
			}
		});
	}

	/** Places the block when the spot is free, and schedules it to clear; false when nothing was placed. */
	public static boolean place(final ServerLevel level, final BlockPos pos, final BlockState state, final int lifetimeTicks) {
		if (!level.getBlockState(pos).canBeReplaced() || level.getBlockState(pos).is(state.getBlock())
			|| !level.setBlockAndUpdate(pos, state)) {
			return false;
		}
		synchronized (PLACED) {
			PLACED.add(new Placed(level, pos.immutable(), state.getBlock(), level.getServer().getTickCount() + lifetimeTicks));
		}
		return true;
	}

	private static boolean loaded(final Placed placed) {
		return !UNLOADED_FOR_TEST.contains(placed.pos()) && placed.level().isLoaded(placed.pos());
	}

	/** The test seam: treats a spot as unloaded, or as loaded again when {@code unloaded} is false. */
	public static void pinUnloaded(final BlockPos pos, final boolean unloaded) {
		if (unloaded) {
			UNLOADED_FOR_TEST.add(pos.immutable());
		} else {
			UNLOADED_FOR_TEST.remove(pos);
		}
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		synchronized (PLACED) {
			Iterator<Placed> it = PLACED.iterator();
			while (it.hasNext()) {
				Placed placed = it.next();
				// A block whose chunk is away waits for the chunk to come back, so it is never left standing.
				if (now < placed.until() || !loaded(placed)) {
					continue;
				}
				it.remove();
				if (placed.level().getBlockState(placed.pos()).is(placed.block())) {
					placed.level().setBlockAndUpdate(placed.pos(), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}
}
