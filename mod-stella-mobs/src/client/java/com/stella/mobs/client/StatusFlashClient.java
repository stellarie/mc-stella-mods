package com.stella.mobs.client;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.flash.StatusFlash;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/** Draws a soft orange edge ring while the player burns and a bluish-white one while the player is frozen. */
final class StatusFlashClient {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "status_flash");
	private static final int RING_STEPS = 20;
	private static final float EDGE_FRACTION = 0.18F;

	// Only the client thread touches this.
	private static int ticks;

	private StatusFlashClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ticks++);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, HUD_ID, StatusFlashClient::extract);
	}

	private static void extract(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null || player.isSpectator()) {
			return;
		}

		float time = ticks + delta.getGameTimeDeltaPartialTick(false);
		drawRing(graphics, StatusFlash.ORANGE, StatusFlash.burnAlpha(player, time));
		drawRing(graphics, StatusFlash.FROST, StatusFlash.chillAlpha(player));
	}

	private static void drawRing(final GuiGraphicsExtractor graphics, final int rgb, final float peak) {
		if (peak <= 0.0F) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int thickness = Math.max(1, Math.round(Math.min(width, height) * EDGE_FRACTION / RING_STEPS));

		for (int step = 0; step < RING_STEPS; step++) {
			int color = StatusFlash.ringColor(rgb, peak, step, RING_STEPS);
			int inset = step * thickness;
			int inner = inset + thickness;
			graphics.fill(inset, inset, width - inset, inner, color);
			graphics.fill(inset, height - inner, width - inset, height - inset, color);
			graphics.fill(inset, inner, inner, height - inner, color);
			graphics.fill(width - inner, inner, width - inset, height - inner, color);
		}
	}
}
