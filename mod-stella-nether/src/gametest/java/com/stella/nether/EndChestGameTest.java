package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The End portal opens only once the sealed chest in its room holds a Blaze Core; the chest cannot be broken. */
public class EndChestGameTest {
	/** The frame's last empty socket, in the middle of its north side. */
	static final BlockPos LAST = new BlockPos(3, 1, 1);
	static final BlockPos CHEST = new BlockPos(7, 1, 3);

	/**
	 * Clears everything a chest could govern from, first: test batches reuse the same ground, and a filled chest
	 * or open portal an earlier test left beside it would open this test's frame.
	 */
	static void clearAround(final GameTestHelper helper) {
		int reach = EndChest.RANGE + 6;
		for (BlockPos at : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-reach, 0, -reach)), helper.absolutePos(new BlockPos(reach, 5, reach)))) {
			var state = helper.getLevel().getBlockState(at);
			if (state.is(NetherBlocks.SEALED_CHEST) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
				helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
			}
		}
	}

	/** A complete ring of twelve frames around a 3 by 3 hole at (2..4, 1, 2..4), every eye in but the last. */
	static void frame(final GameTestHelper helper) {
		clearAround(helper);
		for (int i = 2; i <= 4; i++) {
			place(helper, new BlockPos(i, 1, 1), Direction.SOUTH);
			place(helper, new BlockPos(i, 1, 5), Direction.NORTH);
			place(helper, new BlockPos(1, 1, i), Direction.EAST);
			place(helper, new BlockPos(5, 1, i), Direction.WEST);
		}
	}

	private static void place(final GameTestHelper helper, final BlockPos at, final Direction facing) {
		helper.setBlock(at, Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.FACING, facing)
			.setValue(EndPortalFrameBlock.HAS_EYE, !at.equals(LAST)));
	}

	/** Sets the last eye as a player would. */
	static void lastEye(final GameTestHelper helper, final ServerPlayer player) {
		BlockPos at = helper.absolutePos(LAST);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE));
		Items.ENDER_EYE.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
	}

	static int portalBlocks(final GameTestHelper helper) {
		int count = 0;
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				count += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.END_PORTAL) ? 1 : 0;
			}
		}
		return count;
	}

	/** Puts what the player holds into the chest, as a right-click would. */
	static void offer(final GameTestHelper helper, final ServerPlayer player, final BlockPos chest) {
		BlockPos at = helper.absolutePos(chest);
		BlockState state = helper.getLevel().getBlockState(at);
		state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	@GameTest(environment = "mod-stella-nether-test:chest_shut", maxTicks = 20)
	public void theLastEyeLeavesThePortalShutWhileTheChestIsEmpty(final GameTestHelper helper) {
		frame(helper);
		helper.setBlock(CHEST, NetherBlocks.SEALED_CHEST);
		ServerPlayer player = NetherTestSupport.join(helper, 7.5, 5.5);
		lastEye(helper, player);
		boolean eyeIn = helper.getBlockState(LAST).getValue(EndPortalFrameBlock.HAS_EYE);
		int portal = portalBlocks(helper);
		NetherTestSupport.result("portalShut", "eyeIn=" + eyeIn + " portal=" + portal);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(eyeIn && portal == 0, "with an empty chest the last eye went in=" + eyeIn + " and opened " + portal + " portal blocks");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:chest_open", maxTicks = 20)
	public void oneBlazeCoreInTheChestOpensThePortal(final GameTestHelper helper) {
		frame(helper);
		helper.setBlock(CHEST, NetherBlocks.SEALED_CHEST);
		ServerPlayer player = NetherTestSupport.join(helper, 7.5, 5.5);
		lastEye(helper, player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NetherItems.BLAZE_CORE, 2));
		offer(helper, player, CHEST);
		int portal = portalBlocks(helper);
		int left = player.getMainHandItem().getCount();
		NetherTestSupport.result("portalOpens", "portal=" + portal + " coresLeft=" + left);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(portal == 9 && left == 1, "one core opened " + portal + " portal blocks and left " + left + " of 2");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:chest_once", maxTicks = 20)
	public void aFilledChestTakesNoSecondCore(final GameTestHelper helper) {
		frame(helper);
		helper.setBlock(CHEST, NetherBlocks.SEALED_CHEST);
		ServerPlayer player = NetherTestSupport.join(helper, 7.5, 5.5);
		lastEye(helper, player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(NetherItems.BLAZE_CORE, 2));
		offer(helper, player, CHEST);
		offer(helper, player, CHEST);
		int left = player.getMainHandItem().getCount();
		NetherTestSupport.result("chestOnce", "coresLeft=" + left);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(left == 1, "the chest took " + (2 - left) + " cores");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:chest_raised", maxTicks = 20)
	public void aRoomWithoutAChestRaisesOneOnTheFrame(final GameTestHelper helper) {
		frame(helper);
		ServerPlayer player = NetherTestSupport.join(helper, 7.5, 5.5);
		lastEye(helper, player);
		boolean raised = helper.getBlockState(LAST.above()).is(NetherBlocks.SEALED_CHEST);
		int portal = portalBlocks(helper);
		NetherTestSupport.result("chestRaised", "raised=" + raised + " portal=" + portal);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(raised && portal == 0, "a chestless room raised a chest=" + raised + " and opened " + portal);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:chest_hard", maxTicks = 20)
	public void theChestCannotBeBrokenOrBlownUp(final GameTestHelper helper) {
		helper.setBlock(CHEST, NetherBlocks.SEALED_CHEST);
		BlockState state = helper.getBlockState(CHEST);
		float hardness = state.getDestroySpeed(helper.getLevel(), helper.absolutePos(CHEST));
		float resistance = state.getBlock().getExplosionResistance();
		NetherTestSupport.result("chestUnbreakable", "hardness=" + hardness + " resistance=" + resistance);
		helper.assertTrue(hardness < 0.0F && resistance >= Blocks.BEDROCK.getExplosionResistance(),
			"the chest has hardness " + hardness + " and resistance " + resistance);
		helper.succeed();
	}

	/**
	 * Places a stronghold as the place command does, but loads every chunk under it first; the command refuses
	 * a structure whose chunks are not already loaded.
	 */
	static void placeStronghold(final GameTestHelper helper, final ServerLevel level, final BlockPos at) {
		var stronghold = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
			.getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.STRONGHOLD);
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		var start = stronghold.value().generate(stronghold, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
			random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED), random,
			level.getStructureTemplateManager(), level.getSeed(), net.minecraft.world.level.ChunkPos.containing(at), 0, level, biome -> true);
		helper.assertTrue(start.isValid(), "no stronghold could be placed at " + at);
		var box = start.getBoundingBox();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.getChunk(cx, cz);
				var chunk = new net.minecraft.world.level.ChunkPos(cx, cz);
				start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), new net.minecraft.world.level.levelgen.structure.BoundingBox(
					chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ()), chunk);
			}
		}
	}

	@GameTest(environment = "mod-stella-nether-test:chest_stronghold", maxTicks = 400)
	public void aStrongholdsPortalRoomKeepsOneChestBesideItsFrame(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int before = EndChest.placedInRooms().size();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1)).offset(3000, 0, 0);
		placeStronghold(helper, level, at);
		helper.succeedWhen(() -> {
			List<BlockPos> placed = EndChest.placedInRooms().subList(before, EndChest.placedInRooms().size());
			helper.assertTrue(!placed.isEmpty(), "no portal room has placed a chest yet");
			BlockPos chest = placed.get(0);
			int frames = 0;
			for (BlockPos near : BlockPos.betweenClosed(chest.offset(-EndChest.RANGE, -4, -EndChest.RANGE), chest.offset(EndChest.RANGE, 4, EndChest.RANGE))) {
				frames += level.getBlockState(near).is(Blocks.END_PORTAL_FRAME) ? 1 : 0;
			}
			boolean standing = level.getBlockState(chest).is(NetherBlocks.SEALED_CHEST);
			boolean floored = level.getBlockState(chest.below()).isFaceSturdy(level, chest.below(), Direction.UP);
			NetherTestSupport.result("strongholdChest", "chests=" + placed.size() + " frames=" + frames + " standing=" + standing + " floored=" + floored + " at=" + chest);
			helper.assertTrue(placed.size() == 1 && frames == 12 && standing && floored,
				placed.size() + " chests, " + frames + " frames within reach, standing=" + standing + " floored=" + floored);
		});
	}
}
