package com.stella.nether.client;

import com.stella.nether.BlazeGuardian;
import com.stella.nether.GuardianWindow;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.BlazeRenderer;

/** The raid's client side: the Blaze Sovereign's blue tint and the Blaze Guardian's green one, and the guardian's window. */
public final class NetherClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The guardian is a blaze to the eye; the tint and the scale come from the mixin and the entity's attribute.
		EntityRendererRegistry.register(BlazeGuardian.TYPE, BlazeRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(GuardianWindow.Open.TYPE,
			(payload, context) -> context.client().setScreenAndShow(new GuardianScreen(payload)));
		com.stella.nether.BehaviorLog.info("[NETHER] client ready");
	}
}
