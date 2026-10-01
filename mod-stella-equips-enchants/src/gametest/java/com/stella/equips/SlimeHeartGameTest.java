package com.stella.equips;

import com.stella.equips.heart.SlimeHeart;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/** The slime heart: its drop pool, the anvil insert, and the split of an attack into now and five later parts. */
public final class SlimeHeartGameTest {
	private static final float HIT = 8.0F;

	@GameTest(maxTicks = 200)
	public void dropPool(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootTable.Builder builder = LootTable.lootTable();
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse("mod-stella-mobs:entities/red_slime"));
		LootTableEvents.MODIFY.invoker().modifyLootTable(key, builder, LootTableSource.MOD, level.registryAccess());
		LootTable table = builder.build();
		int rolls = 200_000;
		int hearts = 0;
		LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
		for (int roll = 0; roll < rolls; roll++) {
			for (ItemStack stack : table.getRandomItems(params)) {
				if (stack.is(SlimeHeart.ITEM)) {
					hearts += stack.getCount();
				}
			}
		}
		StellaEquips.LOGGER.info("[BEHAVIOR] test=slimeHeartDrop RESULT rolls={} hearts={} maxStack={}", rolls, hearts, new ItemStack(SlimeHeart.ITEM).getMaxStackSize());
		helper.assertTrue(hearts >= 5 && hearts <= 45, hearts + " hearts in " + rolls + " rolls, expected about 20");
		helper.assertTrue(new ItemStack(SlimeHeart.ITEM).getMaxStackSize() == 64, "the heart does not stack to 64");
		helper.succeed();
	}

	@GameTest
	public void anvilInserts(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)));
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.IRON_CHESTPLATE));
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(SlimeHeart.ITEM, 3));
		menu.createResult();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		menu.getSlot(AnvilMenu.RESULT_SLOT).onTake(player, menu.getSlot(AnvilMenu.RESULT_SLOT).getItem());
		int heartsLeft = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount();
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(result.copy());
		menu.createResult();
		boolean second = !menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty();
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.IRON_SWORD));
		menu.createResult();
		boolean sword = !menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty();
		StellaEquips.LOGGER.info("[BEHAVIOR] test=slimeHeartAnvil RESULT result={} heart={} left={} second={} sword={}",
			result, SlimeHeart.holdsHeart(result), heartsLeft, second, sword);
		helper.assertTrue(result.is(Items.IRON_CHESTPLATE) && SlimeHeart.holdsHeart(result), "the anvil gave " + result);
		helper.assertTrue(heartsLeft == 2, "the insert used " + (3 - heartsLeft) + " hearts");
		helper.assertTrue(!second && !sword, "a second heart or a sword took a heart");
		helper.succeed();
	}

	/** Two heart pieces: 75% now, 25% in five parts over five seconds, the same total; a fall is not split. */
	@GameTest(maxTicks = 200)
	public void splitsAttack(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
		Zombie hearted = victim(helper, 3.5, true);
		Zombie control = victim(helper, 5.5, false);
		float heartedStart = hearted.getHealth();
		float controlStart = control.getHealth();
		hearted.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
		control.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
		float now = heartedStart - hearted.getHealth();
		float full = controlStart - control.getHealth();
		float[] midway = new float[1];
		helper.runAfterDelay(SlimeHeart.DRAIN_INTERVAL * 2 + 5, () -> midway[0] = heartedStart - hearted.getHealth());
		helper.runAfterDelay(SlimeHeart.DRAIN_INTERVAL * SlimeHeart.DRAIN_STEPS + 10, () -> {
			float total = heartedStart - hearted.getHealth();
			hearted.damageCooldownTime = 0;
			control.damageCooldownTime = 0;
			float heartedBefore = hearted.getHealth();
			float controlBefore = control.getHealth();
			hearted.hurtServer(level, level.damageSources().fall(), 4.0F);
			control.hurtServer(level, level.damageSources().fall(), 4.0F);
			float heartedFall = heartedBefore - hearted.getHealth();
			float controlFall = controlBefore - control.getHealth();
			StellaEquips.LOGGER.info("[BEHAVIOR] test=slimeHeartSplit RESULT full={} now={} midway={} total={} fall={}/{}",
				full, now, midway[0], total, heartedFall, controlFall);
			helper.assertTrue(full > 0.0F && Math.abs(now - full * 0.75F) < 0.01F, "the hit landed " + now + " of " + full + " at once");
			helper.assertTrue(Math.abs(midway[0] - (now + full * 0.25F * 2 / 5)) < 0.01F, "after two seconds " + midway[0] + " had landed");
			helper.assertTrue(Math.abs(total - full) < 0.01F, "in total " + total + " landed, expected " + full);
			helper.assertTrue(Math.abs(heartedFall - controlFall) < 0.01F && controlFall > 0.0F, "a fall split: " + heartedFall + " against " + controlFall);
			helper.succeed();
		});
	}

	private static Zombie victim(final GameTestHelper helper, final double x, final boolean hearts) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, 3.5));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
		if (hearts) {
			chest.set(SlimeHeart.INSERTED, true);
			legs.set(SlimeHeart.INSERTED, true);
		}
		zombie.setItemSlot(EquipmentSlot.CHEST, chest);
		zombie.setItemSlot(EquipmentSlot.LEGS, legs);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		zombie.setHealth(500.0F);
		return zombie;
	}
}
