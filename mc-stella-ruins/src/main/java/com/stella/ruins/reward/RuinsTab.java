package com.stella.ruins.reward;

import com.stella.ruins.StellaRuins;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * The creative tab of the mod. Every ruins item joins it through {@link #add}, in the order of registration.
 */
public final class RuinsTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, StellaRuins.id("ruins"));

	private static final List<ItemLike> ITEMS = new ArrayList<>();

	private RuinsTab() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mod-stella-ruins"))
			.icon(() -> new ItemStack(ITEMS.isEmpty() ? Items.NETHER_STAR : ITEMS.getFirst()))
			.displayItems((parameters, output) -> ITEMS.forEach(output::accept))
			.build());
	}

	public static void add(final ItemLike item) {
		if (!ITEMS.contains(item)) {
			ITEMS.add(item);
		}
	}

	public static List<ItemLike> items() {
		return List.copyOf(ITEMS);
	}
}
