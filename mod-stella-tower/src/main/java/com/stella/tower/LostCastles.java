package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The lost castles' grounds: each castle, once generated, is recorded; the first time a player comes within
 * {@link #WAKE_DISTANCE} blocks of it, three to six tower mobs rise in its courtyard, at the lost room's
 * level rule. They rise once and never again.
 * <p>
 * Worldgen threads only queue a castle; the server thread writes the record.
 */
public final class LostCastles {
	public static final int WAKE_DISTANCE = 32;
	public static final int MIN_MOBS = 3;
	public static final int MAX_MOBS = 6;
	public static final int CHECK_TICKS = 20;
	public static final int SPOT_TRIES = 40;
	private static final Queue<LostRoomData.Castle> FOUND = new ConcurrentLinkedQueue<>();

	private LostCastles() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				wake(server);
			}
		});
	}

	/** Queues a castle a chunk has just built; any thread. */
	public static void found(final ResourceKey<Level> dimension, final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		FOUND.add(new LostRoomData.Castle(dimension, new BlockPos(box.minX(), box.minY(), box.minZ()), piece.facing(), false, piece.variant().ordinal()));
	}

	/** The castle's piece, rebuilt from its record. */
	static LostRoomPiece piece(final LostRoomData.Castle castle) {
		return new LostRoomPiece(castle.corner().getX(), castle.corner().getY(), castle.corner().getZ(), castle.facing(),
			LostCastleVariant.values()[Math.floorMod(castle.variant(), LostCastleVariant.values().length)]);
	}

	/** Records the queued castles, then stocks the grounds of each castle a player has come near; the mobs raised. */
	static List<Mob> wake(final MinecraftServer server) {
		LostRoomData data = LostRoomData.get(server);
		for (LostRoomData.Castle castle = FOUND.poll(); castle != null; castle = FOUND.poll()) {
			data.found(castle);
		}
		List<Mob> raised = new ArrayList<>();
		for (LostRoomData.Castle castle : data.unstocked()) {
			ServerLevel level = server.getLevel(castle.dimension());
			if (level == null) {
				continue;
			}
			LostRoomPiece piece = piece(castle);
			AABB box = AABB.of(piece.getBoundingBox());
			boolean near = level.players().stream().anyMatch(player -> !player.isSpectator()
				&& box.distanceToSqr(player.position()) <= (double) WAKE_DISTANCE * WAKE_DISTANCE);
			if (near) {
				raised.addAll(stock(level, piece, server.getPlayerList().getPlayers()));
				data.stocked(castle);
			}
		}
		return raised;
	}

	/** Raises three to six mobs in the courtyard, each at the players' average level less five to fifteen more. */
	static List<Mob> stock(final ServerLevel level, final LostRoomPiece piece, final List<ServerPlayer> players) {
		RandomSource random = level.getRandom();
		int average = LostRooms.averageLevel(players);
		int count = MIN_MOBS + random.nextInt(MAX_MOBS - MIN_MOBS + 1);
		int[] yard = piece.yard();
		List<Mob> raised = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int mobLevel = Math.max(0, average + random.nextIntBetweenInclusive(-LostRooms.LEVEL_BELOW, LostRooms.LEVEL_ABOVE));
			List<EntityType<? extends Mob>> pool = TowerMobs.pool(LostRooms.equivalentFloor(mobLevel));
			Mob mob = pool.get(random.nextInt(pool.size())).create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			BlockPos first = piece.worldPos(yard[0], 1, yard[1]);
			mob.snapTo(Vec3.atBottomCenterOf(first));
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(first), EntitySpawnReason.EVENT, null);
			TowerMobs.settle(mob);
			boolean placed = false;
			for (int attempt = 0; attempt < SPOT_TRIES && !placed; attempt++) {
				BlockPos feet = piece.worldPos(yard[0] + random.nextInt(yard[2] - yard[0] + 1), 1, yard[1] + random.nextInt(yard[3] - yard[1] + 1));
				Vec3 spot = TowerMobs.spotFor(mob, feet);
				mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
				placed = level.isLoaded(feet) && TowerMobs.standsFree(level, mob, feet);
			}
			if (placed && LostRooms.raise(level, mob, mobLevel, TowerRank.MOB, random)) {
				raised.add(mob);
			} else {
				mob.discard();
			}
		}
		StellaTower.LOGGER.debug("[TOWER] lost castle at {} WOKE with {} mobs", piece.getBoundingBox(), raised.size());
		return raised;
	}
}
