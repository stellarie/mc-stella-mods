package com.stella.villages.patrol;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

/**
 * Emerald payment through the equips mod's purse, so emerald bags count. Without that mod it counts and takes
 * the loose emeralds in the inventory.
 */
final class Purse {
	static final String COUNT_SHARE = "mod-stella-equips-enchants:emerald-count";
	static final String SPEND_SHARE = "mod-stella-equips-enchants:emerald-spend";

	private Purse() {
	}

	@SuppressWarnings("unchecked")
	static int count(final Player player) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(COUNT_SHARE);
		if (shared instanceof Function<?, ?> function) {
			Integer value = ((Function<Object, Integer>) function).apply(player);
			if (value != null) {
				return value;
			}
		}
		return WitchShop.emeralds(player.getInventory());
	}

	/** Takes the whole price, or nothing when the player carries too few. */
	@SuppressWarnings("unchecked")
	static boolean spend(final Player player, final int price) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(SPEND_SHARE);
		if (shared instanceof BiFunction<?, ?, ?> function) {
			Boolean paid = ((BiFunction<Object, Integer, Boolean>) function).apply(player, price);
			return Boolean.TRUE.equals(paid);
		}
		if (WitchShop.emeralds(player.getInventory()) < price) {
			return false;
		}
		WitchShop.takeLoose(player.getInventory(), price);
		return true;
	}
}
