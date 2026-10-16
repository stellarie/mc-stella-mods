package com.stella.end;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The End level's castle: its plan, its saved state, and the one build per world. */
public final class EndCastles {
	private static final AttachmentType<EndState> STATE = AttachmentRegistry.createPersistent(StellaEnd.id("end_state"), EndState.CODEC);

	private static CastleLayout cached;
	private static boolean pending;

	private EndCastles() {
	}

	public static void register() {
		// The first load of a castle chunk asks for the build. The build waits for the next tick, outside the load.
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension().equals(Level.END) && inCastleChunks(chunk.getPos().x(), chunk.getPos().z())) {
				pending = true;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!pending) {
				return;
			}
			pending = false;
			ServerLevel end = server.getLevel(Level.END);
			if (end != null) {
				ensureBuilt(end);
			}
		});
	}

	private static boolean inCastleChunks(final int chunkX, final int chunkZ) {
		return chunkX >= EndCastle.MIN_X >> 4 && chunkX <= (EndCastle.MIN_X + EndCastle.SIZE - 1) >> 4
			&& chunkZ >= EndCastle.MIN_Z >> 4 && chunkZ <= (EndCastle.MIN_Z + EndCastle.SIZE - 1) >> 4;
	}

	public static EndState state(final ServerLevel end) {
		EndState state = ((AttachmentTarget) end).getAttached(STATE);
		return state == null ? EndState.EMPTY : state;
	}

	static void setState(final ServerLevel end, final EndState state) {
		((AttachmentTarget) end).setAttached(STATE, state);
	}

	public static CastleLayout layout(final ServerLevel end) {
		long seed = end.getSeed();
		if (cached == null || cached.seed() != seed) {
			cached = CastleLayout.generate(seed);
		}
		return cached;
	}

	/** Builds the castle if the level has none yet. Returns whether it built now. */
	public static boolean ensureBuilt(final ServerLevel end) {
		if (state(end).built()) {
			return false;
		}
		CastleBuilder.build(end, layout(end), EndCastle.min());
		setState(end, state(end).withBuilt());
		BehaviorLog.info("[END] castle built seed={}", end.getSeed());
		return true;
	}
}
