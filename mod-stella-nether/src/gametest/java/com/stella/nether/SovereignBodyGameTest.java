package com.stella.nether;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The Sovereign bursts out when shut in, its dive breaks no block of its own, and its spiral flies 32 blocks. */
public class SovereignBodyGameTest {
	/** Every block in a box set to stone. */
	static List<BlockPos> fill(final SovereignSpecialsGameTest.Scene scene, final AABB box) {
		List<BlockPos> placed = new ArrayList<>();
		BlockPos.betweenClosedStream(box).map(BlockPos::immutable).forEach(at -> {
			scene.raid().setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			placed.add(at);
		});
		return placed;
	}

	/** The dive breaks nothing of its own: a roof in its way opens only because the Sovereign shut in it bursts out. */
	@GameTest(environment = "mod-stella-nether-test:body_dive", maxTicks = 1200)
	public void aDiveIntoARoofIsOpenedByTheObstructionBurstAlone(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 16);
		// A stone ceiling four blocks over its head, well within the dive's rise.
		double top = scene.sovereign().getBoundingBox().maxY;
		BlockPos over = BlockPos.containing(scene.sovereign().getX(), top + 2.0, scene.sovereign().getZ());
		fill(scene, new AABB(net.minecraft.world.phys.Vec3.atCenterOf(over.offset(-3, 0, -3)), net.minecraft.world.phys.Vec3.atCenterOf(over.offset(3, 0, 3))));
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		int[] bursts = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded");
				// Only a ticking creature suffocates, and the dive crosses a one-block roof in a few ticks.
				helper.assertTrue(scene.raid().isPositionEntityTicking(scene.sovereign().blockPosition()), "the Sovereign is not ticking yet");
			})
			.thenExecute(() -> {
				bursts[0] = SovereignBody.burstsOf(scene.sovereign());
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.DIVE, scene.player());
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].age() >= SovereignSpecials.DIVE_RISE_TICKS, "the dive has not risen yet");
			})
			.thenExecute(() -> {
				boolean opened = scene.raid().getBlockState(over).isAir();
				boolean inside = !scene.raid().getBlockCollisions(scene.sovereign(), scene.sovereign().getBoundingBox()).iterator().hasNext();
				int burst = SovereignBody.burstsOf(scene.sovereign()) - bursts[0];
				NetherTestSupport.result("bodyDive", "bursts=" + burst + " ceilingOpened=" + opened + " clear=" + inside);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(burst > 0 && opened && inside, "the dive opened the roof with " + burst + " bursts, ceiling open=" + opened + ", body clear=" + inside);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:body_burst", maxTicks = 600)
	public void aSovereignShutInBurstsOut(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		int[] bursts = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				bursts[0] = SovereignBody.burstsOf(scene.sovereign());
				// Shut in: stone over its whole body.
				fill(scene, scene.sovereign().getBoundingBox().deflate(0.05));
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(SovereignBody.burstsOf(scene.sovereign()) > bursts[0], "it has not burst out");
			})
			.thenExecute(() -> {
				boolean clear = !scene.raid().getBlockCollisions(scene.sovereign(), scene.sovereign().getBoundingBox()).iterator().hasNext();
				boolean alive = scene.sovereign().isAlive();
				NetherTestSupport.result("bodyBurst", "bursts=" + SovereignBody.burstsOf(scene.sovereign()) + " clear=" + clear + " alive=" + alive);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(clear && alive, "after bursting out its body is clear=" + clear + ", alive=" + alive);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:body_reach", maxTicks = 1200)
	public void theSpiralsFireballsFlyThirtyTwoBlocks(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		// Three tunnels, east, south and north, at the Sovereign's middle and past the spiral's reach: the first
		// volley's fireballs fly down them. Three, because a wandering raid mob can stop one ball short (seen once in
		// five gate runs: the east ball gone at tick 27, well short of its reach). None runs west: that way lies the
		// arrival hall and its portal, which holds a fireball that flies in for good.
		int length = (int) SovereignSpecials.SPIRAL_REACH + 8;
		BlockPos mouth = BlockPos.containing(scene.sovereign().getX(), scene.sovereign().getY() + scene.sovereign().getBbHeight() * 0.5, scene.sovereign().getZ());
		int[][] ways = {{1, 0}, {0, 1}, {0, -1}};
		Runnable hold = () -> {
			for (int[] way : ways) {
				// Every chunk the tunnel crosses: a hold ticks entities only in the chunk it is centred on.
				for (int step = 0; step <= length; step += 4) {
					BlazeSovereignGameTest.hold(scene.raid(), mouth.offset(way[0] * step, 0, way[1] * step));
				}
			}
		};
		hold.run();
		for (int[] way : ways) {
			for (int step = 0; step <= length; step++) {
				for (int a = -1; a <= 1; a++) {
					for (int y = -1; y <= 1; y++) {
						scene.raid().setBlock(mouth.offset(way[0] * step + way[1] * a, y, way[1] * step + way[0] * a), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.FLAME_SPIRAL, scene.player()))
			.thenWaitUntil(() -> {
				hold.run();
				// The first volley's three tunnel balls, headings 0, 2 and 6 of eight.
				boolean gone = cast[0].spawned().size() >= SovereignSpecials.SPIRAL_PER_VOLLEY
					&& java.util.stream.IntStream.of(0, 2, 6).allMatch(i -> cast[0].spawned().get(i).isRemoved());
				helper.assertTrue(gone, "the tunnel fireballs are still flying: " + (cast[0].spawned().size() < SovereignSpecials.SPIRAL_PER_VOLLEY ? "none yet"
					: java.util.stream.IntStream.of(0, 2, 6).mapToObj(i -> {
						net.minecraft.world.entity.Entity ball = cast[0].spawned().get(i);
						return i + ":removed=" + ball.isRemoved() + ",at=" + ball.blockPosition().toShortString()
							+ ",ticking=" + scene.raid().isPositionEntityTicking(ball.blockPosition()) + ",speed=" + String.format("%.3f", ball.getDeltaMovement().length());
					}).toList()));
			})
			.thenExecute(() -> {
				java.util.Collection<Double> spent = cast[0].spent().values();
				boolean atReach = spent.stream().allMatch(d -> d >= SovereignSpecials.SPIRAL_REACH && d < SovereignSpecials.SPIRAL_REACH + 4.0);
				NetherTestSupport.result("spiralReach", "spent=" + spent.stream().map(d -> String.format("%.2f", d)).toList() + " reach=" + SovereignSpecials.SPIRAL_REACH);
				cast[0].spawned().forEach(Entity::discard);
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(!spent.isEmpty() && atReach, "spiral fireballs spent at " + spent + " against a reach of " + SovereignSpecials.SPIRAL_REACH);
			})
			.thenSucceed();
	}
}
