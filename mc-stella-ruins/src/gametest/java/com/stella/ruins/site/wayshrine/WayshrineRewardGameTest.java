package com.stella.ruins.site.wayshrine;

import com.stella.ruins.reward.RuinSets;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 9 and 10: the four recipes, the set bonuses and the aura, and the Pilgrim censer. */
public class WayshrineRewardGameTest {
	private static final double BASE_HEALTH = 20.0;

	private static Item piece(final EquipmentSlot slot) {
		return WayshrineRewards.set().pieces().get(slot);
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

	private static List<Item> list(final int shards, final int ash, final int light) {
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < shards; i++) {
			items.add(WayshrineItems.SHRINE_SHARD);
		}
		for (int i = 0; i < ash; i++) {
			items.add(WayshrineItems.PILGRIM_ASH);
		}
		for (int i = 0; i < light; i++) {
			items.add(WayshrineItems.WISP_LIGHT);
		}
		return items;
	}

	private static List<Item> swap(final List<Item> items, final Item out) {
		List<Item> copy = new ArrayList<>(items);
		copy.set(copy.indexOf(out), Items.STICK);
		return copy;
	}

	private static void crafts(final GameTestHelper helper, final EquipmentSlot slot, final int shards, final int ash, final int light) {
		ServerLevel level = helper.getLevel();
		List<Item> items = list(shards, ash, light);
		ItemStack made = craft(level, items);
		// A missing shard or ash is a stick, because the helmet minus a shard is exactly the boots recipe.
		ItemStack lacksShard = craft(level, swap(items, WayshrineItems.SHRINE_SHARD));
		ItemStack lacksAsh = craft(level, swap(items, WayshrineItems.PILGRIM_ASH));
		WayshrineTestSupport.result("recipe", "slot=" + slot + " made=" + made.getItem() + " x" + made.getCount() + " lacksShard=" + lacksShard.getItem() + " lacksAsh=" + lacksAsh.getItem());
		helper.assertTrue(made.is(piece(slot)) && made.getCount() == 1, shards + " shards, " + ash + " ash and " + light + " light made " + made);
		helper.assertTrue(lacksShard.isEmpty(), "a recipe with a stick for a shard still crafted " + lacksShard);
		helper.assertTrue(lacksAsh.isEmpty(), "a recipe with a stick for ash still crafted " + lacksAsh);
		helper.succeed();
	}

