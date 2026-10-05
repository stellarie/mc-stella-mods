package com.stella.ruins.site.wayshrine;

import com.stella.ruins.reward.RuinItems;
import net.minecraft.world.item.Item;

/** The three materials of the wayshrine. Only its vault and its mobs produce them. */
public final class WayshrineItems {
	public static final Item SHRINE_SHARD = RuinItems.register("shrine_shard", Item::new, new Item.Properties());
	public static final Item PILGRIM_ASH = RuinItems.register("pilgrim_ash", Item::new, new Item.Properties());
	public static final Item WISP_LIGHT = RuinItems.register("wisp_light", Item::new, new Item.Properties());

	private WayshrineItems() {
	}

	/** Loads the class, which registers the items. */
	public static void register() {
	}
}
