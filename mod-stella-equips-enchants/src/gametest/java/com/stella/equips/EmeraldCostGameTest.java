package com.stella.equips;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameType;

/** Slice 6. Enchanting costs emeralds and spends no experience. */
public class EmeraldCostGameTest {
	private static final Identifier TEST_MARKER =
		Identifier.fromNamespaceAndPath(PowerFixtures.TEST_MOD_ID, "test_marker");
	private static final Identifier SHARPNESS = Identifier.withDefaultNamespace("sharpness");
	private static final int EMERALDS = 16;
	private static final int LEVELS = 10;
	private static final int ONE_ENCHANTMENT = 1;
	private static final int NO_EMERALDS = 0;

	@GameTest
	public void anvilChargesOneEmeraldForOneEnchantment(final GameTestHelper helper) {
		Player player = survival(helper);
		AnvilMenu menu = anvilWith(helper, player, book(helper, TEST_MARKER));
		int price = menu.getCost();
		player.getInventory().add(new ItemStack(Items.EMERALD, EMERALDS));
		player.experienceLevel = LEVELS;
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, ItemStack.EMPTY);
		int left = countEmeralds(player);
		helper.assertTrue(
			price == ONE_ENCHANTMENT && left == EMERALDS - ONE_ENCHANTMENT && player.experienceLevel == LEVELS,
			"one enchantment: price=" + price + " emeraldsLeft=" + left + " level=" + player.experienceLevel
		);
		log("anvilOneEnchantment", price, left, player.experienceLevel);
		helper.succeed();
	}

	@GameTest
	public void anvilDisplayEqualsTheCharge(final GameTestHelper helper) {
		Player player = survival(helper);
		AnvilMenu menu = anvilWith(helper, player, book(helper, TEST_MARKER, SHARPNESS));
		int displayed = menu.getCost();
		player.getInventory().add(new ItemStack(Items.EMERALD, EMERALDS));
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, ItemStack.EMPTY);
		int charged = EMERALDS - countEmeralds(player);
		helper.assertTrue(
			displayed == charged,
			"the anvil displayed " + displayed + " and charged " + charged
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=anvilDisplayIsTheCharge RESULT displayed={} charged={}",
			displayed, charged
		);
		helper.succeed();
	}

	@GameTest
	public void anvilRefusesWhenTheEmeraldsAreShort(final GameTestHelper helper) {
		Player player = survival(helper);
		AnvilMenu menu = anvilWith(helper, player, book(helper, TEST_MARKER, SHARPNESS));
		int price = menu.getCost();
		player.getInventory().add(new ItemStack(Items.EMERALD, price - 1));
		boolean refusedWithFew = !menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		player.getInventory().add(new ItemStack(Items.EMERALD, 1));
		boolean allowedWithEnough = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		helper.assertTrue(
			refusedWithFew && allowedWithEnough,
			"price=" + price + " refusedWithFew=" + refusedWithFew + " allowedWithEnough=" + allowedWithEnough
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=anvilRefusesShortEmeralds RESULT price={} refusedWithFew={} allowedWithEnough={}",
			price, refusedWithFew, allowedWithEnough
		);
		helper.succeed();
	}

	@GameTest
	public void tableChargesTheOfferedLevelAndSpendsNoExperience(final GameTestHelper helper) {
		Player player = survival(helper);
		EnchantmentMenu menu = table(helper, player);
		int offer = firstOffer(menu);
		helper.assertTrue(offer >= 0, "the table offered nothing");
		int price = menu.costs[offer];
		int level = menu.levelClue[offer];
		menu.getSlot(1).set(new ItemStack(Items.EMERALD, EMERALDS));
		player.experienceLevel = LEVELS;
		boolean accepted = menu.clickMenuButton(player, offer);
		int left = menu.getSlot(1).getItem().getCount();
		helper.assertTrue(
			accepted && price == level && left == EMERALDS - price && player.experienceLevel == LEVELS,
			"offer=" + offer + " price=" + price + " offeredLevel=" + level + " accepted=" + accepted
				+ " emeraldsLeft=" + left + " level=" + player.experienceLevel
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=tableChargesTheOfferedLevel RESULT offer={} price={} offeredLevel={} accepted={} "
				+ "emeraldsLeft={} level={}",
			offer, price, level, accepted, left, player.experienceLevel
		);
		helper.succeed();
	}

	@GameTest
	public void tableDisplayEqualsTheCharge(final GameTestHelper helper) {
		Player player = survival(helper);
		EnchantmentMenu menu = table(helper, player);
		int offer = firstOffer(menu);
		helper.assertTrue(offer >= 0, "the table offered nothing");
		int displayed = menu.costs[offer];
		menu.getSlot(1).set(new ItemStack(Items.EMERALD, EMERALDS));
		menu.clickMenuButton(player, offer);
		int charged = EMERALDS - menu.getSlot(1).getItem().getCount();
		helper.assertTrue(
			displayed == charged,
			"the table displayed " + displayed + " and charged " + charged
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=tableDisplayIsTheCharge RESULT displayed={} charged={}",
			displayed, charged
		);
		helper.succeed();
	}

	@GameTest
	public void tableRefusesWhenTheEmeraldsAreShort(final GameTestHelper helper) {
		Player player = survival(helper);
		EnchantmentMenu menu = table(helper, player);
		int offer = firstOffer(menu);
		helper.assertTrue(offer >= 0, "the table offered nothing");
		int price = menu.costs[offer];
		menu.getSlot(1).set(new ItemStack(Items.EMERALD, price - 1));
		boolean accepted = menu.clickMenuButton(player, offer);
		int left = menu.getSlot(1).getItem().getCount();
		helper.assertTrue(
			!accepted && left == price - 1,
			"price=" + price + " accepted=" + accepted + " emeraldsLeft=" + left
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=tableRefusesShortEmeralds RESULT price={} accepted={} emeraldsLeft={}",
			price, accepted, left
		);
		helper.succeed();
	}

	private static void log(final String test, final int price, final int left, final int level) {
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test={} RESULT price={} emeraldsLeft={} level={}",
			test, price, left, level
		);
	}

	private static int firstOffer(final EnchantmentMenu menu) {
		for (int i = 0; i < menu.costs.length; i++) {
			if (menu.costs[i] > 0 && menu.levelClue[i] > 0) {
				return i;
			}
		}

		return -1;
	}

	private static Player survival(final GameTestHelper helper) {
		return helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	private static AnvilMenu anvilWith(final GameTestHelper helper, final Player player, final ItemStack addition) {
		Inventory inventory = player.getInventory();
		AnvilMenu menu = new AnvilMenu(1, inventory, ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
		menu.createResult();
		return menu;
	}

	private static EnchantmentMenu table(final GameTestHelper helper, final Player player) {
		EnchantmentMenu menu = new EnchantmentMenu(
			1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), new BlockPos(1, 2, 1))
		);
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
		return menu;
	}

	private static ItemStack book(final GameTestHelper helper, final Identifier... ids) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack book = null;
		for (Identifier id : ids) {
			Enchantment enchantment = registry.getValue(id);
			if (enchantment == null) {
				throw new IllegalStateException("unknown enchantment " + id);
			}

			EnchantmentInstance instance = new EnchantmentInstance(registry.wrapAsHolder(enchantment), 1);
			if (book == null) {
				book = EnchantmentHelper.createBook(instance);
			} else {
				ItemStack target = book;
				EnchantmentHelper.updateEnchantments(
					target, enchantments -> enchantments.set(instance.enchantment(), instance.level())
				);
			}
		}

		return book;
	}

	private static int countEmeralds(final Player player) {
		Inventory inventory = player.getInventory();
		int count = NO_EMERALDS;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}

		return count;
	}
}
