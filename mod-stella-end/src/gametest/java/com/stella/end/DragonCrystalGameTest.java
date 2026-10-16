package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** The ten pillar crystals: each live one gives the dragon its own buff, and breaking it takes that buff away. */
public class DragonCrystalGameTest {
	record Pillar(ServerLevel end, EnderDragon dragon, ServerPlayer player) {
		float hit(final double share) {
			dragon.damageCooldownTime = 0;
			float before = dragon.getHealth();
			dragon.hurt(end, dragon.head, end.damageSources().explosion(null, player), (float) (dragon.getMaxHealth() * share / HealthBridge.scale()));
			return before - dragon.getHealth();
		}

		void healTo(final double share) {
			dragon.setHealth((float) (dragon.getMaxHealth() * share));
		}

		/** Six blows from one health, summed: another mod rolls dice on the damage, so one blow varies a little. */
		float blows(final double from, final double share) {
			float total = 0.0F;
			for (int i = 0; i < 6; i++) {
				healTo(from);
				total += hit(share);
			}
			return total;
		}
	}

	static Set<Integer> buffs(final Pillar pillar) {
		return new TreeSet<>(DragonBuffs.of(pillar.dragon()));
	}

	@GameTest(environment = "mod-stella-end-test:crystals_each", maxTicks = 900)
	public void eachLiveCrystalAddsItsOwnBuffAndBreakingItTakesThatBuffAway(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.freshWide(helper);
		ServerPlayer player = EndTestSupport.joinOnFloor(helper, end, 5);
		EnderDragon dragon = EndDragons.spawn(end);
		helper.assertTrue(dragon != null, "no dragon was made");
		Pillar pillar = new Pillar(end, dragon, player);
		List<String> bad = new ArrayList<>();
		float[] plainHit = new float[1];
		float[] plainRegen = new float[1];
		float[] stamp = new float[1];
		EndCrystal[] crystal = new EndCrystal[1];
		var sequence = helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				helper.assertTrue(buffs(pillar).isEmpty(), "buffs with no crystal: " + buffs(pillar));
				plainHit[0] = pillar.blows(0.9, 0.01);
				pillar.healTo(0.9);
				stamp[0] = dragon.getHealth();
			})
			.thenIdle(DragonRules.REGEN_PERIOD * 2 + 1)
			.thenExecute(() -> plainRegen[0] = dragon.getHealth() - stamp[0]);
		for (int spike = 0; spike < DragonRules.BUFFS; spike++) {
			int number = spike + 1;
			sequence = sequence
				.thenExecute(() -> crystal[0] = EndTestSupport.crystal(end, number - 1))
				.thenWaitUntil(() -> helper.assertTrue(buffs(pillar).equals(Set.of(number)), "with only crystal " + number + " the buffs are " + buffs(pillar)))
				.thenExecute(() -> {
					switch (number) {
						case DragonRules.REGENERATION -> {
							pillar.healTo(0.9);
							stamp[0] = dragon.getHealth();
						}
						case DragonRules.TOUGHNESS -> {
							float taken = pillar.blows(0.9, 0.01);
							if (Math.abs(taken / plainHit[0] - DragonRules.TOUGHNESS_BUFF) > 0.05) {
								bad.add("crystal 3: took " + taken / plainHit[0] + " of a blow");
							}
						}
						case DragonRules.KNOCKBACK -> {
							if (dragon.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) != 1.0) {
								bad.add("crystal 6: knockback resistance " + dragon.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
							}
						}
						case DragonRules.ABSORPTION -> {
							if (dragon.getAbsorptionAmount() != DragonRules.ABSORPTION_BUFF * HealthBridge.scale()) {
								bad.add("crystal 10: absorption " + dragon.getAbsorptionAmount());
							}
						}
						default -> {
						}
					}
				});
			if (number == DragonRules.REGENERATION) {
				sequence = sequence
					.thenIdle(DragonRules.REGEN_PERIOD * 2 + 1)
					.thenExecute(() -> {
						float regen = dragon.getHealth() - stamp[0];
						float expected = dragon.getMaxHealth() * DragonRules.REGEN_PER_SECOND * 2;
						if (plainRegen[0] > 0.0F || regen < expected * 0.4F || regen > expected * 1.6F) {
							bad.add("crystal 1: healed " + regen + " (expected about " + expected + "), plain " + plainRegen[0]);
						}
					});
			}
			sequence = sequence
				.thenExecute(() -> crystal[0].discard())
				.thenWaitUntil(() -> helper.assertTrue(buffs(pillar).isEmpty(), "after breaking crystal " + number + " the buffs are " + buffs(pillar)))
				.thenExecute(() -> {
					if (number == DragonRules.KNOCKBACK && dragon.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1.0) {
						bad.add("crystal 6: the resistance outlived the crystal");
					}
					if (number == DragonRules.ABSORPTION && dragon.getAbsorptionAmount() > 0.0F) {
						bad.add("crystal 10: the absorption outlived the crystal: " + dragon.getAbsorptionAmount());
					}
				});
		}
		sequence
			.thenExecute(() -> {
				for (int spike = 0; spike < DragonRules.BUFFS; spike++) {
					EndTestSupport.crystal(end, spike);
				}
			})
			.thenWaitUntil(() -> helper.assertTrue(buffs(pillar).size() == DragonRules.BUFFS, "with all ten crystals the buffs are " + buffs(pillar)))
			.thenExecute(() -> {
				EndTestSupport.result("crystalsEach", "bad=" + bad.size() + " plainHit=" + plainHit[0] + " plainRegen=" + plainRegen[0]);
				EndTestSupport.releaseCrystals(end);
				EndTestSupport.fresh(helper);
				helper.assertTrue(bad.isEmpty(), "crystal defects: " + bad);
			})
			.thenSucceed();
	}
}
