package com.stella.rpg.client;

import com.stella.rpg.client.mixin.client.ContainerScreenPositionAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class StatsTabs {
	public static final int INVENTORY = 0;
	public static final int STATS = 1;

	private static final int SIZE = 28;
	private static final int STEP = 29;
	private static final int ACTIVE_FILL = 0xFFC6C6C6;
	private static final int IDLE_FILL = 0xFF8B8B8B;
	private static final int EDGE = 0xFF373737;

	private StatsTabs() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen inventory)) {
				return;
			}
			ContainerScreenPositionAccessor position = (ContainerScreenPositionAccessor) inventory;
			ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, delta) -> {
				if (ClientStats.has()) {
					draw(graphics, client.font, position.stellaRpgLeftPos(), position.stellaRpgTopPos(), INVENTORY, mouseX, mouseY);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> {
				if (!ClientStats.has()) {
					return true;
				}
				int tab = hit(position.stellaRpgLeftPos(), position.stellaRpgTopPos(), event.x(), event.y());
				if (tab == STATS) {
					openStats(client);
				}
				return tab < 0;
			});
		});
	}

	public static int hit(final int left, final int top, final double mouseX, final double mouseY) {
		if (mouseY < top - SIZE || mouseY >= top) {
			return -1;
		}
		for (int tab = INVENTORY; tab <= STATS; tab++) {
			double x = left + tab * STEP;
			if (mouseX >= x && mouseX < x + SIZE) {
				return tab;
			}
		}
		return -1;
	}

	public static void draw(
		final GuiGraphicsExtractor graphics,
		final Font font,
		final int left,
		final int top,
		final int selected,
		final int mouseX,
		final int mouseY
	) {
		for (int tab = INVENTORY; tab <= STATS; tab++) {
			int x = left + tab * STEP;
			int y = top - SIZE;
			boolean active = tab == selected;
			graphics.fill(x, y, x + SIZE, top, active ? ACTIVE_FILL : IDLE_FILL);
			graphics.outline(x, y, SIZE, SIZE, EDGE);
			if (active) {
				graphics.fill(x + 1, top - 1, x + SIZE - 1, top, ACTIVE_FILL);
			}
			graphics.item(icon(tab), x + 6, y + 6);
		}
		int hovered = hit(left, top, mouseX, mouseY);
		if (hovered >= 0) {
			graphics.setTooltipForNextFrame(font, Component.translatable(hovered == INVENTORY
				? "screen.mod-stella-rpg.tab.inventory"
				: "screen.mod-stella-rpg.tab.stats"), mouseX, mouseY);
		}
	}

	public static void click(final Minecraft client) {
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	public static void openStats(final Minecraft client) {
		click(client);
		client.player.closeContainer();
		client.gui.setScreen(new StatsScreen());
	}

	public static void openInventory(final Minecraft client) {
		click(client);
		client.gui.setScreen(new InventoryScreen(client.player));
	}

	private static ItemStack icon(final int tab) {
		return tab == INVENTORY ? new ItemStack(Items.CHEST) : new ItemStack(Items.IRON_SWORD);
	}
}
