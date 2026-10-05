package com.stella.tower;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Every castle variant, over many seeds and all four facings: it stands, its portal is lit, its workstations
 * stand by the portal, it holds four stocked chests, its yard raises its mobs, and a player walks from the
 * gate to the portal and back.
 */
public class TowerLostCastleVariantGameTest {
	/** Castles built for each variant: each a fresh seed, the facings in turn. */
	public static final int SEEDS = 8;
	/** How near the portal the workstations stand. */
	public static final int STATION_REACH = 10;
	/** The farthest a walker drops in one step. */
	public static final int DROP = 3;

	@GameTest(maxTicks = 40)
	public void theCastleVariantIsWalkableAndWhole(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CASTLE);
	}

	@GameTest(maxTicks = 40)
	public void theKeepVariantIsWalkableAndWhole(final GameTestHelper helper) {
		check(helper, LostCastleVariant.KEEP);
	}

	@GameTest(maxTicks = 40)
	public void theMazeVariantIsWalkableAndWhole(final GameTestHelper helper) {
		check(helper, LostCastleVariant.MAZE);
	}

	@GameTest(maxTicks = 40)
	public void theFloodedVariantIsWalkableAndWhole(final GameTestHelper helper) {
		check(helper, LostCastleVariant.FLOODED);
	}

	@GameTest(maxTicks = 40)
	public void theCryptVariantIsWalkableAndWhole(final GameTestHelper helper) {
		check(helper, LostCastleVariant.CRYPT);
	}

	@GameTest(maxTicks = 40)
	public void rubbleKeepsTheGateEntranceOpen(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper);
		LostRoomPiece piece = arena.piece();
		BoundingBox box = BoundingBox.infinite();
		for (int x = LostRoomPiece.GATE_X; x <= LostRoomPiece.GATE_X + 1; x++) {
			piece.set(arena.level(), box, Blocks.AIR.defaultBlockState(), x, 1, 1);
			piece.set(arena.level(), box, Blocks.AIR.defaultBlockState(), x, 2, 1);
			piece.placeRubble(arena.level(), box, RandomSource.create(x), x, 1, 1);
		}
		BlockPos gate = piece.gateInside();
		boolean open = standable(arena.level(), gate) && standable(arena.level(), gate.relative(Direction.EAST));
		TowerTestSupport.result("gateRubble", "open=" + open);
		helper.assertTrue(open, "rubble blocked the built gate entrance");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aRejectedYardCandidateGetsReplaced(final GameTestHelper helper) {
		TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, LostCastleVariant.CASTLE, Direction.NORTH);
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.experienceLevel = 40;
		AtomicInteger eligible = new AtomicInteger();
		List<Mob> raised = List.of();
		try {
			raised = LostCastles.stock(arena.level(), arena.piece(), List.of(player), RandomSource.create(20261005L),
				LostCastles.MIN_MOBS, mob -> eligible.getAndIncrement() > 0);
			Set<UUID> identities = raised.stream().map(Mob::getUUID).collect(java.util.stream.Collectors.toSet());
			long present = raised.stream().filter(mob -> arena.level().getEntity(mob.getUUID()) == mob).count();
			TowerTestSupport.result("castleRetry", "requested=" + LostCastles.MIN_MOBS + " eligible=" + eligible.get()
				+ " added=" + raised.size() + " distinct=" + identities.size() + " present=" + present);
			helper.assertTrue(eligible.get() > 0, "no eligible yard candidate reached the injected rejection");
			helper.assertTrue(raised.size() == LostCastles.MIN_MOBS && identities.size() == raised.size() && present == raised.size(),
				"a rejected candidate left only " + raised.size() + " actual yard mobs");
			helper.succeed();
		} finally {
			raised.forEach(Mob::discard);
			TowerTestSupport.leave(helper, player);
		}
	}

	@GameTest(maxTicks = 20)
	public void theSeedChoosesEveryVariant(final GameTestHelper helper) {
		Map<LostCastleVariant, Integer> seen = new EnumMap<>(LostCastleVariant.class);
		int castles = 1000;
		for (int i = 0; i < castles; i++) {
			LostRoomPiece piece = new LostRoomPiece(i * 400, 64 + i % 7, -i * 336, Direction.NORTH);
			seen.merge(piece.variant(), 1, Integer::sum);
		}
		TowerTestSupport.result("castleVariantShare", "of " + castles + ": " + seen);
		boolean even = seen.size() == LostCastleVariant.values().length
			&& seen.values().stream().allMatch(count -> count >= castles / LostCastleVariant.values().length / 2);
		helper.assertTrue(even, "the seed does not choose every variant fairly: " + seen);
		helper.succeed();
	}

	static void check(final GameTestHelper helper, final LostCastleVariant variant) {
		StringBuilder wrong = new StringBuilder();
		Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.experienceLevel = 40;
		int walked = 0;
		for (int seed = 0; seed < SEEDS; seed++) {
			TowerLostRoomGameTest.Arena arena = TowerLostRoomGameTest.castle(helper, variant, facings[seed % facings.length]);
			if (seed == 0) {
				TowerLostRoomGameTest.dump(helper, arena, "lost_castle_" + variant.name().toLowerCase(java.util.Locale.ROOT) + ".txt");
			}
			String fault = whole(arena, player);
			if (fault.isEmpty()) {
				walked++;
			} else {
				wrong.append(" [seed ").append(seed).append(' ').append(facings[seed % facings.length]).append(':').append(fault).append(']');
			}
		}
		TowerTestSupport.result("castleVariant", "variant=" + variant + " sound=" + walked + "/" + SEEDS + " wrong=" + wrong);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(walked == SEEDS, variant + " castles fail:" + wrong);
		helper.succeed();
	}

	/** What is wrong with one built castle, or nothing. */
	static String whole(final TowerLostRoomGameTest.Arena arena, final ServerPlayer player) {
		ServerLevel level = arena.level();
		LostRoomPiece piece = arena.piece();
		StringBuilder fault = new StringBuilder();
		BlockPos portal = piece.portalPos();
		boolean lit = level.getBlockState(portal).is(TowerBlocks.TOWER_PORTAL) && level.getBlockState(portal.above()).is(TowerBlocks.TOWER_PORTAL)
			&& level.getBlockState(portal.below()).is(TowerBlocks.TOWER_FRAME);
		if (!lit) {
			fault.append(" unlit");
		}
		BoundingBox box = piece.getBoundingBox();
		int low = box.minY() + piece.lowest() - 1;
		Set<Block> nearPortal = new HashSet<>();
		int stocked = 0;
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), low, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(at);
			if (at.distManhattan(portal) <= 2 * STATION_REACH && Math.abs(at.getY() - portal.getY()) <= 2) {
				nearPortal.add(state.getBlock());
			}
			if (state.is(Blocks.CHEST) && level.getBlockEntity(at) instanceof ChestBlockEntity chest && !chest.isEmpty()) {
				stocked++;
			}
		}
		boolean stations = nearPortal.contains(Blocks.CRAFTING_TABLE) && nearPortal.contains(Blocks.FURNACE) && nearPortal.contains(Blocks.CANDLE)
			&& nearPortal.stream().anyMatch(block -> block.defaultBlockState().is(BlockTags.ANVIL));
		if (!stations) {
			fault.append(" noStations");
		}
		if (stocked < 4) {
			fault.append(" chests=").append(stocked);
		}
		BlockPos gate = piece.gateInside();
		Set<BlockPos> fromGate = walk(level, box, low, gate);
		Set<BlockPos> fromPortal = walk(level, box, low, portal);
		if (!standable(level, gate)) {
			fault.append(" gateBlocked(").append(level.getBlockState(gate.below()).getBlock()).append('/').append(level.getBlockState(gate).getBlock())
				.append('/').append(level.getBlockState(gate.above()).getBlock()).append(')');
		}
		if (!fromGate.contains(portal)) {
			fault.append(" noWayIn(reached=").append(fromGate.size()).append(')');
		}
		if (!fromPortal.contains(gate)) {
			fault.append(" noWayOut(reached=").append(fromPortal.size()).append(')');
		}
		List<Mob> raised = LostCastles.stock(level, piece, List.of(player));
		raised.forEach(Mob::discard);
		if (raised.size() < LostCastles.MIN_MOBS) {
			fault.append(" yardMobs=").append(raised.size());
		}
		return fault.toString();
	}

	/** Whether a body may be in this cell: no lava, no collision, or a carpet's at the feet; doors open. */
	static boolean passable(final ServerLevel level, final BlockPos at, final boolean feet) {
		BlockState state = level.getBlockState(at);
		if (state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		if (state.getBlock() instanceof DoorBlock) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, at);
		return shape.isEmpty() || feet && shape.max(Direction.Axis.Y) <= 0.125;
	}

	/** Whether a block holds a walker up: a collision at least half high and at most one; never lava or a door. */
	static boolean support(final ServerLevel level, final BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (state.getFluidState().is(FluidTags.LAVA) || state.getBlock() instanceof DoorBlock) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(level, at);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) >= 0.5 && shape.max(Direction.Axis.Y) <= 1.0;
	}

	/** Feet here: two blocks of room and a block to stand on. */
	static boolean standable(final ServerLevel level, final BlockPos feet) {
		return passable(level, feet, true) && passable(level, feet.above(), false) && support(level, feet.below());
	}

	/**
	 * Every feet spot a walker reaches from a start: a step across, a jump of one with a third block of room
	 * over the take-off, or a drop of up to {@value #DROP}. The walk keeps within the castle's box, two out.
	 */
	static Set<BlockPos> walk(final ServerLevel level, final BoundingBox box, final int low, final BlockPos start) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos here = queue.poll();
			for (Direction way : Direction.Plane.HORIZONTAL) {
				BlockPos next = here.relative(way);
				if (next.getX() < box.minX() - 2 || next.getX() > box.maxX() + 2 || next.getZ() < box.minZ() - 2 || next.getZ() > box.maxZ() + 2
					|| next.getY() < low || next.getY() > box.maxY() + 2) {
					continue;
				}
				BlockPos to = null;
				if (standable(level, next)) {
					to = next;
				} else if (standable(level, next.above()) && passable(level, here.above(2), false)) {
					to = next.above();
				} else if (passable(level, next, true) && passable(level, next.above(), false)) {
					for (int drop = 1; drop <= DROP; drop++) {
						BlockPos down = next.below(drop);
						if (standable(level, down)) {
							to = down;
							break;
						}
						if (!passable(level, down, false)) {
							break;
						}
					}
				}
				if (to != null && seen.add(to.immutable())) {
					queue.add(to.immutable());
				}
			}
		}
		return seen;
	}
}
