package com.stella.equips.client;

import com.stella.equips.readout.ReadoutPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class ReadoutClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(
			ReadoutPayload.TYPE,
			(payload, context) -> ReadoutPlateState.set(payload.isClear() ? null : payload)
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ReadoutPlateState.set(null));
		LevelRenderEvents.COLLECT_SUBMITS.register(ReadoutPlateRenderer::submit);
		AnvilTabs.register();
	}
}
