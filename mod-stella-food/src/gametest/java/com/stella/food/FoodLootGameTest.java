package com.stella.food;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Container dish rolls: 10% combined, 5% tipped with a potion and a seasoning, in searchable containers only. */
public final class FoodLootGameTest {
	private static final int SAMPLES = 200_000;

	@GameTest
	public void chances(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261001L);
		int plain = 0;
		int tipped = 0;
		for (int i = 0; i < SAMPLES; i++) {
			for (ItemStack stack : FoodLoot.roll(random)) {
				if (stack.has(DataComponents.POTION_CONTENTS)) {
					tipped++;
				} else {
					plain++;
				}
			}
		}
		boolean dishOk = within(plain, FoodLoot.DISH_CHANCE);
		boolean tippedOk = within(tipped, FoodLoot.TIPPED_CHANCE);
		StellaFood.LOGGER.info("[BEHAVIOR] test=chances RESULT plain={} tipped={} of={}", plain, tipped, SAMPLES);
		helper.assertTrue(dishOk && tippedOk, "dish rate " + plain + " or tipped rate " + tipped + " is off");
		helper.succeed();
	}

	@GameTest
	public void tippedDish(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(7L);
		List<String> bad = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			ItemStack dish = FoodLoot.tipped(random);
			if (!FoodItems.isDish(dish.getItem()) || !dish.has(DataComponents.POTION_CONTENTS) || Seasoning.read(dish) == null) {
				bad.add(dish.toString());
			}
		}
		StellaFood.LOGGER.info("[BEHAVIOR] test=tippedDish RESULT bad={} sample={}", bad.size(), FoodLoot.tipped(random));
		helper.assertTrue(bad.isEmpty(), "tipped dishes missing a potion or a seasoning: " + bad);
		helper.succeed();
	}

	@GameTest
	public void containerMatcher(final GameTestHelper helper) {
		Map<String, Boolean> cases = new LinkedHashMap<>();
		cases.put("minecraft:chests/simple_dungeon", true);
		cases.put("minecraft:pots/trial_chambers/corridor", true);
		cases.put("repurposed_structures:barrels/mansion", true);
		cases.put("minecraft:blocks/chest", false);
		cases.put("minecraft:entities/cow", false);
		cases.put("minecraft:gameplay/fishing", false);
		cases.put("minecraft:chests/jungle_temple_dispenser", false);
		List<String> wrong = new ArrayList<>();
		cases.forEach((id, expected) -> {
			if (ContainerTables.targets(Identifier.parse(id)) != expected) {
				wrong.add(id);
			}
		});
		StellaFood.LOGGER.info("[BEHAVIOR] test=containerMatcher RESULT wrong={}", wrong);
		helper.assertTrue(wrong.isEmpty(), "the container matcher misjudged " + wrong);
		helper.succeed();
	}

	private static boolean within(final int hits, final double chance) {
		double expected = SAMPLES * chance;
		double sigma = Math.sqrt(SAMPLES * chance * (1.0 - chance));
		return Math.abs(hits - expected) <= 3.0 * sigma;
	}
}
