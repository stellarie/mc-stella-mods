package com.stella.nether;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The End stays sealed until a player steps into an End portal carrying {@link #CORES_TO_OPEN} Blaze Cores.
 * The cores are spent and the End is open to everyone from then on, for good. Vanilla's own requirements, the
 * eyes in the frame, still stand before any of this.
 */
public final class EndSeal {
	public static final int CORES_TO_OPEN = 3;
	public static final AttachmentType<Boolean> OPEN = AttachmentRegistry.createPersistent(StellaNether.id("end_open"), Codec.BOOL);

	private EndSeal() {
	}

	public static boolean open(final MinecraftServer server) {
		return Boolean.TRUE.equals(((AttachmentTarget) server.overworld()).getAttached(OPEN));
	}

	/** Test hook: seals the End again. */
	static void seal(final MinecraftServer server) {
		((AttachmentTarget) server.overworld()).removeAttached(OPEN);
	}

	/** Whether an End portal lets this entity through toward the End; a player with the cores opens it. */
	public static boolean allows(final ServerLevel from, final Entity entity) {
		MinecraftServer server = from.getServer();
		if (from.dimension().equals(Level.END) || open(server)) {
			return true;
		}
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		if (count(player) < CORES_TO_OPEN) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-nether.end_sealed", CORES_TO_OPEN).withStyle(ChatFormatting.GOLD));
			return false;
		}
		take(player, CORES_TO_OPEN);
		((AttachmentTarget) server.overworld()).setAttached(OPEN, true);
		server.getPlayerList().broadcastSystemMessage(
			Component.translatable("message.mod-stella-nether.end_opened", player.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		StellaNether.LOGGER.info("[NETHER] the End opened by {}", player.getScoreboardName());
		return true;
	}

	public static int count(final ServerPlayer player) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(NetherItems.BLAZE_CORE)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void take(final ServerPlayer player, final int wanted) {
		int left = wanted;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(NetherItems.BLAZE_CORE)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
	}
}
