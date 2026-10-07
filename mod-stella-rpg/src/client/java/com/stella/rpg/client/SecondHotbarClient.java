package com.stella.rpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stella.rpg.SecondHotbar;
import com.stella.rpg.StellaRpg;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The second hotbar: the swap key, and the row itself drawn left of the vanilla hotbar. The row comes from the
 * server attachment, so a server without this mod leaves both off.
 */
@Environment(EnvType.CLIENT)
public final class SecondHotbarClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(StellaRpg.id("second_hotbar"));
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	/** A vanilla slot is 18 pixels wide, and the hotbar sprite is 182. */
	private static final int SLOT = 18;
	private static final int ROW_WIDTH = SLOT * SecondHotbar.SIZE;
	private static final int HOTBAR_WIDTH = 182;
	private static final int ROW_BOTTOM = 22;
	private static final int HELD = 0x80FFFFFF;
	private static KeyMapping swapKey;

	private SecondHotbarClient() {
	}

	public static void register() {
		swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.mod-stella-rpg.swap_hotbar", InputConstants.KEY_V, CATEGORY
		));
		ClientTickEvents.END_CLIENT_TICK.register(SecondHotbarClient::pollSwapKey);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, StellaRpg.id("second_hotbar"), (graphics, tracker) -> {
			if (active()) {
				drawRow(graphics);
			}
		});
	}

	/** A server with this mod, and an inventory to read the row from. */
	public static boolean active() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && ClientPlayNetworking.canSend(SecondHotbar.SwapPayload.TYPE);
	}

	private static void pollSwapKey(final Minecraft client) {
		while (swapKey.consumeClick()) {
			if (client.player != null && client.gui.screen() == null && !client.player.isSpectator() && active()) {
				ClientPlayNetworking.send(new SecondHotbar.SwapPayload());
			}
		}
	}

	/**
	 * Nine slots left of the vanilla hotbar, so the row the swap key brings in is visible before the press. The slot
	 * the player holds is marked here too, because the swap keeps that index in both rows.
	 */
	private static void drawRow(final GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		SecondHotbar row = SecondHotbar.of(client.player);
		int left = graphics.guiWidth() / 2 - HOTBAR_WIDTH / 2 - ROW_WIDTH;
		int top = graphics.guiHeight() - ROW_BOTTOM;
		int held = client.player.getInventory().getSelectedSlot();
		for (int i = 0; i < SecondHotbar.SIZE; i++) {
			int x = left + i * SLOT;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, top, SLOT, SLOT);
			if (i == held) {
				graphics.fill(x, top, x + SLOT - 2, top + SLOT - 2, HELD);
			}
			ItemStack stack = row.get(i);
			if (!stack.isEmpty()) {
				graphics.item(client.player, stack, x + 1, top + 1, 0);
				graphics.itemDecorations(client.font, stack, x + 1, top + 1);
			}
		}
	}
}
