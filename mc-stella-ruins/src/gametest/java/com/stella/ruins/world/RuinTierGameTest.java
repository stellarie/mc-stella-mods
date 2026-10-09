package com.stella.ruins.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/** Tiers: a reset site climbs one tier for each completion, its mobs grow, its vault rolls more, and an old save loads as tier 0. */
public class RuinTierGameTest {
	static final int TICKS = 6000;

	/** Ends the visit, lets the delay pass and pulses, so the core rearms. */
	static void rearm(final GameTestHelper helper, final WorldTestSupport.Arena arena, final ServerPlayer player, final AtomicLong clock) {
		arena.leave(player);
		clock.addAndGet(Claim.DAY_TICKS);
		arena.core().pulse(arena.level());
		helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site did not rearm: " + arena.phase());
	}

	static Mob rosterMob(final WorldTestSupport.Arena arena, final int index) {
		UUID id = arena.entry().roster().get(index);
		return (Mob) arena.level().getEntity(id);
	}

	@GameTest(maxTicks = TICKS)
	public void aResetSiteClimbsOneTierForEachCompletionUpToTheCap(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			AtomicLong clock = new AtomicLong(31_000_000L);
			RuinClock.Source old = RuinClock.replace(l -> clock.get());
			try {
				int cap = RuinConfig.get().tierCap();
				int[] tiers = new int[cap + 2];
				for (int i = 0; i < tiers.length; i++) {
					ServerPlayer player = arena.finish(helper);
					helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "clear " + (i + 1) + " did not complete");
					rearm(helper, arena, player, clock);
					tiers[i] = arena.entry().tier();
				}
				WorldTestSupport.result("tierClimb", "cap=" + cap + " tiersAfterRearm=" + java.util.Arrays.toString(tiers));
				for (int i = 0; i < tiers.length; i++) {
					helper.assertTrue(tiers[i] == Math.min(cap, i + 1), "after rearm " + (i + 1) + " the tier is " + tiers[i] + ", not " + Math.min(cap, i + 1));
				}
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aOnceSiteStaysAtTierZero(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			arena.finish(helper);
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && arena.entry().tier() == 0, "the once site is at tier " + arena.entry().tier());
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aHigherTierSpawnsMobsWithMoreHealthAndDamage(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			RuinConfig.Values config = RuinConfig.get();
			RuinData.get(arena.level()).entryFor(arena.corePos(), WorldTestInit.reset.id()).setTier(2);
			Mob baseline = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
			baseline.setNoAi(true);
			arena.wake();
			Mob mob = rosterMob(arena, 0);
			double healthRatio = mob.getMaxHealth() / baseline.getMaxHealth();
			double damageRatio = mob.getAttributeValue(Attributes.ATTACK_DAMAGE) / baseline.getAttributeValue(Attributes.ATTACK_DAMAGE);
			WorldTestSupport.result("tierMobs", "tier=2 healthRatio=" + healthRatio + " damageRatio=" + damageRatio + " health=" + mob.getHealth() + "/" + mob.getMaxHealth());
			helper.assertTrue(Math.abs(healthRatio - (1.0 + 2 * config.tierHealthStep())) < 1.0E-3, "the health ratio is " + healthRatio + ", not " + (1.0 + 2 * config.tierHealthStep()));
			helper.assertTrue(Math.abs(damageRatio - (1.0 + 2 * config.tierDamageStep())) < 1.0E-3, "the damage ratio is " + damageRatio + ", not " + (1.0 + 2 * config.tierDamageStep()));
			helper.assertTrue(mob.getHealth() == mob.getMaxHealth(), "the mob spawned hurt: " + mob.getHealth() + "/" + mob.getMaxHealth());
			baseline.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theVaultRollsOnceMoreForEachTier(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			AtomicLong clock = new AtomicLong(33_000_000L);
			RuinClock.Source old = RuinClock.replace(l -> clock.get());
			try {
				int base = RuinConfig.get().vaultRolls(RuinLevels.at(arena.level(), net.minecraft.world.phys.Vec3.atCenterOf(arena.corePos())));
				int[] gained = new int[3];
				int before = 0;
				for (int i = 0; i < gained.length; i++) {
					ServerPlayer player = arena.finish(helper);
					int now = RuinCoreGameTest.count(arena.vault(), Items.EMERALD);
					gained[i] = now - before;
					before = now;
					rearm(helper, arena, player, clock);
				}
				WorldTestSupport.result("tierRolls", "base=" + base + " gainedPerClear=" + java.util.Arrays.toString(gained));
				for (int i = 0; i < gained.length; i++) {
					helper.assertTrue(gained[i] == base + i, "clear " + (i + 1) + " at tier " + i + " rolled " + gained[i] + " times, not " + (base + i));
				}
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest
	public void theHealthGainOfTierAndOmensNeverPassesOneHundredPercent(final GameTestHelper helper) {
		RuinConfig.Values shipped = RuinConfig.get();
		double top = RuinTiers.healthGain(shipped, shipped.tierCap(), List.of(Omen.BULWARK));
		helper.assertTrue(top <= RuinTiers.HEALTH_GAIN_CAP + 1.0E-9, "tier cap plus Bulwark gains " + top);
		RuinConfig.Values steep = shipped.withTiers(3, 0.5, 0.1);
		double capped = RuinTiers.healthGain(steep, 3, List.of(Omen.BULWARK, Omen.BULWARK));
		helper.assertTrue(capped == RuinTiers.HEALTH_GAIN_CAP, "a gain of 2.0 was capped to " + capped);
		helper.assertTrue(RuinTiers.healthGain(shipped, 1, List.of()) == shipped.tierHealthStep(), "a small gain was changed by the cap");
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void aSteepTierPlusBulwarkSpawnsMobsAtDoubleHealthAndNoMore(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			RuinConfig.Values oldConfig = RuinConfig.replace(RuinConfig.get().withTiers(3, 0.5, 0.1));
			Omens.Roller oldRoller = Omens.replaceRoller((random, tier) -> List.of(Omen.BULWARK));
			try {
				RuinData.get(arena.level()).entryFor(arena.corePos(), WorldTestInit.reset.id()).setTier(3);
				Mob baseline = WorldTestInit.husk.spawn(arena.level(), RuinMobGameTest.spawnSpot(arena));
				baseline.setNoAi(true);
				arena.wake();
				double ratio = rosterMob(arena, 0).getMaxHealth() / baseline.getMaxHealth();
				WorldTestSupport.result("tierCap", "healthRatio=" + ratio);
				helper.assertTrue(Math.abs(ratio - 2.0) < 1.0E-3, "tier 3 at step 0.5 plus Bulwark gave a health ratio of " + ratio + ", not the cap of 2.0");
				baseline.discard();
			} finally {
				Omens.replaceRoller(oldRoller);
				RuinConfig.replace(oldConfig);
			}
		});
	}

	@GameTest
	public void aSavedCoreWithoutTierOrOmensLoadsAtTierZeroAndANewOneKeepsBoth(final GameTestHelper helper) {
		BlockPos pos = new BlockPos(5, 64, 5);
		RuinData data = new RuinData();
		RuinData.Entry entry = data.entryFor(pos, "test_reset");
		entry.setTier(2);
		entry.setOmens(List.of("hexed", "swarming"));
		JsonElement json = RuinData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		RuinData kept = RuinData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(kept.entry(pos).tier() == 2 && kept.entry(pos).omens().equals(List.of("hexed", "swarming")), "a round trip lost the tier or the omens: " + kept.entry(pos).tier() + " " + kept.entry(pos).omens());
		JsonArray cores = json.getAsJsonObject().getAsJsonArray("cores");
		helper.assertTrue(cores.size() == 1 && cores.get(0).getAsJsonObject().has("tier"), "the saved core has no tier field, so the strip below proves nothing");
		for (JsonElement core : cores) {
			JsonObject object = core.getAsJsonObject();
			object.remove("tier");
			object.remove("omens");
		}
		RuinData old = RuinData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(old.entry(pos) != null && old.entry(pos).tier() == 0 && old.entry(pos).omens().isEmpty(), "an old save loaded at tier " + (old.entry(pos) == null ? -1 : old.entry(pos).tier()));
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS)
	public void aSiteHookRunsOnceWhenItsCoreRearms(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.hooked, arena -> {
			AtomicLong clock = new AtomicLong(35_000_000L);
			RuinClock.Source old = RuinClock.replace(l -> clock.get());
			try {
				ServerPlayer player = arena.finish(helper);
				BlockPos core = arena.corePos();
				arena.leave(player);
				clock.addAndGet(Claim.DAY_TICKS - 1);
				arena.core().pulse(arena.level());
				int early = WorldTestInit.REARMS.getOrDefault(core, new java.util.concurrent.atomic.AtomicInteger()).get();
				clock.addAndGet(1);
				arena.core().pulse(arena.level());
				int rearmed = WorldTestInit.REARMS.getOrDefault(core, new java.util.concurrent.atomic.AtomicInteger()).get();
				ServerLevel level = arena.level();
				WorldTestSupport.result("rearmHook", "early=" + early + " afterDelay=" + rearmed + " phase=" + arena.phase() + " level=" + level.dimension().identifier());
				helper.assertTrue(early == 0, "the hook ran before the delay");
				helper.assertTrue(rearmed == 1, "the hook ran " + rearmed + " times at the rearm, not 1");
			} finally {
				RuinClock.replace(old);
			}
		});
	}
}
