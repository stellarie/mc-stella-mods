package com.stella.tower;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.ChunkEncounters;
import com.stella.mobs.behavior.EliteState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The spawner in every storey's turret: where it stands, how it is set, and the tower mobs it raises. */
public class TowerLostCastleSpawnerGameTest {
	/** The ticks the light engine takes to settle after a build, before any spawner is ticked. */
	private static final int SETTLE_TICKS = 30;
	private static final int MAX_SPAWNER_TICKS = 30_000;

	private interface Reading {
		String read(TowerLostRoomGameTest.Arena arena);
	}

	/** Builds a castle of every variant and facing and returns the first fault a reading finds. */
	private static String overCastles(final GameTestHelper helper, final Reading reading) {
		for (LostCastleVariant variant : LostCastleVariant.values()) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facing);
				String fault = reading.read(arena);
				if (!fault.isEmpty()) {
					return variant + " " + facing + ": " + fault;
				}
			}
		}
		return "";
	}

	/** The spawner's spot of a storey, in the world. */
	private static BlockPos spawnerAt(final LostRoomPiece piece, final LostCastlePlan.FloorPlan floor, final LostCastlePlan.Turret turret) {
		return piece.worldPos(turret.nook().tuckX(), TowerHex.floorAt(floor.storey()) + 2, turret.nook().tuckZ());
	}

	/** Records an arena's castle as worldgen would, with its grounds already stocked, so the spawner hook knows it. */
	private static void register(final TowerLostRoomGameTest.Arena arena) {
		BoundingBox box = arena.piece().getBoundingBox();
		LostRoomData.get(arena.level().getServer()).found(new LostRoomData.Castle(arena.level().dimension(),
			new BlockPos(box.minX(), box.minY(), box.minZ()), arena.piece().facing(), true, arena.piece().variant().ordinal()));
	}

	private static AABB castle(final LostRoomPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
	}

	private static AABB slab(final LostRoomPiece piece, final int storey) {
		BoundingBox box = piece.getBoundingBox();
		int low = box.minY() + TowerHex.floorAt(storey);
		return new AABB(box.minX(), low, box.minZ(), box.maxX() + 1, low + TowerHex.STOREY, box.maxZ() + 1);
	}

	private static int spawners(final ServerLevel level, final LostRoomPiece piece, final int storey) {
		AABB slab = slab(piece, storey);
		int count = 0;
		for (BlockPos at : BlockPos.betweenClosed((int) slab.minX, (int) slab.minY, (int) slab.minZ, (int) slab.maxX - 1, (int) slab.maxY - 1, (int) slab.maxZ - 1)) {
			count += level.getBlockState(at).is(Blocks.SPAWNER) ? 1 : 0;
		}
		return count;
	}

	/** The cover styles seen, across the castles read. */
	private static final Set<LostCastlePlan.Cover> COVERS = new HashSet<>();

	/** Whether each storey holds one spawner, set in the turret's tuck column and walled in on all six sides. */
	static String sealed(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			int count = spawners(level, piece, floor.storey());
			int wanted = floor.storey() == TowerHex.STOREYS - 1 ? TowerHex.cells().size() - 1 : 1;
			if (count != floor.turrets().size() || count != wanted) {
				return "storey " + floor.storey() + " holds " + count + " spawners for " + floor.turrets().size() + " turrets";
			}
			for (LostCastlePlan.Turret turret : floor.turrets()) {
			BlockPos at = spawnerAt(piece, floor, turret);
			if (!level.getBlockState(at).is(Blocks.SPAWNER) || !(level.getBlockEntity(at) instanceof SpawnerBlockEntity)) {
				return "storey " + floor.storey() + " has no spawner block entity at its turret";
			}
			int bars = 0;
			for (Direction side : Direction.values()) {
				BlockState next = level.getBlockState(at.relative(side));
				if (next.isAir()) {
					return "storey " + floor.storey() + " spawner touches air on its " + side;
				}
				bars += next.is(Blocks.IRON_BARS) ? 1 : 0;
			}
			TowerHex.Nook nook = turret.nook();
			boolean tell = level.getBlockState(piece.worldPos(nook.tuckX() + nook.dx(), TowerHex.floorAt(floor.storey()) + 2, nook.tuckZ() + nook.dz()))
				.is(piece.palette().accent());
			if (turret.cover() == LostCastlePlan.Cover.SOLID ? bars != 0 || !tell : bars != 1) {
				return "storey " + floor.storey() + " cover " + turret.cover() + " shows bars=" + bars + " tell=" + tell;
			}
			COVERS.add(turret.cover());
			}
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void eachStoreyHoldsOneSealedSpawner(final GameTestHelper helper) {
		COVERS.clear();
		String fault = overCastles(helper, TowerLostCastleSpawnerGameTest::sealed);
		TowerTestSupport.result("castleSpawnerSealed", "covers=" + COVERS + " fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.assertTrue(COVERS.size() == LostCastlePlan.Cover.values().length, "some cover styles never occur: " + COVERS);
		helper.succeed();
	}

	/** Whether each spawner is set as its storey kind says, with the shipped numbers and the light rule. */
	static String settings(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			for (LostCastlePlan.Turret turret : floor.turrets()) {
			SpawnerBlockEntity spawner = (SpawnerBlockEntity) level.getBlockEntity(spawnerAt(piece, floor, turret));
			CompoundTag tag = spawner.saveCustomOnly(level.registryAccess());
			Map<String, Integer> listed = new HashMap<>();
			ListTag potentials = tag.getListOrEmpty("SpawnPotentials");
			for (int i = 0; i < potentials.size(); i++) {
				CompoundTag entry = potentials.getCompoundOrEmpty(i);
				CompoundTag data = entry.getCompoundOrEmpty("data");
				CompoundTag entity = data.getCompoundOrEmpty("entity");
				if (entity.keySet().size() != 1 || !data.contains("custom_spawn_rules")) {
					return "storey " + floor.storey() + " entry " + i + " holds more than an id, or has no light rule: " + data;
				}
				String id = entity.getStringOr("id", "");
				if (id.equals("minecraft:creeper")) {
					return "storey " + floor.storey() + " lists a creeper";
				}
				listed.merge(id, entry.getIntOr("weight", 0), Integer::sum);
			}
			if (!listed.equals(LostCastleSpawner.table(floor.kind(), piece.variant()))) {
				return "storey " + floor.storey() + " " + floor.kind() + " lists " + listed + ", not " + LostCastleSpawner.table(floor.kind(), piece.variant());
			}
			int[][] numbers = {
				{tag.getIntOr("RequiredPlayerRange", -1), LostCastleSpawner.REQUIRED_PLAYER_RANGE},
				{tag.getIntOr("SpawnRange", -1), LostCastleSpawner.SPAWN_RANGE},
				{tag.getIntOr("SpawnCount", -1), LostCastleSpawner.SPAWN_COUNT},
				{tag.getIntOr("MaxNearbyEntities", -1), LostCastleSpawner.MAX_NEARBY_ENTITIES},
				{tag.getIntOr("MinSpawnDelay", -1), LostCastleSpawner.MIN_SPAWN_DELAY},
				{tag.getIntOr("MaxSpawnDelay", -1), LostCastleSpawner.MAX_SPAWN_DELAY}
			};
			for (int[] pair : numbers) {
				if (pair[0] != pair[1]) {
					return "storey " + floor.storey() + " has a number " + pair[0] + ", not " + pair[1];
				}
			}
			}
		}
		return "";
	}

	@GameTest(maxTicks = 400)
	public void spawnerSettingsMatchTheFloorKind(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleSpawnerGameTest::settings);
		TowerTestSupport.result("castleSpawnerSettings", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	/**
	 * Whether every open column a spawner can raise a mob in belongs to a way of the castle: a cell, or a
	 * doorway in the lattice wall. A column outside the octagon or in an unsealed corner cut would let a mob out.
	 */
	static String reach(final TowerLostRoomGameTest.Arena arena) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int open = 0;
		int reach = LostCastleSpawner.SPAWN_RANGE;
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			for (LostCastlePlan.Turret turret : floor.turrets()) {
			TowerHex.Nook nook = turret.nook();
			int sy = TowerHex.floorAt(floor.storey()) + 2;
			for (int x = nook.tuckX() - reach; x <= nook.tuckX() + reach; x++) {
				for (int z = nook.tuckZ() - reach; z <= nook.tuckZ() + reach; z++) {
					for (int y = sy - 1; y <= sy + 2; y++) {
						BlockPos at = piece.worldPos(x, y, z);
						if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
							continue;
						}
						open++;
						if (TowerHex.cellOf(x, z) == null && TowerHex.kind(x, z) != TowerHex.Part.WALL) {
							return "storey " + floor.storey() + " spawner reaches the open column " + x + "," + z + " at " + y + " that no way owns";
						}
					}
				}
			}
			}
		}
		return open == 0 ? "no spawner reaches any open space" : "";
	}

	@GameTest(maxTicks = 400)
	public void spawnerBoxReachesOnlyCellColumns(final GameTestHelper helper) {
		String fault = overCastles(helper, TowerLostCastleSpawnerGameTest::reach);
		TowerTestSupport.result("castleSpawnerReach", "fault=" + fault);
		helper.assertTrue(fault.isEmpty(), fault);
		helper.succeed();
	}

	private static List<Mob> tagged(final ServerLevel level, final LostRoomPiece piece, final int storey) {
		return level.getEntitiesOfClass(Mob.class, slab(piece, storey), mob -> mob.isAlive() && mob.entityTags().contains(LostCastles.SPAWN_TAG));
	}

	/** The mobs a spawner of the castle has raised near itself. */
	private static List<Mob> raisedNear(final ServerLevel level, final BlockPos at) {
		return level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(LostCastleSpawner.SPAWN_RANGE + 2.0, 3.0, LostCastleSpawner.SPAWN_RANGE + 2.0),
			mob -> mob.isAlive() && mob.entityTags().contains(LostCastles.SPAWN_TAG));
	}

	/** Ticks a spawner until it has raised a mob near itself, or the budget runs out; the ticks spent. */
	private static int fire(final ServerLevel level, final BlockPos at) {
		SpawnerBlockEntity spawner = (SpawnerBlockEntity) level.getBlockEntity(at);
		int ticks = 0;
		while (ticks < MAX_SPAWNER_TICKS && (ticks % 20 != 0 || raisedNear(level, at).isEmpty())) {
			SpawnerBlockEntity.serverTick(level, at, level.getBlockState(at), spawner);
			ticks++;
		}
		return ticks;
	}

	@GameTest(maxTicks = 100)
	public void aStoreySpawnerRaisesTowerMobsInsideTheCastle(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, LostCastleVariant.KEEP, Direction.NORTH);
		register(arena);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		// A player arrives in another dimension on the tick after the move, so each turret gets its own player now.
		List<ServerPlayer> players = new ArrayList<>();
		for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
			for (LostCastlePlan.Turret turret : floor.turrets()) {
				TowerHex.Nook nook = turret.nook();
				BlockPos stand = piece.worldPos(nook.tuckX() + 2 * nook.dx(), TowerHex.floorAt(floor.storey()) + 1, nook.tuckZ() + 2 * nook.dz());
				ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
				player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				players.add(player);
			}
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			StringBuilder report = new StringBuilder();
			int turrets = 0;
			for (LostCastlePlan.FloorPlan floor : piece.plan().floors()) {
				for (LostCastlePlan.Turret turret : floor.turrets()) {
					turrets++;
					BlockPos at = spawnerAt(piece, floor, turret);
					int ticks = fire(level, at);
					List<Mob> raised = raisedNear(level, at);
					helper.assertTrue(!raised.isEmpty(), "storey " + floor.storey() + " turret at " + at + " raised no mob in " + ticks + " ticks");
					Set<String> allowed = LostCastleSpawner.table(floor.kind(), piece.variant()).keySet();
					for (Mob mob : raised) {
						TowerMobs.State state = TowerMobs.stateOf(mob);
						String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
						helper.assertTrue(state != null && state.rank() == TowerRank.MOB, "a spawner mob is not a tower mob of rank MOB: " + id);
						helper.assertTrue(mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) && mob.entityTags().contains(LostRooms.TAG), "a spawner mob lacks its tags");
						helper.assertTrue(allowed.contains(id), id + " is not in the " + floor.kind() + " table " + allowed);
						// A mob may drop through a ruined floor gap, so the bound is the castle, not its storey.
						helper.assertTrue(castle(piece).contains(mob.position()), "a spawner mob stands outside the castle at " + mob.blockPosition());
						report.append(" [storey ").append(floor.storey()).append(' ').append(id).append(" floor=").append(state.floor()).append(" ticks=").append(ticks).append(']');
						mob.discard();
					}
				}
			}
			TowerTestSupport.result("castleSpawnerRaises", "turrets=" + turrets + report);
			helper.assertTrue(turrets == 1 + 1 + TowerHex.cells().size() - 1, "the castle holds " + turrets + " turrets");
			players.forEach(player -> TowerTestSupport.leave(helper, player));
			helper.succeed();
		});
	}

	private static Mob spawnerMob(final ServerLevel level, final EntityType<? extends Mob> type, final EntitySpawnReason reason, final BlockPos feet) {
		Mob mob = type.create(level, reason);
		mob.snapTo(Vec3.atBottomCenterOf(feet));
		return mob;
	}

	/** A spot of a storey's cell middle, in the open, for a mob to stand in. */
	private static BlockPos middle(final LostRoomPiece piece, final int storey, final int cell) {
		TowerHex.Cell at = TowerHex.cells().get(cell);
		return piece.worldPos(at.middle().getX(), TowerHex.floorAt(storey) + 1, at.middle().getZ());
	}

	@GameTest(maxTicks = 100)
	public void spawnerMobsStopAtTheStoreyCap(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena inside = TowerLostRoomGameTest.castle(helper, LostCastleVariant.CRYPT, Direction.EAST);
		TowerLostRoomGameTest.Arena outside = TowerLostRoomGameTest.castle(helper, LostCastleVariant.CRYPT, Direction.EAST);
		register(inside);
		ServerLevel level = inside.level();
		LostRoomPiece piece = inside.piece();
		List<Mob> all = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(piece, 0, i % TowerHex.cells().size()));
			all.add(mob);
			level.addFreshEntity(mob);
		}
		for (int i = 0; i < 3; i++) {
			Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(piece, 1, i));
			all.add(mob);
			level.addFreshEntity(mob);
		}
		for (int i = 0; i < 20; i++) {
			Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(piece, 2, 2 + i % 2));
			all.add(mob);
			level.addFreshEntity(mob);
		}
		int ground = tagged(level, piece, 0).size();
		int upper = tagged(level, piece, 1).size();
		int top = tagged(level, piece, 2).size();
		Mob event = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.EVENT, middle(piece, 2, 0));
		TowerTestSupport.unrolled(event);
		level.addFreshEntity(event);
		Mob away = spawnerMob(outside.level(), EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(outside.piece(), 0, 0));
		TowerTestSupport.unrolled(away);
		level.addFreshEntity(away);
		Mob known = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(piece, 2, 1));
		TowerMobs.makeTowerMob(known, 7, TowerElement.values()[0], TowerRank.MOB, 9);
		level.addFreshEntity(known);
		TowerMobs.State kept = TowerMobs.stateOf(known);
		TowerTestSupport.result("castleSpawnerCap", "ground=" + ground + " upper=" + upper + " top=" + top + " eventTagged=" + event.entityTags().contains(LostCastles.SPAWN_TAG)
			+ " awayTagged=" + away.entityTags().contains(LostCastles.SPAWN_TAG) + " keptFloor=" + (kept == null ? -1 : kept.floor()));
		boolean eventUntouched = TowerMobs.stateOf(event) == null && !event.entityTags().contains(LostCastles.SPAWN_TAG);
		boolean awayUntouched = TowerMobs.stateOf(away) == null && !away.entityTags().contains(LostCastles.SPAWN_TAG);
		boolean knownUntouched = kept != null && kept.floor() == 7 && !known.entityTags().contains(LostCastles.SPAWN_TAG);
		all.add(event);
		all.add(away);
		all.add(known);
		all.forEach(Mob::discard);
		helper.assertTrue(ground == LostCastles.storeyCap(0), "the ground storey holds " + ground + " spawner mobs, not the cap " + LostCastles.storeyCap(0));
		helper.assertTrue(top == LostCastles.storeyCap(TowerHex.STOREYS - 1), "the top storey holds " + top + " spawner mobs, not the cap " + LostCastles.storeyCap(TowerHex.STOREYS - 1));
		helper.assertTrue(LostCastles.storeyCap(0) == 6 && LostCastles.storeyCap(1) == 6 && LostCastles.storeyCap(TowerHex.STOREYS - 1) == 10, "the shipped caps are not 6, 6 and 10");
		helper.assertTrue(upper == 3, "the next storey's three mobs were cut to " + upper + ": the cap is per storey");
		helper.assertTrue(eventUntouched, "a mob that no spawner raised was changed");
		helper.assertTrue(awayUntouched, "a spawner mob outside every castle was changed");
		helper.assertTrue(knownUntouched, "a mob that was a tower mob already was changed again");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aSpawnerMobIsNeverAnEliteOrABoss(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena inside = TowerLostRoomGameTest.castle(helper, LostCastleVariant.MAZE, Direction.SOUTH);
		TowerLostRoomGameTest.Arena outside = TowerLostRoomGameTest.castle(helper, LostCastleVariant.MAZE, Direction.SOUTH);
		register(inside);
		ServerLevel level = inside.level();
		List<Mob> raised = new ArrayList<>();
		List<Mob> control = new ArrayList<>();
		// The chunk elite tag forces an elite roll on any mob the mobs mod rolls, so only the hook's early order saves these.
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			for (int i = 0; i < 4; i++) {
				Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(inside.piece(), storey, i));
				mob.addTag(ChunkEncounters.CHUNK_ELITE_TAG);
				raised.add(mob);
				level.addFreshEntity(mob);
			}
		}
		for (int i = 0; i < 3; i++) {
			Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(outside.piece(), 0, i));
			mob.addTag(ChunkEncounters.CHUNK_ELITE_TAG);
			control.add(mob);
			level.addFreshEntity(mob);
		}
		int elites = 0;
		int bosses = 0;
		int towerMobs = 0;
		for (Mob mob : raised) {
			elites += EliteState.hasEliteFlag(mob) ? 1 : 0;
			bosses += EliteState.hasBossFlag(mob) ? 1 : 0;
			TowerMobs.State state = TowerMobs.stateOf(mob);
			towerMobs += state != null && state.rank() == TowerRank.MOB && mob.isAlive() ? 1 : 0;
		}
		int controlElites = 0;
		for (Mob mob : control) {
			controlElites += EliteState.hasEliteFlag(mob) ? 1 : 0;
		}
		TowerTestSupport.result("castleSpawnerNoElite", "raised=" + raised.size() + " elites=" + elites + " bosses=" + bosses + " towerMobs=" + towerMobs
			+ " controlElites=" + controlElites + "/" + control.size());
		raised.forEach(Mob::discard);
		control.forEach(Mob::discard);
		helper.assertTrue(controlElites > 0, "the control never rolled an elite, so this test proves nothing");
		helper.assertTrue(elites == 0 && bosses == 0, "a spawner mob rolled " + elites + " elites and " + bosses + " bosses");
		helper.assertTrue(towerMobs == raised.size(), "only " + towerMobs + " of " + raised.size() + " spawner mobs are tower mobs");
		helper.succeed();
	}
}
