package com.stella.spells;

import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mana Crystal. A player breaking amethyst or lapis can find one. A crystal crafted with paper and a spell's
 * material turns out four scrolls of that spell instead of two; those recipes are data, one per scroll.
 */
public final class ManaCrystal {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "mana_crystal");
	/** The share of a break of a source block that drops one. */
	public static final double DROP_CHANCE = 0.10;
	public static final Set<Block> SOURCES = Set.of(Blocks.AMETHYST_CLUSTER, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
	public static final Item ITEM = Registry.register(
		BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID))
			.component(DataComponents.LORE, Spellbooks.lore(Spellbooks.block("mana_crystal", ChatFormatting.AQUA))))
	);

	/** Whether one break drops a crystal, given the chance. The test seam can force either side. */
	public interface DropRoller {
		boolean drops(ServerPlayer player, RandomSource random, double chance);
	}

	private static final DropRoller FAIR = (player, random, chance) -> random.nextDouble() < chance;
	private static volatile DropRoller dropRoller = FAIR;

	private ManaCrystal() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register(ManaCrystal::afterBlockBreak);
	}

	public static void setDropRoller(final DropRoller newRoller) {
		dropRoller = newRoller == null ? FAIR : newRoller;
	}

	private static void afterBlockBreak(
		final Level world, final Player player, final BlockPos pos, final BlockState state, final BlockEntity blockEntity
	) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer breaker) || breaker.isCreative()
			|| !SOURCES.contains(state.getBlock())) {
			return;
		}
		ItemStack tool = breaker.getMainHandItem();
		// A break that gives no ore drop gives no crystal either.
		if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state) || hasSilkTouch(level, tool)) {
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
