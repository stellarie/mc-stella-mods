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

	/** A castle's footprint, in an empty spot of the tower dimension no earlier run has used. */
	record Arena(ServerLevel level, int minX, int minZ, int maxX, int maxZ, LostRoomPiece piece) {
	}

	/** The ground's top under a column: the hill each arena is given, 0 to 4 blocks over y 60. */
	static int hill(final int x, final int z) {
		return 60 + Math.floorMod(x * 7 + z * 3, 5);
	}

	/** A castle built over a hill of stone, its floor one block above that hill. */
	static Arena castle(final GameTestHelper helper) {
		return castle(helper, LostCastleVariant.CASTLE, Direction.NORTH);
	}

	/** A castle of one variant, facing one way, built over a hill of stone, its floor one block above that hill. */
	static Arena castle(final GameTestHelper helper, final LostCastleVariant variant, final Direction facing) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int area = TowerTestSupport.freshArea();
		int minX = Math.floorMod(area, 4096) * 64;
		int minZ = -200_000 - Math.floorMod(area / 4096, 1000) * 64;
		int maxX = minX + LostRoomPiece.LENGTH - 1;
		int maxZ = minZ + LostRoomPiece.WIDTH - 1;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = 56; y <= hill(x, z); y++) {
					tower.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		int floor = LostRoomPiece.floorOver(minX, minZ, maxX, maxZ, 1, (x, z) -> hill(x, z));
		LostRoomPiece piece = new LostRoomPiece(minX, floor, minZ, facing, variant);
		piece.build(tower, BoundingBox.infinite());
		return new Arena(tower, minX, minZ, maxX, maxZ, piece);
	}

	/**
	 * The castle's foundation: one block above the highest ground, and held up under the hexagon it stands on.
	 * The corners of the frame lie outside the hexagon, so the corners read are the hexagon's own four.
	 */
	@GameTest(maxTicks = 40)
	public void theCastleStandsAboveTheGroundOnAFoundation(final GameTestHelper helper) {
		Arena arena = castle(helper);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int floor = piece.getBoundingBox().minY();
		int highest = Integer.MIN_VALUE;
		int gaps = 0;
		for (int x = arena.minX(); x <= arena.maxX(); x++) {
			for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
				highest = Math.max(highest, hill(x, z));
			}
		}
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				if (TowerHex.kind(x, z) == TowerHex.Part.OUT) {
					continue;
				}
				BlockPos at = piece.worldPos(x, 0, z);
				for (int y = hill(at.getX(), at.getZ()) + 1; y < floor; y++) {
					gaps += level.getBlockState(new BlockPos(at.getX(), y, at.getZ())).is(piece.palette().wall()) ? 0 : 1;
				}
			}
		}
		int beyond = 0;
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				if (TowerHex.kind(x, z) != TowerHex.Part.OUT) {
					continue;
				}
				BlockPos at = piece.worldPos(x, floor - 1, z);
				beyond += level.getBlockState(at).is(piece.palette().wall()) ? 1 : 0;
			}
		}
		TowerTestSupport.result("castleSurface", "floor=" + floor + " highestGround=" + highest + " foundationGaps=" + gaps + " outsideFrame=" + beyond);
		helper.assertTrue(floor == highest + 1, "the castle's floor is not one block above the highest ground: " + floor + " over " + highest);
		helper.assertTrue(gaps == 0, "the foundation leaves " + gaps + " gaps down to the ground");
		helper.succeed();
	}

	/**
	 * The castle's frame: three hexagonal storeys of six-wide ways, a breach through the ground storey's
	 * south wall for its way in, the lost room's portal on the top storey with the workstations beside it,
	 * and the ruin over all of it. The old two halls and their oak gate are gone: the castle is a maze now.
	 */
	@GameTest(maxTicks = 40)
	public void theCastleHasItsStoreysItsBreachAndItsPortal(final GameTestHelper helper) {
		Arena arena = castle(helper);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		StringBuilder wrong = new StringBuilder();
		int clearWays = 0;
		int lids = 0;
		for (int storey = 0; storey < TowerHex.STOREYS; storey++) {
			int feet = TowerHex.floorAt(storey) + 1;
			int ceiling = TowerHex.ceilingAt(storey);
			for (TowerHex.Cell cell : TowerHex.cells()) {
				boolean clear = true;
				boolean lid = true;
				// A walker needs the two blocks of its own height at the middle, not the whole column to the ceiling.
				for (int y = feet; y <= feet + 1; y++) {
					clear &= level.getBlockState(piece.worldPos(cell.middle().getX(), y, cell.middle().getZ())).isAir();
				}
				for (int dx = 0; dx < TowerMaze.PATH; dx++) {
					for (int dz = 0; dz < TowerMaze.PATH; dz++) {
						int x = cell.middle().getX() - TowerMaze.PATH / 2 + dx;
						int z = cell.middle().getZ() - TowerMaze.PATH / 2 + dz;
						// Only the cell's own blocks carry a lid: the hexagon's cut corner has no room for one.
						if (TowerHex.kind(x, z) != TowerHex.Part.WAY) {
							continue;
						}
						// The stair of this storey rises through its own ceiling, so that mouth is open.
						if (storey + 1 < TowerHex.STOREYS && stairMouth(x, z)) {
							continue;
						}
						BlockState above = level.getBlockState(piece.worldPos(x, ceiling, z));
						// The top storey's roof has to hold. A lower storey's ceiling is the floor above it, which
						// the ruin gaps, cracks and greens on purpose, so only the roof is read as a lid.
						boolean lidded = storey + 1 < TowerHex.STOREYS || !above.isAir();
						lid &= lidded;
						if (!lidded) {
							wrong.append(" lid(").append(x).append(',').append(z).append(")=").append(above.getBlock().getName().getString())
								.append(" kind=").append(TowerHex.kind(x, z))
								.append(" palette=").append(piece.palette().wall().getName().getString())
								.append(" foot=").append(level.getBlockState(piece.worldPos(x, feet, z)).getBlock().getName().getString());
						}
					}
				}
				clearWays += clear ? 1 : 0;
				lids += lid ? 1 : 0;
				if (!clear || !lid) {
					wrong.append(" storey=").append(storey).append(" cell=").append(cell.cx()).append(',').append(cell.cz())
						.append(" clear=").append(clear).append(" lid=").append(lid);
				}
			}
		}
		BlockPos portal = piece.portalPos();
		boolean portalOk = level.getBlockState(portal).is(TowerBlocks.TOWER_PORTAL) && level.getBlockState(portal.above()).is(TowerBlocks.TOWER_PORTAL)
			&& level.getBlockState(portal.below()).is(TowerBlocks.TOWER_FRAME) && level.getBlockState(portal.above(2)).is(TowerBlocks.TOWER_FRAME);
		boolean onTop = portal.getY() - piece.getBoundingBox().minY() - 1 == TowerHex.floorAt(TowerHex.STOREYS - 1);
		int open = 0;
		int row = LostCastleHex.breachRow();
		for (int x = LostCastleHex.breachLow(); x < LostCastleHex.breachLow() + LostCastleHex.BREACH; x++) {
			for (int y = 2; y <= LostCastleHex.BREACH_HEIGHT; y++) {
				open += level.getBlockState(piece.worldPos(x, y, row)).isAir() ? 1 : 0;
			}
		}
		// The wall the breach was cut through still stands over it, on the columns the hexagon holds.
		boolean walled = true;
		for (int x = LostCastleHex.breachLow(); x < LostCastleHex.breachLow() + LostCastleHex.BREACH; x++) {
			if (TowerHex.kind(x, row) == TowerHex.Part.OUT) {
				continue;
			}
			walled &= !level.getBlockState(piece.worldPos(x, LostCastleHex.BREACH_HEIGHT + 1, row)).isAir();
		}
		boolean lit = level.getBlockState(piece.worldPos(LostCastleHex.entranceLow(), LostCastleHex.BREACH_HEIGHT + 1, row)).is(TowerBlocks.TOWER_LAMP)
			&& level.getBlockState(piece.worldPos(LostCastleHex.entranceHigh(), LostCastleHex.BREACH_HEIGHT + 1, row)).is(TowerBlocks.TOWER_LAMP);
		dump(helper, arena);
		int chests = 0;
		int cobwebs = 0;
		int rubble = 0;
		int bones = 0;
		int candles = 0;
		int chains = 0;
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				for (int y = 1; y < LostRoomPiece.HEIGHT; y++) {
					BlockPos at = piece.worldPos(x, y, z);
					BlockState state = level.getBlockState(at);
					cobwebs += state.is(Blocks.COBWEB) ? 1 : 0;
					chains += state.is(Blocks.IRON_CHAIN) ? 1 : 0;
					bones += state.is(Blocks.SKELETON_SKULL) ? 1 : 0;
					candles += state.is(Blocks.CANDLE) ? 1 : 0;
					rubble += state.is(Blocks.COBBLESTONE) || state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)
						|| state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.MOSS_CARPET) ? 1 : 0;
					if (level.getBlockEntity(at) instanceof ChestBlockEntity chest && !chest.isEmpty()) {
						chests++;
					}
				}
			}
		}
		TowerTestSupport.result("castleHex", "storeys=" + TowerHex.STOREYS + " ways=" + TowerHex.cells().size() + " clear=" + clearWays
			+ " ceiling=" + lids + " wrong=[" + wrong + "] portal=" + portalOk + " topStorey=" + onTop + " breach=" + open + " walled=" + walled
			+ " lit=" + lit + " stockedChests=" + chests + " cobwebs=" + cobwebs + " rubble=" + rubble + " bones=" + bones + " candles=" + candles
			+ " chains=" + chains);
		helper.assertTrue(wrong.isEmpty(), "a storey's way is not a six-wide hall under its ceiling:" + wrong);
		helper.assertTrue(portalOk && onTop, "the lost room's portal is not lit on the top storey at " + portal);
		helper.assertTrue(open == LostCastleHex.BREACH * (LostCastleHex.BREACH_HEIGHT - 1) && walled && lit,
			"the breach in the south wall is not open, walled at its sides and lit: open=" + open + " walled=" + walled + " lit=" + lit);
		helper.assertTrue(chests >= TowerHex.STOREYS * LostCastleHex.CHESTS_PER_STOREY,
			"the castle has " + chests + " stocked chests, not " + TowerHex.STOREYS * LostCastleHex.CHESTS_PER_STOREY);
		helper.assertTrue(cobwebs > 0 && rubble > 0 && bones > 0 && candles > 0 && chains > 0, "the castle is not ruined");
		helper.succeed();
	}

	/** Whether a column is one a storey's stair climbs, whose ceiling is open over every step. */
	private static boolean stairMouth(final int x, final int z) {
		for (TowerHex.Cell cell : TowerHex.cells()) {
			if (cell.cz() != TowerHex.COLS - 1) {
				continue;
			}
			for (int rise = 0; rise < TowerHex.STOREY; rise++) {
				int[] at = LostCastleHex.stairStep(cell, rise);
				if (at[0] == x && at[1] == z) {
					return true;
				}
			}
		}
		return false;
	}

	/** Writes the castle's blocks, foundation included, for the preview render. */
	static void dump(final GameTestHelper helper, final Arena arena) {
		dump(helper, arena, "lost_castle_layout.txt");
	}

	static void dump(final GameTestHelper helper, final Arena arena, final String name) {
		StringBuilder out = new StringBuilder();
		int floor = arena.piece().getBoundingBox().minY();
		for (int x = 0; x < LostRoomPiece.LENGTH; x++) {
			for (int z = 0; z < LostRoomPiece.WIDTH; z++) {
				for (int y = Math.min(56 - floor, arena.piece().lowest() - 1); y < LostRoomPiece.HEIGHT; y++) {
					BlockState state = arena.level().getBlockState(arena.piece().worldPos(x, y, z));
					if (!state.isAir()) {
						out.append(x).append(' ').append(y).append(' ').append(z).append(' ')
							.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())).append('\n');
					}
				}
			}
		}
		try {
			java.nio.file.Files.writeString(helper.getLevel().getServer().getServerDirectory().resolve(name), out.toString());
		} catch (java.io.IOException e) {
			TowerTestSupport.LOG.warn("no castle layout written", e);
		}
	}

	@GameTest(maxTicks = 40)
	public void theGroundsWakeOnceWhenAPlayerComesNear(final GameTestHelper helper) {
		Arena arena = castle(helper);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		AABB box = AABB.of(piece.getBoundingBox());
		LostCastles.found(level.dimension(), piece);
		ServerPlayer player = withLevel(helper, 40);
		// Out from the gate, in the castle's frame.
		Vec3 away = Vec3.atBottomCenterOf(piece.worldPos(LostCastleHex.breachMiddle(), 1, -LostCastles.WAKE_DISTANCE - 8));
		Vec3 near = Vec3.atBottomCenterOf(piece.worldPos(LostCastleHex.breachMiddle(), 1, -LostCastles.WAKE_DISTANCE + 8));
		player.teleportTo(level, away.x, away.y, away.z, Set.of(), 0.0F, 0.0F, true);
		int far = (int) LostCastles.wake(level.getServer()).stream().filter(mob -> box.contains(mob.position())).count();
		player.teleportTo(level, near.x, near.y, near.z, Set.of(), 0.0F, 0.0F, true);
		int average = LostRooms.averageLevel(level.getServer().getPlayerList().getPlayers());
		List<Mob> woke = LostCastles.wake(level.getServer()).stream().filter(mob -> box.contains(mob.position())).toList();
		int outOfRange = 0;
		StringBuilder levels = new StringBuilder();
		for (Mob mob : woke) {
			int mobLevel = mob.getAttached(ThreatState.TYPE).areaLevel();
			levels.append(' ').append(mobLevel);
			boolean out = mobLevel < Math.max(0, average - LostRooms.LEVEL_BELOW) || mobLevel > average + LostRooms.LEVEL_ABOVE || !LostRooms.isLostMob(mob);
			outOfRange += out ? 1 : 0;
		}
		woke.forEach(Mob::discard);
		int again = (int) LostCastles.wake(level.getServer()).stream().filter(mob -> box.contains(mob.position())).count();
		TowerTestSupport.result("castleGrounds", "far=" + far + " woke=" + woke.size() + " average=" + average + " levels=[" + levels + "] again=" + again);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(far == 0, "the grounds woke for a player " + (LostCastles.WAKE_DISTANCE + 8) + " blocks away");
		helper.assertTrue(woke.size() >= LostCastles.MIN_MOBS && woke.size() <= LostCastles.MAX_MOBS, "the grounds raised " + woke.size() + " mobs");
		helper.assertTrue(outOfRange == 0, "a grounds mob's level left [average - 5, average + 15]:" + levels);
		helper.assertTrue(again == 0, "the grounds raised mobs a second time");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void everyTowerBossPaysEmeraldsDiamondsAndOneRune(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		int floor = TowerTestSupport.freshFloorWhere(TowerBuffs::isBossFloor);
		TowerFloors.ensure(tower, floor);
		TowerBuffs.pinMobBuffs(floor, List.of(TowerBuffs.MobBuff.SPEED, TowerBuffs.MobBuff.RESISTANCE));
		Mob floorBoss = TowerElites.rise(tower, floor, TowerRank.BOSS);
		ServerPlayer player = withLevel(helper, 61);
		LostRoomData.Instance instance = LostRooms.open(tower, freshHut(), anywhere(helper), List.of(player));
		Mob lostBoss = LostRooms.mobsIn(tower, instance.room()).stream().filter(mob -> TowerMobs.stateOf(mob).rank() == TowerRank.BOSS).findFirst().orElse(null);
		helper.assertTrue(floorBoss != null && lostBoss != null, "a boss could not rise, so the case proves nothing");
		String floorDrops = bossDrops(tower, player, floorBoss, floor);
		String lostDrops = bossDrops(tower, player, lostBoss, LostRooms.equivalentFloor(instance.level()));
		TowerBuffs.pinMobBuffs(floor, null);
		TowerTestSupport.result("bossDrops", "floor=" + floorDrops + " lost=" + lostDrops);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(floorDrops.startsWith("ok"), "an endless tower boss paid wrong: " + floorDrops);
		helper.assertTrue(lostDrops.startsWith("ok"), "a lost room boss paid wrong: " + lostDrops);
		helper.succeed();
	}

	/**
	 * What a boss's own loot may add as emeralds: the same kind, as a plain tower mob of the same floor and
	 * level, killed the same way, and its own loot read. The pack grows a mob's own loot by the floor's drop
	 * factor, so a hand-written count is wrong at any floor but the first; the same kind at the same level is
	 * the pack's own number for it.
	 */
	static int ownLootEmeralds(final ServerLevel tower, final ServerPlayer player, final Mob boss, final int floor) {
		net.minecraft.world.entity.Entity twin = boss.getType().create(tower, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (!(twin instanceof Mob own)) {
			return 0;
		}
		own.snapTo(boss.getX(), boss.getY(), boss.getZ());
		TowerMobs.makeTowerMob(own, floor, TowerElement.DARK, TowerRank.MOB, TowerMobs.levelOf(floor));
		if (!tower.addFreshEntity(own)) {
			return 0;
		}
		own.setLastHurtByPlayer(player, 100);
		own.kill(tower);
		List<ItemEntity> left = tower.getEntitiesOfClass(ItemEntity.class, own.getBoundingBox().inflate(4.0));
		int emeralds = left.stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.EMERALD)).mapToInt(item -> item.getItem().getCount()).sum();
		left.forEach(ItemEntity::discard);
		return emeralds;
	}

	/** Kills a boss for the player with the rune roll pinned to nothing, and reads what it left. */
	static String bossDrops(final ServerLevel tower, final ServerPlayer player, final Mob boss, final int runeFloor) {
		int floor = TowerMobs.stateOf(boss).floor();
		int mobLevel = boss.getAttached(ThreatState.TYPE).areaLevel();
		int own = ownLootEmeralds(tower, player, boss, floor);
		TowerRunes.pinDropChance(floor, 0.0);
		player.teleportTo(tower, boss.getX(), boss.getY(), boss.getZ(), Set.of(), 0.0F, 0.0F, true);
		boss.setLastHurtByPlayer(player, 100);
		boss.kill(tower);
		List<ItemEntity> drops = tower.getEntitiesOfClass(ItemEntity.class, boss.getBoundingBox().inflate(4.0));
		int emeralds = drops.stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.EMERALD)).mapToInt(item -> item.getItem().getCount()).sum();
		int diamonds = drops.stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(item -> item.getItem().getCount()).sum();
		List<ItemEntity> runes = drops.stream().filter(item -> item.getItem().is(TowerItems.RUNE)).toList();
		int runeAt = runes.size() == 1 ? runes.get(0).getItem().get(TowerItems.RUNE_DATA).floor() : -1;
		TowerRunes.pinDropChance(floor, null);
		drops.forEach(ItemEntity::discard);
		int wantEmeralds = TowerRewards.BOSS_EMERALDS + TowerRewards.BOSS_EMERALDS_PER_LEVEL * mobLevel;
		int low = TowerRewards.BOSS_DIAMONDS_MIN + mobLevel / TowerRewards.LEVELS_PER_BOSS_DIAMOND;
		int high = TowerRewards.BOSS_DIAMONDS_MAX + mobLevel / TowerRewards.LEVELS_PER_BOSS_DIAMOND;
		// The tower's pay is exact; the boss's own loot is its kind's own loot, with a roll's worth of room.
		int bound = own * 2 + 8;
		boolean ok = emeralds >= wantEmeralds && emeralds <= wantEmeralds + bound && diamonds >= low && diamonds <= high && runes.size() == 1 && runeAt == runeFloor;
		return (ok ? "ok" : "WRONG") + " level=" + mobLevel + " emeralds=" + emeralds + "/" + wantEmeralds + "+" + bound + "(own=" + own + ") diamonds=" + diamonds
			+ " in " + low + ".." + high + " runes=" + runes.size() + " runeFloor=" + runeAt + "/" + runeFloor;
	}

	/** The castle's portal as it stands in the portal hall: one wide, two high, axis x, tower frame round it. */
	static BlockPos castlePortal(final GameTestHelper helper) {
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(1, y, 2, TowerBlocks.TOWER_FRAME);
			helper.setBlock(3, y, 2, TowerBlocks.TOWER_FRAME);
		}
		helper.setBlock(2, 1, 2, TowerBlocks.TOWER_FRAME);
		helper.setBlock(2, 4, 2, TowerBlocks.TOWER_FRAME);
		BlockState portal = TowerBlocks.TOWER_PORTAL.defaultBlockState().setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS, Direction.Axis.X);
		helper.setBlock(2, 2, 2, portal);
		helper.setBlock(2, 3, 2, portal);
		return helper.absolutePos(new BlockPos(2, 2, 2));
	}

	@GameTest(maxTicks = 20)
	public void theCastlesKeepClearOfVillages(final GameTestHelper helper) {
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
			&& zone.get("chunk_count").getAsInt() * 16 >= 64 + Math.max(LostRoomPiece.LENGTH, LostRoomPiece.WIDTH) && houses && typed,
			"the castles are not kept 64 blocks and a footprint from villages: " + placement);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void thePortalLeadsToTheRoomAndBack(final GameTestHelper helper) {
		ServerLevel tower = TowerTestSupport.tower(helper);
		ServerLevel level = helper.getLevel();
		BlockPos portal = castlePortal(helper);
		LostRooms.forget(level.getServer(), LostRooms.hutKey(level, portal));
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		Vec3 before = Vec3.atBottomCenterOf(portal.north());
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
