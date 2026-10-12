package com.stella.tower;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.ChunkEncounters;
import com.stella.mobs.behavior.EliteState;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The spawners set in every floor's walls: how each is set, the tower mobs it raises, and the cap on a floor. */
public class TowerLostCastleSpawnerGameTest {
	/** The ticks the light engine takes to settle after a build, before any spawner is ticked. */
	private static final int SETTLE_TICKS = 30;
	private static final int MAX_SPAWNER_TICKS = 30_000;

	/** The spawner's spot of a floor, in the world. */
	private static BlockPos spawnerAt(final LostRoomPiece piece, final LostCastlePlan.Level floor, final LostCastlePlan.Spawner spawner) {
		return piece.worldPos(spawner.x(), LostCastleGrid.feet(piece.floors(), floor.index()) + 2, spawner.z());
	}

	/** The box of one floor of the castle, in the world. */
	private static AABB slab(final LostRoomPiece piece, final int floor) {
		net.minecraft.world.level.levelgen.structure.BoundingBox box = piece.getBoundingBox();
		int low = box.minY() + LostCastleGrid.slabY(piece.floors(), floor);
		return new AABB(box.minX(), low, box.minZ(), box.maxX() + 1, low + LostCastleGrid.FLOOR_HEIGHT, box.maxZ() + 1);
	}

