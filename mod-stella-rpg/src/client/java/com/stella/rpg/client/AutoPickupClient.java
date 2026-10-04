package com.stella.rpg.client;

import com.stella.rpg.AutoPickup;
import com.stella.rpg.client.mixin.client.ContainerScreenPositionAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** The auto-pickup checkbox under the Inventory tab. The server holds the value and syncs it back. */
@Environment(EnvType.CLIENT)
public final class AutoPickupClient {
	// Right of the spells quick slot panel.
	private static final int X = 100;
	private static final int Y = 170;

	private AutoPickupClient() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen) || client.player == null
				|| !ClientPlayNetworking.canSend(AutoPickup.TogglePayload.PAYLOAD_TYPE)) {
				return;
			}
			ContainerScreenPositionAccessor position = (ContainerScreenPositionAccessor) screen;
			Checkbox box = Checkbox.builder(Component.translatable("screen.mod-stella-rpg.auto_pickup"), client.font)
				.pos(position.stellaRpgLeftPos() + X, position.stellaRpgTopPos() + Y)
				.selected(AutoPickup.isOn(client.player))
				.onValueChange((checkbox, on) -> ClientPlayNetworking.send(new AutoPickup.TogglePayload(on)))
				.build();
			Screens.getWidgets(screen).add(box);
			// The recipe book moves the screen after init.
			ScreenEvents.afterBackground(screen).register((background, graphics, mouseX, mouseY, delta) ->
				box.setPosition(position.stellaRpgLeftPos() + X, position.stellaRpgTopPos() + Y));
		});
	}
}
