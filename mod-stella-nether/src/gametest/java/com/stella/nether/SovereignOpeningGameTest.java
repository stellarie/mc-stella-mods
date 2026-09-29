package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The Sovereign's opening: once, a rush through walls to sight, a three-second charge, fifteen strikes. */
public class SovereignOpeningGameTest {
	/** A wall across the arena between the Sovereign and a target beyond it, as high as the arena. */
	static void wall(final SovereignSpecialsGameTest.Scene scene, final int x) {
		BlockPos base = BlockPos.containing(scene.floor()).offset(x, 0, 0);
		for (int z = -7; z <= 7; z++) {
			for (int y = 0; y < 10; y++) {
				scene.raid().setBlock(base.offset(0, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	static Zombie target(final GameTestHelper helper, final SovereignSpecialsGameTest.Scene scene, final Vec3 at) {
		Zombie zombie = SovereignSpecialsGameTest.standIn(helper, scene, at);
		zombie.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 4));
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0));
		return zombie;
	}

	@GameTest(environment = "mod-stella-nether-test:open_rush", maxTicks = 1200)
	public void theOpeningRushesThroughAWallUntilItSeesItsTarget(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		wall(scene, 3);
		Zombie behind = target(helper, scene, scene.floor().add(6.0, 0.0, 0.0));
		SovereignOpening.Opening[] opening = new SovereignOpening.Opening[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, behind), "the arena's entities are not loaded"))
			.thenExecute(() -> opening[0] = SovereignOpening.start(scene.sovereign(), behind))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, behind);
				helper.assertTrue(opening[0].phase() != SovereignOpening.Phase.RUSH, "it is still rushing");
			})
			.thenExecute(() -> {
				SovereignOpening.Opening made = opening[0];
				double travelled = scene.sovereign().position().distanceTo(made.start());
				double speed = travelled / Math.max(1, made.rushTicks());
				boolean sees = scene.sovereign().hasLineOfSight(behind);
				NetherTestSupport.result("openingRush", "rushTicks=" + made.rushTicks() + " travelled=" + String.format("%.2f", travelled)
					+ " perTick=" + String.format("%.3f", speed) + " broken=" + made.broken().size() + " sees=" + sees + " phase=" + made.phase());
				SovereignSpecialsGameTest.end(helper, scene, behind);
				helper.assertTrue(!made.broken().isEmpty() && sees && made.rushTicks() > 0 && made.rushTicks() < SovereignOpening.RUSH_MAX_TICKS
						&& Math.abs(speed - SovereignOpening.RUSH_SPEED) < 0.01,
					"the rush broke " + made.broken().size() + " blocks at " + speed + " a tick over " + made.rushTicks() + " ticks and sees=" + sees);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:open_salvo", maxTicks = 1200)
	public void theOpeningChargesThreeSecondsThenStrikesFifteenTimesAroundItsTarget(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie seen = target(helper, scene, scene.floor().add(5.0, 0.0, 0.0));
		SovereignOpening.Opening[] opening = new SovereignOpening.Opening[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, seen), "the arena's entities are not loaded"))
			.thenExecute(() -> opening[0] = SovereignOpening.start(scene.sovereign(), seen))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, seen);
				helper.assertTrue(opening[0].phase() == SovereignOpening.Phase.DONE, "the opening is not over");
			})
			.thenExecute(() -> {
				SovereignOpening.Opening made = opening[0];
				if (made.strikes().isEmpty()) {
					SovereignSpecialsGameTest.end(helper, scene, seen);
					helper.fail("the opening struck nothing");
				}
				int first = made.strikeAges().get(0);
				int last = made.strikeAges().get(made.strikeAges().size() - 1);
				double farthest = made.strikes().stream().mapToDouble(at -> Math.hypot(at.x - seen.getX(), at.z - seen.getZ())).max().orElse(-1.0);
				int charge = made.salvoStartAge() - made.chargeStartAge();
				NetherTestSupport.result("openingSalvo", "strikes=" + made.strikes().size() + " charge=" + charge + " firstAt=" + first + " lastAt=" + last
					+ " span=" + (last - first) + " farthest=" + String.format("%.2f", farthest) + " hit=" + made.hits().contains(seen.getUUID()));
				SovereignSpecialsGameTest.end(helper, scene, seen);
				helper.assertTrue(made.strikes().size() == SovereignOpening.SALVO_STRIKES && charge == SovereignOpening.CHARGE_TICKS
						&& first == made.salvoStartAge() && last - first < SovereignOpening.SALVO_TICKS && last - first >= SovereignOpening.SALVO_TICKS * 4 / 5
						&& farthest <= SovereignOpening.SALVO_RADIUS + 1.0E-6,
					made.strikes().size() + " strikes after a charge of " + charge + ", from " + first + " to " + last + ", as far as " + farthest);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:open_once", maxTicks = 1200)
	public void aPlayerWithinRangeStartsTheOpeningAndOnlyOnce(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		// The scene holds the specials; the opening waits on the same hold, so it is lifted.
		SovereignSpecials.resume(scene.sovereign(), SovereignSpecials.SPECIAL_EVERY_TICKS * 100);
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + 8.0, scene.floor().y, scene.floor().z);
		helper.startSequence()
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(SovereignOpening.of(scene.sovereign()) != null, "no opening has started");
			})
			.thenExecute(() -> {
				SovereignOpening.Opening made = SovereignOpening.of(scene.sovereign());
				SovereignOpening.Opening again = SovereignOpening.start(scene.sovereign(), scene.player());
				boolean marked = Boolean.TRUE.equals(scene.sovereign().getAttached(SovereignOpening.OPENED));
				NetherTestSupport.result("openingOnce", "target=" + made.target().getScoreboardName() + " again=" + (again != null) + " marked=" + marked);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(made.target() == scene.player() && again == null && marked,
					"the opening's target=" + made.target().getScoreboardName() + ", a second began=" + (again != null) + ", marked=" + marked);
			})
			.thenSucceed();
	}
}
