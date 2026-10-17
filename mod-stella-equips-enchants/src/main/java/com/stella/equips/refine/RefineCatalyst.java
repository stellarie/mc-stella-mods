package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import com.stella.equips.tooltip.DescriptionLines;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Refine Catalyst. Deep ore drops it. A refine from {@link #MIN_LEVEL} up spends one when the roll fails, and that
 * failure keeps the piece's level. A successful roll, a whetstone included, spends nothing.
 */
public final class RefineCatalyst {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(StellaEquips.MOD_ID, "refine_catalyst");
	/** The share of a deep ore break that drops one. */
	public static final double DROP_CHANCE = 0.05;
	/** A piece at this level or higher can use a catalyst. */
	public static final int MIN_LEVEL = 7;
	public static final Set<Block> SOURCES = Set.of(Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.ANCIENT_DEBRIS);
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID))
			.component(DataComponents.LORE, new ItemLore(List.of(
				DescriptionLines.requires(Component.translatable("item." + StellaEquips.MOD_ID + ".refine_catalyst.requires").withStyle(ChatFormatting.WHITE)),
				DescriptionLines.desc(Component.translatable("item." + StellaEquips.MOD_ID + ".refine_catalyst.desc").withStyle(ChatFormatting.GRAY))))))
	);

	/** Whether one break drops a catalyst, given the chance. The test seam can force either side. */
	public interface DropRoller {
		boolean drops(ServerPlayer player, RandomSource random, double chance);
	}

	private static final DropRoller FAIR = (player, random, chance) -> random.nextDouble() < chance;
	private static volatile DropRoller dropRoller = FAIR;

	private RefineCatalyst() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(RefineCatalyst::afterBlockBreak);
	}

	public static void setDropRoller(final DropRoller newRoller) {
		dropRoller = newRoller == null ? FAIR : newRoller;
	}

	/** Spends one catalyst from the inventory; whether there was one. */
	public static boolean take(final Inventory inventory) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(ITEM)) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	private static void afterBlockBreak(
		final Level world, final Player player, final BlockPos pos, final BlockState state, final BlockEntity blockEntity
	) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer breaker) || breaker.isCreative()
			|| !SOURCES.contains(state.getBlock())) {
			return;
		}
		ItemStack tool = breaker.getMainHandItem();
		// A break that gives no ore drop gives no catalyst either.
		if (!tool.isCorrectToolForDrops(state) || hasSilkTouch(level, tool)) {
			return;
		}
		if (dropRoller.drops(breaker, breaker.getRandom(), DROP_CHANCE)) {
			Block.popResource(level, pos, new ItemStack(ITEM));
		}
	}

	private static boolean hasSilkTouch(final ServerLevel level, final ItemStack tool) {
		return EnchantmentHelper.getItemEnchantmentLevel(
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
	}
}
