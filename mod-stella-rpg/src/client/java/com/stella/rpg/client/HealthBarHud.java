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

/** Replaces the heart row with one bar. The vanilla element only runs when the player can be hurt. */
@Environment(EnvType.CLIENT)
public final class HealthBarHud {
	private static final int ROW_HEIGHT = 10;
	private static final int LEFT_OFFSET = 91;
	private static final int BACKGROUND = 0xFF1B1B1B;
	private static final int BORDER = 0xFF000000;
	private static final int ABSORPTION = 0xFFFFD83A;
	private static final int TEXT = 0xFFFFFFFF;

	private HealthBarHud() {
	}

	public static void register() {
		// One fixed row keeps the armor strip above the bar at any max health.
		HudStatusBarHeightRegistry.addLeft(VanillaHudElements.HEALTH_BAR, player -> ROW_HEIGHT);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (graphics, tracker) -> draw(graphics));
		// Armor moves into the Def line and breath is the blue bar, so neither vanilla row draws or takes height.
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> (graphics, tracker) -> { });
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, vanilla -> (graphics, tracker) -> { });
		HudStatusBarHeightRegistry.addLeft(VanillaHudElements.ARMOR_BAR, player -> 0);
		HudStatusBarHeightRegistry.addRight(VanillaHudElements.AIR_BAR, player -> 0);
	}

	private static void draw(final GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(minecraft.getCameraEntity() instanceof Player player)) {
			return;
		}
		int x = graphics.guiWidth() / 2 - LEFT_OFFSET;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR) - ROW_HEIGHT;
		float health = player.getHealth();
		float max = player.getMaxHealth();

		graphics.fill(x, y, x + HudLayout.BAR_WIDTH, y + HudLayout.BAR_HEIGHT, BACKGROUND);
		int color = HudLayout.fillColor(player.hasEffect(MobEffects.WITHER), player.hasEffect(MobEffects.POISON), player.isFullyFrozen());
		graphics.fill(x, y, x + HudLayout.fillWidth(health, max, HudLayout.BAR_WIDTH), y + HudLayout.BAR_HEIGHT, color);
		graphics.outline(x, y, HudLayout.BAR_WIDTH, HudLayout.BAR_HEIGHT, BORDER);
		int absorption = HudLayout.absorptionWidth(player.getAbsorptionAmount(), max, HudLayout.BAR_WIDTH);
		if (absorption > 0) {
			graphics.fill(x, y, x + absorption, y + HudLayout.ABSORPTION_HEIGHT, ABSORPTION);
		}

		drawAir(graphics, player, x, y);

		Font font = minecraft.font;
		String text = HudLayout.healthText(health, max);
		graphics.text(font, text, x + (HudLayout.BAR_WIDTH - font.width(text)) / 2, y + 1, TEXT, true);
	}

	private static void drawAir(final GuiGraphicsExtractor graphics, final Player player, final int x, final int healthTop) {
		int air = player.getAirSupply();
		int maxAir = player.getMaxAirSupply();
		if (!HudLayout.airVisible(air, maxAir)) {
			return;
		}
		int top = HudLayout.airBarTop(healthTop);
		graphics.fill(x, top, x + HudLayout.BAR_WIDTH, top + HudLayout.AIR_BAR_HEIGHT, BACKGROUND);
		graphics.fill(x, top, x + HudLayout.airFillWidth(air, maxAir, HudLayout.BAR_WIDTH), top + HudLayout.AIR_BAR_HEIGHT, HudLayout.FILL_AIR);
		graphics.outline(x, top, HudLayout.BAR_WIDTH, HudLayout.AIR_BAR_HEIGHT, BORDER);
	}
}
