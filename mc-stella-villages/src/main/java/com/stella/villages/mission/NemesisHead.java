package com.stella.villages.mission;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

public final class NemesisHead {
	private static final String TYPE_KEY = "mc_stella_villages_nemesis_type";
	private static final Map<Identifier, Item> VANILLA_HEADS = Map.of(
		Identifier.parse("minecraft:zombie"), Items.ZOMBIE_HEAD,
		Identifier.parse("minecraft:skeleton"), Items.SKELETON_SKULL,
		Identifier.parse("minecraft:creeper"), Items.CREEPER_HEAD,
		Identifier.parse("minecraft:wither_skeleton"), Items.WITHER_SKELETON_SKULL,
		Identifier.parse("minecraft:piglin"), Items.PIGLIN_HEAD
	);
	private static final Set<Item> TRADEABLE_VANILLA_HEADS = Set.of(
		Items.ZOMBIE_HEAD, Items.SKELETON_SKULL, Items.CREEPER_HEAD,
		Items.WITHER_SKELETON_SKULL, Items.PIGLIN_HEAD, Items.DRAGON_HEAD
	);

	private NemesisHead() {
	}

	public static Item itemFor(final Identifier type) {
		return VANILLA_HEADS.getOrDefault(type, Items.PLAYER_HEAD);
	}

	public static ItemStack create(final Identifier type) {
		ItemStack head = new ItemStack(itemFor(type));
		CompoundTag data = new CompoundTag();
		data.putString(TYPE_KEY, type.toString());
		head.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		head.set(DataComponents.ITEM_NAME, Component.literal(
			BuiltInRegistries.ENTITY_TYPE.getValue(type).getDescription().getString() + " Head"
		));
		return head;
	}

	static boolean isTradeableHead(final ItemStack stack) {
		if (TRADEABLE_VANILLA_HEADS.contains(stack.getItem())) {
			return true;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.PLAYER_HEAD) && data != null
			&& data.copyTag().getString(TYPE_KEY).isPresent();
	}

	static void trade(final ServerLevel level, final ServerPlayer player, final ItemStack held) {
		held.shrink(1);
		List<Holder<Enchantment>> pool = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.listElements().map(holder -> (Holder<Enchantment>) holder).toList();
		for (int index = 0; index < 3; index++) {
			Holder<Enchantment> enchantment = pool.get(level.getRandom().nextInt(pool.size()));
			int rank = Mth.nextInt(level.getRandom(), enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
			ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, rank));
			if (!player.getInventory().add(book)) {
				level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
					level, player.getX(), player.getY(), player.getZ(), book
				));
			}
		}
	}
}
