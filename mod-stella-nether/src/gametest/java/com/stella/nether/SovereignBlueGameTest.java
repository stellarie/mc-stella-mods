package com.stella.nether;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/** The Sovereign burns blue: its mark reaches every client, and its fire flies as soul fire. */
public class SovereignBlueGameTest {
	@GameTest(environment = "mod-stella-nether-test:blue_mark", maxTicks = 20)
	public void theSovereignsMarkReachesEveryClient(final GameTestHelper helper) {
		boolean synced = BlazeSovereign.SOVEREIGN.isSynced();
		NetherTestSupport.result("sovereignMarkSynced", "synced=" + synced);
		helper.assertTrue(synced, "the Sovereign's mark stays on the server, so no client can draw it blue");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:blue_fire", maxTicks = 600)
	public void theSovereignsFireballsFlyAsSoulFire(final GameTestHelper helper) {
		// The pinned scene the specials use: its own hunting would carry an AI-driven Sovereign out of held chunks.
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		ServerPlayer player = scene.player();
		ServerLevel raid = scene.raid();
		Blaze sovereign = scene.sovereign();
		SovereignSpecials.Cast cast = SovereignSpecials.cast(sovereign, SovereignSpecials.Special.FLAME_SPIRAL, player);
		SmallFireball[] loose = new SmallFireball[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not visible yet"))
			.thenExecute(() -> {
				// A fireball the Sovereign looses any other way, as its own attack does, added where the level sees it.
				loose[0] = new SmallFireball(raid, sovereign, new Vec3(1.0, 0.0, 0.0));
				loose[0].setPos(sovereign.position().add(0.0, 3.0, 0.0));
				loose[0].accelerationPower = 0.0;
				loose[0].setDeltaMovement(Vec3.ZERO);
				raid.addFreshEntity(loose[0]);
			})
			.thenWaitUntil(() -> helper.assertTrue(cast.done(), "the spiral is still firing"))
			.thenExecute(() -> {
				long plain = cast.spawned().stream().filter(entity -> entity instanceof SmallFireball ball && !ball.getItem().is(NetherItems.SOUL_FIRE_CHARGE)).count();
				boolean looseBlue = loose[0].getItem().is(NetherItems.SOUL_FIRE_CHARGE);
				NetherTestSupport.result("sovereignFireBlue", "spiral=" + cast.spawned().size() + " plain=" + plain + " looseBlue=" + looseBlue);
				cast.spawned().forEach(Entity::discard);
				loose[0].discard();
				sovereign.discard();
				NetherTestSupport.leave(helper, player);
				helper.assertTrue(!cast.spawned().isEmpty() && plain == 0 && looseBlue, plain + " spiral fireballs flew plain; the loose one blue=" + looseBlue);
			})
			.thenSucceed();
	}

	/**
	 * Tagged, not required: the Sovereign drawn in soul-fire blue is the client's work, and a server game test
	 * cannot see a render. It fails every run, by design, until a person has looked.
	 */
	@GameTest(environment = "mod-stella-nether-test:blue_tint", maxTicks = 20, required = false)
	public void theSovereignIsDrawnBlueRequiresManualTest(final GameTestHelper helper) {
		helper.fail("the Sovereign's blue tint is drawn by the client; check it by hand");
	}
}
