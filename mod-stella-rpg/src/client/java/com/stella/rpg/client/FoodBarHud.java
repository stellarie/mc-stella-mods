package com.stella.rpg.client;

import com.stella.rpg.HudLayout;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/** Replaces the drumstick row with one bar, mirrored on the right of the hotbar. */
@Environment(EnvType.CLIENT)
public final class FoodBarHud {
	private static final int ROW_HEIGHT = 10;
	private static final int RIGHT_OFFSET = 91;
	private static final int BACKGROUND = 0xFF1B1B1B;
	private static final int BORDER = 0xFF000000;
	private static final int SATURATION = 0xFFFFD83A;
	private static final int TEXT = 0xFFFFFFFF;

	private FoodBarHud() {
	}

	public static void register() {
		HudStatusBarHeightRegistry.addRight(VanillaHudElements.FOOD_BAR, player -> ROW_HEIGHT);
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> (graphics, tracker) -> draw(graphics));
	}

	private static void draw(final GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.getCameraEntity() instanceof Player player)) {
			return;
		}
		int x = graphics.guiWidth() / 2 + RIGHT_OFFSET - HudLayout.BAR_WIDTH;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR) - ROW_HEIGHT;
		FoodData food = player.getFoodData();
		int max = HudLayout.MAX_FOOD;

		graphics.fill(x, y, x + HudLayout.BAR_WIDTH, y + HudLayout.BAR_HEIGHT, BACKGROUND);
		int color = player.hasEffect(MobEffects.HUNGER) ? HudLayout.FILL_HUNGER : HudLayout.FILL_FOOD;
		graphics.fill(x, y, x + HudLayout.fillWidth(food.getFoodLevel(), max, HudLayout.BAR_WIDTH), y + HudLayout.BAR_HEIGHT, color);
		graphics.outline(x, y, HudLayout.BAR_WIDTH, HudLayout.BAR_HEIGHT, BORDER);
		int saturation = HudLayout.absorptionWidth(food.getSaturationLevel(), max, HudLayout.BAR_WIDTH);
		if (saturation > 0) {
			graphics.fill(x, y, x + saturation, y + HudLayout.ABSORPTION_HEIGHT, SATURATION);
		}

		Font font = minecraft.font;
		String text = HudLayout.foodText(food.getFoodLevel());
		graphics.text(font, text, x + (HudLayout.BAR_WIDTH - font.width(text)) / 2, y + 1, TEXT, true);
	}
}
