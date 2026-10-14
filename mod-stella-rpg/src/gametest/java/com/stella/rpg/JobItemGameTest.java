package com.stella.rpg;

import com.stella.rpg.job.Bleed;
import com.stella.rpg.job.Job;
import com.stella.rpg.job.JobItems;
import com.stella.rpg.job.JobPerks;
import com.stella.rpg.job.Jobs;
import com.stella.rpg.job.MechanicalBow;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** The job items: the grant, the bow, the sword, the garbs, and their refine perks. */
public final class JobItemGameTest {
	private static final float HIT = 4.0F;

	@GameTest
	public void grantGivesTheJobItem(final GameTestHelper helper) {
		Object[][] cases = {
			{Job.ARCHER, JobItems.MECHANICAL_BOW}, {Job.FIGHTER, JobItems.KNIGHT_SWORD},
			{Job.MAGE, JobItems.MAGE_STAFF}, {Job.PRIEST, JobItems.PRIEST_GARBS}
		};
		for (Object[] row : cases) {
			ServerPlayer player = RpgTestSupport.player(helper);
			player.getInventory().clearContent();
			player.experienceLevel = 10;
			helper.assertTrue(!Jobs.grantItems(player), "items went to a player without a job");
			helper.assertTrue(Jobs.choose(player, (Job) row[0]), row[0] + " refused");
			helper.assertTrue(Jobs.grantItems(player), "grantItems gave nothing for " + row[0]);
			Item expected = (Item) row[1];
			int found = 0;
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty()) {
					helper.assertTrue(stack.is(expected), row[0] + " got " + stack.getItem());
					found += stack.getCount();
				}
			}
			RpgTestSupport.result("grantGivesTheJobItem", row[0] + " -> " + expected);
			helper.assertTrue(found == 1, row[0] + " got " + found + " items");
		}
		helper.assertTrue(JobItems.MECHANICAL_BOW.getDefaultInstance().getMaxDamage() == MechanicalBow.DURABILITY, "bow durability");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void bowDrawsFastAndDoubles(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		ItemStack bow = bowFor(player, 0);
		helper.assertTrue(bow.getMaxDamage() == 1000, "durability " + bow.getMaxDamage());
		try {
			JobItems.setRoller((who, random, chance) -> false);
			List<AbstractArrow> full = shoot(helper, player, bow, 10);
			List<AbstractArrow> early = shoot(helper, player, bow, 9);
			double fullSpeed = full.getFirst().getDeltaMovement().length();
			double earlySpeed = early.getFirst().getDeltaMovement().length();
			RpgTestSupport.result("bowDrawsFastAndDoubles", "full=" + fullSpeed + " early=" + earlySpeed + " arrows=" + full.size());
			helper.assertTrue(fullSpeed >= 2.9, "10 ticks drew to speed " + fullSpeed);
			helper.assertTrue(earlySpeed < 2.9, "9 ticks drew to speed " + earlySpeed);
			helper.assertTrue(full.size() == 1, "no roll fired " + full.size() + " arrows");

			JobItems.setRoller((who, random, chance) -> true);
			List<AbstractArrow> doubled = shoot(helper, player, bow, 10);
			helper.assertTrue(doubled.size() == 2, "a roll fired " + doubled.size() + " arrows");
			long free = doubled.stream().filter(arrow -> arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY).count();
			helper.assertTrue(free == 1, "free arrows that survival cannot pick up: " + free);
			helper.assertTrue(bow.getDamageValue() == 3, "three shots used " + bow.getDamageValue() + " durability");
		} finally {
			JobItems.setRoller(null);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 140)
	public void swordBleeds(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(JobItems.KNIGHT_SWORD));
		Zombie victim = zombie(helper);
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		ItemStack plain = new ItemStack(Items.IRON_SWORD);
		double withSword = RpgCombat.armorIgnored(victim, source);
		player.setItemSlot(EquipmentSlot.MAINHAND, plain);
		double without = RpgCombat.armorIgnored(victim, source);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(JobItems.KNIGHT_SWORD));
		helper.assertTrue(Math.abs(withSword - without - 0.10) < 1e-9, "sword " + withSword + " plain " + without);

		float before;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
			JobItems.setRoller((who, random, chance) -> true);
			victim.hurtServer(helper.getLevel(), source, HIT);
			helper.assertTrue(Bleed.isBleeding(victim), "the hit started no bleed");
			victim.setInvulnerableTime(0);
			victim.hurtServer(helper.getLevel(), source, HIT);
			before = victim.getHealth();
		} finally {
			JobItems.setRoller(null);
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		victim.setPermanentlyInvulnerable(true);
		helper.runAfterDelay(110, () -> {
			float after = victim.getHealth();
			float expected = (float) (before * Math.pow(0.95, 5));
			RpgTestSupport.result("swordBleeds", "before=" + before + " after=" + after + " expected=" + expected);
			helper.assertTrue(Math.abs(after - expected) < 0.01F * before, "health " + before + " became " + after + ", want " + expected);
			helper.assertTrue(!Bleed.isBleeding(victim), "the bleed outlived five seconds");
			helper.succeed();
		});
	}

	@GameTest
	public void garbs(final GameTestHelper helper) {
		ServerPlayer priest = RpgTestSupport.player(helper);
		ServerPlayer plain = RpgTestSupport.player(helper);
		priest.setItemSlot(EquipmentSlot.CHEST, new ItemStack(JobItems.PRIEST_GARBS));
		float undead = undeadShare(helper, priest);
		RpgTestSupport.result("garbs", "undead=" + undead);
		helper.assertTrue(Math.abs(undead - 0.8F) < 0.001F, "undead damage share " + undead);
		helper.assertTrue(Math.abs(undeadShare(helper, plain) - 1.0F) < 0.001F, "a player without garbs takes less from undead");

		priest.experienceLevel = 20;
		plain.experienceLevel = 20;
		double base = RpgSpells.spellHeal(plain, 10.0);
		double healed = RpgSpells.spellHeal(priest, 10.0);
		helper.assertTrue(Math.abs(healed / base - 1.5) < 1e-6, "heal ratio " + healed / base);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void refinePerks(final GameTestHelper helper) {
		helper.assertTrue(JobPerks.bowFreeArrow(6) == 0.25 && JobPerks.bowFreeArrow(7) == 0.35 && JobPerks.bowFreeArrow(15) == 0.50, "bow free arrow");
		helper.assertTrue(JobPerks.bowDrawTicks(11) == 10 && JobPerks.bowDrawTicks(12) == 7, "bow draw");
		helper.assertTrue(JobPerks.swordBleedChance(6) == 0.10 && JobPerks.swordBleedChance(7) == 0.15, "sword bleed");
		helper.assertTrue(JobPerks.garbsUndeadReduction(9) == 0.20 && JobPerks.garbsUndeadReduction(10) == 0.30, "garbs undead");

		// bow at +15: the fifth shot fires 3 arrows, every shot pierces 1 more target
		ServerPlayer archer = RpgTestSupport.realPlayer(helper);
		ItemStack bow = bowFor(archer, 15);
		int total = 0;
		int pierce = 0;
		try {
			JobItems.setRoller((who, random, chance) -> false);
			for (int i = 0; i < 5; i++) {
				List<AbstractArrow> arrows = shoot(helper, archer, bow, 10);
				total += arrows.size();
				pierce += arrows.getFirst().getPierceLevel();
			}
			double fast = shoot(helper, archer, bow, 7).getFirst().getDeltaMovement().length();
			helper.assertTrue(fast >= 2.9, "7 ticks at +15 drew to speed " + fast);
		} finally {
			JobItems.setRoller(null);
		}
		RpgTestSupport.result("refinePerks", "arrows=" + total + " pierce=" + pierce);
		helper.assertTrue(total == 7, "five shots fired " + total + " arrows");
		helper.assertTrue(pierce == 5, "pierce levels " + pierce);

		// sword at +15: a hit on a bleeding target deals 20 percent more
		ServerPlayer fighter = RpgTestSupport.player(helper);
		fighter.setItemSlot(EquipmentSlot.MAINHAND, refined(JobItems.KNIGHT_SWORD, 15));
		DamageSource source = helper.getLevel().damageSources().playerAttack(fighter);
		Zombie calm = zombie(helper);
		Zombie bleeding = zombie(helper);
		Bleed.apply(bleeding, 0.05);
		double lossCalm;
		double lossBleeding;
		try {
			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
			JobItems.setRoller((who, random, chance) -> false);
			lossCalm = loss(helper, calm, source);
			lossBleeding = loss(helper, bleeding, source);
		} finally {
			JobItems.setRoller(null);
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("refinePerks", "calm=" + lossCalm + " bleeding=" + lossBleeding);
		helper.assertTrue(Math.abs(lossBleeding / lossCalm - 1.2) < 0.01, "bleeding ratio " + lossBleeding / lossCalm);

		// garbs at +15: 30 percent less from undead, the heal cleanses one effect and grants Regeneration
		ServerPlayer priest = RpgTestSupport.player(helper);
		priest.setItemSlot(EquipmentSlot.CHEST, refined(JobItems.PRIEST_GARBS, 15));
		float undead = undeadShare(helper, priest);
		helper.assertTrue(Math.abs(undead - 0.7F) < 0.001F, "garbs +15 undead share " + undead);

		ServerPlayer ally = RpgTestSupport.player(helper);
		ally.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400));
		RpgSpells.spellHeal(priest, 10.0);
		ally.heal(1.0F);
		RpgTestSupport.result("refinePerks", "weakness=" + ally.hasEffect(MobEffects.WEAKNESS) + " regen=" + ally.hasEffect(MobEffects.REGENERATION));
		helper.assertTrue(!ally.hasEffect(MobEffects.WEAKNESS), "the heal kept the harmful effect");
		helper.assertTrue(ally.hasEffect(MobEffects.REGENERATION), "the heal gave no Regeneration");
		helper.succeed();
	}

	private static ItemStack refined(final Item item, final int level) {
		ItemStack stack = new ItemStack(item);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("stella_refine", level));
		return stack;
	}

	private static ItemStack bowFor(final ServerPlayer player, final int refine) {
		ItemStack bow = refined(JobItems.MECHANICAL_BOW, refine);
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.getInventory().add(new ItemStack(Items.ARROW, 64));
		return bow;
	}

	/** Draws the bow for the held ticks, releases it, and returns the arrows that release created. */
	private static List<AbstractArrow> shoot(final GameTestHelper helper, final ServerPlayer player, final ItemStack bow, final int held) {
		Set<Integer> known = new HashSet<>();
		for (AbstractArrow arrow : arrowsOf(helper, player)) {
			known.add(arrow.getId());
		}
		player.startUsingItem(InteractionHand.MAIN_HAND);
		bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getUseDuration(player) - held);
		player.stopUsingItem();
		List<AbstractArrow> created = new ArrayList<>();
		for (AbstractArrow arrow : arrowsOf(helper, player)) {
			if (!known.contains(arrow.getId())) {
				created.add(arrow);
			}
		}
		helper.assertTrue(!created.isEmpty(), "the release fired no arrow at " + held + " ticks");
		return created;
	}

	private static List<AbstractArrow> arrowsOf(final GameTestHelper helper, final ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(16.0),
			arrow -> arrow.getOwner() == player);
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	private static double loss(final GameTestHelper helper, final Zombie victim, final DamageSource source) {
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), source, HIT);
		return before - victim.getHealth();
	}

	/** The share of a hit that the player keeps against a zombie, compared with a spider. */
	private static float undeadShare(final GameTestHelper helper, final ServerPlayer player) {
		Zombie zombie = zombie(helper);
		Spider spider = helper.spawnMob(EntityTypes.SPIDER, 1, 1, 2).withNoFreeWill().spawn();
		float fromZombie = RpgCombat.scaleIncoming(player, helper.getLevel().damageSources().mobAttack(zombie), 6.0F);
		float fromSpider = RpgCombat.scaleIncoming(player, helper.getLevel().damageSources().mobAttack(spider), 6.0F);
		helper.assertTrue(fromSpider > 0.0F, "the control hit did no damage");
		return fromZombie / fromSpider;
	}
}
