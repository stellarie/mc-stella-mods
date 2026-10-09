package com.stella.ruins.world;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Item 8: wave, offer, keys and sequence objectives each complete, and the core's block carries the item uses. */
public class ObjectiveGameTest {
	static final int TICKS = 6000;

	/** Uses the core block with the item in the player's main hand, as a right click does. */
	static InteractionResult useCore(final WorldTestSupport.Arena arena, final ServerPlayer player, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos pos = arena.corePos();
		return arena.level().getBlockState(pos).useItemOn(stack, arena.level(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	@GameTest(maxTicks = TICKS)
	public void aWaveObjectiveSpawnsEachWaveWhenTheLastIsDeadAndCompletesAfterTheFinalOne(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.waves, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().size() == 2, "wave one holds " + arena.entry().roster().size() + " mobs, not 2");
			arena.freeze();
			arena.killRoster(player);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().size() == 1, "wave two holds " + arena.entry().roster().size() + " mobs, not 1; phase " + arena.phase());
			helper.assertTrue(arena.vault().sealed(), "the vault opened between waves");
			arena.freeze();
			arena.killRoster(player);
			WorldTestSupport.result("wavesComplete", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the final wave's death left the core " + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void anOfferObjectiveSpendsOneAcceptedItemAndIgnoresTheRest(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.offer, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core did not wake");
			ItemStack wrong = new ItemStack(Items.STICK, 4);
			InteractionResult ignored = useCore(arena, player, wrong);
			helper.assertTrue(!ignored.consumesAction() && wrong.getCount() == 4 && arena.phase() == RuinPhase.ACTIVE, "a wrong item was spent or counted");
			ItemStack diamonds = new ItemStack(Items.DIAMOND, 2);
			InteractionResult taken = useCore(arena, player, diamonds);
			WorldTestSupport.result("offerComplete", "taken=" + taken.consumesAction() + " left=" + diamonds.getCount() + " phase=" + arena.phase());
			helper.assertTrue(taken.consumesAction(), "the offered diamond was not taken");
			helper.assertTrue(diamonds.getCount() == 1, "the offer spent " + (2 - diamonds.getCount()) + " diamonds, not 1");
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the offer left the core " + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aKeysObjectiveNeedsEveryKeyAndKeepsTheCountBetweenUses(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.keys, arena -> {
			ServerPlayer player = arena.wake();
			ItemStack ingots = new ItemStack(Items.GOLD_INGOT, 5);
			useCore(arena, player, ingots);
			useCore(arena, player, ingots);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && ingots.getCount() == 3, "two keys left the core " + arena.phase() + " with " + ingots.getCount() + " ingots");
			helper.assertTrue(arena.entry().objective().getIntOr("used", -1) == 2, "the saved count is " + arena.entry().objective().getIntOr("used", -1));
			useCore(arena, player, ingots);
			WorldTestSupport.result("keysComplete", "left=" + ingots.getCount() + " phase=" + arena.phase());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && ingots.getCount() == 2, "the third key left the core " + arena.phase() + " with " + ingots.getCount() + " ingots");
			ItemStack extra = new ItemStack(Items.GOLD_INGOT, 1);
			helper.assertTrue(!useCore(arena, player, extra).consumesAction() && extra.getCount() == 1, "a key was spent after completion");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aSequenceObjectiveRunsItsStepsInOrder(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.sequence, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().isEmpty(), "the offer step spawned mobs");
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().isEmpty(), "the sequence moved on without the offer");
			useCore(arena, player, new ItemStack(Items.DIAMOND, 1));
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().size() == 1, "after the offer the wave step holds " + arena.entry().roster().size() + " mobs; phase " + arena.phase());
			ItemStack late = new ItemStack(Items.DIAMOND, 1);
			helper.assertTrue(!useCore(arena, player, late).consumesAction() && late.getCount() == 1, "the finished offer step took a second diamond");
			arena.freeze();
			arena.killRoster(player);
			WorldTestSupport.result("sequenceComplete", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the sequence ended " + arena.phase());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aChooseObjectiveStartsTheBranchOfTheItemOfferedAndThenIgnoresTheOtherOffer(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.chooser, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().isEmpty(), "the core woke with mobs before any offer");
			ItemStack wrong = new ItemStack(Items.STICK, 2);
			helper.assertTrue(!useCore(arena, player, wrong).consumesAction() && wrong.getCount() == 2 && arena.entry().roster().isEmpty(), "a wrong item chose a branch");
			ItemStack diamonds = new ItemStack(Items.DIAMOND, 2);
			helper.assertTrue(useCore(arena, player, diamonds).consumesAction() && diamonds.getCount() == 1, "the diamond was not spent");
			helper.assertTrue(arena.entry().roster().size() == 1, "the diamond branch holds " + arena.entry().roster().size() + " mobs, not 1");
			ItemStack gold = new ItemStack(Items.GOLD_INGOT, 2);
			helper.assertTrue(!useCore(arena, player, gold).consumesAction() && gold.getCount() == 2 && arena.entry().roster().size() == 1, "a second offer changed the branch");
			int base = RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), Vec3.atCenterOf(arena.corePos())));
			arena.freeze();
			arena.killRoster(player);
			int emeralds = RuinCoreGameTest.count(arena.vault(), Items.EMERALD);
			WorldTestSupport.result("chooseDiamond", "roster=1 emeralds=" + emeralds + " base=" + base);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && emeralds == base, "the diamond branch left " + arena.phase() + " with " + emeralds + " rolls, not " + base);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aChoiceCanAddVaultRollsAndTheChoiceSurvivesASaveOfTheObjective(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.chooser, arena -> {
			ServerPlayer player = arena.wake();
			helper.assertTrue(useCore(arena, player, new ItemStack(Items.GOLD_INGOT, 2)).consumesAction(), "the gold was not taken");
			helper.assertTrue(arena.entry().roster().size() == 2, "the gold branch holds " + arena.entry().roster().size() + " mobs, not 2");
			Objective reloaded = WorldTestInit.chooser.newObjective();
			reloaded.load(arena.entry().objective());
			helper.assertTrue(reloaded.extraRolls() == 2, "the reloaded objective adds " + reloaded.extraRolls() + " rolls, not 2");
			int base = RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), Vec3.atCenterOf(arena.corePos())));
			arena.freeze();
			arena.killRoster(player);
			int emeralds = RuinCoreGameTest.count(arena.vault(), Items.EMERALD);
			WorldTestSupport.result("chooseGold", "emeralds=" + emeralds + " base=" + base);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && emeralds == base + 2, "the gold branch paid " + emeralds + " rolls, not " + (base + 2));
		});
	}
}
