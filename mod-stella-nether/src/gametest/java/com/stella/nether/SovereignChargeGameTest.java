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

/** The Sovereign's charge at a player keeping away: through walls at 8 blocks a second, then nine shots. */
public class SovereignChargeGameTest {
	/** A wall across the wide arena, between the Sovereign and a target to the east. */
	static void wall(final SovereignSpecialsGameTest.Scene scene, final int x) {
		BlockPos base = BlockPos.containing(scene.floor()).offset(x, 0, 0);
		for (int z = -SoulNovaGameTest.HALF; z <= SoulNovaGameTest.HALF; z++) {
			for (int y = 0; y < SoulNovaGameTest.HEIGHT; y++) {
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

	@GameTest(environment = "mod-stella-nether-test:charge_rush", maxTicks = 1200)
	public void theChargeBreaksThroughAWallAtEightBlocksASecond(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		wall(scene, 4);
		Zombie behind = target(helper, scene, scene.floor().add(16.0, 0.0, 0.0));
		SovereignCharge.Charge[] charge = new SovereignCharge.Charge[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, behind), "the arena's entities are not loaded"))
			.thenExecute(() -> charge[0] = SovereignCharge.start(scene.sovereign(), behind))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, behind);
				helper.assertTrue(charge[0].phase() != SovereignCharge.Phase.CHARGE, "it is still charging");
			})
			.thenExecute(() -> {
				SovereignCharge.Charge made = charge[0];
				double travelled = scene.sovereign().position().distanceTo(made.start());
				double speed = travelled / Math.max(1, made.chargeTicks());
				double left = scene.sovereign().distanceTo(behind);
				boolean sees = scene.sovereign().hasLineOfSight(behind);
				NetherTestSupport.result("chargeRush", "chargeTicks=" + made.chargeTicks() + " travelled=" + String.format("%.2f", travelled)
					+ " perTick=" + String.format("%.3f", speed) + " broken=" + made.broken().size() + " left=" + String.format("%.2f", left) + " sees=" + sees);
				SovereignSpecialsGameTest.end(helper, scene, behind);
				helper.assertTrue(!made.broken().isEmpty() && sees && left <= SovereignCharge.STOP_DISTANCE && made.chargeTicks() < SovereignCharge.MAX_CHARGE_TICKS
						&& Math.abs(speed - SovereignCharge.SPEED) < 0.01,
					"the charge broke " + made.broken().size() + " blocks at " + speed + " a tick over " + made.chargeTicks() + " ticks, ending " + left + " away, sees=" + sees);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:charge_volley", maxTicks = 1200)
	public void theChargeEndsInAVolleyOfNineOverThreeSeconds(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		Zombie seen = target(helper, scene, scene.floor().add(6.0, 0.0, 0.0));
		SovereignCharge.Charge[] charge = new SovereignCharge.Charge[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, seen), "the arena's entities are not loaded"))
			.thenExecute(() -> charge[0] = SovereignCharge.start(scene.sovereign(), seen))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, seen);
				helper.assertTrue(charge[0].phase() == SovereignCharge.Phase.DONE, "the volley is not over");
			})
			.thenExecute(() -> {
				SovereignCharge.Charge made = charge[0];
				int shots = made.shotAges().size();
				int first = shots == 0 ? -1 : made.shotAges().get(0);
				int last = shots == 0 ? -1 : made.shotAges().get(shots - 1);
				boolean blue = !made.shots().isEmpty() && made.shots().stream().allMatch(ball -> ball.getItem().is(NetherItems.SOUL_FIRE_CHARGE));
				NetherTestSupport.result("chargeVolley", "shots=" + shots + " fired=" + made.shots().size() + " volleyAt=" + made.volleyStartAge()
					+ " firstAt=" + first + " lastAt=" + last + " span=" + (last - first) + " blue=" + blue);
				made.shots().forEach(net.minecraft.world.entity.Entity::discard);
				SovereignSpecialsGameTest.end(helper, scene, seen);
				helper.assertTrue(shots == SovereignCharge.VOLLEY_SHOTS && made.shots().size() == shots && blue && first == made.volleyStartAge()
						&& last - first < SovereignCharge.VOLLEY_TICKS && last - first >= SovereignCharge.VOLLEY_TICKS * 4 / 5,
					shots + " shots from " + first + " to " + last + " after a volley began at " + made.volleyStartAge() + ", blue=" + blue);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:charge_repeat", maxTicks = 1200)
	public void aPlayerKeepingAwayIsChargedAgainAndAgain(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		// The scene holds the specials; the charge waits on the same hold, so it is lifted, and no special comes.
		SovereignSpecials.resume(scene.sovereign(), SovereignSpecials.SPECIAL_EVERY_TICKS * 100);
		int far = SoulNovaGameTest.HALF - 1;
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + far, scene.floor().y, scene.floor().z + far);
		int[] first = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(SovereignCharge.madeBy(scene.sovereign()) >= 1, "no charge has started");
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				SovereignCharge.Charge charge = SovereignCharge.of(scene.sovereign());
				helper.assertTrue(charge != null && charge.phase() == SovereignCharge.Phase.DONE, "the first charge is not over");
			})
			.thenExecute(() -> {
				first[0] = SovereignCharge.madeBy(scene.sovereign());
				// Away again, to the far corner, and the cooldown run out: it comes again.
				BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x - far, scene.floor().y, scene.floor().z - far);
				SovereignCharge.setCooldown(scene.sovereign(), 0);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(SovereignCharge.madeBy(scene.sovereign()) > first[0], "no second charge has started");
			})
			.thenExecute(() -> {
				SovereignCharge.Charge second = SovereignCharge.of(scene.sovereign());
				int made = SovereignCharge.madeBy(scene.sovereign());
				NetherTestSupport.result("chargeRepeat", "first=" + first[0] + " made=" + made + " target=" + second.target().getScoreboardName());
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(first[0] == 1 && made == 2 && second.target() == scene.player(), "charges made " + made + " after " + first[0] + ", at " + second.target().getScoreboardName());
			})
			.thenSucceed();
	}
}
