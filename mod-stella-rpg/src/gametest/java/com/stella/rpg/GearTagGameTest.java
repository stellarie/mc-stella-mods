package com.stella.rpg;

import com.stella.rpg.api.BonusGrantShare;
import com.stella.rpg.roll.ItemTiers;
import com.stella.rpg.roll.WeaponRolls;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Gear in {@code #mod-stella-rpg:bonus_weapons} and {@code bonus_armor} carries and uses bonuses but never rolls at random.
 * The test data pack lists shears and a carved pumpkin, and one absent item id with {@code required: false}.
 */
public final class GearTagGameTest {
	@SuppressWarnings("unchecked")
	private static boolean grant(final ItemStack stack, final Map<String, Integer> delta) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(BonusGrantShare.OBJECT_SHARE_KEY);
		return ((Function<Object[], Boolean>) shared).apply(new Object[] {stack, delta});
	}

	@GameTest
	public void taggedWeaponCarriesAndCounts(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack shears = new ItemStack(Items.SHEARS);
		boolean granted = grant(shears, Map.of("str", 4));
		player.setItemSlot(EquipmentSlot.MAINHAND, shears);
		int held = StatBonuses.bonus(player, Stat.STR);
		player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.OFFHAND, shears.copy());
		int offhand = StatBonuses.bonus(player, Stat.STR);
		RpgTestSupport.result("taggedWeaponCarriesAndCounts", "granted=" + granted + " held=" + held + " offhand=" + offhand);
		helper.assertTrue(granted, "the tagged weapon refused a grant");
		helper.assertTrue(held == 4, "held bonus " + held);
		helper.assertTrue(offhand == 0, "a weapon in the offhand added " + offhand);
		helper.succeed();
	}

	@GameTest
	public void taggedArmorCarriesAndCounts(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
		boolean granted = grant(pumpkin, Map.of("vit", 6));
		player.setItemSlot(EquipmentSlot.HEAD, pumpkin);
		int worn = StatBonuses.bonus(player, Stat.VIT);
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.MAINHAND, pumpkin.copy());
		int held = StatBonuses.bonus(player, Stat.VIT);
		RpgTestSupport.result("taggedArmorCarriesAndCounts", "granted=" + granted + " worn=" + worn + " held=" + held);
		helper.assertTrue(granted && worn == 6 && held == 0, "worn " + worn + " held " + held);
		helper.succeed();
	}

	@GameTest
	public void craftSumFeedsTaggedGear(final GameTestHelper helper) {
		ItemStack shears = BonusTestSupport.craft(helper, 2, 2, ItemStack.EMPTY,
			BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("agi", 2)), BonusTestSupport.rolled(Items.IRON_INGOT, 1, Map.of("agi", 3)), ItemStack.EMPTY);
		RpgTestSupport.result("craftSumFeedsTaggedGear", "stats=" + BonusTestSupport.stats(shears));
		helper.assertTrue(shears.is(Items.SHEARS) && Map.of("agi", 5).equals(BonusTestSupport.stats(shears)), "shears " + BonusTestSupport.stats(shears));
		helper.succeed();
	}

	@GameTest
	public void taggedGearNeverRollsAtRandom(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(5L);
		ItemStack shears = new ItemStack(Items.SHEARS);
		ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
		helper.assertTrue(!WeaponRolls.roll(shears, random) && !WeaponRolls.roll(pumpkin, random), "a tagged item rolled at random");
		helper.assertTrue(ItemTiers.of(shears) == null && ItemTiers.of(pumpkin) == null, "a tagged item got a tier");
		helper.assertTrue(!shears.has(WeaponRolls.STAT_ROLLS) && !pumpkin.has(WeaponRolls.STAT_ROLLS), "a roll component appeared");
		helper.succeed();
	}

	@GameTest
	public void toolsStillCarryNothing(final GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		helper.assertTrue(!grant(pickaxe, Map.of("str", 1)) && !pickaxe.has(WeaponRolls.STAT_ROLLS), "a pickaxe took a bonus");
		helper.succeed();
	}

	@GameTest
	public void taggedChestGearIsMarked(final GameTestHelper helper) {
		LootTable table = LootTable.lootTable().setParamSet(LootContextParamSets.CHEST)
			.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.SHEARS)).add(LootItem.lootTableItem(Items.CARVED_PUMPKIN))).build();
		LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		int seen = 0;
		for (int seed = 0; seed < 10; seed++) {
			for (ItemStack stack : table.getRandomItems(params, seed)) {
				seen++;
				helper.assertTrue(stack.has(WeaponRolls.STAT_ROLLS) && stack.get(WeaponRolls.STAT_ROLLS).isEmpty(), "tagged chest gear is unmarked: " + stack.get(WeaponRolls.STAT_ROLLS));
			}
		}
		helper.assertTrue(seen > 0, "nothing dropped");
		helper.succeed();
	}

	@GameTest
	public void absentSiblingIdsDoNotBreakTheTags(final GameTestHelper helper) {
		// The test tags also list ids of a mod that is not installed. The tags still load and the real entries still work.
		helper.assertTrue(new ItemStack(Items.SHEARS).is(com.stella.rpg.roll.BonusCarrier.WEAPONS), "shears left the weapon tag");
		helper.assertTrue(new ItemStack(Items.CARVED_PUMPKIN).is(com.stella.rpg.roll.BonusCarrier.ARMOR), "pumpkin left the armor tag");
		helper.succeed();
	}
}
