package com.stella.learn.gym;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Gear tiers and the hotbar every bot carries. A brain picks the slot it wants; the tier sets armor and weapons. */
public final class BotLoadout {
	private static final int PROTECTION_LEVEL = 3;
	private static final int PEARLS = 4;
	private static final int POTIONS = 3;

	private BotLoadout() {
	}

	/** Clears the bot and fits it out for `gear`. */
	public static void equip(final Bot bot, final Gear gear) {
		equip(bot, gear, gear == Gear.DIAMOND ? PROTECTION_LEVEL : 0);
	}

	/** Like {@link #equip(Bot, Gear)}, with a Protection level on the armor; leather stands in when the tier has none. */
	public static void equip(final Bot bot, final Gear gear, final int protection) {
		ServerPlayer player = bot.player();
		player.getInventory().clearContent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor() || slot == EquipmentSlot.OFFHAND) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

		wearArmor(bot, gear, protection);

		bot.give(Hotbar.MELEE, new ItemStack(melee(gear)));
		bot.give(Hotbar.BOW, new ItemStack(Items.BOW));
		bot.give(Hotbar.CROSSBOW, new ItemStack(Items.CROSSBOW));
		bot.give(Hotbar.BLOCKS, new ItemStack(Items.COBBLESTONE, 64));
		bot.give(Hotbar.PEARLS, new ItemStack(Items.ENDER_PEARL, PEARLS));
		ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
		healing.setCount(POTIONS);
		bot.give(Hotbar.POTIONS, healing);
		bot.give(Hotbar.SHOVEL, new ItemStack(gear == Gear.NONE ? Items.WOODEN_SHOVEL : gear == Gear.IRON ? Items.IRON_SHOVEL : Items.DIAMOND_SHOVEL));
		bot.give(Hotbar.PICKAXE, new ItemStack(gear == Gear.NONE ? Items.WOODEN_PICKAXE : gear == Gear.IRON ? Items.IRON_PICKAXE : Items.DIAMOND_PICKAXE));
		bot.give(Hotbar.ARROWS, new ItemStack(Items.ARROW, 64));
		if (gear == Gear.DIAMOND) {
			player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		}
	}

	/** Puts on a full set of armor for `armor`; diamond carries Protection III. NONE takes the set off. */
	public static void wearArmor(final Bot bot, final Gear armor) {
		wearArmor(bot, armor, armor == Gear.DIAMOND ? PROTECTION_LEVEL : 0);
	}

	public static void wearArmor(final Bot bot, final Gear armor, final int protection) {
		ServerPlayer player = bot.player();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.isArmor()) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

		if (armor == Gear.NONE && protection == 0) {
			return;
		}

		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> enchantment = registry.getOrThrow(Enchantments.PROTECTION);
		boolean diamond = armor == Gear.DIAMOND;
		boolean leather = armor == Gear.NONE;
		int level = protection;
		player.setItemSlot(EquipmentSlot.HEAD, piece(leather ? Items.LEATHER_HELMET : diamond ? Items.DIAMOND_HELMET : Items.IRON_HELMET, level, enchantment));
		player.setItemSlot(EquipmentSlot.CHEST, piece(leather ? Items.LEATHER_CHESTPLATE : diamond ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE, level, enchantment));
		player.setItemSlot(EquipmentSlot.LEGS, piece(leather ? Items.LEATHER_LEGGINGS : diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS, level, enchantment));
		player.setItemSlot(EquipmentSlot.FEET, piece(leather ? Items.LEATHER_BOOTS : diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS, level, enchantment));
	}

	public static void giveShield(final Bot bot) {
		bot.player().setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	private static Item melee(final Gear gear) {
		return switch (gear) {
			case NONE -> Items.STONE_SWORD;
			case IRON -> Items.IRON_SWORD;
			case DIAMOND -> Items.DIAMOND_SWORD;
		};
	}

	private static ItemStack piece(final Item item, final int level, final Holder<Enchantment> protection) {
		ItemStack stack = new ItemStack(item);
		if (level > 0) {
			stack.enchant(protection, level);
		}

		return stack;
	}
}
