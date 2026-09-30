package com.stella.rpg;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Enchantments on the worn armor and the held item grant stats for each level. */
public final class EnchantStatsGameTest {
	private record Row(Stat stat, int perLevel, List<String> names) {
	}

	@GameTest
	public void table(final GameTestHelper helper) {
		// The plan table. Equips names resolve by the last part of the id path.
		List<Row> rows = List.of(
			new Row(Stat.STR, 2, List.of("minecraft:sharpness", "minecraft:impaling", "minecraft:density",
				"mod-stella-equips-enchants:weapon/wounding", "mod-stella-equips-enchants:weapon/impale", "mod-stella-equips-enchants:weapon/shockwave")),
			new Row(Stat.STR, 1, List.of("minecraft:smite", "minecraft:bane_of_arthropods", "minecraft:breach", "minecraft:knockback", "minecraft:sweeping_edge")),
			new Row(Stat.DEX, 2, List.of("minecraft:power", "mod-stella-equips-enchants:bow/marksman", "mod-stella-equips-enchants:crossbow/railgun")),
			new Row(Stat.DEX, 1, List.of("minecraft:piercing", "mod-stella-equips-enchants:bow/longshot", "mod-stella-equips-enchants:bow/arrow_shower",
				"mod-stella-equips-enchants:armor/farsight", "mod-stella-equips-enchants:weapon/reach")),
			new Row(Stat.AGI, 2, List.of("minecraft:quick_charge", "mod-stella-equips-enchants:armor/fleetfoot", "mod-stella-equips-enchants:crossbow/repeater",
				"mod-stella-equips-enchants:weapon/momentum", "mod-stella-equips-enchants:weapon/tempo")),
			new Row(Stat.AGI, 1, List.of("minecraft:lunge", "minecraft:swift_sneak", "minecraft:feather_falling")),
			new Row(Stat.VIT, 1, List.of("minecraft:protection", "minecraft:blast_protection", "minecraft:fire_protection", "minecraft:projectile_protection",
				"minecraft:respiration")),
			new Row(Stat.VIT, 2, List.of("mod-stella-equips-enchants:armor/bulwark", "mod-stella-equips-enchants:armor/guarding_ward",
				"mod-stella-equips-enchants:armor/second_wind")),
			new Row(Stat.INT, 2, List.of("minecraft:channeling", "mod-stella-equips-enchants:weapon/stormcaller", "mod-stella-equips-enchants:weapon/meteor",
				"mod-stella-equips-enchants:weapon/void_brand", "mod-stella-equips-enchants:armor/ender_ward")),
			new Row(Stat.INT, 1, List.of("minecraft:loyalty", "minecraft:riptide", "mod-stella-equips-enchants:weapon/frostbite", "mod-stella-equips-enchants:weapon/chill")),
			new Row(Stat.LUK, 2, List.of("minecraft:looting", "minecraft:fortune", "minecraft:luck_of_the_sea", "mod-stella-equips-enchants:curse/greed",
				"mod-stella-equips-enchants:tool/prospector", "mod-stella-equips-enchants:tool/enchanted_yield"))
		);
		Map<String, Map<Stat, Integer>> config = RpgConfigStore.current().tables().enchantStats();
		int checked = 0;
		for (Row row : rows) {
			for (String name : row.names()) {
				Identifier id = Identifier.parse(name);
				for (Stat stat : Stat.values()) {
					int expected = stat == row.stat() ? row.perLevel() : 0;
					int actual = EnchantStats.perLevel(id, stat, config);
					helper.assertTrue(actual == expected, name + " " + stat + " gave " + actual + " expected " + expected);
				}
				checked++;
			}
		}
		String[][] curses = {
			{"reckless", "STR", "3", "VIT", "-2"}, {"berserker", "STR", "3", "VIT", "-2"}, {"heavyweight", "VIT", "3", "AGI", "-2"},
			{"leadfoot", "AGI", "-2", "", "0"}, {"sureshot", "DEX", "3", "AGI", "-2"}, {"deadeye", "DEX", "3", "LUK", "-2"}
		};
		for (String[] curse : curses) {
			Identifier id = Identifier.parse("mod-stella-equips-enchants:curse/" + curse[0]);
			for (Stat stat : Stat.values()) {
				int expected = 0;
				if (stat.name().equals(curse[1])) {
					expected = Integer.parseInt(curse[2]);
				} else if (stat.name().equals(curse[3])) {
					expected = Integer.parseInt(curse[4]);
				}
				helper.assertTrue(EnchantStats.perLevel(id, stat, config) == expected, curse[0] + " " + stat);
			}
		}
		for (String other : List.of("minecraft:mending", "minecraft:unbreaking", "othermod:sharpness", "mod-stella-equips-enchants:utility/soulbound",
			"mod-stella-equips-enchants:weapon/venom", "mod-stella-food:sharpness")) {
			for (Stat stat : Stat.values()) {
				helper.assertTrue(EnchantStats.perLevel(Identifier.parse(other), stat, config) == 0, other + " granted " + stat);
			}
		}
		RpgTestSupport.result("table", "names=" + checked);
		helper.succeed();
	}

