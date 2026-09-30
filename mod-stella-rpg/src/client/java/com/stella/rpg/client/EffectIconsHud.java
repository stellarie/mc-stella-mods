package com.stella.rpg.client;

import com.stella.rpg.HudLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/** Draws status effects as small icons above the health bar. Vanilla's top-right icons are removed. */
@Environment(EnvType.CLIENT)
public final class EffectIconsHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("mod-stella-rpg", "effect_icons");
	private static final int BLINK_TICKS = 200;
	private static final int LEFT_OFFSET = 91;

	private EffectIconsHud() {
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, tracker) -> { });
		// Between armor and health: the height provider lifts the armor strip by the icon rows.
		HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, (graphics, tracker) -> draw(graphics));
		HudStatusBarHeightRegistry.addLeft(ID, EffectIconsHud::height);
	}

	private static List<MobEffectInstance> visible(final Minecraft minecraft) {
		List<MobEffectInstance> icons = new ArrayList<>();
		if (minecraft.player == null || (minecraft.gui.screen() != null && minecraft.gui.screen().showsActiveEffects())) {
			return icons;
		}
		for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
			if (effect.showIcon()) {
				icons.add(effect);
			}
		}
		icons.sort(Comparator.reverseOrder());
		return icons;
	}

	private static int height(final Player player) {
		return HudLayout.iconRows(visible(Minecraft.getInstance()).size(), HudLayout.ICONS_PER_ROW) * HudLayout.ICON_SIZE;
	}

	private static void draw(final GuiGraphicsExtractor graphics) {
		List<MobEffectInstance> icons = visible(Minecraft.getInstance());
		if (icons.isEmpty()) {
			return;
		}
		int perRow = HudLayout.ICONS_PER_ROW;
		int left = graphics.guiWidth() / 2 - LEFT_OFFSET;
		int rows = HudLayout.iconRows(icons.size(), perRow);
		int top = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID) - rows * HudLayout.ICON_SIZE;
		for (int i = 0; i < icons.size(); i++) {
			MobEffectInstance effect = icons.get(i);
			float alpha = !effect.isAmbient() && effect.endsWithin(BLINK_TICKS) ? HudLayout.blinkAlpha(effect.getDuration()) : 1.0f;
			int x = left + (i % perRow) * HudLayout.ICON_SIZE;
			int y = top + (rows - 1 - i / perRow) * HudLayout.ICON_SIZE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()), x, y, HudLayout.ICON_SIZE, HudLayout.ICON_SIZE, ARGB.white(alpha));
		}
	}
}
