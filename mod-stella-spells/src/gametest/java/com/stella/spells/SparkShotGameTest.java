package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class SparkShotGameTest {
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");

	@GameTest
	public void sparkShotIsRegisteredAsOneBook(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "sparkShotIsRegisteredAsOneBook");
		Item item = BuiltInRegistries.ITEM.get(SPARK_SHOT).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item != Items.AIR, "Spark Shot item is not registered");
		helper.assertTrue(item.getDefaultMaxStackSize() == 1, "Spark Shot item is stackable");
		outcome.pass();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void sparkShotDamagesAndBurnsAnAimedHostile(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "sparkShotDamagesAndBurnsAnAimedHostile");
		Item item = BuiltInRegistries.ITEM.get(SPARK_SHOT).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item != Items.AIR, "Spark Shot item is not registered");
		helper.setTime(18_000L);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 playerPosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		ItemStack book = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(healthBefore - target.getHealth() == 3.0f, "Spark Shot did not deal 3 damage");
		helper.assertTrue(target.getRemainingFireTicks() >= 40, "Spark Shot did not burn the target for 2 seconds");
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "Spark Shot consumed the book");
		helper.assertTrue(player.getCooldowns().isOnCooldown(book), "Spark Shot did not start its cooldown");
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void sparkShotRecipeUsesOnlyTheFlintCornerX(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "sparkShotRecipeUsesOnlyTheFlintCornerX");
		CraftingInput correct = CraftingInput.of(
			3, 3, List.of(
				new ItemStack(Items.FLINT), ItemStack.EMPTY, new ItemStack(Items.FLINT),
				ItemStack.EMPTY, new ItemStack(Items.BOOK), ItemStack.EMPTY,
				new ItemStack(Items.FLINT), ItemStack.EMPTY, new ItemStack(Items.FLINT)
			)
		);
		var manager = helper.getLevel().getServer().getRecipeManager();
		var match = manager.getRecipeFor(RecipeType.CRAFTING, correct, helper.getLevel());
		helper.assertTrue(match.isPresent(), "the book and four flints in an X did not match");
		ItemStack output = match.orElseThrow().value().assemble(correct);
		helper.assertTrue(
			SPARK_SHOT.equals(BuiltInRegistries.ITEM.getKey(output.getItem())) && output.getCount() == 1,
			"the X recipe did not return one Spark Shot book"
		);
		CraftingInput extraSide = CraftingInput.of(
			3, 3, List.of(
				new ItemStack(Items.FLINT), new ItemStack(Items.FLINT), new ItemStack(Items.FLINT),
				ItemStack.EMPTY, new ItemStack(Items.BOOK), ItemStack.EMPTY,
				new ItemStack(Items.FLINT), ItemStack.EMPTY, new ItemStack(Items.FLINT)
			)
		);
		helper.assertTrue(
			manager.getRecipeFor(RecipeType.CRAFTING, extraSide, helper.getLevel()).isEmpty(),
			"the Spark Shot recipe accepted an extra side ingredient"
		);
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void invalidSparkShotTargetHasNoEffectOrCooldown(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "invalidSparkShotTargetHasNoEffectOrCooldown");
		Item item = BuiltInRegistries.ITEM.get(SPARK_SHOT).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item != Items.AIR, "Spark Shot item is not registered");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 playerPosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		helper.setBlock(2, 2, 4, Blocks.STONE);
		float healthBefore = target.getHealth();
		ItemStack book = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getHealth() == healthBefore, "Spark Shot affected a target behind a block");
		helper.assertFalse(player.getCooldowns().isOnCooldown(book), "an invalid cast started a cooldown");
		outcome.pass();
		helper.succeed();
	}

	@GameTest
	public void sparkShotCooldownUsesSixtyTicksAndIsPerItem(final GameTestHelper helper) {
		SpellTestLog.Outcome outcome = SpellTestLog.track(helper, "sparkShotCooldownUsesSixtyTicksAndIsPerItem");
		int cooldownTicks = SpellbookTestAccess.cooldownTicks();
		helper.assertTrue(cooldownTicks == 60, "Spark Shot production cooldown is not 60 ticks: " + cooldownTicks);
		Item item = BuiltInRegistries.ITEM.get(SPARK_SHOT).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item != Items.AIR, "Spark Shot item is not registered");
		helper.setTime(18_000L);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 playerPosition = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(playerPosition.x, playerPosition.y, playerPosition.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float healthBefore = target.getHealth();
		ItemStack book = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, book);
		item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(healthBefore - target.getHealth() == 3.0f, "a successful cast did not deal 3 damage");
		helper.assertTrue(player.getCooldowns().isOnCooldown(book), "a successful cast did not start a cooldown");
		helper.assertFalse(
			player.getCooldowns().isOnCooldown(Items.BOOK.getDefaultInstance()),
			"Spark Shot cooldown affected a different item"
		);
		target.discard();
		Mob recastTarget = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float recastHealth = recastTarget.getHealth();
		item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(recastTarget.getHealth() == recastHealth, "an immediate recast bypassed the cooldown");
		for (int tick = 1; tick < cooldownTicks; tick++) {
			player.doTick();
		}
		helper.assertTrue(player.getCooldowns().isOnCooldown(book), "the cooldown expired before 60 player ticks");
		player.doTick();
		helper.assertFalse(player.getCooldowns().isOnCooldown(book), "the cooldown lasted more than 60 player ticks");
		outcome.pass();
		helper.succeed();
	}
}
