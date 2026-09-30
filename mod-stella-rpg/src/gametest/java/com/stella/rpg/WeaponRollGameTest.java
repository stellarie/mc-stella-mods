package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.AABB;

public final class WeaponRollGameTest {
	private static final Item[][] WEAPONS = {
		{Items.WOODEN_SWORD, Items.WOODEN_AXE},
		{Items.STONE_SWORD, Items.STONE_AXE},
		{Items.COPPER_SWORD, Items.COPPER_AXE},
		{Items.GOLDEN_SWORD, Items.GOLDEN_AXE},
		{Items.IRON_SWORD, Items.IRON_AXE},
		{Items.DIAMOND_SWORD, Items.DIAMOND_AXE},
		{Items.NETHERITE_SWORD, Items.NETHERITE_AXE},
		{Items.BOW, Items.CROSSBOW},
		{Items.TRIDENT, Items.MACE}
	};
	private static final int[][] WEAPON_RANGE = {
		{1, 2}, {1, 3}, {1, 3}, {2, 4}, {2, 5}, {3, 7}, {4, 10}, {2, 5}, {3, 7}
	};
	private static final Item[][] ARMOR = {
		{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS, Items.TURTLE_HELMET},
		{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
		{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
		{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
	};
	private static final int[][] ARMOR_RANGE = {{1, 2}, {1, 3}, {2, 4}, {2, 5}, {3, 7}, {4, 10}};

	@GameTest
	public void rollShape(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20260930L);
		int[] counts = new int[4];
		Set<Stat> seen = EnumSet.noneOf(Stat.class);
		for (int i = 0; i < 2000; i++) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			helper.assertTrue(WeaponRolls.roll(sword, random), "roll " + i + " did not apply");
			Map<String, Integer> rolls = sword.get(WeaponRolls.STAT_ROLLS);
			helper.assertTrue(rolls != null && rolls.size() >= 1 && rolls.size() <= 3, "size " + rolls);
			counts[rolls.size()]++;
			for (Map.Entry<String, Integer> entry : rolls.entrySet()) {
				Stat stat = Stat.byKey(entry.getKey()).orElse(null);
				helper.assertTrue(stat != null, "unknown stat key " + entry.getKey());
				helper.assertTrue(entry.getValue() >= 2 && entry.getValue() <= 5, "value " + rolls);
				seen.add(stat);
			}
		}
		RpgTestSupport.result("rollShape", "counts=" + counts[1] + "/" + counts[2] + "/" + counts[3] + " stats=" + seen);
		helper.assertTrue(counts[1] > 0 && counts[2] > 0 && counts[3] > 0, "a count never appeared");
		helper.assertTrue(counts[1] > 900 && counts[1] < 1100, "count 1 share " + counts[1]);
		helper.assertTrue(counts[2] > 580 && counts[2] < 820, "count 2 share " + counts[2]);
		helper.assertTrue(counts[3] > 200 && counts[3] < 400, "count 3 share " + counts[3]);
		helper.assertTrue(seen.size() == Stat.values().length, "stats seen " + seen);
		helper.succeed();
	}

	@GameTest
	public void tierRanges(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		for (int tier = 0; tier < WEAPONS.length; tier++) {
			for (Item item : WEAPONS[tier]) {
				int[] seen = observe(helper, item, random, false);
				helper.assertTrue(seen[0] == WEAPON_RANGE[tier][0] && seen[1] == WEAPON_RANGE[tier][1],
					item + " observed " + seen[0] + ".." + seen[1] + " expected "
						+ WEAPON_RANGE[tier][0] + ".." + WEAPON_RANGE[tier][1]);
			}
		}
		for (int tier = 0; tier < ARMOR.length; tier++) {
			for (Item item : ARMOR[tier]) {
				int[] seen = observe(helper, item, random, true);
				helper.assertTrue(seen[0] == ARMOR_RANGE[tier][0] && seen[1] == ARMOR_RANGE[tier][1],
					item + " observed " + seen[0] + ".." + seen[1] + " expected "
						+ ARMOR_RANGE[tier][0] + ".." + ARMOR_RANGE[tier][1]);
			}
		}
		RpgTestSupport.result("tierRanges", "weapon tiers=" + WEAPONS.length + " armor tiers=" + ARMOR.length);
		helper.succeed();
	}

	@GameTest
	public void craftRolls(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fill(menu, swordGrid(Items.DIAMOND, 1));
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND_SWORD), "no sword result");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		Map<String, Integer> rolls = taken.get(WeaponRolls.STAT_ROLLS);
		RpgTestSupport.result("craftRolls", "pickup=" + rolls);
		helper.assertTrue(taken.is(Items.DIAMOND_SWORD) && rolls != null, "picked up sword has no rolls: " + taken);
		helper.assertTrue(inRange(rolls, 3, 7), "diamond range " + rolls);
		menu.setCarried(ItemStack.EMPTY);

