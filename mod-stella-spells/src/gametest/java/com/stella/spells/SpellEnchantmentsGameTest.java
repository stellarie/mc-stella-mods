package com.stella.spells;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SpellEnchantmentsGameTest {
	private static final String MOD_ID = "mod-stella-spells";
	private static final String EQUIPS_MOD_ID = "mod-stella-equips-enchants";
	private static final Identifier QUICKENING = spellEnchantment("quickening");
	private static final Identifier POTENCY = spellEnchantment("potency");
	private static final Identifier PRECISION = spellEnchantment("precision");
	private static final Identifier ARCANE_SIGHT = spellEnchantment("arcane_sight");
	private static final Identifier CLEAR_MIND = spellEnchantment("clear_mind");
	private static final Identifier ARCANE_WEAVE = spellEnchantment("arcane_weave");
	private static final Identifier SPELLGUARD = spellEnchantment("spellguard");
	private static final Identifier SPARK_SHOT = spellbookId("spark_shot");
	private static final Identifier MENDING_WORD = spellbookId("mending_word");
	private static final Identifier STONEGUARD = spellbookId("stoneguard");
	private static final Identifier FROSTBIND = spellbookId("frostbind");
	private static final Identifier WARDENS_ROAR = spellbookId("wardens_roar");
	private static final List<Identifier> SPELL_ENCHANTMENTS = List.of(
		QUICKENING, POTENCY, PRECISION, ARCANE_SIGHT, CLEAR_MIND, ARCANE_WEAVE, SPELLGUARD
	);

	@GameTest
	public void spellEnchantmentsRespectBookAndArmorSlotRestrictions(final GameTestHelper helper) {
		SpellTestLog.track(helper, "spellEnchantmentsRespectBookAndArmorSlotRestrictions");
		List<Holder.Reference<Enchantment>> enchantments = SPELL_ENCHANTMENTS.stream()
			.map(id -> enchantment(helper, id))
			.toList();
		List<ItemStack> spellbooks = SpellbookCatalog.DEFINITIONS.stream()
			.map(definition -> spellbook(helper, definition.id()).getDefaultInstance())
			.toList();
		ItemStack helmet = Items.DIAMOND_HELMET.getDefaultInstance();
		ItemStack chestplate = Items.DIAMOND_CHESTPLATE.getDefaultInstance();
		ItemStack sword = Items.DIAMOND_SWORD.getDefaultInstance();
		helper.assertTrue(spellbooks.size() == 40, "the catalog must contain 40 spellbooks");

		for (int index = 0; index < enchantments.size(); index++) {
			Enchantment value = enchantments.get(index).value();
			if (index < 3) {
				helper.assertTrue(
					spellbooks.stream().allMatch(value::isSupportedItem)
						&& spellbooks.stream().allMatch(value::isPrimaryItem)
						&& !value.isSupportedItem(helmet) && !value.isSupportedItem(chestplate)
						&& !value.isSupportedItem(sword) && value.matchingSlot(EquipmentSlot.MAINHAND)
						&& !value.matchingSlot(EquipmentSlot.HEAD) && !value.matchingSlot(EquipmentSlot.CHEST),
					SPELL_ENCHANTMENTS.get(index) + " must only support all spellbooks in hand"
				);
			} else if (index < 5) {
				helper.assertTrue(
					value.isSupportedItem(helmet) && value.isPrimaryItem(helmet)
						&& spellbooks.stream().noneMatch(value::isSupportedItem) && !value.isSupportedItem(chestplate)
						&& !value.isSupportedItem(sword)
						&& value.matchingSlot(EquipmentSlot.HEAD) && !value.matchingSlot(EquipmentSlot.CHEST),
					SPELL_ENCHANTMENTS.get(index) + " must only support the head slot"
				);
			} else {
				helper.assertTrue(
					value.isSupportedItem(chestplate) && value.isPrimaryItem(chestplate)
						&& spellbooks.stream().noneMatch(value::isSupportedItem) && !value.isSupportedItem(helmet)
						&& !value.isSupportedItem(sword)
						&& value.matchingSlot(EquipmentSlot.CHEST) && !value.matchingSlot(EquipmentSlot.HEAD),
					SPELL_ENCHANTMENTS.get(index) + " must only support the chest slot"
				);
			}
			helper.assertTrue(value.getMaxLevel() == 3, SPELL_ENCHANTMENTS.get(index) + " must have three levels");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void quickeningAndClearMindApplyCombinedCooldownFormulaAtLevelsIToIII(final GameTestHelper helper) {
		SpellTestLog.track(helper, "quickeningAndClearMindApplyCombinedCooldownFormulaAtLevelsIToIII");
		Holder.Reference<Enchantment> quickening = enchantment(helper, QUICKENING);
		Holder.Reference<Enchantment> clearMind = enchantment(helper, CLEAR_MIND);
		for (int level = 1; level <= 3; level++) {
			assertCooldown(helper, quickening, level, clearMind, 0);
			assertCooldown(helper, quickening, 0, clearMind, level);
			assertCooldown(helper, quickening, level, clearMind, level);
		}
		assertCooldown(helper, quickening, 0, clearMind, 3, EquipmentSlot.CHEST);
		int cooldownFloor = productionCalculation(
			"cooldownTicks", new Class<?>[] {int.class, int.class, int.class}, 100, 6, 6
		);
		int minimumCooldown = productionCalculation(
			"cooldownTicks", new Class<?>[] {int.class, int.class, int.class}, 30, 3, 3
		);
		helper.assertTrue(
			cooldownFloor == 50 && minimumCooldown == 20,
			"synthetic cooldown floor/minimum: floor=" + cooldownFloor + " minimum=" + minimumCooldown
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void potencyScalesActualDamageAndHealingAtLevelsIToIII(final GameTestHelper helper) {
		SpellTestLog.track(helper, "potencyScalesActualDamageAndHealingAtLevelsIToIII");
		Holder.Reference<Enchantment> potency = enchantment(helper, POTENCY);
		helper.setTime(18_000L);
		clearTargetRayBarriers(helper);
		for (int level = 1; level <= 3; level++) {
			ServerPlayer player = addPlayer(helper, 2.5, 2.5);
			Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
			float targetHealth = target.getHealth();
			SpellbookItem sparkShot = spellbook(helper, SPARK_SHOT);
			ItemStack damageBook = withEnchantment(sparkShot.getDefaultInstance(), potency, level);
			player.setItemInHand(InteractionHand.MAIN_HAND, damageBook);
			InteractionResult damageCast = sparkShot.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			double expectedDamage = 3.0 * (1.0 + 0.10 * level);
			helper.assertTrue(
				damageCast == InteractionResult.SUCCESS
					&& Math.abs((targetHealth - target.getHealth()) - expectedDamage) < 0.01,
					"Potency " + level + " damage expected=" + expectedDamage + " actual="
						+ (targetHealth - target.getHealth())
			);
			target.discard();

			player.setHealth(10.0f);
			float healthBefore = player.getHealth();
			SpellbookItem mendingWord = spellbook(helper, MENDING_WORD);
			ItemStack healingBook = withEnchantment(mendingWord.getDefaultInstance(), potency, level);
			player.setItemInHand(InteractionHand.MAIN_HAND, healingBook);
			InteractionResult healingCast = mendingWord.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			double expectedHealing = 4.0 * (1.0 + 0.10 * level);
			helper.assertTrue(
				healingCast == InteractionResult.SUCCESS
					&& Math.abs((player.getHealth() - healthBefore) - expectedHealing) < 0.01,
					"Potency " + level + " healing expected=" + expectedHealing + " actual="
						+ (player.getHealth() - healthBefore)
			);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void precisionScalesActualAreaRadiusAtLevelsIToIII(final GameTestHelper helper) {
		SpellTestLog.track(helper, "precisionScalesActualAreaRadiusAtLevelsIToIII");
		Holder.Reference<Enchantment> precision = enchantment(helper, PRECISION);
		helper.setTime(18_000L);
		for (int level = 1; level <= 3; level++) {
			ServerPlayer player = addPlayer(helper, 2.5, 2.5);
			SpellbookItem roar = spellbook(helper, WARDENS_ROAR);
			ItemStack stack = withEnchantment(roar.getDefaultInstance(), precision, level);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			double radius = 5.0 * (1.0 + 0.10 * level);
			Mob inside = spawnAtX(helper, 2.5 + radius - 0.25);
			Mob outside = spawnAtX(helper, 2.5 + radius + 0.25);
			float insideHealth = inside.getHealth();
			float outsideHealth = outside.getHealth();
			InteractionResult cast = roar.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(
				cast == InteractionResult.SUCCESS && Math.abs((insideHealth - inside.getHealth()) - 6.0f) < 0.01
					&& outside.getHealth() == outsideHealth,
				"Precision " + level + " radius=" + radius + " insideDamage="
					+ (insideHealth - inside.getHealth()) + " outsideDamage=" + (outsideHealth - outside.getHealth())
			);
			inside.discard();
			outside.discard();
		}
		helper.succeed();
	}

	// The line of fire runs about 24 blocks past the arena, so the padding keeps neighbouring arenas off it.
	@GameTest(maxTicks = 200, padding = 32)
	public void arcaneSightAddsTwoBlocksPerLevelOnlyInHeadSlot(final GameTestHelper helper) {
		SpellTestLog.track(helper, "arcaneSightAddsTwoBlocksPerLevelOnlyInHeadSlot");
		Holder.Reference<Enchantment> arcaneSight = enchantment(helper, ARCANE_SIGHT);
		int baseRange = productionBaseRangeBlocks();
		double baseRangeFromCastingTests = SpellbookTestAccess.targetRangeBlocks();
		helper.assertTrue(
			baseRange == baseRangeFromCastingTests,
			"SpellModifiers base range differs from the shared test helper: "
				+ baseRange + " != " + baseRangeFromCastingTests
		);
		helper.setTime(18_000L);
		clearTargetRayBarriers(helper);
		for (int level = 1; level <= 3; level++) {
			ServerPlayer player = addPlayer(helper, 2.5, 2.5);
			ItemStack helmet = withEnchantment(Items.DIAMOND_HELMET.getDefaultInstance(), arcaneSight, level);
			player.setItemSlot(EquipmentSlot.HEAD, helmet);
			double range = baseRange + 2.0 * level;
			Mob target = spawnAtZ(helper, 2.5 + range);
			float healthBefore = target.getHealth();
			SpellbookItem sparkShot = spellbook(helper, SPARK_SHOT);
			ItemStack stack = sparkShot.getDefaultInstance();
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			InteractionResult cast = sparkShot.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
			helper.assertTrue(
				cast == InteractionResult.SUCCESS && target.getHealth() == healthBefore - 3.0f,
				"Arcane Sight " + level + " did not hit at " + range + " blocks"
			);
			target.discard();
		}

		ServerPlayer wrongSlotPlayer = addPlayer(helper, 2.5, 2.5);
		wrongSlotPlayer.setItemSlot(
			EquipmentSlot.CHEST,
			withEnchantment(Items.DIAMOND_CHESTPLATE.getDefaultInstance(), arcaneSight, 3)
		);
		Mob wrongSlotTarget = spawnAtZ(helper, 2.5 + baseRange + 1.0);
		float wrongSlotHealth = wrongSlotTarget.getHealth();
		SpellbookItem sparkShot = spellbook(helper, SPARK_SHOT);
		ItemStack wrongSlotBook = sparkShot.getDefaultInstance();
		wrongSlotPlayer.setItemInHand(InteractionHand.MAIN_HAND, wrongSlotBook);
		sparkShot.use(helper.getLevel(), wrongSlotPlayer, InteractionHand.MAIN_HAND);
		helper.assertTrue(
			wrongSlotTarget.getHealth() == wrongSlotHealth
				&& !wrongSlotPlayer.getCooldowns().isOnCooldown(wrongSlotBook),
			"Arcane Sight in the chest slot changed range or started a cooldown"
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void arcaneWeaveAndSpellguardExtendCorrectTimedEffectsAtLevelsIToIII(final GameTestHelper helper) {
		SpellTestLog.track(helper, "arcaneWeaveAndSpellguardExtendCorrectTimedEffectsAtLevelsIToIII");
		Holder.Reference<Enchantment> arcaneWeave = enchantment(helper, ARCANE_WEAVE);
		Holder.Reference<Enchantment> spellguard = enchantment(helper, SPELLGUARD);
		helper.setTime(18_000L);
		clearTargetRayBarriers(helper);
		for (int level = 1; level <= 3; level++) {
			int extended = Math.round(80.0f * (1.0f + 0.10f * level));
			assertDuration(
				helper, FROSTBIND, arcaneWeave, level, MobEffects.SLOWNESS, extended, false, EquipmentSlot.CHEST
			);
			assertDuration(helper, FROSTBIND, spellguard, level, MobEffects.SLOWNESS, 80, false, EquipmentSlot.CHEST);
			assertDuration(
				helper, STONEGUARD, spellguard, level, MobEffects.RESISTANCE, extended, true, EquipmentSlot.CHEST
			);
			assertDuration(
				helper, STONEGUARD, arcaneWeave, level, MobEffects.RESISTANCE, 80, true, EquipmentSlot.CHEST
			);
		}
		assertDuration(
			helper, FROSTBIND, arcaneWeave, 3, MobEffects.SLOWNESS, 80, false, EquipmentSlot.HEAD
		);
		assertDuration(
			helper, STONEGUARD, spellguard, 3, MobEffects.RESISTANCE, 80, true, EquipmentSlot.HEAD
		);
		int durationAtLevelOne = productionCalculation(
			"durationTicks", new Class<?>[] {int.class, int.class}, 85, 1
		);
		int durationAtLevelThree = productionCalculation(
			"durationTicks", new Class<?>[] {int.class, int.class}, 85, 3
		);
		helper.assertTrue(
			durationAtLevelOne == 94 && durationAtLevelThree == 111,
			"duration tick rounding: level1=" + durationAtLevelOne + " level3=" + durationAtLevelThree
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void enchantingTableOffersAllSevenWithStandaloneAndEquipEnchantPricing(final GameTestHelper helper) {
		SpellTestLog.track(helper, "enchantingTableOffersAllSevenWithStandaloneAndEquipEnchantPricing");
		boolean equipsLoaded = FabricLoader.getInstance().isModLoaded(EQUIPS_MOD_ID);
		for (int index = 0; index < SPELL_ENCHANTMENTS.size(); index++) {
			Holder.Reference<Enchantment> enchantment = enchantment(helper, SPELL_ENCHANTMENTS.get(index));
			assertTableOffer(helper, enchantment, tableCarrier(helper, index), equipsLoaded);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void spellAnvilSupportsZeroCostPickupAndNewOrRaisedEmeraldFees(final GameTestHelper helper) {
		SpellTestLog.track(helper, "spellAnvilSupportsZeroCostPickupAndNewOrRaisedEmeraldFees");
		Holder.Reference<Enchantment> quickening = enchantment(helper, QUICKENING);
		boolean equipsLoaded = FabricLoader.getInstance().isModLoaded(EQUIPS_MOD_ID);
		assertSpellAnvilOperation(helper, quickening, 0, 1, equipsLoaded);
		assertSpellAnvilOperation(helper, quickening, 1, 2, equipsLoaded);
		helper.succeed();
	}

	@GameTest
	public void nonSpellAnvilControlKeepsVanillaCostAndPickup(final GameTestHelper helper) {
		SpellTestLog.track(helper, "nonSpellAnvilControlKeepsVanillaCostAndPickup");
		Holder.Reference<Enchantment> sharpness = enchantment(
			helper, Identifier.withDefaultNamespace("sharpness")
		);
		boolean equipsLoaded = FabricLoader.getInstance().isModLoaded(EQUIPS_MOD_ID);
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setGameMode(GameType.SURVIVAL);
		player.setExperienceLevels(30);
		if (equipsLoaded) {
			player.getInventory().add(new ItemStack(Items.EMERALD, 8));
		}
		AnvilMenu menu = anvilMenu(
			player,
			Items.DIAMOND_SWORD.getDefaultInstance(),
			enchantedBook(sharpness, 1)
		);
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		int cost = menu.getCost();
		int levelsBefore = player.experienceLevel;
		int emeraldsBefore = countItems(player, Items.EMERALD);
		boolean mayPickup = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		helper.assertTrue(
			cost == 1 && !result.isEmpty() && result.is(Items.DIAMOND_SWORD)
				&& EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(sharpness) == 1 && mayPickup,
			"a non-spell anvil operation lost its vanilla result, cost, or pickup rule: cost=" + cost
		);
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
		if (equipsLoaded) {
			helper.assertTrue(
				player.experienceLevel == levelsBefore && countItems(player, Items.EMERALD) == emeraldsBefore - cost,
				"Equip-Enchants non-spell pricing changed: levels=" + player.experienceLevel
					+ " emeralds=" + countItems(player, Items.EMERALD)
			);
		} else {
			helper.assertTrue(
				player.experienceLevel == levelsBefore - cost,
				"vanilla non-spell pickup did not charge its level cost: before=" + levelsBefore
					+ " after=" + player.experienceLevel + " cost=" + cost
			);
		}
		helper.succeed();
	}

	private static void assertCooldown(
		final GameTestHelper helper,
		final Holder<Enchantment> quickening,
		final int quickeningLevel,
		final Holder<Enchantment> clearMind,
		final int clearMindLevel
	) {
		assertCooldown(helper, quickening, quickeningLevel, clearMind, clearMindLevel, EquipmentSlot.HEAD);
	}

	private static void assertCooldown(
		final GameTestHelper helper,
		final Holder<Enchantment> quickening,
		final int quickeningLevel,
		final Holder<Enchantment> clearMind,
		final int clearMindLevel,
		final EquipmentSlot clearMindSlot
	) {
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setGameMode(GameType.SURVIVAL);
		SpellbookItem book = spellbook(helper, STONEGUARD);
		ItemStack stack = book.getDefaultInstance();
		if (quickeningLevel > 0) {
			stack = withEnchantment(stack, quickening, quickeningLevel);
		}
		if (clearMindLevel > 0) {
			Item armor = clearMindSlot == EquipmentSlot.HEAD ? Items.DIAMOND_HELMET : Items.DIAMOND_CHESTPLATE;
			player.setItemSlot(
				clearMindSlot, withEnchantment(armor.getDefaultInstance(), clearMind, clearMindLevel)
			);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		int effectiveClearMind = clearMindSlot == EquipmentSlot.HEAD ? clearMindLevel : 0;
		int baseTicks = book.definition().baseCooldownTicks();
		double reduction = Math.max(0.50, 1.0 - 0.10 * quickeningLevel - 0.05 * effectiveClearMind);
		int expectedTicks = Math.max(20, (int) Math.round(baseTicks * reduction));
		helper.assertTrue(
			result == InteractionResult.SUCCESS && player.getCooldowns().isOnCooldown(stack),
			"the spell did not start a cooldown: quickening=" + quickeningLevel + " clearMind=" + effectiveClearMind
		);
		for (int tick = 1; tick < expectedTicks; tick++) {
			player.doTick();
		}
		helper.assertTrue(
			player.getCooldowns().isOnCooldown(stack),
			"cooldown expired before " + expectedTicks + " ticks"
		);
		player.doTick();
		helper.assertTrue(
			!player.getCooldowns().isOnCooldown(stack),
			"cooldown exceeded " + expectedTicks + " ticks"
		);
	}

	private static void assertDuration(
		final GameTestHelper helper,
		final Identifier spellId,
		final Holder<Enchantment> armorEnchantment,
		final int level,
		final Holder<net.minecraft.world.effect.MobEffect> effect,
		final int expectedDuration,
		final boolean selfCast,
		final EquipmentSlot armorSlot
	) {
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setGameMode(GameType.SURVIVAL);
		Item armor = armorSlot == EquipmentSlot.HEAD ? Items.DIAMOND_HELMET : Items.DIAMOND_CHESTPLATE;
		player.setItemSlot(armorSlot, withEnchantment(armor.getDefaultInstance(), armorEnchantment, level));
		SpellbookItem book = spellbook(helper, spellId);
		ItemStack stack = book.getDefaultInstance();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		Mob target = null;
		if (!selfCast) {
			target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		}
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		MobEffectInstance instance = selfCast ? player.getEffect(effect) : target.getEffect(effect);
		helper.assertTrue(
			instance != null && instance.getDuration() == expectedDuration,
			spellId + " with " + armorEnchantment.unwrapKey().map(ResourceKey::identifier).orElseThrow()
				+ " level " + level + " expected duration=" + expectedDuration
				+ " actual=" + (instance == null ? -1 : instance.getDuration())
		);
		if (target != null) {
			target.discard();
		}
	}

	private static void assertTableOffer(
		final GameTestHelper helper,
		final Holder.Reference<Enchantment> target,
		final ItemStack carrier,
		final boolean equipsLoaded
	) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		int targetId = registry.getId(target.value());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setExperienceLevels(50);
		BlockPos tablePos = new BlockPos(2, 2, 2);
		helper.setBlock(2, 2, 2, Blocks.ENCHANTING_TABLE);
		for (int offset = -1; offset <= 1; offset++) {
			helper.setBlock(2 + offset, 2, 0, Blocks.BOOKSHELF);
			helper.setBlock(2 + offset, 2, 4, Blocks.BOOKSHELF);
			helper.setBlock(0, 2, 2 + offset, Blocks.BOOKSHELF);
			helper.setBlock(4, 2, 2 + offset, Blocks.BOOKSHELF);
		}
		for (int offset = -1; offset <= 1; offset++) {
			helper.setBlock(2 + offset, 3, 0, Blocks.BOOKSHELF);
		}
		ContainerLevelAccess access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(tablePos));
		EnchantmentMenu menu = new EnchantmentMenu(1, player.getInventory(), access);
		menu.getSlot(0).set(carrier.copy());
		Item currency = equipsLoaded ? Items.EMERALD : Items.LAPIS_LAZULI;
		menu.getSlot(1).set(new ItemStack(currency, 64));
		for (int seed = 0; seed < 2048; seed++) {
			setEnchantmentSeed(menu, seed);
			menu.slotsChanged(menu.getSlot(0).container);
			for (int offer = 0; offer < menu.costs.length; offer++) {
				if (menu.costs[offer] <= 0 || menu.enchantClue[offer] != targetId) {
					continue;
				}
				int displayedPrice = menu.costs[offer];
				int offeredLevel = menu.levelClue[offer];
				int currencyBefore = menu.getSlot(1).getItem().getCount();
				int levelsBefore = player.experienceLevel;
				boolean accepted = menu.clickMenuButton(player, offer);
				int appliedLevel = EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).getLevel(target);
				int currencyAfter = menu.getSlot(1).getItem().getCount();
				if (equipsLoaded) {
					helper.assertTrue(
						accepted && appliedLevel > 0 && displayedPrice == offeredLevel
							&& currencyBefore - currencyAfter == displayedPrice
							&& player.experienceLevel == levelsBefore,
					"Equip-Enchants table offer mismatch for " + target.unwrapKey().map(ResourceKey::identifier).orElseThrow()
						+ ": price=" + displayedPrice + " level=" + offeredLevel + " accepted=" + accepted
						+ " applied=" + appliedLevel + " emeralds=" + (currencyBefore - currencyAfter)
						+ " XP=" + player.experienceLevel
				);
				} else {
					helper.assertTrue(
						accepted && appliedLevel > 0 && player.experienceLevel == levelsBefore - offer - 1
							&& currencyBefore - currencyAfter == offer + 1,
					"vanilla table offer mismatch for " + target.unwrapKey().map(ResourceKey::identifier).orElseThrow()
						+ ": offer=" + offer + " accepted=" + accepted + " applied=" + appliedLevel
						+ " XP before=" + levelsBefore + " after=" + player.experienceLevel
						+ " lapis=" + (currencyBefore - currencyAfter)
				);
				}
				return;
			}
		}
		helper.fail("the enchanting table did not offer " + target.unwrapKey().map(ResourceKey::identifier).orElseThrow());
	}

	private static void assertSpellAnvilOperation(
		final GameTestHelper helper,
		final Holder.Reference<Enchantment> enchantment,
		final int inputLevel,
		final int additionLevel,
		final boolean equipsLoaded
	) {
		ServerPlayer player = addPlayer(helper, 2.5, 2.5);
		player.setGameMode(GameType.SURVIVAL);
		player.setExperienceLevels(equipsLoaded ? 30 : 0);
		if (equipsLoaded) {
			player.getInventory().add(new ItemStack(Items.EMERALD, 8));
		}
		ItemStack input = spellbook(helper, SPARK_SHOT).getDefaultInstance();
		if (inputLevel > 0) {
			input = withEnchantment(input, enchantment, inputLevel);
		}
		AnvilMenu menu = anvilMenu(player, input, enchantedBook(enchantment, additionLevel));
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		int cost = menu.getCost();
		int levelsBefore = player.experienceLevel;
		int emeraldsBefore = countItems(player, Items.EMERALD);
		boolean mayPickup = menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player);
		int outputLevel = result.isEmpty()
			? 0
			: EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(enchantment);
		if (equipsLoaded) {
			helper.assertTrue(
				!result.isEmpty() && result.is(spellbook(helper, SPARK_SHOT)) && outputLevel == additionLevel
					&& cost == 1 && mayPickup,
				"Equip-Enchants spell anvil operation failed: input=" + inputLevel + " book=" + additionLevel
					+ " output=" + outputLevel + " cost=" + cost + " mayPickup=" + mayPickup
			);
			menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
			helper.assertTrue(
				countItems(player, Items.EMERALD) == emeraldsBefore - 1
					&& player.experienceLevel == levelsBefore,
				"Equip-Enchants spell anvil fee changed XP or did not charge one emerald"
			);
		} else {
			helper.assertTrue(
				!result.isEmpty() && result.is(spellbook(helper, SPARK_SHOT)) && outputLevel == additionLevel
					&& cost == 0 && mayPickup,
				"standalone spell anvil must permit zero-cost pickup: input=" + inputLevel + " book=" + additionLevel
					+ " output=" + outputLevel + " cost=" + cost + " mayPickup=" + mayPickup
			);
			menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, result);
			helper.assertTrue(
				player.experienceLevel == levelsBefore,
				"standalone spell anvil changed XP at zero cost"
			);
		}
	}

	private static AnvilMenu anvilMenu(
		final ServerPlayer player, final ItemStack input, final ItemStack addition
	) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(addition);
		menu.createResult();
		return menu;
	}

	private static int productionCalculation(
		final String methodName, final Class<?>[] parameterTypes, final Object... arguments
	) {
		try {
			Class<?> modifiers = Class.forName("com.stella.spells.SpellModifiers");
			Method method = modifiers.getDeclaredMethod(methodName, parameterTypes);
			method.setAccessible(true);
			return ((Number) method.invoke(null, arguments)).intValue();
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Cannot invoke SpellModifiers." + methodName, error);
		}
	}

	private static int productionBaseRangeBlocks() {
		try {
			Class<?> modifiers = Class.forName("com.stella.spells.SpellModifiers");
			Field field = modifiers.getDeclaredField("BASE_TARGET_RANGE_BLOCKS");
			field.setAccessible(true);
			return field.getInt(null);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Cannot read SpellModifiers.BASE_TARGET_RANGE_BLOCKS", error);
		}
	}

	private static ItemStack withEnchantment(
		final ItemStack stack, final Holder<Enchantment> enchantment, final int level
	) {
		ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(
			EnchantmentHelper.getEnchantmentsForCrafting(stack)
		);
		values.set(enchantment, level);
		stack.set(DataComponents.ENCHANTMENTS, values.toImmutable());
		return stack;
	}

	private static ItemStack enchantedBook(final Holder<Enchantment> enchantment, final int level) {
		ItemEnchantments.Mutable values = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		values.set(enchantment, level);
		ItemStack book = Items.ENCHANTED_BOOK.getDefaultInstance();
		book.set(DataComponents.STORED_ENCHANTMENTS, values.toImmutable());
		return book;
	}

	private static ItemStack tableCarrier(final GameTestHelper helper, final int index) {
		if (index < 3) {
			return spellbook(helper, SPARK_SHOT).getDefaultInstance();
		}
		return index < 5
			? Items.DIAMOND_HELMET.getDefaultInstance()
			: Items.DIAMOND_CHESTPLATE.getDefaultInstance();
	}

	private static Holder.Reference<Enchantment> enchantment(final GameTestHelper helper, final Identifier id) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
		var result = registry.get(key);
		helper.assertTrue(result.isPresent(), "missing enchantment data: " + id);
		return result.orElseThrow();
	}

	private static SpellbookItem spellbook(final GameTestHelper helper, final Identifier id) {
		Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, "spellbook item is not registered: " + id);
		return (SpellbookItem) item;
	}

	private static ServerPlayer addPlayer(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(position.x, position.y, position.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}

	private static Mob spawnAtX(final GameTestHelper helper, final double x) {
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, 2.5));
		target.setPos(position.x, position.y, position.z);
		return target;
	}

	private static Mob spawnAtZ(final GameTestHelper helper, final double z) {
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		Vec3 position = helper.absoluteVec(new Vec3(2.5, 1.0, z));
		target.setPos(position.x, position.y, position.z);
		return target;
	}

	private static void clearTargetRayBarriers(final GameTestHelper helper) {
		int maxTargetRange = productionBaseRangeBlocks() + 6;
		int lastBarrier = (int) (Math.ceil(maxTargetRange / 8.0) * 8);
		for (int z = 8; z <= lastBarrier; z += 8) {
			helper.setBlock(2, 2, z, Blocks.AIR);
		}
	}

	private static void setEnchantmentSeed(final EnchantmentMenu menu, final int seed) {
		try {
			Field field = EnchantmentMenu.class.getDeclaredField("enchantmentSeed");
			field.setAccessible(true);
			((DataSlot) field.get(menu)).set(seed);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Cannot set the deterministic table seed", error);
		}
	}

	private static int countItems(final Player player, final Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static Identifier spellEnchantment(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static Identifier spellbookId(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
