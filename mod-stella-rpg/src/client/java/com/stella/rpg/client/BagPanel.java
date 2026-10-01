package com.stella.rpg.client;

import com.stella.rpg.bag.Bag;
import com.stella.rpg.client.mixin.client.ContainerScreenPositionAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Draws the bag panel and its slot frames under the inventory panel, in the vanilla palette. */
@Environment(EnvType.CLIENT)
public final class BagPanel {
	private static final int WIDTH = 176;
	private static final int PANEL = 0xFFC6C6C6;
	private static final int EDGE = 0xFF373737;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int SLOT = 0xFF8B8B8B;

	private BagPanel() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				ContainerScreenPositionAccessor position = (ContainerScreenPositionAccessor) inventory;
				ScreenEvents.afterBackground(screen).register((background, graphics, mouseX, mouseY, delta) -> {
					if (client.player != null && !client.player.hasInfiniteMaterials()) {
						draw(graphics, position.stellaRpgLeftPos(), position.stellaRpgTopPos());
					}
				});
			}
		});
	}

	private static void draw(final GuiGraphicsExtractor graphics, final int left, final int top) {
		int y = top + Bag.PANEL_TOP;
		graphics.fill(left, y, left + WIDTH, top + Bag.PANEL_BOTTOM, PANEL);
		graphics.outline(left, y, WIDTH, Bag.PANEL_BOTTOM - Bag.PANEL_TOP, EDGE);
		for (int row = 0; row < Bag.ROWS; row++) {
			for (int column = 0; column < 9; column++) {
				int x = left + Bag.SLOT_X + column * 18;
				int slotY = top + Bag.SLOT_Y + row * 18;
				graphics.fill(x - 1, slotY - 1, x + 17, slotY + 17, EDGE);
				graphics.fill(x, slotY, x + 17, slotY + 17, LIGHT);
				graphics.fill(x, slotY, x + 16, slotY + 16, SLOT);
			}
		}
	}
}
