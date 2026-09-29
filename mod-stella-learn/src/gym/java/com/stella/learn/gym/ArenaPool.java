package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** All arenas of one gym server, on a grid with 32 blocks between origins, with every chunk they cover forced loaded. */
public final class ArenaPool {
	public static final int SPACING = 32;
	private static final int BASE_Y = 64;

	private final ServerLevel level;
	private final List<Arena> arenas = new ArrayList<>();

	public ArenaPool(final ServerLevel level, final int count) {
		this.level = level;
		int columns = (int) Math.ceil(Math.sqrt(count));
		for (int i = 0; i < count; i++) {
			BlockPos origin = new BlockPos((i % columns) * SPACING, BASE_Y, (i / columns) * SPACING);
			arenas.add(new Arena(i, level, origin));
		}
	}

	public int size() {
		return arenas.size();
	}

	public Arena get(final int index) {
		return arenas.get(index);
	}

	public List<Arena> all() {
		return arenas;
	}

	/** Forces the chunks, then builds each arena. An unforced chunk stops ticking its entities. */
	public void buildAll() {
		long start = System.nanoTime();
		Set<Long> chunks = new HashSet<>();
		for (Arena arena : arenas) {
			BlockPos low = arena.origin().offset(-1, 0, -1);
			BlockPos high = arena.origin().offset(Arena.OPEN, 0, Arena.OPEN);
			for (int cx = low.getX() >> 4; cx <= high.getX() >> 4; cx++) {
				for (int cz = low.getZ() >> 4; cz <= high.getZ() >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					chunks.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
				}
			}
		}

		for (Arena arena : arenas) {
			arena.build();
		}

		double ms = (System.nanoTime() - start) / 1.0e6;
		GymLog.info(GymLog.ARENA, "POOL", "arenas=" + arenas.size() + " chunks=" + chunks.size() + " ms=" + LearnLog.num(ms));
	}
}
