package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What a built lost castle holds: the stone of its biome, one or two double chests and two spawners hidden in
 * the walls of every floor, a small portal that no mob spawns in, and a vault behind a cracked wall.
 */
public class TowerLostCastleContentsGameTest {
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	/** Records an arena's castle as worldgen would, with its yard already stocked, so the hooks know it. */
	static void register(final TowerLostCastleSupport.Arena arena) {
		BoundingBox box = arena.box();
		LostRoomData.get(arena.level().getServer()).found(new LostRoomData.Castle(arena.level().dimension(), new BlockPos(box.minX(), box.minY(), box.minZ()),
			arena.piece().facing(), true, arena.piece().palette().ordinal(), arena.piece().floors(), arena.piece().drop()));
	}

	/** Catches a biome that gets the wrong stone: snow gets snow, sand and desert get sandstone, anything else green. */
	@GameTest(maxTicks = 100)
	public void biomesPickTheSnowSandstoneAndGreenPalettes(final GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		Map<ResourceKey<Biome>, LostCastlePalette> wanted = Map.of(
			Biomes.SNOWY_PLAINS, LostCastlePalette.SNOW,
			Biomes.ICE_SPIKES, LostCastlePalette.SNOW,
			Biomes.DESERT, LostCastlePalette.SANDSTONE,
			Biomes.BEACH, LostCastlePalette.SANDSTONE,
			Biomes.PLAINS, LostCastlePalette.GREEN,
			Biomes.FOREST, LostCastlePalette.GREEN,
			Biomes.SWAMP, LostCastlePalette.GREEN);
		List<String> faults = new ArrayList<>();
		for (Map.Entry<ResourceKey<Biome>, LostCastlePalette> entry : wanted.entrySet()) {
			LostCastlePalette got = LostCastlePalette.of(biomes.getOrThrow(entry.getKey()));
			if (got != entry.getValue()) {
				faults.add(entry.getKey().identifier() + " gives " + got + ", not " + entry.getValue());
			}
		}
		// And each palette builds its own stone, in the walls, the ring and the yard.
		StringBuilder report = new StringBuilder();
		for (LostCastlePalette palette : LostCastlePalette.values()) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, palette, FACINGS[palette.ordinal()], 3);
			BoundingBox box = arena.box();
			int[] walls = new int[LostCastlePalette.values().length];
			for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState state = arena.level().getBlockState(at);
				for (LostCastlePalette other : LostCastlePalette.values()) {
					walls[other.ordinal()] += state.is(other.wall()) ? 1 : 0;
				}
			}
			int yardGround = 0;
			int surface = arena.piece().surfaceFeet();
			for (int x = LostCastleGrid.MID - 4; x <= LostCastleGrid.MID + 5; x++) {
				for (int z = LostCastleGrid.MID - 4; z <= LostCastleGrid.MID + 5; z++) {
					yardGround += arena.at(x, surface - 1, z).is(palette.ground()) ? 1 : 0;
				}
			}
			int flowers = 0;
			int leaves = 0;
			int snow = 0;
			for (int x = LostCastleGrid.MID - 12; x <= LostCastleGrid.MID + 13; x++) {
				for (int z = LostCastleGrid.MID - 12; z <= LostCastleGrid.MID + 13; z++) {
					for (int y = surface; y < surface + LostCastleGrid.RING_HEIGHT; y++) {
						BlockState state = arena.at(x, y, z);
						flowers += state.is(BlockTags.SMALL_FLOWERS) ? 1 : 0;
						leaves += state.is(Blocks.OAK_LEAVES) ? 1 : 0;
						snow += state.is(Blocks.SNOW) ? 1 : 0;
					}
				}
			}
			if (palette == LostCastlePalette.GREEN && (flowers < 1 || leaves < 1)) {
				faults.add("the green ring has " + flowers + " flowers and " + leaves + " leaves");
			}
			if (palette == LostCastlePalette.SNOW && snow < 5) {
				faults.add("the snowy ring has " + snow + " snow layers");
			}
			report.append(" [").append(palette).append(" wall=").append(walls[palette.ordinal()]).append(" yard=").append(yardGround).append(" flowers=")
				.append(flowers).append(" leaves=").append(leaves).append(" snow=").append(snow).append(']');
			if (walls[palette.ordinal()] < 10_000) {
				faults.add(palette + " castle holds only " + walls[palette.ordinal()] + " blocks of " + palette.wall().getName().getString());
			}
			if (yardGround < 50) {
				faults.add(palette + " yard has " + yardGround + " blocks of " + palette.ground().getName().getString());
			}
			for (LostCastlePalette other : LostCastlePalette.values()) {
				if (other != palette && walls[other.ordinal()] != 0) {
					faults.add(palette + " castle holds " + walls[other.ordinal()] + " blocks of the " + other + " palette");
				}
			}
		}
		TowerTestSupport.result("castlePalettes", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the palettes are wrong: " + faults);
		helper.succeed();
	}

	private static boolean wall(final BlockState state, final LostCastlePalette palette) {
		return state.is(palette.wall()) || state.is(palette.worn());
	}

	/**
	 * Catches a floor with no double chest or more than two, a half chest with no partner, an empty chest, a floor
	 * without exactly two spawners, a spawner that shows more than one face, and a spawner that is not set in wall stone.
	 */
	@GameTest(maxTicks = 300)
	public void everyFloorHoldsOneOrTwoDoubleChestsAndTwoSpawnersSetInTheWalls(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (int floors = LostCastleGrid.MIN_FLOORS; floors <= LostCastleGrid.MAX_FLOORS; floors++) {
			LostCastlePalette palette = LostCastlePalette.values()[(floors + 1) % LostCastlePalette.values().length];
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, palette, FACINGS[floors % FACINGS.length], floors);
			ServerLevel level = arena.level();
			for (int floor = 0; floor < floors; floor++) {
				int feet = LostCastleGrid.feet(floors, floor);
				int doubles = 0;
				int singles = 0;
				int spawners = 0;
				int empty = 0;
				for (int x = 0; x < LostCastleGrid.SIZE; x++) {
					for (int z = 0; z < LostCastleGrid.SIZE; z++) {
						for (int y = feet; y <= LostCastleGrid.head(floors, floor); y++) {
							BlockPos at = arena.piece().worldPos(x, y, z);
							BlockState state = level.getBlockState(at);
							if (state.getBlock() instanceof ChestBlock) {
								if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest) || chest.isEmpty()) {
									empty++;
								}
								if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
									singles++;
								} else {
									BlockState partner = level.getBlockState(at.relative(ChestBlock.getConnectedDirection(state)));
									boolean whole = partner.getBlock() instanceof ChestBlock && partner.getValue(ChestBlock.TYPE) != state.getValue(ChestBlock.TYPE)
										&& partner.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING);
									if (!whole) {
										faults.add(floors + " floors: floor " + floor + " half chest at " + at + " has no partner");
									}
									doubles += state.getValue(ChestBlock.TYPE) == ChestType.LEFT ? 1 : 0;
								}
							}
							if (state.is(Blocks.SPAWNER)) {
								spawners++;
								int open = 0;
								boolean enclosed = true;
								for (Direction side : Direction.values()) {
									BlockState next = level.getBlockState(at.relative(side));
									if (next.isAir()) {
										open++;
										enclosed &= side.getAxis().isHorizontal();
									} else {
										enclosed &= wall(next, palette);
									}
								}
								if (open != 1 || !enclosed) {
									faults.add(floors + " floors: floor " + floor + " spawner at " + at + " shows " + open + " open faces and is not set in wall stone");
								}
							}
						}
					}
				}
				boolean bottom = floor == floors - 1;
				if (doubles < 1 || doubles > 2 || singles != (bottom ? 1 : 0) || spawners != 2 || empty != 0) {
					faults.add(floors + " floors: floor " + floor + " holds doubles=" + doubles + " singles=" + singles + " spawners=" + spawners + " empty=" + empty);
				}
				report.append(" [").append(floors).append('.').append(floor).append(" d=").append(doubles).append(" s=").append(singles).append(" sp=")
					.append(spawners).append(']');
			}
		}
		TowerTestSupport.result("castleContents", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "the floors do not hold what they should: " + faults);
		helper.succeed();
	}

	/**
	 * Catches rooms with nowhere to hide: every floor must hold standing cover inside its rooms (pillars and broken wall
	 * segments, counted as solid columns over the floor, off the lanes) and recesses cut into its outer wall.
	 */
	@GameTest(maxTicks = 200)
	public void everyFloorHoldsPillarsBrokenWallsAndAlcovesToHideIn(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (int floors = LostCastleGrid.MIN_FLOORS; floors <= LostCastleGrid.MAX_FLOORS; floors += 3) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, FACINGS[floors % FACINGS.length], floors);
			for (int floor = 0; floor < floors; floor++) {
				int feet = LostCastleGrid.feet(floors, floor);
				LostCastlePlan.Level level = arena.piece().plan().levels().get(floor);
				int cover = 0;
				int recess = 0;
				for (int x = 0; x < LostCastleGrid.SIZE; x++) {
					for (int z = 0; z < LostCastleGrid.SIZE; z++) {
						int ox = LostCastleGrid.cellOf(x) < 0 ? -1 : x - LostCastleGrid.cellLow(LostCastleGrid.cellOf(x));
						int oz = LostCastleGrid.cellOf(z) < 0 ? -1 : z - LostCastleGrid.cellLow(LostCastleGrid.cellOf(z));
						boolean laneColumn = LostCastleGrid.inLane(ox) || LostCastleGrid.inLane(oz);
						// The stairs' own stone is not cover: it stands along the wall of the two stair rooms, and the entrance's on floor 0.
						boolean stairStone = floor == 0 && x >= LostCastleGrid.MID - 2 && x <= LostCastleGrid.MID + 3 && z >= LostCastleGrid.ENTRY_Z;
						for (LostCastlePlan.Stair stair : new LostCastlePlan.Stair[] {level.exit(), level.arrival()}) {
							stairStone |= stair != null && LostCastleGrid.cellOf(x) == stair.cx() && (stair.north() ? z <= stair.barrierZ() : z >= stair.barrierZ());
						}
						// Two blocks up clears the props that stand a block high; a column of wall or stone there is cover.
						if (ox >= 0 && oz >= 0 && !laneColumn && !stairStone && wall(arena.at(x, feet + 2, z), LostCastlePalette.GREEN)) {
							cover++;
						}
						boolean outerLayer = x == LostCastleGrid.OUTER - 1 || x == LostCastleGrid.SIZE - LostCastleGrid.OUTER || z == LostCastleGrid.OUTER - 1
							|| z == LostCastleGrid.SIZE - LostCastleGrid.OUTER;
						if (outerLayer && arena.at(x, feet + 1, z).isAir()) {
							recess++;
						}
					}
				}
				report.append(" [").append(floors).append('.').append(floor).append(" cover=").append(cover).append(" recess=").append(recess).append(']');
				if (cover < 30 || recess < (LostCastlePlan.MIN_CHESTS + LostCastlePlan.NICHES) * LostCastlePlan.ALCOVE_WIDTH) {
					faults.add(floors + " floors: floor " + floor + " has cover " + cover + " and recess " + recess);
				}
			}
		}
		TowerTestSupport.result("castleHiding", "faults=" + faults + report);
		helper.assertTrue(faults.isEmpty(), "rooms with nowhere to hide: " + faults);
		helper.succeed();
	}

	/**
	 * Catches a portal that is not a smaller kin of the tower portal, one whose frame is open, and a mob that a
	 * spawner, the dark or a chunk raises inside the frame's volume. Mobs placed on purpose and mobs outside the
	 * volume are left alone.
	 */
	@GameTest(maxTicks = 100)
	public void theBottomPortalIsSmallerThanTheTowerPortalAndNoMobSpawnsInsideItsFrame(final GameTestHelper helper) {
		TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.GREEN, Direction.SOUTH, 3);
		register(arena);
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		int floors = piece.floors();
		int feet = LostCastleGrid.feet(floors, floors - 1);
		BlockPos portal = piece.portalPos();
		int lit = 0;
		int width = 0;
		int height = 0;
		for (int x = LostCastleBuilder.PORTAL_X - 1; x <= LostCastleBuilder.PORTAL_X + 3; x++) {
			boolean column = false;
			for (int y = feet + 1; y <= feet + 4; y++) {
				boolean on = arena.at(x, y, LostCastleBuilder.PORTAL_Z).is(TowerBlocks.TOWER_PORTAL);
				lit += on ? 1 : 0;
				column |= on;
			}
			width += column ? 1 : 0;
		}
		for (int y = feet + 1; y <= feet + 4; y++) {
			height += arena.at(LostCastleBuilder.PORTAL_X, y, LostCastleBuilder.PORTAL_Z).is(TowerBlocks.TOWER_PORTAL) ? 1 : 0;
		}
		int frame = 0;
		for (int x = LostCastleBuilder.PORTAL_X - 1; x <= LostCastleBuilder.PORTAL_X + 2; x++) {
			for (int y = feet; y <= feet + 3; y++) {
				boolean edge = x == LostCastleBuilder.PORTAL_X - 1 || x == LostCastleBuilder.PORTAL_X + 2 || y == feet || y == feet + 3;
				frame += edge && arena.at(x, y, LostCastleBuilder.PORTAL_Z).is(TowerBlocks.TOWER_FRAME) ? 1 : 0;
			}
		}
		BoundingBox volume = piece.portalFrame();
		List<String> faults = new ArrayList<>();
		for (BlockPos at : BlockPos.betweenClosed(volume.minX(), volume.minY(), volume.minZ(), volume.maxX(), volume.maxY(), volume.maxZ())) {
			for (EntitySpawnReason reason : new EntitySpawnReason[] {EntitySpawnReason.SPAWNER, EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION}) {
				Mob mob = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(level, reason));
				mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				level.addFreshEntity(mob);
				if (!mob.isRemoved()) {
					faults.add("a " + reason + " mob stood in the frame volume at " + at);
					mob.discard();
				}
			}
		}
		Mob placed = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT));
		placed.snapTo(portal.getX() + 0.5, portal.getY(), portal.getZ() + 0.5);
		level.addFreshEntity(placed);
		boolean placedStays = !placed.isRemoved();
		placed.discard();
		BlockPos away = piece.worldPos(LostCastleGrid.cellLow(1) + 1, feet, LostCastleGrid.cellLow(1) + 1);
		Mob outside = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.SPAWNER));
		outside.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		level.addFreshEntity(outside);
		boolean outsideStays = !outside.isRemoved();
		outside.discard();
		TowerTestSupport.result("castlePortal", "lit=" + lit + " opening=" + width + "x" + height + " towerOpening=" + TowerLayout.PORTAL_INNER_WIDTH + "x"
			+ TowerLayout.PORTAL_INNER_HEIGHT + " frame=" + frame + " volume=" + volume + " faults=" + faults + " placedStays=" + placedStays
			+ " outsideStays=" + outsideStays);
		boolean footed = arena.at(LostCastleBuilder.PORTAL_X, feet, LostCastleBuilder.PORTAL_Z).is(TowerBlocks.TOWER_FRAME);
		helper.assertTrue(footed && frame == 12 && lit == width * height && lit > 0, "the portal is not whole: lit=" + lit + " frame=" + frame + " footed=" + footed);
		helper.assertTrue(width <= TowerLayout.PORTAL_INNER_WIDTH && height <= TowerLayout.PORTAL_INNER_HEIGHT && width * height < TowerLayout.PORTAL_INNER_WIDTH
			* TowerLayout.PORTAL_INNER_HEIGHT, "the portal's opening " + width + "x" + height + " is not smaller than the tower portal's");
		helper.assertTrue(faults.isEmpty(), "mobs spawned inside the portal frame: " + faults);
		helper.assertTrue(placedStays, "a mob placed on purpose in the frame was removed");
		helper.assertTrue(outsideStays, "a spawner mob outside the frame was removed");
		helper.succeed();
	}

	/**
	 * Catches a vault that is open, one without a cracked stone brick wall, or one with poor loot: the chest is
	 * not reached on foot until the cracked wall is broken, and then it is.
	 */
	@GameTest(maxTicks = 200)
	public void theBottomFloorHidesAVaultBehindCrackedStoneBrick(final GameTestHelper helper) {
		List<String> faults = new ArrayList<>();
		for (int floors : new int[] {LostCastleGrid.MIN_FLOORS, LostCastleGrid.MAX_FLOORS}) {
			TowerLostCastleSupport.Arena arena = TowerLostCastleSupport.castle(helper, LostCastlePalette.SNOW, FACINGS[floors % FACINGS.length], floors);
			ServerLevel level = arena.level();
			LostRoomPiece piece = arena.piece();
			int feet = LostCastleGrid.feet(floors, floors - 1);
			List<BlockPos> singles = new ArrayList<>();
			List<BlockPos> cracked = new ArrayList<>();
			for (int x = 0; x < LostCastleGrid.SIZE; x++) {
				for (int z = 0; z < LostCastleGrid.SIZE; z++) {
					for (int y = feet; y <= LostCastleGrid.head(floors, floors - 1); y++) {
						BlockPos at = piece.worldPos(x, y, z);
						BlockState state = level.getBlockState(at);
						if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
							singles.add(at);
						}
						if (state.is(Blocks.CRACKED_STONE_BRICKS)) {
							cracked.add(at);
						}
					}
				}
			}
			if (singles.size() != 1 || cracked.size() < LostCastlePlan.Vault.INSIDE * LostCastlePlan.Vault.INSIDE) {
				faults.add(floors + " floors: " + singles.size() + " vault chests and " + cracked.size() + " cracked bricks");
				continue;
			}
			BlockPos chestAt = singles.get(0);
			int loot = 0;
			if (level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest) {
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					loot += chest.getItem(slot).isEmpty() ? 0 : 1;
				}
			}
			if (loot < 20) {
				faults.add(floors + " floors: the vault chest holds " + loot + " stacks");
			}
			Set<BlockPos> closed = TowerLostCastleSupport.walk(level, arena.box(), piece.entranceTop());
			boolean sealed = Direction.Plane.HORIZONTAL.stream().noneMatch(side -> closed.contains(chestAt.relative(side)));
			cracked.forEach(at -> level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
			Set<BlockPos> opened = TowerLostCastleSupport.walk(level, arena.box(), piece.entranceTop());
			boolean reached = Direction.Plane.HORIZONTAL.stream().anyMatch(side -> opened.contains(chestAt.relative(side)));
			TowerTestSupport.result("castleVault", "floors=" + floors + " chest=" + chestAt + " cracked=" + cracked.size() + " loot=" + loot + " sealed=" + sealed
				+ " reachedAfter=" + reached);
			if (!sealed || !reached) {
				faults.add(floors + " floors: the vault is " + (sealed ? "" : "open before the cracked wall breaks, ") + (reached ? "" : "closed after it breaks"));
			}
		}
		helper.assertTrue(faults.isEmpty(), "the vault is wrong: " + faults);
		helper.succeed();
	}
}
