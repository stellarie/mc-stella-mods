package com.stella.rpg;

import com.stella.rpg.api.BagShare;
import com.stella.rpg.bag.Bag;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The bag view share: another mod reads and writes a player's bag through vanilla types only. */
public final class BagShareGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void shareIsRegistered(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(BagShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the bag share is " + shared);
		ServerPlayer player = RpgTestSupport.player(helper);
		Container view = ((Function<Player, Container>) shared).apply(player);
		view.setItem(4, new ItemStack(Items.EMERALD, 7));
		ItemStack stored = Bag.of(player).get(4);
		RpgTestSupport.result("bagShare", "size=" + view.getContainerSize() + " stored=" + stored);
		helper.assertTrue(view.getContainerSize() == Bag.SIZE && stored.is(Items.EMERALD) && stored.getCount() == 7, "the share wrote " + stored);
		helper.succeed();
	}
}
