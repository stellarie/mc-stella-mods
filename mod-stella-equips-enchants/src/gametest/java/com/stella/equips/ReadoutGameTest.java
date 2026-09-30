package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.power.PowerTracker;
import com.stella.equips.readout.ReadoutPayload;
import com.stella.equips.readout.ReadoutRank;
import com.stella.equips.readout.ReadoutService;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class ReadoutGameTest {
	// A ticked mock player falls while its area loads, so each one returns to where the test placed it.
	private static final Map<UUID, Vec3> STARTS = new ConcurrentHashMap<>();
	private static final int MAX_TICKS = 300;
	private static final int CHUNK_SETTLE_TICKS = 60;
	private static final int REACT_TICKS = 20;
	// The rescan window after a hit, plus one look interval for a slow goal tick.
	private static final int HOSTILITY_DEADLINE_TICKS = ReadoutService.PENDING_TICKS + ReadoutService.LOOK_INTERVAL_TICKS;
	private static final int FORCED_CHUNK_RADIUS = 3;
	private static final double NEAR_DISTANCE = 10.0;
	private static final double FAR_DISTANCE = 40.0;
	private static final double NEARER_DISTANCE = 6.0;
	private static final double WALL_DISTANCE = 5.0;
	private static final int CORRIDOR_LENGTH = 45;
	private static final float FACING_EAST = -90.0F;
	private static final float FACING_WEST = 90.0F;
	private static final float LEVEL_HEAD = 0.0F;
	private static final float HEALTH_AFTER_HIT = 5.0F;
	private static final float LETHAL_DAMAGE = 1000.0F;
	private static final float ABSORPTION_TEST_MAX = 20.0F;
	private static final float ABSORPTION_TEST_VALUE = 4.0F;
	private static final double STAGED_RADIUS = 20.0;
	private static final double STAGED_OUTSIDE_DISTANCE = 25.0;
	private static final int STAGED_CAP = 3;
	private static final double STAGED_HEIGHT_OFFSET = 2.5;
	private static final double STAGED_VIEW_RANGE = 0.25;
	private static final double HEALTH_LINE_TEST_X = 29000.0;
	private static final double TEST_X_STEP = 400.0;
	private static final double FIRST_TEST_X = 40000.0;
	private static final double INSIDE_TEST_X = FIRST_TEST_X;
	private static final double BEYOND_TEST_X = FIRST_TEST_X + TEST_X_STEP;
	private static final double ASIDE_TEST_X = FIRST_TEST_X + 2 * TEST_X_STEP;
	private static final double WALL_TEST_X = FIRST_TEST_X + 3 * TEST_X_STEP;
	private static final double AWAY_TEST_X = FIRST_TEST_X + 4 * TEST_X_STEP;
	private static final double NEAREST_TEST_X = FIRST_TEST_X + 5 * TEST_X_STEP;
	private static final double CONTENT_TEST_X = FIRST_TEST_X + 6 * TEST_X_STEP;
	private static final double UNCHANGED_TEST_X = FIRST_TEST_X + 7 * TEST_X_STEP;
	private static final double HEALTH_TEST_X = FIRST_TEST_X + 8 * TEST_X_STEP;
	private static final double OPTOUT_TEST_X = FIRST_TEST_X + 9 * TEST_X_STEP;
	private static final double DISCONNECT_TEST_X = FIRST_TEST_X + 10 * TEST_X_STEP;
	private static final double DEATH_TEST_X = FIRST_TEST_X + 11 * TEST_X_STEP;
	private static final double LEAK_TEST_X = FIRST_TEST_X + 12 * TEST_X_STEP;
	private static final double RANK_TEST_X = FIRST_TEST_X + 13 * TEST_X_STEP;
	private static final double CONFIG_TEST_X = FIRST_TEST_X + 14 * TEST_X_STEP;
	private static final double HOSTILITY_TEST_X = FIRST_TEST_X + 15 * TEST_X_STEP;
	private static final double GOLEM_HIT_TEST_X = FIRST_TEST_X + 16 * TEST_X_STEP;
	private static final double SECOND_VIEWER_TEST_X = FIRST_TEST_X + 17 * TEST_X_STEP;
	private static final double TARGET_CHANGE_TEST_X = FIRST_TEST_X + 18 * TEST_X_STEP;
	private static final double HOSTILITY_PACKET_TEST_X = FIRST_TEST_X + 19 * TEST_X_STEP;
	private static final double REAL_TICK_TEST_X = FIRST_TEST_X + 20 * TEST_X_STEP;
	private static final Predicate<Packet<?>> THE_HEALTH_LINE = packet ->
		packet instanceof ClientboundSystemChatPacket line
			&& line.overlay()
			&& line.content().getString().startsWith("Lv ");

	@GameTest
	public void theReadoutScanRunsOnTheRealServerTick(final GameTestHelper helper) {
		PowerRecorder.install();
		boolean registered = ReadoutService.isRegistered();
		int before = ReadoutService.serverTicks();
		helper.runAfterDelay(10, () -> {
			int after = ReadoutService.serverTicks();
			helper.assertTrue(registered, "the readout service did not report that it is registered");
			helper.assertTrue(
				after > before,
				"the real server tick advanced the readout scan by " + (after - before) + " ticks"
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutRegistered RESULT registered={} ticksBefore={} ticksAfter={} advanced={} lookInterval={}",
				registered, before, after, after - before, ReadoutService.LOOK_INTERVAL_TICKS
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aRealPlayerGetsTheReadoutFromTheServerTick(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, REAL_TICK_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);

			helper.runAfterDelay(REACT_TICKS, () -> {
				ReadoutPayload current = ReadoutService.owner(owner).current();
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutRealTick RESULT cow={} current={} sent={}",
					cow.getId(), current, payloads(owner).size()
				);
				helper.assertTrue(
					current != null && current.entityId() == cow.getId(),
					"the server tick left a looked-at mob without a readout; current=" + current
				);
				helper.assertTrue(
					payloads(owner).size() == 1,
					"the server tick sent " + payloads(owner).size() + " payloads for one steady target"
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aLookedAtMobInsideRangeGetsAReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, INSIDE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload current = ReadoutService.owner(owner).current();
			List<ReadoutPayload> sent = payloads(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookInside RESULT distance={} radius={} cow={} current={} sent={}",
				NEAR_DISTANCE, config.readout().radius(), cow.getId(), current, sent.size()
			);
			helper.assertTrue(
				NEAR_DISTANCE <= config.readout().radius(),
				"the test mob at " + NEAR_DISTANCE + " blocks sits outside the radius " + config.readout().radius()
			);
			helper.assertTrue(
				current != null && current.entityId() == cow.getId(),
				"a mob " + NEAR_DISTANCE + " blocks down the view ray carried no readout; current=" + current
			);
			helper.assertTrue(
				sent.size() == 1 && sent.get(0).equals(current),
				"the owner received " + sent.size() + " payloads instead of the one for the looked-at mob"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aMobBeyondRangeGetsNoReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, BEYOND_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, FAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookBeyond RESULT distance={} radius={} cow={} current={} sent={}",
				FAR_DISTANCE, config.readout().radius(), cow.getId(), ReadoutService.owner(owner).current(),
				payloads(owner).size()
			);
			helper.assertTrue(
				FAR_DISTANCE > config.readout().radius(),
				"the test mob at " + FAR_DISTANCE + " blocks sits inside the radius " + config.readout().radius()
			);
			helper.assertTrue(
				ReadoutService.owner(owner).current() == null,
				"a mob " + FAR_DISTANCE + " blocks away carried a readout"
			);
			helper.assertTrue(payloads(owner).isEmpty(), "the owner received a payload for a mob beyond range");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aMobNotLookedAtGetsNoReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, ASIDE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);
			face(owner, FACING_WEST);
			ReadoutService.scanPlayer(owner, config);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookAside RESULT current={} sent={}",
				ReadoutService.owner(owner).current(), payloads(owner).size()
			);
			helper.assertTrue(
				ReadoutService.owner(owner).current() == null,
				"a mob behind the player carried a readout"
			);
			helper.assertTrue(payloads(owner).isEmpty(), "the owner received a payload for a mob not looked at");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aBlockBetweenBlocksTheReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, WALL_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);
			BlockPos wall = BlockPos.containing(owner.getX() + WALL_DISTANCE, owner.getEyeY(), owner.getZ());
			helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
			ReadoutService.scanPlayer(owner, config);
			boolean walled = !helper.getLevel().getBlockState(wall).isAir();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookWall RESULT wall={} walled={} current={} sent={}",
				wall, walled, ReadoutService.owner(owner).current(), payloads(owner).size()
			);
			helper.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 2);
			helper.assertTrue(walled, "the test wall at " + wall + " never stood");
			helper.assertTrue(
				ReadoutService.owner(owner).current() == null,
				"a mob behind a stone block carried a readout"
			);
			helper.assertTrue(payloads(owner).isEmpty(), "the owner received a payload for a mob behind a wall");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void lookingAwayClearsTheReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, AWAY_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			helper.assertTrue(ReadoutService.owner(owner).current() != null, "the mob was not seen before the turn");
			face(owner, FACING_WEST);
			ReadoutService.scanPlayer(owner, config);
			ReadoutService.scanPlayer(owner, config);
			List<ReadoutPayload> sent = payloads(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookAway RESULT current={} sent={} lastIsClear={}",
				ReadoutService.owner(owner).current(), sent.size(), !sent.isEmpty() && sent.get(sent.size() - 1).isClear()
			);
			helper.assertTrue(ReadoutService.owner(owner).current() == null, "the readout stayed after the player looked away");
			helper.assertTrue(
				sent.size() == 2 && !sent.get(0).isClear() && sent.get(1).isClear(),
				"looking away sent " + sent.size() + " payloads instead of one target then one clear"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theNearestMobOnTheRayWins(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, NEAREST_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob far = spawnOnRay(helper, owner, NEAR_DISTANCE);
			Mob near = spawnOnRay(helper, owner, NEARER_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload current = ReadoutService.owner(owner).current();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutLookNearest RESULT near={} far={} current={}",
				near.getId(), far.getId(), current
			);
			helper.assertTrue(
				current != null && current.entityId() == near.getId(),
				"the readout went to " + (current == null ? "nobody" : String.valueOf(current.entityId()))
					+ " instead of the nearer mob " + near.getId()
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void payloadCarriesThreatRankHealthAndAbsorption(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, CONTENT_TEST_X);
		Map<UUID, String> ranks = new ConcurrentHashMap<>();
		Function<Entity, String> provider = entity -> ranks.get(entity.getUUID());

		whenAreaLoaded(helper, owner, () -> {
			// Set inside one tick, so a parallel test cannot swap the provider between set and scan.
			Object previous = FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, provider);

			try {
				openCorridor(helper, owner);
				Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
				ranks.put(cow.getUUID(), "elite");
				cow.setCustomName(Component.literal("Bessie").withStyle(ChatFormatting.BLUE));
				cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(ABSORPTION_TEST_MAX);
				cow.setAbsorptionAmount(ABSORPTION_TEST_VALUE);
				ReadoutService.scanPlayer(owner, config);
				ReadoutPayload withAbsorption = ReadoutService.owner(owner).current();
				cow.setAbsorptionAmount(0.0F);
				ReadoutService.scanPlayer(owner, config);
				ReadoutPayload without = ReadoutService.owner(owner).current();
				int threat = PowerTracker.threat(cow, config);
				int gap = PowerCurve.normalizedThreat(threat, config) - PowerTracker.playerLevel(owner, config);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutPayloadContent RESULT with={} without={} threat={} gap={}",
					withAbsorption, without, threat, gap
				);
				helper.assertTrue(withAbsorption != null && without != null, "the looked-at mob sent no payload");
				helper.assertTrue(
					withAbsorption.threat() == threat && withAbsorption.bandValue() == PowerCurve.band(gap, config),
					"the payload threat and band read " + withAbsorption.threat() + " " + withAbsorption.bandValue()
				);
				helper.assertTrue(
					withAbsorption.rankValue() == ReadoutRank.ELITE,
					"the payload rank read " + withAbsorption.rankValue()
				);
				helper.assertTrue(
					withAbsorption.health() == cow.getHealth() && withAbsorption.maxHealth() == cow.getMaxHealth(),
					"the payload health read " + withAbsorption.health() + "/" + withAbsorption.maxHealth()
				);
				helper.assertTrue(
					withAbsorption.absorption() == ABSORPTION_TEST_VALUE,
					"the payload absorption read " + withAbsorption.absorption()
				);
				helper.assertTrue(without.absorption() == 0.0F, "the payload absorption read " + without.absorption() + " after it dropped to zero");
				helper.assertTrue(
					"Bessie".equals(withAbsorption.name()),
					"a colored custom name arrived as '" + withAbsorption.name() + "'"
				);
				helper.succeed();
			} finally {
				restoreProvider(previous);
			}
		});
	}

	@GameTest
	public void thePayloadSurvivesTheWire(final GameTestHelper helper) {
		ReadoutPayload original = ReadoutPayload.of(
			42, 17, PowerCurve.Band.ORANGE, ReadoutRank.BOSS, true, 12.5F, 40.0F, 6.0F, "Wither"
		);
		RegistryFriendlyByteBuf buffer = wireBuffer(helper);
		ReadoutPayload.CODEC.encode(buffer, original);
		ReadoutPayload decoded = ReadoutPayload.CODEC.decode(buffer);
		RegistryFriendlyByteBuf clearBuffer = wireBuffer(helper);
		ReadoutPayload.CODEC.encode(clearBuffer, ReadoutPayload.clear());
		ReadoutPayload cleared = ReadoutPayload.CODEC.decode(clearBuffer);
		helper.assertTrue(original.equals(decoded), "the payload read " + decoded + " after a round trip of " + original);
		helper.assertTrue(cleared.isClear(), "the clear payload read " + cleared + " after a round trip");
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void anUnchangedTargetSendsNoPacket(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, UNCHANGED_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);

			for (int i = 0; i < 5; i++) {
				ReadoutService.scanPlayer(owner, config);
			}

			StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutUnchanged RESULT sent={}", payloads(owner).size());
			helper.assertTrue(
				payloads(owner).size() == 1,
				"five scans of a steady target sent " + payloads(owner).size() + " payloads"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aHealthChangeSendsOneUpdate(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, HEALTH_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			cow.setHealth(HEALTH_AFTER_HIT);
			ReadoutService.scanPlayer(owner, config);
			ReadoutService.scanPlayer(owner, config);
			List<ReadoutPayload> sent = payloads(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutHealthChange RESULT sent={} lastHealth={}",
				sent.size(), sent.isEmpty() ? -1.0F : sent.get(sent.size() - 1).health()
			);
			helper.assertTrue(sent.size() == 2, "a health change sent " + sent.size() + " payloads in total, not two");
			helper.assertTrue(
				sent.get(1).health() == HEALTH_AFTER_HIT && sent.get(1).entityId() == cow.getId(),
				"the update read health " + sent.get(1).health() + " instead of " + HEALTH_AFTER_HIT
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theOptOutCommandClearsTheReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, OPTOUT_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			helper.assertTrue(ReadoutService.owner(owner).current() != null, "the mob was not seen before the opt out");
			owner.level().getServer().getCommands().performPrefixedCommand(
				owner.createCommandSourceStack(), "stellaequips readout off"
			);
			ReadoutService.scanPlayer(owner, config);
			List<ReadoutPayload> sent = payloads(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutOptOut RESULT enabled={} current={} sent={}",
				ReadoutService.owner(owner).enabled(), ReadoutService.owner(owner).current(), sent.size()
			);
			helper.assertTrue(!ReadoutService.owner(owner).enabled(), "the opt out command left the readout enabled");
			helper.assertTrue(ReadoutService.owner(owner).current() == null, "the opt out left a readout up");
			helper.assertTrue(
				sent.size() == 2 && sent.get(1).isClear(),
				"the opt out sent " + sent.size() + " payloads instead of one target then one clear"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aDisconnectedOwnerLosesItsReadoutState(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, DISCONNECT_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			helper.assertTrue(ReadoutService.online(owner.getUUID()) != null, "the owner held no state before the disconnect");
			ReadoutService.forget(owner.getUUID());
			boolean gone = ReadoutService.online(owner.getUUID()) == null;
			ReadoutService.scanPlayer(owner, config);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutDisconnect RESULT gone={} sentAfterReturn={}", gone, payloads(owner).size()
			);
			helper.assertTrue(gone, "the owner state survived the disconnect");
			helper.assertTrue(
				payloads(owner).size() == 2,
				"a returning owner received " + payloads(owner).size() + " payloads instead of a fresh one"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aDeadMobLosesItsReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = lookerAt(helper, DEATH_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			helper.assertTrue(ReadoutService.owner(owner).current() != null, "the mob was not seen before its death");
			cow.hurtServer(level, level.damageSources().generic(), LETHAL_DAMAGE);
			helper.assertTrue(cow.isDeadOrDying(), "the mob survived " + LETHAL_DAMAGE + " of generic damage");
			ReadoutService.scanPlayer(owner, config);
			List<ReadoutPayload> sent = payloads(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutMobDeath RESULT current={} sent={}", ReadoutService.owner(owner).current(), sent.size()
			);
			helper.assertTrue(ReadoutService.owner(owner).current() == null, "a readout survived its mob's death");
			helper.assertTrue(
				sent.size() == 2 && sent.get(1).isClear(),
				"a mob death sent " + sent.size() + " payloads instead of one target then one clear"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutNeverReachesAnotherPlayer(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer looker = lookerAt(helper, LEAK_TEST_X);
		ServerPlayer bystander = lookerAt(helper, LEAK_TEST_X);
		face(bystander, FACING_WEST);

		whenAreaLoaded(helper, looker, () -> {
			openCorridor(helper, looker);
			spawnOnRay(helper, looker, NEAR_DISTANCE);
			ReadoutService.scanPlayer(looker, config);
			ReadoutService.scanPlayer(bystander, config);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutNoLeak RESULT looker={} bystander={}", payloads(looker).size(), payloads(bystander).size()
			);
			helper.assertTrue(payloads(looker).size() == 1, "the looker received " + payloads(looker).size() + " payloads");
			helper.assertTrue(
				payloads(bystander).isEmpty(),
				"a player who looked elsewhere received " + payloads(bystander).size() + " readout payloads"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aRankChangeReachesTheClient(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = lookerAt(helper, RANK_TEST_X);
		Map<UUID, String> ranks = new ConcurrentHashMap<>();
		Function<Entity, String> provider = entity -> ranks.get(entity.getUUID());

		whenAreaLoaded(helper, owner, () -> {
			// Set inside one tick, so a parallel test cannot swap the provider between set and scan.
			Object previous = FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, provider);

			try {
				openCorridor(helper, owner);
				Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
				ranks.put(cow.getUUID(), "normal");
				ReadoutService.scanPlayer(owner, config);
				ranks.put(cow.getUUID(), "elite");
				ReadoutService.scanPlayer(owner, config);
				ranks.put(cow.getUUID(), "boss");
				ReadoutService.scanPlayer(owner, config);
				List<ReadoutPayload> sent = payloads(owner);
				StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutRankChange RESULT sent={}", sent);
				helper.assertTrue(sent.size() == 3, "three rank changes sent " + sent.size() + " payloads");
				helper.assertTrue(
					sent.get(0).rankValue() == ReadoutRank.NORMAL && sent.get(1).rankValue() == ReadoutRank.ELITE
						&& sent.get(2).rankValue() == ReadoutRank.BOSS,
					"the ranks arrived as " + sent.stream().map(ReadoutPayload::rankValue).toList()
				);
				helper.succeed();
			} finally {
				restoreProvider(previous);
			}
		});
	}

	@GameTest
	public void theReadoutConfigKeysParse(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("readout-config").resolve(PowerConfigStore.FILE_NAME);
		Files.writeString(file, stagedDocument(false), StandardCharsets.UTF_8);
		PowerConfig loaded = PowerConfigStore.loadFrom(file);
		PowerConfigStore.load();
		helper.assertTrue(
			loaded.readout().equals(new PowerConfig.Readout(false, STAGED_RADIUS, STAGED_CAP, STAGED_HEIGHT_OFFSET, STAGED_VIEW_RANGE)),
			"the staged readout block loaded as " + loaded.readout()
		);
		StellaEquips.LOGGER.info(
			"[BEHAVIOR] test=readoutConfigParse RESULT radius={} maxPerPlayer={} heightOffset={} viewRange={} enabledByDefault={}",
			loaded.readout().radius(), loaded.readout().maxPerPlayer(), loaded.readout().heightOffset(),
			loaded.readout().viewRange(), loaded.readout().enabledByDefault()
		);
		helper.succeed();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theReadoutConfigKeysDriveTheLook(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = lookerAt(helper, CONFIG_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob cow = spawnOnRay(helper, owner, STAGED_OUTSIDE_DISTANCE);
			PowerConfig staged = PowerConfigStore.loadFrom(stageDocument(true));
			ReadoutService.scanPlayer(owner, staged);
			boolean underStaged = ReadoutService.owner(owner).current() != null;
			ReadoutService.scanPlayer(owner, liveConfig());
			boolean underLive = ReadoutService.owner(owner).current() != null;
			ServerPlayer disabledOwner = lookerAt(helper, CONFIG_TEST_X);
			PowerConfig disabled = PowerConfigStore.loadFrom(stageDocument(false));
			ReadoutService.scanPlayer(disabledOwner, disabled);
			boolean disabledHeld = ReadoutService.owner(disabledOwner).current() != null;
			PowerConfigStore.load();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutConfigDrives RESULT stagedRadius={} distance={} underStaged={} underLive={} disabledHeld={} cow={}",
				staged.readout().radius(), STAGED_OUTSIDE_DISTANCE, underStaged, underLive, disabledHeld, cow.getId()
			);
			helper.assertTrue(
				!underStaged,
				"a mob at " + STAGED_OUTSIDE_DISTANCE + " blocks carried a readout under a staged radius of " + STAGED_RADIUS
			);
			helper.assertTrue(underLive, "the same mob carried no readout under the bundled radius");
			helper.assertTrue(!disabledHeld, "a staged enabled_by_default of false still produced a readout");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void enemyNameIsHostileAndPassiveNameIsNot(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = survivalOwnerAt(helper, HOSTILITY_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob husk = spawnOnRay(helper, owner, EntityTypes.HUSK, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload huskPayload = ReadoutService.owner(owner).current();
			husk.discard();
			Mob cow = spawnOnRay(helper, owner, NEAR_DISTANCE);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload cowPayload = ReadoutService.owner(owner).current();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutEnemyColor RESULT husk={} cow={}", huskPayload, cowPayload
			);
			cow.discard();
			helper.assertTrue(
				huskPayload != null && cowPayload != null && huskPayload.hostile() && !cowPayload.hostile(),
				"the husk payload read " + huskPayload + " and the cow payload read " + cowPayload
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void attackedGolemTurnsHostileWithinTheRescanWindow(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, GOLEM_HIT_TEST_X);
		Mob[] golem = {null};
		int[] hitAt = {-1};
		int[] hostileAt = {-1};

		helper.onEachTick(() -> {
			if (golem[0] == null) {
				return;
			}

			int now = ReadoutService.serverTicks();
			ReadoutPayload current = ReadoutService.owner(owner).current();
			boolean seen = current != null && current.entityId() == golem[0].getId();

			if (hitAt[0] < 0 && seen && !current.hostile()) {
				hitAt[0] = now;
				golem[0].hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 1.0F);
			} else if (hitAt[0] >= 0 && hostileAt[0] < 0 && seen && current.hostile()) {
				hostileAt[0] = now;
			}
		});
		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			golem[0] = spawnOnRay(helper, owner, EntityTypes.IRON_GOLEM, NEAR_DISTANCE, false);
		});
		helper.runAfterDelay(MAX_TICKS - 10, () -> {
			int delay = hostileAt[0] < 0 ? -1 : hostileAt[0] - hitAt[0];
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutGolemHit RESULT hitAt={} hostileAt={} delay={} interval={}",
				hitAt[0], hostileAt[0], delay, ReadoutService.LOOK_INTERVAL_TICKS
			);
			if (golem[0] != null) {
				golem[0].discard();
			}
			helper.assertTrue(
				hitAt[0] >= 0 && delay >= 0 && delay <= HOSTILITY_DEADLINE_TICKS,
				"the golem hit at " + hitAt[0] + " turned hostile after " + delay + " ticks, past the deadline of " + HOSTILITY_DEADLINE_TICKS
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void secondViewerStaysPassiveUntilTargeted(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer attacker = survivalOwnerAt(helper, SECOND_VIEWER_TEST_X);
		ServerPlayer bystander = survivalOwnerAt(helper, SECOND_VIEWER_TEST_X);

		whenAreaLoaded(helper, attacker, () -> {
			openCorridor(helper, attacker);
			Mob golem = spawnOnRay(helper, attacker, EntityTypes.IRON_GOLEM, NEAR_DISTANCE, true);
			golem.setTarget(attacker);
			ReadoutService.scanPlayer(attacker, config);
			ReadoutService.scanPlayer(bystander, config);
			ReadoutPayload attackerPayload = ReadoutService.owner(attacker).current();
			ReadoutPayload bystanderPayload = ReadoutService.owner(bystander).current();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutSecondViewer RESULT attacker={} bystander={}", attackerPayload, bystanderPayload
			);
			golem.discard();
			helper.assertTrue(
				attackerPayload != null && attackerPayload.hostile()
					&& bystanderPayload != null && !bystanderPayload.hostile(),
				"the attacker saw " + attackerPayload + " and the bystander saw " + bystanderPayload
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void targetChangeWithoutDamageTurnsHostileOnScan(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = survivalOwnerAt(helper, TARGET_CHANGE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob golem = spawnOnRay(helper, owner, EntityTypes.IRON_GOLEM, NEAR_DISTANCE, true);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload before = ReadoutService.owner(owner).current();
			golem.setTarget(owner);
			ReadoutService.scanPlayer(owner, config);
			ReadoutPayload after = ReadoutService.owner(owner).current();
			StellaEquips.LOGGER.info("[BEHAVIOR] test=readoutTargetChange RESULT before={} after={}", before, after);
			golem.discard();
			helper.assertTrue(
				before != null && !before.hostile() && after != null && after.hostile(),
				"a golem that targeted its viewer read " + before + " then " + after
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aHostilityChangeSendsExactlyOnePacket(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = survivalOwnerAt(helper, HOSTILITY_PACKET_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			openCorridor(helper, owner);
			Mob golem = spawnOnRay(helper, owner, EntityTypes.IRON_GOLEM, NEAR_DISTANCE, true);
			ReadoutService.scanPlayer(owner, config);
			int before = payloads(owner).size();
			golem.setTarget(owner);
			ReadoutService.scanPlayer(owner, config);
			int afterChange = payloads(owner).size() - before;
			ReadoutService.scanPlayer(owner, config);
			int afterRepeat = payloads(owner).size() - before;
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutHostilityPacket RESULT afterChange={} afterRepeat={}", afterChange, afterRepeat
			);
			golem.discard();
			helper.assertTrue(
				afterChange == 1 && afterRepeat == 1,
				"a hostility-only change sent " + afterChange + " packets, then " + afterRepeat + " in total"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theScanTickSendsNoLevelLine(final GameTestHelper helper) {
		ServerPlayer owner = ownerAt(helper, HEALTH_LINE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			int before = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			int interval = liveConfig().tracker().scanIntervalTicks();
			for (int i = 0; i < interval; i++) {
				PowerTracker.onServerTick(owner.level().getServer());
			}
			int after = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=noLevelLine RESULT before={} after={}", before, after);
			helper.assertTrue(after == before, "the scan tick sent " + (after - before) + " level lines");
			helper.succeed();
		});
	}

	/**
	 * The harness runs no client, so nothing here draws the plate. Oniichan checks the pixels by
	 * hand: ring and number left of the name, yellow absorption bar under it, green HP bar below.
	 */
	@GameTest(required = false)
	public void hudPixelLayoutNeedsManualCheck(final GameTestHelper helper) {
		helper.fail("the plate is drawn by the client; look at a mob in the Prism instance to check it");
	}

	private static RegistryFriendlyByteBuf wireBuffer(final GameTestHelper helper) {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
	}

	private static List<ReadoutPayload> payloads(final ServerPlayer player) {
		return PacketRecorder.of(player).readoutPayloads();
	}

	private static PowerConfig liveConfig() {
		PowerConfigStore.load();
		return PowerConfigStore.get();
	}

	/** A mock player faces east, so a mob placed with `spawnOnRay` sits on the view ray. */
	private static ServerPlayer lookerAt(final GameTestHelper helper, final double offsetX) {
		ServerPlayer player = ownerAt(helper, offsetX);
		face(player, FACING_EAST);
		return player;
	}

	/** The view vector of a living entity reads the head yaw, so the head turns with the body. */
	private static void face(final ServerPlayer player, final float yaw) {
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(LEVEL_HEAD);
	}

	private static ServerPlayer ownerAt(final GameTestHelper helper, final double offsetX) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos spawn = helper.getLevel().getRespawnData().pos();
		player.setPos(spawn.getX() + offsetX, spawn.getY() + 1.0, spawn.getZ());
		player.experienceLevel = 0;
		STARTS.put(player.getUUID(), player.position());
		PacketRecorder.attach(player);
		forceChunks(helper.getLevel(), player.getX(), player.getZ(), true);
		return player;
	}

	/** A moved mock player loads no chunk, so the test area is forced and awaited before any mob spawns. */
	private static void whenAreaLoaded(
		final GameTestHelper helper,
		final ServerPlayer owner,
		final Runnable body
	) {
		helper.runAfterDelay(CHUNK_SETTLE_TICKS, () -> {
			helper.assertTrue(
				helper.getLevel().isLoaded(owner.blockPosition()),
				"the test area at " + owner.blockPosition() + " did not load within " + CHUNK_SETTLE_TICKS + " ticks"
			);
			Vec3 start = STARTS.get(owner.getUUID());

			if (start != null) {
				owner.setPos(start);
				owner.setDeltaMovement(Vec3.ZERO);
				owner.fallDistance = 0.0;
			}

			body.run();
		});
	}

	private static void forceChunks(
		final ServerLevel level,
		final double x,
		final double z,
		final boolean forced
	) {
		int centerX = SectionPos.blockToSectionCoord(x);
		int centerZ = SectionPos.blockToSectionCoord(z);

		for (int dx = -FORCED_CHUNK_RADIUS; dx <= FORCED_CHUNK_RADIUS; dx++) {
			for (int dz = -FORCED_CHUNK_RADIUS; dz <= FORCED_CHUNK_RADIUS; dz++) {
				level.setChunkForced(centerX + dx, centerZ + dz, forced);
			}
		}
	}

	/** Clears the air east of the owner, so no terrain hides the ray, and lays a stone floor. */
	private static void openCorridor(final GameTestHelper helper, final ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		BlockPos base = owner.blockPosition();

		for (int dx = -1; dx <= CORRIDOR_LENGTH; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(base.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);

				for (int dy = 0; dy <= 4; dy++) {
					level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	private static Mob spawnOnRay(final GameTestHelper helper, final ServerPlayer owner, final double distance) {
		return spawnOnRay(helper, owner, EntityTypes.COW, distance, true);
	}

	private static Mob spawnOnRay(
		final GameTestHelper helper,
		final ServerPlayer owner,
		final EntityType<? extends Mob> type,
		final double distance
	) {
		return spawnOnRay(helper, owner, type, distance, true);
	}

	/** Places the mob so its box centre sits at the owner's eye height, straight down the east ray. */
	private static Mob spawnOnRay(
		final GameTestHelper helper,
		final ServerPlayer owner,
		final EntityType<? extends Mob> type,
		final double distance,
		final boolean still
	) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.setPos(owner.getX() + distance, owner.getEyeY() - type.getHeight() / 2.0, owner.getZ());
		mob.setNoAi(still);
		mob.setNoGravity(still);
		mob.setPersistenceRequired();
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "the level refused the " + type);
		return mob;
	}

	// The in-level mock player is always creative, which no mob can target, so this one is survival.
	private static ServerPlayer survivalOwnerAt(final GameTestHelper helper, final double offsetX) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(
			new GameProfile(UUID.randomUUID(), "test-survival-player"), false
		);
		ServerPlayer player = new ServerPlayer(
			helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()
		);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos spawn = helper.getLevel().getRespawnData().pos();
		player.setPos(spawn.getX() + offsetX, spawn.getY() + 1.0, spawn.getZ());
		face(player, FACING_EAST);
		player.experienceLevel = 0;
		STARTS.put(player.getUUID(), player.position());
		PacketRecorder.attach(player);
		forceChunks(helper.getLevel(), player.getX(), player.getZ(), true);
		return player;
	}

	private static Path stageDocument(final boolean enabledByDefault) {
		try {
			Path file = Files.createTempDirectory("readout-staged").resolve(PowerConfigStore.FILE_NAME);
			Files.writeString(file, stagedDocument(enabledByDefault), StandardCharsets.UTF_8);
			return file;
		} catch (IOException e) {
			throw new IllegalStateException("could not stage the readout document: " + e.getMessage(), e);
		}
	}

	private static String stagedDocument(final boolean enabledByDefault) {
		return PowerConfigStore.DEFAULT_JSON
			.replace("\"enabled_by_default\": true", "\"enabled_by_default\": " + enabledByDefault)
			.replace("\"radius\": 32", "\"radius\": " + (int) STAGED_RADIUS)
			.replace("\"max_per_player\": 40", "\"max_per_player\": " + STAGED_CAP)
			.replace("\"height_offset\": 0.9", "\"height_offset\": " + STAGED_HEIGHT_OFFSET)
			.replace("\"view_range\": 0.5", "\"view_range\": " + STAGED_VIEW_RANGE);
	}

	private static void restoreProvider(final Object previous) {
		if (previous == null) {
			FabricLoader.getInstance().getObjectShare().remove(ReadoutRank.OBJECT_SHARE_KEY);
		} else {
			FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, previous);
		}
	}
}
