package com.stella.end;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The way up: a walk from the gate to the foot of the arena stair, and the four flights that leave floor 4. */
public class CastleStairsGameTest {
	@GameTest(environment = "mod-stella-end-test:walk_gate", maxTicks = 40)
	public void aWalkerFromTheGateReachesEveryFloorAndTheFootOfTheStairIntoTheArena(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		CastleLayout layout = EndCastles.layout(end);
		int gateX = EndCastle.MIN_X + (CastleLayout.GATE.x0() + CastleLayout.GATE.x1()) / 2;
		int outsideZ = EndCastle.MIN_Z + CastleLayout.GATE.z0() + 1;
		BlockPos start = new BlockPos(gateX, EndCastle.standY(1), outsideZ);
		helper.assertTrue(CastleWalk.standable(end, start.getX(), start.getY(), start.getZ()), "nobody can stand outside the gate at " + start);
		Set<Long> reached = CastleWalk.reach(end, start);
		StringBuilder bad = new StringBuilder();
		for (int floor = 1; floor <= EndCastle.FLOORS; floor++) {
			CastleLayout.FloorPlan plan = layout.floor(floor);
			if (!CastleWalk.reaches(reached, EndCastle.MIN_X + plan.landingX(), EndCastle.standY(floor), EndCastle.MIN_Z + plan.landingZ())) {
				bad.append(" floor ").append(floor).append(" landing");
			}
		}
		CastleLayout.Flight stair = CastleLayout.FLIGHT_5;
		BlockPos foot = new BlockPos(EndCastle.MIN_X + stair.x(-1, 1), EndCastle.standY(5), EndCastle.MIN_Z + stair.z(-1, 1));
		boolean atFoot = CastleWalk.reaches(reached, foot.getX(), foot.getY(), foot.getZ());
		BlockPos trigger = CastleLayout.sovereignTrigger();
		boolean onMiddle = CastleWalk.reaches(reached, trigger.getX(), trigger.getY() + 1, trigger.getZ());
		EndTestSupport.result("walkGate", "reached=" + reached.size() + " foot=" + foot + " atFoot=" + atFoot + " middleStep=" + trigger + " onMiddle=" + onMiddle + " missing=" + bad);
		helper.assertTrue(bad.isEmpty(), "floors a walker from the gate does not reach:" + bad);
		helper.assertTrue(atFoot, "a walker from the gate does not reach the stair foot at " + foot);
		helper.assertTrue(onMiddle, "a walker does not climb to the middle step at " + trigger);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:walk_stairs", maxTicks = 40)
	public void floorFourHasFourStairsToFloorFiveOneBesideEachWallOfTheMiddleRoom(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int low = EndCastle.standY(4);
		int high = EndCastle.slabY(5);
		List<BlockPos> stairs = new ArrayList<>();
		for (int x = EndCastle.MIN_X; x < EndCastle.MIN_X + EndCastle.SIZE; x++) {
			for (int z = EndCastle.MIN_Z; z < EndCastle.MIN_Z + EndCastle.SIZE; z++) {
				for (int y = low; y <= high; y++) {
					if (end.getBlockState(new BlockPos(x, y, z)).is(Blocks.PURPUR_STAIRS)) {
						stairs.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		Set<BlockPos> unseen = new HashSet<>(stairs);
		List<List<BlockPos>> flights = new ArrayList<>();
		for (BlockPos first : stairs) {
			if (!unseen.remove(first)) {
				continue;
			}
			List<BlockPos> flight = new ArrayList<>(List.of(first));
			ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(first));
			while (!queue.isEmpty()) {
				BlockPos at = queue.poll();
				Direction facing = end.getBlockState(at).getValue(StairBlock.FACING);
				for (BlockPos near : List.copyOf(unseen)) {
					BlockState state = end.getBlockState(near);
					if (Math.abs(near.getX() - at.getX()) + Math.abs(near.getZ() - at.getZ()) <= 1 && Math.abs(near.getY() - at.getY()) <= 1
						&& state.getValue(StairBlock.FACING) == facing && unseen.remove(near)) {
						flight.add(near);
						queue.add(near);
					}
				}
			}
			flights.add(flight);
		}
		StringBuilder bad = new StringBuilder();
		Set<String> sides = new HashSet<>();
		CastleLayout.Rect room = CastleLayout.MIDDLE_ROOM;
		for (List<BlockPos> flight : flights) {
			int x0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z0 = Integer.MAX_VALUE;
			int z1 = Integer.MIN_VALUE;
			int y0 = Integer.MAX_VALUE;
			int y1 = Integer.MIN_VALUE;
			for (BlockPos at : flight) {
				x0 = Math.min(x0, at.getX());
				x1 = Math.max(x1, at.getX());
				z0 = Math.min(z0, at.getZ());
				z1 = Math.max(z1, at.getZ());
				y0 = Math.min(y0, at.getY());
				y1 = Math.max(y1, at.getY());
			}
			Direction facing = end.getBlockState(flight.get(0)).getValue(StairBlock.FACING);
			boolean alongX = facing.getAxis() == Direction.Axis.X;
			int length = (alongX ? x1 - x0 : z1 - z0) + 1;
			int width = (alongX ? z1 - z0 : x1 - x0) + 1;
			if (flight.size() != 48 || length != 16 || width != 3 || y0 != low || y1 != high) {
				bad.append(" flight of ").append(flight.size()).append(" steps, ").append(length).append(" long, ").append(width).append(" wide, from ").append(y0).append(" to ").append(y1);
			}
			// The lanes lie against a wall line of the middle room: three cells off it, on the outside.
			int wallLow = alongX ? EndCastle.MIN_Z + room.z0() : EndCastle.MIN_X + room.x0();
			int wallHigh = alongX ? EndCastle.MIN_Z + room.z1() : EndCastle.MIN_X + room.x1();
			int from = alongX ? z0 : x0;
			if (from == wallLow - 3) {
				sides.add(alongX ? "north" : "west");
			} else if (from == wallHigh + 1) {
				sides.add(alongX ? "south" : "east");
			} else {
				bad.append(" a flight lies ").append(from).append(" off the walls ").append(wallLow).append(" and ").append(wallHigh);
			}
		}
		EndTestSupport.result("walkStairs", "flights=" + flights.size() + " sides=" + sides + " bad=" + bad.length());
		helper.assertTrue(flights.size() == 4, "floor 4 has " + flights.size() + " flights to floor 5");
		helper.assertTrue(sides.size() == 4, "the flights lie beside " + sides);
		helper.assertTrue(bad.isEmpty(), "flights off the plan:" + bad);
		helper.succeed();
	}
}
