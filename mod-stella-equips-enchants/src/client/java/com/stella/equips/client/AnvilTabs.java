package com.stella.equips.client;

import com.stella.equips.mixin.client.AnvilScreenAccessor;
import com.stella.equips.mixin.client.EquipsContainerScreenAccessor;
import com.stella.equips.refine.OpenRefineTabPayload;
import com.stella.equips.refine.Refine;
import com.stella.equips.refine.RefineRequestPayload;
import com.stella.equips.refine.RefineStation;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Repair and Refine tabs above the anvil. The Refine tab covers the repair area with a refine panel. */
@Environment(EnvType.CLIENT)
public final class AnvilTabs {
	public static final int REPAIR = 0;
	public static final int REFINE = 1;
	private static final int SIZE = 28;
	private static final int STEP = 29;
	private static final int ACTIVE_FILL = 0xFFC6C6C6;
	private static final int IDLE_FILL = 0xFF8B8B8B;
	private static final int EDGE = 0xFF373737;
	private static final int TEXT = 0xFF404040;
	private static final int SHORT_TEXT = 0xFFA02020;
	private static final int BUTTON_FILL = 0xFFA8A8A8;
	private static final int BUTTON_HOVER = 0xFFE0E0E0;
	private static final int PANEL_LEFT = 50;
	private static final int PANEL_TOP = 16;
	private static final int PANEL_RIGHT = 170;
	private static final int PANEL_BOTTOM = 80;
	private static final int BUTTON_LEFT = 110;
	private static final int BUTTON_TOP = 62;
	private static final int BUTTON_WIDTH = 54;
	private static final int BUTTON_HEIGHT = 14;
	private static final Map<AnvilScreen, Integer> SELECTED = new WeakHashMap<>();
	private static boolean openRefineNext;

