package com.stella.nether;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.Vec3;

/** A raid mob's level is its mode's minimum or the best player's level; its health and hits follow the mobs mod's curves. */
public class RaidLevelGameTest {
	static final int MOBS = 4;
	static final float PROBE_HIT = 3.0F;
	static final double TOLERANCE = 0.05;

	/** A raid at a mode with a player of a level, and mobs spawned beside them with no AI. */
	record Raid(ServerPlayer player, ServerLevel level, List<ZombifiedPiglin> mobs, Zombie sponge) {
	}

	static Raid open(final GameTestHelper helper, final RaidMode mode, final int playerLevel) {
		NetherTestSupport.fresh(helper);
		ServerPlayer player = RaidModeGameTest.enter(helper, RaidTravel.Choice.RAID, mode);
		player.experienceLevel = playerLevel;
		ServerLevel raid = (ServerLevel) player.level();
		List<ZombifiedPiglin> mobs = new ArrayList<>();
		for (int i = 0; i < MOBS; i++) {
			ZombifiedPiglin mob = RaidModeGameTest.spawnInRaid(raid, player.position().add(2.0 + i % 2, 0.0, i / 2 - 1.0));
			mob.setNoAi(true);
			mobs.add(mob);
		}
		// Tagged, so the raid leaves its health as set here: nothing can finish it, and its armor is none.
		Zombie sponge = EntityTypes.ZOMBIE.create(raid, EntitySpawnReason.MOB_SUMMONED);
		sponge.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		Vec3 at = player.position().add(0.0, 0.0, 4.0);
		sponge.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		sponge.setNoAi(true);
		sponge.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		sponge.setHealth(1000.0F);
		sponge.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		raid.addFreshEntity(sponge);
		return new Raid(player, raid, mobs, sponge);
	}

