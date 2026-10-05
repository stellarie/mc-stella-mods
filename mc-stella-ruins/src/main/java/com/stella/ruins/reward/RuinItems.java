package com.stella.ruins.reward;

import com.stella.ruins.StellaRuins;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** The one door for items of this mod: it sets the id, registers the item, and adds it to the tab. */
public final class RuinItems {
	private RuinItems() {
	}

	public static <T extends Item> T register(final String id, final Function<Item.Properties, T> factory, final Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, StellaRuins.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		RuinsTab.add(item);
		return item;
	}
}