		fill(menu, swordGrid(Items.DIAMOND, 3));
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		List<ItemStack> swords = new ArrayList<>();
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.DIAMOND_SWORD)) {
				swords.add(stack);
			}
		}
		RpgTestSupport.result("craftRolls", "shift swords=" + swords.size());
		helper.assertTrue(swords.size() == 3, "shift-click swords=" + swords.size());
		for (ItemStack sword : swords) {
			helper.assertTrue(inRange(sword.get(WeaponRolls.STAT_ROLLS), 3, 7), "shift sword rolls " + sword.get(WeaponRolls.STAT_ROLLS));
		}
		helper.succeed();
	}

	@GameTest
	public void lootRolls(final GameTestHelper helper) {
		LootTable table = LootTable.lootTable()
			.withPool(LootPool.lootPool()
				.add(LootItem.lootTableItem(Items.IRON_SWORD))
				.add(LootItem.lootTableItem(Items.STONE_PICKAXE)))
			.build();
		ServerLevel level = helper.getLevel();
		int swords = 0;
		for (int i = 0; i < 20; i++) {
			ObjectArrayList<ItemStack> drops = table.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY));
			for (ItemStack drop : drops) {
				if (drop.is(Items.IRON_SWORD)) {
					swords++;
					helper.assertTrue(inRange(drop.get(WeaponRolls.STAT_ROLLS), 2, 5), "loot sword rolls " + drop.get(WeaponRolls.STAT_ROLLS));
				} else {
					helper.assertTrue(!drop.has(WeaponRolls.STAT_ROLLS), "pickaxe rolled");
				}
			}
		}
		RpgTestSupport.result("lootRolls", "swords=" + swords);
		helper.assertTrue(swords > 0, "loot never produced a sword");
		helper.succeed();
	}

	@GameTest
	public void mobDropRolls(final GameTestHelper helper) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STONE_PICKAXE));
		zombie.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
		zombie.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
		helper.assertTrue(!zombie.getMainHandItem().has(WeaponRolls.STAT_ROLLS), "rolled before death");
		AABB area = new AABB(zombie.blockPosition()).inflate(4.0);
		ServerLevel level = helper.getLevel();
		boolean original = level.getGameRules().get(GameRules.MOB_DROPS);
		List<ItemEntity> items;
		try {
			level.getGameRules().set(GameRules.MOB_DROPS, true, level.getServer());
			ServerPlayer killer = RpgTestSupport.realPlayer(helper);
			zombie.hurtServer(level, level.damageSources().playerAttack(killer), 1000.0F);
			helper.assertTrue(zombie.isDeadOrDying(), "the zombie survived");
			items = level.getEntitiesOfClass(ItemEntity.class, area);
		} finally {
			level.getGameRules().set(GameRules.MOB_DROPS, original, level.getServer());
		}
		boolean sword = false;
		for (ItemEntity entity : items) {
			ItemStack stack = entity.getItem();
			if (stack.is(Items.IRON_SWORD)) {
				sword = true;
				helper.assertTrue(inRange(stack.get(WeaponRolls.STAT_ROLLS), 2, 5), "dropped sword rolls " + stack.get(WeaponRolls.STAT_ROLLS));
			} else if (stack.is(Items.STONE_PICKAXE)) {
				helper.assertTrue(!stack.has(WeaponRolls.STAT_ROLLS), "pickaxe rolled");
			}
		}
		RpgTestSupport.result("mobDropRolls", "drops=" + items.stream().map(entity -> entity.getItem().toString()).toList() + " sword=" + sword);
		helper.assertTrue(sword, "the sword did not drop");
		helper.succeed();
	}

	@GameTest
	public void neverReroll(final GameTestHelper helper) {
		// Values above every tier range: a reroll cannot reproduce them.
		Map<String, Integer> preset = Map.of("str", 9, "vit", 9, "luk", 9);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fill(menu, swordGrid(Items.DIAMOND, 1));
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		helper.assertTrue(taken.has(WeaponRolls.STAT_ROLLS), "first take did not roll");
		taken.set(WeaponRolls.STAT_ROLLS, preset);
		taken.onCraftedBy(player, 1);
		Map<String, Integer> afterTake = taken.get(WeaponRolls.STAT_ROLLS);

		LootTable table = LootTable.lootTable()
			.withPool(LootPool.lootPool()
				.add(LootItem.lootTableItem(Items.IRON_SWORD)
					.apply(SetComponentsFunction.setComponent(WeaponRolls.STAT_ROLLS, preset))))
			.build();
		ObjectArrayList<ItemStack> drops = table.getRandomItems(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY));
		Map<String, Integer> afterLoot = drops.get(0).get(WeaponRolls.STAT_ROLLS);
		RpgTestSupport.result("neverReroll", "take=" + afterTake + " loot=" + afterLoot);
		helper.assertTrue(preset.equals(afterTake), "second craft-take rerolled: " + afterTake);
		helper.assertTrue(preset.equals(afterLoot), "loot pass rerolled: " + afterLoot);
		helper.succeed();
	}

	@GameTest
	public void mainHandOnly(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(WeaponRolls.STAT_ROLLS, Map.of("str", 4));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int held = StatBonuses.bonus(player, Stat.STR);
		int other = StatBonuses.bonus(player, Stat.VIT);
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.OFFHAND, sword.copy());
		int offhand = StatBonuses.bonus(player, Stat.STR);
		player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		player.getInventory().setItem(9, sword.copy());
		int packed = StatBonuses.bonus(player, Stat.STR);
		RpgTestSupport.result("mainHandOnly", "main=" + held + " off=" + offhand + " pack=" + packed + " other=" + other);
		helper.assertTrue(held == 4, "main hand bonus=" + held);
		helper.assertTrue(other == 0, "unrolled stat bonus=" + other);
		helper.assertTrue(offhand == 0, "offhand bonus=" + offhand);
		helper.assertTrue(packed == 0, "inventory bonus=" + packed);
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, sword.copy());
		helper.assertTrue(StatBonuses.bonus(zombie, Stat.STR) == 4, "mob bonus=" + StatBonuses.bonus(zombie, Stat.STR));
		helper.succeed();
	}

	@GameTest
	public void stacksWithArmor(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		RpgTestSupport.bare(player);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(WeaponRolls.STAT_ROLLS, Map.of("vit", 3));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int vit = StatBonuses.bonus(player, Stat.VIT);
		RpgTestSupport.result("stacksWithArmor", "vit=" + vit);
		helper.assertTrue(vit == 10, "vit=" + vit + " expected 4 pieces + set 3 + sword 3");
		helper.succeed();
	}

	@GameTest
	public void nonWeaponIgnored(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		ItemStack[] pickaxe = empty();
		pickaxe[0] = new ItemStack(Items.DIAMOND);
		pickaxe[1] = new ItemStack(Items.DIAMOND);
		pickaxe[2] = new ItemStack(Items.DIAMOND);
		pickaxe[4] = new ItemStack(Items.STICK);
		pickaxe[7] = new ItemStack(Items.STICK);
		fill(menu, pickaxe);
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.DIAMOND_PICKAXE), "no pickaxe result");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		helper.assertTrue(taken.is(Items.DIAMOND_PICKAXE) && !taken.has(WeaponRolls.STAT_ROLLS), "pickaxe rolled: " + taken);
		menu.setCarried(ItemStack.EMPTY);

		ItemStack[] shield = empty();
		for (int i : new int[] {0, 2, 3, 4, 5, 7}) {
			shield[i] = new ItemStack(Items.OAK_PLANKS);
		}
		shield[1] = new ItemStack(Items.IRON_INGOT);
		fill(menu, shield);
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.SHIELD), "no shield result: " + menu.getSlot(0).getItem());
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack shieldTaken = menu.getCarried();
		RpgTestSupport.result("nonWeaponIgnored", "pickaxe=" + taken.has(WeaponRolls.STAT_ROLLS) + " shield=" + shieldTaken.has(WeaponRolls.STAT_ROLLS));
		helper.assertTrue(shieldTaken.is(Items.SHIELD) && !shieldTaken.has(WeaponRolls.STAT_ROLLS), "shield rolled: " + shieldTaken);
		helper.succeed();
	}

	@GameTest
	public void armorRollsOne(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		ItemStack last = ItemStack.EMPTY;
		int[] perStat = new int[Stat.values().length];
		for (int i = 0; i < 500; i++) {
			ItemStack[] grid = empty();
			for (int slot : new int[] {0, 2, 3, 4, 5, 6, 7, 8}) {
				grid[slot] = new ItemStack(Items.IRON_INGOT);
			}
			fill(menu, grid);
			menu.clicked(0, 0, ContainerInput.PICKUP, player);
			ItemStack taken = menu.getCarried();
			helper.assertTrue(taken.is(Items.IRON_CHESTPLATE), "craft " + i + " gave " + taken);
			Map<String, Integer> rolls = taken.get(WeaponRolls.STAT_ROLLS);
			helper.assertTrue(rolls != null && rolls.size() == 1, "craft " + i + " rolls " + rolls);
			helper.assertTrue(inRange(rolls, 2, 5), "craft " + i + " range " + rolls);
			perStat[Stat.byKey(rolls.keySet().iterator().next()).orElseThrow().ordinal()]++;
			last = taken;
			menu.setCarried(ItemStack.EMPTY);
		}
		Map.Entry<String, Integer> roll = last.get(WeaponRolls.STAT_ROLLS).entrySet().iterator().next();
		Stat stat = Stat.byKey(roll.getKey()).orElseThrow();
		int piece = stat == Stat.VIT ? 1 : 0;
		ServerPlayer wearer = RpgTestSupport.player(helper);
		RpgTestSupport.bare(wearer);
		wearer.setItemSlot(EquipmentSlot.CHEST, last.copy());
		int worn = StatBonuses.bonus(wearer, stat);
		wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		wearer.getInventory().setItem(9, last.copy());
		int packed = StatBonuses.bonus(wearer, stat);
		wearer.getInventory().setItem(9, ItemStack.EMPTY);
		wearer.setItemSlot(EquipmentSlot.MAINHAND, last.copy());
		int held = StatBonuses.bonus(wearer, stat);
		RpgTestSupport.result("armorRollsOne", "spread=" + Arrays.toString(perStat) + " stat=" + stat + " worn=" + worn
			+ " pack=" + packed + " held=" + held);
		helper.assertTrue(Arrays.stream(perStat).allMatch(count -> count > 0), "a stat never rolled: " + Arrays.toString(perStat));
		helper.assertTrue(worn == roll.getValue() + piece, "worn=" + worn + " roll=" + roll.getValue() + " iron piece=" + piece);
		helper.assertTrue(packed == 0, "inventory piece added " + packed);
		helper.assertTrue(held == 0, "armor in the main hand added " + held);
		helper.succeed();
	}

	@GameTest
	public void persistsAndSyncs(final GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		Map<String, Integer> rolls = Map.of("str", 3, "luk", 5);
		sword.set(WeaponRolls.STAT_ROLLS, rolls);
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var encoded = ItemStack.CODEC.encodeStart(ops, sword).getOrThrow();
		ItemStack saved = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		ItemStack.STREAM_CODEC.encode(buffer, sword);
		ItemStack synced = ItemStack.STREAM_CODEC.decode(buffer);
		RpgTestSupport.result("persistsAndSyncs", "saved=" + saved.get(WeaponRolls.STAT_ROLLS) + " synced=" + synced.get(WeaponRolls.STAT_ROLLS));
		helper.assertTrue(rolls.equals(saved.get(WeaponRolls.STAT_ROLLS)), "saved=" + saved.get(WeaponRolls.STAT_ROLLS));
		helper.assertTrue(rolls.equals(synced.get(WeaponRolls.STAT_ROLLS)), "synced=" + synced.get(WeaponRolls.STAT_ROLLS));
		helper.succeed();
	}

	@GameTest
	public void configOverride(final GameTestHelper helper) {
		try {
			RpgConfigStore.set(RpgConfigStore.parse(
				"{\"weapon_rolls\":{\"count_weights\":[0,0,1],\"iron\":{\"min\":8,\"max\":8,\"items\":[\"minecraft:iron_sword\"]}}}",
				message -> { }));
			RandomSource random = RandomSource.create(3L);
			for (int i = 0; i < 50; i++) {
				ItemStack sword = new ItemStack(Items.IRON_SWORD);
				WeaponRolls.roll(sword, random);
				Map<String, Integer> rolls = sword.get(WeaponRolls.STAT_ROLLS);
				helper.assertTrue(rolls.size() == 3, "count override ignored: " + rolls);
				helper.assertTrue(rolls.values().stream().allMatch(value -> value == 8), "range override ignored: " + rolls);
			}
			ItemStack axe = new ItemStack(Items.IRON_AXE);
			helper.assertTrue(!WeaponRolls.roll(axe, random), "axe left the item list but still rolled");
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
			WeaponRolls.roll(chest, random);
			helper.assertTrue(chest.get(WeaponRolls.STAT_ROLLS).size() == 1, "armor ignored the armor rule");
			RpgTestSupport.result("configOverride", "ok");
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}

	@GameTest
	public void badConfigFallsBack(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"weapon_rolls\":{\"count_weights\":[0,0,0],\"iron\":{\"min\":9,\"max\":3},"
				+ "\"diamond\":{\"items\":[\"minecraft:diamond_sword\",\"minecraft:not_an_item\"]},\"stone\":{\"max\":1.5}}}",
			errors::add);
		RpgConfig.WeaponRolls rolls = config.weaponRolls();
		RpgConfig.WeaponRolls defaults = RpgConfig.DEFAULT_WEAPON_ROLLS;
		RpgTestSupport.result("badConfigFallsBack", "errors=" + errors);
		helper.assertTrue(errors.size() == 4, "errors=" + errors);
		helper.assertTrue(rolls.countWeights().equals(defaults.countWeights()), "weights=" + rolls.countWeights());
		helper.assertTrue(rolls.tiers().get("iron").equals(defaults.tiers().get("iron")), "iron=" + rolls.tiers().get("iron"));
		helper.assertTrue(rolls.tiers().get("stone").equals(defaults.tiers().get("stone")), "stone=" + rolls.tiers().get("stone"));
		helper.assertTrue(rolls.tiers().get("diamond").items().size() == 1, "diamond=" + rolls.tiers().get("diamond").items());
		helper.assertTrue(rolls.tiers().get("gold").equals(defaults.tiers().get("gold")), "gold changed");
		helper.succeed();
	}

	private static int[] observe(final GameTestHelper helper, final Item item, final RandomSource random, final boolean armor) {
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int i = 0; i < 400; i++) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(WeaponRolls.roll(stack, random), item + " did not roll");
			Map<String, Integer> rolls = stack.get(WeaponRolls.STAT_ROLLS);
			helper.assertTrue(!armor || rolls.size() == 1, item + " armor rolls " + rolls);
			for (int value : rolls.values()) {
				low = Math.min(low, value);
				high = Math.max(high, value);
			}
		}
		return new int[] {low, high};
	}

	private static boolean inRange(final Map<String, Integer> rolls, final int min, final int max) {
		return rolls != null && !rolls.isEmpty() && rolls.values().stream().allMatch(value -> value >= min && value <= max);
	}

	private static ItemStack[] empty() {
		ItemStack[] grid = new ItemStack[9];
		Arrays.fill(grid, ItemStack.EMPTY);
		return grid;
	}

	private static ItemStack[] swordGrid(final Item blade, final int copies) {
		ItemStack[] grid = empty();
		grid[1] = new ItemStack(blade, copies);
		grid[4] = new ItemStack(blade, copies);
		grid[7] = new ItemStack(Items.STICK, copies);
		return grid;
	}

	private static CraftingMenu menu(final GameTestHelper helper, final ServerPlayer player) {
		return new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), BlockPos.ZERO));
	}

	private static void fill(final CraftingMenu menu, final ItemStack[] grid) {
		for (int i = 0; i < 9; i++) {
			menu.getSlot(1 + i).set(grid[i]);
		}
	}
}
