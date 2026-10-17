package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

/** The anvil step of the two cores: what each gives, what it refuses, what it costs, and what it takes. */
public class CoreImbueGameTest {
	private static final int PURSE = 100;

	static Player survival(final GameTestHelper helper, final int emeralds) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
		return player;
	}

	static AnvilMenu anvil(final Player player, final ItemStack input, final ItemStack addition) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
		menu.createResult();
		return menu;
	}

	static ItemStack result(final AnvilMenu menu) {
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}

	static ItemStack take(final AnvilMenu menu, final Player player) {
		ItemStack taken = result(menu).copy();
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result(menu));
		return taken;
	}

	static ItemStack dark(final int count) {
		return new ItemStack(EndItems.darkBlazeCore(), count);
	}

	static ItemStack wither(final int count) {
		return new ItemStack(EndItems.witherCore(), count);
	}

	@GameTest(environment = "mod-stella-end-test:imbue_blaze", maxTicks = 20)
	public void aWeaponAndADarkBlazeCoreGiveTheWeaponBlazeSoulAndTakeOneCoreAndLeaveTheInputWhole(final GameTestHelper helper) {
		Player player = survival(helper, PURSE);
		List<String> bad = new ArrayList<>();
		for (ItemStack weapon : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.BOW), new ItemStack(WitheringArms.sword()),
			new ItemStack(WitheringArms.bow()))) {
			ItemStack input = weapon.copy();
			AnvilMenu menu = anvil(player, input, dark(3));
			boolean unchanged = !BlazeSoul.has(input) && !BlazeSoul.has(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem());
			ItemStack taken = take(menu, player);
			int left = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount();
			if (!taken.is(weapon.getItem()) || !BlazeSoul.has(taken) || WitherDrain.has(taken)) {
				bad.add(weapon.getItem() + " gave " + taken);
			}
			if (left != 2 || !unchanged) {
				bad.add(weapon.getItem() + " left " + left + " of 3 cores, input untouched " + unchanged);
			}
		}
		EndTestSupport.result("imbueBlaze", "bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "dark blaze core anvil off the spec: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:imbue_drain", maxTicks = 20)
	public void aWeaponAndAWitherCoreGiveTheWeaponTheWitherDrainEnchantmentAndKeepItsOtherEnchantments(final GameTestHelper helper) {
		Player player = survival(helper, PURSE);
		var access = helper.getLevel().registryAccess();
		List<String> bad = new ArrayList<>();
		for (ItemStack weapon : List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW), new ItemStack(WitheringArms.sword()), new ItemStack(Items.NETHERITE_AXE))) {
			ItemStack input = weapon.copy();
			// An enchantment already on the weapon survives the imbue.
			net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> unbreaking = access.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
			input.enchant(unbreaking, 2);
			AnvilMenu menu = anvil(player, input, wither(2));
			boolean inputClean = !WitherDrain.has(input);
			ItemStack taken = take(menu, player);
			ItemEnchantments held = taken.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			int left = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount();
			if (!taken.is(weapon.getItem()) || !WitherDrain.has(taken) || BlazeSoul.has(taken) || held.getLevel(unbreaking) != 2 || held.size() != 2) {
				bad.add(weapon.getItem() + " gave " + taken + " holding " + held.size() + " enchantments");
			}
			if (left != 1 || !inputClean) {
				bad.add(weapon.getItem() + " left " + left + " of 2 cores, input untouched " + inputClean);
			}
		}
		EndTestSupport.result("imbueDrain", "bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "wither core anvil off the spec: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:imbue_refuse", maxTicks = 20)
	public void theAnvilRefusesNonWeaponsAndWeaponsThatAlreadyHoldTheImbueAndTwoCores(final GameTestHelper helper) {
		Player player = survival(helper, PURSE);
		var access = helper.getLevel().registryAccess();
		List<String> bad = new ArrayList<>();
		ItemStack soulSword = BlazeSoul.imbued(new ItemStack(Items.IRON_SWORD));
		ItemStack drainSword = WitherDrain.enchanted(new ItemStack(Items.IRON_SWORD), access);
		record Case(String name, ItemStack input, ItemStack addition) {
		}
		for (Case refused : List.of(
			new Case("stick and dark core", new ItemStack(Items.STICK), dark(1)),
			new Case("helmet and dark core", new ItemStack(Items.NETHERITE_HELMET), dark(1)),
			new Case("shield and wither core", new ItemStack(Items.SHIELD), wither(1)),
			new Case("soul sword and dark core", soulSword, dark(1)),
			new Case("drain sword and wither core", drainSword, wither(1)),
			new Case("core and dark core", wither(1), dark(1)))) {
			AnvilMenu menu = anvil(player, refused.input(), refused.addition());
			if (!result(menu).isEmpty()) {
				bad.add(refused.name() + " gave " + result(menu));
			}
		}
		// A weapon with one imbue still takes the other.
		ItemStack both = result(anvil(player, soulSword, wither(1)));
		if (both.isEmpty() || !BlazeSoul.has(both) || !WitherDrain.has(both)) {
			bad.add("a soul sword and a wither core gave " + both);
		}
		EndTestSupport.result("imbueRefuse", "bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "refusals off the spec: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:imbue_price", maxTicks = 20)
	public void theImbueCostsEightEmeraldsWithTheEquipsAnvilAndAPlayerWithSevenCannotTakeIt(final GameTestHelper helper) {
		Player rich = survival(helper, PURSE);
		Player poor = survival(helper, CoreImbue.COST - 1);
		AnvilMenu paid = anvil(rich, new ItemStack(Items.IRON_SWORD), dark(1));
		AnvilMenu short1 = anvil(poor, new ItemStack(Items.IRON_SWORD), wither(1));
		int cost = paid.getCost();
		boolean richCan = paid.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(rich);
		boolean poorCan = short1.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(poor);
		int before = countEmeralds(rich);
		take(paid, rich);
		int spent = before - countEmeralds(rich);
		EndTestSupport.result("imbuePrice", "cost=" + cost + " richCan=" + richCan + " poorCan=" + poorCan + " spent=" + spent + " constant=" + CoreImbue.COST);
		helper.assertTrue(CoreImbue.COST == 8, "the shipped cost is " + CoreImbue.COST);
		helper.assertTrue(cost == CoreImbue.COST, "the anvil shows a cost of " + cost);
		helper.assertTrue(richCan && !poorCan, "rich " + richCan + ", poor " + poorCan);
		helper.assertTrue(spent == CoreImbue.COST, "the anvil spent " + spent + " emeralds");
		helper.succeed();
	}

	private static int countEmeralds(final Player player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			count += stack.is(Items.EMERALD) ? stack.getCount() : 0;
		}
		return count;
	}
}
