package com.stella.tower;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every fifth floor keeps an elite by its portal, and every tenth a boss beside it. Each returns five
 * minutes after it falls, one of each at a time, and neither counts toward the floor's cap. A boss drops
 * its floor's key, and carries a bar for the players on its floor.
 */
public final class TowerElites {
	public static final int ELITE_EVERY = 5;
	public static final int BOSS_EVERY = 10;
	public static final int RESPAWN_TICKS = 6000;
	/** Where they stand: this far in front of the portal. */
	public static final int GUARD_DISTANCE = 5;
	public static final int CHECK_TICKS = 20;
	public static final int PLACE_TRIES = 40;
	private static int respawnTicks = RESPAWN_TICKS;
	/** When each floor's elite or boss may next rise, by floor and rank. */
	private static final Map<Long, Integer> NEXT_RISE = new HashMap<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private TowerElites() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TowerElites::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state != null && state.rank() == TowerRank.BOSS && entity.level() instanceof ServerLevel level) {
				entity.spawnAtLocation(level, FloorKeyItem.forFloor(state.floor()));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			NEXT_RISE.clear();
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

	private static long key(final int floor, final TowerRank rank) {
		return ((long) floor << 2) | rank.ordinal();
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel tower = server.getLevel(TowerTravel.TOWER);
		if (tower == null) {
			return;
		}
		if (server.getTickCount() % CHECK_TICKS == 0) {
			Map<Integer, Boolean> occupied = new HashMap<>();
			for (ServerPlayer player : tower.players()) {
				int floor = TowerLayout.floorAt(player.blockPosition());
				if (floor > 0 && !player.isSpectator() && TowerFloors.isBuilt(tower, floor)) {
					occupied.put(floor, true);
				}
			}
			int now = server.getTickCount();
			for (int floor : occupied.keySet()) {
				if (keepsElite(floor)) {
					keep(tower, floor, TowerRank.ELITE, now);
				}
				if (keepsBoss(floor)) {
					keep(tower, floor, TowerRank.BOSS, now);
				}
			}
		}
		updateBars(tower);
	}

	/** One of the rank on the floor: while it lives its time is pushed on, and once gone it rises when due. */
	private static void keep(final ServerLevel tower, final int floor, final TowerRank rank, final int now) {
		long key = key(floor, rank);
		if (alive(tower, floor, rank) > 0) {
			NEXT_RISE.put(key, now + respawnTicks);
			return;
		}
		Integer due = NEXT_RISE.get(key);
		if (due != null && now < due) {
			return;
		}
		if (rise(tower, floor, rank) != null) {
			NEXT_RISE.put(key, now + respawnTicks);
		}
	}

	/** Raises the floor's elite or boss in front of its portal. */
	public static @Nullable Mob rise(final ServerLevel tower, final int floor, final TowerRank rank) {
		RandomSource random = tower.getRandom();
		List<EntityType<? extends Mob>> pool = TowerMobs.pool(floor);
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
			if (!tower.addFreshEntity(mob)) {
				return null;
			}
			if (rank == TowerRank.BOSS) {
				ServerBossEvent bar = new ServerBossEvent(mob.getUUID(), mob.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
				BARS.put(mob.getUUID(), bar);
			}
			StellaTower.LOGGER.debug("[TOWER] floor={} {} RISES as {}", floor, rank, mob.getType());
			return mob;
		}
		mob.discard();
		return null;
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
