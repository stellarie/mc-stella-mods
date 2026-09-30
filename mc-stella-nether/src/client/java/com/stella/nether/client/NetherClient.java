package com.stella.nether.client;

import com.stella.nether.StellaNether;
import net.fabricmc.api.ClientModInitializer;

/** The raid's client side: the Blaze Sovereign's blue tint, applied by its renderer mixins, and the portal's window. */
public final class NetherClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The portal's choice opens as a window; the server sends it only to a client with the mod.
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.stella.nether.PortalWindow.Open.TYPE,
			(payload, context) -> context.client().setScreenAndShow(new PortalChoiceScreen(payload)));
		com.stella.nether.BehaviorLog.info("[NETHER] client ready");
	}
}
