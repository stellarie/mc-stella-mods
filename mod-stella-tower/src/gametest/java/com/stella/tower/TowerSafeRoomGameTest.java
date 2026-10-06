package com.stella.tower;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss floor's walled safe room, and the rule of every safe zone: no mob raised in it, none that walks
 * in stays, and a player inside draws no aggro from anything.
 */
public class TowerSafeRoomGameTest {
	private static final int S = TowerLayout.SIZE;
	private static final int[][] WAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	// ---------------------------------------------------------------- plan helpers

	private static long key(final int x, final int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static AABB zoneBox(final TowerPlan plan, final TowerMaze.Zone zone) {
		BlockPos o = plan.origin();
		return new AABB(o.getX() + zone.x(), o.getY() + zone.y() - 1, o.getZ() + zone.z(),
			o.getX() + zone.x() + TowerMaze.SAFE, o.getY() + zone.top(), o.getZ() + zone.z() + TowerMaze.SAFE);
	}

	private static boolean intersectsAnyZone(final TowerPlan plan, final AABB box) {
		for (TowerMaze.Zone zone : plan.safeZones()) {
			if (box.intersects(zoneBox(plan, zone))) {
				return true;
			}
		}
		return false;
	}

	private static boolean inHall(final TowerPlan plan, final int x, final int z) {
		return x >= plan.hallLow() && x < plan.hallLow() + plan.hall() && z >= plan.hallLow() && z < plan.hallLow() + plan.hall();
	}

	/** Cells reached from a start over the plan's open spots, none of the blocked ones entered. */
	private static boolean[][] reach(final TowerPlan plan, final int fromX, final int fromZ, final Set<Long> blocked) {
		boolean[][] seen = new boolean[S][S];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		seen[fromX][fromZ] = true;
		queue.add(new int[] {fromX, fromZ});
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : WAYS) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < S && z < S && !seen[x][z] && !plan.wall(x, z) && !plan.isFramePost(x, z) && !blocked.contains(key(x, z))) {
					seen[x][z] = true;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen;
	}

	private static Set<Long> zoneCells(final TowerMaze.Zone zone) {
		Set<Long> cells = new HashSet<>();
		for (int x = zone.x(); x < zone.x() + TowerMaze.SAFE; x++) {
			for (int z = zone.z(); z < zone.z() + TowerMaze.SAFE; z++) {
				cells.add(key(x, z));
			}
		}
		return cells;
	}

	private static Set<Long> gateCells(final TowerPlan.Gate gate) {
		Set<Long> cells = new HashSet<>();
		for (TowerPlan.Spot spot : gate.cells()) {
			cells.add(key(spot.x(), spot.z()));
		}
		return cells;
	}

	/** The open cells just beyond a gate's cells, on the side away from the room's middle. */
	private static List<TowerPlan.Spot> beyond(final TowerPlan plan, final TowerPlan.Gate gate) {
		TowerMaze.Zone room = plan.safeZones().get(1);
		Set<Long> cells = gateCells(gate);
		List<TowerPlan.Spot> outside = new ArrayList<>();
		for (TowerPlan.Spot spot : gate.cells()) {
			for (int[] way : WAYS) {
				int x = spot.x() + way[0];
				int z = spot.z() + way[1];
				if (!cells.contains(key(x, z)) && !plan.wall(x, z) && !room.contains(x, z)) {
					outside.add(new TowerPlan.Spot(x, z));
				}
			}
		}
		return outside;
	}

	/**
	 * What is wrong with a boss floor's walk from arrival through the room to the hall, as a list of faults; empty
	 * when the arrival reaches both gates' outer cells without entering the room, the hall is reached only through
	 * the room, the portal only through both gates, and each opening is two wide.
	 */
	static List<String> roomWalkFaults(final TowerPlan plan) {
		List<String> faults = new ArrayList<>();
		if (!plan.ring() || plan.gates().size() != 2) {
			faults.add("no ring or not two gates: ring=" + plan.ring() + " gates=" + plan.gates().size());
			return faults;
		}
		TowerMaze.Zone room = plan.safeZones().get(1);
		Set<Long> roomCells = zoneCells(room);
		TowerPlan.Gate entrance = plan.gates().stream().filter(gate -> !gate.exit()).findFirst().orElse(null);
		TowerPlan.Gate exit = plan.gates().stream().filter(TowerPlan.Gate::exit).findFirst().orElse(null);
		if (entrance == null || exit == null) {
			faults.add("gates are not one entrance and one exit");
			return faults;
		}
		boolean[][] mazeSide = reach(plan, plan.arrivalX(), plan.arrivalZ(), roomCells);
		boolean[][] whole = reach(plan, plan.arrivalX(), plan.arrivalZ(), Set.of());
		for (TowerPlan.Spot out : beyond(plan, entrance)) {
			if (!mazeSide[out.x()][out.z()]) {
				faults.add("the entrance's outer cell " + out + " is not reached from the arrival outside the room");
			}
		}
		if (beyond(plan, entrance).size() < 2) {
			faults.add("the entrance has " + beyond(plan, entrance).size() + " outer cells");
		}
		boolean hallFromMaze = false;
		for (int x = plan.hallLow(); x < plan.hallLow() + plan.hall(); x++) {
			for (int z = plan.hallLow(); z < plan.hallLow() + plan.hall(); z++) {
				hallFromMaze |= mazeSide[x][z];
			}
		}
		if (hallFromMaze) {
			faults.add("the hall is reached from the arrival without passing the room");
		}
		for (TowerPlan.Spot out : beyond(plan, exit)) {
			if (!whole[out.x()][out.z()] || !inHall(plan, out.x(), out.z())) {
				faults.add("the exit's outer cell " + out + " is not a hall cell reached through the room");
			}
		}
		if (beyond(plan, exit).size() < 2) {
			faults.add("the exit has " + beyond(plan, exit).size() + " outer cells");
		}
		if (!whole[plan.portalX()][plan.portalZ()]) {
			faults.add("the portal is not reached from the arrival");
		}
		for (TowerPlan.Gate gate : plan.gates()) {
			boolean[][] shut = reach(plan, plan.arrivalX(), plan.arrivalZ(), gateCells(gate));
			if (shut[plan.portalX()][plan.portalZ()]) {
				faults.add("the portal is reached with the " + (gate.exit() ? "exit" : "entrance") + " shut: a second way round");
			}
			Set<Integer> across = new TreeSet<>();
			Set<Integer> along = new TreeSet<>();
			for (TowerPlan.Spot spot : gate.cells()) {
				across.add(spot.x());
				along.add(spot.z());
			}
			// Two wide and, at the exit, two through the double wall: 2 x 2 cells; the entrance 2 x 1.
			int shape = across.size() * along.size();
			if (gate.cells().size() != (gate.exit() ? 4 : 2) || shape != gate.cells().size() || Math.max(across.size(), along.size()) != 2) {
				faults.add((gate.exit() ? "exit" : "entrance") + " is not two wide: " + gate.cells());
			}
		}
		return faults;
	}

