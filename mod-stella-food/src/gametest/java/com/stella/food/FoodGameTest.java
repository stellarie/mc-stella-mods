package com.stella.food;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class FoodGameTest {
	private static final String MOD_ID = "mod-stella-food";
	private static final int ORDINARY_TICKS = 6_000;
	private static final int DISH_TICKS = 36_000;
	private static final int INFUSED_TICKS = 54_000;
	private static final List<DishExpectation> DISHES = List.of(
		new DishExpectation("hunter_stew", "surefooted"),
		new DishExpectation("miners_lunch", "deep_breath"),
		new DishExpectation("orchard_tart", "light_step"),
		new DishExpectation("fishers_feast", "current_rider"),
		new DishExpectation("nether_skewer", "ember_skin"),
		new DishExpectation("golden_supper", "second_wind")
	);

	@GameTest
	public void vanillaFoodWorksAtFullHungerAndRegenerates(final GameTestHelper helper) {
		FoodTestLog.track(helper, "vanillaFoodWorksAtFullHungerAndRegenerates");
		ServerPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack apple = new ItemStack(Items.APPLE);
		Consumable consumable = apple.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, "apple has no consumable component");
		helper.assertTrue(consumable.canConsume(player, apple), "full hunger rejected a vanilla food");
		consumable.onConsume(helper.getLevel(), player, apple);
		MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
		helper.assertTrue(
			regeneration != null && regeneration.getAmplifier() == 0 && regeneration.getDuration() == 40,
			"vanilla food did not grant Regeneration I for two seconds"
		);
		helper.succeed();
	}

	@GameTest
	public void moddedDishWorksAtFullHunger(final GameTestHelper helper) {
		FoodTestLog.track(helper, "moddedDishWorksAtFullHunger");
		ServerPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack dish = item("hunter_stew").getDefaultInstance();
		Consumable consumable = dish.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, "Hunter Stew has no consumable component");
		helper.assertTrue(consumable.canConsume(player, dish), "full hunger rejected a modded food");
		helper.succeed();
	}

	@GameTest
	public void ordinaryModdedFoodWorksAtFullHungerAndGetsNourished(final GameTestHelper helper) {
		FoodTestLog.track(helper, "ordinaryModdedFoodWorksAtFullHungerAndGetsNourished");
		ServerPlayer player = player(helper);
		player.getFoodData().setFoodLevel(20);
		ItemStack food = FoodTestMod.PLAIN_FOOD.getDefaultInstance();
		Consumable consumable = food.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, "test-mod food has no consumable component");
		helper.assertTrue(consumable.canConsume(player, food), "full hunger rejected an ordinary modded food");
		consumable.onConsume(helper.getLevel(), player, food);
		MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
		MobEffectInstance nourished = player.getEffect(effect("nourished"));
		helper.assertTrue(
			regeneration != null && regeneration.getDuration() == 40,
			"ordinary modded food did not grant two-second regeneration"
		);
		helper.assertTrue(
			nourished != null && nourished.getDuration() == ORDINARY_TICKS,
			"ordinary modded food did not receive the Nourished fallback"
		);
		helper.succeed();
	}

	@GameTest
	public void cakeAndCandleCakeWorkAtFullHunger(final GameTestHelper helper) {
		FoodTestLog.track(helper, "cakeAndCandleCakeWorkAtFullHunger");
		ServerPlayer cakeEater = player(helper);
		cakeEater.getFoodData().setFoodLevel(20);
		BlockPos cakePos = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.CAKE);
		InteractionResult cakeResult = useWithoutItem(helper, cakeEater, cakePos);
		helper.assertTrue(cakeResult.consumesAction(), "full hunger rejected a cake slice");
		helper.assertTrue(
			helper.getLevel().getBlockState(cakePos).getValue(CakeBlock.BITES) == 1,
			"cake bite state did not advance"
		);
		assertCakeEffects(helper, cakeEater, "cake");

		ServerPlayer candleEater = player(helper);
		candleEater.getFoodData().setFoodLevel(20);
		BlockPos candlePos = helper.absolutePos(new BlockPos(4, 1, 2));
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.CANDLE_CAKE);
		InteractionResult candleResult = useWithoutItem(helper, candleEater, candlePos);
		helper.assertTrue(candleResult.consumesAction(), "full hunger rejected a candle-cake slice");
		helper.assertTrue(
			helper.getLevel().getBlockState(candlePos).is(Blocks.CAKE)
				&& helper.getLevel().getBlockState(candlePos).getValue(CakeBlock.BITES) == 1,
			"candle cake did not become a bitten cake"
		);
		assertCakeEffects(helper, candleEater, "candle cake");
		helper.succeed();
	}

	@GameTest
	public void ordinaryFoodsReceiveCategoryEffects(final GameTestHelper helper) {
		FoodTestLog.track(helper, "ordinaryFoodsReceiveCategoryEffects");
		List<CategoryExpectation> expectations = List.of(
			new CategoryExpectation(Items.BEEF, "steady_hand"),
			new CategoryExpectation(Items.COD, "river_sense"),
			new CategoryExpectation(Items.APPLE, "quick_step"),
			new CategoryExpectation(Items.CARROT, "sturdy"),
			new CategoryExpectation(Items.BREAD, "endurance"),
			new CategoryExpectation(Items.COOKIE, "sugar_rush"),
			new CategoryExpectation(Items.ROTTEN_FLESH, "wild_taste"),
			new CategoryExpectation(Items.GOLDEN_CARROT, "nourished")
		);
		for (CategoryExpectation expected : expectations) {
			ServerPlayer player = player(helper);
			consume(helper, player, new ItemStack(expected.food()));
			MobEffectInstance effect = player.getEffect(effect(expected.effect()));
			helper.assertTrue(
				effect != null && effect.getAmplifier() == 0 && effect.getDuration() == ORDINARY_TICKS,
				expected.food() + " did not grant " + expected.effect() + " for five minutes"
			);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void ordinaryBuffsChangeMovementKnockbackAndExhaustion(final GameTestHelper helper) {
		FoodTestLog.track(helper, "ordinaryBuffsChangeMovementKnockbackAndExhaustion");
		ServerPlayer steady = player(helper);
		steady.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK).setBaseValue(1.0);
		consume(helper, steady, new ItemStack(Items.BEEF));
		helper.assertTrue(
			steady.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK) < 1.0,
			"Steady Hand did not reduce attack knockback"
		);

		ServerPlayer swimmer = player(helper);
		consume(helper, swimmer, new ItemStack(Items.COD));
		helper.assertTrue(
			swimmer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.WATER_MOVEMENT_EFFICIENCY) > 0.0,
			"River Sense did not improve water movement"
		);

		ServerPlayer runner = player(helper);
		double baseSpeed = runner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		consume(helper, runner, new ItemStack(Items.APPLE));
		helper.assertTrue(
			runner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > baseSpeed,
			"Quick Step did not improve movement speed"
		);

		ServerPlayer sturdy = player(helper);
		consume(helper, sturdy, new ItemStack(Items.CARROT));
		helper.assertTrue(
			sturdy.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) >= 0.10,
			"Sturdy did not improve knockback resistance"
		);

		ServerPlayer enduring = player(helper);
		consume(helper, enduring, new ItemStack(Items.BREAD));
		float saturationBefore = enduring.getFoodData().getSaturationLevel();
		enduring.causeFoodExhaustion(5.0F);
		enduring.getFoodData().tick(enduring);
		helper.assertTrue(
			enduring.getFoodData().getSaturationLevel() == saturationBefore,
			"Endurance did not reduce exhaustion by twenty percent"
		);
		helper.succeed();
	}

	@GameTest
	public void dishesGrantDistinctThirtyMinuteEffects(final GameTestHelper helper) {
		FoodTestLog.track(helper, "dishesGrantDistinctThirtyMinuteEffects");
		for (DishExpectation expected : DISHES) {
			ServerPlayer player = player(helper);
			consume(helper, player, item(expected.item()).getDefaultInstance());
			MobEffectInstance effect = player.getEffect(effect(expected.effect()));
			helper.assertTrue(
				effect != null && effect.getAmplifier() == 0 && effect.getDuration() == DISH_TICKS,
				expected.item() + " did not grant " + expected.effect() + " for thirty minutes"
			);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void dishBuffsChangeFallFireAirAndSecondWind(final GameTestHelper helper) throws ReflectiveOperationException {
		FoodTestLog.track(helper, "dishBuffsChangeFallFireAirAndSecondWind");
		ServerPlayer light = player(helper);
		consume(helper, light, item("orchard_tart").getDefaultInstance());
		helper.assertTrue(
			light.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER) < 1.0,
			"Light Step did not reduce fall damage"
		);

		ServerPlayer breath = player(helper);
		consume(helper, breath, item("miners_lunch").getDefaultInstance());
		java.lang.reflect.Method decreaseAir = net.minecraft.world.entity.LivingEntity.class
			.getDeclaredMethod("decreaseAirSupply", int.class);
		decreaseAir.setAccessible(true);
		int remainingAir = (int) decreaseAir.invoke(breath, 100);
		helper.assertTrue(remainingAir == 100, "Deep Breath did not delay air loss");

		LivingEntity ember = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 2).withNoFreeWill().spawn();
		consume(helper, ember, item("nether_skewer").getDefaultInstance());
		ember.removeEffect(MobEffects.REGENERATION);
		ember.hurtServer(helper.getLevel(), ember.damageSources().onFire(), 10.0F);
		helper.assertTrue(
			Math.abs(ember.getHealth() - 14.0F) < 0.001F,
			"Ember Skin did not reduce fire damage: health=" + ember.getHealth()
				+ " effect=" + ember.getEffect(effect("ember_skin"))
		);

		ServerPlayer wind = player(helper);
		consume(helper, wind, item("golden_supper").getDefaultInstance());
		wind.removeEffect(MobEffects.REGENERATION);
		wind.setHealth(9.0F);
		wind.doTick();
		helper.assertTrue(wind.getHealth() == 13.0F, "Second Wind did not heal the first low-health event");
		wind.setHealth(9.0F);
		wind.doTick();
		helper.assertTrue(wind.getHealth() == 9.0F, "Second Wind healed more than once");
		helper.succeed();
	}

	@GameTest
	public void stationCombinesAllSixDishRecipes(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationCombinesAllSixDishRecipes");
		List<RecipeExpectation> recipes = List.of(
			new RecipeExpectation("hunter_stew", List.of(Items.COOKED_BEEF, Items.BROWN_MUSHROOM, Items.CARROT)),
			new RecipeExpectation("miners_lunch", List.of(Items.BAKED_POTATO, Items.COOKED_CHICKEN, Items.BEETROOT)),
			new RecipeExpectation("orchard_tart", List.of(Items.APPLE, Items.WHEAT, Items.HONEY_BOTTLE)),
			new RecipeExpectation("fishers_feast", List.of(Items.COOKED_SALMON, Items.DRIED_KELP, Items.POTATO)),
			new RecipeExpectation("nether_skewer", List.of(Items.COOKED_PORKCHOP, Items.WARPED_FUNGUS, Items.BLAZE_POWDER)),
			new RecipeExpectation("golden_supper", List.of(Items.GOLDEN_CARROT, Items.COOKED_MUTTON, Items.HONEY_BOTTLE))
		);
		for (RecipeExpectation recipe : recipes) {
			ServerPlayer player = player(helper);
			AbstractContainerMenu menu = openStationMenu(helper, player,
				recipe.ingredients().stream().map(ItemStack::new).toArray(ItemStack[]::new));
			ItemStack result = takeStationResult(helper, player, menu);
			helper.assertTrue(
				result.is(item(recipe.output()))
					&& menu.getSlot(1).getItem().isEmpty()
					&& menu.getSlot(2).getItem().isEmpty()
					&& menu.getSlot(3).getItem().isEmpty(),
				"station did not create " + recipe.output()
			);
			player.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void stationUsesVanillaCookedOutput(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationUsesVanillaCookedOutput");
		ServerPlayer player = player(helper);
		AbstractContainerMenu menu = openStationMenu(helper, player, new ItemStack(Items.BEEF));
		ItemStack result = takeStationResult(helper, player, menu);
		helper.assertTrue(result.is(Items.COOKED_BEEF) && menu.getSlot(1).getItem().isEmpty(),
			"station did not cook beef from its grid");
		helper.succeed();
	}

	@GameTest
	public void stationImprovesUnknownFoodOnceAndPreservesData(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationImprovesUnknownFoodOnceAndPreservesData");
		ServerPlayer player = player(helper);
		ItemStack apple = new ItemStack(Items.APPLE);
		apple.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keepsake"));
		FoodProperties before = apple.get(DataComponents.FOOD);
		AbstractContainerMenu menu = openStationMenu(helper, player, apple);
		ItemStack cooked = takeStationResult(helper, player, menu).copy();
		FoodProperties after = cooked.get(DataComponents.FOOD);
		helper.assertTrue(cooked.is(Items.APPLE), "generic cooking changed the source item identity");
		helper.assertTrue(
			after != null && before != null && after.nutrition() > before.nutrition()
				&& after.nutrition() <= before.nutrition() + 2,
			"generic cooking did not apply a bounded nutrition increase"
		);
		helper.assertTrue("Keepsake".equals(cooked.getHoverName().getString()), "generic cooking dropped source item data");
		helper.assertTrue(
			cooked.getOrDefault(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false),
			"generic cooked food has no visible cooked indicator"
		);
		int firstNutrition = after.nutrition();
		menu.setCarried(ItemStack.EMPTY);
		menu.getSlot(1).set(cooked.copy());
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(
			menu.getSlot(0).getItem().isEmpty()
				&& menu.getSlot(1).getItem().get(DataComponents.FOOD).nutrition() == firstNutrition,
			"station improved the same generic cooked variant twice"
		);
		helper.succeed();
	}

	@GameTest
	public void craftingTableMakesAllSixDishesAndRecipesAreDiscoverable(final GameTestHelper helper) {
		FoodTestLog.track(helper, "craftingTableMakesAllSixDishesAndRecipesAreDiscoverable");
		List<RecipeExpectation> recipes = recipeExpectations();
		for (RecipeExpectation recipe : recipes) {
			CraftingInput input = CraftingInput.of(
				3,
				1,
				recipe.ingredients().stream().map(ItemStack::new).toList()
			);
			ItemStack output = helper.getLevel().recipeAccess()
				.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
				.map(holder -> holder.value().assemble(input))
				.orElse(ItemStack.EMPTY);
			helper.assertTrue(output.is(item(recipe.output())), "crafting table did not create " + recipe.output());
			Identifier recipeId = StellaFood.id(recipe.output());
			Identifier advancementId = StellaFood.id("recipes/food/" + recipe.output());
			var advancement = helper.getLevel().getServer().getAdvancements().get(advancementId);
			helper.assertTrue(advancement != null, "missing recipe advancement for " + recipe.output());
			helper.assertTrue(
				advancement.value().rewards().recipes().stream().anyMatch(key -> key.identifier().equals(recipeId)),
				"recipe advancement does not unlock " + recipe.output()
			);
		}
		Identifier stationAdvancementId = StellaFood.id("recipes/decorations/cooking_station");
		helper.assertTrue(
			helper.getLevel().getServer().getAdvancements().get(stationAdvancementId) != null,
			"missing cooking station recipe advancement"
		);
		helper.succeed();
	}

	@GameTest
	public void stationGridRejectsIncompleteDishAndNonFood(final GameTestHelper helper) {
		FoodTestLog.track(helper, "stationGridRejectsIncompleteDishAndNonFood");
		ServerPlayer incomplete = player(helper);
		AbstractContainerMenu partial = openStationMenu(helper, incomplete,
			new ItemStack(Items.APPLE), new ItemStack(Items.WHEAT));
		helper.assertTrue(partial.getSlot(0).getItem().isEmpty()
			&& partial.getSlot(1).getItem().is(Items.APPLE)
			&& partial.getSlot(2).getItem().is(Items.WHEAT),
			"incomplete dish produced output or consumed ingredients");
		ServerPlayer nonFood = player(helper);
		AbstractContainerMenu invalid = openStationMenu(helper, nonFood, new ItemStack(Items.STICK));
		helper.assertTrue(invalid.getSlot(0).getItem().isEmpty(), "non-food input produced station output");
		ServerPlayer fishBucket = player(helper);
		AbstractContainerMenu bucketMenu = openStationMenu(helper, fishBucket, new ItemStack(Items.COD_BUCKET));
		helper.assertTrue(bucketMenu.getSlot(0).getItem().isEmpty(),
			"non-consumable fish bucket produced cooked food");
		helper.succeed();
	}

	@GameTest
	public void infusedDishKeepsDishBuffAndAppliesHarmfulPotion(final GameTestHelper helper) {
		FoodTestLog.track(helper, "infusedDishKeepsDishBuffAndAppliesHarmfulPotion");
		ServerPlayer player = player(helper);
		AbstractContainerMenu menu = openStationMenu(helper, player,
			item("hunter_stew").getDefaultInstance(), PotionContents.createItemStack(Items.POTION, Potions.POISON));
		ItemStack infused = takeStationResult(helper, player, menu);
		helper.assertTrue(infused.has(DataComponents.POTION_CONTENTS), "station did not infuse the dish");
		helper.assertTrue(bottleCount(player, menu) == 1, "station did not return the bottle");
		consume(helper, player, infused);
		MobEffectInstance dish = player.getEffect(effect("surefooted"));
		MobEffectInstance queasy = player.getEffect(effect("queasy"));
		helper.assertTrue(dish != null && dish.getDuration() == INFUSED_TICKS, "infusion did not extend the dish buff");
		// A damaging potion in food becomes a five-minute mild debuff, never poison.
		helper.assertTrue(
			!player.hasEffect(MobEffects.POISON) && queasy != null && queasy.getDuration() == FoodBehavior.MILD_DEBUFF_TICKS,
			"a poison infusion did not become five minutes of Queasy: " + player.getActiveEffects()
		);
		helper.succeed();
	}

	/** A stack of potions infuses one dish and keeps the rest: one potion used, one bottle back. */
	@GameTest
	public void infusionFromStackUsesOnePotion(final GameTestHelper helper) {
		FoodTestLog.track(helper, "infusionFromStackUsesOnePotion");
		ServerPlayer player = player(helper);
		ItemStack potions = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
		potions.setCount(3);
		AbstractContainerMenu menu = openStationMenu(helper, player, item("hunter_stew").getDefaultInstance(), potions);
		ItemStack infused = takeStationResult(helper, player, menu);
		int left = 0;
		for (int slot = 1; slot <= 9; slot++) {
			ItemStack held = menu.getSlot(slot).getItem();
			if (held.is(Items.POTION)) {
				left += held.getCount();
			}
		}
		helper.assertTrue(infused.has(DataComponents.POTION_CONTENTS), "station did not infuse the dish");
		helper.assertTrue(left == 2 && bottleCount(player, menu) == 1, "a stack infusion left potions=" + left + " bottles=" + bottleCount(player, menu));
		helper.succeed();
	}

	@GameTest
	public void infusedInstantPotionAppliesImmediatelyAndOnlyOnce(final GameTestHelper helper) {
		FoodTestLog.track(helper, "infusedInstantPotionAppliesImmediatelyAndOnlyOnce");
		ServerPlayer player = player(helper);
		player.setHealth(10.0F);
		AbstractContainerMenu menu = openStationMenu(helper, player,
			item("orchard_tart").getDefaultInstance(), PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		ItemStack infused = takeStationResult(helper, player, menu).copy();
		PotionContents first = infused.get(DataComponents.POTION_CONTENTS);
		menu.setCarried(ItemStack.EMPTY);
		menu.getSlot(1).set(infused.copy());
		menu.getSlot(2).set(PotionContents.createItemStack(Items.POTION, Potions.POISON));
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "station allowed a second infusion");
		helper.assertTrue(first.equals(menu.getSlot(1).getItem().get(DataComponents.POTION_CONTENTS)),
			"station replaced an existing infusion");
		helper.assertTrue(menu.getSlot(2).getItem().is(Items.POTION), "station consumed a second potion");
		consume(helper, player, infused);
		helper.assertTrue(player.getHealth() > 10.0F, "instant healing did not apply immediately");
		helper.assertTrue(player.getEffect(MobEffects.INSTANT_HEALTH) == null, "instant healing became a timed effect");
		helper.succeed();
	}

	@GameTest
	public void regularSplashAndLingeringPotionsInfuse(final GameTestHelper helper) {
		FoodTestLog.track(helper, "regularSplashAndLingeringPotionsInfuse");
		for (Item potionItem : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
			ServerPlayer player = player(helper);
			ItemStack potion = PotionContents.createItemStack(potionItem, Potions.POISON);
			PotionContents expected = potion.get(DataComponents.POTION_CONTENTS);
			AbstractContainerMenu menu = openStationMenu(helper, player,
				item("hunter_stew").getDefaultInstance(), potion);
			ItemStack infused = takeStationResult(helper, player, menu);
			helper.assertTrue(
				expected.equals(infused.get(DataComponents.POTION_CONTENTS)),
				potionItem + " did not infuse the dish"
			);
			helper.assertTrue(bottleCount(player, menu) == 1, potionItem + " did not return a bottle");
			player.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void nonFoodItemsRemainNonConsumable(final GameTestHelper helper) {
		FoodTestLog.track(helper, "nonFoodItemsRemainNonConsumable");
		ItemStack stick = new ItemStack(Items.STICK);
		helper.assertTrue(stick.get(DataComponents.FOOD) == null, "stick became food");
		helper.assertTrue(stick.get(DataComponents.CONSUMABLE) == null, "stick became consumable");
		helper.succeed();
	}

	private static void consume(final GameTestHelper helper, final LivingEntity player, final ItemStack stack) {
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		helper.assertTrue(consumable != null, stack.getItem() + " has no consumable component");
		consumable.onConsume(helper.getLevel(), player, stack);
	}

	private static AbstractContainerMenu openStationMenu(
		final GameTestHelper helper,
		final ServerPlayer player,
		final ItemStack... inputs
	) {
		Block station = block("cooking_station");
		BlockPos relative = new BlockPos(2, 1, 2);
		helper.setBlock(relative, station);
		BlockPos absolute = helper.absolutePos(relative);
		player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		helper.useBlock(relative, player);
		helper.assertTrue(player.containerMenu != player.inventoryMenu, "station menu did not open");
		AbstractContainerMenu menu = player.containerMenu;
		for (int index = 0; index < inputs.length; index++) {
			menu.getSlot(index + 1).set(inputs[index]);
		}
		menu.slotsChanged(menu.getSlot(1).container);
		return menu;
	}

	private static ItemStack takeStationResult(
		final GameTestHelper helper,
		final ServerPlayer player,
		final AbstractContainerMenu menu
	) {
		helper.assertTrue(!menu.getSlot(0).getItem().isEmpty(), "station grid has no result");
		menu.clicked(0, 0, ContainerInput.PICKUP, player);
		return menu.getCarried();
	}

	private static int bottleCount(final ServerPlayer player, final AbstractContainerMenu menu) {
		int bottles = player.getInventory().countItem(Items.GLASS_BOTTLE);
		for (int input = 1; input <= 9; input++) {
			if (menu.getSlot(input).getItem().is(Items.GLASS_BOTTLE)) {
				bottles++;
			}
		}
		return bottles;
	}

	private static InteractionResult useWithoutItem(
		final GameTestHelper helper,
		final ServerPlayer player,
		final BlockPos pos
	) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
	}

	private static void assertCakeEffects(
		final GameTestHelper helper,
		final ServerPlayer player,
		final String source
	) {
		MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
		MobEffectInstance sugarRush = player.getEffect(effect("sugar_rush"));
		helper.assertTrue(
			regeneration != null && regeneration.getDuration() == 40,
			source + " did not grant Regeneration I for two seconds"
		);
		helper.assertTrue(
			sugarRush != null && sugarRush.getDuration() == ORDINARY_TICKS,
			source + " did not grant Sugar Rush for five minutes"
		);
	}

	private static List<RecipeExpectation> recipeExpectations() {
		return List.of(
			new RecipeExpectation("hunter_stew", List.of(Items.COOKED_BEEF, Items.BROWN_MUSHROOM, Items.CARROT)),
			new RecipeExpectation("miners_lunch", List.of(Items.BAKED_POTATO, Items.COOKED_CHICKEN, Items.BEETROOT)),
			new RecipeExpectation("orchard_tart", List.of(Items.APPLE, Items.WHEAT, Items.HONEY_BOTTLE)),
			new RecipeExpectation("fishers_feast", List.of(Items.COOKED_SALMON, Items.DRIED_KELP, Items.POTATO)),
			new RecipeExpectation("nether_skewer", List.of(Items.COOKED_PORKCHOP, Items.WARPED_FUNGUS, Items.BLAZE_POWDER)),
			new RecipeExpectation("golden_supper", List.of(Items.GOLDEN_CARROT, Items.COOKED_MUTTON, Items.HONEY_BOTTLE))
		);
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().invulnerable = false;
		return player;
	}

	private static Item item(final String path) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
		return BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
	}

	private static Block block(final String path) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
		return BuiltInRegistries.BLOCK.get(id).map(Holder.Reference::value).orElse(net.minecraft.world.level.block.Blocks.AIR);
	}

	private static Holder<MobEffect> effect(final String path) {
		Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, path);
		return BuiltInRegistries.MOB_EFFECT.get(id).orElseThrow();
	}

	private record CategoryExpectation(Item food, String effect) {
	}

	private record DishExpectation(String item, String effect) {
	}

	private record RecipeExpectation(String output, List<Item> ingredients) {
	}
}
