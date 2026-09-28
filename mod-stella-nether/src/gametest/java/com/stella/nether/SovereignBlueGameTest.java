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

	@GameTest(environment = "mod-stella-nether-test:blue_fire", maxTicks = 200)
	public void theSovereignsFireballsFlyAsSoulFire(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 floor = BlazeSovereignGameTest.arena(raid, helper.getLevel().getServer(), 10);
		Blaze sovereign = BlazeSovereign.rise(raid, floor);
		SovereignSpecials.pause(sovereign);
		SovereignSpecials.Cast cast = SovereignSpecials.cast(sovereign, SovereignSpecials.Special.FLAME_SPIRAL, player);
		// A fireball the Sovereign looses any other way, as its own attack does.
		SmallFireball loose = new SmallFireball(raid, sovereign, new Vec3(1.0, 0.0, 0.0));
		loose.setPos(floor.add(0.0, 3.0, 2.0));
		raid.addFreshEntity(loose);
		helper.succeedWhen(() -> {
			helper.assertTrue(!cast.spawned().isEmpty(), "the spiral has fired nothing yet");
			long plain = cast.spawned().stream().filter(entity -> entity instanceof SmallFireball ball && !ball.getItem().is(NetherItems.SOUL_FIRE_CHARGE)).count();
			boolean looseBlue = loose.getItem().is(NetherItems.SOUL_FIRE_CHARGE);
			NetherTestSupport.result("sovereignFireBlue", "spiral=" + cast.spawned().size() + " plain=" + plain + " looseBlue=" + looseBlue);
			helper.assertTrue(plain == 0 && looseBlue, plain + " spiral fireballs flew plain; the loose one blue=" + looseBlue);
			cast.spawned().forEach(Entity::discard);
			loose.discard();
			sovereign.discard();
			NetherTestSupport.leave(helper, player);
		});
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
