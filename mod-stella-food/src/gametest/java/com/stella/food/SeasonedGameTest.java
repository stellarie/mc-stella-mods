package com.stella.food;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;

/**
 * A dish infused with a potion carries one random stat bonus. The bonus is rolled once at the station, stored
 * on the stack, shown in the tooltip, and eaten as a {@code seasoned_<stat>} effect the RPG mod reads.
 */
public final class SeasonedGameTest {
	private static final String LANG_PATH = "/assets/mod-stella-food/lang/en_us.json";

	@GameTest
	public void effectsRegistered(final GameTestHelper helper) {
		FoodTestLog.track(helper, "effectsRegistered");
		helper.assertTrue(Seasoning.STATS.equals(List.of("str", "agi", "vit", "dex", "int", "luk")),
			"the seasoned stats are " + Seasoning.STATS);
		for (String stat : Seasoning.STATS) {
			Identifier id = StellaFood.id("seasoned_" + stat);
			Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
			helper.assertTrue(effect != null, id + " is not registered");
			helper.assertTrue(effect.value().isBeneficial(), id + " is not a beneficial effect");
			int[] modifiers = {0};
			effect.value().createModifiers(9, (attribute, modifier) -> modifiers[0]++);
			helper.assertTrue(modifiers[0] == 0, id + " carries " + modifiers[0] + " attribute modifiers");
			helper.assertTrue(FoodEffects.SEASONED.get(stat) == effect, id + " is not the effect the code rolls");
		}
		helper.succeed();
	}

	@GameTest
	public void infusionRollsOnce(final GameTestHelper helper) {
		FoodTestLog.track(helper, "infusionRollsOnce");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(FoodItems.HUNTER_STEW));
		menu.getSlot(2).set(PotionContents.createItemStack(Items.POTION, Potions.POISON));
		menu.slotsChanged(menu.getSlot(1).container);
		Seasoning first = Seasoning.read(menu.getSlot(0).getItem());
		helper.assertTrue(first != null, "the station output carries no seasoning");
		helper.assertTrue(Seasoning.STATS.contains(first.stat()), "the rolled stat is " + first.stat());
		helper.assertTrue(first.amount() >= Seasoning.MIN_AMOUNT && first.amount() <= Seasoning.MAX_AMOUNT,
			"the rolled amount is " + first.amount());

