package com.stella.ruins.world;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Omens: a run rolls up to two, stores them, applies them to its mobs and players, and pays one more vault roll for each. */
public class RuinOmenGameTest {
	static final int TICKS = 6000;
	static final int ROLLS = 4000;

	@FunctionalInterface
	interface Body {
		void run(WorldTestSupport.Arena arena, Mob baseline) throws RuntimeException;
	}

	/** Runs a body with a fixed set of omens, a baseline husk with no omens, and the old roller restored at the end. */
	static void withOmens(final GameTestHelper helper, final List<Omen> omens, final Body body) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			Omens.Roller old = Omens.replaceRoller((random, tier) -> omens);
			Mob baseline = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			baseline.setNoAi(true);
			try {
				body.run(arena, baseline);
			} finally {
				Omens.replaceRoller(old);
				baseline.discard();
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void anActivationStoresItsOmensAndFrenziedAndFerociousChangeTheRosterMobs(final GameTestHelper helper) {
		withOmens(helper, List.of(Omen.FRENZIED, Omen.FEROCIOUS), (arena, baseline) -> {
			arena.wake();
			Mob mob = RuinTierGameTest.rosterMob(arena, 0);
			double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) / baseline.getAttributeValue(Attributes.MOVEMENT_SPEED);
			double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) / baseline.getAttributeValue(Attributes.ATTACK_DAMAGE);
			WorldTestSupport.result("omenMobs", "omens=" + arena.entry().omens() + " speedRatio=" + speed + " damageRatio=" + damage);
			helper.assertTrue(arena.entry().omens().equals(List.of("frenzied", "ferocious")), "the entry holds " + arena.entry().omens());
			helper.assertTrue(Math.abs(speed - (1.0 + Omen.FRENZIED.speed())) < 1.0E-3, "the speed ratio is " + speed);
			helper.assertTrue(Math.abs(damage - (1.0 + Omen.FEROCIOUS.damage())) < 1.0E-3, "the damage ratio is " + damage);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void bulwarkAddsEightArmorAndSomeHealth(final GameTestHelper helper) {
		withOmens(helper, List.of(Omen.BULWARK), (arena, baseline) -> {
			arena.wake();
			Mob mob = RuinTierGameTest.rosterMob(arena, 0);
			double armor = mob.getAttributeValue(Attributes.ARMOR) - baseline.getAttributeValue(Attributes.ARMOR);
			double health = mob.getMaxHealth() / baseline.getMaxHealth();
			helper.assertTrue(Math.abs(armor - Omen.BULWARK.armor()) < 1.0E-3, "the armor gain is " + armor);
			helper.assertTrue(Math.abs(health - (1.0 + Omen.BULWARK.health())) < 1.0E-3, "the health ratio is " + health);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void swarmingAddsAQuarterMoreMobsOfTheFirstKind(final GameTestHelper helper) {
		withOmens(helper, List.of(Omen.SWARMING), (arena, baseline) -> {
			arena.wake();
			int normal = WorldTestInit.reset.roster().entries().stream().mapToInt(Roster.Entry::count).sum();
			int extra = (int) Math.ceil(WorldTestInit.reset.roster().entries().get(0).count() * Omen.SWARM_SHARE);
			WorldTestSupport.result("omenSwarm", "normal=" + normal + " roster=" + arena.entry().roster().size());
			helper.assertTrue(arena.entry().roster().size() == normal + extra, "the roster holds " + arena.entry().roster().size() + ", not " + (normal + extra));
		});
	}

	@GameTest(maxTicks = TICKS)
	public void hexedPutsWeaknessOnThePlayersInTheRadius(final GameTestHelper helper) {
		withOmens(helper, List.of(Omen.HEXED), (arena, baseline) -> {
			ServerPlayer player = arena.wake();
			arena.core().pulse(arena.level());
			helper.assertTrue(player.hasEffect(MobEffects.WEAKNESS), "a hexed run left the player without Weakness");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aRunWithoutOmensLeavesThePlayersAndTheRosterAlone(final GameTestHelper helper) {
		withOmens(helper, List.of(), (arena, baseline) -> {
			ServerPlayer player = arena.wake();
			arena.core().pulse(arena.level());
			Mob mob = RuinTierGameTest.rosterMob(arena, 0);
			helper.assertTrue(!player.hasEffect(MobEffects.WEAKNESS), "a run with no omen weakened the player");
			helper.assertTrue(mob.getMaxHealth() == baseline.getMaxHealth() && arena.entry().omens().isEmpty(), "a run with no omen changed the mob or stored an omen");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void eachOmenAddsOneVaultRoll(final GameTestHelper helper) {
		withOmens(helper, List.of(Omen.FRENZIED, Omen.HEXED), (arena, baseline) -> {
			int base = RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), Vec3.atCenterOf(arena.corePos())));
			arena.finish(helper);
			int emeralds = RuinCoreGameTest.count(arena.vault(), Items.EMERALD);
			WorldTestSupport.result("omenRolls", "base=" + base + " emeralds=" + emeralds);
			helper.assertTrue(emeralds == base + 2, "two omens rolled the vault " + emeralds + " times, not " + (base + 2));
		});
	}

	@GameTest
	public void theDefaultRollGivesZeroToTwoDistinctOmensAndMoreOftenAtAHigherTier(final GameTestHelper helper) {
		double[] mean = new double[2];
		int[] tiers = {0, 3};
		for (int t = 0; t < tiers.length; t++) {
			RandomSource random = RandomSource.create(20261009L);
			int total = 0;
			for (int i = 0; i < ROLLS; i++) {
				List<Omen> rolled = Omens.rollDefault(random, tiers[t]);
				helper.assertTrue(rolled.size() <= Omens.MAX && rolled.stream().distinct().count() == rolled.size(), "a roll gave " + rolled);
				total += rolled.size();
			}
			mean[t] = (double) total / ROLLS;
			double first = Omens.firstChance(tiers[t]);
			double expected = first * (1.0 + first * Omens.SECOND_SHARE);
			helper.assertTrue(Math.abs(mean[t] - expected) < 0.05, "tier " + tiers[t] + " averaged " + mean[t] + " omens, not about " + expected);
		}
		WorldTestSupport.result("omenOdds", "meanTier0=" + mean[0] + " meanTier3=" + mean[1]);
		helper.assertTrue(mean[1] > mean[0] + 0.3, "tier 3 averaged " + mean[1] + " against " + mean[0] + " at tier 0");
		helper.succeed();
	}

	@GameTest
	public void theNoticeNamesEveryOmen(final GameTestHelper helper) {
		Component notice = Omens.notice(List.of(Omen.FRENZIED, Omen.HEXED));
		helper.assertTrue(notice.getContents() instanceof TranslatableContents line && line.getKey().equals("message.mod-stella-ruins.omen"), "the notice is " + notice);
		String text = notice.toString();
		helper.assertTrue(text.contains("omen.mod-stella-ruins.frenzied") && text.contains("omen.mod-stella-ruins.hexed"), "the notice does not name both omens: " + text);
		helper.succeed();
	}
}
