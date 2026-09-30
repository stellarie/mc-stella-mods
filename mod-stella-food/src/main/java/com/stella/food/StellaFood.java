package com.stella.food;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StellaFood implements ModInitializer {
	public static final String MOD_ID = "mod-stella-food";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final DataComponentType<Boolean> COOKED_VARIANT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		id("cooked_variant"),
		DataComponentType.<Boolean>builder()
			.persistent(Codec.BOOL)
			.networkSynchronized(ByteBufCodecs.BOOL)
			.build()
	);

	public static final DataComponentType<Seasoning> SEASONING = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		id("seasoning"),
		DataComponentType.<Seasoning>builder()
			.persistent(Seasoning.CODEC)
			.networkSynchronized(Seasoning.STREAM_CODEC)
			.build()
	);

	@Override
	public void onInitialize() {
		FoodEffects.register();
		FoodItems.register();
		CookingStationMenu.register();
		CookingStationBlock.register();
		LOGGER.info("{} initialized", MOD_ID);
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
