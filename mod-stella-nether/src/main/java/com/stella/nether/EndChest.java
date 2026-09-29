package com.stella.nether;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.jspecify.annotations.Nullable;

/**
 * An End portal opens only once the sealed chest in its room holds a Blaze Core. The eyes still go in
 * first, as in vanilla; filling the last one while the chest is empty leaves the portal shut. A stronghold
 * built before this mod has no chest, so completing its frame raises one on the frame.
 */
public final class EndChest {
	/** How far from a frame the chest that governs it may stand. */
	public static final int RANGE = 16;
	/** Where a stronghold's portal room keeps its chest, in the room's own coordinates. */
	public static final int ROOM_X = 3;
	public static final int ROOM_Y = 1;
	public static final int ROOM_Z = 2;

	/** Every chest a portal room has placed while the server runs, in the order placed. */
	private static final java.util.List<BlockPos> PLACED = new java.util.concurrent.CopyOnWriteArrayList<>();

	private EndChest() {
	}

	public static void placedInRoom(final BlockPos at) {
		PLACED.add(at);
	}

	public static java.util.List<BlockPos> placedInRooms() {
		return java.util.List.copyOf(PLACED);
	}

	/** Whether a filled chest stands near enough to a frame to let its portal open. */
	public static boolean opens(final Level level, final BlockPos frame) {
		return nearest(level, frame, true) != null;
	}

	/** Called as a frame is completed while it may not open: says why, and raises a chest if the room has none. */
	public static void refused(final ServerLevel level, final BlockPos frame, final @Nullable Player player) {
		if (nearest(level, frame, false) == null && level.getBlockState(frame.above()).isAir()) {
			level.setBlock(frame.above(), NetherBlocks.SEALED_CHEST.defaultBlockState(), Block.UPDATE_ALL);
			StellaNether.LOGGER.info("[NETHER] a sealed chest raised on the frame at {}", frame);
		}
		if (player instanceof ServerPlayer server) {
			server.sendOverlayMessage(Component.translatable("message.mod-stella-nether.portal_sealed").withStyle(ChatFormatting.GOLD));
		}
	}

	/** Takes one Blaze Core into the chest and opens the portal it stands by, if the frame is complete. */
	static void fill(final ServerLevel level, final BlockPos chest, final Player player, final ItemStack stack) {
		stack.consume(1, player);
		level.setBlock(chest, level.getBlockState(chest).setValue(SealedChestBlock.FILLED, true), Block.UPDATE_ALL);
		StellaNether.LOGGER.info("[NETHER] a Blaze Core set in the sealed chest at {}", chest);
		openNear(level, chest);
	}

	/** Opens the first complete frame near a chest: the nine portal blocks, as an eye completing it would. */
	static boolean openNear(final ServerLevel level, final BlockPos chest) {
		BlockPattern shape = EndPortalFrameBlock.getOrCreatePortalShape();
		for (BlockPos at : BlockPos.betweenClosed(chest.offset(-RANGE, -4, -RANGE), chest.offset(RANGE, 4, RANGE))) {
			BlockState state = level.getBlockState(at);
			if (!state.is(Blocks.END_PORTAL_FRAME) || !state.getValue(EndPortalFrameBlock.HAS_EYE)) {
				continue;
			}
			BlockPattern.BlockPatternMatch match = shape.find(level, at);
			if (match == null) {
				continue;
			}
			BlockPos corner = match.getFrontTopLeft().offset(-3, 0, -3);
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					level.setBlock(corner.offset(x, 0, z), Blocks.END_PORTAL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			level.globalLevelEvent(1038, corner.offset(1, 0, 1), 0);
			StellaNether.LOGGER.info("[NETHER] End portal opened at {}", corner.offset(1, 0, 1));
			return true;
		}
		return false;
	}

	private static @Nullable BlockPos nearest(final Level level, final BlockPos frame, final boolean filled) {
		for (BlockPos at : BlockPos.betweenClosed(frame.offset(-RANGE, -4, -RANGE), frame.offset(RANGE, 4, RANGE))) {
			BlockState state = level.getBlockState(at);
			if (state.is(NetherBlocks.SEALED_CHEST) && (!filled || state.getValue(SealedChestBlock.FILLED))) {
				return at.immutable();
			}
		}
		return null;
	}
}
