package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A floor with a player on it fills to its cap, away from the player, each mob of its floor and element. */
public class TowerSpawnerGameTest {
	private static AABB room(final int floor) {
		net.minecraft.core.BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + TowerLayout.SIZE, o.getY() + TowerLayout.HEIGHT, o.getZ() + TowerLayout.SIZE);
	}

	@GameTest(maxTicks = 400)
	public void anOccupiedFloorFillsToItsCapAndNoFurther(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		// An ordinary floor: an elite or a boss stands outside the cap.
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		int[] filledAt = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			// Counted by tag, not by the spawner's own count, so a mob the cap misses is still seen here.
			// A ghost is not one of the floor's mobs: it stands on the players' side, outside the cap.
			int now = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.isAlive() && mob.entityTags().contains(TowerMobs.TAG)
				&& TowerMobs.stateOf(mob) != null && TowerMobs.stateOf(mob).rank() == TowerRank.MOB && !(mob instanceof TowerGhost)).size();
			most[0] = Math.max(most[0], now);
			if (filledAt[0] < 0 && now >= TowerMobs.FLOOR_CAP) {
				filledAt[0] = (int) helper.getTick();
			}
		});
		helper.runAfterDelay(380L, () -> {
			List<Mob> mobs = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.entityTags().contains(TowerMobs.TAG) && !(mob instanceof TowerGhost));
			double nearest = Double.MAX_VALUE;
			int stamped = 0;
			for (Mob mob : mobs) {
				TowerMobs.State state = TowerMobs.stateOf(mob);
				stamped += state != null && state.floor() == floor && mob.getCustomName() != null
					&& mob.getCustomName().getString().startsWith(state.element().label()) ? 1 : 0;
			}
			TowerTestSupport.result("floorFills", "filledAtTick=" + filledAt[0] + " most=" + most[0] + " stamped=" + stamped + "/" + mobs.size()
				+ " cap=" + TowerMobs.FLOOR_CAP);
			for (Mob mob : new ArrayList<>(mobs)) {
				mob.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(filledAt[0] >= 0, "the floor never filled: at most " + most[0] + " of " + TowerMobs.FLOOR_CAP);
			helper.assertTrue(most[0] <= TowerMobs.FLOOR_CAP, "the floor held " + most[0] + ", past its cap of " + TowerMobs.FLOOR_CAP);
			helper.assertTrue(stamped == mobs.size(), stamped + " of " + mobs.size() + " mobs carry their floor and element");
			helper.succeed();
		});
	}

	@GameTest
	public void aSpawnedMobStartsWellAwayFromThePlayer(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		double nearest = Double.MAX_VALUE;
		int spawned = 0;
		for (int i = 0; i < 40; i++) {
			Mob mob = TowerMobs.spawnOne(tower, floor);
			if (mob != null) {
				spawned++;
				nearest = Math.min(nearest, mob.distanceTo(player));
				mob.discard();
			}
		}
		TowerTestSupport.result("spawnDistance", "spawned=" + spawned + " nearest=" + nearest + " min=" + TowerMobs.MIN_PLAYER_DISTANCE);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(spawned > 0, "no mob could be placed on the floor");
		helper.assertTrue(nearest >= TowerMobs.MIN_PLAYER_DISTANCE, "a mob started " + nearest + " from the player");
		helper.succeed();
	}

	/** Only the floor a player stands on is stocked: the empty floor beside it raises nothing. */
	@GameTest(maxTicks = 100)
	public void anEmptyFloorRaisesNothing(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		int empty = floor + 1;
		TowerFloors.ensure(tower, floor);
		TowerFloors.ensure(tower, empty);
		// The empty floor is kept loaded, or a mob raised there would sit in an unloaded chunk and never be counted.
		BlockPos middle = TowerLayout.origin(empty).offset(TowerLayout.SIZE / 2, 0, TowerLayout.SIZE / 2);
		tower.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL, net.minecraft.world.level.ChunkPos.containing(middle), TowerFloors.CHUNK_RADIUS);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		helper.runAfterDelay(60L, () -> {
			int stocked = TowerMobs.count(tower, floor);
			int count = TowerMobs.count(tower, empty);
			TowerTestSupport.result("emptyFloor", "stocked=" + stocked + " empty=" + count);
			for (Mob mob : tower.getEntitiesOfClass(Mob.class, room(floor).minmax(room(empty)), mob -> mob.entityTags().contains(TowerMobs.TAG))) {
				mob.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(stocked > 0, "the occupied floor raised nothing, so the case proves nothing");
			helper.assertTrue(count == 0, "a floor with nobody on it raised " + count + " mobs");
			helper.succeed();
		});
	}

	/** While a floor is occupied the spawner holds its whole room loaded, so no mob in it goes uncounted. */
	@GameTest(maxTicks = 200)
	public void anOccupiedRoomIsHeldLoadedCornerToCorner(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		BlockPos far = TowerLayout.origin(floor).offset(TowerLayout.SIZE - 1, 0, TowerLayout.SIZE - 1);
		helper.runAfterDelay(40L, () -> TowerTestSupport.leave(helper, player));
		helper.runAfterDelay(140L, () -> {
			boolean held = tower.isPositionEntityTicking(far);
			TowerTestSupport.result("roomHeld", "farCornerTicking=" + held);
			helper.assertTrue(held, "the far corner of a room just left was let go at once");
			helper.succeed();
		});
	}
}