		ItemStack preview = menu.getSlot(0).getItem();
		helper.assertTrue(first.equals(Seasoning.read(preview)) && first.equals(Seasoning.read(preview)),
			"two reads of one stack gave different bonuses");
		for (int refresh = 0; refresh < 20; refresh++) {
			menu.slotsChanged(menu.getSlot(1).container);
			helper.assertTrue(first.equals(Seasoning.read(menu.getSlot(0).getItem())),
				"refreshing the grid rerolled the bonus");
		}
		ItemStack potion = menu.getSlot(2).getItem();
		for (int swap = 0; swap < 20; swap++) {
			menu.getSlot(2).set(ItemStack.EMPTY);
			menu.getSlot(2).set(potion.copy());
			helper.assertTrue(first.equals(Seasoning.read(menu.getSlot(0).getItem())),
				"taking the potion out and putting it back rerolled the bonus");
		}

		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack taken = menu.getCarried();
		helper.assertTrue(first.equals(Seasoning.read(taken)), "the taken dish differs from the previewed bonus: "
			+ Seasoning.read(taken) + " against " + first);
		ItemStack copy = taken.copy();
		helper.assertTrue(first.equals(Seasoning.read(copy)) && first.equals(Seasoning.read(taken)),
			"a later read of the taken dish gave another bonus");
		helper.succeed();
	}

	@GameTest
	public void rollCoversEveryStatAndAmount(final GameTestHelper helper) {
		FoodTestLog.track(helper, "rollCoversEveryStatAndAmount");
		Set<String> stats = new HashSet<>();
		Set<Integer> amounts = new HashSet<>();
		for (int i = 0; i < 600; i++) {
			Seasoning roll = Seasoning.roll(helper.getLevel().getRandom());
			stats.add(roll.stat());
			amounts.add(roll.amount());
		}
		helper.assertTrue(stats.size() == Seasoning.STATS.size(), "600 rolls reached only " + stats);
		helper.assertTrue(amounts.equals(Set.of(5, 6, 7, 8, 9, 10)), "600 rolls reached the amounts " + amounts);
		helper.succeed();
	}

	@GameTest
	public void eatingApplies(final GameTestHelper helper) {
		FoodTestLog.track(helper, "eatingApplies");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack dish = new ItemStack(FoodItems.MINERS_LUNCH);
		dish.set(DataComponents.POTION_CONTENTS,
			PotionContents.createItemStack(Items.POTION, Potions.POISON).get(DataComponents.POTION_CONTENTS));
		dish.set(StellaFood.SEASONING, new Seasoning("dex", 7));
		consume(helper, player, dish);
		MobEffectInstance seasoned = player.getEffect(effect("seasoned_dex"));
		helper.assertTrue(seasoned != null, "eating did not apply seasoned_dex");
		helper.assertTrue(seasoned.getAmplifier() == 6, "seasoned_dex has amplifier " + seasoned.getAmplifier() + ", not 6");
		helper.assertTrue(seasoned.getDuration() == FoodBehavior.INFUSED_TICKS,
			"seasoned_dex lasts " + seasoned.getDuration() + " ticks");
		for (String other : Seasoning.STATS) {
			helper.assertTrue(other.equals("dex") || player.getEffect(effect("seasoned_" + other)) == null,
				"eating also applied seasoned_" + other);
		}

		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, second);
		menu.getSlot(1).set(new ItemStack(FoodItems.HUNTER_STEW));
		menu.getSlot(2).set(PotionContents.createItemStack(Items.POTION, Potions.POISON));
		menu.slotsChanged(menu.getSlot(1).container);
		menu.clicked(0, 0, ContainerInput.PICKUP, second);
		ItemStack infused = menu.getCarried();
		Seasoning rolled = Seasoning.read(infused);
		helper.assertTrue(rolled != null, "the station dish carries no seasoning");
		consume(helper, second, infused);
		MobEffectInstance eaten = second.getEffect(effect("seasoned_" + rolled.stat()));
		helper.assertTrue(eaten != null && eaten.getAmplifier() == rolled.amount() - 1
				&& eaten.getDuration() == FoodBehavior.INFUSED_TICKS,
			"a station dish rolled " + rolled + " but the effect is " + eaten);
		helper.succeed();
	}

	@GameTest
	public void tooltipLine(final GameTestHelper helper) {
		FoodTestLog.track(helper, "tooltipLine");
		ItemStack dish = new ItemStack(FoodItems.FISHERS_FEAST);
		dish.set(DataComponents.POTION_CONTENTS,
			PotionContents.createItemStack(Items.POTION, Potions.POISON).get(DataComponents.POTION_CONTENTS));
		String plain = tooltip(helper, dish);
		dish.set(StellaFood.SEASONING, new Seasoning("dex", 7));
		String seasoned = tooltip(helper, dish);
		helper.assertTrue(seasoned.contains("Seasoned: +7 DEX"), "the tooltip has no Seasoned line: " + seasoned);
		helper.assertTrue(!plain.contains("Seasoned"), "an infused dish with no bonus shows a Seasoned line: " + plain);
		helper.assertTrue(seasoned.contains("Current Rider") && seasoned.contains("Queasy"),
			"the Seasoned line displaced an existing tooltip line: " + seasoned);
		helper.succeed();
	}

	@GameTest
	public void plainFoodNoBonus(final GameTestHelper helper) {
		FoodTestLog.track(helper, "plainFoodNoBonus");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbstractContainerMenu menu = openStation(helper, player);
		menu.getSlot(1).set(new ItemStack(Items.APPLE));
		menu.getSlot(2).set(new ItemStack(Items.WHEAT));
		menu.getSlot(3).set(new ItemStack(Items.HONEY_BOTTLE));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().is(FoodItems.ORCHARD_TART), "the station did not preview the tart");
		helper.assertTrue(Seasoning.read(menu.getSlot(0).getItem()) == null, "a freshly combined dish carries a bonus");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		ItemStack tart = menu.getCarried().copy();
		helper.assertTrue(tart.is(FoodItems.ORCHARD_TART) && Seasoning.read(tart) == null, "a combined dish carries a bonus");

		for (int slot = 1; slot <= 9; slot++) {
			menu.getSlot(slot).set(ItemStack.EMPTY);
		}
		menu.getSlot(1).set(new ItemStack(Items.BEEF));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().is(Items.COOKED_BEEF) && Seasoning.read(menu.getSlot(0).getItem()) == null,
			"cooked food carries a bonus");

		// A dish infused before seasoning existed has a potion and no component: it eats without a bonus or a crash.
		ItemStack old = new ItemStack(FoodItems.NETHER_SKEWER);
		old.set(DataComponents.POTION_CONTENTS,
			PotionContents.createItemStack(Items.POTION, Potions.POISON).get(DataComponents.POTION_CONTENTS));
		for (ItemStack food : List.of(tart, new ItemStack(Items.APPLE), old)) {
			ServerPlayer eater = helper.makeMockServerPlayerInLevel();
			consume(helper, eater, food);
			for (String stat : Seasoning.STATS) {
				helper.assertTrue(eater.getEffect(effect("seasoned_" + stat)) == null,
					food.getItem() + " applied seasoned_" + stat);
			}
		}
		helper.assertTrue(!tooltip(helper, old).contains("Seasoned"), "an old infused dish shows a Seasoned line");
		helper.succeed();
	}

	private static void consume(final GameTestHelper helper, final ServerPlayer player, final ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, stack.getItem() + " has no consumable component");
		consumable.onConsume(helper.getLevel(), player, stack);
	}

	private static Holder<MobEffect> effect(final String path) {
		return BuiltInRegistries.MOB_EFFECT.get(StellaFood.id(path)).orElseThrow();
	}

	private static AbstractContainerMenu openStation(final GameTestHelper helper, final ServerPlayer player) {
		BlockPos relative = new BlockPos(2, 1, 2);
		helper.setBlock(relative, CookingStationBlock.BLOCK);
		BlockPos absolute = helper.absolutePos(relative);
		player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		helper.useBlock(relative, player);
		helper.assertTrue(player.containerMenu != player.inventoryMenu, "station menu did not open");
		return player.containerMenu;
	}

	private static String tooltip(final GameTestHelper helper, final ItemStack stack) {
		Language previous = Language.getInstance();
		try {
			installLanguage();
			List<Component> lines = stack.getTooltipLines(
				Item.TooltipContext.of(helper.getLevel().registryAccess()), null, TooltipFlag.NORMAL
			);
			return String.join(" ", lines.stream().map(Component::getString).toList());
		} finally {
			Language.inject(previous);
		}
	}

	private static void installLanguage() {
		Map<String, String> shipped = new HashMap<>();
		try (InputStream stream = SeasonedGameTest.class.getResourceAsStream(LANG_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("missing resource " + LANG_PATH);
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				shipped.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (IOException error) {
			throw new IllegalStateException("cannot load " + LANG_PATH, error);
		}
		Language vanilla = Language.DEFAULT_INSTANCE;
		Language.inject(new Language() {
			@Override
			public String getOrDefault(final String id, final String fallback) {
				return shipped.getOrDefault(id, vanilla.getOrDefault(id, fallback));
			}

			@Override
			public boolean has(final String id) {
				return shipped.containsKey(id) || vanilla.has(id);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return vanilla.isDefaultRightToLeft();
			}

			@Override
			public FormattedCharSequence getVisualOrder(final FormattedText text) {
				return vanilla.getVisualOrder(text);
			}
		});
	}
}
