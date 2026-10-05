package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.reward.RuinSets;
import com.stella.ruins.reward.RuinUniques;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 8 and 9: the four recipes, the set bonuses and the kill strength, and the Gladius of the Crowd. */
public class ProvingRewardGameTest {
	private static final double BASE_DAMAGE = 1.0;
	private static final double BASE_SPEED = 4.0;

	private static Item piece(final EquipmentSlot slot) {
		return GroundsRewards.set().pieces().get(slot);
	}

	private static ItemStack craft(final ServerLevel level, final List<Item> ingredients) {
		List<ItemStack> grid = new ArrayList<>();
		for (Item item : ingredients) {
			grid.add(new ItemStack(item));
		}
		while (grid.size() < 9) {
			grid.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, 3, grid);
		Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
		return recipe.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
	}

	private static List<Item> list(final int tokens, final int fiber, final int fangs) {
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < tokens; i++) {
			items.add(GroundsItems.CHAMPION_TOKEN);
		}
		for (int i = 0; i < fiber; i++) {
			items.add(GroundsItems.NET_FIBER);
		}
		for (int i = 0; i < fangs; i++) {
			items.add(GroundsItems.HOUND_FANG);
		}
		return items;
	}

	private static List<Item> swap(final List<Item> items, final Item out) {
		List<Item> copy = new ArrayList<>(items);
		copy.set(copy.indexOf(out), Items.STICK);
		return copy;
	}

	private static void crafts(final GameTestHelper helper, final EquipmentSlot slot, final int tokens, final int fiber, final int fangs) {
		ServerLevel level = helper.getLevel();
		List<Item> items = list(tokens, fiber, fangs);
		ItemStack made = craft(level, items);
		ItemStack lacksToken = craft(level, swap(items, GroundsItems.CHAMPION_TOKEN));
		ItemStack lacksFiber = fiber > 0 ? craft(level, swap(items, GroundsItems.NET_FIBER)) : ItemStack.EMPTY;
		ItemStack lacksFang = fangs > 0 ? craft(level, swap(items, GroundsItems.HOUND_FANG)) : ItemStack.EMPTY;
		ProvingTestSupport.result("recipe", "slot=" + slot + " made=" + made.getItem() + " x" + made.getCount() + " lacksToken=" + lacksToken.getItem() + " lacksFiber=" + lacksFiber.getItem() + " lacksFang=" + lacksFang.getItem());
		helper.assertTrue(made.is(piece(slot)) && made.getCount() == 1, tokens + " tokens, " + fiber + " fiber and " + fangs + " fangs made " + made);
		helper.assertTrue(lacksToken.isEmpty(), "a recipe with a stick for a token still crafted " + lacksToken);
		helper.assertTrue(lacksFiber.isEmpty(), "a recipe with a stick for fiber still crafted " + lacksFiber);
		helper.assertTrue(lacksFang.isEmpty(), "a recipe with a stick for a fang still crafted " + lacksFang);
		helper.succeed();
	}

	@GameTest
	public void threeTokensAndTwoFiberCraftTheHelm(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.HEAD, 3, 2, 0);
	}

	@GameTest
	public void fiveTokensTwoFiberAndTwoFangsCraftTheCuirass(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.CHEST, 5, 2, 2);
	}

	@GameTest
	public void fourTokensOneFiberAndTwoFangsCraftTheGreaves(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.LEGS, 4, 1, 2);
	}

	@GameTest
	public void twoTokensOneFiberAndOneFangCraftTheSandals(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.FEET, 2, 1, 1);
	}

	@GameTest
	public void aTokenRepairsTheChampionSetAndNothingElseDoes(final GameTestHelper helper) {
		helper.assertTrue(new ItemStack(GroundsItems.CHAMPION_TOKEN).is(GroundsRewards.REPAIR), "the token is not in the repair tag");
		helper.assertTrue(!new ItemStack(GroundsItems.NET_FIBER).is(GroundsRewards.REPAIR) && !new ItemStack(GroundsItems.HOUND_FANG).is(GroundsRewards.REPAIR) && !new ItemStack(Items.IRON_INGOT).is(GroundsRewards.REPAIR), "the repair tag holds more than the token");
		helper.succeed();
	}

	@GameTest
	public void theSetHasThePlannedDefenseToughnessAndFourPieces(final GameTestHelper helper) {
		helper.assertTrue(GroundsRewards.set().pieces().size() == 4, "the set has " + GroundsRewards.set().pieces().size() + " pieces");
		var spec = GroundsRewards.set().spec();
		helper.assertTrue(spec.toughness() == 2.0F, "the toughness is " + spec.toughness());
		helper.assertTrue(spec.boots() == 3 && spec.leggings() == 6 && spec.chestplate() == 8 && spec.helmet() == 3, "the defense is off the plan");
		helper.succeed();
	}

	private static void wear(final ServerPlayer player, final EquipmentSlot... slots) {
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(piece(slot)));
		}
	}

	private static void quiet(final ServerPlayer player) {
		player.getFoodData().setFoodLevel(6);
		player.getFoodData().setSaturation(0.0F);
	}

	@GameTest(maxTicks = 200)
	public void twoPiecesAddTenPercentAttackDamageAndOnePieceDoesNot(final GameTestHelper helper) {
		ServerPlayer two = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer one = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 6.5));
		wear(two, EquipmentSlot.HEAD, EquipmentSlot.FEET);
		wear(one, EquipmentSlot.CHEST);
		helper.startSequence()
			.thenWaitUntil(() -> {
				double got = two.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(Math.abs(got - BASE_DAMAGE * (1.0 + GroundsRewards.DAMAGE_BONUS)) < 1.0E-6, "two pieces give attack damage " + got);
			})
			.thenExecute(() -> {
				ProvingTestSupport.result("twoPieces", "damage=" + two.getAttributeValue(Attributes.ATTACK_DAMAGE) + " onePiece=" + one.getAttributeValue(Attributes.ATTACK_DAMAGE) + " speed=" + two.getAttributeValue(Attributes.ATTACK_SPEED));
				helper.assertTrue(one.getAttributeValue(Attributes.ATTACK_DAMAGE) == BASE_DAMAGE, "one piece gave attack damage " + one.getAttributeValue(Attributes.ATTACK_DAMAGE));
				helper.assertTrue(two.getAttributeValue(Attributes.ATTACK_SPEED) == BASE_SPEED, "two pieces changed the attack speed to " + two.getAttributeValue(Attributes.ATTACK_SPEED));
				two.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertTrue(two.getAttributeValue(Attributes.ATTACK_DAMAGE) == BASE_DAMAGE, "taking a piece off left attack damage " + two.getAttributeValue(Attributes.ATTACK_DAMAGE)))
			.thenExecute(() -> {
				ProvingTestSupport.leave(helper, two);
				ProvingTestSupport.leave(helper, one);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void fourPiecesAddTenPercentAttackSpeedAndKeepTheDamageBonus(final GameTestHelper helper) {
		ServerPlayer four = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer three = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 6.5));
		wear(four, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		wear(three, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);
		helper.startSequence()
			.thenWaitUntil(() -> {
				double got = four.getAttributeValue(Attributes.ATTACK_SPEED);
				helper.assertTrue(Math.abs(got - BASE_SPEED * (1.0 + GroundsRewards.SPEED_BONUS)) < 1.0E-6, "four pieces give attack speed " + got);
			})
			.thenExecute(() -> {
				ProvingTestSupport.result("fourPieces", "speed=" + four.getAttributeValue(Attributes.ATTACK_SPEED) + " damage=" + four.getAttributeValue(Attributes.ATTACK_DAMAGE) + " threeSpeed=" + three.getAttributeValue(Attributes.ATTACK_SPEED));
				helper.assertTrue(Math.abs(four.getAttributeValue(Attributes.ATTACK_DAMAGE) - BASE_DAMAGE * 1.1) < 1.0E-6, "four pieces lost the damage bonus: " + four.getAttributeValue(Attributes.ATTACK_DAMAGE));
				helper.assertTrue(three.getAttributeValue(Attributes.ATTACK_SPEED) == BASE_SPEED && Math.abs(three.getAttributeValue(Attributes.ATTACK_DAMAGE) - BASE_DAMAGE * 1.1) < 1.0E-6, "three pieces gave speed " + three.getAttributeValue(Attributes.ATTACK_SPEED));
				helper.assertTrue(four.getAttributeValue(Attributes.ARMOR) == 20.0 && four.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 8.0, "the full set gives armor " + four.getAttributeValue(Attributes.ARMOR) + " and toughness " + four.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
				ProvingTestSupport.leave(helper, four);
				ProvingTestSupport.leave(helper, three);
			})
			.thenSucceed();
	}

	private static Mob victim(final ServerLevel level, final GameTestHelper helper, final double x, final double z) {
		Mob cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
		cow.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		cow.setNoAi(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		cow.setHealth(100_000.0F);
		level.addFreshEntity(cow);
		return cow;
	}

	@GameTest(maxTicks = 200)
	public void aKillByAFourPieceWearerGivesStrengthOneForOneHundredTicksAndThreePiecesDoNot(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer four = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer three = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 6.5));
		wear(four, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		wear(three, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);
		quiet(four);
		quiet(three);
		Mob first = victim(level, helper, 3.5, 1.5);
		Mob second = victim(level, helper, 3.5, 6.5);
		Mob third = victim(level, helper, 8.5, 8.5);
		first.hurtServer(level, level.damageSources().playerAttack(four), 200_000.0F);
		second.hurtServer(level, level.damageSources().playerAttack(three), 200_000.0F);
		third.hurtServer(level, level.damageSources().generic(), 200_000.0F);
		MobEffectInstance strength = four.getEffect(MobEffects.STRENGTH);
		ProvingTestSupport.result("killStrength", "four=" + (strength == null ? -1 : strength.getDuration()) + " amplifier=" + (strength == null ? -1 : strength.getAmplifier()) + " three=" + three.hasEffect(MobEffects.STRENGTH));
		helper.assertTrue(!first.isAlive() && !second.isAlive() && !third.isAlive(), "a victim survived, so the check proves nothing");
		helper.assertTrue(strength != null && strength.getAmplifier() == 0 && strength.getDuration() == GroundsRewards.KILL_STRENGTH_TICKS, "the four piece wearer's Strength is " + strength);
		helper.assertTrue(!three.hasEffect(MobEffects.STRENGTH), "a three piece wearer got Strength");
		ProvingTestSupport.leave(helper, four);
		ProvingTestSupport.leave(helper, three);
		helper.succeed();
	}

	@GameTest
	public void theGladiusIsTheSitesUniqueAndNothingElseIs(final GameTestHelper helper) {
		ItemStack unique = ProvingGroundsSite.site().newUnique();
		helper.assertTrue(unique.is(GroundsRewards.gladius()) && ProvingGroundsSite.site().hasUnique(), "the site's unique is " + unique);
		helper.assertTrue(GroundsRewards.CROWD_EFFECT.equals(RuinUniques.effectOf(unique)), "the gladius carries effect " + RuinUniques.effectOf(unique));
		helper.assertTrue(RuinUniques.all().containsValue(GroundsRewards.CROWD_EFFECT) && RuinSets.get("champion").isPresent(), "the gladius or the set is not registered");
		helper.succeed();
	}

	private static void swing(final ServerLevel level, final ServerPlayer player, final Mob target) {
		// A hit inside the target's invulnerable window does no damage and fires no event.
		target.damageCooldownTime = 0;
		target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
	}

	@GameTest(maxTicks = 200)
	public void theCrowdAddsSixPercentPerConsecutiveHitUpToFiveStacks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GroundsRewards.gladius()));
		quiet(player);
		Mob target = victim(level, helper, 2.5, 1.5);
		int[] hits = {0};
		double[] base = {0.0};
		helper.startSequence()
			.thenWaitUntil(() -> {
				base[0] = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(base[0] > BASE_DAMAGE + 2.5, "the sword's attack damage has not applied yet: " + base[0]);
			})
			.thenExecute(() -> {
				StringBuilder log = new StringBuilder();
				for (int hit = 1; hit <= 7; hit++) {
					swing(level, player, target);
					int stacks = GroundsRewards.crowdStacks(player.getUUID());
					double ratio = player.getAttributeValue(Attributes.ATTACK_DAMAGE) / base[0];
					int expected = Math.min(5, hit);
					log.append(hit).append(':').append(stacks).append('@').append(String.format("%.2f", ratio)).append(' ');
					helper.assertTrue(stacks == expected, "after hit " + hit + " the stacks are " + stacks + ", not " + expected);
					helper.assertTrue(Math.abs(ratio - (1.0 + GroundsRewards.CROWD_STEP * expected)) < 1.0E-6, "after hit " + hit + " the damage is x" + ratio + ", not x" + (1.0 + GroundsRewards.CROWD_STEP * expected));
				}
				ProvingTestSupport.result("crowdStacks", log.toString().trim());
				ProvingTestSupport.leave(helper, player);
				target.discard();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void theCrowdResetsAfterSixtyTicksWithoutAHitAndNotBefore(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GroundsRewards.gladius()));
		quiet(player);
		Mob target = victim(level, helper, 2.5, 1.5);
		int[] lastHit = {0};
		boolean[] premature = {false};
		boolean[] late = {false};
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > BASE_DAMAGE + 2.5, "the sword has not applied yet"))
			.thenExecute(() -> {
				swing(level, player, target);
				swing(level, player, target);
				swing(level, player, target);
				lastHit[0] = player.tickCount;
				helper.assertTrue(GroundsRewards.crowdStacks(player.getUUID()) == 3, "three hits did not give three stacks");
			})
			.thenWaitUntil(() -> {
				int idle = player.tickCount - lastHit[0];
				int stacks = GroundsRewards.crowdStacks(player.getUUID());
				if (stacks == 0 && idle < GroundsRewards.CROWD_RESET_TICKS) {
					premature[0] = true;
				}
				if (stacks != 0 && idle > GroundsRewards.CROWD_RESET_TICKS + 2) {
					late[0] = true;
				}
				helper.assertTrue(stacks == 0, "the stacks are still " + stacks + " after " + idle + " idle ticks");
			})
			.thenExecute(() -> {
				int idle = player.tickCount - lastHit[0];
				ProvingTestSupport.result("crowdReset", "idleAtReset=" + idle + " premature=" + premature[0] + " late=" + late[0]);
				helper.assertTrue(!premature[0] && !late[0], "the crowd reset at the wrong time: premature=" + premature[0] + " late=" + late[0]);
				helper.assertTrue(idle >= GroundsRewards.CROWD_RESET_TICKS, "the crowd reset after only " + idle + " ticks");
				swing(level, player, target);
				helper.assertTrue(GroundsRewards.crowdStacks(player.getUUID()) == 1, "a hit after the reset did not start at one stack: " + GroundsRewards.crowdStacks(player.getUUID()));
				ProvingTestSupport.leave(helper, player);
				target.discard();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void dropTheGladiusAndTheStacksGoAtOnce(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(GroundsRewards.gladius()));
		quiet(player);
		Mob target = victim(level, helper, 2.5, 1.5);
		int[] dropped = {0};
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > BASE_DAMAGE + 2.5, "the sword has not applied yet"))
			.thenExecute(() -> {
				swing(level, player, target);
				swing(level, player, target);
				helper.assertTrue(GroundsRewards.crowdStacks(player.getUUID()) == 2, "two hits did not give two stacks");
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
				dropped[0] = player.tickCount;
			})
			.thenWaitUntil(() -> helper.assertTrue(GroundsRewards.crowdStacks(player.getUUID()) == 0, "the stacks outlived the sword"))
			.thenExecute(() -> {
				int gone = player.tickCount - dropped[0];
				ProvingTestSupport.result("crowdDrop", "stacksGoneAfterTicks=" + gone);
				helper.assertTrue(gone <= 3, "the stacks outlived the sword by " + gone + " ticks, not at once");
				helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == BASE_DAMAGE, "the damage modifier stayed: " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
				ProvingTestSupport.leave(helper, player);
				target.discard();
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aHitWithoutTheGladiusBuildsNoStacks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProvingTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		Mob target = victim(level, helper, 2.5, 1.5);
		swing(level, player, target);
		swing(level, player, target);
		helper.assertTrue(GroundsRewards.crowdStacks(player.getUUID()) == 0, "an iron sword built " + GroundsRewards.crowdStacks(player.getUUID()) + " stacks");
		ProvingTestSupport.leave(helper, player);
		target.discard();
		helper.succeed();
	}
}
