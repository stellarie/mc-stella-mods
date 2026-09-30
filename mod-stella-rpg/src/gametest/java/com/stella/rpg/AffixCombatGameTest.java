package com.stella.rpg;

import com.stella.rpg.roll.Affixes;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Damage, HP, and reduction affixes reach the numbers a fight uses, in vanilla units before K. */
public final class AffixCombatGameTest {
	private static final float TOLERANCE = 0.001F;
	private static final float HIT = 4.0F;

	@GameTest
	public void damageAffixes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		Zombie victim = zombie(helper);
		float k = RpgScale.kf();
		DamageSource melee = level.damageSources().playerAttack(player);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> 0);
		try {
			float plain = RpgCombat.scaleOutgoing(victim, melee, HIT);
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("damage_flat", 12, "damage_pct", 55)));
			float sword = RpgCombat.scaleOutgoing(victim, melee, HIT);
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			level.addFreshEntity(arrow);
			DamageSource shot = level.damageSources().arrow(arrow, player);
			player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			float bowless = RpgCombat.scaleOutgoing(victim, shot, HIT);
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.BOW, Map.of("damage_flat", 30, "damage_pct", 20)));
			float bow = RpgCombat.scaleOutgoing(victim, shot, HIT);
			arrow.discard();
			player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.OFFHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("damage_flat", 12)));
			float offhand = RpgCombat.scaleOutgoing(victim, melee, HIT);
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_CHESTPLATE, Map.of("damage_flat", 12)));
			float wrongKind = RpgCombat.scaleOutgoing(victim, melee, HIT);

			RpgTestSupport.result("damageAffixes", "plain=" + plain + " sword=" + sword + " bowless=" + bowless + " bow=" + bow
				+ " offhand=" + offhand + " wrongKind=" + wrongKind);
			helper.assertTrue(near(plain, HIT), "no affix changed the hit to " + plain);
			helper.assertTrue(near(sword, (HIT + 12.0F / k) * 1.055F), "melee " + sword + " expected " + (HIT + 12.0F / k) * 1.055F);
			helper.assertTrue(near(bowless, HIT), "an arrow from an empty hand read an affix: " + bowless);
			helper.assertTrue(near(bow, (HIT + 30.0F / k) * 1.02F), "arrow " + bow + " expected " + (HIT + 30.0F / k) * 1.02F);
			helper.assertTrue(near(offhand, HIT) && near(wrongKind, HIT), "an affix on the wrong slot or item kind counted");
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		helper.succeed();
	}

	@GameTest
	public void damageAffixReachesHealth(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.player(helper);
		player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("damage_flat", 15, "damage_pct", 100)));
		Zombie victim = zombie(helper);
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> 0);
		float loss;
		try {
			victim.hurtServer(level, level.damageSources().playerAttack(player), HIT);
			loss = victim.getMaxHealth() - victim.getHealth();
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		float expected = RpgTestSupport.mobGuard((HIT + 15.0F / RpgScale.kf()) * 1.10F) * RpgScale.kf();
		RpgTestSupport.result("damageAffixReachesHealth", "loss=" + loss + " expected=" + expected);
		helper.assertTrue(Math.abs(loss - expected) < 0.01F, "loss " + loss + " expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void healthAffixes(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		float k = RpgScale.kf();
		float base = player.getMaxHealth();
		player.setItemSlot(EquipmentSlot.CHEST, RpgTestSupport.withAffixes(Items.IRON_CHESTPLATE, Map.of("hp_flat", 60, "hp_pct", 30)));
		player.setItemSlot(EquipmentSlot.LEGS, RpgTestSupport.withAffixes(Items.IRON_LEGGINGS, Map.of("hp_flat", 40, "hp_pct", 20)));
		player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("hp_flat", 500)));
		RpgAttributes.refresh(player);
		float worn = player.getMaxHealth();
		int modifiers = countAffixModifiers(player);
		RpgAttributes.refresh(player);
		float again = player.getMaxHealth();
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		RpgAttributes.refresh(player);
		float legsOnly = player.getMaxHealth();
		player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		RpgAttributes.refresh(player);
		float removed = player.getMaxHealth();
		float expectedWorn = (20.0F + 100.0F / k) * 1.05F * k;
		float expectedLegs = (20.0F + 40.0F / k) * 1.02F * k;
		RpgTestSupport.result("healthAffixes", "base=" + base + " worn=" + worn + " expected=" + expectedWorn + " legsOnly=" + legsOnly
			+ " removed=" + removed + " modifiers=" + modifiers);
		helper.assertTrue(near(base, 20.0F * k) && near(removed, base), "base " + base + " removed " + removed);
		helper.assertTrue(Math.abs(worn - expectedWorn) < 0.01F, "worn " + worn + " expected " + expectedWorn);
		helper.assertTrue(again == worn && modifiers == 2, "a second refresh changed max health or the modifier count " + modifiers);
		helper.assertTrue(Math.abs(legsOnly - expectedLegs) < 0.01F, "legs only " + legsOnly + " expected " + expectedLegs);
		helper.succeed();
	}

	@GameTest
	public void reductionAffixes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.VIT, 100);
		float k = RpgScale.kf();
		DamageSource generic = level.damageSources().generic();
		float plain = RpgCombat.scaleIncoming(player, generic, 10.0F);
		player.setItemSlot(EquipmentSlot.CHEST, RpgTestSupport.withAffixes(Items.IRON_CHESTPLATE, Map.of("reduction_flat", 30, "reduction_pct", 15)));
		player.setItemSlot(EquipmentSlot.LEGS, RpgTestSupport.withAffixes(Items.IRON_LEGGINGS, Map.of("reduction_flat", 15, "reduction_pct", 25)));
		float worn = RpgCombat.scaleIncoming(player, generic, 10.0F);
		float clamped = RpgCombat.scaleIncoming(player, generic, 0.5F);
		float vit = (float) RpgCurve.damageTakenMultiplier(StatBonuses.effective(player, Stat.VIT));
		float expected = 10.0F * vit * (1.0F - 0.04F) - DefenseBonuses.flat(player) - 45.0F / k;
		float loss = healthLoss(helper, player, 10.0F);
		RpgTestSupport.result("reductionAffixes", "plain=" + plain + " worn=" + worn + " expected=" + expected + " clamped=" + clamped
			+ " loss=" + loss);
		helper.assertTrue(plain > 0.0F, "plain " + plain);
		helper.assertTrue(near(worn, expected), "worn " + worn + " expected " + expected);
		helper.assertTrue(clamped == 0.0F, "a small hit healed or hurt: " + clamped);
		helper.assertTrue(Math.abs(loss - expected * k) < 0.01F, "health loss " + loss + " expected " + expected * k);
		helper.succeed();
	}

	private static float healthLoss(final GameTestHelper helper, final ServerPlayer player, final float damage) {
		player.damageCooldownTime = 0;
		player.setHealth(player.getMaxHealth());
		float start = player.getHealth();
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), damage);
		return start - player.getHealth();
	}

	private static int countAffixModifiers(final ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		int count = 0;
		for (Identifier id : new Identifier[] {Affixes.HP_FLAT_ID, Affixes.HP_PCT_ID}) {
			count += health.getModifier(id) == null ? 0 : 1;
		}
		return count;
	}

	private static Zombie zombie(final GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		zombie.setHealth(zombie.getMaxHealth());
		return zombie;
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE * Math.max(1.0F, Math.abs(expected));
	}
}
