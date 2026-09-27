package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MemberRecord;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Every village near a player keeps a patrol: a village settled after its chunk loaded gets one, and a village whose head is gone gets a new one. */
public final class VillageCoverage {
	public static final int SCAN_INTERVAL_TICKS = 100;
	/** Two chunks: a player walking through a village passes this close to its beds. */
	public static final int SCAN_RADIUS_CHUNKS = 2;
	/** How long a head must stay missing before its village gets a new patrol, so a fight never refills itself. */
	public static final int HEAD_RESPAWN_DELAY_TICKS = 1200;
	private static final Map<UUID, Long> HEAD_MISSING_SINCE = new ConcurrentHashMap<>();
	private static volatile Predicate<ServerPlayer> scanFilter = player -> !player.isSpectator();

	private VillageCoverage() {
	}

	/** A new server's level counters start again at zero, so no missing-since tick outlives its server. */
	static void clear() {
		HEAD_MISSING_SINCE.clear();
	}

	/** Test seam: how many villages have a head on the missing clock now. */
	public static int missingHeadCount() {
		return HEAD_MISSING_SINCE.size();
	}

	/** Test seam: starts a village's missing clock, as a scan that found no head would. */
	public static void noteMissing(final UUID villageId, final long tick) {
		HEAD_MISSING_SINCE.putIfAbsent(villageId, tick);
	}

	public static void setScanFilter(final Predicate<ServerPlayer> filter) {
		scanFilter = filter;
	}

	/** Every SCAN_INTERVAL_TICKS of the level's own counter, the loaded chunks around each player are checked for villages. */
	static void tick(final ServerLevel level, final long tick) {
		if (tick % SCAN_INTERVAL_TICKS != 0L) {
			return;
		}

		Set<UUID> near = new LinkedHashSet<>();
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (!scanFilter.test(player)) {
				continue;
			}

			ChunkPos center = player.chunkPosition();
			for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
				for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz);
					if (chunk != null) {
						// The chunk-load registration, run again: it registers villages whose beds were claimed since.
						near.addAll(PatrolService.onChunkLoad(level, chunk));
					}
				}
			}
		}

		VillageWorldData data = VillageWorldData.get(level);
		for (UUID villageId : near) {
			data.village(villageId).ifPresent(village -> checkHead(level, data, village, tick));
		}
	}

	private static void checkHead(final ServerLevel level, final VillageWorldData data, final VillageRecord village, final long tick) {
		if (!village.rosterStarted() || PatrolService.headFor(level, village.id()) != null) {
			HEAD_MISSING_SINCE.remove(village.id());
			return;
		}

		// A head on record in a chunk that is not loaded may still be out there.
		Optional<MemberRecord> head = village.members().stream()
			.filter(member -> member.alive() && PatrolState.Role.HEAD.name().equals(member.role()))
			.findFirst();
		if (head.isPresent() && !level.isPositionEntityTicking(head.get().lastPosition())) {
			return;
		}

		long since = HEAD_MISSING_SINCE.computeIfAbsent(village.id(), id -> {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=village_head_missing village={} tick={}", McStellaVillages.MOD_ID, village.id(), tick
			);
			return tick;
		});
		if (tick - since >= HEAD_RESPAWN_DELAY_TICKS) {
			HEAD_MISSING_SINCE.remove(village.id());
			PatrolService.refillRoster(level, data, village);
		}
	}
}
