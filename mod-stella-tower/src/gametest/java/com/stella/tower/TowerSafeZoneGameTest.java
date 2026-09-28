package com.stella.tower;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** A floor's safe zones, and the ways round the maze the tower allows and refuses. */
public class TowerSafeZoneGameTest {
	private static final int S = TowerLayout.SIZE;

	/** Open at a spot of the room: the feet and head blocks both let a body through. */
	private static boolean[][] open(final ServerLevel tower, final int floor) {
		BlockPos o = TowerLayout.origin(floor);
		boolean[][] open = new boolean[S][S];
		for (int x = 0; x < S; x++) {
			for (int z = 0; z < S; z++) {
				BlockPos feet = o.offset(x, 0, z);
				open[x][z] = tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
					&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty();
			}
		}
		return open;
	}

	private static int steps(final boolean[][] open, final int fromX, final int fromZ, final int toX, final int toZ) {
		int[][] seen = new int[S][S];
		for (int[] row : seen) {
			Arrays.fill(row, -1);
		}
		Deque<int[]> queue = new ArrayDeque<>();
		seen[fromX][fromZ] = 0;
		queue.add(new int[] {fromX, fromZ});
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!queue.isEmpty()) {
			int[] at = queue.poll();
			for (int[] way : ways) {
				int x = at[0] + way[0];
				int z = at[1] + way[1];
				if (x >= 0 && z >= 0 && x < S && z < S && open[x][z] && seen[x][z] < 0) {
					seen[x][z] = seen[at[0]][at[1]] + 1;
					queue.add(new int[] {x, z});
				}
			}
		}
		return seen[toX][toZ];
	}

	/** Walled spots of a zone, and its floor blocks not laid as a safe zone's. */
	private static int[] zoneFaults(final ServerLevel tower, final int floor, final TowerMaze.Zone zone) {
		boolean[][] open = open(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		int shut = 0;
		int unmarked = 0;
		for (int x = zone.x(); x < zone.x() + TowerMaze.SAFE; x++) {
			for (int z = zone.z(); z < zone.z() + TowerMaze.SAFE; z++) {
				BlockPos at = o.offset(x, 0, z);
				boolean station = TowerFloors.workstations(floor).containsKey(at);
				shut += open[x][z] || station ? 0 : 1;
				unmarked += tower.getBlockState(at.below()).is(TowerFloors.SAFE_FLOOR) ? 0 : 1;
			}
		}
		return new int[] {shut, unmarked};
	}

	@GameTest
	public void everyFloorOpensASafeZoneAtItsEntrance(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(f -> f % TowerElites.ELITE_EVERY != 0);
		TowerFloors.ensure(tower, floor);
		TowerMaze.Zone zone = TowerMaze.safeZones(floor).get(0);
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(floor)).subtract(TowerLayout.origin(floor));
		int[] faults = zoneFaults(tower, floor, zone);
		TowerTestSupport.result("entranceSafe", "zone=" + zone + " arrival=" + arrival + " shut=" + faults[0] + " unmarked=" + faults[1]);
		helper.assertTrue(zone.contains(arrival.getX(), arrival.getZ()), "the entrance zone " + zone + " does not hold the arrival " + arrival);
		helper.assertTrue(faults[0] == 0 && faults[1] == 0, "the entrance zone has " + faults[0] + " walled and " + faults[1] + " unmarked spots");
		helper.succeed();
	}

	/** A boss floor built before the safe zones gets them on the next arrival, and what a player built there stays. */
	@GameTest
	public void anOlderFloorGetsItsSafeZonesOnArrival(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		BlockPos o = TowerLayout.origin(floor);
		// Made old: no safe-zone mark, maze walls standing in both zones, plain floor, no workstations.
		tower.setBlock(TowerFloors.settledMarker(floor), Blocks.BEDROCK.defaultBlockState(), 3);
		for (TowerMaze.Zone zone : TowerMaze.safeZones(floor)) {
			for (int x = zone.x(); x < zone.x() + TowerMaze.SAFE; x++) {
				for (int z = zone.z(); z < zone.z() + TowerMaze.SAFE; z++) {
					// The floor's own stone, as an older floor was laid.
					tower.setBlock(o.offset(x, -1, z), TowerFloors.palette(floor)[0], 3);
					BlockState column = x % TowerMaze.PITCH == 0 ? TowerBlocks.TOWER_MAZE_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState();
					for (int y = 0; y < TowerLayout.HEIGHT; y++) {
						tower.setBlock(o.offset(x, y, z), column, 3);
					}
				}
			}
		}
		TowerFloors.workstations(floor).keySet().forEach(pos -> tower.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
		BlockPos built = o.offset(TowerMaze.ENTRANCE_X + 1, 0, TowerMaze.SAFE_Z + 1);
		tower.setBlock(built, Blocks.COBBLESTONE.defaultBlockState(), 3);
		// A player's chest where a station would stand, and a block of their own in the zone's floor.
		BlockPos chestAt = TowerFloors.workstations(floor).keySet().iterator().next();
		tower.setBlock(chestAt, Blocks.CHEST.defaultBlockState(), 3);
		BlockPos goldAt = o.offset(TowerMaze.ENTRANCE_X + 2, -1, TowerMaze.SAFE_Z + 2);
		tower.setBlock(goldAt, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		TowerFloors.ensure(tower, floor);
		int[] entrance = zoneFaults(tower, floor, TowerMaze.safeZones(floor).get(0));
		int[] room = zoneFaults(tower, floor, TowerMaze.safeZones(floor).get(1));
		long stations = TowerFloors.workstations(floor).entrySet().stream()
			.filter(entry -> tower.getBlockState(entry.getKey()).equals(entry.getValue())).count();
		boolean kept = tower.getBlockState(built).is(Blocks.COBBLESTONE) && tower.getBlockState(chestAt).is(Blocks.CHEST)
			&& tower.getBlockState(goldAt).is(Blocks.GOLD_BLOCK);
		TowerTestSupport.result("olderFloorSettled", "entranceShut=" + entrance[0] + " entranceUnmarked=" + entrance[1] + " roomShut=" + room[0]
			+ " roomUnmarked=" + room[1] + " stations=" + stations + " kept=" + kept);
		// The cobblestone is the one walled spot the entrance keeps, and the gold its one unmarked floor block;
		// the chest stands where a station would have.
		helper.assertTrue(entrance[0] == 1 && room[0] == 0, "the older floor kept " + entrance[0] + " and " + room[0] + " walled spots in its zones");
		helper.assertTrue(entrance[1] == 1 && room[1] == 0 && stations == 2, "the older floor's zones: unmarked " + entrance[1] + "/" + room[1] + ", stations " + stations);
		helper.assertTrue(kept, "the settling cleared a block a player built");
		helper.succeed();
	}

	/** A boss floor's hall is reached only through its safe room, which holds a crafting table, a furnace and an anvil. */
	@GameTest
	public void aBossFloorsHallIsReachedThroughItsSafeRoom(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		helper.assertTrue(TowerMaze.safeZones(floor).size() == 2, "a boss floor has " + TowerMaze.safeZones(floor).size() + " safe zones, not 2");
		TowerMaze.Zone room = TowerMaze.safeZones(floor).get(1);
		int[] faults = zoneFaults(tower, floor, room);
		long stations = TowerFloors.workstations(floor).entrySet().stream()
			.filter(entry -> tower.getBlockState(entry.getKey()).equals(entry.getValue())).count();
		BlockPos o = TowerLayout.origin(floor);
		BlockPos arrival = BlockPos.containing(TowerLayout.arrival(floor)).subtract(o);
		BlockPos portal = TowerLayout.portalFrame(floor).get(0).subtract(o).east();
		boolean[][] open = open(tower, floor);
		int way = steps(open, arrival.getX(), arrival.getZ(), portal.getX(), portal.getZ());
		for (int x = room.x(); x < room.x() + TowerMaze.SAFE; x++) {
			for (int z = room.z(); z < room.z() + TowerMaze.SAFE; z++) {
				open[x][z] = false;
			}
		}
		int around = steps(open, arrival.getX(), arrival.getZ(), portal.getX(), portal.getZ());
		TowerTestSupport.result("bossSafeRoom", "room=" + room + " shut=" + faults[0] + " unmarked=" + faults[1] + " stations=" + stations
			+ " way=" + way + " around=" + around);
		helper.assertTrue(faults[0] == 0 && faults[1] == 0, "the safe room has " + faults[0] + " walled and " + faults[1] + " unmarked spots");
		helper.assertTrue(stations == 3 && TowerFloors.workstations(floor).size() == 3, "the safe room holds " + stations + " of its 3 workstations");
		helper.assertTrue(way > 0 && around < 0, "the way to the boss is " + way + " steps, and " + around + " around the safe room");
		helper.succeed();
	}

	/** On a boss floor every open spot of the maze is still reached from the arrival, though the hall is walled off. */
	@GameTest
	public void aBossFloorsMazeLeavesNoSpotCutOff(final GameTestHelper helper) {
		long seed = helper.getLevel().getSeed();
		int cut = 0;
		int floors = 0;
		for (int floor = TowerElites.BOSS_EVERY; floor <= 200 * TowerElites.BOSS_EVERY; floor += TowerElites.BOSS_EVERY) {
			boolean[][] walls = TowerMaze.walls(seed, floor);
			boolean[][] seen = new boolean[S][S];
			Deque<int[]> queue = new ArrayDeque<>();
			queue.add(new int[] {TowerLayout.ARRIVAL_X, TowerLayout.MIDDLE_Z});
			seen[TowerLayout.ARRIVAL_X][TowerLayout.MIDDLE_Z] = true;
			int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			while (!queue.isEmpty()) {
				int[] at = queue.poll();
				for (int[] way : ways) {
					int x = at[0] + way[0];
					int z = at[1] + way[1];
					if (x >= 0 && z >= 0 && x < S && z < S && !walls[x][z] && !seen[x][z]) {
						seen[x][z] = true;
						queue.add(new int[] {x, z});
					}
				}
			}
			for (int x = 0; x < S; x++) {
				for (int z = 0; z < S; z++) {
					cut += !walls[x][z] && !seen[x][z] ? 1 : 0;
				}
			}
			floors++;
		}
		TowerTestSupport.result("bossMazeWhole", "floors=" + floors + " cut=" + cut);
		helper.assertTrue(cut == 0, cut + " open spots across " + floors + " boss floors lie cut off from the arrival");
		helper.succeed();
	}

	/** A boss floor's furnace and anvil face into the room, not the wall behind them. */
	@GameTest
	public void theSafeRoomsStationsFaceTheRoom(final GameTestHelper helper) {
		int floor = TowerElites.BOSS_EVERY;
		long facingWall = TowerFloors.workstations(floor).values().stream()
			.filter(state -> state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) != net.minecraft.core.Direction.SOUTH)
			.count();
		TowerTestSupport.result("stationFacing", "facingWall=" + facingWall);
		helper.assertTrue(facingWall == 0, facingWall + " stations face the wall");
		helper.succeed();
	}

	/** No mob is raised in a safe zone, though the floor around it fills. */
	@GameTest
	public void noMobIsRaisedInASafeZone(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		RandomSource random = RandomSource.create(29L);
		BlockPos o = TowerLayout.origin(floor);
		int placed = 0;
		int inZone = 0;
		for (int i = 0; i < 400; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(tower, EntitySpawnReason.COMMAND);
			if (TowerMobs.placeInRoom(tower, floor, zombie, random)) {
				placed++;
				BlockPos at = zombie.blockPosition().subtract(o);
				inZone += TowerMaze.inSafeZone(floor, at.getX(), at.getZ()) ? 1 : 0;
			}
			zombie.discard();
		}
		TowerTestSupport.result("safeSpawns", "placed=" + placed + " inZone=" + inZone);
		helper.assertTrue(placed >= 200, "only " + placed + " of 400 zombies found room, so the case proves little");
		helper.assertTrue(inZone == 0, inZone + " zombies were raised in a safe zone");
		helper.succeed();
	}

	/**
	 * Chorus fruit eaten mid-jump from a pillar lands in the maze, never on the roof and never in another
	 * floor. From the floor the fruit's reach stops at the ceiling; three blocks up it tops the roof.
	 */
	@GameTest
	public void chorusFruitEatenFromAPillarNeverLandsOnTheRoof(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		Vec3 arrival = TowerLayout.arrival(floor);
		int moved = 0;
		int roof = 0;
		int astray = 0;
		double highest = 0.0;
		double eatenAt = TowerLayout.BASE_Y + 3.25;
		for (int i = 0; i < 128; i++) {
			player.teleportTo(tower, arrival.x, eatenAt, arrival.z, java.util.Set.of(), 0.0F, 0.0F, true);
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(Items.CHORUS_FRUIT.getDefaultInstance()));
			new ItemStack(Items.CHORUS_FRUIT).finishUsingItem(tower, player);
			if (player.position().distanceTo(new Vec3(arrival.x, eatenAt, arrival.z)) > 0.5) {
				moved++;
			}
			highest = Math.max(highest, player.getY());
			roof += player.getY() >= TowerLayout.BASE_Y + TowerLayout.HEIGHT ? 1 : 0;
			astray += player.level() == tower && TowerLayout.floorAt(player.blockPosition()) == floor ? 0 : 1;
		}
		TowerTestSupport.result("chorusMidJump", "moved=" + moved + " roof=" + roof + " astray=" + astray + " highest=" + highest);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(moved > 0, "no fruit moved the player, so the case proves nothing");
		helper.assertTrue(roof == 0 && astray == 0, roof + " fruits landed on the roof and " + astray + " off the floor; highest y " + highest);
		helper.succeed();
	}

	/** A bed in the tower does nothing: no sleep, no spawn point, and no blast. */
	@GameTest
	public void aBedInTheTowerDoesNothing(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloor();
		TowerFloors.ensure(tower, floor);
		ServerPlayer player = TowerTestSupport.onFloor(helper, tower, floor);
		BlockPos foot = BlockPos.containing(TowerLayout.arrival(floor)).east();
		BlockPos head = foot.north();
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		tower.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
		tower.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
		float health = player.getHealth();
		tower.getBlockState(foot).useWithoutItem(tower, player, new BlockHitResult(Vec3.atCenterOf(foot), Direction.UP, foot, false));
		boolean standing = tower.getBlockState(foot).is(Blocks.BED.red()) && tower.getBlockState(head).is(Blocks.BED.red());
		boolean sleeping = player.isSleeping();
		boolean spawnSet = player.getRespawnConfig() != null && player.getRespawnConfig().respawnData().dimension().equals(TowerTravel.TOWER);
		TowerTestSupport.result("towerBed", "standing=" + standing + " sleeping=" + sleeping + " spawnSet=" + spawnSet + " hurt=" + (health - player.getHealth()));
		tower.setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
		tower.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(standing && !sleeping && !spawnSet, "a tower bed: standing=" + standing + " sleeping=" + sleeping + " spawnSet=" + spawnSet);
		helper.succeed();
	}
}
