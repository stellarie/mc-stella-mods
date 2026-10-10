package com.stella.ruins.world;

import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Site mobs follow the mobs mod's level scaling: the level is the area level at the core plus the size, tier and boss
 * bonuses, and the kind's own multipliers come on top. Without the mobs mod a site mob keeps its old stats.
 */
public class RuinMobScalingGameTest {
	static final int TICKS = 6000;
	static final int AREA = 40;
	/** The area level the fixed source returns. The time to kill record moves it. */
	static volatile int areaNow = AREA;

	/** Runs a body with scaling on and the area level at every spot fixed. */
	@FunctionalInterface
	interface Body {
		void run(WorldTestSupport.Arena arena);
	}

	private static void scaled(final GameTestHelper helper, final RuinSite site, final int tier, final Body body) {
		WorldTestSupport.inArena(helper, site, arena -> {
			RuinConfig.Values oldConfig = RuinConfig.replace(RuinConfig.get().withScaling(RuinConfig.Scaling.DEFAULTS));
			BiFunction<ServerLevel, net.minecraft.world.phys.Vec3, Integer> oldArea = RuinLevels.replaceAreaSource((level, at) -> areaNow);
			areaNow = AREA;
			try {
				RuinData.get(arena.level()).entryFor(arena.corePos(), site.id()).setTier(tier);
				body.run(arena);
			} finally {
				RuinLevels.replaceAreaSource(oldArea);
				RuinConfig.replace(oldConfig);
			}
		});
	}

	/** A plain mob of the kind's base type, given a level the way a natural spawn at that level has it. */
	private static Mob natural(final ServerLevel level, final RuinMobKind kind, final int atLevel) {
		Mob mob = (Mob) kind.base().create(level, EntitySpawnReason.COMMAND);
		mob.addTag(RuinMobKind.NO_SPAWN_ROLL_TAG);
		RuinLevels.apply(mob, atLevel);
		return mob;
	}

