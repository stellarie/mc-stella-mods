package com.stella.tower;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What the ghost merchant buys: raw ores and gems, paid in emeralds. A stack is sold whole at a rate of {@code emeralds} for every
 * {@code items}, rounded down; the items that do not make a whole emerald stay with the player.
 */
public final class OreSales {
	/** {@code emeralds} emeralds for every {@code items} items. */
	public record Rate(int emeralds, int items) {
	}

	/** What selling a stack does: the emeralds paid, and the items taken for them. */
	public record Quote(int emeralds, int taken) {
	}

	private static final Map<Item, Rate> RATES = new LinkedHashMap<>();

	static {
		rate(Items.COAL, 1, 64);
		rate(Items.COPPER_INGOT, 1, 32);
		rate(Items.RAW_COPPER, 1, 32);
		rate(Items.RAW_IRON, 1, 16);
		rate(Items.RAW_GOLD, 1, 12);
		rate(Items.REDSTONE, 1, 32);
		rate(Items.LAPIS_LAZULI, 1, 24);
		rate(Items.QUARTZ, 1, 24);
		rate(Items.AMETHYST_SHARD, 1, 16);
		rate(Items.DIAMOND, 2, 1);
		rate(Items.ANCIENT_DEBRIS, 6, 1);
		rate(Items.NETHERITE_SCRAP, 6, 1);
		// A raw ore block is nine of its item.
		rate(Items.RAW_COPPER_BLOCK, 9, 32);
		rate(Items.RAW_IRON_BLOCK, 9, 16);
		rate(Items.RAW_GOLD_BLOCK, 9, 12);
	}

	private OreSales() {
	}

	private static void rate(final Item item, final int emeralds, final int items) {
		RATES.put(item, new Rate(emeralds, items));
	}

	/** Every item the merchant buys, with its rate, in the order they are listed. */
	public static Map<Item, Rate> rates() {
		return java.util.Collections.unmodifiableMap(RATES);
	}

	public static @Nullable Rate rateOf(final ItemStack stack) {
		return stack.isEmpty() ? null : RATES.get(stack.getItem());
	}

	/** The sale of a whole stack, or null when it is not bought or is too few to make one emerald. */
	public static @Nullable Quote quote(final ItemStack stack) {
		Rate rate = rateOf(stack);
		if (rate == null) {
			return null;
		}
		long emeralds = (long) stack.getCount() * rate.emeralds() / rate.items();
		if (emeralds < 1) {
			return null;
		}
		// The fewest items that pay these emeralds: the rest stay with the player.
		long taken = (emeralds * rate.items() + rate.emeralds() - 1) / rate.emeralds();
		return new Quote((int) emeralds, (int) taken);
	}

	/**
	 * Sells the stack in a slot of the player's inventory to the merchant. Refused unless the merchant stands in the tower and
	 * the stack is bought, and large enough to make an emerald.
	 */
	public static boolean sell(final ServerPlayer player, final GhostMerchant merchant, final int inventorySlot) {
		if (!(merchant.level() instanceof ServerLevel level) || !level.dimension().equals(TowerTravel.TOWER)) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.tower_only"));
			return false;
		}
		if (inventorySlot < 0 || inventorySlot >= player.getInventory().getContainerSize()) {
			return false;
		}
		ItemStack stack = player.getInventory().getItem(inventorySlot);
		Rate rate = rateOf(stack);
		if (rate == null) {
			if (!stack.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.no_buy"));
			}
			return false;
		}
		Quote quote = quote(stack);
		if (quote == null) {
			int needed = (rate.items() + rate.emeralds() - 1) / rate.emeralds();
			player.sendOverlayMessage(Component.translatable("message.mod-stella-tower.merchant.too_little", needed));
			return false;
		}
		Component name = stack.getHoverName();
		stack.shrink(quote.taken());
		player.getInventory().setChanged();
		int left = quote.emeralds();
		while (left > 0) {
			ItemStack paid = new ItemStack(Items.EMERALD, Math.min(left, new ItemStack(Items.EMERALD).getMaxStackSize()));
			left -= paid.getCount();
			if (!player.getInventory().add(paid)) {
				level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), paid));
			}
		}
		level.playSound(null, merchant.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.mod-stella-tower.merchant.sold", quote.taken(), name, quote.emeralds()));
		StellaTower.LOGGER.debug("[TOWER] player={} sold {} x{} for {} emeralds", player.getScoreboardName(), name.getString(), quote.taken(), quote.emeralds());
		return true;
	}
}
