package com.stella.equips;

import com.stella.equips.anvil.EmeraldPurse;
import com.stella.equips.imbue.NetherImbue;
import com.stella.equips.refine.Refine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;

/** The nether star anvil: the recipe, the count, the emerald price, the stat grant bridge, and the refusals. */
public final class NetherImbueAnvilGameTest {
	private static final int PURSE = 400;

	private static Player survival(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, PURSE));
		return player;
	}

	private static AnvilMenu anvil(final Player player, final ItemStack input, final ItemStack addition) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
		menu.createResult();
		return menu;
	}

	private static ItemStack result(final AnvilMenu menu) {
		return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}

	private static ItemStack take(final AnvilMenu menu, final Player player) {
		ItemStack taken = result(menu).copy();
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result(menu));
		return taken;
	}

	private static ItemStack imbuedTimes(final ItemStack base, final int count) {
		ItemStack stack = base.copy();
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NetherImbue.KEY, count));
		return stack;
	}

	/** Runs the body with a stand-in for the rpg grant share, or with none, then restores what was there. */
	private static void withShare(final Function<Object[], Boolean> standIn, final Runnable body) {
		Object previous = standIn == null
			? FabricLoader.getInstance().getObjectShare().remove(NetherImbue.GRANT_SHARE)
			: FabricLoader.getInstance().getObjectShare().put(NetherImbue.GRANT_SHARE, standIn);
		try {
			body.run();
		} finally {
			if (previous == null) {
				FabricLoader.getInstance().getObjectShare().remove(NetherImbue.GRANT_SHARE);
			} else {
				FabricLoader.getInstance().getObjectShare().put(NetherImbue.GRANT_SHARE, previous);
			}
		}
	}

	/** Records each call and adds the delta into the stack under another key, as the real share adds into its rolls. */
	private static Function<Object[], Boolean> recorder(final List<Map<String, Integer>> deltas) {
		return args -> {
			ItemStack stack = (ItemStack) args[0];
			@SuppressWarnings("unchecked")
			Map<String, Integer> delta = (Map<String, Integer>) args[1];
			deltas.add(new LinkedHashMap<>(delta));
			for (Map.Entry<String, Integer> entry : delta.entrySet()) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("standin_" + entry.getKey(),
					tag.getIntOr("standin_" + entry.getKey(), 0) + entry.getValue()));
			}
			return true;
		};
	}

	private static int standIn(final ItemStack stack, final String stat) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr("standin_" + stat, 0);
	}

	private static List<String> lore(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	@GameTest
	public void aWeaponAndAStarGiveOneImbueAndTakeOneStar(final GameTestHelper helper) {
		Player player = survival(helper);
		withShare(null, () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR, 3));
			ItemStack taken = take(menu, player);
			int left = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount();
			StellaEquips.LOGGER.info("[BEHAVIOR] test=aWeaponAndAStarGiveOneImbueAndTakeOneStar RESULT result={} count={} starsLeft={}",
				taken.getItem(), NetherImbue.count(taken), left);
			helper.assertTrue(taken.is(Items.IRON_SWORD) && NetherImbue.count(taken) == 1, "the anvil gave " + taken);
			helper.assertTrue(left == 2, "the anvil left " + left + " of 3 stars");
		});
		helper.succeed();
	}

	@GameTest
	public void aSecondImbueMakesTwo(final GameTestHelper helper) {
		Player player = survival(helper);
		withShare(null, () -> {
			ItemStack first = take(anvil(player, new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.NETHER_STAR)), player);
			ItemStack second = result(anvil(player, first, new ItemStack(Items.NETHER_STAR))).copy();
			StellaEquips.LOGGER.info("[BEHAVIOR] test=aSecondImbueMakesTwo RESULT first={} second={}", NetherImbue.count(first), NetherImbue.count(second));
			helper.assertTrue(NetherImbue.count(first) == 1 && NetherImbue.count(second) == 2,
				"counts " + NetherImbue.count(first) + " then " + NetherImbue.count(second));
		});
		helper.succeed();
	}

	@GameTest
	public void theInputIsNeverChanged(final GameTestHelper helper) {
		Player player = survival(helper);
		ItemStack input = new ItemStack(Items.IRON_SWORD);
		ItemStack[] out = new ItemStack[1];
		withShare(null, () -> out[0] = result(anvil(player, input, new ItemStack(Items.NETHER_STAR))).copy());
		helper.assertTrue(NetherImbue.count(out[0]) == 1, "no result to compare: " + out[0]);
		helper.assertTrue(NetherImbue.count(input) == 0, "the input held " + NetherImbue.count(input) + " imbues");
		helper.succeed();
	}

	@GameTest
	public void priceIsSixteenTimesTheNextCountInEmeralds(final GameTestHelper helper) {
		Player player = survival(helper);
		int[] prices = new int[3];
		withShare(null, () -> {
			prices[0] = anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR)).getCost();
			prices[1] = anvil(player, imbuedTimes(new ItemStack(Items.IRON_SWORD), 1), new ItemStack(Items.NETHER_STAR)).getCost();
			prices[2] = anvil(player, imbuedTimes(new ItemStack(Items.IRON_SWORD), 9), new ItemStack(Items.NETHER_STAR)).getCost();
		});
		StellaEquips.LOGGER.info("[BEHAVIOR] test=priceIsSixteenTimesTheNextCountInEmeralds RESULT first={} second={} tenth={} step={}",
			prices[0], prices[1], prices[2], NetherImbue.PRICE_STEP);
		helper.assertTrue(NetherImbue.PRICE_STEP == 16, "the step is " + NetherImbue.PRICE_STEP);
		helper.assertTrue(prices[0] == NetherImbue.PRICE_STEP && prices[1] == 2 * NetherImbue.PRICE_STEP && prices[2] == 10 * NetherImbue.PRICE_STEP,
			"prices " + prices[0] + ", " + prices[1] + ", " + prices[2]);
		helper.succeed();
	}

	@GameTest
	public void theChargeIsTheDisplayedPriceInEmeraldsAndNoLevels(final GameTestHelper helper) {
		Player player = survival(helper);
		player.experienceLevel = 30;
		int[] numbers = new int[2];
		withShare(null, () -> {
			AnvilMenu menu = anvil(player, imbuedTimes(new ItemStack(Items.IRON_SWORD), 2), new ItemStack(Items.NETHER_STAR));
			numbers[0] = menu.getCost();
			take(menu, player);
			numbers[1] = PURSE - EmeraldPurse.count(player);
		});
		StellaEquips.LOGGER.info("[BEHAVIOR] test=theChargeIsTheDisplayedPriceInEmeraldsAndNoLevels RESULT displayed={} charged={} level={}",
			numbers[0], numbers[1], player.experienceLevel);
		helper.assertTrue(numbers[0] == NetherImbue.price(2) && numbers[1] == numbers[0], "displayed " + numbers[0] + " charged " + numbers[1]);
		helper.assertTrue(player.experienceLevel == 30, "the level fell to " + player.experienceLevel);
		helper.succeed();
	}

	@GameTest
	public void shortEmeraldsBlockThePickup(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.EMERALD, NetherImbue.price(0) - 1));
		boolean[] pickable = new boolean[1];
		boolean[] offered = new boolean[1];
		withShare(null, () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR));
			pickable[0] = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
			offered[0] = !result(menu).isEmpty();
		});
		helper.assertTrue(offered[0], "the anvil offered no result");
		helper.assertTrue(!pickable[0], "the result was takeable with " + (NetherImbue.price(0) - 1) + " emeralds");
		helper.succeed();
	}

	@GameTest
	public void creativeImbuesForNothing(final GameTestHelper helper) {
		Player player = helper.makeMockServerPlayer(GameType.CREATIVE);
		boolean[] pickable = new boolean[1];
		withShare(null, () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR));
			pickable[0] = !result(menu).isEmpty() && menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		});
		helper.assertTrue(pickable[0], "creative could not take the result");
		helper.succeed();
	}

	@GameTest
	public void armorAndABowAreImbuedAndOthersAreRefused(final GameTestHelper helper) {
		Player player = survival(helper);
		List<String> wrong = new ArrayList<>();
		withShare(null, () -> {
			for (ItemStack ok : List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.BOW),
				new ItemStack(Items.CROSSBOW), new ItemStack(Items.DIAMOND_SWORD))) {
				ItemStack out = result(anvil(player, ok, new ItemStack(Items.NETHER_STAR)));
				if (NetherImbue.count(out) != 1) {
					wrong.add("accepted? " + ok.getItem() + " gave " + out);
				}
			}
			for (ItemStack no : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.STICK), new ItemStack(Items.ENCHANTED_BOOK),
				new ItemStack(Items.DIRT), ItemStack.EMPTY)) {
				ItemStack out = result(anvil(player, no, new ItemStack(Items.NETHER_STAR)));
				if (!out.isEmpty()) {
					wrong.add("refused? " + no.getItem() + " gave " + out);
				}
			}
		});
		StellaEquips.LOGGER.info("[BEHAVIOR] test=armorAndABowAreImbuedAndOthersAreRefused RESULT wrong={}", wrong);
		helper.assertTrue(wrong.isEmpty(), "wrong: " + wrong);
		helper.succeed();
	}

	@GameTest
	public void aRefusedPieceKeepsItsStar(final GameTestHelper helper) {
		Player player = survival(helper);
		int[] numbers = new int[3];
		withShare(null, () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.STICK), new ItemStack(Items.NETHER_STAR, 2));
			numbers[0] = result(menu).isEmpty() ? 0 : 1;
			numbers[1] = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount();
			// The control: the same stars on a sword give a result, so the refusal above is the piece and not the stars.
			numbers[2] = result(anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR, 2))).isEmpty() ? 0 : 1;
		});
		helper.assertTrue(numbers[2] == 1, "the control sword gave no result");
		helper.assertTrue(numbers[0] == 0 && numbers[1] == 2, "result " + numbers[0] + " stars " + numbers[1]);
		helper.succeed();
	}

	@GameTest
	public void theStatGrantShareGetsTenOnAllSixStatsOnTheResult(final GameTestHelper helper) {
		Player player = survival(helper);
		List<Map<String, Integer>> deltas = new ArrayList<>();
		ItemStack[] out = new ItemStack[1];
		withShare(recorder(deltas), () -> out[0] = take(anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR)), player));
		Map<String, Integer> expected = new LinkedHashMap<>();
		for (String stat : List.of("str", "agi", "vit", "dex", "int", "luk")) {
			expected.put(stat, 10);
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=theStatGrantShareGetsTenOnAllSixStatsOnTheResult RESULT calls={} last={} str={}",
			deltas.size(), deltas.isEmpty() ? null : deltas.get(deltas.size() - 1), standIn(out[0], "str"));
		helper.assertTrue(!deltas.isEmpty() && deltas.get(deltas.size() - 1).equals(expected), "the share saw " + deltas);
		helper.assertTrue(NetherImbue.STAT_PER_IMBUE == 10, "the amount is " + NetherImbue.STAT_PER_IMBUE);
		helper.assertTrue(standIn(out[0], "luk") == 10, "the grant did not reach the result stack: " + out[0]);
		helper.succeed();
	}

	@GameTest
	public void theGrantStacksAcrossImbues(final GameTestHelper helper) {
		Player player = survival(helper);
		List<Map<String, Integer>> deltas = new ArrayList<>();
		ItemStack[] out = new ItemStack[1];
		withShare(recorder(deltas), () -> {
			ItemStack piece = new ItemStack(Items.IRON_CHESTPLATE);
			for (int i = 0; i < 3; i++) {
				piece = take(anvil(player, piece, new ItemStack(Items.NETHER_STAR)), player);
			}
			out[0] = piece;
		});
		StellaEquips.LOGGER.info("[BEHAVIOR] test=theGrantStacksAcrossImbues RESULT count={} str={} vit={}",
			NetherImbue.count(out[0]), standIn(out[0], "str"), standIn(out[0], "vit"));
		helper.assertTrue(NetherImbue.count(out[0]) == 3 && standIn(out[0], "str") == 30 && standIn(out[0], "vit") == 30,
			"count " + NetherImbue.count(out[0]) + " str " + standIn(out[0], "str"));
		helper.succeed();
	}

	@GameTest
	public void withoutTheShareTheImbueStillWorks(final GameTestHelper helper) {
		Player player = survival(helper);
		ItemStack[] out = new ItemStack[1];
		withShare(null, () -> out[0] = take(anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR)), player));
		StellaEquips.LOGGER.info("[BEHAVIOR] test=withoutTheShareTheImbueStillWorks RESULT count={} str={}", NetherImbue.count(out[0]), standIn(out[0], "str"));
		helper.assertTrue(NetherImbue.count(out[0]) == 1 && standIn(out[0], "str") == 0, "count " + NetherImbue.count(out[0]));
		helper.succeed();
	}

	@GameTest
	public void aShareThatThrowsDoesNotStopTheImbue(final GameTestHelper helper) {
		Player player = survival(helper);
		ItemStack[] out = new ItemStack[1];
		withShare(args -> {
			throw new IllegalStateException("stand-in failure");
		}, () -> out[0] = take(anvil(player, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.NETHER_STAR)), player));
		helper.assertTrue(NetherImbue.count(out[0]) == 1, "count " + NetherImbue.count(out[0]));
		helper.succeed();
	}

	@GameTest
	public void refineAndImbueKeepEachOther(final GameTestHelper helper) {
		Player player = survival(helper);
		ItemStack[] out = new ItemStack[2];
		withShare(null, () -> {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			Refine.setLevel(sword, 3);
			out[0] = take(anvil(player, sword, new ItemStack(Items.NETHER_STAR)), player);
			ItemStack again = out[0].copy();
			Refine.setLevel(again, 4);
			out[1] = again;
		});
		List<String> first = lore(out[0]);
		List<String> second = lore(out[1]);
		StellaEquips.LOGGER.info("[BEHAVIOR] test=refineAndImbueKeepEachOther RESULT afterImbue={} level={} count={} afterRefine={} level={} count={}",
			first, Refine.level(out[0]), NetherImbue.count(out[0]), second, Refine.level(out[1]), NetherImbue.count(out[1]));
		helper.assertTrue(Refine.level(out[0]) == 3 && NetherImbue.count(out[0]) == 1, "imbue lost the refine or the count");
		helper.assertTrue(first.contains("Refine +3") && first.contains("Nether Star x1"), "lore after imbue " + first);
		helper.assertTrue(Refine.level(out[1]) == 4 && NetherImbue.count(out[1]) == 1, "refine lost the count");
		helper.assertTrue(second.contains("Refine +4") && second.contains("Nether Star x1")
			&& second.stream().filter(line -> line.startsWith("Nether Star")).count() == 1, "lore after refine " + second);
		helper.succeed();
	}

	@GameTest
	public void theLoreLineIsRewrittenNotRepeated(final GameTestHelper helper) {
		Player player = survival(helper);
		ItemStack[] out = new ItemStack[1];
		withShare(null, () -> {
			ItemStack piece = new ItemStack(Items.IRON_SWORD);
			piece.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("a foreign line"))));
			for (int i = 0; i < 2; i++) {
				piece = take(anvil(player, piece, new ItemStack(Items.NETHER_STAR)), player);
			}
			out[0] = piece;
		});
		List<String> lore = lore(out[0]);
		helper.assertTrue(lore.equals(List.of("a foreign line", "Nether Star x2")), "lore " + lore);
		helper.succeed();
	}

	/**
	 * The equips run loads no rpg jar, so the grant is proved against a stand-in share only.
	 * Oniichan checks the pair by hand: put both jars in one instance, imbue a sword once at an anvil,
	 * hold it, and read +10 on every stat of the stat screen. A second imbue reads +20.
	 */
	@GameTest(required = false)
	public void realRpgJarGrantNeedsManualCheck(final GameTestHelper helper) {
		helper.fail("this run loads no rpg jar; imbue a sword with both mods installed and read +10 on all six stats");
	}
}
