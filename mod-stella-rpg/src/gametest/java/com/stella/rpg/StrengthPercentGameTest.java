package com.stella.rpg;

import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;

/** Strength adds 10% damage per level. Weakness removes 10% per level, down to no damage. */
public final class StrengthPercentGameTest {
	private static final float BASE = 5.0F;
	private static final float TOLERANCE = 0.0005F;

	@GameTest
	public void strengthAndWeakness(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		double attackBefore = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 1, 2, 1);
		DamageSource melee = level.damageSources().playerAttack(player);
		StringBuilder log = new StringBuilder();
		RpgCombat.setRoller((attacker, random, chance) -> false);
		RpgCombat.setDiceRoller((attacker, random, dice) -> 0);
		try {
			float none = RpgCombat.scaleOutgoing(victim, melee, BASE);
			float strength1 = with(player, victim, melee, MobEffects.STRENGTH, 0);
			float strength3 = with(player, victim, melee, MobEffects.STRENGTH, 2);
			double attackWithStrength = attackAfter(player, MobEffects.STRENGTH, 2);
			float weakness1 = with(player, victim, melee, MobEffects.WEAKNESS, 0);
			float weakness3 = with(player, victim, melee, MobEffects.WEAKNESS, 2);
			double attackWithWeakness = attackAfter(player, MobEffects.WEAKNESS, 2);
			float wiped = with(player, victim, melee, MobEffects.WEAKNESS, 11);
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 0));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
			float both = RpgCombat.scaleOutgoing(victim, melee, BASE);
			player.removeAllEffects();
			player.setItemSlot(EquipmentSlot.MAINHAND, RpgTestSupport.withAffixes(Items.IRON_SWORD, Map.of("damage_pct", 55)));
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 1));
			float stacked = RpgCombat.scaleOutgoing(victim, melee, BASE);
			player.removeAllEffects();

			log.append("none=").append(none).append(" s1=").append(strength1).append(" s3=").append(strength3)
				.append(" w1=").append(weakness1).append(" w3=").append(weakness3).append(" w11=").append(wiped)
				.append(" both=").append(both).append(" stacked=").append(stacked)
				.append(" attack=").append(attackBefore).append('/').append(attackWithStrength).append('/').append(attackWithWeakness);
			helper.assertTrue(near(none, BASE), "no effect changed the hit to " + none);
			helper.assertTrue(near(strength1, BASE * 1.1F), "Strength I " + strength1);
			helper.assertTrue(near(strength3, BASE * 1.3F), "Strength III " + strength3);
			helper.assertTrue(near(weakness1, BASE * 0.9F), "Weakness I " + weakness1);
			helper.assertTrue(near(weakness3, BASE * 0.7F), "Weakness III " + weakness3);
			helper.assertTrue(wiped == 0.0F, "Weakness XII left damage " + wiped);
			helper.assertTrue(near(both, BASE), "Strength I and Weakness I " + both);
			helper.assertTrue(near(stacked, BASE * (1.0F + 0.055F + 0.2F)), "Strength II with a 5.5% affix " + stacked);
			helper.assertTrue(attackWithStrength == attackBefore && attackWithWeakness == attackBefore,
				"the vanilla attack modifier still applies: " + attackBefore + " " + attackWithStrength + " " + attackWithWeakness);
		} finally {
			RpgCombat.setRoller(null);
			RpgCombat.setDiceRoller(null);
		}
		RpgTestSupport.result("strengthAndWeakness", log.toString());
		helper.succeed();
	}

	private static float with(
		final ServerPlayer player, final Zombie victim, final DamageSource source, final Holder<MobEffect> effect, final int amplifier
	) {
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(effect, 600, amplifier));
		float result = RpgCombat.scaleOutgoing(victim, source, BASE);
		player.removeAllEffects();
		return result;
	}

	private static double attackAfter(final ServerPlayer player, final Holder<MobEffect> effect, final int amplifier) {
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(effect, 600, amplifier));
		double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		player.removeAllEffects();
		return attack;
	}

	private static boolean near(final float actual, final float expected) {
		return Math.abs(actual - expected) < TOLERANCE;
	}
}
