package com.stella.equips.client;

import com.stella.equips.warning.LowHealthWarning;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

/** Plays the heartbeat and draws the red screen edge while health is low. */
final class LowHealthWarningClient {
	private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("mod-stella-equips-enchants", "low_health_vignette");
	private static final int RING_STEPS = 14;
	private static final float EDGE_FRACTION = 0.16F;
	private static final float MAX_ALPHA = 0.6F;
	private static final int RED = 0x00B01010;
	private static final int ALPHA_SHIFT = 24;
	private static final int ALPHA_MAX = 255;

	// Only the client thread touches these.
	private static boolean active;
	private static int ticksActive;
	private static float health;
	private static float maxHealth;

	private LowHealthWarningClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(LowHealthWarningClient::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, HUD_ID, LowHealthWarningClient::extract);
	}

	private static void tick(final Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean survival = minecraft.gameMode != null && minecraft.gameMode.getPlayerMode().isSurvival();

		if (player == null || !survival || !player.isAlive()) {
			active = false;
			return;
		}

		health = player.getHealth();
		maxHealth = player.getMaxHealth();

		if (!LowHealthWarning.active(health, maxHealth)) {
			active = false;
			return;
		}

		if (!active) {
			active = true;
			ticksActive = 0;
		}

		if (LowHealthWarning.beatDue(ticksActive)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F));
		}

		ticksActive++;
	}

	private static void extract(final GuiGraphicsExtractor graphics, final DeltaTracker delta) {
		if (!active) {
			return;
		}

		float strength = LowHealthWarning.vignette(health, maxHealth, ticksActive);

		if (strength <= 0.0F) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int thickness = Math.max(1, Math.round(Math.min(width, height) * EDGE_FRACTION / RING_STEPS));

		for (int step = 0; step < RING_STEPS; step++) {
			float fade = 1.0F - (float) step / RING_STEPS;
			int alpha = Math.round(strength * MAX_ALPHA * fade * fade * ALPHA_MAX);
			int color = (alpha << ALPHA_SHIFT) | RED;
			int inset = step * thickness;
			int inner = inset + thickness;
			graphics.fill(inset, inset, width - inset, inner, color);
			graphics.fill(inset, height - inner, width - inset, height - inset, color);
			graphics.fill(inset, inner, inner, height - inner, color);
			graphics.fill(width - inner, inner, width - inset, height - inner, color);
		}
	}
}
