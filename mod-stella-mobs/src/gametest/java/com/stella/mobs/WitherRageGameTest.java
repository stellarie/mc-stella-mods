package com.stella.mobs;

import com.stella.mobs.behavior.WitherShots;
import com.stella.mobs.behavior.WitherState;
import com.stella.mobs.config.WitherConfig;
import com.stella.mobs.config.WitherConfigStore;
import com.stella.mobs.projectile.WitherSkullShot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * Below half health the Wither adds a sphere burst of skulls to its volleys. The old barrage and rain rages are gone.
 * One scene runs the phases in order, because each arena costs the server a large chunk load.
 */
public final class WitherRageGameTest {
	private static final double ABOVE_HALF = 0.55;
	private static final double BELOW_HALF = 0.45;
	private static final int QUIET_TICKS = 60;
	private static final int SHIPPED_COUNT = 26;
	private static final int SHIPPED_INTERVAL = 100;
	private static final int SHIPPED_CAP = 40;
	private static final double RAY_SLACK = 2.0;
	private static final double FLAT = 0.2;
	private static final double OFF_AXIS = 0.15;
	private static final int LEAST_PER_QUADRANT = 3;
	private static final int INTERVAL_SLACK = 2;
	private static final int BURSTS_WANTED = 2;

	/** The skulls of one tick, in the order they were loosed. */
	private record Burst(int ownerTick, List<WideArena.Shot> shots) {
	}

	private static List<Burst> bursts(final List<WideArena.Shot> shots, final double share) {
		List<Burst> found = new ArrayList<>();
		for (WideArena.Shot shot : shots) {
			if (!(shot.projectile() instanceof WitherSkullShot skull) || skull.spec().share() != share) {
				continue;
			}
			if (found.isEmpty() || found.get(found.size() - 1).ownerTick() != shot.ownerTick()) {
				found.add(new Burst(shot.ownerTick(), new ArrayList<>()));
			}
			found.get(found.size() - 1).shots().add(shot);
		}
		return found;
	}

	@GameTest
	public void theShippedPhasesAreHalfAndAQuarter(final GameTestHelper helper) {
		WitherConfig config = WitherConfigStore.get();
		helper.assertTrue(config.get(WitherConfig.SPHERE_THRESHOLD) == 0.5, "the sphere starts at " + config.get(WitherConfig.SPHERE_THRESHOLD) + ", want half health");
		helper.assertTrue(config.get(WitherConfig.SMALL_THRESHOLD) == 0.25, "the small withers come at " + config.get(WitherConfig.SMALL_THRESHOLD) + ", want a quarter");
		helper.assertTrue(config.getInt(WitherConfig.SPHERE_COUNT) == SHIPPED_COUNT && config.getInt(WitherConfig.SPHERE_INTERVAL) == SHIPPED_INTERVAL,
			"the sphere is " + config.getInt(WitherConfig.SPHERE_COUNT) + " skulls every " + config.getInt(WitherConfig.SPHERE_INTERVAL) + " ticks");
		helper.assertTrue(config.getInt(WitherConfig.SKULL_LIVE_CAP) == SHIPPED_CAP, "the skull cap moved to " + config.getInt(WitherConfig.SKULL_LIVE_CAP));
		helper.assertTrue(config.getInt(WitherConfig.SMALL_COUNT) == 4 && config.get(WitherConfig.SMALL_SCALE) == 0.6
			&& config.get(WitherConfig.SMALL_DAMAGE_FACTOR) == 0.5 && config.get(WitherConfig.SMALL_HEALTH_SHARE) == 0.25, "a small wither number moved");
		helper.assertTrue(config.getInt(WitherConfig.AIM_RANDOM_SKULLS) == 2, "a volley adds " + config.getInt(WitherConfig.AIM_RANDOM_SKULLS) + " random skulls, want 2");
		helper.succeed();
	}

