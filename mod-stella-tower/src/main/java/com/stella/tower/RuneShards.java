package com.stella.tower;

import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Rune shards: a player who breaks a deepslate ore, or ancient debris, may find one. Nine shards of one element craft a rune
 * ({@link RuneShardRecipe}); only a player's break rolls, and a silk touch break never does.
 */
public final class RuneShards {
	public static final double DROP_CHANCE = 0.04;
	/** The shards that make a rune, or, with a rune, the shards that raise it. */
	public static final int SHARDS_PER_RUNE = 9;
	public static final int UPGRADE_SHARDS = SHARDS_PER_RUNE - 1;
	/** The floor a rune made of shards starts on, and the floors a rune gains from shards. */
	public static final int FLOOR_STEP = 10;
	public static final Identifier RECIPE_ID = StellaTower.id("rune_shards");

	private static final List<String> ORE_TAGS = List.of("coal_ores", "iron_ores", "copper_ores", "gold_ores", "redstone_ores", "lapis_ores",
		"diamond_ores", "emerald_ores");
	private static final List<TagKey<Block>> ORES = ORE_TAGS.stream()
		.map(path -> TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path))).toList();

	private static volatile @Nullable Double pinnedRoll;

	private RuneShards() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RECIPE_ID, RuneShardRecipe.SERIALIZER);
		LootTableEvents.MODIFY_DROPS.register(RuneShards::onDrops);
	}

	/** The test seam: pins the roll a break is made against; null restores chance. */
	public static void pinRoll(final @Nullable Double roll) {
		pinnedRoll = roll;
	}

	public static ItemStack shard(final TowerElement element) {
		ItemStack stack = new ItemStack(TowerItems.RUNE_SHARD);
		stack.set(TowerItems.SHARD, element);
		return stack;
	}

	/** The element a shard holds, or null for any other stack. */
	public static @Nullable TowerElement elementOf(final ItemStack stack) {
		return stack.is(TowerItems.RUNE_SHARD) ? stack.get(TowerItems.SHARD) : null;
	}

	/** Whether breaking this block may leave a shard: a deepslate ore of the vanilla ores, or ancient debris. */
	public static boolean isShardOre(final BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return true;
		}
		if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("deepslate")) {
			return false;
		}
		for (TagKey<Block> ore : ORES) {
			if (state.is(ore)) {
				return true;
			}
		}
		return false;
	}

	private static void onDrops(final Holder<LootTable> table, final LootContext context, final List<ItemStack> drops) {
		if (table.value().getParamSet() != LootContextParamSets.BLOCK) {
			return;
		}
		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		if (state == null || !isShardOre(state) || !(context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer)) {
			return;
		}
		ItemInstance tool = context.getOptional(LootContextParams.TOOL);
		Holder<Enchantment> silk = context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
		if (tool != null && EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0) {
			return;
		}
		RandomSource random = context.getRandom();
		Double pin = pinnedRoll;
		if ((pin != null ? pin : random.nextDouble()) < DROP_CHANCE) {
			drops.add(shard(TowerElement.values()[random.nextInt(TowerElement.values().length)]));
		}
	}
}
