package com.stella.rpg;

import com.stella.rpg.api.BonusGrantShare;
import com.stella.rpg.roll.WeaponRolls;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The grant-bonus share: a sibling mod writes stat and affix bonuses onto gear through JDK and vanilla types only. */
public final class BonusGrantGameTest {
	private static final Map<String, Integer> ALL_TEN = allStats(10);

	@SuppressWarnings("unchecked")
	private static Function<Object[], Boolean> share(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(BonusGrantShare.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the grant share is " + shared);
		return (Function<Object[], Boolean>) shared;
	}

	private static Map<String, Integer> allStats(final int amount) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (Stat stat : Stat.values()) {
			map.put(stat.key(), amount);
		}
		return map;
	}

	private static boolean grant(final Function<Object[], Boolean> share, final ItemStack stack, final Map<String, Integer> delta) {
		return share.apply(new Object[] {stack, delta});
	}

	@GameTest
	public void grantThroughTheShare(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(WeaponRolls.STAT_ROLLS, Map.of("str", 2));
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int before = StatBonuses.bonus(player, Stat.DEX);
		boolean applied = grant(share, sword, ALL_TEN);
		int dex = StatBonuses.bonus(player, Stat.DEX);
		int str = StatBonuses.bonus(player, Stat.STR);
		RpgTestSupport.result("grantThroughTheShare", "applied=" + applied + " dexBefore=" + before + " dex=" + dex + " str=" + str);
		helper.assertTrue(applied, "the share returned false");
		helper.assertTrue(dex == before + 10, "dex " + before + " -> " + dex);
		helper.assertTrue(str == 12, "existing roll plus grant is " + str);
		helper.succeed();
	}

	@GameTest
	public void grantStacks(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		for (int i = 0; i < 5; i++) {
			helper.assertTrue(grant(share, sword, ALL_TEN), "grant " + i + " returned false");
		}
		Map<String, Integer> rolls = sword.get(WeaponRolls.STAT_ROLLS);
		RpgTestSupport.result("grantStacks", "rolls=" + rolls);
		for (Stat stat : Stat.values()) {
			helper.assertTrue(rolls != null && rolls.getOrDefault(stat.key(), 0) == 50, stat.key() + " is " + rolls);
		}
		helper.succeed();
	}

	@GameTest
	public void grantWritesAffixesByKind(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		boolean mixed = grant(share, sword, Map.of("damage_flat", 5, "hp_flat", 30));
		Map<String, Integer> affixes = sword.get(WeaponRolls.AFFIX_ROLLS);
		RpgTestSupport.result("grantWritesAffixesByKind", "mixed=" + mixed + " affixes=" + affixes);
		helper.assertTrue(mixed, "a valid affix key was refused");
		helper.assertTrue(affixes != null && affixes.size() == 1 && affixes.get("damage_flat") == 5, "weapon affixes " + affixes);
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		boolean wrongKind = grant(share, helmet, Map.of("damage_flat", 5));
		helper.assertTrue(!wrongKind, "a weapon affix landed on armor");
		helper.assertTrue(!helmet.has(WeaponRolls.STAT_ROLLS) && !helmet.has(WeaponRolls.AFFIX_ROLLS), "armor changed on refusal");
		helper.succeed();
	}

	@GameTest
	public void sheetCeiling(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		for (int i = 0; i < 200; i++) {
			grant(share, sword, ALL_TEN);
		}
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		int bonus = StatBonuses.bonus(player, Stat.STR);
		int stored = sword.get(WeaponRolls.STAT_ROLLS).get("str");
		RpgTestSupport.result("sheetCeiling", "bonus=" + bonus + " stored=" + stored);
		helper.assertTrue(stored == 2000, "stored " + stored);
		helper.assertTrue(bonus == StatSheet.CEILING, "bonus " + bonus + " ceiling " + StatSheet.CEILING);
		helper.succeed();
	}

	@GameTest
	public void keyCapHolds(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		helper.assertTrue(grant(share, sword, Map.of("str", Integer.MAX_VALUE)), "huge grant refused");
		helper.assertTrue(grant(share, sword, Map.of("str", Integer.MAX_VALUE)), "second huge grant refused");
		int stored = sword.get(WeaponRolls.STAT_ROLLS).get("str");
		RpgTestSupport.result("keyCapHolds", "stored=" + stored);
		helper.assertTrue(stored == 100000, "stored " + stored);
		helper.succeed();
	}

	@GameTest
	public void rejectsBadInput(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack stick = new ItemStack(Items.STICK);
		Map<String, Integer> negative = Map.of("str", -3, "agi", 0);
		Map<String, Integer> unknown = Map.of("charisma", 4);
		boolean results = grant(share, stick, ALL_TEN) | grant(share, ItemStack.EMPTY, ALL_TEN)
			| grant(share, sword, Map.of()) | grant(share, sword, negative) | grant(share, sword, unknown);
		boolean wrongArity = share.apply(new Object[] {sword});
		boolean wrongTypes = share.apply(new Object[] {"sword", ALL_TEN});
		boolean nullArgs = share.apply(null);
		boolean nullDelta = share.apply(new Object[] {sword, null});
		RpgTestSupport.result("rejectsBadInput", "any=" + (results | wrongArity | wrongTypes | nullArgs | nullDelta));
		helper.assertTrue(!results && !wrongArity && !wrongTypes && !nullArgs && !nullDelta, "a bad call returned true");
		helper.assertTrue(!sword.has(WeaponRolls.STAT_ROLLS) && !sword.has(WeaponRolls.AFFIX_ROLLS), "a refused call changed the sword");
		helper.assertTrue(!stick.has(WeaponRolls.STAT_ROLLS), "a non-gear stack changed");
		helper.succeed();
	}

	@GameTest
	public void grantOnUnrolledGear(final GameTestHelper helper) {
		Function<Object[], Boolean> share = share(helper);
		ServerPlayer player = RpgTestSupport.player(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		grant(share, sword, Map.of("vit", 7));
		Map<String, Integer> afterGrant = Map.copyOf(sword.get(WeaponRolls.STAT_ROLLS));
		player.getInventory().setItem(9, sword);
		WeaponRolls.rollPossessed(player);
		Map<String, Integer> afterScan = sword.get(WeaponRolls.STAT_ROLLS);
		RpgTestSupport.result("grantOnUnrolledGear", "granted=" + afterGrant + " scan=" + afterScan);
		helper.assertTrue(afterGrant.equals(Map.of("vit", 7)), "grant wrote " + afterGrant);
		helper.assertTrue(afterGrant.equals(afterScan), "the scan rolled on top: " + afterScan);
		helper.succeed();
	}
}
