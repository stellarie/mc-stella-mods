package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Exclusives;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** The materials, the Starweave set, the Fallen Star, the vault table and the recipes. */
public class CraterRewardsGameTest {
	static final int ROLLS = 4000;
	static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static Item piece(final EquipmentSlot slot) {
		return CraterItems.STARWEAVE.pieces().get(slot);
	}

	private static void wear(final ServerPlayer player, final EquipmentSlot... slots) {
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(piece(slot)));
		}
	}

	@GameTest
	public void everyCraterItemIsExclusiveAndTheUniqueIsTheFallenStar(final GameTestHelper helper) {
		List<Item> items = new ArrayList<>(List.of(CraterItems.RAW_STARMETAL, CraterItems.STARMETAL_INGOT, CraterItems.STAR_FRAGMENT, CraterItems.EMBER_CORE, CraterItems.FALLEN_STAR));
		items.addAll(CraterItems.STARWEAVE.pieces().values());
		for (Item item : items) {
			helper.assertTrue(Exclusives.isExclusive(item), BuiltInRegistries.ITEM.getKey(item) + " is not exclusive");
		}
		helper.assertTrue(CraterTestSupport.site().newUnique().is(CraterItems.FALLEN_STAR), "the site's unique is not the Fallen Star");
		helper.assertTrue("starfall".equals(RuinUniques.effectOf(new ItemStack(CraterItems.FALLEN_STAR))), "the Fallen Star has no starfall effect");
		helper.assertTrue(CraterItems.STARWEAVE.pieces().size() == 4, "the set has " + CraterItems.STARWEAVE.pieces().size() + " pieces");
		helper.succeed();
	}

	@GameTest
	public void theStarweaveSetHasTheMageNumbersAndRepairsWithIngots(final GameTestHelper helper) {
		int[] defense = {2, 6, 5, 2};
		for (int i = 0; i < 4; i++) {
			ItemStack stack = new ItemStack(piece(ARMOR[i]));
			double armor = 0.0;
			double toughness = 0.0;
			for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
				armor += entry.attribute().is(Attributes.ARMOR) ? entry.modifier().amount() : 0.0;
				toughness += entry.attribute().is(Attributes.ARMOR_TOUGHNESS) ? entry.modifier().amount() : 0.0;
			}
			helper.assertTrue(armor == defense[i] && toughness == 1.0, ARMOR[i] + " armor " + armor + " toughness " + toughness);
		}
		helper.assertTrue(CraterItems.STARWEAVE.spec().repair().equals(CraterItems.STARMETAL_REPAIR), "the set repairs with " + CraterItems.STARWEAVE.spec().repair());
		helper.assertTrue(new ItemStack(CraterItems.STARMETAL_INGOT).is(CraterItems.STARMETAL_REPAIR), "an ingot does not repair the set");
		helper.assertTrue(!new ItemStack(Items.IRON_INGOT).is(CraterItems.STARMETAL_REPAIR), "an iron ingot repairs the set");
		helper.succeed();
	}

	@GameTest
	public void rawStarmetalSmeltsIntoAnIngotInAVanillaFurnace(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var found = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(CraterItems.RAW_STARMETAL)), level);
		helper.assertTrue(found.isPresent(), "no furnace recipe for raw starmetal");
		ItemStack result = found.get().value().assemble(new SingleRecipeInput(new ItemStack(CraterItems.RAW_STARMETAL)));
		helper.assertTrue(result.is(CraterItems.STARMETAL_INGOT) && result.getCount() == 1, "the furnace gave " + result);
		helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(CraterItems.STARMETAL_INGOT)), level).isEmpty(), "an ingot smelts");
		CraterTestSupport.result("smelting", "raw_starmetal -> " + result);
		helper.succeed();
	}

	@GameTest
	public void theFourStarweavePiecesCraftFromIngotsAndOnlyFromIngots(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack ingot = new ItemStack(CraterItems.STARMETAL_INGOT);
		ItemStack none = ItemStack.EMPTY;
		List<List<ItemStack>> grids = List.of(
			List.of(ingot, ingot, ingot, ingot, none, ingot),
			List.of(ingot, none, ingot, ingot, ingot, ingot, ingot, ingot, ingot),
			List.of(ingot, ingot, ingot, ingot, none, ingot, ingot, none, ingot),
			List.of(ingot, none, ingot, ingot, none, ingot));
		int[] widths = {3, 3, 3, 3};
		int[] heights = {2, 3, 3, 2};
		for (int i = 0; i < 4; i++) {
			CraftingInput input = CraftingInput.of(widths[i], heights[i], grids.get(i));
			var found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
			helper.assertTrue(found.isPresent(), "no recipe for " + ARMOR[i]);
			ItemStack result = found.get().value().assemble(input);
			helper.assertTrue(result.is(piece(ARMOR[i])), "the " + ARMOR[i] + " recipe gave " + result);
		}
		List<ItemStack> iron = List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), none, new ItemStack(Items.IRON_INGOT));
		var plain = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 2, iron), level);
		helper.assertTrue(plain.isEmpty() || !plain.get().value().assemble(CraftingInput.of(3, 2, iron)).is(piece(EquipmentSlot.HEAD)), "iron crafts a starweave hood");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void twoPiecesGiveTenPercentAttackSpeedAndFourAddFireResistance(final GameTestHelper helper) {
		ServerPlayer player = CraterTestSupport.join(helper);
		double base = player.getAttributeValue(Attributes.ATTACK_SPEED);
		wear(player, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - base * 1.10) < 1.0E-6, "two pieces: attack speed " + player.getAttributeValue(Attributes.ATTACK_SPEED)))
			.thenExecute(() -> helper.assertTrue(!player.hasEffect(MobEffects.FIRE_RESISTANCE), "two pieces gave fire resistance"))
			.thenExecute(() -> wear(player, EquipmentSlot.LEGS, EquipmentSlot.FEET))
			.thenWaitUntil(() -> helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "four pieces: no fire resistance"))
			.thenExecute(() -> {
				helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - base * 1.10) < 1.0E-6, "four pieces: attack speed " + player.getAttributeValue(Attributes.ATTACK_SPEED));
				CraterTestSupport.result("setBonus", "attackSpeed=" + player.getAttributeValue(Attributes.ATTACK_SPEED) + " base=" + base + " fireResistance=true");
				player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - base * 1.10) < 1.0E-6, "back to two pieces"))
			.thenExecute(() -> {
				player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_SPEED) - base) < 1.0E-6, "bonus stays after the pieces came off"))
			.thenExecute(() -> CraterTestSupport.leave(helper, player))
			.thenSucceed();
	}

	private static float magicLoss(final GameTestHelper helper, final ServerPlayer player, final boolean indirectMagic) {
		ServerLevel level = helper.getLevel();
		Cow cow = CraterTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		cow.damageCooldownTime = 0;
		float before = cow.getHealth();
		var source = indirectMagic ? level.damageSources().indirectMagic(player, player) : level.damageSources().playerAttack(player);
		cow.hurtServer(level, source, 10.0F);
		float loss = before - cow.getHealth();
		cow.discard();
		return loss;
	}

	@GameTest
	public void fourPiecesRaiseMagicDamageByFifteenPercentAndNothingElse(final GameTestHelper helper) {
		ServerPlayer player = CraterTestSupport.join(helper);
		float bare = magicLoss(helper, player, true);
		wear(player, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);
		float three = magicLoss(helper, player, true);
		wear(player, EquipmentSlot.FEET);
		float four = magicLoss(helper, player, true);
		float melee = magicLoss(helper, player, false);
		CraterTestSupport.result("magicBonus", "bare=" + bare + " three=" + three + " four=" + four + " melee=" + melee);
		helper.assertTrue(Math.abs(bare - 10.0F) < 1.0E-3F && Math.abs(three - 10.0F) < 1.0E-3F, "magic damage without four pieces: " + bare + " / " + three);
		helper.assertTrue(Math.abs(four - 11.5F) < 1.0E-3F, "magic damage with four pieces: " + four);
		helper.assertTrue(Math.abs(melee - 10.0F) < 1.0E-3F, "melee damage with four pieces: " + melee);
		CraterTestSupport.leave(helper, player);
		helper.succeed();
	}

	@GameTest
	public void theFallenStarFallsOnTenPercentOfHitsOverEightThousandSeededTrials(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20261006L);
		int stars = 0;
		int trials = 8000;
		for (int i = 0; i < trials; i++) {
			if (StarfallEffect.roll(random)) {
				stars++;
			}
		}
		double rate = stars / (double) trials;
		CraterTestSupport.result("starfallRate", "trials=" + trials + " stars=" + stars + " rate=" + rate);
		helper.assertTrue(Math.abs(rate - StarfallEffect.CHANCE) <= StarfallEffect.CHANCE * 0.15, "starfall rate " + rate + " is not within 15% of " + StarfallEffect.CHANCE);
		helper.succeed();
	}

	private static Supplier<RandomSource> rolling(final boolean star) {
		long seed = 0;
		while (StarfallEffect.roll(RandomSource.create(seed)) != star) {
			seed++;
		}
		long found = seed;
		return () -> RandomSource.create(found);
	}

	private static float hit(final GameTestHelper helper, final ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		Cow cow = CraterTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		cow.damageCooldownTime = 0;
		float before = cow.getHealth();
		cow.hurtServer(level, player.damageSources().playerAttack(player), 8.0F);
		float loss = before - cow.getHealth();
		cow.discard();
		return loss;
	}

	@GameTest
	public void aFallingStarDealsSixMagicDamageOnTopOfTheHit(final GameTestHelper helper) {
		ServerPlayer player = CraterTestSupport.join(helper);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(CraterItems.FALLEN_STAR));
		StarfallEffect.setRandom(player.getUUID(), rolling(true));
		float withStar = hit(helper, player);
		StarfallEffect.setRandom(player.getUUID(), rolling(false));
		float without = hit(helper, player);
		player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		StarfallEffect.setRandom(player.getUUID(), rolling(true));
		float noItem = hit(helper, player);
		StarfallEffect.setRandom(player.getUUID(), null);
		CraterTestSupport.result("starfall", "withStar=" + withStar + " failedRoll=" + without + " noItem=" + noItem);
		helper.assertTrue(Math.abs(withStar - 14.0F) < 1.0E-3F, "the hit with a star did " + withStar + ", not 8 plus 6");
		helper.assertTrue(Math.abs(without - 8.0F) < 1.0E-3F, "the hit after a failed roll did " + without);
		helper.assertTrue(Math.abs(noItem - 8.0F) < 1.0E-3F, "the hit without the item did " + noItem);
		CraterTestSupport.leave(helper, player);
		helper.succeed();
	}

	private static List<ItemStack> chest(final ServerLevel level, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(CraterMobs.VAULT_LOOT);
		return table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST), seed);
	}

	@GameTest
	public void theVaultTableHoldsIngotsFragmentsGoldAndEmeraldsInTheStatedCounts(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(7_000_023L);
		boolean[] seenIngots = new boolean[5];
		for (int i = 0; i < ROLLS; i++) {
			int ingots = 0;
			int fragments = 0;
			int gold = 0;
			int emeralds = 0;
			int templates = 0;
			for (ItemStack stack : chest(level, master.nextLong())) {
				if (stack.is(CraterItems.STARMETAL_INGOT)) {
					ingots += stack.getCount();
				} else if (stack.is(CraterItems.STAR_FRAGMENT)) {
					fragments += stack.getCount();
				} else if (stack.is(Items.GOLD_INGOT)) {
					gold += stack.getCount();
				} else if (stack.is(Items.EMERALD)) {
					emeralds += stack.getCount();
				} else if (stack.is(CraterItems.EMBERWARD.template()) && stack.getCount() == 1) {
					templates++;
				} else {
					helper.fail("the vault table gave " + stack);
				}
			}
			helper.assertTrue(ingots >= 2 && ingots <= 4, "roll " + i + " gave " + ingots + " ingots");
			helper.assertTrue(fragments >= 2 && fragments <= 4, "roll " + i + " gave " + fragments + " fragments");
			helper.assertTrue(gold >= 3 && gold <= 6 && emeralds >= 1 && emeralds <= 3, "roll " + i + " gold " + gold + " emeralds " + emeralds);
			helper.assertTrue(templates <= 1, "roll " + i + " gave " + templates + " emberward templates");
			seenIngots[ingots] = true;
		}
		helper.assertTrue(seenIngots[2] && seenIngots[3] && seenIngots[4], "the ingot counts did not cover 2, 3 and 4");
		CraterTestSupport.result("vaultTable", "rolls=" + ROLLS);
		helper.succeed();
	}
}
