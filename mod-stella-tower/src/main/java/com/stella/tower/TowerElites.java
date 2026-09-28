package com.stella.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every fifth floor keeps an elite by its portal, and every tenth a boss beside it. Each returns five
 * minutes after it falls, one of each at a time, and neither counts toward the floor's cap. A boss slain
 * by a player drops its floor's key, and carries a bar for the players on its floor.
 * <p>
 * Each post is saved in {@link GuardianData}: a guardian recorded there is held to be alive until its
 * death is seen or its room, fully loaded, no longer holds it, so a floor that loads slowly never raises
 * a second one, and a guardian's rest outlives a restart.
 */
public final class TowerElites {
	public static final int ELITE_EVERY = 5;
	public static final int BOSS_EVERY = 10;
	public static final int RESPAWN_TICKS = 6000;
	/** Where they stand: this far in front of the portal, and how far they may stray from it. */
	public static final int GUARD_DISTANCE = 5;
	public static final int GUARD_RADIUS = 8;
	public static final int CHECK_TICKS = 20;
	public static final int PLACE_TRIES = 40;
	private static int respawnTicks = RESPAWN_TICKS;
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private TowerElites() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerElites::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state == null || state.rank() == TowerRank.MOB || !(entity.level() instanceof ServerLevel level)
				|| !level.dimension().equals(TowerTravel.TOWER)) {
				return;
			}
			GuardianData data = GuardianData.get(level);
			GuardianData.Post post = data.post(state.floor(), state.rank());
			if (post != null && post.guardian().filter(entity.getUUID()::equals).isPresent()) {
				// The rest runs from the death itself.
				data.set(state.floor(), state.rank(), new GuardianData.Post(Optional.empty(), data.ticks() + respawnTicks));
			}
			if (state.rank() == TowerRank.BOSS && entity.getLastHurtByPlayer() != null && level.getGameRules().get(GameRules.MOB_DROPS)) {
				entity.spawnAtLocation(level, FloorKeyItem.forFloor(state.floor()));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
		});
	}

	/** The test seam: how long a slain elite or boss stays away. */
	public static void pinRespawnTicks(final int ticks) {
		respawnTicks = ticks;
	}

	public static int respawnTicks() {
		return respawnTicks;
	}

	public static boolean keepsElite(final int floor) {
		return floor % ELITE_EVERY == 0;
	}

	public static boolean keepsBoss(final int floor) {
		return floor % BOSS_EVERY == 0;
	}

	/** The kinds that may hold a post: the floor's own, but no creeper, whose blast ends it without a death. */
	public static List<EntityType<? extends Mob>> guardianPool(final int floor) {
		List<EntityType<? extends Mob>> pool = new ArrayList<>(TowerMobs.pool(floor));
		pool.remove(EntityTypes.CREEPER);
		return pool;
	}

	private static List<Mob> of(final ServerLevel tower, final int floor, final TowerRank rank) {
		return tower.getEntitiesOfClass(Mob.class, TowerMobs.room(floor), mob -> {
			TowerMobs.State state = TowerMobs.stateOf(mob);
			return mob.isAlive() && state != null && state.floor() == floor && state.rank() == rank;
		});
	}

	/** The live elite or boss of this rank on the floor, if any. */
	public static @Nullable Mob guardian(final ServerLevel tower, final int floor, final TowerRank rank) {
		List<Mob> found = of(tower, floor, rank);
		return found.isEmpty() ? null : found.get(0);
	}

	public static int alive(final ServerLevel tower, final int floor, final TowerRank rank) {
		return of(tower, floor, rank).size();
	}

	public static @Nullable ServerBossEvent barOf(final Mob boss) {
		return BARS.get(boss.getUUID());
	}

	/** The test seam: drops a boss's bar, as an unload does, so its rebuilding can be seen. */
	public static void forgetBar(final Mob boss) {
		ServerBossEvent bar = BARS.remove(boss.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** Whether every chunk of the room has its entities loaded. */
	public static boolean roomLoaded(final ServerLevel tower, final int floor) {
		BlockPos origin = TowerLayout.origin(floor);
		for (int cx = (origin.getX() - 1) >> 4; cx <= (origin.getX() + TowerLayout.SIZE) >> 4; cx++) {
			for (int cz = (origin.getZ() - 1) >> 4; cz <= (origin.getZ() + TowerLayout.SIZE) >> 4; cz++) {
				if (!tower.areEntitiesLoaded(ChunkPos.pack(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null) {
			return;
		}
		GuardianData data = GuardianData.get(tower);
		data.tick();
		if (server.getTickCount() % CHECK_TICKS == 0) {
			Map<Integer, Boolean> occupied = new HashMap<>();
			for (ServerPlayer player : tower.players()) {
				int floor = TowerLayout.floorAt(player.blockPosition());
				if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
					occupied.put(floor, true);
				}
			}
			for (int floor : occupied.keySet()) {
				if (!roomLoaded(tower, floor)) {
					continue;
				}
				if (keepsElite(floor)) {
					keep(tower, data, floor, TowerRank.ELITE);
				}
				if (keepsBoss(floor)) {
					keep(tower, data, floor, TowerRank.BOSS);
					Mob boss = guardian(tower, floor, TowerRank.BOSS);
					if (boss != null && !BARS.containsKey(boss.getUUID())) {
						BARS.put(boss.getUUID(), newBar(boss));
					}
				}
			}
		}
		updateBars(tower);
	}

	/** Holds a post: a recorded guardian stands until seen gone; an empty post rises again once rested. */
	private static void keep(final ServerLevel tower, final GuardianData data, final int floor, final TowerRank rank) {
		GuardianData.Post post = data.post(floor, rank);
		if (post != null && post.guardian().isPresent()) {
			boolean present = of(tower, floor, rank).stream().anyMatch(mob -> mob.getUUID().equals(post.guardian().get()));
			if (present) {
				return;
			}
			// Gone from a fully loaded room without a death seen: a blast or a discard. Its rest starts now.
			data.set(floor, rank, new GuardianData.Post(Optional.empty(), data.ticks() + respawnTicks));
			return;
		}
		if (post != null && data.ticks() < post.nextRise()) {
			return;
		}
		if (alive(tower, floor, rank) > 0) {
			// One already stands, from before the record was kept: it holds the post.
			data.set(floor, rank, new GuardianData.Post(Optional.of(guardian(tower, floor, rank).getUUID()), 0L));
			return;
		}
		Mob risen = rise(tower, floor, rank);
		if (risen != null) {
			data.set(floor, rank, new GuardianData.Post(Optional.of(risen.getUUID()), 0L));
		}
	}

	/** Raises the floor's elite or boss in front of its portal, kept near it. */
	public static @Nullable Mob rise(final ServerLevel tower, final int floor, final TowerRank rank) {
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = guardianPool(floor);
		Mob mob = pool.get(random.nextInt(pool.size())).create(tower, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		BlockPos front = TowerLayout.portalInside(floor).get(0).offset(-GUARD_DISTANCE, 0, 0);
		mob.snapTo(Vec3.atBottomCenterOf(front));
		mob.finalizeSpawn(tower, tower.getCurrentDifficultyAt(front), EntitySpawnReason.EVENT, null);
		TowerMobs.settle(mob);
		for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
			BlockPos feet = front.offset(random.nextInt(5) - 2, 0, random.nextInt(7) - 3);
			mob.snapTo(Vec3.atBottomCenterOf(feet).x, feet.getY(), Vec3.atBottomCenterOf(feet).z, -90.0F, 0.0F);
			if (!tower.isLoaded(feet) || !TowerMobs.standsFree(tower, mob, feet)) {
				continue;
			}
			TowerMobs.makeTowerMob(mob, floor, TowerElement.values()[random.nextInt(TowerElement.values().length)], rank);
			mob.setHomeTo(front, GUARD_RADIUS);
			if (!tower.addFreshEntity(mob)) {
				return null;
			}
			if (rank == TowerRank.BOSS) {
				BARS.put(mob.getUUID(), newBar(mob));
			}
			StellaTower.LOGGER.debug("[TOWER] floor={} {} RISES as {}", floor, rank, mob.getType());
			return mob;
		}
		mob.discard();
		return null;
	}

	private static ServerBossEvent newBar(final Mob boss) {
		return new ServerBossEvent(boss.getUUID(), boss.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	}

	/** Each boss's bar: its health, shown to the players on its floor and to nobody else. */
	private static void updateBars(final ServerLevel tower) {
		BARS.entrySet().removeIf(entry -> {
			ServerBossEvent bar = entry.getValue();
			if (!(tower.getEntity(entry.getKey()) instanceof Mob boss) || !boss.isAlive()) {
				bar.removeAllPlayers();
				return true;
			}
			bar.setProgress(boss.getHealth() / boss.getMaxHealth());
			int floor = TowerLayout.floorAt(boss.blockPosition());
			for (ServerPlayer player : tower.players()) {
				boolean there = floor > 0 && TowerLayout.floorAt(player.blockPosition()) == floor;
				if (there && !bar.getPlayers().contains(player)) {
					bar.addPlayer(player);
				} else if (!there && bar.getPlayers().contains(player)) {
					bar.removePlayer(player);
				}
			}
			for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
				if (player.level() != tower || player.isRemoved()) {
					bar.removePlayer(player);
				}
			}
			return false;
		});
	}
}
