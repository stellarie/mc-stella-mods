package com.stella.mobs;

import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A summoned Wither is level 100, or 20 above the highest player level near it, whichever is higher. The level is fixed
 * at the summon, and the spawn scaling reads it. A Wither with no state is not an encounter.
 */
public final class WitherLevelGameTest {
	private static final int FAR = 500;

	@GameTest
	public void levelFollowsTheHighestPlayer(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		int base = config.getInt(WitherConfig.LEVEL_BASE);
		int over = config.getInt(WitherConfig.LEVEL_OVER_PLAYER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = player.position();
		StringBuilder log = new StringBuilder();
		int[] playerLevels = {1, 50, 80, 81, 150, 400};
		for (int playerLevel : playerLevels) {
			player.experienceLevel = playerLevel;
			int level = WitherEncounter.levelFor(List.of(player), at, ScalingConfigStore.get(), config);
			int expected = Math.max(base, playerLevel + over);
			log.append(playerLevel).append("->").append(level).append(' ');
			helper.assertTrue(level == expected, "player level " + playerLevel + " gave " + level + ", want " + expected);
		}
		int none = WitherEncounter.levelFor(List.of(), at, ScalingConfigStore.get(), config);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherLevelTable RESULT {}none->{}", log, none);
		helper.assertTrue(none == base, "no players gave " + none);
		helper.assertTrue(base == 100 && over == 20, "the defaults are not 100 and 20: " + base + " " + over);
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void spectatorsAndFarPlayersAreIgnored(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		int base = config.getInt(WitherConfig.LEVEL_BASE);
		int over = config.getInt(WitherConfig.LEVEL_OVER_PLAYER);
		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		Vec3 at = near.position();
		helper.assertTrue(spectator.isSpectator(), "the stand-in spectator is not one");
		spectator.experienceLevel = 300;
		near.experienceLevel = 130;
		far.experienceLevel = 400;
		far.setPos(at.x + FAR, at.y, at.z);
		int mixed = WitherEncounter.levelFor(List.of(spectator, near, far), at, ScalingConfigStore.get(), config);
		int onlyFar = WitherEncounter.levelFor(List.of(spectator, far), at, ScalingConfigStore.get(), config);
		int onlySpectator = WitherEncounter.levelFor(List.of(spectator), at, ScalingConfigStore.get(), config);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherLevelIgnored RESULT mixed={} onlyFar={} onlySpectator={}", mixed, onlyFar, onlySpectator);
		helper.assertTrue(mixed == 130 + over, "a spectator or a far player set the level: " + mixed);
		helper.assertTrue(onlyFar == 400 + over, "with nobody near, the highest player counts: " + onlyFar);
		helper.assertTrue(onlySpectator == base, "a spectator set the level: " + onlySpectator);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = WideArena.PADDING)
	public void aSummonedWitherTakesTheLevel(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		WideArena arena = WideArena.build(helper);
		ServerPlayer player = arena.survivalPlayer(28, 28);
		player.experienceLevel = 150;
		helper.runBeforeTestEnd(() -> {
			player.experienceLevel = 0;
			arena.finish();
		});
		arena.whenReady(List.of(new Vec3(28, 12, 40)), () -> {
			WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
			Vec3 spot = arena.at(28, 12, 40);
			wither.snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
			helper.getLevel().addFreshEntity(wither);
			arena.track(wither);
			int expected = 150 + config.getInt(WitherConfig.LEVEL_OVER_PLAYER);
			int level = ThreatState.level(wither);
			float health = wither.getMaxHealth();
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSpawnLevel RESULT level={} health={} flag={}", level, health, WitherState.hasWitherFlag(wither));
			helper.assertTrue(WitherState.hasWitherFlag(wither), "a fresh Wither has no encounter state");
			helper.assertTrue(level == expected, "the Wither is level " + level + ", want " + expected);
			// The vanilla attribute caps max health, so a high level reaches the cap.
			double cap = ((RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
			float scaled = (float) Math.min(cap, SpawnScaling.healthMultiplier(expected) * 300.0);
			helper.assertTrue(Math.abs(health - scaled) < 1.0F, "max health " + health + ", want " + scaled);
			player.experienceLevel = 500;
			helper.assertTrue(ThreatState.level(wither) == expected, "the level moved with the player: " + ThreatState.level(wither));
			helper.succeed();
		});
	}

	@GameTest
	public void flagReadsWithoutCreatingState(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
		WitherBoss bare = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		boolean zombieFlag = WitherState.hasWitherFlag(zombie);
		boolean bareFlag = WitherState.hasWitherFlag(bare);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=witherFlagRead RESULT zombie={} bare={} zombieState={} bareState={}",
			zombieFlag, bareFlag, WitherState.find(zombie), WitherState.find(bare));
		helper.assertTrue(!zombieFlag && !bareFlag, "a mob with no state read as an encounter Wither");
		helper.assertTrue(WitherState.find(zombie) == null && WitherState.find(bare) == null, "reading the flag created a state");
		helper.succeed();
	}
}
