package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.BlazeVolleyGoal;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.projectile.StellaSmallFireball;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * R24, the elite blaze volley: ten fireballs over the first forty ticks of a two hundred tick cycle,
 * then a long silence, and one ground spread per volley rather than one per ball. Identifiers are
 * accumulated level wide, because a ball in flight is not a ball loosed.
 */
public class BlazeVolleyGameTest {
	private static final String TEST = "blazeVolley";
	private static final String ELITE_CONFIG = """
		{
			"entities": {},
			"rolls": { "elite_chance": 1.0, "boss_chance": 0.0, "roll_chance": 0.0 }
		}""";

	@GameTest(maxTicks = 400)
	public void eliteBlazeLoosesTenBallsThenGoesQuiet(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		try {
			Arena.buildFloor(helper);
			helper.setTime(Arena.NIGHT_TIME);
			Blaze blaze = helper.spawnMob(EntityTypes.BLAZE, 2, 3, 2).spawn();
			// No config write in this test: the elite is marked and the goal installed by hand, so a long
			// window cannot leak a non-default elite chance into the tests running beside it.
			Elite.mark(blaze, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
			BehaviorInstaller.installBlazeVolley(blaze);
			Zombie target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 3, 7).withNoFreeWill().spawn();
			target.setPersistenceRequired();
			// The target must outlive the volley: a dead target stops the goal at canUse, and the count
			// then reads as a short volley rather than a stopped one.
			target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
			target.setHealth(1000.0F);
			helper.onEachTick(() -> blaze.setTarget(target));

			Map<UUID, Long> loosedAt = new HashMap<>();
			int[] firesBefore = {countFire(helper, target)};
			// Fire on stone burns out, so the spread is a decaying quantity and its maximum is the fact.
			int[] maxFires = {firesBefore[0]};
			helper.onEachTick(() -> {
				// Level wide, not a box: a ball that leaves a box still had an identity, and a ball that
				// lands and despawns inside one tick must still be counted where it appeared.
				for (Entity entity : helper.getLevel().getAllEntities()) {
					if (entity instanceof StellaSmallFireball ball && ball.getOwner() == blaze) {
						loosedAt.putIfAbsent(ball.getUUID(), helper.getTick());
					}
				}

				if (helper.getTick() % 5 == 0) {
					maxFires[0] = Math.max(maxFires[0], countFire(helper, target));
				}
			});

			helper.startSequence()
				// Wait for the property, never a chosen tick: the goal's own counter decides when the tenth
				// ball leaves, and a fixed window closed before it in three probes.
				.thenWaitUntil(() -> helper.assertTrue(
					loosedAt.size() >= BlazeVolleyGoal.VOLLEY_SIZE,
					"the volley never reached " + BlazeVolleyGoal.VOLLEY_SIZE + " balls, last seen "
						+ loosedAt.size()
				))
				.thenExecute(() -> {
					helper.assertTrue(Elite.isElite(blaze), "the blaze never rolled elite");
					helper.assertValueEqual(
						Integer.valueOf(loosedAt.size()), Integer.valueOf(BlazeVolleyGoal.VOLLEY_SIZE), "balls loosed in one volley"
					);
					long first = loosedAt.values().stream().mapToLong(Long::longValue).min().orElse(-1L);
					long last = loosedAt.values().stream().mapToLong(Long::longValue).max().orElse(-1L);
					long span = last - first;
					helper.assertTrue(
						span >= (BlazeVolleyGoal.VOLLEY_SIZE - 1) * BlazeVolleyGoal.BALL_SPACING_TICKS,
						"the volley spanned " + span + " ticks, tighter than the spec's "
							+ (BlazeVolleyGoal.VOLLEY_SIZE - 1) * BlazeVolleyGoal.BALL_SPACING_TICKS
					);
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test={} RESULT volley={} firstTick={} lastTick={} span={} interval={} spacing={}",
						TEST, loosedAt.size(), first, last, span, BlazeVolleyGoal.VOLLEY_INTERVAL_TICKS,
						BlazeVolleyGoal.BALL_SPACING_TICKS
					);
				})
				.thenIdle(BlazeVolleyGoal.VOLLEY_INTERVAL_TICKS / 2)
				.thenExecute(() -> {
					boolean griefing = helper.getLevel().getGameRules().get(GameRules.MOB_GRIEFING);
					int firesNow = countFire(helper, target);
					helper.assertValueEqual(
						Integer.valueOf(loosedAt.size()), Integer.valueOf(BlazeVolleyGoal.VOLLEY_SIZE),
						"balls loosed by the end of the silence window"
					);					// The documented contract either way: with griefing on the spread writes fire, and with it
					// off the spread writes nothing while the volley still fires. The maximum is read because
					// fire on stone burns out before this assertion runs.
					helper.assertTrue(
						griefing ? maxFires[0] > firesBefore[0] : firesNow == firesBefore[0],
						"the spread and mob griefing disagree: griefing=" + griefing + " before=" + firesBefore[0]
							+ " max=" + maxFires[0] + " now=" + firesNow
					);
					StellaMobs.LOGGER.info(
						"[BEHAVIOR] test={} RESULT quiet={} balls={} firesBefore={} firesMax={} radius={}",
						TEST, BlazeVolleyGoal.VOLLEY_INTERVAL_TICKS - BlazeVolleyGoal.VOLLEY_SIZE * BlazeVolleyGoal.BALL_SPACING_TICKS,
						loosedAt.size(), firesBefore[0], maxFires[0], BlazeVolleyGoal.SPREAD_RADIUS
					);
				})
				.thenSucceed();
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static int countFire(final GameTestHelper helper, final Zombie target) {
		int count = 0;
		BlockPos centre = target.blockPosition();

		for (int dx = -BlazeVolleyGoal.SPREAD_RADIUS; dx <= BlazeVolleyGoal.SPREAD_RADIUS; dx++) {
			for (int dz = -BlazeVolleyGoal.SPREAD_RADIUS; dz <= BlazeVolleyGoal.SPREAD_RADIUS; dz++) {
				for (int dy = 1; dy >= -BlazeVolleyGoal.SPREAD_DEPTH; dy--) {
					if (helper.getLevel().getBlockState(centre.offset(dx, dy, dz)).is(Blocks.FIRE)) {
						count++;
					}
				}
			}
		}

		return count;
	}
}