	private AnvilTabs() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(OpenRefineTabPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof AnvilScreen anvil) {
				SELECTED.put(anvil, REFINE);
			} else {
				openRefineNext = true;
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AnvilScreen anvil)) {
				return;
			}
			if (openRefineNext) {
				openRefineNext = false;
				SELECTED.put(anvil, REFINE);
			}
			EquipsContainerScreenAccessor position = (EquipsContainerScreenAccessor) anvil;
			ScreenEvents.afterExtract(screen).register((rendered, graphics, mouseX, mouseY, delta) -> {
				int left = position.stellaEquipsLeftPos();
				int top = position.stellaEquipsTopPos();
				int selected = SELECTED.getOrDefault(anvil, REPAIR);
				((AnvilScreenAccessor) anvil).stellaEquipsName().setVisible(selected == REPAIR);
				drawTabs(graphics, client.font, left, top, selected, mouseX, mouseY);
				if (selected == REFINE) {
					drawPanel(graphics, client, anvil, left, top, mouseX, mouseY);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> {
				int left = position.stellaEquipsLeftPos();
				int top = position.stellaEquipsTopPos();
				int tab = hitTab(left, top, event.x(), event.y());
				if (tab >= 0) {
					click(client);
					SELECTED.put(anvil, tab);
					return false;
				}
				if (SELECTED.getOrDefault(anvil, REPAIR) != REFINE || !inPanel(left, top, event.x(), event.y())) {
					return true;
				}
				if (inButton(left, top, event.x(), event.y()) && ready(client, anvil)) {
					click(client);
					ClientPlayNetworking.send(new RefineRequestPayload(anvil.getMenu().containerId));
				}
				return false;
			});
		});
	}

	public static int hitTab(final int left, final int top, final double mouseX, final double mouseY) {
		if (mouseY < top - SIZE || mouseY >= top) {
			return -1;
		}
		for (int tab = REPAIR; tab <= REFINE; tab++) {
			double x = left + tab * STEP;
			if (mouseX >= x && mouseX < x + SIZE) {
				return tab;
			}
		}
		return -1;
	}

	private static boolean inPanel(final int left, final int top, final double x, final double y) {
		return x >= left + PANEL_LEFT && x < left + PANEL_RIGHT && y >= top + PANEL_TOP && y < top + PANEL_BOTTOM;
	}

	private static boolean inButton(final int left, final int top, final double x, final double y) {
		return x >= left + BUTTON_LEFT && x < left + BUTTON_LEFT + BUTTON_WIDTH
			&& y >= top + BUTTON_TOP && y < top + BUTTON_TOP + BUTTON_HEIGHT;
	}

	private static void drawTabs(
		final GuiGraphicsExtractor graphics, final Font font, final int left, final int top, final int selected,
		final int mouseX, final int mouseY
	) {
		for (int tab = REPAIR; tab <= REFINE; tab++) {
			int x = left + tab * STEP;
			int y = top - SIZE;
			boolean active = tab == selected;
			graphics.fill(x, y, x + SIZE, top, active ? ACTIVE_FILL : IDLE_FILL);
			graphics.outline(x, y, SIZE, SIZE, EDGE);
			if (active) {
				graphics.fill(x + 1, top - 1, x + SIZE - 1, top, ACTIVE_FILL);
			}
			graphics.item(new ItemStack(tab == REPAIR ? Items.ANVIL : Items.AMETHYST_SHARD), x + 6, y + 6);
		}
		int hovered = hitTab(left, top, mouseX, mouseY);
		if (hovered >= 0) {
			graphics.setTooltipForNextFrame(font, Component.translatable(hovered == REPAIR
				? "screen.mod-stella-equips-enchants.tab.repair"
				: "screen.mod-stella-equips-enchants.tab.refine"), mouseX, mouseY);
		}
	}

	private static void drawPanel(
		final GuiGraphicsExtractor graphics, final Minecraft client, final AnvilScreen anvil, final int left, final int top,
		final int mouseX, final int mouseY
	) {
		graphics.fill(left + PANEL_LEFT, top + PANEL_TOP, left + PANEL_RIGHT, top + PANEL_BOTTOM, ACTIVE_FILL);
		Font font = client.font;
		int x = left + PANEL_LEFT + 4;
		int y = top + PANEL_TOP + 4;
		ItemStack piece = anvil.getMenu().getSlot(0).getItem();
		if (!Refine.canRefine(piece)) {
			graphics.text(font, text("screen.mod-stella-equips-enchants.refine.empty"), x, y, TEXT);
			return;
		}
		int level = Refine.level(piece);
		graphics.text(font, font.plainSubstrByWidth(piece.getHoverName().getString() + " +" + level, PANEL_RIGHT - PANEL_LEFT - 8), x, y, TEXT);
		if (level >= Refine.MAX_LEVEL) {
			graphics.text(font, text("screen.mod-stella-equips-enchants.refine.capped"), x, y + 12, TEXT);
			return;
		}
		int next = level + 1;
		int chance = Refine.grade(piece).chanceTo(next);
		int cost = Refine.cost(level);
		Item material = Refine.material(piece);
		int owned = RefineStation.count(client.player.getInventory(), piece);
		boolean enabled = owned >= cost;
		graphics.text(font, text("screen.mod-stella-equips-enchants.refine.next", next, chance), x, y + 12, TEXT);
		String costLine = text("screen.mod-stella-equips-enchants.refine.cost", cost,
			material.getName(material.getDefaultInstance()).getString(), owned);
		graphics.text(font, font.plainSubstrByWidth(costLine, PANEL_RIGHT - PANEL_LEFT - 8), x, y + 24, enabled ? TEXT : SHORT_TEXT);
		int bx = left + BUTTON_LEFT;
		int by = top + BUTTON_TOP;
		int fill = !enabled ? IDLE_FILL : inButton(left, top, mouseX, mouseY) ? BUTTON_HOVER : BUTTON_FILL;
		graphics.fill(bx, by, bx + BUTTON_WIDTH, by + BUTTON_HEIGHT, fill);
		graphics.outline(bx, by, BUTTON_WIDTH, BUTTON_HEIGHT, EDGE);
		String label = text("screen.mod-stella-equips-enchants.refine.button");
		graphics.text(font, label, bx + (BUTTON_WIDTH - font.width(label)) / 2, by + 3, TEXT);
	}

	private static String text(final String key, final Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static boolean ready(final Minecraft client, final AnvilScreen anvil) {
		ItemStack piece = anvil.getMenu().getSlot(0).getItem();
		return Refine.canRefine(piece) && Refine.level(piece) < Refine.MAX_LEVEL
			&& RefineStation.count(client.player.getInventory(), piece) >= Refine.cost(Refine.level(piece));
	}

	private static void click(final Minecraft client) {
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