	@GameTest
	public void threeShardsAndTwoAshCraftTheHood(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.HEAD, 3, 2, 0);
	}

	@GameTest
	public void fiveShardsTwoAshAndOneLightCraftTheRobe(final GameTestHelper helper) {
		ItemStack noLight = craft(helper.getLevel(), list(5, 2, 0));
		helper.assertTrue(noLight.isEmpty(), "the robe crafted without wisp light: " + noLight);
		crafts(helper, EquipmentSlot.CHEST, 5, 2, 1);
	}

	@GameTest
	public void fourShardsAndThreeAshCraftTheTrousers(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.LEGS, 4, 3, 0);
	}

	@GameTest
	public void twoShardsAndTwoAshCraftTheSandals(final GameTestHelper helper) {
		crafts(helper, EquipmentSlot.FEET, 2, 2, 0);
	}

	@GameTest
	public void aShrineShardRepairsThePilgrimSet(final GameTestHelper helper) {
		helper.assertTrue(new ItemStack(WayshrineItems.SHRINE_SHARD).is(WayshrineRewards.REPAIR), "the shard is not in the repair tag");
		helper.assertTrue(!new ItemStack(WayshrineItems.PILGRIM_ASH).is(WayshrineRewards.REPAIR) && !new ItemStack(Items.IRON_INGOT).is(WayshrineRewards.REPAIR), "the repair tag holds more than the shard");
		helper.succeed();
	}

	private static void wear(final ServerPlayer player, final EquipmentSlot... slots) {
		for (EquipmentSlot slot : slots) {
			player.setItemSlot(slot, new ItemStack(piece(slot)));
		}
	}

	/**
	 * Leaves the censer as the only heal. Food is low, and Regeneration is stripped every tick: the Pilgrim aura of a
	 * neighbouring test, up to 8 blocks away in the shared level, once gave every player in this test one heart.
	 */
	private static void quiet(final GameTestHelper helper, final ServerPlayer player) {
		player.getFoodData().setFoodLevel(6);
		player.getFoodData().setSaturation(0.0F);
		helper.onEachTick(() -> player.removeEffect(MobEffects.REGENERATION));
	}

	@GameTest(maxTicks = 200)
	public void twoPiecesAddTenPercentMaxHealthAndOnePieceDoesNot(final GameTestHelper helper) {
		ServerPlayer two = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer one = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 6.5));
		wear(two, EquipmentSlot.HEAD, EquipmentSlot.FEET);
		wear(one, EquipmentSlot.CHEST);
		helper.startSequence()
			.thenWaitUntil(() -> {
				double got = two.getAttributeValue(Attributes.MAX_HEALTH);
				helper.assertTrue(Math.abs(got - 22.0) < 1.0E-6, "two pieces give max health " + got + ", not 22");
			})
			.thenExecute(() -> {
				WayshrineTestSupport.result("twoPieces", "maxHealth=" + two.getAttributeValue(Attributes.MAX_HEALTH) + " onePiece=" + one.getAttributeValue(Attributes.MAX_HEALTH));
				helper.assertTrue(one.getAttributeValue(Attributes.MAX_HEALTH) == BASE_HEALTH, "one piece gave max health " + one.getAttributeValue(Attributes.MAX_HEALTH));
				two.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			})
			.thenWaitUntil(() -> helper.assertTrue(two.getAttributeValue(Attributes.MAX_HEALTH) == BASE_HEALTH, "taking a piece off left max health " + two.getAttributeValue(Attributes.MAX_HEALTH)))
			.thenExecute(() -> {
				WayshrineTestSupport.leave(helper, two);
				WayshrineTestSupport.leave(helper, one);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void fourPiecesGiveRegenerationToTheWearerAndPlayersWithinEightBlocksOnly(final GameTestHelper helper) {
		ServerPlayer wearer = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer near = WayshrineTestSupport.join(helper, new Vec3(6.5, 1.0, 1.5));
		ServerPlayer far = WayshrineTestSupport.join(helper, new Vec3(11.5, 1.0, 1.5));
		wear(wearer, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(wearer.hasEffect(MobEffects.REGENERATION), "the wearer has no Regeneration");
				helper.assertTrue(near.hasEffect(MobEffects.REGENERATION), "a player 5 blocks away has no Regeneration");
			})
			.thenExecute(() -> {
				MobEffectInstance seen = near.getEffect(MobEffects.REGENERATION);
				WayshrineTestSupport.result("fourPieces", "wearer=" + wearer.hasEffect(MobEffects.REGENERATION) + " near=" + seen.getAmplifier() + "/" + seen.getDuration() + " far=" + far.hasEffect(MobEffects.REGENERATION));
				helper.assertTrue(seen.getAmplifier() == 0, "the aura gave Regeneration " + (seen.getAmplifier() + 1));
				helper.assertTrue(!far.hasEffect(MobEffects.REGENERATION), "a player 10 blocks away got Regeneration");
			})
			.thenExecute(() -> {
				WayshrineTestSupport.leave(helper, wearer);
				WayshrineTestSupport.leave(helper, near);
				WayshrineTestSupport.leave(helper, far);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 200)
	public void threePiecesGiveNoAura(final GameTestHelper helper) {
		ServerPlayer wearer = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer near = WayshrineTestSupport.join(helper, new Vec3(4.5, 1.0, 1.5));
		wear(wearer, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(wearer.getAttributeValue(Attributes.MAX_HEALTH) > BASE_HEALTH, "the tracker has not counted the pieces yet"))
			.thenIdle(45)
			.thenExecute(() -> {
				helper.assertTrue(!wearer.hasEffect(MobEffects.REGENERATION), "three pieces gave the wearer Regeneration");
				helper.assertTrue(!near.hasEffect(MobEffects.REGENERATION), "three pieces gave a neighbour Regeneration");
				WayshrineTestSupport.leave(helper, wearer);
				WayshrineTestSupport.leave(helper, near);
			})
			.thenSucceed();
	}

	private static ServerPlayer holder(final GameTestHelper helper, final double z, final boolean offHand) {
		ServerPlayer player = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, z));
		quiet(helper, player);
		ItemStack censer = new ItemStack(WayshrineRewards.censer());
		if (offHand) {
			player.setItemSlot(EquipmentSlot.OFFHAND, censer);
		} else {
			player.setItemSlot(EquipmentSlot.MAINHAND, censer);
		}
		return player;
	}

	@GameTest(maxTicks = 300)
	public void theCenserHealsAHolderBelowHalfHealthAndNoOneElse(final GameTestHelper helper) {
		ServerPlayer low = holder(helper, 1.5, true);
		ServerPlayer high = holder(helper, 6.5, true);
		ServerPlayer mainHand = holder(helper, 11.5, false);
		ServerPlayer bare = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 16.5));
		quiet(helper, bare);
		low.setHealth(5.0F);
		high.setHealth(15.0F);
		mainHand.setHealth(5.0F);
		bare.setHealth(5.0F);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(low.getHealth() > 5.0F, "the censer has not healed the holder yet: " + low.getHealth()))
			.thenIdle(10)
			.thenExecute(() -> {
				WayshrineTestSupport.result("censerHeals", "low=" + low.getHealth() + " aboveHalf=" + high.getHealth() + " mainHand=" + mainHand.getHealth() + " bareControl=" + bare.getHealth());
				helper.assertTrue(low.getHealth() >= 5.0F + WayshrineRewards.CENSER_TICK_HEAL, "the holder below half health healed to " + low.getHealth());
				helper.assertTrue(high.getHealth() == 15.0F, "a holder above half health healed to " + high.getHealth());
				helper.assertTrue(mainHand.getHealth() == 5.0F, "a censer in the main hand healed its holder to " + mainHand.getHealth());
				helper.assertTrue(bare.getHealth() == 5.0F, "a player with no censer healed to " + bare.getHealth() + ", so the check proves nothing");
				for (ServerPlayer player : new ServerPlayer[] {low, high, mainHand, bare}) {
					WayshrineTestSupport.leave(helper, player);
				}
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void aKillByTheCenserHolderHealsTwo(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer holder = holder(helper, 1.5, true);
		ServerPlayer bare = WayshrineTestSupport.join(helper, new Vec3(1.5, 1.0, 6.5));
		quiet(helper, bare);
		holder.setHealth(10.0F);
		bare.setHealth(10.0F);
		Cow first = cow(helper, 4.5, 1.5);
		Cow second = cow(helper, 4.5, 6.5);
		first.hurtServer(level, level.damageSources().playerAttack(holder), 10_000.0F);
		second.hurtServer(level, level.damageSources().playerAttack(bare), 10_000.0F);
		WayshrineTestSupport.result("censerKill", "holder=" + holder.getHealth() + " bareControl=" + bare.getHealth());
		helper.assertTrue(!first.isAlive() && !second.isAlive(), "a cow survived the kill");
		helper.assertTrue(holder.getHealth() == 10.0F + WayshrineRewards.CENSER_KILL_HEAL, "the holder's health is " + holder.getHealth() + " after a kill, not " + (10.0F + WayshrineRewards.CENSER_KILL_HEAL));
		helper.assertTrue(bare.getHealth() == 10.0F, "a kill with no censer healed to " + bare.getHealth());
		WayshrineTestSupport.leave(helper, holder);
		WayshrineTestSupport.leave(helper, bare);
		helper.succeed();
	}

	private static Cow cow(final GameTestHelper helper, final double x, final double z) {
		Cow cow = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		cow.addTag(com.stella.ruins.world.RuinMobKind.NO_SPAWN_ROLL_TAG);
		cow.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		cow.setNoAi(true);
		helper.getLevel().addFreshEntity(cow);
		return cow;
	}

	@GameTest
	public void theCenserIsTheSitesUniqueAndNothingElseIs(final GameTestHelper helper) {
		ItemStack unique = WayshrineSite.site().newUnique();
		helper.assertTrue(unique.is(WayshrineRewards.censer()) && WayshrineSite.site().hasUnique(), "the site's unique is " + unique);
		helper.assertTrue(com.stella.ruins.reward.RuinUniques.effectOf(unique).equals(WayshrineRewards.CENSER_EFFECT), "the censer carries effect " + com.stella.ruins.reward.RuinUniques.effectOf(unique));
		helper.assertTrue(com.stella.ruins.reward.RuinUniques.all().containsValue(WayshrineRewards.CENSER_EFFECT) && RuinSets.get("pilgrim").isPresent(), "the censer or the set is not registered");
		helper.succeed();
	}
}
