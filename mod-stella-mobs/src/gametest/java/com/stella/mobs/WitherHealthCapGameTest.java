package com.stella.mobs;

import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * The vanilla attribute caps max health at 1024. A Wither whose formula health is higher keeps the rest as a divisor on
 * every hit, so it still takes formula-health worth of damage to die while its bar reads health over max health.
 */
public final class WitherHealthCapGameTest {
	private static final double BASE_HEALTH = 300.0;
	private static final int PLAYER_LEVEL = 150;

	@GameTest(maxTicks = 1000, padding = WideArena.PADDING)
	public void aLevelPastTheCapTakesFormulaHealthToDie(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		ServerPlayer player = arena.survivalPlayer(28, 28);
		player.experienceLevel = PLAYER_LEVEL;
		helper.runBeforeTestEnd(() -> {
			player.experienceLevel = 0;
			arena.finish();
		});
		arena.whenReady(List.of(new Vec3(12, 12, 40), new Vec3(44, 12, 40)), () -> {
			WitherBoss half = spawn(helper, arena, 12);
			WitherBoss full = spawn(helper, arena, 44);
			ServerLevel level = helper.getLevel();
			int summonLevel = ThreatState.level(half);
			double formula = BASE_HEALTH * SpawnScaling.healthMultiplier(summonLevel);
			float max = half.getMaxHealth();
			helper.assertTrue(formula > max, "level " + summonLevel + " does not pass the cap: formula " + formula + " max " + max);
			double divisor = WitherState.find(half).healthDivisor();
			DamageSource source = level.damageSources().genericKill();

			half.hurtServer(level, source, (float) (formula * 0.5));
			float afterHalf = half.getHealth();
			boolean aliveAfterHalf = !half.isDeadOrDying();
			full.hurtServer(level, source, (float) (formula * 1.02));
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherHealthCap RESULT level={} formula={} max={} divisor={} healthAfterHalf={} aliveAfterHalf={} deadAfterFull={}",
				summonLevel, formula, max, divisor, afterHalf, aliveAfterHalf, full.isDeadOrDying());
			helper.assertTrue(aliveAfterHalf, "half the formula health killed it");
			helper.assertTrue(Math.abs(afterHalf - max / 2.0F) <= 2.0F, "half the formula health left " + afterHalf + " of " + max);
			helper.assertTrue(full.isDeadOrDying(), "the full formula health did not kill it: health " + full.getHealth());
			helper.succeed();
		});
	}

	private static WitherBoss spawn(final GameTestHelper helper, final WideArena arena, final double x) {
		WitherBoss wither = EntityTypes.WITHER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		Vec3 spot = arena.at(x, 12, 40);
		wither.snapTo(spot.x, spot.y, spot.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(wither);
		arena.track(wither);
		return wither;
	}
}
