package com.stella.ruins.world;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;

/** The trophy: a site unique that pity puts in the vault, and the summoned mobs that must never farm one. */
public class RuinTrophyGameTest {
	static final int TICKS = 6000;
	static final String SITE = "test_trophy";

	@GameTest(maxTicks = TICKS)
	public void aSiteKindsSecondDryClearPutsItsUniqueInTheVaultAndResetsThePity(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.trophy, arena -> {
			ServerLevel level = arena.level();
			AtomicLong clock = new AtomicLong(21_000_000L);
			RuinClock.Source oldClock = RuinClock.replace(l -> clock.get());
			RuinConfig.Values oldConfig = RuinConfig.replace(RuinConfig.get().withTrophy(0.0, 1.0));
			RuinPity pity = RuinPity.get(level);
			pity.reset(SITE);
			try {
				ServerPlayer player = arena.finish(helper);
				int starsFirst = RuinCoreGameTest.count(arena.vault(), Items.NETHER_STAR);
				int dryFirst = pity.dry(SITE);
				arena.leave(player);
				clock.addAndGet(Claim.DAY_TICKS);
				arena.core().pulse(level);
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site did not rearm: " + arena.phase());
				arena.finish(helper);
				int starsSecond = RuinCoreGameTest.count(arena.vault(), Items.NETHER_STAR);
				WorldTestSupport.result("trophyPity", "starsFirst=" + starsFirst + " dryFirst=" + dryFirst + " starsSecond=" + starsSecond + " drySecond=" + pity.dry(SITE));
				helper.assertTrue(starsFirst == 0 && dryFirst == 1, "the first clear paid " + starsFirst + " uniques with " + dryFirst + " dry clears, not 0 and 1");
				helper.assertTrue(starsSecond == 1, "the second clear left " + starsSecond + " uniques in the vault, not 1");
				helper.assertTrue(pity.dry(SITE) == 0, "the pity stayed at " + pity.dry(SITE) + " after a trophy");
			} finally {
				pity.reset(SITE);
				RuinConfig.replace(oldConfig);
				RuinClock.replace(oldClock);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aSummonedMobRollsNoUniqueAndNoKindDropWhileARosterMobStillDoes(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			RuinConfig.Values old = RuinConfig.get();
			try {
				ServerPlayer player = arena.wake();
				arena.freeze();
				RuinConfig.replace(old.withUniqueDropChance(1.0));
				List<UUID> roster = arena.entry().roster();
				((Mob) level.getEntity(roster.get(0))).hurtServer(level, level.damageSources().playerAttack(player), 10_000.0F);
				int starsControl = RuinMobGameTest.itemsNear(arena, Items.NETHER_STAR);
				int nuggetsControl = RuinMobGameTest.itemsNear(arena, Items.GOLD_NUGGET);
				Mob summoned = WorldTestInit.husk.spawn(level, RuinMobGameTest.spawnSpot(arena));
				summoned.setNoAi(true);
				RuinMobs.markSummoned(summoned);
				arena.entry().addMob(summoned.getUUID());
				summoned.hurtServer(level, level.damageSources().playerAttack(player), 10_000.0F);
				int stars = RuinMobGameTest.itemsNear(arena, Items.NETHER_STAR);
				int nuggets = RuinMobGameTest.itemsNear(arena, Items.GOLD_NUGGET);
				WorldTestSupport.result("summonedRollsNothing", "starsControl=" + starsControl + " nuggetsControl=" + nuggetsControl + " starsAfter=" + stars + " nuggetsAfter=" + nuggets);
				helper.assertTrue(!summoned.isAlive(), "the summoned mob survived the kill");
				helper.assertTrue(starsControl == 1 && nuggetsControl == 1, "the roster control dropped " + starsControl + " uniques and " + nuggetsControl + " nuggets, so the check proves nothing");
				helper.assertTrue(stars == starsControl, "a summoned mob dropped a unique at chance 1.0");
				helper.assertTrue(nuggets == nuggetsControl, "a summoned mob rolled its kind table");
			} finally {
				RuinConfig.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theSummonedTagIsPartOfTheSavedMob(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			Mob mob = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			mob.setNoAi(true);
			RuinMobs.markSummoned(mob);
			CompoundTag saved = RuinMobGameTest.save(arena.level(), mob);
			helper.assertTrue(saved.toString().contains(RuinMobs.TAG_SUMMONED), "the saved mob carries no summoned tag: " + saved);
			helper.assertTrue(RuinMobs.isRuinMob(mob), "marking a mob summoned dropped its ruin tag");
			mob.discard();
		});
	}

	@GameTest
	public void pityCountsSurviveSaveAndLoad(final GameTestHelper helper) {
		RuinPity pity = new RuinPity();
		pity.miss("alpha");
		pity.miss("alpha");
		pity.miss("beta");
		Tag saved = RuinPity.CODEC.encodeStart(NbtOps.INSTANCE, pity).getOrThrow();
		RuinPity loaded = RuinPity.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.dry("alpha") == 2 && loaded.dry("beta") == 1 && loaded.dry("gamma") == 0, "the load holds " + loaded.dry("alpha") + ", " + loaded.dry("beta") + ", " + loaded.dry("gamma") + ", not 2, 1, 0");
		loaded.reset("alpha");
		helper.assertTrue(loaded.dry("alpha") == 0 && loaded.dry("beta") == 1, "a reset touched another kind");
		helper.succeed();
	}
}
