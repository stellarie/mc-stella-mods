package com.stella.rpg;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** The weapon slot hotkey rule, the main hand it feeds, and the slot's life across death and save. */
public final class WeaponSlotGameTest {
	private static final int SLOT = WeaponSlot.SLOT;
	private static final float TOLERANCE = 0.0005F;

	@GameTest
	public void swordOverAxe(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(SLOT, new ItemStack(Items.IRON_AXE));
		inventory.setItem(2, new ItemStack(Items.IRON_SWORD));
		inventory.setSelectedSlot(SLOT);

		WeaponSlot.Result result = WeaponSlot.press(player, 2);
		helper.assertTrue(result == WeaponSlot.Result.EQUIPPED, "result " + result);
		helper.assertTrue(inventory.getItem(SLOT).is(Items.IRON_SWORD), "weapon slot holds " + inventory.getItem(SLOT));
		helper.assertTrue(inventory.getItem(2).is(Items.IRON_AXE), "the axe did not land in the pressed slot: " + inventory.getItem(2));
		helper.assertTrue(inventory.getSelectedSlot() == SLOT, "selected " + inventory.getSelectedSlot());
		helper.assertTrue(player.getMainHandItem().is(Items.IRON_SWORD), "main hand " + player.getMainHandItem());
		RpgTestSupport.result("swordOverAxe", "slot=" + inventory.getItem(SLOT) + " pressed=" + inventory.getItem(2));
		helper.succeed();
	}

	@GameTest
	public void fullInventorySwapLosesNothing(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			inventory.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		inventory.setItem(SLOT, new ItemStack(Items.IRON_AXE));
		inventory.setItem(4, new ItemStack(Items.DIAMOND_SWORD));
		inventory.setItem(7, new ItemStack(Items.DIAMOND_HELMET));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Map<String, Integer> before = totals(inventory);

		WeaponSlot.Result weapon = WeaponSlot.press(player, 4);
		WeaponSlot.Result armor = WeaponSlot.press(player, 7);
		Map<String, Integer> after = totals(inventory);
		helper.assertTrue(weapon == WeaponSlot.Result.EQUIPPED && armor == WeaponSlot.Result.ARMOR, "results " + weapon + " " + armor);
		helper.assertTrue(before.equals(after), "before " + before + " after " + after);
		RpgTestSupport.result("fullInventorySwapLosesNothing", "totals=" + after);
		helper.succeed();
	}

	@GameTest
	public void spellbookCastsInPlace(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(SLOT, new ItemStack(Items.IRON_AXE));
		inventory.setItem(4, new ItemStack(RpgTestMod.testBook));
		inventory.setSelectedSlot(SLOT);

		WeaponSlot.Result first = WeaponSlot.press(player, 4);
		ItemStack book = inventory.getItem(4);
		helper.assertTrue(first == WeaponSlot.Result.CAST, "first press " + first);
		helper.assertTrue(book.is(RpgTestMod.testBook), "the book moved: slot 4 holds " + book);
		helper.assertTrue(player.getCooldowns().isOnCooldown(book), "no cooldown started");
		helper.assertTrue(inventory.getSelectedSlot() == SLOT && player.getMainHandItem().is(Items.IRON_AXE), "the weapon was put away");
		// The book's own rule refuses a cast on cooldown.
		WeaponSlot.Result second = WeaponSlot.press(player, 4);
		helper.assertTrue(second == WeaponSlot.Result.NO_EFFECT, "second press " + second);
		RpgTestSupport.result("spellbookCastsInPlace", "first=" + first + " second=" + second);
		helper.succeed();
	}

	@GameTest
	public void armorSwapsIntoItsSlot(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(SLOT, new ItemStack(Items.IRON_AXE));
		inventory.setSelectedSlot(SLOT);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		inventory.setItem(1, new ItemStack(Items.DIAMOND_HELMET));

		WeaponSlot.Result result = WeaponSlot.press(player, 1);
		helper.assertTrue(result == WeaponSlot.Result.ARMOR, "result " + result);
		helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "head " + player.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(inventory.getItem(1).is(Items.IRON_HELMET), "the old helmet did not return: " + inventory.getItem(1));
		helper.assertTrue(inventory.getSelectedSlot() == SLOT, "selected " + inventory.getSelectedSlot());
		RpgTestSupport.result("armorSwapsIntoItsSlot", "head=" + player.getItemBySlot(EquipmentSlot.HEAD));
		helper.succeed();
	}

