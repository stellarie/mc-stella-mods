package com.stella.nether;

import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The Blaze Sovereign's body: where and how often it rises, its health, size, footing, fire and bar. */
public class BlazeSovereignGameTest {
	/** A cleared room beside the arrival hall, floored in obsidian, for a Sovereign to stand in. */
	static Vec3 arena(final ServerLevel raid, final MinecraftServer server, final int height) {
		return arena(raid, server, RaidMode.NORMAL, height);
	}

	/** The same room beside a mode's arrival hall, so a Sovereign risen in it belongs to that mode's raid. */
	static Vec3 arena(final ServerLevel raid, final MinecraftServer server, final RaidMode mode, final int height) {
		BlockPos centre = RaidInstance.arrival(server, mode).offset(16, 0, 0);
		hold(raid, centre);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				raid.setBlock(centre.offset(x, -1, z), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int y = 0; y < height; y++) {
					raid.setBlock(centre.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return Vec3.atBottomCenterOf(centre);
	}

	/**
	 * Loads the chunks around a spot at once and holds them with a portal ticket (300 ticks). A test player
	 * holds no chunks of its own, and a chunk that unloads under a test saves its entities and loads them
	 * again as new objects, leaving the test holding a stale one.
	 */
	static void hold(final ServerLevel raid, final BlockPos at) {
		net.minecraft.world.level.ChunkPos chunk = net.minecraft.world.level.ChunkPos.containing(at);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				raid.getChunk(chunk.x() + dx, chunk.z() + dz);
			}
		}
		raid.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL, chunk, 2);
		// A server catching up after a stall runs ticks back to back and skips its queued chunk work, which is
		// what makes loaded entities visible; the queue is drained here so a wait never starves on it.
		for (int i = 0; i < 1000 && raid.getChunkSource().pollTask(); i++) {
		}
	}

	/** Moves a test player within the raid; a real player's tick moves its chunk tickets, a test player's does not. */
	static void moveTo(final ServerPlayer player, final double x, final double y, final double z) {
		ServerLevel raid = (ServerLevel) player.level();
		player.teleportTo(raid, x, y, z, java.util.Set.of(), 0.0F, 0.0F, true);
		raid.getChunkSource().move(player);
	}

	static ServerPlayer enter(final GameTestHelper helper) {
		NetherTestSupport.fresh(helper);
		return RaidModeGameTest.enter(helper, RaidMode.NORMAL);
	}

	@GameTest(environment = "mod-stella-nether-test:sov_rise", maxTicks = 600)
	public void theSovereignRisesAtTheFortressWhenAPlayerComesNear(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		BlockPos fortress = RaidInstance.site(server, RaidMode.NORMAL).fortress();
		moveTo(player, fortress.getX() + 0.5, 80.0, fortress.getZ() + 0.5);
		helper.succeedWhen(() -> {
			Blaze blaze = BlazeSovereign.find(server, RaidMode.NORMAL);
			helper.assertTrue(blaze != null, "no Sovereign has risen");
			double dx = blaze.getX() - fortress.getX();
			double dz = blaze.getZ() - fortress.getZ();
			double away = Math.sqrt(dx * dx + dz * dz);
			NetherTestSupport.result("sovereignRises", "at=" + blaze.blockPosition() + " fromFortress=" + (int) away);
			helper.assertTrue(away <= BlazeSovereign.GROUND_SEARCH * Math.sqrt(2) + 1, "the Sovereign rose " + away + " from the fortress");
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:sov_health", maxTicks = 100)
	public void theSovereignsHealthFollowsTheRaidsThreatLevel(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze blaze = BlazeSovereign.rise(raid, arena(raid, server, 8));
		double effective = BlazeSovereign.effectiveHealth(blaze);
		int threat = ThreatState.level(blaze);
		int expectedThreat = RaidInstance.MIN_LEVEL_NORMAL + BlazeSovereign.LEVEL_BONUS;
		double expected = BlazeSovereign.BASE_HEALTH * SpawnScaling.healthMultiplier(expectedThreat);
		NetherTestSupport.result("sovereignHealth", "effective=" + effective + " expected=" + expected + " threat=" + threat + " attribute=" + blaze.getMaxHealth());
		blaze.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(effective - expected) < 1.0 && threat == expectedThreat,
			"the Sovereign holds " + effective + " of " + expected + " at threat " + threat);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sov_divisor", maxTicks = 100)
	public void aHitOnTheSovereignIsTakenThroughItsDivisor(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze blaze = BlazeSovereign.rise(raid, arena(raid, server, 8));
		double before = BlazeSovereign.effectiveHealth(blaze);
		float hit = 100.0F;
		blaze.hurtServer(raid, raid.damageSources().generic(), hit);
		double after = BlazeSovereign.effectiveHealth(blaze);
		double attributeDrop = blaze.getMaxHealth() - blaze.getHealth();
		NetherTestSupport.result("sovereignDivisor", "effectiveDrop=" + (before - after) + " attributeDrop=" + attributeDrop);
		blaze.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(before - after - hit) < 0.5 && attributeDrop < hit - 1.0,
			"a hit of " + hit + " took " + (before - after) + " effective and " + attributeDrop + " from the attribute");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sov_size", maxTicks = 100)
	public void theSovereignStandsThreeTimesABlazesHeight(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze blaze = BlazeSovereign.rise(raid, arena(raid, server, 8));
		double expected = EntityTypes.BLAZE.getHeight() * BlazeSovereign.SCALE;
		double height = blaze.getBbHeight();
		NetherTestSupport.result("sovereignSize", "height=" + height + " expected=" + expected);
		blaze.discard();
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(Math.abs(height - expected) < 0.01, "the Sovereign stands " + height + " of " + expected);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sov_ground", maxTicks = 200)
	public void theSovereignComesBackDownToTheGround(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Vec3 floor = arena(raid, server, 20);
		double start = 10.0;
		Blaze blaze = BlazeSovereign.rise(raid, floor.add(0.0, start, 0.0));
		// Held from its charges: the test player waits walled in the hall, and a charge at them skips the pull to
		// the ground while it lasts (seen once in four gate runs, hanging 7.2 blocks up).
		SovereignSpecials.pause(blaze);
		// A blaze's own slow fall would take some 88 ticks to come down ten blocks; the Sovereign is back within the window.
		int window = (int) Math.ceil((start - BlazeSovereign.NEAR_GROUND) / BlazeSovereign.DESCENT_PER_TICK) + 20;
		helper.runAfterDelay(window, () -> {
			double height = BlazeSovereign.heightAboveGround(raid, blaze);
			boolean alive = blaze.isAlive();
			NetherTestSupport.result("sovereignGround", "start=" + start + " height=" + height + " window=" + window + " alive=" + alive);
			blaze.discard();
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(alive && height <= BlazeSovereign.NEAR_GROUND + 0.5, "the Sovereign hangs " + height + " above the ground after " + window + " ticks");
			helper.succeed();
		});
	}

	@GameTest(environment = "mod-stella-nether-test:sov_burn", maxTicks = 100)
	public void theSovereignsHitsSetTargetsAlightAndAPlainBlazesDoNot(final GameTestHelper helper) {
		ServerPlayer struck = enter(helper);
		ServerPlayer control = NetherTestSupport.join(helper, 2.5, 1.5);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) struck.level();
		control.teleportTo(raid, struck.getX() + 1.0, struck.getY(), struck.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
		NetherTestSupport.vulnerable(struck);
		NetherTestSupport.vulnerable(control);
		Vec3 arena = arena(raid, server, 8);
		Blaze sovereign = BlazeSovereign.rise(raid, arena);
		Blaze plain = EntityTypes.BLAZE.create(raid, EntitySpawnReason.MOB_SUMMONED);
		plain.snapTo(arena.x + 3.0, arena.y, arena.z, 0.0F, 0.0F);
		raid.addFreshEntity(plain);
		struck.clearFire();
		control.clearFire();
		struck.hurtServer(raid, raid.damageSources().mobAttack(sovereign), 1.0F);
		control.hurtServer(raid, raid.damageSources().mobAttack(plain), 1.0F);
		int struckFire = struck.getRemainingFireTicks();
		int controlFire = control.getRemainingFireTicks();
		NetherTestSupport.result("sovereignBurns", "struck=" + struckFire + " control=" + controlFire);
		sovereign.discard();
		plain.discard();
		NetherTestSupport.leave(helper, struck);
		NetherTestSupport.leave(helper, control);
		helper.assertTrue(struckFire > 0 && controlFire <= 0, "fire ticks from the Sovereign " + struckFire + ", from a plain blaze " + controlFire);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:sov_bar", maxTicks = 400)
	public void playersNearTheSovereignSeeItsBar(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze blaze = BlazeSovereign.rise(raid, arena(raid, server, 8));
		// Held from its charges, whose rush and volley would fall on the player the bar is shown to.
		SovereignSpecials.pause(blaze);
		helper.succeedWhen(() -> {
			boolean shown = BlazeSovereign.bar(RaidMode.NORMAL) != null && BlazeSovereign.bar(RaidMode.NORMAL).getPlayers().contains(player);
			helper.assertTrue(shown, "the player does not see the Sovereign's bar");
			NetherTestSupport.result("sovereignBar", "shown=" + shown + " distance=" + (int) player.distanceTo(blaze));
			blaze.discard();
			NetherTestSupport.leave(helper, player);
		});
	}

	@GameTest(environment = "mod-stella-nether-test:sov_once", maxTicks = 400)
	public void aSlainSovereignNeverRisesAgainInItsRun(final GameTestHelper helper) {
		ServerPlayer player = enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		Blaze first = BlazeSovereign.rise(raid, arena(raid, server, 8));
		first.kill(raid);
		BlockPos fortress = RaidInstance.site(server, RaidMode.NORMAL).fortress();
		moveTo(player, fortress.getX() + 0.5, 80.0, fortress.getZ() + 0.5);
		helper.runAfterDelay(300, () -> {
			// This run's Sovereigns only: an earlier failing test can leave its own standing elsewhere in the level.
			int run = RaidInstance.state(server, RaidMode.NORMAL).run();
			long risen = raid.getEntities(EntityTypes.BLAZE, blaze -> BlazeSovereign.isSovereign(blaze) && blaze.isAlive()
				&& Integer.valueOf(run).equals(blaze.getAttached(BlazeSovereign.SOVEREIGN))).size();
			boolean loaded = raid.isLoaded(fortress);
			NetherTestSupport.result("sovereignOnce", "alive=" + risen + " fortressLoaded=" + loaded + " risen=" + RaidInstance.state(server, RaidMode.NORMAL).sovereignRisen());
			NetherTestSupport.leave(helper, player);
			helper.assertTrue(loaded && risen == 0, "after the Sovereign fell, " + risen + " stand again (fortress loaded=" + loaded + ")");
			helper.succeed();
		});
	}
}
