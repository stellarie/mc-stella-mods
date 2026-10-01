package com.stella.equips;

import com.stella.equips.anvil.EmeraldPurse;
import com.stella.equips.bag.EmeraldBag;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

/** The emerald bag: item and recipe, deposit and withdraw, the hand check, and the purse that counts bags. */
public final class EmeraldBagGameTest {
	@GameTest
	public void itemAndRecipe(final GameTestHelper helper) {
		CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BUNDLE), new ItemStack(Items.EMERALD)));
		ItemStack result = helper.getLevel().getServer().getRecipeManager()
			.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		int maxStack = new ItemStack(EmeraldBag.ITEM).getMaxStackSize();
		log("itemAndRecipe", "result=" + result + " maxStack=" + maxStack + " stored=" + EmeraldBag.stored(result));
		helper.assertTrue(EmeraldBag.isBag(result) && maxStack == 1 && EmeraldBag.stored(result) == 0, "the bag recipe or item is wrong");
		helper.succeed();
	}

	@GameTest
	public void depositAndWithdraw(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 50));
		int deposited = EmeraldBag.handle(player, new EmeraldBag.Request(false, true, 16));
		int afterSixteen = EmeraldBag.stored(player.getMainHandItem());
		EmeraldBag.handle(player, new EmeraldBag.Request(false, true, EmeraldBag.ALL));
		int afterAll = EmeraldBag.stored(player.getMainHandItem());
		int looseAfterAll = EmeraldBag.loose(player.getInventory());
		int withdrawn = EmeraldBag.handle(player, new EmeraldBag.Request(false, false, 100));
		int afterWithdraw = EmeraldBag.stored(player.getMainHandItem());
		int looseAfterWithdraw = EmeraldBag.loose(player.getInventory());
		log("depositAndWithdraw", "deposited=" + deposited + " afterSixteen=" + afterSixteen + " afterAll=" + afterAll
			+ " looseAfterAll=" + looseAfterAll + " withdrawn=" + withdrawn + " afterWithdraw=" + afterWithdraw + " loose=" + looseAfterWithdraw);
		helper.assertTrue(deposited == 16 && afterSixteen == 16 && afterAll == 50 && looseAfterAll == 0, "deposit moved the wrong amount");
		helper.assertTrue(withdrawn == 50 && afterWithdraw == 0 && looseAfterWithdraw == 50, "withdraw moved the wrong amount");
		helper.succeed();
	}

	@GameTest
	public void rejectsWithoutBag(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 10));
		int offhand = EmeraldBag.handle(player, new EmeraldBag.Request(true, true, EmeraldBag.ALL));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		int noBag = EmeraldBag.handle(player, new EmeraldBag.Request(false, false, 10));
		log("rejectsWithoutBag", "offhand=" + offhand + " noBag=" + noBag + " loose=" + EmeraldBag.loose(player.getInventory()));
		helper.assertTrue(offhand == -1 && noBag == -1 && EmeraldBag.loose(player.getInventory()) == 10, "a request without a bag moved emeralds");
		helper.succeed();
	}

	@GameTest
	public void purseUsesBags(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		player.getMainHandItem().set(EmeraldBag.EMERALDS, 30);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 5));
		int counted = EmeraldPurse.count(player);
		boolean tooMuch = EmeraldPurse.trySpend(player, 40);
		int afterRefusal = EmeraldPurse.count(player);
		boolean paid = EmeraldPurse.trySpend(player, 12);
		int loose = EmeraldBag.loose(player.getInventory());
		int stored = EmeraldBag.stored(player.getMainHandItem());
		log("purseUsesBags", "counted=" + counted + " tooMuch=" + tooMuch + " afterRefusal=" + afterRefusal
			+ " paid=" + paid + " loose=" + loose + " stored=" + stored);
		helper.assertTrue(counted == 35 && !tooMuch && afterRefusal == 35, "the purse miscounted or spent part of a refused price");
		helper.assertTrue(paid && loose == 0 && stored == 23, "the purse did not take loose emeralds first, then the bag");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("unchecked")
	public void sharesPublished(final GameTestHelper helper) {
		ServerPlayer player = holder(helper);
		player.getMainHandItem().set(EmeraldBag.EMERALDS, 7);
		Object count = FabricLoader.getInstance().getObjectShare().get(EmeraldPurse.COUNT_SHARE);
		Object spend = FabricLoader.getInstance().getObjectShare().get(EmeraldPurse.SPEND_SHARE);
		int counted = count instanceof Function<?, ?> f ? ((Function<Object, Integer>) f).apply(player) : -1;
		boolean paid = spend instanceof BiFunction<?, ?, ?> g && ((BiFunction<Object, Integer, Boolean>) g).apply(player, 7);
		log("sharesPublished", "counted=" + counted + " paid=" + paid + " left=" + EmeraldPurse.count(player));
		helper.assertTrue(counted == 7 && paid && EmeraldPurse.count(player) == 0, "the purse shares are missing or wrong");
		helper.succeed();
	}

	private static ServerPlayer holder(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EmeraldBag.ITEM));
		return player;
	}

	private static void log(final String test, final String result) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, result);
	}
}