	@GameTest
	public void theSiteLevelIsTheAreaLevelPlusTheSizeTierAndBossBonuses(final GameTestHelper helper) {
		RuinConfig.Scaling scaling = RuinConfig.Scaling.DEFAULTS;
		helper.assertTrue(SiteLevels.level(AREA, SiteSize.SMALL, 0, false, scaling) == AREA, "a small site at tier 0 is not the area level");
		helper.assertTrue(SiteLevels.level(AREA, SiteSize.LARGE, 0, false, scaling) == AREA + scaling.largeBonus(), "a large site is not area plus " + scaling.largeBonus());
		helper.assertTrue(SiteLevels.level(AREA, SiteSize.SMALL, 3, false, scaling) == AREA + 3 * scaling.tierLevels(), "tier 3 is not area plus 3 x " + scaling.tierLevels());
		helper.assertTrue(SiteLevels.level(AREA, SiteSize.SMALL, 0, true, scaling) == AREA + scaling.bossBonus(), "a boss is not area plus " + scaling.bossBonus());
		int all = SiteLevels.level(AREA, SiteSize.LARGE, 2, true, scaling);
		helper.assertTrue(all == AREA + scaling.largeBonus() + 2 * scaling.tierLevels() + scaling.bossBonus(), "the bonuses do not add up: " + all);
		RuinConfig.Scaling off = new RuinConfig.Scaling(false, 10, 5, 10, 0.3);
		helper.assertTrue(SiteLevels.level(AREA, SiteSize.LARGE, 3, true, off) == AREA + 10 + 15 + 10, "the formula changed when scaling is off; the switch lives in the spawn");
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void aSiteMobTakesTheSiteLevelFromTheMobsModAndMoreHealthWithIt(final GameTestHelper helper) {
		if (!RuinLevels.canApply()) {
			WorldTestSupport.result("siteMobLevel", "skipped=true reason=mobs-mod-absent");
			helper.succeed();
			return;
		}
		scaled(helper, WorldTestInit.large, 2, arena -> {
			RuinConfig.Scaling scaling = RuinConfig.get().scaling();
			Mob mob = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			int expected = AREA + scaling.largeBonus() + 2 * scaling.tierLevels();
			int level = RuinLevels.of(mob);
			Mob zero = natural(arena.level(), WorldTestInit.husk, 0);
			WorldTestSupport.result("siteMobLevel", "level=" + level + " expected=" + expected + " health=" + mob.getMaxHealth());
			helper.assertTrue(level == expected, "the site mob has level " + level + ", not " + expected);
			helper.assertTrue(mob.getMaxHealth() > zero.getMaxHealth(), "the health did not rise with the level");
			mob.discard();
			zero.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theKindMultipliersStillApplyOnTopOfTheLevelScaledBase(final GameTestHelper helper) {
		scaled(helper, WorldTestInit.surface, 0, arena -> {
			Mob kindMob = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			Mob plain = natural(arena.level(), WorldTestInit.husk, AREA);
			Mob vanilla = (Mob) WorldTestInit.husk.base().create(arena.level(), EntitySpawnReason.COMMAND);
			double healthRatio = kindMob.getMaxHealth() / plain.getMaxHealth();
			double damageRatio = kindMob.getAttributeValue(Attributes.ATTACK_DAMAGE) / vanilla.getAttributeValue(Attributes.ATTACK_DAMAGE);
			WorldTestSupport.result("kindOnTop", "mobs=" + RuinLevels.canApply() + " healthRatio=" + healthRatio + " damageRatio=" + damageRatio + " kind=" + kindMob.getMaxHealth() + " plain=" + plain.getMaxHealth());
			helper.assertTrue(Math.abs(healthRatio - 2.0) < 0.001, "the kind's health factor is " + healthRatio + " over the level-scaled base, not 2.0");
			helper.assertTrue(Math.abs(damageRatio - 1.5) < 0.001, "the kind's damage factor is " + damageRatio + ", not 1.5");
			kindMob.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aBossKeepsOnlyAShareOfItsHealthFactorOnTopOfTheLevelScaledBase(final GameTestHelper helper) {
		scaled(helper, WorldTestInit.surface, 0, arena -> {
			areaNow = 0;
			RuinConfig.Scaling scaling = RuinConfig.get().scaling();
			Mob boss = WorldTestInit.boss.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			Mob plain = natural(arena.level(), WorldTestInit.boss, scaling.bossBonus());
			Mob vanilla = (Mob) WorldTestInit.boss.base().create(arena.level(), EntitySpawnReason.COMMAND);
			double expected = RuinLevels.canApply() ? 1.0 + 7.0 * scaling.bossHealthShare() : 8.0;
			double ratio = boss.getMaxHealth() / (RuinLevels.canApply() ? plain.getMaxHealth() : vanilla.getMaxHealth());
			WorldTestSupport.result("bossShare", "mobs=" + RuinLevels.canApply() + " ratio=" + ratio + " expected=" + expected + " boss=" + boss.getMaxHealth());
			helper.assertTrue(Math.abs(ratio - expected) < 0.001, "a boss's health factor is " + ratio + " over the base, not " + expected);
			boss.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void withoutTheShareOrWithScalingOffASiteMobKeepsItsOldStats(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			RuinConfig.Values oldConfig = RuinConfig.replace(RuinConfig.get().withScaling(new RuinConfig.Scaling(false, 10, 5, 10, 0.3)));
			BiFunction<ServerLevel, net.minecraft.world.phys.Vec3, Integer> oldArea = RuinLevels.replaceAreaSource((level, at) -> AREA);
			try {
				Mob off = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
				Mob vanilla = (Mob) WorldTestInit.husk.base().create(arena.level(), EntitySpawnReason.COMMAND);
				double ratio = off.getMaxHealth() / vanilla.getMaxHealth();
				WorldTestSupport.result("scalingOff", "mobs=" + RuinLevels.canApply() + " ratio=" + ratio);
				helper.assertTrue(Math.abs(ratio - 2.0) < 0.001, "with scaling off the site mob has " + ratio + " times the vanilla health, not the kind's 2.0");
				off.discard();
				if (!RuinLevels.canApply()) {
					RuinConfig.replace(RuinConfig.get().withScaling(RuinConfig.Scaling.DEFAULTS));
					Mob absent = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
					double absentRatio = absent.getMaxHealth() / vanilla.getMaxHealth();
					helper.assertTrue(Math.abs(absentRatio - 2.0) < 0.001, "without the mobs mod the site mob has " + absentRatio + " times the vanilla health");
					absent.discard();
				}
			} finally {
				RuinLevels.replaceAreaSource(oldArea);
				RuinConfig.replace(oldConfig);
			}
		});
	}

	/** Hits a mob with a fixed blow until it dies. Returns the number of blows. */
	private static int blowsToKill(final WorldTestSupport.Arena arena, final ServerPlayer player, final Mob mob, final float blow) {
		mob.setNoAi(true);
		int blows = 0;
		while (mob.isAlive() && blows < 5000) {
			mob.damageCooldownTime = 0;
			mob.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), blow);
			blows++;
		}
		return blows;
	}

	@GameTest(maxTicks = TICKS)
	public void recordTheBlowsAFixedSwordNeedsAgainstASiteMobASiteBossAndANaturalMobOfTheSameLevel(final GameTestHelper helper) {
		if (!RuinLevels.canApply()) {
			WorldTestSupport.result("timeToKill", "skipped=true reason=mobs-mod-absent");
			helper.succeed();
			return;
		}
		scaled(helper, WorldTestInit.large, 0, arena -> {
			ServerPlayer player = arena.join(arena.standAt(3, 3));
			float blow = 7.0F;
			RuinMobKind boss = RuinMobKind.get("castellan");
			RuinMobKind grunt = RuinMobKind.get("spectral_knight");
			for (int area : new int[] {0, AREA, 150}) {
				areaNow = area;
				StringBuilder line = new StringBuilder("area=" + area + " blow=" + blow);
				for (RuinMobKind kind : new RuinMobKind[] {WorldTestInit.husk, grunt, boss}) {
					if (kind == null) {
						continue;
					}
					Mob site = kind.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
					int level = RuinLevels.of(site);
					float health = site.getMaxHealth();
					int siteBlows = blowsToKill(arena, player, site, blow);
					Mob natural = natural(arena.level(), kind, level);
					float naturalHealth = natural.getMaxHealth();
					int naturalBlows = blowsToKill(arena, player, natural, blow);
					line.append(" | ").append(kind.id()).append(" level=").append(level).append(" siteHealth=").append(health).append(" siteBlows=").append(siteBlows)
						.append(" naturalHealth=").append(naturalHealth).append(" naturalBlows=").append(naturalBlows);
					helper.assertTrue(siteBlows >= 1 && naturalBlows >= 1, kind.id() + " died to no blow");
				}
				WorldTestSupport.result("timeToKill", line.toString());
			}
		});
	}
}
