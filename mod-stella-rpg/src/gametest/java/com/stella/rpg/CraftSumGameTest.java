package com.stella.rpg;

import com.stella.rpg.roll.WeaponRolls;
import java.util.Arrays;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/** Crafting sums the bonus maps and the enchantments of the materials into the result. */
public final class CraftSumGameTest {
	private static ItemStack[] emptyGrid() {
		ItemStack[] grid = new ItemStack[9];
		Arrays.fill(grid, ItemStack.EMPTY);
		return grid;
	}

	private static CraftingMenu menu(final GameTestHelper helper, final ServerPlayer player) {
		return new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), BlockPos.ZERO));
	}

	/** A sword grid in the menu: the blade stacks in the top two cells of the middle column. */
	private static void fillSword(final CraftingMenu menu, final ItemStack top, final ItemStack middle) {
		ItemStack[] grid = emptyGrid();
		grid[1] = top;
		grid[4] = middle;
		grid[7] = new ItemStack(Items.STICK, Math.max(top.getCount(), middle.getCount()));
		for (int i = 0; i < 9; i++) {
			menu.getSlot(1 + i).set(grid[i]);
		}
	}

	@GameTest
	public void swordIsTheSum(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fillSword(menu, BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 2)), BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 6)));
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_SWORD), "no sword result");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		RpgTestSupport.result("swordIsTheSum", "taken=" + BonusTestSupport.stats(taken));
		helper.assertTrue(taken.is(Items.IRON_SWORD) && Map.of("str", 8).equals(BonusTestSupport.stats(taken)), "sword rolls " + BonusTestSupport.stats(taken));
		helper.succeed();
	}

	@GameTest
	public void mixedStatsSum(final GameTestHelper helper) {
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3,
			BonusTestSupport.rolled(Items.DIAMOND, 1, Map.of("str", 2)),
			BonusTestSupport.rolled(Items.DIAMOND, 1, Map.of("dex", 12)),
			new ItemStack(Items.STICK));
		RpgTestSupport.result("mixedStatsSum", "stats=" + BonusTestSupport.stats(sword));
		helper.assertTrue(sword.is(Items.DIAMOND_SWORD), "result " + sword);
		helper.assertTrue(Map.of("str", 2, "dex", 12).equals(BonusTestSupport.stats(sword)), "sword rolls " + BonusTestSupport.stats(sword));
		helper.succeed();
	}

	@GameTest
	public void previewEqualsTake(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fillSword(menu, BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("vit", 3)), BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("vit", 4)));
		ItemStack preview = menu.getSlot(0).getItem().copy();
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		RpgTestSupport.result("previewEqualsTake", "preview=" + BonusTestSupport.stats(preview) + " taken=" + BonusTestSupport.stats(taken));
		helper.assertTrue(Map.of("vit", 7).equals(BonusTestSupport.stats(preview)), "preview rolls " + BonusTestSupport.stats(preview));
		helper.assertTrue(ItemStack.isSameItemSameComponents(preview, taken), "preview " + preview + " taken " + taken);
		helper.succeed();
	}

	@GameTest
	public void shiftClickCraftsCarrySum(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fillSword(menu, BonusTestSupport.rolled(Items.DIAMOND, 2, Map.of("agi", 1)), BonusTestSupport.rolled(Items.DIAMOND, 2, Map.of("agi", 5)));
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
		int swords = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(Items.DIAMOND_SWORD)) {
				swords++;
				helper.assertTrue(Map.of("agi", 6).equals(BonusTestSupport.stats(stack)), "shift sword rolls " + BonusTestSupport.stats(stack));
			}
		}
		RpgTestSupport.result("shiftClickCraftsCarrySum", "swords=" + swords);
		helper.assertTrue(swords == 2, "shift-click swords=" + swords);
		helper.succeed();
	}

	@GameTest
	public void plainMaterialsCraftNoRandomRoll(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		CraftingMenu menu = menu(helper, player);
		fillSword(menu, new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND));
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		RpgTestSupport.result("plainMaterialsCraftNoRandomRoll", "rolls=" + BonusTestSupport.stats(taken));
		helper.assertTrue(taken.is(Items.DIAMOND_SWORD) && Map.of().equals(BonusTestSupport.stats(taken)), "rolls " + BonusTestSupport.stats(taken));
		helper.succeed();
	}

	@GameTest
	public void enchantmentsCombine(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		Holder<Enchantment> unbreaking = BonusTestSupport.enchantment(helper, Enchantments.UNBREAKING);
		ItemStack first = BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), sharpness, 2);
		ItemStack second = BonusTestSupport.enchanted(BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), sharpness, 1), unbreaking, 2);
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3, first, second, new ItemStack(Items.STICK));
		ItemStack capped = BonusTestSupport.craft(helper, 1, 3,
			BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), sharpness, 5),
			BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), sharpness, 5),
			new ItemStack(Items.STICK));
		RpgTestSupport.result("enchantmentsCombine", "sharp=" + BonusTestSupport.level(sword, sharpness) + " unb=" + BonusTestSupport.level(sword, unbreaking)
			+ " capped=" + BonusTestSupport.level(capped, sharpness));
		helper.assertTrue(BonusTestSupport.level(sword, sharpness) == 3, "sharpness " + BonusTestSupport.level(sword, sharpness));
		helper.assertTrue(BonusTestSupport.level(sword, unbreaking) == 2, "unbreaking " + BonusTestSupport.level(sword, unbreaking));
		helper.assertTrue(BonusTestSupport.level(capped, sharpness) == sharpness.value().getMaxLevel(), "capped " + BonusTestSupport.level(capped, sharpness));
		helper.succeed();
	}

	@GameTest
	public void conflictingEnchantmentIsSkipped(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		Holder<Enchantment> smite = BonusTestSupport.enchantment(helper, Enchantments.SMITE);
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3,
			BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), sharpness, 2),
			BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), smite, 4),
			new ItemStack(Items.STICK));
		RpgTestSupport.result("conflictingEnchantmentIsSkipped", "sharp=" + BonusTestSupport.level(sword, sharpness) + " smite=" + BonusTestSupport.level(sword, smite));
		helper.assertTrue(BonusTestSupport.level(sword, sharpness) == 2 && BonusTestSupport.level(sword, smite) == 0, "enchantments " + sword.get(DataComponents.ENCHANTMENTS));
		helper.succeed();
	}

	@GameTest
	public void enchantmentThatDoesNotFitTheGearIsSkipped(final GameTestHelper helper) {
		Holder<Enchantment> fireProtection = BonusTestSupport.enchantment(helper, Enchantments.FIRE_PROTECTION);
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3,
			BonusTestSupport.enchanted(new ItemStack(Items.IRON_INGOT), fireProtection, 2),
			new ItemStack(Items.IRON_INGOT),
			new ItemStack(Items.STICK));
		helper.assertTrue(BonusTestSupport.level(sword, fireProtection) == 0, "fire protection landed on a sword: " + sword.get(DataComponents.ENCHANTMENTS));
		helper.succeed();
	}

	@GameTest
	public void typedAffixFilter(final GameTestHelper helper) {
		ItemStack armorOnly = BonusTestSupport.withAffixes(BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 3)), Map.of("hp_flat", 40, "damage_flat", 9));
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3, armorOnly, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK));
		Map<String, Integer> affixes = sword.get(WeaponRolls.AFFIX_ROLLS);
		RpgTestSupport.result("typedAffixFilter", "stats=" + BonusTestSupport.stats(sword) + " affixes=" + affixes);
		helper.assertTrue(Map.of("str", 3).equals(BonusTestSupport.stats(sword)), "stats " + BonusTestSupport.stats(sword));
		helper.assertTrue(Map.of("damage_flat", 9).equals(affixes), "a sword kept an armor affix: " + affixes);
		helper.succeed();
	}

	@GameTest
	public void compactionNeverInflates(final GameTestHelper helper) {
		ItemStack[] ingots = new ItemStack[9];
		int start = 0;
		for (int i = 0; i < 9; i++) {
			ingots[i] = BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", i + 1, "luk", 1));
			start += i + 1;
		}
		ItemStack block = BonusTestSupport.craft(helper, 3, 3, ingots);
		ItemStack unpacked = BonusTestSupport.craft(helper, 1, 1, block);
		Map<String, Integer> blockStats = BonusTestSupport.stats(block);
		Map<String, Integer> ingotStats = BonusTestSupport.stats(unpacked);
		int back = ingotStats == null ? 0 : ingotStats.getOrDefault("str", 0) * unpacked.getCount();
		RpgTestSupport.result("compactionNeverInflates", "block=" + blockStats + " ingot=" + ingotStats + " count=" + unpacked.getCount() + " start=" + start + " back=" + back);
		helper.assertTrue(block.is(Items.IRON_BLOCK) && blockStats != null && blockStats.get("str") == start, "block " + blockStats);
		helper.assertTrue(unpacked.is(Items.IRON_INGOT) && unpacked.getCount() == 9, "unpacked " + unpacked);
		helper.assertTrue(back <= start && back > 0, "round trip " + start + " -> " + back);
		helper.succeed();
	}

	@GameTest
	public void plainMaterialsCompactPlain(final GameTestHelper helper) {
		ItemStack[] ingots = new ItemStack[9];
		Arrays.fill(ingots, new ItemStack(Items.IRON_INGOT));
		ItemStack block = BonusTestSupport.craft(helper, 3, 3, ingots);
		helper.assertTrue(block.is(Items.IRON_BLOCK) && block.getComponentsPatch().isEmpty(), "plain block carries components: " + block.getComponentsPatch());
		helper.succeed();
	}

	@GameTest
	public void enchantedUnpackRefused(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		ItemStack block = BonusTestSupport.enchanted(new ItemStack(Items.IRON_BLOCK), sharpness, 2);
		ItemStack unpacked = BonusTestSupport.craft(helper, 1, 1, block);
		RpgTestSupport.result("enchantedUnpackRefused", "unpacked=" + unpacked);
		helper.assertTrue(unpacked.isEmpty(), "an enchanted block unpacked to " + unpacked);
		helper.succeed();
	}

	@GameTest
	public void gridRepairKeepsBetter(final GameTestHelper helper) {
		ItemStack weak = BonusTestSupport.rolled(Items.IRON_SWORD, 1, Map.of("str", 2));
		ItemStack strong = BonusTestSupport.rolled(Items.IRON_SWORD, 1, Map.of("str", 5, "dex", 1));
		ItemStack repaired = BonusTestSupport.craft(helper, 2, 1, weak, strong);
		RpgTestSupport.result("gridRepairKeepsBetter", "stats=" + BonusTestSupport.stats(repaired));
		helper.assertTrue(repaired.is(Items.IRON_SWORD), "result " + repaired);
		helper.assertTrue(Map.of("str", 5, "dex", 1).equals(BonusTestSupport.stats(repaired)), "repair rolls " + BonusTestSupport.stats(repaired));
		helper.succeed();
	}

	@GameTest
	public void uncoveredCraftGetsNoRandomRoll(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.onCraftedBy(player, 1);
		RpgTestSupport.result("uncoveredCraftGetsNoRandomRoll", "rolls=" + BonusTestSupport.stats(sword));
		helper.assertTrue(Map.of().equals(BonusTestSupport.stats(sword)), "rolls " + BonusTestSupport.stats(sword));
		helper.succeed();
	}

	@GameTest
	public void sumClamps(final GameTestHelper helper) {
		ItemStack huge = BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 90000));
		ItemStack sword = BonusTestSupport.craft(helper, 1, 3, huge, huge.copy(), new ItemStack(Items.STICK));
		RpgTestSupport.result("sumClamps", "stats=" + BonusTestSupport.stats(sword));
		helper.assertTrue(Map.of("str", 100000).equals(BonusTestSupport.stats(sword)), "stats " + BonusTestSupport.stats(sword));
		helper.succeed();
	}

	@GameTest
	public void nonGearNonMaterialLosesBonus(final GameTestHelper helper) {
		ItemStack ingot = BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("str", 3));
		ItemStack[] grid = new ItemStack[6];
		Arrays.fill(grid, ingot);
		ItemStack bars = BonusTestSupport.craft(helper, 3, 2, grid);
		RpgTestSupport.result("nonGearNonMaterialLosesBonus", "bars=" + bars + " patch=" + bars.getComponentsPatch());
		helper.assertTrue(bars.is(Items.IRON_BARS) && bars.getComponentsPatch().isEmpty(), "bars carry " + bars.getComponentsPatch());
		helper.succeed();
	}

	@GameTest
	public void netherUpgradeKeepsStatsAndEnchants(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		ItemStack diamondSword = BonusTestSupport.enchanted(BonusTestSupport.rolled(Items.DIAMOND_SWORD, 1, Map.of("str", 4, "luk", 2)), sharpness, 3);
		ItemStack upgraded = BonusTestSupport.smith(helper, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), diamondSword, new ItemStack(Items.NETHERITE_INGOT));
		RpgTestSupport.result("netherUpgradeKeepsStatsAndEnchants", "stats=" + BonusTestSupport.stats(upgraded) + " sharp=" + BonusTestSupport.level(upgraded, sharpness));
		helper.assertTrue(upgraded.is(Items.NETHERITE_SWORD), "result " + upgraded);
		helper.assertTrue(Map.of("str", 4, "luk", 2).equals(BonusTestSupport.stats(upgraded)), "stats " + BonusTestSupport.stats(upgraded));
		helper.assertTrue(BonusTestSupport.level(upgraded, sharpness) == 3, "sharpness " + BonusTestSupport.level(upgraded, sharpness));
		helper.succeed();
	}

	@GameTest
	public void netherUpgradeAddsIngotBonus(final GameTestHelper helper) {
		Holder<Enchantment> sharpness = BonusTestSupport.enchantment(helper, Enchantments.SHARPNESS);
		ItemStack diamondSword = BonusTestSupport.rolled(Items.DIAMOND_SWORD, 1, Map.of("str", 4));
		ItemStack ingot = BonusTestSupport.enchanted(BonusTestSupport.rolled(Items.NETHERITE_INGOT, 1, Map.of("str", 6, "agi", 2)), sharpness, 1);
		ItemStack upgraded = BonusTestSupport.smith(helper, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), diamondSword, ingot);
		RpgTestSupport.result("netherUpgradeAddsIngotBonus", "stats=" + BonusTestSupport.stats(upgraded) + " sharp=" + BonusTestSupport.level(upgraded, sharpness));
		helper.assertTrue(Map.of("str", 10, "agi", 2).equals(BonusTestSupport.stats(upgraded)), "stats " + BonusTestSupport.stats(upgraded));
		helper.assertTrue(BonusTestSupport.level(upgraded, sharpness) == 1, "sharpness " + BonusTestSupport.level(upgraded, sharpness));
		helper.succeed();
	}

	@GameTest
	public void craftRandomRollToggleKeepsTheOldRoll(final GameTestHelper helper) {
		try {
			RpgConfigStore.set(RpgConfigStore.parse("{\"material_rolls\":{\"craft_random_roll\":true},\"weapon_rolls\":{\"affix_chance\":0}}", message -> { }));
			ServerPlayer player = RpgTestSupport.realPlayer(helper);
			CraftingMenu menu = menu(helper, player);
			fillSword(menu, new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND));
			menu.clicked(0, 0, ContainerInput.PICKUP, player);
			Map<String, Integer> plain = BonusTestSupport.stats(menu.getCarried());
			menu.setCarried(ItemStack.EMPTY);
			ItemStack summed = BonusTestSupport.craft(helper, 1, 3, BonusTestSupport.rolled(Items.DIAMOND, 1, Map.of("str", 2)), new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK));
			RpgTestSupport.result("craftRandomRollToggleKeepsTheOldRoll", "plain=" + plain + " summed=" + BonusTestSupport.stats(summed));
			helper.assertTrue(plain != null && !plain.isEmpty() && plain.values().stream().allMatch(value -> value >= 3 && value <= 7), "plain craft rolls " + plain);
			helper.assertTrue(Map.of("str", 2).equals(BonusTestSupport.stats(summed)), "a material sum must win over the random roll: " + BonusTestSupport.stats(summed));
		} finally {
			RpgConfigStore.set(null);
		}
		helper.succeed();
	}
}