	/**
	 * Catches a spawner set with another floor kind's mobs, one that lists a creeper or a mob with more than its id,
	 * one that lacks the light rule, and numbers other than the shipped ones.
	 */
	@GameTest(maxTicks = 200)
	public void spawnerSettingsMatchTheFloorKind(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.SNOW, Direction.EAST, LostCastleGrid.MAX_FLOORS);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		List<String> faults = new ArrayList<>();
		int checked = 0;
		for (LostCastlePlan.Level floor : piece.plan().levels()) {
			for (LostCastlePlan.Spawner spawner : floor.spawners()) {
				SpawnerBlockEntity block = (SpawnerBlockEntity) level.getBlockEntity(spawnerAt(piece, floor, spawner));
				CompoundTag tag = block.saveCustomOnly(level.registryAccess());
				Map<String, Integer> listed = new HashMap<>();
				ListTag potentials = tag.getListOrEmpty("SpawnPotentials");
				for (int i = 0; i < potentials.size(); i++) {
					CompoundTag entry = potentials.getCompoundOrEmpty(i);
					CompoundTag data = entry.getCompoundOrEmpty("data");
					CompoundTag entity = data.getCompoundOrEmpty("entity");
					if (entity.keySet().size() != 1 || !data.contains("custom_spawn_rules")) {
						faults.add("floor " + floor.index() + " entry " + i + " holds more than an id, or has no light rule");
					}
					String id = entity.getStringOr("id", "");
					if (id.equals("minecraft:creeper")) {
						faults.add("floor " + floor.index() + " lists a creeper");
					}
					listed.merge(id, entry.getIntOr("weight", 0), Integer::sum);
				}
				if (!listed.equals(LostCastleSpawner.table(floor.kind(), piece.palette()))) {
					faults.add("floor " + floor.index() + " " + floor.kind() + " lists " + listed);
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
						faults.add("floor " + floor.index() + " has a number " + pair[0] + ", not " + pair[1]);
					}
				}
				checked++;
			}
		}
		TowerTestSupport.result("castleSpawnerSettings", "checked=" + checked + " faults=" + faults);
		helper.assertTrue(checked == LostCastleGrid.MAX_FLOORS * LostCastlePlan.SPAWNERS, "only " + checked + " spawners were read");
		helper.assertTrue(faults.isEmpty(), "spawner settings are wrong: " + faults);
		helper.succeed();
	}

	private static List<Mob> tagged(final ServerLevel level, final LostRoomPiece piece, final int floor) {
		return level.getEntitiesOfClass(Mob.class, slab(piece, floor), mob -> mob.isAlive() && mob.entityTags().contains(LostCastles.SPAWN_TAG));
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

	/** Catches a spawner that raises nothing, a mob that is not a tower mob, one from another floor's table, or one that appears outside the castle. */
	@GameTest(maxTicks = 100)
	public void aFloorSpawnerRaisesTowerMobsInsideTheCastle(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.SANDSTONE, Direction.NORTH, LostCastleGrid.MIN_FLOORS);
		TowerLostCastleContentsGameTest.register(arena);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		// A player arrives in another dimension on the tick after the move, so each spawner gets its own player now.
		List<ServerPlayer> players = new ArrayList<>();
		for (LostCastlePlan.Level floor : piece.plan().levels()) {
			for (LostCastlePlan.Spawner spawner : floor.spawners()) {
				BlockPos at = spawnerAt(piece, floor, spawner);
				BlockPos stand = at.offset(spawner.side().inX() * 2, -2, spawner.side().inZ() * 2);
				ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
				player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				players.add(player);
			}
		}
		helper.runAfterDelay(SETTLE_TICKS, () -> {
			StringBuilder report = new StringBuilder();
			int spawners = 0;
			for (LostCastlePlan.Level floor : piece.plan().levels()) {
				for (LostCastlePlan.Spawner spawner : floor.spawners()) {
					spawners++;
					BlockPos at = spawnerAt(piece, floor, spawner);
					int ticks = fire(level, at);
					List<Mob> raised = raisedNear(level, at);
					helper.assertTrue(!raised.isEmpty(), "floor " + floor.index() + " spawner at " + at + " raised no mob in " + ticks + " ticks");
					Set<String> allowed = LostCastleSpawner.table(floor.kind(), piece.palette()).keySet();
					for (Mob mob : raised) {
						TowerMobs.State state = TowerMobs.stateOf(mob);
						String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
						helper.assertTrue(state != null && state.rank() == TowerRank.MOB, "a spawner mob is not a tower mob of rank MOB: " + id);
						helper.assertTrue(mob.entityTags().contains(StellaMobs.NO_SPAWN_ROLL_TAG) && mob.entityTags().contains(LostRooms.TAG), "a spawner mob lacks its tags");
						helper.assertTrue(allowed.contains(id), id + " is not in the " + floor.kind() + " table " + allowed);
						helper.assertTrue(AABB.of(piece.getBoundingBox()).contains(mob.position()), "a spawner mob stands outside the castle at " + mob.blockPosition());
						report.append(" [floor ").append(floor.index()).append(' ').append(id).append(" ticks=").append(ticks).append(']');
						mob.discard();
					}
				}
			}
			TowerTestSupport.result("castleSpawnerRaises", "spawners=" + spawners + report);
			helper.assertTrue(spawners == LostCastleGrid.MIN_FLOORS * LostCastlePlan.SPAWNERS, "the castle holds " + spawners + " spawners");
			players.forEach(player -> TowerTestSupport.leave(helper, player));
			helper.succeed();
		});
	}

	private static Mob spawnerMob(final ServerLevel level, final EntityType<? extends Mob> type, final EntitySpawnReason reason, final BlockPos feet) {
		Mob mob = type.create(level, reason);
		mob.snapTo(Vec3.atBottomCenterOf(feet));
		return mob;
	}

	/** A room's lane crossing on a floor, in the open, for a mob to stand in. */
	private static BlockPos middle(final LostRoomPiece piece, final int floor, final int room) {
		return piece.worldPos(LostCastleGrid.cellLow(room % 3) + LostCastleGrid.DOOR_FROM, LostCastleGrid.feet(piece.floors(), floor),
			LostCastleGrid.cellLow(room / 3) + LostCastleGrid.DOOR_FROM);
	}

	/** Catches a floor that holds more spawner mobs than the cap, a cap that is shared between floors, and a mob changed that no spawner raised. */
	@GameTest(maxTicks = 100)
	public void spawnerMobsStopAtTheFloorCap(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena inside = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, Direction.EAST, 3);
		TowerLostCastleSupport.Arena outside = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, Direction.EAST, 3);
		TowerLostCastleContentsGameTest.register(inside);
		ServerLevel level = inside.level();
		LostRoomPiece piece = inside.piece();
		List<Mob> all = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(piece, 0, i % 9));
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
		int top = tagged(level, piece, 0).size();
		int middleFloor = tagged(level, piece, 1).size();
		int bottom = tagged(level, piece, 2).size();
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
		TowerTestSupport.result("castleSpawnerCap", "top=" + top + " middle=" + middleFloor + " bottom=" + bottom + " eventTagged=" + event.entityTags().contains(LostCastles.SPAWN_TAG)
			+ " awayTagged=" + away.entityTags().contains(LostCastles.SPAWN_TAG) + " keptFloor=" + (kept == null ? -1 : kept.floor()));
		boolean eventUntouched = TowerMobs.stateOf(event) == null && !event.entityTags().contains(LostCastles.SPAWN_TAG);
		boolean awayUntouched = TowerMobs.stateOf(away) == null && !away.entityTags().contains(LostCastles.SPAWN_TAG);
		boolean knownUntouched = kept != null && kept.floor() == 7 && !known.entityTags().contains(LostCastles.SPAWN_TAG);
		all.add(event);
		all.add(away);
		all.add(known);
		all.forEach(Mob::discard);
		helper.assertTrue(top == LostCastles.FLOOR_CAP, "the top floor holds " + top + " spawner mobs, not the cap " + LostCastles.FLOOR_CAP);
		helper.assertTrue(bottom == LostCastles.FLOOR_CAP, "the bottom floor holds " + bottom + " spawner mobs, not the cap " + LostCastles.FLOOR_CAP);
		helper.assertTrue(LostCastles.FLOOR_CAP == 8, "the shipped cap is not 8");
		helper.assertTrue(middleFloor == 3, "the next floor's three mobs were cut to " + middleFloor + ": the cap is per floor");
		helper.assertTrue(eventUntouched, "a mob that no spawner raised was changed");
		helper.assertTrue(awayUntouched, "a spawner mob outside every castle was changed");
		helper.assertTrue(knownUntouched, "a mob that was a tower mob already was changed again");
		helper.succeed();
	}

	/** Catches a spawner mob that rolls an elite or a boss: the chunk elite tag forces a roll on any mob the mobs mod rolls. */
	@GameTest(maxTicks = 100)
	public void aSpawnerMobIsNeverAnEliteOrABoss(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena inside = TowerLostCastleSupport.castle(helper, LostCastlePalette.SNOW, Direction.SOUTH, 3);
		TowerLostCastleSupport.Arena outside = TowerLostCastleSupport.castle(helper, LostCastlePalette.SNOW, Direction.SOUTH, 3);
		TowerLostCastleContentsGameTest.register(inside);
		ServerLevel level = inside.level();
		List<Mob> raised = new ArrayList<>();
		List<Mob> control = new ArrayList<>();
		for (int floor = 0; floor < 3; floor++) {
			for (int i = 0; i < 4; i++) {
				Mob mob = spawnerMob(level, EntityTypes.ZOMBIE, EntitySpawnReason.SPAWNER, middle(inside.piece(), floor, i));
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
