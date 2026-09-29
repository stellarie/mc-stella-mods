package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.Vec3;

/** The vortex: twelve seconds pulling players in while flames fly from its edge to its centre, then a shield. */
public class SovereignVortexGameTest {
	@GameTest(environment = "mod-stella-nether-test:vortex_pull", maxTicks = 1200)
	public void theVortexPullsPlayersInWhileFlamesFlyFromItsEdgeToItsCentre(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		// Within the square, and off every line a flame could be aimed down but by chance.
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + 10.0, scene.floor().y, scene.floor().z + 3.0);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player()))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].age() >= SovereignSpecials.VORTEX_TICKS, "the pull is not over");
			})
			.thenExecute(() -> {
				SovereignSpecials.Cast vortex = cast[0];
				Vec3 pull = vortex.pushes().get(scene.player().getUUID());
				Vec3 toCentre = vortex.home().subtract(scene.player().position());
				boolean inward = pull != null && pull.x * toCentre.x + pull.z * toCentre.z > 0.0;
				List<SovereignSpecials.Bullet> bullets = vortex.bullets();
				long blue = bullets.stream().filter(SovereignSpecials.Bullet::blue).count();
				boolean fromEdge = !bullets.isEmpty() && bullets.stream().allMatch(b -> Math.abs(b.spawnRadius() - SovereignSpecials.VORTEX_HALF) < 1.0E-6);
				long atCentre = bullets.stream().filter(b -> b.end() == SovereignSpecials.BulletEnd.CENTRE).count();
				long hit = bullets.stream().filter(b -> b.end() == SovereignSpecials.BulletEnd.HIT).count();
				// A flame still in flight has not yet had time to cross; any other ending is a flame lost on the way.
				long flying = bullets.stream().filter(b -> b.end() == null).count();
				int expected = (SovereignSpecials.VORTEX_TICKS / SovereignSpecials.VORTEX_BULLETS_EVERY) * SovereignSpecials.VORTEX_BULLETS_EACH;
				int crossing = (int) Math.ceil(SovereignSpecials.VORTEX_HALF / SovereignSpecials.VORTEX_BULLET_SPEED);
				long crossingCap = (long) Math.ceil((double) crossing / SovereignSpecials.VORTEX_BULLETS_EVERY + 1) * SovereignSpecials.VORTEX_BULLETS_EACH;
				NetherTestSupport.result("vortexPull", "pull=" + pull + " inward=" + inward + " bullets=" + bullets.size() + " expected=" + expected + " blue=" + blue
					+ " fromEdge=" + fromEdge + " centre=" + atCentre + " hit=" + hit + " flying=" + flying);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(inward && bullets.size() == expected && blue > 0 && blue < bullets.size() && fromEdge
						&& atCentre + hit + flying == bullets.size() && flying <= crossingCap
						&& SovereignSpecials.VORTEX_BULLET_SPEED > SovereignSpecials.VORTEX_PULL_MAX,
					"pull=" + pull + " inward=" + inward + ", " + bullets.size() + " of " + expected + " flames, " + blue + " blue, from the edge=" + fromEdge
						+ ", " + atCentre + " at the centre, " + hit + " hit, " + flying + " flying");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_hit", maxTicks = 1200)
	public void aBlueVortexFlameBurstsOnThePlayerItHits(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		// East of the centre, on the line a flame sent in from due east flies down.
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + 8.0, scene.floor().y, scene.floor().z);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		SovereignSpecials.Bullet[] sent = new SovereignSpecials.Bullet[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player());
				sent[0] = SovereignSpecials.vortexBullet(cast[0], 0.0, true);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(sent[0].end() != null, "the flame is still flying");
			})
			.thenExecute(() -> {
				SovereignSpecials.Bullet flame = sent[0];
				boolean pushed = cast[0].pushes().containsKey(scene.player().getUUID());
				boolean burst = cast[0].impacts().stream().anyMatch(at -> at.distanceTo(flame.position()) < 1.0E-6);
				NetherTestSupport.result("vortexHit", "end=" + flame.end() + " hitPlayer=" + scene.player().getUUID().equals(flame.hit()) + " burst=" + burst
					+ " at=" + String.format("%.2f", flame.position().x - scene.floor().x));
				cast[0].flames().forEach(at -> scene.raid().removeBlock(at, false));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(flame.end() == SovereignSpecials.BulletEnd.HIT && scene.player().getUUID().equals(flame.hit()) && burst && pushed,
					"the blue flame ended " + flame.end() + " on " + flame.hit() + ", burst=" + burst);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_shield", maxTicks = 1200)
	public void afterThePullAShieldHoldsTheSovereignStillAndCallsBlazes(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		Vec3[] held = new Vec3[1];
		double[] shield = new double[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				// It thinks again, so a shield that did not hold it would let it move and fire.
				scene.sovereign().setNoAi(false);
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player());
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].shield(), "the shield is not up");
			})
			.thenExecute(() -> {
				held[0] = scene.sovereign().position();
				Double divisor = scene.sovereign().getAttached(BlazeSovereign.DIVISOR);
				shield[0] = scene.sovereign().getAbsorptionAmount() * (divisor == null ? 1.0 : divisor);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].waves().size() >= 2, "a second wave has not come");
			})
			.thenExecute(() -> {
				SovereignSpecials.Cast vortex = cast[0];
				double moved = scene.sovereign().position().distanceTo(held[0]);
				int shots = SovereignBurst.shotsOf(scene.sovereign().getUUID()).size();
				List<Integer> sizes = vortex.waves().stream().map(List::size).toList();
				boolean inSquare = !vortex.summonedAt().isEmpty() && vortex.summonedAt().stream().allMatch(at -> SovereignSpecials.inSquare(at, vortex.home()));
				// Its shield broken: a blow worth more than the shield, in the Sovereign's whole health.
				scene.sovereign().damageCooldownTime = 0;
				scene.sovereign().hurtServer(scene.raid(), scene.raid().damageSources().generic(), (float) (SovereignSpecials.VORTEX_SHIELD * 1.5));
				SovereignSpecials.tickOnce(scene.raid(), scene.sovereign());
				boolean broken = !vortex.shield() && vortex.done() && scene.sovereign().getAbsorptionAmount() <= 0.0F;
				NetherTestSupport.result("vortexShield", "shield=" + String.format("%.1f", shield[0]) + " moved=" + String.format("%.3f", moved) + " shots=" + shots
					+ " waves=" + sizes + " inSquare=" + inSquare + " broken=" + broken);
				vortex.waves().forEach(wave -> wave.forEach(Blaze::discard));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(Math.abs(shield[0] - SovereignSpecials.VORTEX_SHIELD) < 0.5 && moved < 0.1 && shots == 0 && inSquare && broken
						&& sizes.stream().allMatch(size -> size >= SovereignSpecials.VORTEX_SUMMON_MIN && size <= SovereignSpecials.VORTEX_SUMMON_MAX),
					"shield " + shield[0] + ", moved " + moved + ", fired " + shots + ", waves " + sizes + " in the square=" + inSquare + ", broken=" + broken);
			})
			.thenSucceed();
	}
}
