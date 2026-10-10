package com.stella.rpg.client;

import net.minecraft.client.Minecraft;
import com.stella.rpg.DrawSpeed;
import com.stella.rpg.net.StatsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class StellaRpgClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(StatsSyncPayload.TYPE, (payload, context) -> ClientStats.accept(payload));
		// The client has no stat sheet, so its own draw reads the payload the server syncs.
		DrawSpeed.setClientSource(entity -> entity == Minecraft.getInstance().player && ClientStats.has()
			? DrawSpeed.reduction(ClientStats.get()) : 0.0);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientStats.clear());
		StatsTabs.register();
		HealthBarHud.register();
		FoodBarHud.register();
		EffectIconsHud.register();
		WeaponRollTooltip.register();
		WeaponSlotClient.register();
		SecondHotbarClient.register();
		AutoPickupClient.register();
	}
}
