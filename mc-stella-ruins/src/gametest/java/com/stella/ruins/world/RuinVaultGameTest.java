package com.stella.ruins.world;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/** The vault holds 27 stacks. What does not fit waits in the vault and drops at its feet when a player opens it. */
public class RuinVaultGameTest {
	static final int TICKS = 6000;
	static final int ROLLS = 40;

	static int dropped(final ServerLevel level, final RuinVaultBlockEntity vault) {
		int total = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(vault.getBlockPos()).inflate(3.0))) {
			ItemStack stack = entity.getItem();
			total += stack.is(Items.EMERALD) ? stack.getCount() : 0;
		}
		return total;
	}

	@GameTest(maxTicks = TICKS)
	public void aVaultFilledPastItsSlotsLosesNothingAndDropsTheRestWhenAPlayerOpensIt(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt(3, 3));
			RuinVaultBlockEntity vault = arena.vault();
			vault.fill(level, WorldTestInit.VAULT_LOOT, ROLLS);
			int inVault = RuinCoreGameTest.count(vault, Items.EMERALD);
			int before = dropped(level, vault);
			vault.createMenu(1, player.getInventory());
			int afterOpen = dropped(level, vault);
			int vaultAfter = RuinCoreGameTest.count(vault, Items.EMERALD);
			vault.createMenu(2, player.getInventory());
			int afterSecond = dropped(level, vault);
			WorldTestSupport.result("vaultOverflow", "rolls=" + ROLLS + " inVault=" + inVault + " droppedBeforeOpen=" + before + " droppedAfterOpen=" + afterOpen + " vaultAfter=" + vaultAfter + " afterSecondOpen=" + afterSecond);
			helper.assertTrue(inVault == RuinVaultBlockEntity.SIZE, "the vault holds " + inVault + " emeralds, not a full " + RuinVaultBlockEntity.SIZE + ", so the overflow check proves nothing");
			helper.assertTrue(before == 0, "the overflow dropped before anyone opened the vault: " + before);
			helper.assertTrue(vaultAfter + afterOpen == ROLLS, "the vault holds " + vaultAfter + " and the world " + afterOpen + " emeralds, not " + ROLLS + " in all");
			helper.assertTrue(afterSecond == afterOpen, "a second opening dropped the overflow again: " + afterSecond);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theOverflowSurvivesASaveAndALoadOfTheVault(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			RuinVaultBlockEntity vault = arena.vault();
			vault.fill(level, WorldTestInit.VAULT_LOOT, ROLLS);
			int waiting = vault.overflow().size();
			CompoundTag saved = vault.saveWithoutMetadata(level.registryAccess());
			RuinVaultBlockEntity loaded = new RuinVaultBlockEntity(vault.getBlockPos(), vault.getBlockState());
			loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
			int emeralds = 0;
			for (ItemStack stack : loaded.overflow()) {
				emeralds += stack.is(Items.EMERALD) ? stack.getCount() : 0;
			}
			WorldTestSupport.result("vaultOverflowSaved", "waiting=" + waiting + " loaded=" + loaded.overflow().size() + " emeralds=" + emeralds + " vaultEmeralds=" + RuinCoreGameTest.count(loaded, Items.EMERALD));
			helper.assertTrue(waiting == ROLLS - RuinVaultBlockEntity.SIZE, "the vault holds " + waiting + " waiting stacks, not " + (ROLLS - RuinVaultBlockEntity.SIZE));
			helper.assertTrue(loaded.overflow().size() == waiting && emeralds == waiting, "the load holds " + loaded.overflow().size() + " waiting stacks with " + emeralds + " emeralds, not " + waiting);
			helper.assertTrue(RuinCoreGameTest.count(loaded, Items.EMERALD) == RuinVaultBlockEntity.SIZE, "the load lost slot items");
		});
	}
}
