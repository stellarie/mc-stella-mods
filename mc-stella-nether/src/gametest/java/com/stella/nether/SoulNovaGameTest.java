package com.stella.nether;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The soul nova: slow blue flames in every direction, bursting after five seconds, breaking nothing. */
public class SoulNovaGameTest {
	/** Room for a flame to drift its whole reach and burst in open air. */
	static final int HALF = (int) Math.ceil(SovereignSpecials.NOVA_REACH) + 3;
	static final int HEIGHT = 12;

	static SovereignSpecialsGameTest.Scene wideScene(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		BlockPos centre = RaidInstance.arrival(server).offset(16 + HALF, 0, 0);
		for (int x = -HALF; x <= HALF; x += 16) {
			for (int z = -HALF; z <= HALF; z += 16) {
				BlazeSovereignGameTest.hold(raid, centre.offset(x, 0, z));
			}
		}
		BlazeSovereignGameTest.hold(raid, centre.offset(HALF, 0, HALF));
		for (int x = -HALF; x <= HALF; x++) {
			for (int z = -HALF; z <= HALF; z++) {
				raid.setBlock(centre.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 0; y < HEIGHT; y++) {
					raid.setBlock(centre.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		Vec3 floor = Vec3.atBottomCenterOf(centre);
		Blaze sovereign = BlazeSovereign.rise(raid, floor);
		SovereignSpecials.pause(sovereign);
		sovereign.setNoAi(true);
		return new SovereignSpecialsGameTest.Scene(player, raid, sovereign, floor);
	}

	/** Where the first flame, heading east, bursts in open air: its full reach from the Sovereign's middle. */
	static Vec3 eastBurst(final SovereignSpecialsGameTest.Scene scene) {
		return new Vec3(scene.sovereign().getX() + SovereignSpecials.NOVA_REACH, scene.sovereign().getY() + scene.sovereign().getBbHeight() * 0.5,
			scene.sovereign().getZ());
	}

	@GameTest(environment = "mod-stella-nether-test:sp_nova", maxTicks = 1200)
	public void theSoulNovaDriftsFiveSecondsThenBurstsAndThrowsBack(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = wideScene(helper);
		Vec3 burst = eastBurst(scene);
		Zombie struck = SovereignSpecialsGameTest.standIn(helper, scene, new Vec3(burst.x + 1.0, scene.floor().y, burst.z));
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		float[] before = new float[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, struck), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				before[0] = struck.getHealth();
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.SOUL_NOVA, struck);
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the nova has not burst yet"))
			.thenExecute(() -> {
				SovereignSpecials.Cast nova = cast[0];
				Set<Integer> octants = new HashSet<>();
				for (Vec3 heading : nova.headings()) {
					octants.add((int) Math.floor((Math.atan2(heading.z, heading.x) + Math.PI) / (Math.PI / 4)) % 8);
				}
				double drift = nova.orbs().get(0).distanceTo(nova.home().add(0.0, scene.sovereign().getBbHeight() * 0.5, 0.0));
				int burstAge = nova.burstAt().getOrDefault(0, -1);
				int expectedAge = (int) Math.ceil(SovereignSpecials.NOVA_REACH / SovereignSpecials.NOVA_SPEED) - 1;
				Vec3 push = nova.pushes().get(struck.getUUID());
				boolean hurt = struck.getHealth() < before[0];
				NetherTestSupport.result("soulNova", "flames=" + nova.orbs().size() + " octants=" + octants.size() + " burstAt=" + burstAge + " expectedAt=" + expectedAge
					+ " drift=" + String.format("%.2f", drift) + " push=" + push + " hurt=" + hurt + " bursts=" + nova.impacts().size());
				SovereignSpecialsGameTest.end(helper, scene, struck);
				helper.assertTrue(nova.orbs().size() == SovereignSpecials.NOVA_RING + SovereignSpecials.NOVA_UPPER && octants.size() == 8
						&& burstAge == expectedAge && burstAge < SovereignSpecials.NOVA_LIFE_TICKS
						&& drift >= SovereignSpecials.NOVA_REACH && drift < SovereignSpecials.NOVA_REACH + SovereignSpecials.NOVA_SPEED + 1.0E-6
						&& push != null && push.x > 0.0 && hurt,
					"the nova: " + nova.orbs().size() + " flames over " + octants.size() + " octants, the first burst at " + burstAge + " of " + expectedAge + " after drifting "
						+ drift + "; the one standing there pushed " + push + " hurt " + hurt);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:sp_nova_wall", maxTicks = 1200)
	public void aSoulNovaFlameBurstsAtTheWallItMeets(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = wideScene(helper);
		// A wall across the east, WALL blocks out, where the first flame heads.
		BlockPos wallAt = BlockPos.containing(scene.floor()).offset(WALL, 0, 0);
		for (int z = -HALF; z <= HALF; z++) {
			for (int y = 0; y < HEIGHT; y++) {
				scene.raid().setBlock(wallAt.offset(0, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.SOUL_NOVA, scene.player()))
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the nova has not burst yet"))
			.thenExecute(() -> {
				SovereignSpecials.Cast nova = cast[0];
				int burstAge = nova.burstAt().getOrDefault(0, -1);
				double reached = nova.orbs().get(0).x - scene.sovereign().getX();
				int reachAge = (int) Math.ceil(SovereignSpecials.NOVA_REACH / SovereignSpecials.NOVA_SPEED) - 1;
				NetherTestSupport.result("soulNovaWall", "burstAt=" + burstAge + " reachAge=" + reachAge + " reachedEast=" + String.format("%.2f", reached) + " wall=" + WALL);
				nova.flames().forEach(at -> scene.raid().removeBlock(at, false));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(burstAge >= 0 && burstAge < reachAge && reached < WALL && reached > WALL - 1.0 - SovereignSpecials.NOVA_SPEED,
					"the east flame burst at " + burstAge + " (its reach at " + reachAge + ") " + reached + " east, the wall at " + WALL);
			})
			.thenSucceed();
	}

	static final int WALL = 6;

	@GameTest(environment = "mod-stella-nether-test:sp_nova_blocks", maxTicks = 1200)
	public void theSoulNovaSetsSoulFlamesAndBreaksNoBlock(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = wideScene(helper);
		BlockPos centre = BlockPos.containing(scene.floor());
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.SOUL_NOVA, scene.player()))
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the nova has not burst yet"))
			.thenExecute(() -> {
				SovereignSpecials.Cast nova = cast[0];
				long burning = nova.flames().stream().filter(at -> scene.raid().getBlockState(at).is(NetherBlocks.SOUL_FLAME)).count();
				int broken = 0;
				for (int x = -SoulNovaGameTest.HALF; x <= SoulNovaGameTest.HALF; x++) {
					for (int z = -SoulNovaGameTest.HALF; z <= SoulNovaGameTest.HALF; z++) {
						broken += scene.raid().getBlockState(centre.offset(x, -1, z)).is(Blocks.OBSIDIAN) ? 0 : 1;
					}
				}
				NetherTestSupport.result("soulNovaBlocks", "flames=" + nova.flames().size() + " burning=" + burning + " brokenFloor=" + broken);
				nova.flames().forEach(at -> scene.raid().removeBlock(at, false));
				SovereignSpecialsGameTest.end(helper, scene);
				helper.assertTrue(!nova.flames().isEmpty() && burning == nova.flames().size() && broken == 0,
					nova.flames().size() + " soul flames set, " + burning + " burning, " + broken + " floor blocks broken");
			})
			.thenSucceed();
	}
}
