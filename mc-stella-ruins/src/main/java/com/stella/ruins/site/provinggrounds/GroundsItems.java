package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.reward.RuinItems;
import net.minecraft.world.item.Item;

/** The three materials of the proving grounds. Only its vault and its mobs produce them. */
public final class GroundsItems {
	public static final Item CHAMPION_TOKEN = RuinItems.register("champion_token", Item::new, new Item.Properties());
	public static final Item NET_FIBER = RuinItems.register("net_fiber", Item::new, new Item.Properties());
	public static final Item HOUND_FANG = RuinItems.register("hound_fang", Item::new, new Item.Properties());

	private GroundsItems() {
	}

	/** Loads the class, which registers the items. */
	public static void register() {
	}
}
