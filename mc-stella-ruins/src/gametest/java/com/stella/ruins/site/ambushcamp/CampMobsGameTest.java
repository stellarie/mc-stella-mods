package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 3 and 9: the three kinds, the Chief's hide and reveal, and each kind's drops at its table rate. */
public class CampMobsGameTest {
	static final int ROLLS = 10_000;

	private static Mob spawn(final GameTestHelper helper, final RuinMobKind kind, final double x, final double z) {
		Mob mob = kind.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)));
		mob.setNoAi(true);
		return mob;
	}

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Mob mob = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.COMMAND);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(LootContextParamSets.ENTITY);
		return table.getRandomItems(params, seed);
	}

	@GameTest
	public void aBanditIsAHoodedVindicatorWithAnAxe(final GameTestHelper helper) {
		Mob mob = spawn(helper, CampMobs.BANDIT, 3.5, 3.5);
		helper.assertTrue(mob.getType() == EntityTypes.VINDICATOR && RuinMobs.kindOf(mob) == CampMobs.BANDIT, "the bandit is no vindicator");
		helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE), "the bandit holds no axe");
		ItemStack hood = mob.getItemBySlot(EquipmentSlot.HEAD);
		helper.assertTrue(hood.is(Items.LEATHER_HELMET) && hood.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(0)).rgb() == CampMobs.HOOD_COLOR, "the hood is not brown leather");
		helper.assertTrue(CampMobs.BANDIT.drops().equals(CampMobs.BANDIT_DROPS) && mob.entityTags().contains(RuinMobKind.TAG_MOB), "the kind or its drops are wrong");
		helper.assertTrue(!CampMobs.isHidden(mob), "a bandit is hidden");
		CampTestSupport.result("banditKit", "head=" + hood + " main=" + mob.getItemBySlot(EquipmentSlot.MAINHAND));
		mob.discard();
		helper.succeed();
	}

	@GameTest
	public void aLookoutIsACappedSkeletonWithABow(final GameTestHelper helper) {
		Mob mob = spawn(helper, CampMobs.LOOKOUT, 3.5, 3.5);
		helper.assertTrue(mob.getType() == EntityTypes.SKELETON && RuinMobs.kindOf(mob) == CampMobs.LOOKOUT, "the lookout is no skeleton");
		helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW) && mob.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET), "the lookout lacks a bow or a cap");
		helper.assertTrue(CampMobs.LOOKOUT.drops().equals(CampMobs.LOOKOUT_DROPS), "the lookout drops the wrong table");
		mob.discard();
		helper.succeed();
	}

	@GameTest
	public void theChiefIsABossVindicatorAtScaleOnePointTwoWithFiveTimesTheHealth(final GameTestHelper helper) {
		Mob chief = spawn(helper, CampMobs.CHIEF, 3.5, 3.5);
		Mob plain = EntityTypes.VINDICATOR.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		double expected = plain.getAttributeBaseValue(Attributes.MAX_HEALTH) * CampMobs.CHIEF_HEALTH;
		CampTestSupport.result("chiefStats", "scale=" + chief.getAttributeValue(Attributes.SCALE) + " maxHealth=" + chief.getMaxHealth() + " expected=" + expected);
		helper.assertTrue(chief.getType() == EntityTypes.VINDICATOR && RuinMobs.kindOf(chief) == CampMobs.CHIEF && CampMobs.CHIEF.boss(), "the Chief is not a boss vindicator");
		helper.assertTrue(Math.abs(chief.getAttributeValue(Attributes.SCALE) - 1.2) < 1.0E-6, "scale " + chief.getAttributeValue(Attributes.SCALE));
		helper.assertTrue(Math.abs(chief.getMaxHealth() - expected) < 1.0E-3 && chief.getHealth() == chief.getMaxHealth(), "health " + chief.getHealth() + "/" + chief.getMaxHealth() + ", expected " + expected);
		helper.assertTrue(chief.isCustomNameVisible() && CampMobs.CHIEF.drops().equals(CampMobs.CHIEF_DROPS), "the Chief has no name tag or drops the wrong table");
		chief.discard();
		helper.succeed();
	}

	@GameTest
	public void hidingStripsTheChiefAndRevealingGivesHimBackHisGear(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		Mob chief = spawn(helper, CampMobs.CHIEF, 12.5, 12.5);
		CampMobs.hide(chief);
		helper.assertTrue(CampMobs.isHidden(chief) && chief.hasEffect(MobEffects.INVISIBILITY) && chief.isNoAi(), "hide did not hide");
		helper.assertTrue(chief.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && chief.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "a hidden Chief carries gear");
		helper.assertTrue(CampMobs.reveal(chief, player), "reveal said no");
		helper.assertTrue(!CampMobs.isHidden(chief) && chief.entityTags().contains(CampMobs.TAG_REVEALED) && !chief.hasEffect(MobEffects.INVISIBILITY) && !chief.isNoAi(), "reveal left him hidden");
		helper.assertTrue(chief.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_AXE), "the revealed Chief holds no axe");
		ItemStack hood = chief.getItemBySlot(EquipmentSlot.HEAD);
		helper.assertTrue(hood.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(0)).rgb() == CampMobs.CHIEF_HOOD_COLOR, "the Chief's hood is not his own");
		helper.assertTrue(chief.getTarget() == player, "the Chief does not hunt the player");
		helper.assertTrue(!CampMobs.reveal(chief, player), "a second reveal said yes");
		CampTestSupport.leave(helper, player);
		chief.discard();
		helper.succeed();
	}

	@GameTest
	public void aHiddenChiefRevealsWhenAPlayerStandsInsideFourBlocksOfHim(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		Vec3 playerAt = player.position();
		Mob far = spawn(helper, CampMobs.CHIEF, 1.5 + 10.0, 1.5);
		Mob near = spawn(helper, CampMobs.CHIEF, 1.5 + CampMobs.REVEAL_RADIUS - 1.0, 1.5);
		CampMobs.hide(far);
		CampMobs.hide(near);
		CampMobs.CHIEF.ability().tick(far, helper.getLevel());
		CampMobs.CHIEF.ability().tick(near, helper.getLevel());
		CampTestSupport.result("proximity", "far=" + player.distanceTo(far) + " near=" + player.distanceTo(near) + " from=" + playerAt);
		helper.assertTrue(CampMobs.isHidden(far), "a Chief " + player.distanceTo(far) + " blocks away was revealed");
		helper.assertTrue(!CampMobs.isHidden(near), "a Chief " + player.distanceTo(near) + " blocks away stayed hidden");
		CampTestSupport.leave(helper, player);
		far.discard();
		near.discard();
		helper.succeed();
	}

	@GameTest
	public void aSpectatorDoesNotRevealTheChief(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
		Mob chief = spawn(helper, CampMobs.CHIEF, 1.5 + 1.0, 1.5);
		CampMobs.hide(chief);
		CampMobs.CHIEF.ability().tick(chief, helper.getLevel());
		helper.assertTrue(CampMobs.isHidden(chief), "a spectator revealed the Chief");
		CampTestSupport.leave(helper, player);
		chief.discard();
		helper.succeed();
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261005L);
		int banditMarks = 0;
		int lookoutRolls = 0;
		boolean one = false;
		boolean three = false;
		int chiefMarks2 = 0;
		int chiefMarks3 = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, CampMobs.BANDIT_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(CampItems.BANDITS_MARK) && stack.getCount() == 1, "the bandit dropped " + stack);
				banditMarks++;
			}
			for (ItemStack stack : roll(level, CampMobs.LOOKOUT_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(CampItems.STOLEN_COIN) && stack.getCount() >= 1 && stack.getCount() <= 3, "the lookout dropped " + stack);
				lookoutRolls++;
				one |= stack.getCount() == 1;
				three |= stack.getCount() == 3;
			}
			int marks = 0;
			int ledgers = 0;
			for (ItemStack stack : roll(level, CampMobs.CHIEF_DROPS, master.nextLong())) {
				if (stack.is(CampItems.BANDITS_MARK)) {
					marks += stack.getCount();
				} else if (stack.is(CampItems.CHIEFS_LEDGER)) {
					ledgers += stack.getCount();
				} else {
					helper.fail("the Chief dropped " + stack);
				}
			}
			helper.assertTrue(ledgers == 1, "the Chief dropped " + ledgers + " ledgers");
			helper.assertTrue(marks == 2 || marks == 3, "the Chief dropped " + marks + " marks");
			chiefMarks2 += marks == 2 ? 1 : 0;
			chiefMarks3 += marks == 3 ? 1 : 0;
		}
		double banditRate = banditMarks / (double) ROLLS;
		double lookoutRate = lookoutRolls / (double) ROLLS;
		CampTestSupport.result("mobDrops", "rolls=" + ROLLS + " banditMarkRate=" + banditRate + " lookoutCoinRate=" + lookoutRate + " chiefMarks2=" + chiefMarks2 + " chiefMarks3=" + chiefMarks3);
		helper.assertTrue(Math.abs(banditRate - 0.20) <= 0.20 * 0.10, "bandit mark rate " + banditRate);
		helper.assertTrue(Math.abs(lookoutRate - 0.60) <= 0.60 * 0.05, "lookout coin rate " + lookoutRate);
		helper.assertTrue(one && three, "coin counts did not cover 1 and 3");
		helper.assertTrue(chiefMarks2 > ROLLS / 3 && chiefMarks3 > ROLLS / 3, "chief mark counts " + chiefMarks2 + "/" + chiefMarks3);
		helper.succeed();
	}

	/** A camp next to another ruin: the Chief's call must reach his own band only. */
	@GameTest
	public void aRevealCallsOnlyTheMobsOfItsOwnRun(final GameTestHelper helper) {
		ServerPlayer player = CampTestSupport.join(helper);
		String ownCore = RuinMobs.coreTag(0, 64, 0);
		String otherCore = RuinMobs.coreTag(2000, 64, 2000);
		Mob chief = spawn(helper, CampMobs.CHIEF, 1.5 + 1.0, 1.5);
		Mob own = spawn(helper, CampMobs.BANDIT, 1.5 + 3.0, 1.5);
		Mob foreignBandit = spawn(helper, CampMobs.BANDIT, 1.5 + 5.0, 1.5);
		Mob foreignLookout = spawn(helper, CampMobs.LOOKOUT, 1.5 + 7.0, 1.5);
		chief.addTag(ownCore);
		own.addTag(ownCore);
		foreignBandit.addTag(otherCore);
		foreignLookout.addTag(otherCore);
		CampMobs.hide(chief);
		int before = CampMobs.flaresFired();
		CampMobs.reveal(chief, player);
		int flares = CampMobs.flaresFired() - before;
		CampTestSupport.result("foreignCore", "own=" + (own.getTarget() == player) + " bandit=" + (foreignBandit.getTarget() == null)
			+ " lookout=" + (foreignLookout.getTarget() == null) + " flares=" + flares);
		helper.assertTrue(own.getTarget() == player, "the Chief did not call his own bandit");
		helper.assertTrue(foreignBandit.getTarget() == null && foreignLookout.getTarget() == null && flares == 0,
			"the Chief reached a mob of another run");
		CampTestSupport.leave(helper, player);
		helper.succeed();
	}
}
