package com.stella.mobs;

import com.stella.mobs.behavior.WitherShots;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * A hit that takes the Wither past both thresholds starts the second rage only: skulls rain from above within twenty-four
 * blocks, about half of them near a player.
 */
public final class WitherRainGameTest {
	private static final int SHIPPED_RAGE_TICKS = 600;
	private static final double HEALTH_BEFORE = 0.6;
	private static final double HEALTH_AFTER = 0.2;
	private static final int GAP = 20;
	private static final int WATCH_TICKS = 300;
	private static final double RADIUS = 24.0;
	private static final double HEIGHT = 14.0;
	private static final double NEAR = 6.0;
	private static final double SLACK = 1.0;
	private static final double LEAST_NEAR_SHARE = 0.35;

	private record Spawn(Vec3 at, Vec3 wither) {
	}

	@GameTest(maxTicks = 1500, padding = WideArena.PADDING)
	public void aHitPastBothThresholdsStartsTheRainOnly(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		WitherBoss[] wither = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		long[] start = {-1};
		long[] hitAt = {-1};
		Set<WitherSkullShot> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Spawn> spawns = new ArrayList<>();
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			ServerLevel level = helper.getLevel();
			WitherBoss boss = wither[0];
			if (hitAt[0] < 0 && helper.getTick() - start[0] >= GAP) {
				boss.setHealth((float) (boss.getMaxHealth() * HEALTH_BEFORE));
				double loss = boss.getMaxHealth() * (HEALTH_BEFORE - HEALTH_AFTER);
				boss.setInvulnerableTime(0);
				boss.hurtServer(level, level.damageSources().genericKill(), (float) (WitherState.find(boss).healthDivisor() * loss));
				WitherState after = WitherState.find(boss);
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRageBoth RESULT kind={} ticksLeft={} aUsed={} bUsed={} fraction={}",
					after.rageKind(), after.rageTicksLeft(), after.rageAUsed(), after.rageBUsed(), boss.getHealth() / boss.getMaxHealth());
				helper.assertTrue(after.rageKind() == WitherState.RAGE_RAIN, "a hit past both thresholds did not start the rain: kind " + after.rageKind());
				helper.assertTrue(after.rageAUsed() && after.rageBUsed(), "both rages are not spent");
				helper.assertTrue(after.rageTicksLeft() == SHIPPED_RAGE_TICKS, "the rain lasts " + after.rageTicksLeft() + " ticks");
				// An aimed skull already in the air is not rain.
				seen.addAll(WitherShots.snapshot(boss));
				hitAt[0] = helper.getTick();
				return;
			}
			if (hitAt[0] < 0) {
				return;
			}
			for (WitherSkullShot skull : WitherShots.snapshot(boss)) {
				if (seen.add(skull)) {
					spawns.add(new Spawn(skull.origin(), boss.position()));
					helper.assertTrue(skull.spec().share() == WitherConfigStore.get().get(WitherConfig.RAGE_B_SHARE), "a rain skull with share " + skull.spec().share());
				}
			}
			if (helper.getTick() - hitAt[0] >= WATCH_TICKS) {
				int tall = 0;
				int wide = 0;
				int nearPlayer = 0;
				for (Spawn spawn : spawns) {
					tall += Math.abs(spawn.at().y - (spawn.wither().y + HEIGHT)) > SLACK ? 1 : 0;
					wide += Math.hypot(spawn.at().x - spawn.wither().x, spawn.at().z - spawn.wither().z) > RADIUS + SLACK ? 1 : 0;
					nearPlayer += Math.hypot(spawn.at().x - player[0].getX(), spawn.at().z - player[0].getZ()) <= NEAR + SLACK ? 1 : 0;
				}
				double nearShare = spawns.isEmpty() ? 0.0 : nearPlayer / (double) spawns.size();
				StellaMobs.LOGGER.info("[BEHAVIOR] test=witherRain RESULT skulls={} wrongHeight={} outsideRadius={} nearPlayerShare={}", spawns.size(), tall, wide, nearShare);
				helper.assertTrue(spawns.size() >= WATCH_TICKS / 3 * 0.8, "only " + spawns.size() + " skulls rained in " + WATCH_TICKS + " ticks");
				helper.assertTrue(tall == 0, tall + " skulls began away from 14 blocks above the Wither");
				helper.assertTrue(wide == 0, wide + " skulls began more than 24 blocks from the Wither");
				helper.assertTrue(nearShare >= LEAST_NEAR_SHARE, "only " + nearShare + " of the skulls fell near the player");
				helper.succeed();
			}
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			start[0] = helper.getTick();
		});
	}
}
