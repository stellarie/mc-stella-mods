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

/**
 * Each ordinary archetype's floor fills to its cap, on any of its stories, with no mob in a block. The cap
 * itself, no further, is {@link TowerSpawnerGameTest}'s case.
 */
public class TowerArchetypeSpawnGameTest {
	private static AABB room(final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		return new AABB(o.getX(), o.getY() - 1, o.getZ(), o.getX() + TowerLayout.SIZE, o.getY() + TowerLayout.HEIGHT, o.getZ() + TowerLayout.SIZE);
	}

	private static void fills(final GameTestHelper helper, final TowerArchetype archetype) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerPlanGameTest.freshFloorOf(tower, archetype);
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 arrival = TowerLayout.arrival(tower, floor);
		player.teleportTo(tower, arrival.x, arrival.y, arrival.z, Set.of(), 0.0F, 0.0F, true);
		int[] filledAt = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			int now = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.isAlive() && mob.entityTags().contains(TowerMobs.TAG)
				&& TowerMobs.stateOf(mob) != null && TowerMobs.stateOf(mob).rank() == TowerRank.MOB && !(mob instanceof TowerGhost)).size();
			most[0] = Math.max(most[0], now);
			if (filledAt[0] < 0 && now >= TowerMobs.FLOOR_CAP) {
				filledAt[0] = (int) helper.getTick();
			}
		});
		helper.runAfterDelay(380L, () -> {
			TowerSafety.sweep(tower, floor);
			List<Mob> mobs = tower.getEntitiesOfClass(Mob.class, room(floor), mob -> mob.entityTags().contains(TowerMobs.TAG) && !(mob instanceof TowerGhost));
			TowerPlan plan = TowerPlan.at(tower, floor);
			int inBlock = 0;
			int inZone = 0;
			java.util.Map<Integer, Integer> byHeight = new java.util.TreeMap<>();
			for (Mob mob : mobs) {
				BlockPos at = mob.blockPosition().subtract(plan.origin());
				// A spider may cling to a wall or a pillar: only a body that overlaps a block is in one.
				inBlock += tower.noCollision(mob, mob.getBoundingBox().deflate(0.05)) ? 0 : 1;
				inZone += plan.inSafeZone(at.getX(), at.getY(), at.getZ()) ? 1 : 0;
				byHeight.merge(at.getY(), 1, Integer::sum);
			}
			TowerTestSupport.result("fills" + archetype, "floor=" + floor + " stories=" + plan.stories().size() + " filledAtTick=" + filledAt[0]
				+ " most=" + most[0] + " cap=" + TowerMobs.FLOOR_CAP + " mobs=" + mobs.size() + " inBlock=" + inBlock + " inZone=" + inZone
				+ " byHeight=" + byHeight);
			for (Mob mob : new ArrayList<>(mobs)) {
				mob.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(filledAt[0] >= 0, "the " + archetype + " floor never filled: at most " + most[0] + " of " + TowerMobs.FLOOR_CAP);
			helper.assertTrue(inBlock == 0, inBlock + " mobs stand in a block");
			helper.assertTrue(inZone == 0, inZone + " mobs remain in a safe zone after its sweep");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 400)
	public void aLabyrinthFillsToItsCap(final GameTestHelper helper) {
		fills(helper, TowerArchetype.LABYRINTH);
	}

	@GameTest(maxTicks = 400)
	public void aGreatHallFillsToItsCap(final GameTestHelper helper) {
		fills(helper, TowerArchetype.GREAT_HALL);
	}

	@GameTest(maxTicks = 400)
	public void aCrystalCavernFillsToItsCap(final GameTestHelper helper) {
		fills(helper, TowerArchetype.CRYSTAL_CAVERN);
	}
}
