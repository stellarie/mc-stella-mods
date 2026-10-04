package com.stella.rpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.stella.rpg.StellaRpg;
import com.stella.rpg.WeaponSlot;
import com.stella.rpg.client.mixin.client.ContainerScreenPositionAccessor;
import com.stella.rpg.net.WeaponHotkeyPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** The weapon box beside the hotbar, the hotkeys sent to the server, the ready-weapon key, and the slot frame. */
@Environment(EnvType.CLIENT)
public final class WeaponSlotClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(StellaRpg.id("weapon_slot"));
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final int PANEL = 0xFFC6C6C6;
	// The grid cell that index 35 leaves.
	private static final int VACATED_X = 8 + 8 * 18;
	private static final int VACATED_Y = 84 + 2 * 18;
	private static final Identifier BOX_LEFT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
	private static final Identifier BOX_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
	private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
	private static final int HALF_HOTBAR = 91;
	private static final int BOX_WIDTH = 29;
	private static KeyMapping readyKey;

	private WeaponSlotClient() {
	}

	public static void register() {
		readyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.mod-stella-rpg.ready_weapon", InputConstants.KEY_R, CATEGORY
		));
		ClientTickEvents.END_CLIENT_TICK.register(WeaponSlotClient::pollReadyKey);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, StellaRpg.id("weapon_box"), (graphics, tracker) -> {
			if (active()) {
				drawWeaponBox(graphics);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ContainerScreenPositionAccessor position = (ContainerScreenPositionAccessor) screen;
				ScreenEvents.afterBackground(screen).register((background, graphics, mouseX, mouseY, delta) ->
					drawFrame(graphics, position.stellaRpgLeftPos(), position.stellaRpgTopPos()));
			}
		});
	}

	/** A vanilla server keeps the vanilla hotbar. */
	public static boolean active() {
		return ClientPlayNetworking.canSend(WeaponHotkeyPayload.TYPE);
	}

	public static boolean sendHotkey(final int index) {
		if (!active()) {
			return false;
		}
		ClientPlayNetworking.send(new WeaponHotkeyPayload(index));
		return true;
	}

	private static void pollReadyKey(final Minecraft client) {
		while (readyKey.consumeClick()) {
			if (client.player != null && client.gui.screen() == null && !client.player.isSpectator()) {
				sendHotkey(WeaponSlot.READY);
			}
		}
	}

	/** The weapon slot beside the hotbar, on the side the offhand box does not use; lit while the weapon is held. */
	private static void drawWeaponBox(final GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.isSpectator()) {
			return;
		}
		boolean right = client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		int center = graphics.guiWidth() / 2;
		int height = graphics.guiHeight();
		int boxX = right ? center + HALF_HOTBAR : center - HALF_HOTBAR - BOX_WIDTH;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, right ? BOX_RIGHT : BOX_LEFT, boxX, height - 23, BOX_WIDTH, 24);
		int itemX = right ? boxX + 10 : boxX + 3;
		if (WeaponSlot.isSlot(client.player.getInventory().getSelectedSlot())) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION, itemX - 4, height - 23, 24, 23);
		}
		net.minecraft.world.item.ItemStack weapon = client.player.getInventory().getItem(WeaponSlot.SLOT);
		if (!weapon.isEmpty()) {
			graphics.item(client.player, weapon, itemX, height - 19, 0);
			graphics.itemDecorations(client.font, weapon, itemX, height - 19);
		}
	}

	private static void drawFrame(final GuiGraphicsExtractor graphics, final int left, final int top) {
		graphics.fill(left + VACATED_X - 1, top + VACATED_Y - 1, left + VACATED_X + 17, top + VACATED_Y + 17, PANEL);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, left + WeaponSlot.MENU_X - 1, top + WeaponSlot.MENU_Y - 1, 18, 18);
	}
}
