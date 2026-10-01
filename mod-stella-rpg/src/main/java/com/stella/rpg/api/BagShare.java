package com.stella.rpg.api;

import com.stella.rpg.bag.Bag;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

/**
 * Publishes a player's bag under {@code mod-stella-rpg:bag-view} as a {@code Function<Player, Container>}.
 * A reader needs only vanilla types. A write through {@code setItem} lands in the bag.
 */
public final class BagShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-rpg:bag-view";

	private BagShare() {
	}

	public static void publish() {
		Function<Player, Container> view = Bag::view;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, view);
	}
}
