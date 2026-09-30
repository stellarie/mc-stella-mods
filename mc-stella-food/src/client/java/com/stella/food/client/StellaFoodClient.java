package com.stella.food.client;

import com.stella.food.CookingStationMenu;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class StellaFoodClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(CookingStationMenu.TYPE, CookingStationScreen::new);
	}
}
