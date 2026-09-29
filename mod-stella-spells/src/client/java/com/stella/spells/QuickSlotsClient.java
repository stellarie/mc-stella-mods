package com.stella.spells;

import com.mojang.blaze3d.platform.InputConstants;
import com.stella.spells.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** The four quick slot keys, their cells right of the hotbar, and their frames beside the inventory screen. */
public final class QuickSlotsClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quick_slots"));
	private static final KeyMapping[] KEYS = new KeyMapping[QuickSlots.COUNT];
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	/** Gap between the hotbar's right edge and the first cell; a left-handed player's offhand cell sits there, so the gap widens. */
	private static final int HUD_GAP = 34;
	private static final int HUD_GAP_LEFT_HANDED = 63;
	private static final int CELL = 20;

	@Override
	public void onInitializeClient() {
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			// No default: each key starts unbound and is set under Controls.
			KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + Spellbooks.MOD_ID + ".quick_slot_" + (i + 1), InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), CATEGORY
			));
		}
		ClientTickEvents.END_CLIENT_TICK.register(QuickSlotsClient::pollKeys);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "quick_slots"),
			(graphics, tracker) -> drawHud(graphics));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				ScreenEvents.afterBackground(screen).register((background, graphics, mouseX, mouseY, delta) ->
					drawFrames(graphics, (AbstractContainerScreenAccessor) inventory));
			}
		});
	}

	// A press is sent to the server, which uses the slot. Presses made while a screen is open are dropped.
	private static void pollKeys(final Minecraft client) {
		for (int i = 0; i < KEYS.length; i++) {
			while (KEYS[i].consumeClick()) {
				if (client.player != null && client.gui.screen() == null && !client.player.isSpectator()) {
					ClientPlayNetworking.send(new QuickSlotUsePayload(i));
				}
			}
		}
	}

	private static void drawHud(final GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.isSpectator()) {
			return;
		}
		QuickSlots slots = QuickSlots.of(client.player);
		boolean leftHanded = client.options.mainHand().get() == HumanoidArm.LEFT;
		int x = graphics.guiWidth() / 2 + 91 + (leftHanded ? HUD_GAP_LEFT_HANDED : HUD_GAP);
		int y = graphics.guiHeight() - 22;
		Font font = client.font;
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			int cell = x + i * CELL;
			graphics.fill(cell, y, cell + CELL - 1, y + 21, 0x80000000);
			graphics.outline(cell, y, CELL - 1, 21, 0xFF8B8B8B);
			ItemStack stack = slots.get(i);
			graphics.item(stack, cell + 2, y + 3);
			graphics.itemDecorations(font, stack, cell + 2, y + 3);
			if (!KEYS[i].isUnbound()) {
				String label = KEYS[i].getTranslatedKeyMessage().getString();
				graphics.text(font, label, cell + (CELL - 1 - font.width(label)) / 2, y - 10, 0xFFFFFFFF, true);
			}
		}
	}

	// The panel and frames behind the four menu slots, which stand just outside the inventory screen's own background.
	private static void drawFrames(final GuiGraphicsExtractor graphics, final AbstractContainerScreenAccessor screen) {
		int left = screen.stellaSpellsLeftPos() + QuickSlots.MENU_X - 5;
		int top = screen.stellaSpellsTopPos() + QuickSlots.MENU_Y - 5;
		int width = 18 + 8;
		int height = QuickSlots.COUNT * QuickSlots.MENU_STEP + 8;
		graphics.fill(left, top, left + width, top + height, 0xFFC6C6C6);
		graphics.outline(left, top, width, height, 0xFF373737);
		for (int i = 0; i < QuickSlots.COUNT; i++) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, left + 4, top + 4 + i * QuickSlots.MENU_STEP, 18, 18);
		}
	}
}