	@GameTest(maxTicks = 900, padding = WideArena.PADDING)
	public void theSphereComesBelowHalfAndVolleysContinue(final GameTestHelper helper) {
		WideArena arena = WideArena.build(helper);
		helper.runBeforeTestEnd(arena::finish);
		WitherBoss[] wither = new WitherBoss[1];
		ServerPlayer[] player = new ServerPlayer[1];
		long[] phaseStart = {0};
		int[] phase = {0};
		int[] peakLive = {0};
		Map<WitherSkullShot, Double> farthest = new IdentityHashMap<>();
		helper.onEachTick(() -> {
			if (wither[0] == null) {
				return;
			}
			WideArena.keepValid(player[0]);
			WitherBoss boss = wither[0];
			WitherConfig config = WitherConfigStore.get();
			for (WitherSkullShot skull : WitherShots.snapshot(boss)) {
				if (skull.spec().share() == config.get(WitherConfig.SPHERE_SHARE)) {
					farthest.merge(skull, skull.position().distanceTo(skull.origin()), Math::max);
				}
			}
			peakLive[0] = Math.max(peakLive[0], WitherShots.live(boss));
			long elapsed = helper.getTick() - phaseStart[0];
			List<Burst> spheres = bursts(WideArena.shots(boss), config.get(WitherConfig.SPHERE_SHARE));
			if (phase[0] == 0) {
				if (elapsed < QUIET_TICKS) {
					return;
				}
				helper.assertTrue(spheres.isEmpty(), "a sphere burst came at " + boss.getHealth() / boss.getMaxHealth() + " of max health");
				helper.assertTrue(!WitherState.find(boss).rageAUsed(), "the sphere is marked started above half health");
				boss.setHealth((float) (boss.getMaxHealth() * BELOW_HALF));
				phase[0] = 1;
				phaseStart[0] = helper.getTick();
				return;
			}
			if (spheres.size() < BURSTS_WANTED) {
				if (elapsed > 3L * SHIPPED_INTERVAL) {
					helper.fail("only " + spheres.size() + " sphere bursts came in " + elapsed + " ticks below half health");
				}
				return;
			}
			Burst first = spheres.get(0);
			Burst second = spheres.get(1);
			int upper = 0;
			int lower = 0;
			int[] quadrants = new int[4];
			for (WideArena.Shot shot : first.shots()) {
				Vec3 direction = shot.velocity().normalize();
				upper += direction.y > FLAT ? 1 : 0;
				lower += direction.y < -FLAT ? 1 : 0;
				if (Math.abs(direction.x) > OFF_AXIS && Math.abs(direction.z) > OFF_AXIS) {
					quadrants[(direction.x > 0 ? 1 : 0) + (direction.z > 0 ? 2 : 0)]++;
				}
			}
			int aimedBetween = (int) bursts(WideArena.shots(boss), config.get(WitherConfig.AIM_SHARE)).stream()
				.filter(burst -> burst.ownerTick() > first.ownerTick() && burst.ownerTick() < second.ownerTick()).count();
			double widest = farthest.values().stream().mapToDouble(Double::doubleValue).max().orElse(-1.0);
			StellaMobs.LOGGER.info("[BEHAVIOR] test=witherSphere RESULT sizes={}/{} gap={} upper={} lower={} quadrants={} aimedBetween={} peakLive={} widest={}",
				first.shots().size(), second.shots().size(), second.ownerTick() - first.ownerTick(), upper, lower, List.of(quadrants[0], quadrants[1], quadrants[2], quadrants[3]),
				aimedBetween, peakLive[0], widest);
			// Every sphere-share skull of the scene belongs to a full burst. A trickle of single skulls would be the old barrage.
			helper.assertTrue(spheres.stream().allMatch(burst -> burst.shots().size() == config.getInt(WitherConfig.SPHERE_COUNT)),
				"the bursts held " + spheres.stream().map(burst -> burst.shots().size()).toList() + " skulls, want " + config.getInt(WitherConfig.SPHERE_COUNT) + " each");
			helper.assertTrue(Math.abs(second.ownerTick() - first.ownerTick() - config.getInt(WitherConfig.SPHERE_INTERVAL)) <= INTERVAL_SLACK,
				"the bursts came " + (second.ownerTick() - first.ownerTick()) + " ticks apart, want " + config.getInt(WitherConfig.SPHERE_INTERVAL));
			helper.assertTrue(upper >= SHIPPED_COUNT / 4 && lower >= SHIPPED_COUNT / 4, "the burst is not a sphere: " + upper + " skulls rise and " + lower + " fall");
			for (int quadrant : quadrants) {
				helper.assertTrue(quadrant >= LEAST_PER_QUADRANT, "a horizontal quadrant holds " + quadrant + " skulls: " + List.of(quadrants[0], quadrants[1], quadrants[2], quadrants[3]));
			}
			helper.assertTrue(aimedBetween >= 3, "only " + aimedBetween + " aimed volleys came between two bursts");
			helper.assertTrue(peakLive[0] >= config.getInt(WitherConfig.SPHERE_COUNT) && peakLive[0] <= config.getInt(WitherConfig.SKULL_LIVE_CAP),
				"the live skulls peaked at " + peakLive[0] + ", cap " + config.getInt(WitherConfig.SKULL_LIVE_CAP));
			helper.assertTrue(widest <= config.get(WitherConfig.SPHERE_RANGE) + RAY_SLACK, "a sphere skull flew " + widest + " blocks from where it began");
			helper.succeed();
		});
		arena.whenReady(List.of(new Vec3(28, 3, 28), new Vec3(28, 3, 44)), () -> {
			player[0] = arena.survivalPlayer(28.5, 44.5);
			wither[0] = WitherRig.wither(helper, arena, 28, WideArena.SPAWN_Y, 28, 0);
			wither[0].setHealth((float) (wither[0].getMaxHealth() * ABOVE_HALF));
			WideArena.watch(wither[0], () -> player[0]);
			phaseStart[0] = helper.getTick();
		});
	}
}
