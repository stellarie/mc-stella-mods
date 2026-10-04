package com.stella.tower;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.stella.mobs.scaling.ThreatState;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lost rooms: the hut, its portal both ways, the room's levels, its traps, zones and chests, and its boss. */
public class TowerLostRoomGameTest {
	/** A hut laid facing north at relative (1, 1, 1), its floor on the test's floor plate. */
	static LostRoomPiece hut(final GameTestHelper helper) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		LostRoomPiece piece = new LostRoomPiece(at.getX(), at.getY(), at.getZ(), Direction.NORTH);
		piece.build(helper.getLevel(), BoundingBox.infinite(), helper.getLevel().getRandom());
		return piece;
	}

	/** A hut key no earlier run has used. */
	static String freshHut() {
		return "test@" + TowerTestSupport.freshArea() + "/" + System.nanoTime();
	}

	static TowerTravel.Entry anywhere(final GameTestHelper helper) {
		return new TowerTravel.Entry(helper.getLevel().dimension(), helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F);
	}

	static ServerPlayer withLevel(final GameTestHelper helper, final int level) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.experienceLevel = level;
		return player;
	}

	@GameTest(maxTicks = 20)
	public void theHutHoldsEveryBlockOfTheRequest(final GameTestHelper helper) {
		LostRoomPiece piece = hut(helper);
		ServerLevel level = helper.getLevel();
		StringBuilder wrong = new StringBuilder();
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int y = 0; y < LostRoomPiece.HEIGHT; y++) {
				for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
					boolean shell = x == 0 || x == LostRoomPiece.LENGTH - 1 || y == 0 || y == LostRoomPiece.HEIGHT - 1
						|| z == 0 || z == LostRoomPiece.WIDTH - 1;
					boolean door = z == 0 && (y == 1 || y == 2) && (x == LostRoomPiece.DOOR_X || x == LostRoomPiece.DOOR_X + 1);
					boolean frameBottom = x == LostRoomPiece.PORTAL_X && y == 0 && z == LostRoomPiece.PORTAL_Z;
					if (shell && !door && !frameBottom && !level.getBlockState(piece.worldPos(x, y, z)).is(TowerBlocks.TOWER_WALL)) {
						wrong.append(" wall@").append(x).append(',').append(y).append(',').append(z);
					}
				}
			}
		}
		for (int x = LostRoomPiece.DOOR_X; x <= LostRoomPiece.DOOR_X + 1; x++) {
			BlockState lower = level.getBlockState(piece.worldPos(x, 1, 0));
			BlockState upper = level.getBlockState(piece.worldPos(x, 2, 0));
			if (!lower.is(Blocks.OAK_DOOR) || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || !upper.is(Blocks.OAK_DOOR)) {
				wrong.append(" door@").append(x);
			}
		}
		if (level.getBlockState(piece.worldPos(LostRoomPiece.DOOR_X, 1, 0)).getValue(DoorBlock.HINGE)
			== level.getBlockState(piece.worldPos(LostRoomPiece.DOOR_X + 1, 1, 0)).getValue(DoorBlock.HINGE)) {
			wrong.append(" notDouble");
		}
		BlockPos portal = piece.worldPos(LostRoomPiece.PORTAL_X, 1, LostRoomPiece.PORTAL_Z);
		boolean portalOk = level.getBlockState(portal).is(TowerBlocks.TOWER_PORTAL) && level.getBlockState(portal.above()).is(TowerBlocks.TOWER_PORTAL);
		boolean lined = level.getBlockState(portal.below()).is(TowerBlocks.TOWER_FRAME) && level.getBlockState(portal.above(2)).is(TowerBlocks.TOWER_FRAME);
		for (int y = 1; y <= 3; y++) {
			lined &= level.getBlockState(piece.worldPos(LostRoomPiece.PORTAL_X, y, 1)).is(TowerBlocks.TOWER_FRAME);
			lined &= level.getBlockState(piece.worldPos(LostRoomPiece.PORTAL_X, y, 3)).is(TowerBlocks.TOWER_FRAME);
		}
		Set<Block> inside = new java.util.HashSet<>();
		int cobwebs = 0;
		int loot = 0;
		for (int x = 1; x < LostRoomPiece.LENGTH - 1; x++) {
			for (int y = 1; y < LostRoomPiece.HEIGHT - 1; y++) {
				for (int z = 1; z < LostRoomPiece.WIDTH - 1; z++) {
					BlockPos at = piece.worldPos(x, y, z);
					BlockState state = level.getBlockState(at);
					inside.add(state.getBlock());
					cobwebs += state.is(Blocks.COBWEB) ? 1 : 0;
					if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
						loot += chest.isEmpty() ? 0 : 1;
					}
				}
			}
		}
		boolean furnished = inside.contains(Blocks.CRAFTING_TABLE) && inside.contains(Blocks.CHEST) && inside.contains(Blocks.FURNACE)
			&& inside.contains(Blocks.CANDLE) && inside.stream().anyMatch(block -> block.defaultBlockState().is(BlockTags.ANVIL));
		boolean known = LostRooms.isHutPortal(level, portal);
		TowerTestSupport.result("lostHut", "wrong=[" + wrong + "] portal=" + portalOk + " lined=" + lined + " furnished=" + furnished
			+ " cobwebs=" + cobwebs + " stockedChests=" + loot + " hutPortal=" + known);
		helper.assertTrue(wrong.isEmpty() && portalOk && lined && furnished && cobwebs > 0 && loot > 0 && known,
			"the hut is not the box of the request: " + wrong + " portal=" + portalOk + " lined=" + lined + " furnished=" + furnished
				+ " cobwebs=" + cobwebs + " loot=" + loot + " hutPortal=" + known);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void theHutsKeepFourChunksFromVillages(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET, StellaTower.id("lost_rooms"));
		StructureSet set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(key);
		helper.assertTrue(set != null, "no lost_rooms structure set");
		JsonElement json = StructureSet.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), set).getOrThrow();
		JsonObject placement = json.getAsJsonObject().getAsJsonObject("placement");
		JsonObject zone = placement.getAsJsonObject("exclusion_zone");
		boolean houses = set.structures().stream().anyMatch(entry -> entry.structure().is(LostRooms.STRUCTURE));
		boolean typed = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(LostRooms.STRUCTURE) instanceof LostRoomStructure;
		TowerTestSupport.result("lostVillages", "placement=" + placement + " houses=" + houses + " typed=" + typed);
		helper.assertTrue(zone != null && "minecraft:villages".equals(zone.get("other_set").getAsString())
			&& zone.get("chunk_count").getAsInt() * 16 == 64 && houses && typed, "the huts are not kept 64 blocks from villages: " + placement);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void thePortalLeadsToTheRoomAndBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerLevel level = helper.getLevel();
		LostRoomPiece piece = hut(helper);
		BlockPos portal = piece.worldPos(LostRoomPiece.PORTAL_X, 1, LostRoomPiece.PORTAL_Z);
		LostRooms.forget(level.getServer(), LostRooms.hutKey(level, portal));
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 before = Vec3.atBottomCenterOf(piece.worldPos(LostRoomPiece.PORTAL_X - 1, 1, LostRoomPiece.PORTAL_Z));
		player.teleportTo(level, before.x, before.y, before.z, Set.of(), 0.0F, 0.0F, true);
		TeleportTransition in = TowerTravel.destination(level, player, portal);
		int room = in == null ? 0 : TowerLayout.roomAt(BlockPos.containing(in.position()));
		if (in != null) {
			player.teleport(in);
		}
		LostRoomData.Instance instance = LostRoomData.get(level.getServer()).hut(LostRooms.hutKey(level, portal));
		int shown = LostRooms.shownTo(player);
		TeleportTransition out = room < 0 ? TowerTravel.destination(tower, player, LostRooms.returnPortal(tower, room)) : null;
		boolean lit = room < 0 && tower.getBlockState(LostRooms.returnPortal(tower, room)).is(TowerBlocks.TOWER_PORTAL);
		double fromHut = out == null ? -1 : out.position().distanceTo(Vec3.atBottomCenterOf(portal));
		TowerTestSupport.result("lostPortal", "in=" + (in == null ? "none" : in.newLevel().dimension().identifier() + " " + in.position())
			+ " room=" + room + " shown=" + shown + " level=" + (instance == null ? "none" : instance.level()) + " returnLit=" + lit
			+ " out=" + (out == null ? "none" : out.newLevel().dimension().identifier()) + " fromHut=" + fromHut);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(in != null && in.newLevel() == tower && room < 0, "the hut's portal did not lead into a lost room");
		helper.assertTrue(instance != null && shown == instance.level(), "the arrival did not show the room's level");
		helper.assertTrue(lit && out != null && out.newLevel() == level && fromHut >= 0 && fromHut <= 2.5, "the room's portal did not lead back to the hut");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void mobLevelsFollowThePlayersAverage(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer low = withLevel(helper, 30);
		ServerPlayer high = withLevel(helper, 50);
		int average = LostRooms.averageLevel(List.of(low, high));
		LostRoomData.Instance instance = LostRooms.open(tower, freshHut(), anywhere(helper), List.of(low, high));
		List<Mob> mobs = LostRooms.mobsIn(tower, instance.room());
		int below = 0;
		int above = 0;
		int bosses = 0;
		int bossLevel = -1;
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (Mob mob : mobs) {
			int level = mob.getAttached(ThreatState.TYPE).areaLevel();
			lowest = Math.min(lowest, level);
			highest = Math.max(highest, level);
			below += level < average - LostRooms.LEVEL_BELOW ? 1 : 0;
			above += level > average + LostRooms.LEVEL_ABOVE ? 1 : 0;
			if (TowerMobs.stateOf(mob).rank() == TowerRank.BOSS) {
				bosses++;
				bossLevel = level;
			}
		}
		TowerTestSupport.result("lostLevels", "average=" + average + " room=" + instance.level() + " mobs=" + mobs.size() + " lowest=" + lowest
			+ " highest=" + highest + " bosses=" + bosses + " bossLevel=" + bossLevel);
		TowerTestSupport.leave(helper, low);
		TowerTestSupport.leave(helper, high);
		helper.assertTrue(average == 40 && instance.level() == average, "the room's level is not the players' average");
		helper.assertTrue(mobs.size() > 1 && below == 0 && above == 0, "a mob's level left [average - 5, average + 15]: " + lowest + ".." + highest);
		helper.assertTrue(bosses == 1 && bossLevel == average + LostRooms.BOSS_ABOVE, "the boss is not at average + 15: " + bossLevel);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theRoomHoldsTrapsASafeZoneAndChests(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		LostRoomData.Instance instance = LostRooms.open(tower, freshHut(), anywhere(helper), List.of());
		int room = instance.room();
		TowerPlan plan = LostRooms.plan(tower.getSeed(), room);
		BlockPos origin = TowerLayout.origin(room);
		Set<TrapTheme> themes = EnumSet.noneOf(TrapTheme.class);
		for (int x = 0; x < TowerLayout.SIZE; x++) {
			for (int z = 0; z < TowerLayout.SIZE; z++) {
				if (tower.getBlockState(origin.offset(x, -1, z)).getBlock() instanceof TowerTrapBlock trap) {
					themes.add(trap.theme());
				}
			}
		}
		TowerMaze.Zone zone = plan.safeZones().get(0);
		BlockPos inZone = origin.offset(zone.x() + TowerMaze.SAFE / 2, 0, zone.z() + TowerMaze.SAFE / 2);
		boolean safeFloor = tower.getBlockState(inZone.below()).is(TowerFloors.SAFE_FLOOR);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.teleportTo(tower, inZone.getX() + 0.5, inZone.getY(), inZone.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		boolean sheltered = TowerSafety.sheltered(player);
		int chests = 0;
		int stocked = 0;
		for (TowerPlan.ChestSpot spot : plan.chestSpots()) {
			BlockPos at = origin.offset(spot.x(), 0, spot.z());
			BlockState state = tower.getBlockState(at);
			if (state.is(TowerBlocks.TOWER_CHEST)) {
				chests++;
				Container container = ChestBlock.getContainer((ChestBlock) state.getBlock(), state, tower, at, true);
				stocked += container != null && !container.isEmpty() ? 1 : 0;
			}
		}
		TowerTestSupport.result("lostRoomFloor", "room=" + room + " looks=" + LostRooms.looksFloor(room) + " themes=" + themes
			+ " safeFloor=" + safeFloor + " sheltered=" + sheltered + " chests=" + chests + "/" + plan.chestSpots().size() + " stocked=" + stocked);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(TrapTheme.of(LostRooms.looksFloor(room)) == TrapTheme.CHAOS && themes.size() >= 2,
			"the room's traps are not drawn from the whole pool: " + themes);
		helper.assertTrue(safeFloor && sheltered, "the room's safe zone does not shelter a player");
		helper.assertTrue(chests > 0 && chests == plan.chestSpots().size() && stocked > 0, "the room's chests are missing or empty");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theBossPaysTheTowerRewardButNoKey(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerPlayer player = withLevel(helper, 901);
		LostRoomData.Instance instance = LostRooms.open(tower, freshHut(), anywhere(helper), List.of(player));
		Mob boss = LostRooms.mobsIn(tower, instance.room()).stream().filter(mob -> TowerMobs.stateOf(mob).rank() == TowerRank.BOSS).findFirst().orElse(null);
		helper.assertTrue(boss != null, "the room has no boss");
		int floor = TowerMobs.stateOf(boss).floor();
		TowerRunes.pinDropChance(floor, 1.0);
		player.teleportTo(tower, boss.getX(), boss.getY(), boss.getZ(), Set.of(), 0.0F, 0.0F, true);
		boss.setLastHurtByPlayer(player, 100);
		boss.kill(tower);
		AABB around = boss.getBoundingBox().inflate(4.0);
		List<ItemEntity> drops = tower.getEntitiesOfClass(ItemEntity.class, around);
		boolean rune = drops.stream().anyMatch(item -> item.getItem().is(TowerItems.RUNE));
		boolean key = drops.stream().anyMatch(item -> item.getItem().is(TowerItems.FLOOR_KEY));
		boolean cleared = LostRoomData.get(tower.getServer()).room(instance.room()).cleared();
		TowerRunes.pinDropChance(floor, null);
		drops.forEach(ItemEntity::discard);
		TowerTestSupport.result("lostBoss", "floor=" + floor + " drops=" + drops.size() + " rune=" + rune + " key=" + key + " cleared=" + cleared);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(rune, "the lost room's boss dropped no rune through the rune roll");
		helper.assertTrue(!key, "the lost room's boss dropped a floor key");
		helper.assertTrue(cleared, "the boss's fall did not clear the room");
		helper.succeed();
	}
}