	@GameTest
	public void blockSelectsThenWeaponReturns(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		Inventory inventory = player.getInventory();
		inventory.clearContent();
		inventory.setItem(SLOT, new ItemStack(Items.IRON_AXE));
		inventory.setItem(3, new ItemStack(Items.DIRT, 16));
		inventory.setItem(5, new ItemStack(Items.IRON_SWORD));
		inventory.setSelectedSlot(SLOT);

		WeaponSlot.Result block = WeaponSlot.press(player, 3);
		helper.assertTrue(block == WeaponSlot.Result.SELECTED, "block press " + block);
		helper.assertTrue(inventory.getSelectedSlot() == 3 && player.getMainHandItem().is(Items.DIRT), "the block is not in hand");
		helper.assertTrue(inventory.getItem(SLOT).is(Items.IRON_AXE), "the weapon left its slot");

		WeaponSlot.Result ready = WeaponSlot.press(player, WeaponSlot.READY);
		helper.assertTrue(ready == WeaponSlot.Result.READIED && player.getMainHandItem().is(Items.IRON_AXE), "the ready key did not return the axe");

		WeaponSlot.press(player, 3);
		WeaponSlot.Result weapon = WeaponSlot.press(player, 5);
		helper.assertTrue(weapon == WeaponSlot.Result.EQUIPPED && player.getMainHandItem().is(Items.IRON_SWORD), "a weapon key did not return to the weapon slot");
		helper.assertTrue(inventory.getItem(5).is(Items.IRON_AXE) && inventory.getItem(3).is(Items.DIRT), "the swap moved the wrong items");
		RpgTestSupport.result("blockSelectsThenWeaponReturns", "block=" + block + " ready=" + ready + " weapon=" + weapon);
		helper.succeed();
	}

	@GameTest
	public void diceReadTheWeaponSlot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource melee = level.damageSources().playerAttack(player);
		float hit = 4.0F;
		Inventory inventory = player.getInventory();
		inventory.setItem(0, new ItemStack(Items.IRON_SWORD));
		inventory.setItem(SLOT, new ItemStack(Items.NETHERITE_AXE));
		inventory.setSelectedSlot(0);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
		try {
			float hotbar = RpgCombat.scaleOutgoing(victim, melee, hit);
			WeaponSlot.press(player, WeaponSlot.READY);
			float weapon = RpgCombat.scaleOutgoing(victim, melee, hit);
			RpgTestSupport.result("diceReadTheWeaponSlot", "hotbar=" + hotbar + " weapon=" + weapon);
			helper.assertTrue(near(hotbar, hit + (4 + 4) * 1.2F), "iron sword in the hotbar " + hotbar);
			// STR 1d4 + axe 2d6, times 1.6 for netherite.
			helper.assertTrue(near(weapon, hit + (4 + 12) * 1.6F), "netherite axe in the weapon slot " + weapon);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		helper.succeed();
	}

	@GameTest
	public void weaponSlotSurvivesSaveAndLoad(final GameTestHelper helper) {
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		first.getInventory().clearContent();
		first.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND_AXE));
		WeaponSlot.press(first, WeaponSlot.READY);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		first.saveWithoutId(output);
		CompoundTag tag = output.buildResult();

		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		second.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
		helper.assertTrue(second.getInventory().getSelectedSlot() == SLOT, "selected " + second.getInventory().getSelectedSlot());
		helper.assertTrue(second.getMainHandItem().is(Items.DIAMOND_AXE), "main hand " + second.getMainHandItem());
		RpgTestSupport.result("weaponSlotSurvivesSaveAndLoad", "selected=" + second.getInventory().getSelectedSlot());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void keepInventoryKeepsTheWeapon(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean original = level.getGameRules().get(GameRules.KEEP_INVENTORY);
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().clearContent();
		player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND_AXE));
		WeaponSlot.press(player, WeaponSlot.READY);
		ServerPlayer reborn;
		level.getGameRules().set(GameRules.KEEP_INVENTORY, true, level.getServer());
		try {
			reborn = TombstoneGameTest.dieAndRespawn(helper, player);
		} finally {
			level.getGameRules().set(GameRules.KEEP_INVENTORY, original, level.getServer());
		}
		helper.assertTrue(reborn.getInventory().getSelectedSlot() == SLOT, "selected " + reborn.getInventory().getSelectedSlot());
		helper.assertTrue(reborn.getMainHandItem().is(Items.DIAMOND_AXE), "main hand " + reborn.getMainHandItem());
		RpgTestSupport.result("keepInventoryKeepsTheWeapon", "main=" + reborn.getMainHandItem());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void tombstoneTakesTheWeaponLikeAnySlot(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		player.getInventory().clearContent();
		for (int i = 9; i < 15; i++) {
			player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 1 + i));
		}
		player.getInventory().setItem(SLOT, new ItemStack(Items.DIAMOND_AXE));
		WeaponSlot.press(player, WeaponSlot.READY);
		Map<String, Integer> before = totals(player.getInventory());
		BlockPos death = player.blockPosition();
		ServerPlayer reborn = TombstoneGameTest.dieAndRespawn(helper, player);
		var tomb = TombstoneGameTest.tombstoneOf(helper.getLevel(), death, player.getUUID());
		helper.assertTrue(tomb != null, "no tombstone");
		Map<String, Integer> after = totals(reborn.getInventory());
		for (ItemStack stack : tomb.contents()) {
			after.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
		// The respawn grants a grave compass.
		after.remove("minecraft:compass");
		helper.assertTrue(before.equals(after), "before " + before + " after " + after);
		RpgTestSupport.result("tombstoneTakesTheWeaponLikeAnySlot", "tomb=" + tomb.contents().size());
		helper.succeed();
	}

	private static Map<String, Integer> totals(final Inventory inventory) {
		Map<String, Integer> totals = new HashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return totals;
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE;
	}
}
