package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spark Shot and Ice Lance still hit, and each shows a body in flight with a trail behind it, then cleans up. */
public class SpellBoltGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.bolt");

	static {
		SpellVisualLog.install();
	}

	@GameTest(maxTicks = 100)
	public void sparkShotFliesAFireChargeTrailedByFlames(final GameTestHelper helper) {
		fliesABody(helper, "spark_shot", Items.FIRE_CHARGE, ParticleTypes.FLAME, "sparkShotFliesAFireChargeTrailedByFlames");
	}

	@GameTest(maxTicks = 100)
	public void iceLanceFliesAnIceBodyTrailedBySnow(final GameTestHelper helper) {
		fliesABody(helper, "ice_lance", Items.PACKED_ICE, ParticleTypes.SNOWFLAKE, "iceLanceFliesAnIceBodyTrailedBySnow");
	}

	private static void fliesABody(
		final GameTestHelper helper, final String spell, final Item body, final ParticleOptions trail, final String test
	) {
		helper.setTime(18_000L);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		Mob target = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 7).withNoFreeWill().spawn();
		float before = target.getHealth();
		AABB reach = player.getBoundingBox().inflate(48.0);
		// A neighbour test may have a body in the air, so only the bodies this cast adds count.
		List<Display.ItemDisplay> already = bolts(helper, reach);
		Identifier id = Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell);
		Item book = BuiltInRegistries.ITEM.getValue(id);
		player.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(target.getHealth() < before, spell + " no longer hit the target");
		List<Display.ItemDisplay> bodies = bolts(helper, reach).stream().filter(display -> !already.contains(display)).toList();
		helper.assertTrue(bodies.size() == 1 && bodies.get(0).getItemStack().is(body),
			spell + " showed " + bodies.size() + " bolt bodies, not one " + body);
		int castTrail = trailPoints(player, id, trail);
		helper.runAfterDelay(SpellBolt.MAX_FLIGHT_TICKS + 4L, () -> {
			int flown = trailPoints(player, id, trail);
			LOG.info("[BEHAVIOR] test={} RESULT castTrail={} flownTrail={} removed={}",
				test, castTrail, flown, bodies.get(0).isRemoved());
			helper.assertTrue(flown > castTrail, "no trail particle came after the cast: " + castTrail + " then " + flown);
			helper.assertTrue(bodies.get(0).isRemoved(), "the body stayed in the world after its flight");
			helper.succeed();
		});
	}

	private static List<Display.ItemDisplay> bolts(final GameTestHelper helper, final AABB reach) {
		return helper.getLevel().getEntitiesOfClass(Display.ItemDisplay.class, reach,
			display -> display.entityTags().contains(SpellBolt.TAG));
	}

	private static int trailPoints(final ServerPlayer player, final Identifier spell, final ParticleOptions trail) {
		return (int) SpellVisualLog.played(player.getUUID(), spell).stream()
			.filter(played -> played.particle().getType() == trail.getType()).count();
	}
}
