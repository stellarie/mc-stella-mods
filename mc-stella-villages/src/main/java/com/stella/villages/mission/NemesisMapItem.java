package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.saveddata.maps.MapId;

public final class NemesisMapItem extends MapItem {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "nemesis_map");
	private static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
	public static final NemesisMapItem ITEM = Registry.register(
		BuiltInRegistries.ITEM, KEY, new NemesisMapItem(new Item.Properties().stacksTo(1).setId(KEY))
	);

	private NemesisMapItem(final Properties properties) {
		super(properties);
	}

	// Literal, because the village mod ships no language file.
	@Override
	public net.minecraft.network.chat.Component getName(final ItemStack stack) {
		return net.minecraft.network.chat.Component.literal("Nemesis Map");
	}

	public static void register() {
		McStellaVillages.LOGGER.debug("Registered Nemesis map item {}", ID);
	}

	public static ItemStack create(final ServerLevel level, final BlockPos targetPosition) {
		ItemStack stack = new ItemStack(ITEM);
		MapItemSavedData savedData = MapItemSavedData.createFresh(
			targetPosition.getX(), targetPosition.getZ(), (byte) 0, true, true, level.dimension()
		);
		MapId mapId = level.getFreeMapId();
		level.setMapData(mapId, savedData.locked());
		stack.set(DataComponents.MAP_ID, mapId);
		MapItemSavedData.addTargetDecoration(stack, targetPosition, "nemesis", MapDecorationTypes.TARGET_POINT);
		CompoundTag customData = new CompoundTag();
		customData.put("target_position", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, targetPosition).getOrThrow());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
		return stack;
	}

	public static BlockPos targetPosition(final ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			throw new IllegalArgumentException("Nemesis map target is missing");
		}
		return customData.copyTag().read("target_position", BlockPos.CODEC)
			.orElseThrow(() -> new IllegalArgumentException("Nemesis map target is invalid"));
	}

	public static MapId mapId(final ItemStack stack) {
		return stack.get(DataComponents.MAP_ID);
	}
}
