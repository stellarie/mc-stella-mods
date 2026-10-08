package com.stella.rpg.client;

import com.stella.rpg.SecondHotbar;
import com.stella.rpg.client.mixin.client.ContainerScreenPositionAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;

/**
 * The second hotbar's way in: one toggle at the right end of the inventory screen's hotbar row. The rows
 * exchange in place, so the vanilla hotbar draws the selected one and the HUD carries a single row. A server
 * without this mod leaves the toggle out, because the rows come from the server attachment.
 */
@Environment(EnvType.CLIENT)
public final class SecondHotbarClient {
	private static final int WIDTH = 24;
	private static final int HEIGHT = 20;
	private static final int GAP = 4;
	/** A vanilla slot is 18 pixels wide. */
	private static final int SLOT = 18;
	private static final int FILL = 0xFF6C6C6C;
	private static final int FILL_HOVER = 0xFF8B8B8B;
	private static final int EDGE = 0xFF000000;
	private static final int TEXT = 0xFFFFFFFF;

	private SecondHotbarClient() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, delta) -> draw(graphics, screen, mouseX, mouseY));
				ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> !press(clicked, event.x(), event.y()));
			}
		});
	}

	/** A server with this mod, and an inventory to read the rows from. */
	public static boolean active() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && ClientPlayNetworking.canSend(SecondHotbar.SwapPayload.TYPE);
	}

	/** The toggle's own box, or null: at the right end of the hotbar row, wherever the screen places that row. */
	private static int[] box(final Screen screen) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(screen instanceof AbstractContainerScreen<?> container)
			|| !(container instanceof ContainerScreenPositionAccessor position) || !active()) {
			return null;
		}
		Slot first = null;
		for (Slot slot : container.getMenu().slots) {
			if (slot.container == client.player.getInventory() && slot.getContainerSlot() < SecondHotbar.SIZE) {
				first = slot;
				break;
			}
		}
		if (first == null) {
			return null;
		}
		return new int[] {
			position.stellaRpgLeftPos() + first.x + SecondHotbar.SIZE * SLOT + GAP,
			position.stellaRpgTopPos() + first.y - 1
		};
	}

	private static void draw(final GuiGraphicsExtractor graphics, final Screen screen, final int mouseX, final int mouseY) {
		int[] box = box(screen);
		if (box == null) {
			return;
		}
		int x = box[0];
		int y = box[1];
		boolean hovered = mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + HEIGHT;
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, hovered ? FILL_HOVER : FILL);
		graphics.outline(x, y, WIDTH, HEIGHT, EDGE);
		Font font = Minecraft.getInstance().font;
		Component label = Component.translatable("screen.mod-stella-rpg.second_hotbar");
		graphics.text(font, label, x + (WIDTH - font.width(label)) / 2, y + (HEIGHT - font.lineHeight) / 2 + 1, TEXT, false);
		if (hovered) {
			graphics.setTooltipForNextFrame(font, Component.translatable("screen.mod-stella-rpg.second_hotbar.tooltip"), mouseX, mouseY);
		}
	}

	/** A click inside the toggle asks the server to exchange the rows: true when the click was taken. */
	private static boolean press(final Screen screen, final double mouseX, final double mouseY) {
		int[] box = box(screen);
		if (box == null || mouseX < box[0] || mouseX >= box[0] + WIDTH || mouseY < box[1] || mouseY >= box[1] + HEIGHT) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		if (active()) {
			ClientPlayNetworking.send(new SecondHotbar.SwapPayload());
		}
		return true;
	}
}
