package com.stella.tower;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Tower Whetstone: the next refine at an anvil cannot fail. A whetstone in the inventory is spent on a refine that would
 * have failed and turns it into a success.
 * <p>
 * The equips mod owns refining, and this mod does not depend on it. The ward is offered two ways: {@link #SHARE}, a
 * {@code Predicate<Player>} on the object share that spends one whetstone and says whether it did, and a wrapper that this
 * class puts round the refine station's roller when the equips mod is present. With that mod absent both do nothing.
 */
public final class RefineWard {
	public static final String SHARE = "mod-stella-tower:refine-ward";
	static final String STATION = "com.stella.equips.refine.RefineStation";

	private RefineWard() {
	}

	public static void register() {
		Predicate<Player> take = player -> player instanceof ServerPlayer server && take(server);
		FabricLoader.getInstance().getObjectShare().put(SHARE, take);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> install());
	}

	/** Spends one whetstone from the player's inventory; whether there was one. */
	public static boolean take(final ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(MerchantItems.TOWER_WHETSTONE)) {
				stack.shrink(1);
				player.getInventory().setChanged();
				return true;
			}
		}
		return false;
	}

	/**
	 * Wraps the refine station's roller so that a failed roll spends a whetstone to become a success. The station is reached by
	 * name; any failure leaves the station as it was.
	 */
	static boolean install() {
		try {
			Class<?> station = Class.forName(STATION);
			Class<?> roller = Class.forName(STATION + "$Roller");
			Method fair = station.getMethod("fairRoll", RandomSource.class, int.class);
			Method set = station.getMethod("setRoller", roller);
			Object ward = Proxy.newProxyInstance(roller.getClassLoader(), new Class<?>[] {roller}, (proxy, method, args) -> {
				if (!method.getName().equals("succeeds")) {
					return objectMethod(proxy, method, args);
				}
				boolean fairRoll = (Boolean) fair.invoke(null, args[1], args[2]);
				return fairRoll || args[0] instanceof ServerPlayer player && take(player);
			});
			set.invoke(null, ward);
			StellaTower.LOGGER.info("The Tower Whetstone guards the refine station");
			return true;
		} catch (ClassNotFoundException absent) {
			return false;
		} catch (ReflectiveOperationException | RuntimeException failed) {
			StellaTower.LOGGER.warn("The Tower Whetstone could not guard the refine station: {}", failed.toString());
			return false;
		}
	}

	private static Object objectMethod(final Object proxy, final Method method, final Object[] args) {
		return switch (method.getName()) {
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			default -> "TowerWhetstoneRoller";
		};
	}
}
