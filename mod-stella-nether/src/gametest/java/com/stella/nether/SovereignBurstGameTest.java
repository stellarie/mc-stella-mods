package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** The Sovereign's normal attack: five fireballs in a burst, a burst every five seconds. */
public class SovereignBurstGameTest {
	@GameTest(environment = "mod-stella-nether-test:sov_burst", maxTicks = 800)
	public void theSovereignFiresABurstOfFiveEveryFiveSeconds(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = SovereignSpecialsGameTest.standIn(helper, scene, scene.floor().add(6.0, 0.0, 0.0));
		// It stands to be shot at twice over; a target that burns down ends the attack it is measuring.
		target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 4));
		target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				// Its attack is a goal, so the Sovereign thinks again; the specials stay paused.
				scene.sovereign().setNoAi(false);
				scene.sovereign().setTarget(target);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, target);
				scene.sovereign().setTarget(target);
				helper.assertTrue(SovereignBurst.shotsOf(scene.sovereign().getUUID()).size() >= 2 * SovereignBurst.BURST_SIZE, "two bursts have not been fired yet");
			})
			.thenExecute(() -> {
				List<Integer> shots = SovereignBurst.shotsOf(scene.sovereign().getUUID()).subList(0, 2 * SovereignBurst.BURST_SIZE);
				boolean inBursts = true;
				for (int burst = 0; burst < 2; burst++) {
					for (int i = 1; i < SovereignBurst.BURST_SIZE; i++) {
						int at = burst * SovereignBurst.BURST_SIZE + i;
						inBursts &= shots.get(at) - shots.get(at - 1) == SovereignBurst.SHOT_GAP_TICKS;
					}
				}
				int beat = shots.get(SovereignBurst.BURST_SIZE) - shots.get(0);
				NetherTestSupport.result("sovereignBurst", "shots=" + shots + " beat=" + beat + " inBursts=" + inBursts);
				SovereignSpecialsGameTest.end(helper, scene, target);
				helper.assertTrue(inBursts && beat == SovereignBurst.BURST_EVERY_TICKS, "shots at " + shots + ", bursts " + beat + " ticks apart");
			})
			.thenSucceed();
	}
}
