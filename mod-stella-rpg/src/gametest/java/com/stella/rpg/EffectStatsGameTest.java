package com.stella.rpg;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Active effects grant stats for each level. Food effects are flat, and seasoned effects add 1 for each level. */
public final class EffectStatsGameTest {
	@GameTest
	public void table(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		record Row(Holder<MobEffect> effect, Stat stat, int perLevel) {
		}
		List<Row> rows = List.of(
			new Row(MobEffects.SPEED, Stat.AGI, 10), new Row(MobEffects.SLOWNESS, Stat.AGI, -10),
			new Row(MobEffects.HASTE, Stat.DEX, 10), new Row(MobEffects.MINING_FATIGUE, Stat.DEX, -10),
			new Row(MobEffects.RESISTANCE, Stat.VIT, 10), new Row(MobEffects.LUCK, Stat.LUK, 10),
			new Row(MobEffects.UNLUCK, Stat.LUK, -10), new Row(MobEffects.HERO_OF_THE_VILLAGE, Stat.LUK, 10),
			new Row(MobEffects.STRENGTH, Stat.STR, 10), new Row(MobEffects.WEAKNESS, Stat.STR, -10)
		);
		for (Stat stat : Stat.values()) {
			RpgTestSupport.setStat(player, stat, 100);
		}
		StringBuilder log = new StringBuilder();
		for (Row row : rows) {
			for (int amplifier = 0; amplifier < 3; amplifier++) {
				player.removeAllEffects();
				player.addEffect(new MobEffectInstance(row.effect(), 600, amplifier));
				int bonus = StatBonuses.bonus(player, row.stat());
				int expected = row.perLevel() * (amplifier + 1);
				helper.assertTrue(bonus == expected, row.effect().getRegisteredName() + " level " + (amplifier + 1) + " gave " + bonus + " expected " + expected);
				for (Stat other : Stat.values()) {
					helper.assertTrue(other == row.stat() || StatBonuses.bonus(player, other) == 0, row.effect().getRegisteredName() + " changed " + other);
				}
				helper.assertTrue(StatBonuses.effective(player, row.stat()) == 100 + expected, "the effective stat ignored the effect");
			}
			log.append(row.effect().getRegisteredName()).append(' ');
		}
		player.removeAllEffects();
		helper.assertTrue(StatBonuses.bonus(player, Stat.AGI) == 0, "a removed effect still granted a stat");
		RpgTestSupport.result("table", "effects=" + rows.size());
		helper.succeed();
	}

	@GameTest
	public void negativeClampsAtZero(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		RpgTestSupport.setStat(player, Stat.AGI, 5);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 3));
		int effective = StatBonuses.effective(player, Stat.AGI);
		helper.assertTrue(effective == 0, "Slowness IV took AGI below zero: " + effective);
		helper.succeed();
	}

	@GameTest
	public void food(final GameTestHelper helper) {
		Map<String, StatTables.EffectStat> table = RpgConfigStore.current().tables().effectStats();
		String[] flat = {"surefooted", "deep_breath", "light_step", "current_rider", "ember_skin", "second_wind"};
		Stat[] stats = {Stat.STR, Stat.VIT, Stat.AGI, Stat.DEX, Stat.INT, Stat.LUK};
		for (int i = 0; i < flat.length; i++) {
			Identifier id = Identifier.fromNamespaceAndPath("mod-stella-food", flat[i]);
			for (int level = 1; level <= 3; level++) {
				for (Stat stat : Stat.values()) {
					int expected = stat == stats[i] ? 20 : 0;
					helper.assertTrue(EffectStats.amount(id, level, stat, table) == expected, flat[i] + " level " + level + " " + stat);
				}
			}
		}
		for (Stat stat : Stat.values()) {
			Identifier id = Identifier.fromNamespaceAndPath("mod-stella-food", "seasoned_" + stat.key());
			for (int level = 1; level <= 5; level++) {
				for (Stat other : Stat.values()) {
					int expected = other == stat ? level : 0;
					helper.assertTrue(EffectStats.amount(id, level, other, table) == expected, "seasoned_" + stat.key() + " level " + level + " " + other);
				}
			}
		}
		helper.assertTrue(EffectStats.amount(Identifier.fromNamespaceAndPath("othermod", "surefooted"), 1, Stat.STR, table) == 0
			&& EffectStats.amount(Identifier.fromNamespaceAndPath("othermod", "speed"), 1, Stat.AGI, table) == 0, "an effect of another mod granted a stat");

		// The food mod is absent here: no such effect is registered, and a player with other effects still reads its stats.
		boolean registered = BuiltInRegistries.MOB_EFFECT.containsKey(Identifier.fromNamespaceAndPath("mod-stella-food", "deep_breath"));
		helper.assertTrue(!registered, "the test expects deep_breath to be absent");
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0));
		helper.assertTrue(StatBonuses.bonus(player, Stat.VIT) == 0 && StatBonuses.bonus(player, Stat.AGI) == 10, "the absent food effects changed a stat");

		// Stand-ins registered by the test mod under the food namespace.
		Holder<MobEffect> surefooted = BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("mod-stella-food", "surefooted")).orElseThrow();
		Holder<MobEffect> seasoned = BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("mod-stella-food", "seasoned_str")).orElseThrow();
		player.removeAllEffects();
		player.addEffect(new MobEffectInstance(surefooted, 600, 2));
		int flatStr = StatBonuses.bonus(player, Stat.STR);
		player.addEffect(new MobEffectInstance(seasoned, 600, 3));
		int both = StatBonuses.bonus(player, Stat.STR);
		RpgTestSupport.result("food", "flat=" + flatStr + " both=" + both);
		helper.assertTrue(flatStr == 20, "surefooted III gave STR " + flatStr + " instead of a flat 20");
		helper.assertTrue(both == 24, "surefooted and seasoned_str IV gave " + both);
		helper.succeed();
	}
}
