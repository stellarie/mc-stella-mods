package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The lost castles' grounds: each castle, once generated, is recorded; the first time a player comes within
 * {@link #WAKE_DISTANCE} blocks of it, three to six tower mobs rise in its yard, the ring on the surface, at
 * the lost room's level rule. They rise once and never again.
 * <p>
 * A castle's spawners raise tower mobs, capped per floor. No mob spawns of itself inside the frame of a
 * castle's portal.
 * <p>
 * Worldgen threads only queue a castle; the server thread writes the record.
 */
public final class LostCastles {
	public static final int WAKE_DISTANCE = 32;
	public static final int MIN_MOBS = 3;
	public static final int MAX_MOBS = 6;
	public static final int MAX_STOCK_ATTEMPTS = MAX_MOBS * 4;
	public static final int CHECK_TICKS = 20;
	public static final int SPOT_TRIES = 40;
	/** What a mob of a floor spawner carries, so the floor cap can count its own mobs. */
	public static final String SPAWN_TAG = "mod-stella-tower.lost_castle_spawn";
	/** The most spawner mobs alive on one floor of a castle. */
	public static final int FLOOR_CAP = 8;
	private static final Queue<LostRoomData.Castle> FOUND = new ConcurrentLinkedQueue<>();

	private LostCastles() {
	}

	public static void register() {
		// Before the mobs mod's own listener, so a spawner mob is a tower mob before it can roll an elite or a boss.
		Identifier early = StellaTower.id("lost_castle_spawner");
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerEntityEvents.ENTITY_LOAD.register(early, LostCastles::onSpawnerMob);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				wake(server);
			}
		});
	}

	/** The reasons a mob appears of itself: a spawner, the dark or a chunk's making raises it. */
	private static boolean spawnsOfItself(final EntitySpawnReason reason) {
		return reason == EntitySpawnReason.SPAWNER || reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION
			|| reason == EntitySpawnReason.TRIAL_SPAWNER || reason == EntitySpawnReason.REINFORCEMENT || reason == EntitySpawnReason.JOCKEY
			|| reason == EntitySpawnReason.PATROL;
	}

	/**
	 * Refuses a mob that spawns of itself inside the frame of a castle's portal, and turns a mob that a vanilla
	 * spawner has just raised inside a lost castle into a tower mob of the lost room's level rule, unless its
	 * floor already holds {@link #FLOOR_CAP} of them. Any other mob, a mob loaded from disk and a mob that is a
	 * tower mob already are left alone, so the hook can run twice.
	 */
	static void onSpawnerMob(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob) || !(entity instanceof EntityLoadData load) || load.isLoadedFromDisk() || !spawnsOfItself(load.spawnReason())) {
			return;
		}
		LostRoomData.Castle castle = castleAt(level, mob.blockPosition());
		if (castle == null) {
			return;
		}
		LostRoomPiece piece = piece(castle);
		if (piece.portalFrame().isInside(mob.blockPosition())) {
			mob.discard();
			return;
		}
		if (load.spawnReason() != EntitySpawnReason.SPAWNER || TowerMobs.stateOf(mob) != null) {
			return;
		}
		BoundingBox box = piece.getBoundingBox();
		int floor = Math.max(0, Math.min(castle.floors() - 1, (mob.blockPosition().getY() - box.minY()) / LostCastleGrid.FLOOR_HEIGHT));
		int low = box.minY() + floor * LostCastleGrid.FLOOR_HEIGHT;
		AABB slab = new AABB(box.minX(), low, box.minZ(), box.maxX() + 1, low + LostCastleGrid.FLOOR_HEIGHT, box.maxZ() + 1);
		int alive = level.getEntitiesOfClass(Mob.class, slab, other -> other != mob && other.isAlive() && other.entityTags().contains(SPAWN_TAG)).size();
		if (alive >= FLOOR_CAP) {
			mob.discard();
			return;
		}
		// The no-roll tag comes first: the mobs mod reads it when it loads the mob, right after this.
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		TowerMobs.settle(mob);
		RandomSource random = mob.getRandom();
		int mobLevel = Math.max(0, LostRooms.averageLevel(level.getServer().getPlayerList().getPlayers())
			+ random.nextIntBetweenInclusive(-LostRooms.LEVEL_BELOW, LostRooms.LEVEL_ABOVE));
		TowerElement element = TowerElement.values()[random.nextInt(TowerElement.values().length)];
		TowerMobs.makeTowerMob(mob, LostRooms.equivalentFloor(mobLevel), element, TowerRank.MOB, mobLevel);
		mob.addTag(LostRooms.TAG);
		mob.addTag(SPAWN_TAG);
	}

	/** The recorded lost castle whose box holds a spot, or null. */
	private static LostRoomData.@Nullable Castle castleAt(final ServerLevel level, final BlockPos at) {
		for (LostRoomData.Castle castle : LostRoomData.get(level.getServer()).castles()) {
			BlockPos corner = castle.corner();
			boolean near = at.getX() >= corner.getX() && at.getX() < corner.getX() + LostCastleGrid.SIZE
				&& at.getZ() >= corner.getZ() && at.getZ() < corner.getZ() + LostCastleGrid.SIZE;
			if (castle.floors() > 0 && near && castle.dimension().equals(level.dimension()) && piece(castle).getBoundingBox().isInside(at)) {
				return castle;
			}
		}
		return null;
	}

	/** Queues a castle a chunk has just built; any thread. */
	public static void found(final ResourceKey<Level> dimension, final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		FOUND.add(new LostRoomData.Castle(dimension, new BlockPos(box.minX(), box.minY(), box.minZ()), piece.facing(), false, piece.palette().ordinal(),
			piece.floors(), piece.drop()));
	}

	/** The castle's piece, rebuilt from its record. */
	static LostRoomPiece piece(final LostRoomData.Castle castle) {
		return new LostRoomPiece(castle.corner().getX(), castle.corner().getY(), castle.corner().getZ(), castle.facing(),
			LostCastlePalette.values()[Math.floorMod(castle.variant(), LostCastlePalette.values().length)], castle.floors(), castle.drop());
	}

	/** Records the queued castles, then stocks the yard of each castle a player has come near; the mobs raised. */
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
			if (castle.floors() == 0) {
				// Laid before the castles went down: it has no ring to stock.
				data.stocked(castle);
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

	/** Raises three to six mobs in the yard, each at the players' average level less five to fifteen more. */
	static List<Mob> stock(final ServerLevel level, final LostRoomPiece piece, final List<ServerPlayer> players) {
		RandomSource random = level.getRandom();
		int count = MIN_MOBS + random.nextInt(MAX_MOBS - MIN_MOBS + 1);
		return stock(level, piece, players, random, count, mob -> true);
	}

	static List<Mob> stock(final ServerLevel level, final LostRoomPiece piece, final List<ServerPlayer> players,
		final RandomSource random, final int count, final Predicate<Mob> allowed) {
		int average = LostRooms.averageLevel(players);
		int[] yard = piece.yard();
		int feetY = piece.surfaceFeet();
		List<Mob> raised = new ArrayList<>();
		for (int attempt = 0; attempt < MAX_STOCK_ATTEMPTS && raised.size() < count; attempt++) {
			int mobLevel = Math.max(0, average + random.nextIntBetweenInclusive(-LostRooms.LEVEL_BELOW, LostRooms.LEVEL_ABOVE));
			List<EntityType<? extends Mob>> pool = TowerMobs.pool(LostRooms.equivalentFloor(mobLevel));
			Mob mob = pool.get(random.nextInt(pool.size())).create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			BlockPos first = piece.worldPos(yard[0], feetY, yard[1]);
			mob.snapTo(Vec3.atBottomCenterOf(first));
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(first), EntitySpawnReason.EVENT, null);
			TowerMobs.settle(mob);
			boolean placed = false;
			for (int spotAttempt = 0; spotAttempt < SPOT_TRIES && !placed; spotAttempt++) {
				BlockPos feet = piece.worldPos(yard[0] + random.nextInt(yard[2] - yard[0] + 1), feetY, yard[1] + random.nextInt(yard[3] - yard[1] + 1));
				Vec3 spot = TowerMobs.spotFor(mob, feet);
				mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
				placed = level.isLoaded(feet) && TowerMobs.standsFree(level, mob, feet);
			}
			if (placed && allowed.test(mob) && LostRooms.raise(level, mob, mobLevel, TowerRank.MOB, random)) {
				raised.add(mob);
			} else {
				mob.discard();
			}
		}
		if (raised.size() < count) {
			StellaTower.LOGGER.warn("Lost castle at {}: requested {} yard mobs, raised {} after {} candidates",
				piece.getBoundingBox(), count, raised.size(), MAX_STOCK_ATTEMPTS);
		}
		StellaTower.LOGGER.debug("[TOWER] lost castle at {} WOKE with {} mobs", piece.getBoundingBox(), raised.size());
		return raised;
	}
}