	// ---------------------------------------------------------------- the ring

	private static boolean passable(final ServerLevel tower, final BlockPos pos) {
		return tower.getBlockState(pos).getCollisionShape(tower, pos).isEmpty();
	}

	private static boolean solid(final BlockState state) {
		return state.is(TowerBlocks.TOWER_WALL) || state.is(TowerBlocks.TOWER_LAMP);
	}

	/**
	 * The room's ring, as built on a floor of each of the four turns: solid from floor to ceiling but for two
	 * openings, two wide and two high, the one on the side facing the hall and the other opposite; their jambs
	 * and lintels lit with the tower lamp.
	 */
	@GameTest(maxTicks = 100)
	public void theBossRoomRingIsSolidExceptTwoLitOpenings(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int start = TowerTestSupport.freshBlock(400);
		int[] floorOfTurn = new int[4];
		java.util.Arrays.fill(floorOfTurn, -1);
		for (int floor = start; floor < start + 400 - 3; floor++) {
			if (TowerBuffs.isBossFloor(floor) && floorOfTurn[TowerPlan.of(seed, floor).rotation()] < 0) {
				floorOfTurn[TowerPlan.of(seed, floor).rotation()] = floor;
			}
		}
		int solidFaults = 0;
		int openColumns = 0;
		int lampFaults = 0;
		int gatesSeen = 0;
		int lamps = 0;
		int wrongGroups = 0;
		for (int turn = 0; turn < 4; turn++) {
			helper.assertTrue(floorOfTurn[turn] > 0, "no boss floor of turn " + turn + " in the block");
			int floor = floorOfTurn[turn];
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			helper.assertTrue(plan.ring() && plan.rotation() == turn, "floor " + floor + " has no ring at turn " + turn);
			BlockPos o = plan.origin();
			TowerMaze.Zone room = plan.safeZones().get(1);
			List<int[]> open = new ArrayList<>();
			for (int x = room.x(); x < room.x() + TowerMaze.SAFE; x++) {
				for (int z = room.z(); z < room.z() + TowerMaze.SAFE; z++) {
					boolean perimeter = x == room.x() || x == room.x() + TowerMaze.SAFE - 1 || z == room.z() || z == room.z() + TowerMaze.SAFE - 1;
					if (!perimeter) {
						continue;
					}
					boolean through = passable(tower, o.offset(x, 0, z)) && passable(tower, o.offset(x, 1, z));
					if (through) {
						open.add(new int[] {x, z});
					}
					for (int y = 0; y < TowerPlan.HALL_TOP; y++) {
						BlockState state = tower.getBlockState(o.offset(x, y, z));
						boolean mustBeSolid = !through || y >= 2;
						if (mustBeSolid && !solid(state)) {
							solidFaults++;
						}
					}
				}
			}
			openColumns += open.size();
			// Two groups of two, side by side, on opposite sides of the ring.
			Set<Integer> sides = new TreeSet<>();
			int adjacent = 0;
			for (int[] a : open) {
				sides.add(a[0] == room.x() ? 0 : a[0] == room.x() + TowerMaze.SAFE - 1 ? 1 : a[1] == room.z() ? 2 : 3);
				for (int[] b : open) {
					adjacent += Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == 1 ? 1 : 0;
				}
			}
			boolean opposite = sides.size() == 2 && (sides.contains(0) && sides.contains(1) || sides.contains(2) && sides.contains(3));
			wrongGroups += open.size() == 4 && adjacent == 4 && opposite ? 0 : 1;
			// The gates the plan names: open two high, a lamp lintel, lamp jambs, wall above.
			Set<Long> named = new HashSet<>();
			for (TowerPlan.Gate gate : plan.gates()) {
				gatesSeen++;
				for (TowerPlan.Spot cell : gate.cells()) {
					if (room.contains(cell.x(), cell.z())) {
						named.add(key(cell.x(), cell.z()));
					}
					lampFaults += passable(tower, o.offset(cell.x(), 0, cell.z())) && passable(tower, o.offset(cell.x(), 1, cell.z())) ? 0 : 1;
					lampFaults += tower.getBlockState(o.offset(cell.x(), 2, cell.z())).is(TowerBlocks.TOWER_LAMP) ? 0 : 1;
					lamps += tower.getBlockState(o.offset(cell.x(), 2, cell.z())).is(TowerBlocks.TOWER_LAMP) ? 1 : 0;
					for (int y = 3; y < TowerPlan.HALL_TOP; y++) {
						lampFaults += tower.getBlockState(o.offset(cell.x(), y, cell.z())).is(TowerBlocks.TOWER_WALL) ? 0 : 1;
					}
				}
				for (TowerPlan.Spot jamb : gate.jambs()) {
					for (int y = 0; y <= 2; y++) {
						boolean lamp = tower.getBlockState(o.offset(jamb.x(), y, jamb.z())).is(TowerBlocks.TOWER_LAMP);
						lampFaults += lamp ? 0 : 1;
						lamps += lamp ? 1 : 0;
					}
					for (int y = 3; y < TowerPlan.HALL_TOP; y++) {
						// A jamb in the hall's own wall keeps the maze's wall above its lamps.
						BlockState above = tower.getBlockState(o.offset(jamb.x(), y, jamb.z()));
						lampFaults += solid(above) || above.is(TowerBlocks.TOWER_MAZE_WALL) ? 0 : 1;
					}
				}
			}
			Set<Long> found = new HashSet<>();
			open.forEach(at -> found.add(key(at[0], at[1])));
			lampFaults += found.equals(named) ? 0 : 1;
		}
		TowerTestSupport.result("bossRoomRing", "turns=4 openColumns=" + openColumns + " gates=" + gatesSeen + " solidFaults=" + solidFaults
			+ " lampFaults=" + lampFaults + " lamps=" + lamps + " wrongGroups=" + wrongGroups);
		helper.assertTrue(gatesSeen == 8, "the four floors name " + gatesSeen + " gates, not 8");
		helper.assertTrue(solidFaults == 0, solidFaults + " ring blocks are not solid where they should be");
		helper.assertTrue(openColumns == 16 && wrongGroups == 0, "the rings open " + openColumns + " columns (not 16), " + wrongGroups + " floors with the wrong shape");
		helper.assertTrue(lampFaults == 0 && lamps > 0, lampFaults + " openings lack their lamps or their two-high clearance");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the walk and the hall's one door

	private static final int BOSS_FLOORS = 300;
	private static final int PLAN_SEEDS = 20;

	/** Every boss floor's walk: arrival to the room, through it, into the hall, to the portal; two-wide openings. */
	@GameTest
	public void everyBossFloorWalksFromArrivalThroughTheRoomToTheHall(final GameTestHelper helper) {
		int plans = 0;
		int faulty = 0;
		String first = "";
		Set<Integer> turns = new TreeSet<>();
		for (int i = 0; i < PLAN_SEEDS; i++) {
			long seed = 1_000_003L * i + 17L;
			for (int n = 1; n <= BOSS_FLOORS; n++) {
				TowerPlan plan = TowerPlan.of(seed, n * TowerElites.BOSS_EVERY);
				plans++;
				turns.add(plan.rotation());
				List<String> faults = roomWalkFaults(plan);
				if (!faults.isEmpty()) {
					faulty++;
					first = first.isEmpty() ? seed + "/" + plan.floor() + ": " + faults : first;
				}
			}
		}
		TowerTestSupport.result("bossRoomWalk", "plans=" + plans + " faulty=" + faulty + " turns=" + turns + " first=" + first);
		helper.assertTrue(plans >= 2000 && turns.size() == 4, "the case proves little: " + plans + " plans over turns " + turns);
		helper.assertTrue(faulty == 0, faulty + " boss plans fail the walk through the room, first " + first);
		helper.succeed();
	}

	/** The hall is enclosed but for the room: from inside it nothing of the maze is reached except through the room. */
	@GameTest
	public void aBossCannotReachTheMazeExceptThroughTheRoom(final GameTestHelper helper) {
		int plans = 0;
		int leaks = 0;
		int throughRoom = 0;
		int throughExit = 0;
		String first = "";
		for (int i = 0; i < PLAN_SEEDS; i++) {
			long seed = 1_000_003L * i + 17L;
			for (int n = 1; n <= BOSS_FLOORS; n++) {
				TowerPlan plan = TowerPlan.of(seed, n * TowerElites.BOSS_EVERY);
				plans++;
				TowerMaze.Zone room = plan.safeZones().get(1);
				int startX = plan.hallLow() + 1;
				int startZ = plan.hallLow() + 1;
				boolean[][] seen = reach(plan, startX, startZ, zoneCells(room));
				// The exit's cells in the hall's own wall are the door itself, not the maze.
				Set<Long> exitDoor = new HashSet<>();
				plan.gates().stream().filter(TowerPlan.Gate::exit).forEach(gate -> exitDoor.addAll(gateCells(gate)));
				int stray = 0;
				for (int x = 0; x < S; x++) {
					for (int z = 0; z < S; z++) {
						stray += seen[x][z] && !inHall(plan, x, z) && !exitDoor.contains(key(x, z)) ? 1 : 0;
					}
				}
				leaks += stray > 0 ? 1 : 0;
				if (stray > 0 && first.isEmpty()) {
					first = seed + "/" + plan.floor() + " strays=" + stray;
				}
				boolean[][] withRoom = reach(plan, startX, startZ, Set.of());
				throughRoom += withRoom[plan.arrivalX()][plan.arrivalZ()] ? 1 : 0;
				// With the exit shut the hall reaches nothing beyond itself even through the room's open side.
				Set<Long> shut = new HashSet<>();
				plan.gates().stream().filter(TowerPlan.Gate::exit).forEach(gate -> shut.addAll(gateCells(gate)));
				boolean[][] closed = reach(plan, startX, startZ, shut);
				int out = 0;
				for (int x = 0; x < S; x++) {
					for (int z = 0; z < S; z++) {
						out += closed[x][z] && !inHall(plan, x, z) ? 1 : 0;
					}
				}
				throughExit += out == 0 && !shut.isEmpty() ? 1 : 0;
			}
		}
		TowerTestSupport.result("hallEnclosed", "plans=" + plans + " leaks=" + leaks + " reachArrivalThroughRoom=" + throughRoom + " sealedByExit=" + throughExit + " first=" + first);
		helper.assertTrue(leaks == 0, leaks + " halls reach the maze without the room, first " + first);
		helper.assertTrue(throughRoom == plans, "only " + throughRoom + " of " + plans + " halls reach the arrival through the room");
		helper.assertTrue(throughExit == plans, "only " + throughExit + " of " + plans + " halls are sealed by their exit");
		helper.succeed();
	}

	// ---------------------------------------------------------------- spawns

	/** No spawn path of the floor ever lands a mob in a safe zone, on a floor of each turn and on the other kinds. */
	@GameTest(maxTicks = 200)
	public void noMobIsEverSpawnedInASafeZone(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		long seed = tower.getSeed();
		int start = TowerTestSupport.freshBlock(400);
		int[] floors = new int[6];
		java.util.Arrays.fill(floors, -1);
		for (int floor = start; floor < start + 400 - 3; floor++) {
			TowerPlan plan = TowerPlan.of(seed, floor);
			if (TowerBuffs.isBossFloor(floor) && floors[plan.rotation()] < 0) {
				floors[plan.rotation()] = floor;
			} else if (floor % TowerElites.ELITE_EVERY == 0 && floor % TowerElites.BOSS_EVERY != 0 && floors[4] < 0) {
				floors[4] = floor;
			} else if (floor % TowerElites.ELITE_EVERY != 0 && floors[5] < 0) {
				floors[5] = floor;
			}
		}
		Set<UUID> spawned = new HashSet<>();
		int inZone = 0;
		int placed = 0;
		int tries = 0;
		for (int floor : floors) {
			helper.assertTrue(floor > 0, "no floor of a kind in the block");
			TowerFloors.ensure(tower, floor);
			TowerPlan plan = TowerPlan.at(tower, floor);
			for (int i = 0; i < 150; i++) {
				tries++;
				Mob mob = TowerMobs.spawnOne(tower, floor);
				if (mob == null) {
					continue;
				}
				placed++;
				spawned.add(mob.getUUID());
				inZone += intersectsAnyZone(plan, mob.getBoundingBox()) ? 1 : 0;
				mob.discard();
			}
		}
		TowerTestSupport.result("zoneSpawns", "floors=" + floors.length + " tries=" + tries + " spawned=" + placed + " distinct=" + spawned.size() + " inZone=" + inZone);
		helper.assertTrue(placed >= tries / 2 && spawned.size() == placed, "the case proves little: " + placed + " of " + tries + " spawned");
		helper.assertTrue(inZone == 0, inZone + " mobs were raised in a safe zone");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the keep-out sweep

	/** A tower mob put inside a safe zone is out of it within one sweep. */
	@GameTest(maxTicks = 60)
	public void aTowerMobInsideASafeZoneIsSentOutWithinTheSweep(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerMaze.Zone zone = plan.safeZones().get(0);
		AABB box = zoneBox(plan, zone);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Vec3 away = TowerTestSupport.awayFromArrival(tower, floor, 12.0);
		player.teleportTo(tower, away.x, away.y, away.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, away);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.DARK);
		zombie.setNoAi(true);
		BlockPos o = plan.origin();
		zombie.snapTo(o.getX() + zone.x() + 3.5, TowerLayout.BASE_Y, o.getZ() + zone.z() + 3.5, 0.0F, 0.0F);
		tower.addFreshEntity(zombie);
		UUID id = zombie.getUUID();
		boolean inside = zombie.getBoundingBox().intersects(box);
		long[] outAt = {-1};
		helper.onEachTick(() -> {
			if (outAt[0] < 0 && tower.getEntity(id) instanceof Mob now && !now.getBoundingBox().intersects(box)) {
				outAt[0] = helper.getTick();
			}
		});
		long window = TowerSafety.SWEEP_TICKS + 1L;
		helper.runAfterDelay(window + 1L, () -> {
			Mob now = tower.getEntity(id) instanceof Mob mob ? mob : null;
			boolean alive = now != null && now.isAlive();
			boolean clear = alive && !now.getBoundingBox().intersects(box) && tower.noCollision(now);
			double moved = alive ? now.position().distanceTo(new Vec3(o.getX() + zone.x() + 3.5, TowerLayout.BASE_Y, o.getZ() + zone.z() + 3.5)) : -1.0;
			TowerTestSupport.result("sweepEjects", "startedInside=" + inside + " outAt=" + outAt[0] + " window=" + window + " alive=" + alive
				+ " clear=" + clear + " moved=" + moved + " sweepTicks=" + TowerSafety.SWEEP_TICKS);
			if (now != null) {
				now.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(inside, "the zombie did not start inside the zone");
			helper.assertTrue(outAt[0] >= 0 && outAt[0] <= window, "the zombie was out of the zone at tick " + outAt[0] + ", not within " + window);
			helper.assertTrue(clear, "the zombie was not left standing free outside the zone");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- no aggro inside a zone

	private static boolean aggro(final Mob mob, final ServerPlayer player) {
		if (mob.getTarget() == player || mob.getTargetUnchecked() == player) {
			return true;
		}
		if (!(mob instanceof net.minecraft.world.entity.monster.piglin.PiglinBrute)) {
			// Only a brain mob has the memories; asking any other for one is an error.
			return false;
		}
		if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(target -> target == player).isPresent()) {
			return true;
		}
		return mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).filter(player.getUUID()::equals).isPresent();
	}

	/**
	 * One spot a body of this size stands in and is seen from another: the cell's feet and head are open, the
	 * ground below holds it, and a body at one can see the other. The maze's walls are a plan of its ground
	 * story, so a plan-open cell can still be a wall's top: what stands there is read from the world.
	 */
	private static boolean standsAndSees(final ServerLevel tower, final Vec3 from, final Vec3 to) {
		if (!openAt(tower, from) || !openAt(tower, to)) {
			return false;
		}
		net.minecraft.world.level.ClipContext sight = new net.minecraft.world.level.ClipContext(from.add(0.0, 0.5, 0.0), to.add(0.0, 0.5, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty());
		return !tower.clip(sight).getType().equals(net.minecraft.world.phys.HitResult.Type.BLOCK);
	}

	/** Whether a two-high body stands free at a spot, on ground that holds it. */
	private static boolean openAt(final ServerLevel tower, final Vec3 at) {
		BlockPos feet = BlockPos.containing(at);
		return tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
			&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty()
			&& !tower.getBlockState(feet.below()).getCollisionShape(tower, feet.below()).isEmpty();
	}

	/**
	 * One mob against a player who steps into the entrance zone and out again. Outside, the mob takes the player
	 * on; inside, whatever it held is dropped within a sweep and nothing is taken up again; outside once more, it
	 * takes the player on again.
	 */
	private void noAggroCase(final GameTestHelper helper, final String name, final EntityType<? extends Mob> type, final boolean boss) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(boss ? TowerBuffs::isBossFloor : f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerTestSupport.ZoneSpots spots = TowerTestSupport.spotsBesideZone(plan);
		// The spots beside the zone are chosen from the plan; a plan-open cell can still be a wall's top, and
		// a wall between the two can hide one from the other. The nearest spot that both holds a body and is
		// seen from the mob's is taken, so the case measures the taking up, not where the floor grew.
		final Vec3 mobSpot = spots.mob();
		Vec3 outside = spots.outside();
		if (!standsAndSees(tower, mobSpot, outside)) {
			double best = Double.MAX_VALUE;
			BlockPos o = plan.origin();
			for (int x = 2; x < TowerLayout.SIZE - 2; x++) {
				for (int z = 2; z < TowerLayout.SIZE - 2; z++) {
					Vec3 tryIt = new Vec3(o.getX() + x + 0.5, TowerLayout.BASE_Y, o.getZ() + z + 0.5);
					double away = tryIt.distanceTo(mobSpot);
					if (away < 3.0 || away > 12.0 || away >= best || !standsAndSees(tower, mobSpot, tryIt)) {
						continue;
					}
					best = away;
					outside = tryIt;
				}
			}
		}
		final Vec3 watching = outside;
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.teleportTo(tower, watching.x, watching.y, watching.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, spots.mob());
		Mob mob;
		if (boss) {
			TowerElites.pinGuardianKind(floor, type);
			mob = TowerElites.rise(tower, floor, TowerRank.BOSS);
			helper.assertTrue(mob != null, "the boss could not rise, so the case proves nothing");
			mob.snapTo(spots.mob());
		} else {
			mob = type.create(tower, EntitySpawnReason.EVENT);
			TowerMobs.makeTowerMob(mob, floor, TowerElement.DARK);
			mob.snapTo(spots.mob());
			tower.addFreshEntity(mob);
		}
		helper.assertTrue(TowerGhosts.on(tower, floor).isEmpty(), "a ghost competes with the test player for aggro");
		TowerGhosts.pinRiseChance(floor, 0.0);
		UUID id = mob.getUUID();
		long window = TowerSafety.SWEEP_TICKS + 1L;
		long[] at = {-1, -1, -1, -1, -1};
		int[] counts = {0, 0};
		boolean[] finished = {false};
		helper.onEachTick(() -> {
			if (finished[0]) {
				return;
			}
			Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
			if (now == null || !now.isAlive()) {
				return;
			}
			player.setHealth(player.getMaxHealth());
			long tick = helper.getTick();
			boolean holds = aggro(now, player);
			if (at[0] < 0) {
				// Outside: wait for the mob to take the player on.
				if (holds) {
					at[0] = tick;
					player.teleportTo(tower, spots.inside().x, spots.inside().y, spots.inside().z, java.util.Set.of(), 0.0F, 0.0F, true);
					at[1] = tick;
				} else if (tick > 100) {
					at[0] = -2;
				}
			} else if (at[1] >= 0 && at[3] < 0) {
				// Inside: it must let go within a sweep, then never take up again.
				if (!holds && at[2] < 0) {
					at[2] = tick;
				}
				if (at[2] >= 0 && holds) {
					counts[0]++;
				}
				if (tick >= at[1] + window + 40) {
					// A mob with nothing to hunt wanders, and may round a corner and out of sight in forty ticks: it is
					// stood back where it was, so what the case measures is the taking up, not where it strolled.
					now.snapTo(spots.mob().x, spots.mob().y, spots.mob().z, now.getYRot(), now.getXRot());
					now.getNavigation().stop();
					player.teleportTo(tower, watching.x, watching.y, watching.z, java.util.Set.of(), 0.0F, 0.0F, true);
					at[3] = tick;
				}
			} else if (at[3] >= 0 && holds) {
				at[4] = tick;
				finished[0] = true;
			}
		});
		helper.runAfterDelay(340L, () -> {
			try {
				long acquired = at[0];
				long letGo = at[2] >= 0 ? at[2] - at[1] : -1;
				TowerTestSupport.result(name, "acquiredOutsideAt=" + acquired + " letGoAfter=" + letGo + " window=" + window + " reaggroInside=" + counts[0]
					+ " reacquiredAfter=" + (at[4] >= 0 ? at[4] - at[3] : -1));
				Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
				if (at[4] < 0 && now != null) {
					TowerTestSupport.result(name + "Diagnosis", "variation=" + plan.variation() + " floor=" + floor + " distance=" + now.distanceTo(player) + " sees=" + now.hasLineOfSight(player) + " target=" + now.getTarget()
						+ " alive=" + now.isAlive() + " at=" + now.blockPosition().subtract(plan.origin()) + " player=" + player.blockPosition().subtract(plan.origin()));
				}
				if (now != null) {
					now.discard();
				}
				TowerTestSupport.leave(helper, player);
				helper.assertTrue(acquired >= 0, "the " + name + " never took the player on outside the zone, so the case proves nothing");
				helper.assertTrue(at[2] >= 0 && letGo <= window, "the " + name + " let go of the player after " + letGo + " ticks, not within " + window);
				helper.assertTrue(counts[0] == 0, "the " + name + " took the player on " + counts[0] + " times inside the zone");
				helper.assertTrue(at[4] >= 0, "the " + name + " never took the player on again after the player stepped out");
				helper.succeed();
			} finally {
				TowerGhosts.pinRiseChance(floor, null);
				if (boss) {
					TowerElites.pinGuardianKind(floor, null);
				}
			}
		});
	}

	@GameTest(maxTicks = 360)
	public void aZombieDropsAPlayerInASafeZoneAndTakesThemUpAgainOutside(final GameTestHelper helper) {
		noAggroCase(helper, "zombieNoAggro", EntityTypes.ZOMBIE, false);
	}

	@GameTest(maxTicks = 360)
	public void aSkeletonDropsAPlayerInASafeZoneAndTakesThemUpAgainOutside(final GameTestHelper helper) {
		noAggroCase(helper, "skeletonNoAggro", EntityTypes.SKELETON, false);
	}

	@GameTest(maxTicks = 360)
	public void aPiglinBruteDropsAPlayerInASafeZoneAndTakesThemUpAgainOutside(final GameTestHelper helper) {
		noAggroCase(helper, "bruteNoAggro", EntityTypes.PIGLIN_BRUTE, false);
	}

	@GameTest(maxTicks = 360)
	public void aBossDropsAPlayerInASafeZoneAndTakesThemUpAgainOutside(final GameTestHelper helper) {
		noAggroCase(helper, "bossNoAggro", EntityTypes.ZOMBIE, true);
	}

	// ---------------------------------------------------------------- a boss at the door

	/**
	 * A free boss set at the hall's door, with a quarry beyond the room, walks for the room and is sent back at every
	 * sweep: it never gets further in than a sweep's walk, and never reaches the maze.
	 */
	@GameTest(maxTicks = 320)
	public void aBossAtTheHallsDoorIsKeptOutOfTheRoom(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		helper.assertTrue(plan.ring(), "the boss floor has no ring");
		TowerMaze.Zone room = plan.safeZones().get(1);
		AABB box = zoneBox(plan, room);
		BlockPos o = plan.origin();
		TowerPlan.Gate exit = plan.gates().stream().filter(TowerPlan.Gate::exit).findFirst().orElseThrow();
		TowerPlan.Gate entrance = plan.gates().stream().filter(gate -> !gate.exit()).findFirst().orElseThrow();
		TowerPlan.Spot door = exit.cells().stream().filter(cell -> !room.contains(cell.x(), cell.z())).findFirst().orElseThrow();
		TowerPlan.Spot quarry = beyond(plan, entrance).get(0);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Vec3 bait = new Vec3(o.getX() + quarry.x() + 0.5, TowerLayout.BASE_Y, o.getZ() + quarry.z() + 0.5);
		player.teleportTo(tower, bait.x, bait.y, bait.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, bait);
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		// The fastest boss a floor can make: speed five on top of the tower's own speed, and below a baby zombie's extra.
		TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.RESISTANCE));
		Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
		helper.assertTrue(boss != null, "the boss could not rise, so the case proves nothing");
		// A baby zombie is half as fast again: some boss floors raise one, and it is the one a sweep is hardest put to.
		((Zombie) boss).setBaby(true);
		boss.snapTo(o.getX() + door.x() + 0.5, TowerLayout.BASE_Y, o.getZ() + door.z() + 0.5, 0.0F, 0.0F);
		UUID id = boss.getUUID();
		Set<Long> gates = new HashSet<>();
		plan.gates().forEach(gate -> gates.addAll(gateCells(gate)));
		// The room's axis: from the middle of the exit's face to the middle of the entrance's.
		double sumX = 0.0;
		double sumZ = 0.0;
		int faceCells = 0;
		for (TowerPlan.Spot cell : exit.cells()) {
			if (room.contains(cell.x(), cell.z())) {
				sumX += cell.x() + 0.5;
				sumZ += cell.z() + 0.5;
				faceCells++;
			}
		}
		final double faceX = sumX / faceCells;
		final double faceZ = sumZ / faceCells;
		double toX = 0.0;
		double toZ = 0.0;
		for (TowerPlan.Spot cell : entrance.cells()) {
			toX += cell.x() + 0.5;
			toZ += cell.z() + 0.5;
		}
		final double doorX = toX / entrance.cells().size();
		final double doorZ = toZ / entrance.cells().size();
		final double roomLength = Math.hypot(doorX - faceX, doorZ - faceZ);
		final double alongX = (doorX - faceX) / roomLength;
		final double alongZ = (doorZ - faceZ) / roomLength;
		double[] depth = {0.0};
		int[] entries = {0};
		int[] run = {0};
		int[] longest = {0};
		int[] leaks = {0};
		boolean[] was = {false};
		helper.onEachTick(() -> {
			Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
			if (now == null || !now.isAlive()) {
				return;
			}
			player.setHealth(player.getMaxHealth());
			now.setTarget(player);
			boolean in = now.getBoundingBox().intersects(box);
			// How far along the room, from the exit's face toward the entrance, the boss has got.
			Vec3 at = now.position();
			depth[0] = in ? Math.max(depth[0], (at.x - o.getX() - faceX) * alongX + (at.z - o.getZ() - faceZ) * alongZ) : depth[0];
			run[0] = in ? run[0] + 1 : 0;
			longest[0] = Math.max(longest[0], run[0]);
			entries[0] += in && !was[0] ? 1 : 0;
			was[0] = in;
			BlockPos cell = now.blockPosition().subtract(o);
			boolean home = inHall(plan, cell.getX(), cell.getZ()) || gates.contains(key(cell.getX(), cell.getZ())) || room.contains(cell.getX(), cell.getZ());
			leaks[0] += home ? 0 : 1;
		});
		helper.runAfterDelay(300L, () -> {
			try {
				Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
				// A boss that keeps pushing is sent back at each sweep and walks in again at once, so it is in the room
				// between sweeps; what a sweep bounds is how far it gets: what it walks in one sweep, with half as much
				// again for a jump or a shove, and its own width; and it never crosses the room.
				double speed = now == null ? 0.0 : now.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
				double reach = TowerSafety.SWEEP_TICKS * speed * 1.5 + (now == null ? 0.0 : now.getBbWidth());
				TowerTestSupport.result("bossAtDoor", "entries=" + entries[0] + " longestInRoom=" + longest[0] + " deepest=" + depth[0] + " reach=" + reach
					+ " roomLength=" + roomLength + " leaks=" + leaks[0] + " alive=" + (now != null && now.isAlive()));
				if (now != null) {
					now.discard();
				}
				TowerTestSupport.leave(helper, player);
				helper.assertTrue(entries[0] >= 1, "the boss never tried the room, so the case proves nothing");
				helper.assertTrue(depth[0] <= reach && depth[0] < roomLength, "the boss got " + depth[0] + " blocks into a room " + roomLength + " long, more than the " + reach + " one sweep allows");
				helper.assertTrue(leaks[0] == 0, "the boss was out in the maze for " + leaks[0] + " ticks");
				helper.succeed();
			} finally {
				TowerElites.pinGuardianKind(floor, null);
				TowerBuffs.pinMobBuffs(floor, null);
			}
		});
	}

	/**
	 * A fast mob that drives into the room is deeper in it than the far side is near by the time a sweep comes: sent to
	 * the nearest spot outside it would go out through the far door, into the maze. It is sent back the way it came, to
	 * where it was last seen outside. A tower zombie is seen at the hall's door, then set five blocks into the room,
	 * nearer the entrance than the exit, as a mob that outran a sweep would be.
	 */
	@GameTest(maxTicks = 60)
	public void aMobDrivenDeepIntoTheRoomIsSentBackTheWayItCame(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		TowerMaze.Zone room = plan.safeZones().get(1);
		BlockPos o = plan.origin();
		TowerPlan.Gate exit = plan.gates().stream().filter(TowerPlan.Gate::exit).findFirst().orElseThrow();
		TowerPlan.Gate entrance = plan.gates().stream().filter(gate -> !gate.exit()).findFirst().orElseThrow();
		TowerPlan.Spot door = exit.cells().stream().filter(cell -> !room.contains(cell.x(), cell.z())).findFirst().orElseThrow();
		double sumX = 0.0;
		double sumZ = 0.0;
		int faceCells = 0;
		for (TowerPlan.Spot cell : exit.cells()) {
			if (room.contains(cell.x(), cell.z())) {
				sumX += cell.x() + 0.5;
				sumZ += cell.z() + 0.5;
				faceCells++;
			}
		}
		final double faceX = sumX / faceCells;
		final double faceZ = sumZ / faceCells;
		double toX = 0.0;
		double toZ = 0.0;
		for (TowerPlan.Spot cell : entrance.cells()) {
			toX += cell.x() + 0.5;
			toZ += cell.z() + 0.5;
		}
		final double length = Math.hypot(toX / entrance.cells().size() - faceX, toZ / entrance.cells().size() - faceZ);
		final double alongX = (toX / entrance.cells().size() - faceX) / length;
		final double alongZ = (toZ / entrance.cells().size() - faceZ) / length;
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Vec3 hall = new Vec3(o.getX() + plan.hallLow() + 3.5, TowerLayout.BASE_Y, o.getZ() + plan.hallLow() + 3.5);
		player.teleportTo(tower, hall.x, hall.y, hall.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, hall);
		Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(zombie, floor, TowerElement.DARK);
		zombie.setNoAi(true);
		zombie.snapTo(o.getX() + door.x() + 0.5, TowerLayout.BASE_Y, o.getZ() + door.z() + 0.5, 0.0F, 0.0F);
		tower.addFreshEntity(zombie);
		UUID id = zombie.getUUID();
		double deep = length - 2.0;
		long driveAt = 2L * TowerSafety.SWEEP_TICKS;
		helper.runAfterDelay(driveAt, () -> {
			Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
			helper.assertTrue(now != null, "the zombie is gone, so the case proves nothing");
			now.snapTo(o.getX() + faceX + alongX * deep, TowerLayout.BASE_Y, o.getZ() + faceZ + alongZ * deep, 0.0F, 0.0F);
			helper.assertTrue(now.getBoundingBox().intersects(zoneBox(plan, room)), "the zombie is not inside the room, so the case proves nothing");
		});
		helper.runAfterDelay(driveAt + TowerSafety.SWEEP_TICKS + 1L, () -> {
			Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
			double along = now == null ? Double.NaN : (now.getX() - o.getX() - faceX) * alongX + (now.getZ() - o.getZ() - faceZ) * alongZ;
			Set<Long> doorway = gateCells(exit);
			// Back on the hall's side: in the hall, or in the door through its wall.
			boolean inHall = now != null && (inHall(plan, now.getBlockX() - o.getX(), now.getBlockZ() - o.getZ())
				|| doorway.contains(key(now.getBlockX() - o.getX(), now.getBlockZ() - o.getZ())));
			boolean inside = now != null && now.getBoundingBox().intersects(zoneBox(plan, room));
			TowerTestSupport.result("sentBack", "deep=" + deep + " roomLength=" + length + " alongAfter=" + along + " inHall=" + inHall + " inside=" + inside);
			if (now != null) {
				now.discard();
			}
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(now != null && !inside, "the zombie was not sent out of the room");
			helper.assertTrue(along < 0.0 && inHall, "the zombie was sent " + along + " along the room (the hall is behind the exit, at less than 0), not back into the hall");
			helper.succeed();
		});
	}

	/** A boss with its AI on walks the hall: the old test held it with no AI, and proved only that nothing pulled it back. */
	@GameTest(maxTicks = 160)
	public void aFreeBossWithItsAiOnWalksTheHall(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerPlan plan = TowerPlan.at(tower, floor);
		BlockPos o = plan.origin();
		TowerElites.pinGuardianKind(floor, EntityTypes.ZOMBIE);
		Mob boss = TowerElites.rise(tower, floor, TowerRank.BOSS);
		helper.assertTrue(boss != null, "the boss could not rise, so the case proves nothing");
		// A quarry in the hall's corner, in the boss's sight, away from the portal's pyramid.
		int px = plan.hallLow() + 3;
		int pz = plan.hallLow() + 3;
		Vec3 corner = new Vec3(o.getX() + px + 0.5, TowerLayout.BASE_Y, o.getZ() + pz + 0.5);
		Vec3 from = boss.position();
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		player.teleportTo(tower, corner.x, corner.y, corner.z, java.util.Set.of(), 0.0F, 0.0F, true);
		TowerTestSupport.holdRoom(tower, corner);
		boolean noAi = boss.isNoAi();
		UUID id = boss.getUUID();
		double[] nearest = {from.distanceTo(corner)};
		double[] farthest = {0.0};
		helper.onEachTick(() -> {
			Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
			if (now == null) {
				return;
			}
			player.setHealth(player.getMaxHealth());
			now.setTarget(player);
			nearest[0] = Math.min(nearest[0], now.position().distanceTo(corner));
			farthest[0] = Math.max(farthest[0], now.position().distanceTo(from));
		});
		helper.runAfterDelay(120L, () -> {
			try {
				TowerTestSupport.result("bossWalksHall", "noAi=" + noAi + " startDistance=" + from.distanceTo(corner) + " nearest=" + nearest[0]
					+ " farthestFromStart=" + farthest[0]);
				Mob now = tower.getEntity(id) instanceof Mob m ? m : null;
				if (now != null) {
					now.discard();
				}
				TowerTestSupport.leave(helper, player);
				helper.assertFalse(noAi, "the boss has no AI, so the case proves nothing");
				helper.assertTrue(farthest[0] >= 3.0, "the boss walked only " + farthest[0] + " blocks from its post in the hall");
				helper.assertTrue(from.distanceTo(corner) - nearest[0] >= 3.0, "the boss closed only " + (from.distanceTo(corner) - nearest[0]) + " blocks on its quarry");
				helper.succeed();
			} finally {
				TowerElites.pinGuardianKind(floor, null);
			}
		});
	}
}