	static void loaded(final GameTestHelper helper, final Raid raid) {
		for (ZombifiedPiglin mob : raid.mobs()) {
			helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(mob.getUUID()), "a mob has not loaded yet");
		}
		helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(raid.sponge().getUUID()), "the sponge has not loaded yet");
	}

	/** Checks every mob against the level; returns what is wrong, or nothing. A rank (elite or boss) adds its bonus on top. */
	static String verify(final GameTestHelper helper, final Raid raid, final int expected, final String label) {
		double vanilla = DefaultAttributes.getSupplier(EntityTypes.ZOMBIFIED_PIGLIN).getBaseValue(Attributes.MAX_HEALTH);
		StringBuilder seen = new StringBuilder();
		int wrongLevel = 0;
		int wrongHealth = 0;
		int wrongHit = 0;
		for (ZombifiedPiglin mob : raid.mobs()) {
			ThreatState rolled = ThreatState.get(mob);
			int level = ThreatState.level(mob);
			boolean levelOk = rolled != null && rolled.areaLevel() == expected && rolled.variation() == 0 && level >= expected && level <= expected + 10;
			AttributeModifier health = mob.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID);
			// A rank or a trait moves health on its own, so only the level modifier and the base are judged.
			boolean ranked = level != expected;
			boolean healthOk = health != null && Math.abs(health.amount() - (SpawnScaling.healthMultiplier(level) - 1.0)) < 1.0E-9
				&& (ranked || Math.abs(mob.getAttributeBaseValue(Attributes.MAX_HEALTH) - vanilla) < 1.0E-6);
			// A hard raid hands its boons to the sponge too; none may soften the probe.
			raid.sponge().removeAllEffects();
			raid.sponge().setAbsorptionAmount(0.0F);
			raid.sponge().setHealth(1000.0F);
			raid.sponge().damageCooldownTime = 0;
			raid.sponge().hurtServer(raid.level(), raid.level().damageSources().mobAttack(mob), PROBE_HIT);
			double lost = 1000.0 - raid.sponge().getHealth();
			boolean hitOk = Math.abs(lost - (PROBE_HIT + SpawnScaling.tailFlat(level))) < TOLERANCE;
			wrongLevel += levelOk ? 0 : 1;
			wrongHealth += healthOk ? 0 : 1;
			wrongHit += hitOk ? 0 : 1;
			seen.append(' ').append(rolled == null ? -1 : rolled.areaLevel()).append('/').append(level).append('/').append(mob.getMaxHealth()).append('/').append(lost);
		}
		NetherTestSupport.result(label, "expected=" + expected + " wrongLevel=" + wrongLevel + " wrongHealth=" + wrongHealth + " wrongHit=" + wrongHit
			+ " area/level/max/hit=" + seen);
		return wrongLevel == 0 && wrongHealth == 0 && wrongHit == 0 ? ""
			: label + ": " + wrongLevel + " off the level " + expected + ", " + wrongHealth + " off the level's health, " + wrongHit + " off the level's hit:" + seen + ". ";
	}

	static void close(final GameTestHelper helper, final Raid raid) {
		raid.mobs().forEach(ZombifiedPiglin::discard);
		raid.sponge().discard();
		NetherTestSupport.leave(helper, raid.player());
	}

	@GameTest(environment = "mod-stella-nether-test:raid_level_modes", maxTicks = 600)
	public void levelsFollowTheMode(final GameTestHelper helper) {
		Raid[] raid = new Raid[1];
		StringBuilder failures = new StringBuilder();
		helper.startSequence()
			.thenExecute(() -> raid[0] = open(helper, RaidMode.NORMAL, 0))
			.thenWaitUntil(() -> loaded(helper, raid[0]))
			.thenExecute(() -> {
				failures.append(verify(helper, raid[0], RaidInstance.MIN_LEVEL_NORMAL, "raidLevelNormal"));
				close(helper, raid[0]);
				raid[0] = open(helper, RaidMode.HARD, 0);
			})
			.thenWaitUntil(() -> loaded(helper, raid[0]))
			.thenExecute(() -> {
				failures.append(verify(helper, raid[0], RaidInstance.MIN_LEVEL_HARD, "raidLevelHard"));
				close(helper, raid[0]);
				helper.assertTrue(failures.isEmpty(), failures.toString());
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:raid_level_player", maxTicks = 600)
	public void aBetterPlayerRaisesTheLevelInBothModes(final GameTestHelper helper) {
		Raid[] raid = new Raid[1];
		StringBuilder failures = new StringBuilder();
		helper.startSequence()
			.thenExecute(() -> raid[0] = open(helper, RaidMode.NORMAL, 80))
			.thenWaitUntil(() -> loaded(helper, raid[0]))
			.thenExecute(() -> {
				failures.append(verify(helper, raid[0], 80, "raidLevelBestPlayerNormal"));
				close(helper, raid[0]);
				raid[0] = open(helper, RaidMode.HARD, 60);
			})
			.thenWaitUntil(() -> loaded(helper, raid[0]))
			.thenExecute(() -> {
				failures.append(verify(helper, raid[0], 60, "raidLevelBestPlayerHard"));
				close(helper, raid[0]);
				helper.assertTrue(failures.isEmpty(), failures.toString());
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:raid_level_brood", maxTicks = 1200)
	public void theSovereignsBroodTakesTheRaidLevelToo(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.BROOD, scene.player()))
			.thenWaitUntil(() -> {
				helper.assertTrue(cast[0].done(), "the brood has not been called");
				for (net.minecraft.world.entity.Entity child : cast[0].spawned()) {
					helper.assertTrue(NetherGameTestInit.LOADED_IN_RAID.contains(child.getUUID()), "a blaze has not loaded yet");
				}
			})
			.thenExecute(() -> {
				int wrong = 0;
				StringBuilder seen = new StringBuilder();
				for (net.minecraft.world.entity.Entity child : cast[0].spawned()) {
					Blaze blaze = (Blaze) child;
					ThreatState rolled = ThreatState.get(blaze);
					boolean scaled = blaze.getAttribute(Attributes.MAX_HEALTH).getModifier(SpawnScaling.HEALTH_ID) != null;
					boolean ok = rolled != null && rolled.areaLevel() == RaidInstance.MIN_LEVEL_NORMAL && scaled;
					wrong += ok ? 0 : 1;
					seen.append(' ').append(rolled == null ? -1 : rolled.areaLevel()).append('/').append(scaled).append('/').append(blaze.getMaxHealth());
				}
				NetherTestSupport.result("raidLevelBrood", "wrong=" + wrong + " level/scaled/max=" + seen);
				cast[0].spawned().forEach(net.minecraft.world.entity.Entity::discard);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(wrong == 0, wrong + " of the brood off the raid level:" + seen);
			})
			.thenSucceed();
	}
}
