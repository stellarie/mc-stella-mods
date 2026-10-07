package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The vortex: thirty seconds channelling behind a shield, slowing players and drawing flames in from a sphere all
 * around; broken in time it staggers the Sovereign, held to the end it bursts.
 */
public class SovereignVortexGameTest {
	/** The approved specification of the absorption phase, pinned here so a retune cannot pass unnoticed. */
	static final double SHIELD_SHARE_SPEC = 0.20;
	static final int CHANNEL_SECONDS_SPEC = 30;

	/** The shield covers a fifth of the Sovereign's whole health, and the channel runs thirty seconds. */
	@GameTest
	public void theShieldCoversAFifthOfTheHealthForThirtySeconds(final GameTestHelper helper) {
		NetherTestSupport.result("vortexSpec", "share=" + SovereignSpecials.VORTEX_SHIELD_SHARE + " ticks=" + SovereignSpecials.VORTEX_TICKS
			+ " wantShare=" + SHIELD_SHARE_SPEC + " wantTicks=" + CHANNEL_SECONDS_SPEC * 20);
		helper.assertTrue(Math.abs(SovereignSpecials.VORTEX_SHIELD_SHARE - SHIELD_SHARE_SPEC) < 1.0E-9,
			"the vortex shield covers " + SovereignSpecials.VORTEX_SHIELD_SHARE + " of the whole health, not " + SHIELD_SHARE_SPEC);
		helper.assertTrue(SovereignSpecials.VORTEX_TICKS == CHANNEL_SECONDS_SPEC * 20,
			"the vortex channel runs " + SovereignSpecials.VORTEX_TICKS + " ticks, not " + CHANNEL_SECONDS_SPEC * 20);
		helper.succeed();
	}

	/** The shield left, in the Sovereign's whole health. */
	static double shieldLeft(final SovereignSpecialsGameTest.Scene scene) {
		Double divisor = scene.sovereign().getAttached(BlazeSovereign.DIVISOR);
		return scene.sovereign().getAbsorptionAmount() / BlazeSovereign.healthScale() * (divisor == null ? 1.0 : divisor);
	}

	/** The shield's size in the Sovereign's whole health: its share of the health it has at full. */
	static double wholeShield(final SovereignSpecialsGameTest.Scene scene) {
		Double divisor = scene.sovereign().getAttached(BlazeSovereign.DIVISOR);
		return SovereignSpecials.VORTEX_SHIELD_SHARE * scene.sovereign().getMaxHealth() * (divisor == null ? 1.0 : divisor);
	}

	static final String SHARE = "mod-stella-rpg:health-scale";