	@GameTest
	public void wornAndHeld(final GameTestHelper helper) {
		HolderLookup.RegistryLookup<Enchantment> lookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.STR, 20);
		RpgTestSupport.setStat(player, Stat.VIT, 10);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(lookup.getOrThrow(Enchantments.SHARPNESS), 5);
		sword.enchant(lookup.getOrThrow(Enchantments.LOOTING), 3);
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helmet.enchant(lookup.getOrThrow(Enchantments.PROTECTION), 4);
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		boots.enchant(lookup.getOrThrow(Enchantments.FEATHER_FALLING), 4);
		boots.enchant(lookup.getOrThrow(Enchantments.PROTECTION), 2);
		ItemStack spare = new ItemStack(Items.DIAMOND_SWORD);
		spare.enchant(lookup.getOrThrow(Enchantments.SHARPNESS), 5);

		// Iron armor carries VIT of its own, so the plain pieces give the baseline.
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		int vitPlain = StatBonuses.effective(player, Stat.VIT);
		int strBefore = StatBonuses.effective(player, Stat.STR);
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		player.setItemSlot(EquipmentSlot.HEAD, helmet);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.getInventory().add(spare);
		int str = StatBonuses.effective(player, Stat.STR);
		int vit = StatBonuses.effective(player, Stat.VIT);
		int agi = StatBonuses.effective(player, Stat.AGI);
		int luk = StatBonuses.effective(player, Stat.LUK);
		RpgTestSupport.result("wornAndHeld", "str=" + strBefore + "->" + str + " vit=" + vit + " agi=" + agi + " luk=" + luk);
		helper.assertTrue(strBefore == 20 && str == 30, "sharpness V on the held sword gave STR " + str + " (a spare sword in the pack must not count)");
		helper.assertTrue(vit == vitPlain + 4 + 2, "protection on helmet and boots gave VIT " + vit + " over " + vitPlain);
		helper.assertTrue(agi == 4, "feather falling IV gave AGI " + agi);
		helper.assertTrue(luk == 6, "looting III gave LUK " + luk);
		helper.succeed();
	}

	@GameTest
	public void negativeClampsAtZero(final GameTestHelper helper) {
		HolderLookup.RegistryLookup<Enchantment> lookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RpgConfigStore.set(RpgConfigStore.parse("{\"enchant_stats\":{\"sharpness\":{\"str\":-5}}}", message -> { }));
		try {
			ServerPlayer player = RpgTestSupport.player(helper);
			RpgTestSupport.bare(player);
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			sword.enchant(lookup.getOrThrow(Enchantments.SHARPNESS), 2);
			player.setItemSlot(EquipmentSlot.MAINHAND, sword);
			RpgTestSupport.setStat(player, Stat.STR, 3);
			int low = StatBonuses.effective(player, Stat.STR);
			RpgTestSupport.setStat(player, Stat.STR, 20);
			int high = StatBonuses.effective(player, Stat.STR);
			int bonus = StatBonuses.bonus(player, Stat.STR);
			RpgTestSupport.result("negativeClampsAtZero", "low=" + low + " high=" + high + " bonus=" + bonus);
			helper.assertTrue(low == 0, "a curse took STR below zero: " + low);
			helper.assertTrue(high == 10 && bonus == -10, "a curse did not subtract from a large stat: " + high + " bonus " + bonus);
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}
}
