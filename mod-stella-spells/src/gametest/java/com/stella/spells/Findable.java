package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

/**
 * Runs a cast once its subjects are visible to an entity query. A mob placed past the test's own structure can sit
 * in a chunk the harness loaded but never made entity-accessible, where no spell's search finds it: Spark Shot's
 * range test read NO_TARGET in one gate run of six and Chainstorm's sequence test in another. Each subject's chunk
 * is forced for the wait and released after, unless something else had already forced it.
 */
final class Findable {
	private Findable() {
	}

	static void then(final GameTestHelper helper, final List<? extends Mob> subjects, final Runnable cast) {
		ServerLevel level = helper.getLevel();
		List<ChunkPos> forced = new ArrayList<>();
		for (Mob subject : subjects) {
			ChunkPos chunk = subject.chunkPosition();
			if (!forced.contains(chunk) && !level.getForceLoadedChunks().contains(chunk.pack())) {
				level.setChunkForced(chunk.x(), chunk.z(), true);
				forced.add(chunk);
			}
		}
		final boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || !subjects.stream().allMatch(subject -> level.getEntitiesOfClass(Mob.class, subject.getBoundingBox()).contains(subject))) {
				return;
			}
			done[0] = true;
			try {
				cast.run();
			} finally {
				for (ChunkPos chunk : forced) {
					level.setChunkForced(chunk.x(), chunk.z(), false);
				}
			}
		});
	}
}