	/** Raises the vortex shield with the rpg share set to k (absent when null) and checks its points and its whole size. */
	static void shieldUnderShare(final GameTestHelper helper, final Double k) {
		net.fabricmc.loader.api.ObjectShare share = net.fabricmc.loader.api.FabricLoader.getInstance().getObjectShare();
		Object previous = share.get(SHARE);
		if (k == null) {
			share.remove(SHARE);
		} else {
			share.put(SHARE, k);
		}
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player()))
			.thenWaitUntil(() -> helper.assertTrue(cast[0].age() >= 1, "the channel has not begun"))
			.thenExecute(() -> {
				try {
					double scale = k == null ? 1.0 : k;
					Double divisor = scene.sovereign().getAttached(BlazeSovereign.DIVISOR);
					double effectiveMax = scene.sovereign().getMaxHealth() * (divisor == null ? 1.0 : divisor);
					double points = scene.sovereign().getAbsorptionAmount();
					double expectedPoints = SovereignSpecials.VORTEX_SHIELD_SHARE * effectiveMax / (divisor == null ? 1.0 : divisor) * scale;
					double whole = points / scale * (divisor == null ? 1.0 : divisor);
					NetherTestSupport.result("vortexShieldUnits", "share=" + k + " points=" + points + " expectedPoints=" + expectedPoints
						+ " whole=" + whole + " effectiveMax=" + effectiveMax);
					SovereignSpecialsGameTest.end(helper, scene);
					helper.assertTrue(Math.abs(points - expectedPoints) < 0.05 && Math.abs(whole - SovereignSpecials.VORTEX_SHIELD_SHARE * effectiveMax) < 0.5,
						"shield of " + points + " points (expected " + expectedPoints + ") and " + whole + " whole (share " + k + ")");
				} finally {
					if (previous == null) {
						share.remove(SHARE);
					} else {
						share.put(SHARE, previous);
					}
				}
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_units_k", maxTicks = 600)
	public void theShieldIsInTheUnitsOfActuallyHurtWhenTheRpgShareIsSet(final GameTestHelper helper) {
		shieldUnderShare(helper, 15.0);
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_units_plain", maxTicks = 600)
	public void theShieldIsInPlainPointsWithoutTheRpgShare(final GameTestHelper helper) {
		shieldUnderShare(helper, null);
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_pull", maxTicks = 1200)
	public void theVortexShieldsTheSovereignSlowsPlayersAndDrawsFlamesFromEveryDirection(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + 10.0, scene.floor().y, scene.floor().z + 3.0);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		double[] shield = new double[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player()))
			.thenWaitUntil(() -> helper.assertTrue(cast[0].age() >= 1, "the channel has not begun"))
			.thenExecute(() -> shield[0] = shieldLeft(scene))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].age() >= SovereignSpecials.VORTEX_TICKS / 2, "half the channel has not passed");
			})
			.thenExecute(() -> {
				SovereignSpecials.Cast vortex = cast[0];
				MobEffectInstance slow = scene.player().getEffect(MobEffects.SLOWNESS);
				boolean slowed = slow != null && slow.getAmplifier() == SovereignSpecials.VORTEX_SLOW_AMPLIFIER;
				List<SovereignSpecials.Bullet> bullets = vortex.bullets();
				double half = SovereignSpecials.VORTEX_RADIUS / 2.0;
				boolean onSphere = !bullets.isEmpty() && bullets.stream().allMatch(b -> Math.abs(b.spawnRadius() - SovereignSpecials.VORTEX_RADIUS) < 1.0E-6);
				long above = bullets.stream().filter(b -> b.from().y - vortex.vortexCentre().y > half).count();
				long below = bullets.stream().filter(b -> b.from().y - vortex.vortexCentre().y < -half).count();
				long blue = bullets.stream().filter(SovereignSpecials.Bullet::blue).count();
				double moved = scene.sovereign().position().distanceTo(vortex.shieldAt());
				NetherTestSupport.result("vortexChannel", "shield=" + String.format("%.1f", shield[0]) + " shielded=" + SovereignSpecials.shielded(scene.sovereign())
					+ " slowed=" + slowed + " bullets=" + bullets.size() + " onSphere=" + onSphere + " above=" + above + " below=" + below + " blue=" + blue
					+ " moved=" + String.format("%.3f", moved));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(Math.abs(shield[0] - wholeShield(scene)) < 0.5 && slowed && onSphere && above > 0 && below > 0
						&& blue > 0 && blue < bullets.size() && moved < 0.1,
					"shield " + shield[0] + ", slowed=" + slowed + ", " + bullets.size() + " flames on the sphere=" + onSphere + ", " + above + " from above, "
						+ below + " from below, " + blue + " blue, moved " + moved);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_hit", maxTicks = 1200)
	public void aBlueVortexFlameBurstsOnThePlayerItHits(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + 8.0, scene.floor().y, scene.floor().z);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		SovereignSpecials.Bullet[] sent = new SovereignSpecials.Bullet[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player()))
			.thenWaitUntil(() -> helper.assertTrue(cast[0].age() >= 1, "the channel has not begun"))
			.thenExecute(() -> {
				// Sent in along the line through the player's body, so it passes them on its way to the centre.
				Vec3 through = scene.player().getBoundingBox().getCenter().subtract(cast[0].vortexCentre());
				sent[0] = SovereignSpecials.vortexBullet(cast[0], through, true);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(sent[0].end() != null, "the flame is still flying");
			})
			.thenExecute(() -> {
				SovereignSpecials.Bullet flame = sent[0];
				boolean burst = cast[0].impacts().stream().anyMatch(at -> at.distanceTo(flame.position()) < 1.0E-6);
				NetherTestSupport.result("vortexHit", "end=" + flame.end() + " hitPlayer=" + scene.player().getUUID().equals(flame.hit()) + " burst=" + burst);
				cast[0].flames().forEach(at -> scene.raid().removeBlock(at, false));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(flame.end() == SovereignSpecials.BulletEnd.HIT && scene.player().getUUID().equals(flame.hit()) && burst,
					"the blue flame ended " + flame.end() + " on " + flame.hit() + ", burst=" + burst);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_shield", maxTicks = 1200)
	public void breakingTheShieldInTimeStaggersTheSovereign(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		Vec3[] held = new Vec3[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				// It thinks again, so a stagger that did not hold it would let it move and fire.
				scene.sovereign().setNoAi(false);
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player());
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].age() >= 20, "the channel has not run a second"))
			.thenExecute(() -> {
				// A blow worth more than the shield, in the Sovereign's whole health.
				scene.sovereign().damageCooldownTime = 0;
				scene.sovereign().hurtServer(scene.raid(), scene.raid().damageSources().generic(), (float) (wholeShield(scene) * 1.5));
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].staggeredAt() >= 0, "the Sovereign has not staggered"))
			.thenExecute(() -> held[0] = scene.sovereign().position())
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].done(), "the stagger is not over");
			})
			.thenExecute(() -> {
				SovereignSpecials.Cast vortex = cast[0];
				double moved = scene.sovereign().position().distanceTo(held[0]);
				int shots = SovereignBurst.shotsOf(scene.sovereign().getUUID()).size();
				int lasted = vortex.lastActionAge() - vortex.staggeredAt();
				NetherTestSupport.result("vortexStagger", "staggeredAt=" + vortex.staggeredAt() + " lasted=" + lasted + " moved=" + String.format("%.3f", moved)
					+ " shots=" + shots + " blasted=" + vortex.blasted() + " shieldLeft=" + String.format("%.1f", shieldLeft(scene)));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(lasted == SovereignSpecials.VORTEX_STAGGER_TICKS && vortex.staggeredAt() < SovereignSpecials.VORTEX_TICKS
						&& moved < 0.1 && shots == 0 && !vortex.blasted(),
					"staggered at " + vortex.staggeredAt() + " for " + lasted + ", moved " + moved + ", fired " + shots + ", burst=" + vortex.blasted());
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:vortex_blast", maxTicks = 1200)
	public void anUnbrokenShieldBurstsInASixteenBlockSphere(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		Zombie caught = SovereignSpecialsGameTest.standIn(helper, scene, scene.floor().add(8.0, 0.0, 0.0));
		// The channel is twice as long now, so the stand-in needs a pool the drawn flames cannot empty.
		caught.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		caught.setHealth(1000.0F);
		// A block well inside the sphere, and one just past it.
		BlockPos inside = BlockPos.containing(scene.floor()).offset(0, 2, -10);
		BlockPos outside = BlockPos.containing(scene.floor()).offset(0, 2, -(SoulNovaGameTest.HALF - 1));
		scene.raid().setBlock(inside, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		scene.raid().setBlock(outside, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, caught), "the arena's entities are not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player()))
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, caught);
				helper.assertTrue(cast[0].done(), "the channel is not over");
			})
			.thenExecute(() -> {
				SovereignSpecials.Cast vortex = cast[0];
				double outsideDistance = Vec3.atCenterOf(outside).distanceTo(vortex.vortexCentre());
				boolean insideBroken = scene.raid().getBlockState(inside).isAir();
				boolean outsideStands = scene.raid().getBlockState(outside).is(Blocks.STONE);
				Vec3 push = vortex.pushes().get(caught.getUUID());
				boolean hit = vortex.hits().contains(caught.getUUID());
				NetherTestSupport.result("vortexBlast", "blasted=" + vortex.blasted() + " at=" + vortex.lastActionAge() + " broken=" + vortex.blastBroken()
					+ " insideBroken=" + insideBroken + " outsideStands=" + outsideStands + " outsideAt=" + String.format("%.1f", outsideDistance)
					+ " hit=" + hit + " push=" + push);
				SovereignSpecialsGameTest.end(helper, scene, caught);
				helper.assertTrue(vortex.blasted() && vortex.lastActionAge() == SovereignSpecials.VORTEX_TICKS && insideBroken
						&& outsideStands && outsideDistance > SovereignSpecials.VORTEX_RADIUS && hit && push != null && push.x > 0.0,
					"burst=" + vortex.blasted() + " at " + vortex.lastActionAge() + ", inside broken=" + insideBroken + ", outside stands=" + outsideStands
						+ " (" + outsideDistance + " out), hit=" + hit + " push=" + push);
			})
			.thenSucceed();
	}
}
