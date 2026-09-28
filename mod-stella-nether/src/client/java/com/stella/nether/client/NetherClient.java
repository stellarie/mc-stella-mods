package com.stella.nether.client;

import com.stella.nether.StellaNether;
import net.fabricmc.api.ClientModInitializer;

/** The raid's client side: the Blaze Sovereign's blue tint, applied by its renderer mixins. */
public final class NetherClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		StellaNether.LOGGER.debug("[NETHER] client ready");
	}
}
