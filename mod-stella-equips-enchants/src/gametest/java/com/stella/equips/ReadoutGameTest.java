package com.stella.equips;

import com.stella.equips.power.PowerConfig;
import com.stella.equips.power.PowerConfigStore;
import com.stella.equips.power.PowerCurve;
import com.stella.equips.power.PowerTracker;
import com.stella.equips.readout.ReadoutOwner;
import com.stella.equips.readout.ReadoutRank;
import com.stella.equips.readout.ReadoutService;
import com.stella.equips.readout.ReadoutText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

public class ReadoutGameTest {
	private static final int MAX_TICKS = 300;
	private static final int CHUNK_SETTLE_TICKS = 60;
	private static final int SCAN_WINDOW_TICKS = 45;
	private static final int FORCED_CHUNK_RADIUS = 3;
	private static final double NEAR_DISTANCE = 10.0;
	private static final double FAR_DISTANCE = 40.0;
	private static final int CANDIDATE_MOBS = 100;
	private static final int EXPECTED_CAP = 40;
	private static final double SPIRAL_FIRST_RADIUS = 2.0;
	private static final double SPIRAL_RADIUS_STEP = 0.3;
	private static final double GOLDEN_ANGLE = 2.39996;
	private static final double BAND_TEST_X = 600.0;
	private static final double BAND_TEST_UNCHANGED_X = 800.0;
	private static final int BAND_TEST_START_GAP = -5;
	private static final int BAND_TEST_END_GAP = -6;
	private static final double RADIUS_TEST_X = 2000.0;
	private static final double RADIUS_RANGE_TEST_X = 2200.0;
	private static final double CAP_TEST_X = 5000.0;
	private static final double DEATH_TEST_X = 8000.0;
	private static final double LEAK_TEST_X = 11000.0;
	private static final double LEAK_PRIVATE_TEST_X = 11200.0;
	private static final double OPTOUT_TEST_X = 14000.0;
	private static final double DAMAGE_TEST_X = 17000.0;
	private static final double TEXT_TEST_X = 17200.0;
	private static final double FOLLOW_TEST_X = 20000.0;
	private static final double CONFIG_TEST_X = 23000.0;
	private static final double STAGED_RADIUS = 20.0;
	private static final int STAGED_CAP = 3;
	private static final double STAGED_HEIGHT_OFFSET = 2.5;
	private static final double STAGED_VIEW_RANGE = 0.25;
	private static final double WALK_DISTANCE = 10.0;
	private static final double WALK_SPEED = 1.0;
	private static final double WALK_MIN = 3.0;
	private static final int WALK_POLL_TICKS = 5;
	private static final int WALK_DEADLINE_TICKS = 120;
	private static final double LAG_TOLERANCE = 1.0;
	private static final float LETHAL_DAMAGE = 1000.0F;
	private static final float READOUT_DAMAGE = 100.0F;
	private static final double HEALTH_TEST_X = 26000.0;
	private static final double HEALTH_MOVE_TEST_X = 26200.0;
	private static final double TARGET_DISTANCE = 2.0;
	private static final float HEALTH_DAMAGE = 5.0F;
	private static final double HEALTH_LINE_TEST_X = 29000.0;
	private static final double RANK_TEST_X = 32000.0;
	private static final double HOSTILITY_TEST_X = 35000.0;
	private static final double GOLEM_HIT_TEST_X = 35400.0;
	private static final double SECOND_VIEWER_TEST_X = 35800.0;
	private static final double TARGET_CHANGE_TEST_X = 36200.0;
	private static final double COLOR_PACKET_TEST_X = 36600.0;
	private static final String RED = "red";
	private static final String GREEN = "green";
	private static final int RED_VALUE = TextColor.fromLegacyFormat(ChatFormatting.RED).getValue();
	private static final int GREEN_VALUE = TextColor.fromLegacyFormat(ChatFormatting.GREEN).getValue();
	private static final Predicate<Packet<?>> THE_HEALTH_LINE = packet ->
		packet instanceof ClientboundSystemChatPacket line
			&& line.overlay()
			&& line.content().getString().startsWith("Lv ");
	private static final String SPAWN_LINE = "readout spawn";
	private static final String BAND_LINE = "readout band";
	private static final String REMOVE_LINE = "readout remove";

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
				"[BEHAVIOR] test=readoutRegistered RESULT registered={} ticksBefore={} ticksAfter={} advanced={} scanInterval={}",
				registered, before, after, after - before,
				PowerConfigStore.get().tracker().scanIntervalTicks()
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutExistsOnlyInsideTheRadius(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ownerAt(helper, RADIUS_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob near = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());
			Mob far = spawnStill(helper, owner.getX() + FAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout nearReadout = ReadoutService.owner(owner).get(near.getUUID());
				boolean farPresent = ReadoutService.owner(owner).get(far.getUUID()) != null;
				double nearDistance = horizontalDistance(owner, near);
				double farDistance = horizontalDistance(owner, far);
				int spawnLines = linesFor(SPAWN_LINE, near.getUUID());
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutRadius RESULT nearDistance={} farDistance={} radius={} nearPresent={} farPresent={} spawnLines={} nearInLevel={} readouts={} text={}",
					nearDistance, farDistance, config.readout().radius(), nearReadout != null, farPresent,
					spawnLines, level.getEntity(near.getId()) == near, ReadoutService.owner(owner).size(),
					nearReadout == null ? "none" : nearReadout.text()
				);
				helper.assertTrue(
					nearReadout != null,
					"a mob " + nearDistance + " blocks from its owner carried no readout; entries="
						+ ReadoutService.owner(owner).size()
				);
				helper.assertTrue(
					!farPresent,
					"a mob " + farDistance + " blocks from its owner carried a readout; radius="
						+ config.readout().radius()
				);
				helper.assertTrue(
					nearDistance <= config.readout().radius() && farDistance > config.readout().radius(),
					"the two mobs sat at " + nearDistance + " and " + farDistance + " blocks against a radius of "
						+ config.readout().radius()
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aMobThatLeavesTheRadiusLosesItsReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, RADIUS_RANGE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				boolean present = ReadoutService.owner(owner).get(mob.getUUID()) != null;
				helper.assertTrue(present, "the readout never spawned, so the range removal could not be measured");
				owner.setPos(owner.getX() + FAR_DISTANCE * 2.0, owner.getY(), owner.getZ());
				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					boolean stillPresent = ReadoutService.owner(owner).get(mob.getUUID()) != null;
					double distance = horizontalDistance(owner, mob);
					helper.assertTrue(
						!stillPresent,
						"a readout survived at " + distance + " blocks against a radius of "
							+ config.readout().radius()
					);
					helper.assertTrue(
						distance > config.readout().radius(),
						"the owner walked to " + distance + " blocks, inside the radius of "
							+ config.readout().radius()
					);
					helper.assertTrue(
						linesFor(REMOVE_LINE, mob.getUUID()) == 1
							&& lastFor(REMOVE_LINE, mob.getUUID()).contains("reason=range"),
						"the range removal logged '" + lastFor(REMOVE_LINE, mob.getUUID())
							+ "' instead of one line naming reason=range"
					);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutRangeRemoval RESULT distance={} radius={} present={} removeLines={} line={}",
						distance, config.readout().radius(), stillPresent,
						linesFor(REMOVE_LINE, mob.getUUID()), lastFor(REMOVE_LINE, mob.getUUID())
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theCapKeepsTheNearestFortyOfAHundredCandidates(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, CAP_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			List<Mob> offered = new ArrayList<>();

			for (int i = 0; i < CANDIDATE_MOBS; i++) {
				double radius = SPIRAL_FIRST_RADIUS + SPIRAL_RADIUS_STEP * i;
				double angle = GOLDEN_ANGLE * i;
				offered.add(
					spawnStill(
						helper,
						owner.getX() + Math.cos(angle) * radius,
						owner.getY(),
						owner.getZ() + Math.sin(angle) * radius
					)
				);
			}

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner state = ReadoutService.owner(owner);
				int missing = 0;
				int outOfOrder = 0;
				int outOfRadius = 0;
				double previous = -1.0;

				for (int i = 0; i < EXPECTED_CAP; i++) {
					double distance = horizontalDistance(owner, offered.get(i));

					if (distance <= previous) {
						outOfOrder++;
					}

					previous = distance;

					if (state.get(offered.get(i).getUUID()) == null) {
						missing++;
					}

					if (distance > config.readout().radius()) {
						outOfRadius++;
					}
				}

				Mob firstDropped = offered.get(EXPECTED_CAP);
				double droppedDistance = horizontalDistance(owner, firstDropped);
				double keptDistance = horizontalDistance(owner, offered.get(EXPECTED_CAP - 1));
				int beyondEdge = 0;

				for (Mob mob : offered) {
					if (horizontalDistance(owner, mob) > keptDistance && state.get(mob.getUUID()) != null) {
						beyondEdge++;
					}
				}

				int spawnLines = linesForAny(SPAWN_LINE, offered);
				helper.assertTrue(
					outOfOrder == 0 && outOfRadius == 0,
					"the candidate spiral is not ordered by distance: outOfOrder=" + outOfOrder
						+ " outsideRadius=" + outOfRadius + " lastKept=" + keptDistance
				);
				helper.assertTrue(
					state.size() == EXPECTED_CAP,
					CANDIDATE_MOBS + " candidate mobs produced " + state.size() + " readouts against a cap of "
						+ config.readout().maxPerPlayer()
				);
				helper.assertTrue(
					missing == 0,
					missing + " of the " + EXPECTED_CAP + " nearest mobs carried no readout"
				);
				helper.assertTrue(
					state.get(firstDropped.getUUID()) == null,
					"the " + (EXPECTED_CAP + 1) + "th nearest mob at " + droppedDistance + " blocks kept a readout"
				);
				helper.assertTrue(
					beyondEdge == 0,
					beyondEdge + " mobs farther than the kept edge at " + keptDistance + " blocks held a readout"
				);
				helper.assertTrue(
					spawnLines == EXPECTED_CAP,
					"the cap produced " + spawnLines + " spawn lines instead of " + EXPECTED_CAP
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutCap RESULT offered={} cap={} held={} nearestMissing={} keepDistance={} droppedDistance={} beyondEdge={} spawnLines={}",
					CANDIDATE_MOBS, config.readout().maxPerPlayer(), state.size(), missing, keptDistance,
					droppedDistance, beyondEdge, spawnLines
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void oneOwnerLevelStepMovesTheBandAndSendsOneUpdate(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, BAND_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX(), owner.getY(), owner.getZ());
			owner.experienceLevel = comparedThreat(mob, config) - BAND_TEST_START_GAP;

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout before = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(before != null, "the band test spawned no readout, so no band could change");
				int mobLevel = comparedThreat(mob, config);
				int levelBefore = PowerTracker.playerLevel(owner, config);
				int gapBefore = mobLevel - levelBefore;
				PowerCurve.Band bandBefore = PowerCurve.band(gapBefore, config);
				helper.assertTrue(
					gapBefore == BAND_TEST_START_GAP,
					"the arrangement produced a gap of " + gapBefore + " instead of " + BAND_TEST_START_GAP
						+ "; mobLevel=" + mobLevel + " playerLevel=" + levelBefore
				);
				helper.assertTrue(
					bandBefore == PowerCurve.Band.GREEN,
					"gap " + gapBefore + " read as " + bandBefore + " instead of GREEN"
				);
				helper.assertTrue(
					before.band() == PowerCurve.Band.GREEN,
					"the readout carried band " + before.band() + " at gap " + gapBefore
				);
				owner.experienceLevel = levelBefore + BAND_TEST_START_GAP - BAND_TEST_END_GAP;
				int dataBefore = PacketRecorder.of(owner).dataPackets(before.display().getId());

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					ReadoutOwner.Readout after = ReadoutService.owner(owner).get(mob.getUUID());
					helper.assertTrue(after != null, "the readout vanished during the band change");
					int levelAfter = PowerTracker.playerLevel(owner, config);
					int gapAfter = comparedThreat(mob, config) - levelAfter;
					int dataSent = PacketRecorder.of(owner).dataPackets(after.display().getId()) - dataBefore;
					int bandLines = linesFor(BAND_LINE, mob.getUUID());
					helper.assertTrue(
						gapAfter == BAND_TEST_END_GAP,
						"the owner level moved the gap to " + gapAfter + " instead of " + BAND_TEST_END_GAP
							+ "; playerLevel=" + levelAfter
					);
					helper.assertTrue(
						after.band() == PowerCurve.Band.WHITE,
						"gap " + gapAfter + " left the readout at " + after.band() + " instead of WHITE"
					);
					helper.assertTrue(
						dataSent == 1,
						"one band change sent " + dataSent + " data packets instead of one"
					);
					helper.assertTrue(
						bandLines == 1,
						"one band change wrote " + bandLines + " band lines instead of one"
					);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutBandChange RESULT levelBefore={} gapBefore={} bandBefore={} colorBefore={} levelAfter={} gapAfter={} bandAfter={} colorAfter={} dataPackets={} bandLines={} line={}",
						levelBefore, gapBefore, bandBefore, ReadoutText.colorName(bandBefore), levelAfter, gapAfter,
						after.band(), ReadoutText.colorName(after.band()), dataSent, bandLines,
						lastFor(BAND_LINE, mob.getUUID())
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void anUnchangedBandSendsNoPacket(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, BAND_TEST_UNCHANGED_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX(), owner.getY(), owner.getZ());
			owner.experienceLevel = comparedThreat(mob, config);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout first = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(first != null, "the unchanged band test spawned no readout");
				int levelBefore = PowerTracker.playerLevel(owner, config);
				int gap = comparedThreat(mob, config) - levelBefore;
				owner.experienceLevel = levelBefore + 1;
				int packetsBefore = PacketRecorder.of(owner).entityPackets(Set.of(first.display().getId()));
				int bandLines = linesFor(BAND_LINE, mob.getUUID());

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					ReadoutOwner.Readout second = ReadoutService.owner(owner).get(mob.getUUID());
					int packetsAfter = PacketRecorder.of(owner).entityPackets(Set.of(first.display().getId()));
					int newBandLines = linesFor(BAND_LINE, mob.getUUID());
					int levelAfter = PowerTracker.playerLevel(owner, config);
					PowerCurve.Band bandAfter = PowerCurve.band(
						comparedThreat(mob, config) - levelAfter, config
					);
					helper.assertTrue(second != null, "the readout vanished on an unchanged band");
					helper.assertTrue(
						levelAfter == levelBefore + 1,
						"the owner level moved from " + levelBefore + " to " + levelAfter + " instead of one step"
					);
					helper.assertTrue(
						bandAfter == first.band(),
						"one owner level step crossed from " + first.band() + " to " + bandAfter + " at gap " + gap
					);
					helper.assertTrue(
						packetsAfter == packetsBefore,
						"an unchanged band sent " + (packetsAfter - packetsBefore) + " packets"
					);
					helper.assertTrue(
						newBandLines == bandLines,
						"an unchanged band wrote " + (newBandLines - bandLines) + " band lines"
					);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutNoUpdate RESULT gap={} levelBefore={} levelAfter={} band={} packetsBefore={} packetsAfter={} bandLines={} text={}",
						gap, levelBefore, levelAfter, bandAfter, packetsBefore, packetsAfter, newBandLines,
						second.text()
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void mobReadoutShowsItsLevelNameAndHealth(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, TEXT_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout readout = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(readout != null, "the text test spawned no readout");
				int mobLevel = PowerTracker.threat(mob, config);
				String health = ReadoutText.shown(mob.getHealth()) + "/" + ReadoutText.shown(mob.getMaxHealth());
				String expected = "Threat " + mobLevel + " " + mob.getDisplayName().getString() + " " + health;
				helper.assertTrue(
					expected.equals(readout.text()),
					"the readout read '" + readout.text() + "' instead of '" + expected + "'"
				);
				helper.assertTrue(
					readout.threat() == mobLevel,
					"the readout named threat " + readout.threat() + " for a raw threat of " + mobLevel
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutTextOnMob RESULT mob={} mobLevel={} displayName={} text={} band={} color={}",
					mob.getType(), mobLevel, mob.getDisplayName().getString(), readout.text(), readout.band(),
					ReadoutText.colorName(readout.band())
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void allMobReadoutsShowTheirHealth(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, HEALTH_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob looked = spawnStill(helper, owner.getX(), owner.getY(), owner.getZ() + TARGET_DISTANCE);
			Mob aside = spawnStill(helper, owner.getX() + TARGET_DISTANCE, owner.getY(), owner.getZ());
			owner.setYRot(0.0F);
			owner.setXRot(0.0F);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout lookedReadout = ReadoutService.owner(owner).get(looked.getUUID());
				ReadoutOwner.Readout asideReadout = ReadoutService.owner(owner).get(aside.getUUID());
				helper.assertTrue(lookedReadout != null, "the mob under the crosshair holds no readout");
				helper.assertTrue(asideReadout != null, "the mob beside the crosshair holds no readout");
				String lookedHealth = ReadoutText.shown(looked.getHealth()) + "/" + ReadoutText.shown(looked.getMaxHealth());
				String asideHealth = ReadoutText.shown(aside.getHealth()) + "/" + ReadoutText.shown(aside.getMaxHealth());
				String expectedLooked = "Threat " + PowerTracker.threat(looked, config) + " "
					+ looked.getDisplayName().getString() + " " + lookedHealth;
				String expectedAside = "Threat " + PowerTracker.threat(aside, config) + " "
					+ aside.getDisplayName().getString() + " " + asideHealth;
				helper.assertTrue(
					expectedLooked.equals(lookedReadout.text()),
					"the targeted mob read '" + lookedReadout.text() + "' instead of '" + expectedLooked + "'"
				);
				helper.assertTrue(
					expectedAside.equals(asideReadout.text()),
					"the bystander mob read '" + asideReadout.text() + "' instead of '" + expectedAside + "'"
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutHealth RESULT lookedText={} asideText={} distance={} reach={}",
					lookedReadout.text(), asideReadout.text(), TARGET_DISTANCE,
					owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aTargetedMobHealthChangeMovesTheReadoutText(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ownerAt(helper, HEALTH_MOVE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob looked = spawnStill(helper, owner.getX(), owner.getY(), owner.getZ() + TARGET_DISTANCE);
			Mob aside = spawnStill(helper, owner.getX() + TARGET_DISTANCE, owner.getY(), owner.getZ());
			owner.setYRot(0.0F);
			owner.setXRot(0.0F);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout before = ReadoutService.owner(owner).get(looked.getUUID());
				ReadoutOwner.Readout asideBefore = ReadoutService.owner(owner).get(aside.getUUID());
				helper.assertTrue(before != null, "the targeted mob held no readout before damage");
				helper.assertTrue(asideBefore != null, "the bystander held no readout before damage");
				int lookedDisplay = before.display().getId();
				int asideDisplay = asideBefore.display().getId();
				int lookedPacketsBefore = PacketRecorder.of(owner).dataPackets(lookedDisplay);
				int asidePacketsBefore = PacketRecorder.of(owner).dataPackets(asideDisplay);
				float lookedHealthBefore = looked.getHealth();
				float asideHealthBefore = aside.getHealth();
				looked.hurtServer(level, level.damageSources().generic(), HEALTH_DAMAGE);
				float lookedHealthAfter = looked.getHealth();

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					ReadoutOwner.Readout lookedAfter = ReadoutService.owner(owner).get(looked.getUUID());
					ReadoutOwner.Readout asideAfter = ReadoutService.owner(owner).get(aside.getUUID());
					helper.assertTrue(lookedAfter != null, "the targeted mob lost its readout after damage");
					helper.assertTrue(asideAfter != null, "the bystander lost its readout after target damage");
					String lookedHealth = ReadoutText.shown(lookedHealthAfter) + "/"
						+ ReadoutText.shown(looked.getMaxHealth());
					String expectedLooked = "Threat " + PowerTracker.threat(looked, config) + " "
						+ looked.getDisplayName().getString() + " " + lookedHealth;
					int lookedPacketsAfter = PacketRecorder.of(owner).dataPackets(lookedDisplay);
					int asidePacketsAfter = PacketRecorder.of(owner).dataPackets(asideDisplay);
					int lookedPacketsFirst = lookedPacketsAfter - lookedPacketsBefore;
					int asidePacketsFirst = asidePacketsAfter - asidePacketsBefore;
					boolean targetPhase = lookedHealthAfter < lookedHealthBefore
						&& aside.getHealth() == asideHealthBefore
						&& expectedLooked.equals(lookedAfter.text())
						&& asideBefore.text().equals(asideAfter.text())
						&& lookedPacketsFirst == 1 && asidePacketsFirst == 0;

					int lookedPacketsBeforeBystander = lookedPacketsAfter;
					int asidePacketsBeforeBystander = asidePacketsAfter;
					float lookedHealthBeforeBystander = looked.getHealth();
					float asideHealthBeforeBystander = aside.getHealth();
					aside.hurtServer(level, level.damageSources().generic(), HEALTH_DAMAGE);
					float asideHealthAfter = aside.getHealth();

					helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
						ReadoutOwner.Readout lookedFinal = ReadoutService.owner(owner).get(looked.getUUID());
						ReadoutOwner.Readout asideFinal = ReadoutService.owner(owner).get(aside.getUUID());
						helper.assertTrue(lookedFinal != null, "the targeted mob lost its readout after bystander damage");
						helper.assertTrue(asideFinal != null, "the bystander lost its readout after its damage");
						String expectedAsideAfter = "Threat " + PowerTracker.threat(aside, config) + " "
							+ aside.getDisplayName().getString() + " " + ReadoutText.shown(asideHealthAfter) + "/"
							+ ReadoutText.shown(aside.getMaxHealth());
						int lookedPacketsSecond = PacketRecorder.of(owner).dataPackets(lookedDisplay)
							- lookedPacketsBeforeBystander;
						int asidePacketsSecond = PacketRecorder.of(owner).dataPackets(asideDisplay)
							- asidePacketsBeforeBystander;
						boolean bystanderPhase = asideHealthAfter < asideHealthBeforeBystander
							&& looked.getHealth() == lookedHealthBeforeBystander
							&& expectedLooked.equals(lookedFinal.text())
							&& expectedAsideAfter.equals(asideFinal.text())
							&& lookedPacketsSecond == 0 && asidePacketsSecond == 1;
						StellaEquips.LOGGER.info(
							"[BEHAVIOR] test=readoutHealthMove RESULT targetPhase={} bystanderPhase={} "
								+ "targetUpdatesFirst={} bystanderUpdatesFirst={} targetUpdatesSecond={} "
								+ "bystanderUpdatesSecond={} targetText={} bystanderText={}",
							targetPhase, bystanderPhase, lookedPacketsFirst, asidePacketsFirst,
							lookedPacketsSecond, asidePacketsSecond, lookedFinal.text(), asideFinal.text()
						);
						helper.assertTrue(
							targetPhase && bystanderPhase,
							"target-only phase passed=" + targetPhase + "; bystander-only phase passed=" + bystanderPhase
						);
						helper.succeed();
					});
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aDeadMobLosesItsReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ownerAt(helper, DEATH_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout readout = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(readout != null, "the death test spawned no readout");
				mob.hurtServer(level, level.damageSources().generic(), LETHAL_DAMAGE);
				helper.assertTrue(
					!mob.isAlive() || mob.isDeadOrDying(),
					"the mob survived " + LETHAL_DAMAGE + " of generic damage at " + mob.getHealth() + " health"
				);
				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					boolean present = ReadoutService.owner(owner).get(mob.getUUID()) != null;
					int removeLines = linesFor(REMOVE_LINE, mob.getUUID());
					int removals = PacketRecorder.of(owner).removePackets(readout.display().getId());
					helper.assertTrue(!present, "a readout survived its mob's death");
					helper.assertTrue(
						removeLines == 1,
						"one mob death wrote " + removeLines + " removal lines instead of one"
					);
					helper.assertTrue(
						removals == 1,
						"the owner received " + removals + " remove packets instead of one"
					);
					helper.assertTrue(
						lastFor(REMOVE_LINE, mob.getUUID()).contains("reason=dead"),
						"the death removal logged '" + lastFor(REMOVE_LINE, mob.getUUID())
							+ "' instead of one line naming reason=dead"
					);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutMobDeath RESULT mobHealth={} alive={} present={} removeLines={} removePackets={} line={}",
						mob.getHealth(), mob.isAlive(), present, removeLines, removals,
						lastFor(REMOVE_LINE, mob.getUUID())
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aDisconnectedOwnerLosesEveryReadout(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerLevel level = helper.getLevel();
		ServerPlayer leaving = ownerAt(helper, LEAK_TEST_X);
		ServerPlayer staying = ownerAt(helper, LEAK_TEST_X);

		whenAreaLoaded(helper, leaving, () -> {
			spawnStill(helper, leaving.getX() + NEAR_DISTANCE, leaving.getY(), leaving.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner before = ReadoutService.online(leaving.getUUID());
				int leavingBefore = before == null ? 0 : before.size();
				ReadoutOwner stayingState = ReadoutService.online(staying.getUUID());
				int stayingBefore = stayingState == null ? 0 : stayingState.size();
				helper.assertTrue(leavingBefore > 0, "the leaving player held no readouts before the disconnect");
				helper.assertTrue(stayingBefore > 0, "the staying player held no readouts before the disconnect");
				level.getServer().getPlayerList().remove(leaving);
				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					ReadoutOwner after = ReadoutService.online(leaving.getUUID());
					ReadoutOwner stayingAfterState = ReadoutService.online(staying.getUUID());
					int stayingAfter = stayingAfterState == null ? 0 : stayingAfterState.size();
					boolean online = level.getServer().getPlayerList().getPlayers().contains(leaving);
					helper.assertTrue(!online, "the leaving player stayed in the player list after the removal");
					helper.assertTrue(
						after == null,
						"the disconnected player kept a readout entry of "
							+ (after == null ? 0 : after.size()) + " from " + leavingBefore
					);
					helper.assertTrue(
						stayingAfter == stayingBefore,
						"the disconnect cleared the other player from " + stayingBefore + " to " + stayingAfter
							+ " readouts"
					);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutDisconnect RESULT leavingBefore={} leavingEntryAfter={} stayingBefore={} stayingAfter={} leavingOnline={}",
						leavingBefore, after == null ? "none" : "held", stayingBefore, stayingAfter, online
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutIsNotDamageable(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ownerAt(helper, DAMAGE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout readout = firstReadout(ReadoutService.owner(owner));
				helper.assertTrue(readout != null, "the damage test spawned no readout");
				Display.TextDisplay display = readout.display();
				boolean hurt = display.hurtServer(level, level.damageSources().generic(), READOUT_DAMAGE);
				boolean removed = display.isRemoved();
				helper.assertTrue(
					!hurt,
					"a readout took " + READOUT_DAMAGE + " of damage and reported hurt=" + hurt
				);
				helper.assertTrue(!removed, "a readout was removed by damage");
				helper.assertTrue(
					ReadoutService.owner(owner).get(readout.mobId()) != null,
					"the damaged readout left its owner's set"
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutNotDamageable RESULT display={} damage={} hurt={} removed={} viewRange={}",
					display.getType(), READOUT_DAMAGE, hurt, removed, display.getViewRange()
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutNeverReachesAnotherPlayer(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ownerAt(helper, LEAK_PRIVATE_TEST_X);
		ServerPlayer bystander = ownerAt(helper, LEAK_PRIVATE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			PacketRecorder ownerWire = PacketRecorder.of(owner);
			PacketRecorder bystanderWire = PacketRecorder.of(bystander);
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				Set<Integer> ownerIds = new LinkedHashSet<>();

				for (ReadoutOwner.Readout readout : ReadoutService.owner(owner).readouts().values()) {
					ownerIds.add(readout.display().getId());
				}

				helper.assertTrue(!ownerIds.isEmpty(), "the leak test spawned no readout for the owner");
				helper.assertTrue(
					ReadoutService.owner(bystander).get(mob.getUUID()) != null,
					"the bystander carried no readout of its own, so the overlap was not measured"
				);
				int missingAdds = 0;
				int inLevel = 0;

				for (int id : ownerIds) {
					if (ownerWire.addPackets(id) < 1) {
						missingAdds++;
					}

					if (level.getEntity(id) != null) {
						inLevel++;
					}
				}

				int leaked = bystanderWire.entityPackets(ownerIds);
				int bystanderReadouts = ReadoutService.owner(bystander).size();
				helper.assertTrue(
					missingAdds == 0,
					missingAdds + " of the owner's " + ownerIds.size() + " readouts reached no add packet"
				);
				helper.assertTrue(
					leaked == 0,
					leaked + " packets for the owner's readout entities reached the other player"
				);
				helper.assertTrue(
					inLevel == 0,
					inLevel + " readout entities sit in the level, where the tracking path would broadcast them"
				);
				int sharedMobPackets = bystanderWire.addPackets(mob.getId());
				Set<Integer> bystanderIds = new LinkedHashSet<>();

				for (ReadoutOwner.Readout readout : ReadoutService.owner(bystander).readouts().values()) {
					bystanderIds.add(readout.display().getId());
				}

				int bystanderOwnAdds = 0;

				for (int id : bystanderIds) {
					bystanderOwnAdds += bystanderWire.addPackets(id) >= 1 ? 1 : 0;
				}

				boolean disjoint = true;

				for (int id : bystanderIds) {
					if (ownerIds.contains(id)) {
						disjoint = false;
					}
				}

				helper.assertTrue(
					!bystanderIds.isEmpty() && bystanderOwnAdds == bystanderIds.size(),
					"the other player's own " + bystanderIds.size() + " readouts reached it in only "
						+ bystanderOwnAdds + " add packets, so its channel was not proven live and a leak count of "
						+ leaked + " proves nothing"
				);
				helper.assertTrue(
					disjoint,
					"the two players shared a readout entity id, so the leak count was meaningless"
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutPrivate RESULT ownerReadouts={} ownerIds={} missingAdds={} bystanderReadouts={} bystanderOwnAdds={} leakedPackets={} readoutsInLevel={} sharedMobPackets={} bystanderSawIds={}",
					ownerIds.size(), ownerIds, missingAdds, bystanderReadouts, bystanderOwnAdds, leaked, inLevel,
					sharedMobPackets, bystanderWire.entityIdsSeen()
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theOptOutCommandClearsTheReadouts(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = ownerAt(helper, OPTOUT_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				int before = ReadoutService.owner(owner).size();
				helper.assertTrue(before > 0, "the opt out test spawned no readout to clear");
				Set<Integer> ids = new LinkedHashSet<>();

				for (ReadoutOwner.Readout readout : ReadoutService.owner(owner).readouts().values()) {
					ids.add(readout.display().getId());
				}

				owner.level().getServer().getCommands().performPrefixedCommand(
					owner.createCommandSourceStack(), "stellaequips readout off"
				);
				int afterCommand = ReadoutService.owner(owner).size();
				int removedPackets = removePacketsFor(owner, ids);
				boolean enabled = ReadoutService.owner(owner).enabled();
				helper.assertTrue(
					afterCommand == 0,
					"the opt out command left " + afterCommand + " readouts of " + before
				);
				helper.assertTrue(!enabled, "the opt out command left the readout enabled for the player");
				helper.assertTrue(
					removedPackets >= ids.size(),
					"the opt out command sent " + removedPackets + " remove packets for " + ids.size() + " readouts"
				);
				helper.assertTrue(
					lastFor(REMOVE_LINE, mob.getUUID()).contains("reason=off"),
					"the opt out logged '" + lastFor(REMOVE_LINE, mob.getUUID())
						+ "' instead of one line naming reason=off"
				);
				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					int afterScan = ReadoutService.owner(owner).size();
					helper.assertTrue(
						afterScan == 0,
						"the opt out did not hold: " + afterScan + " readouts came back after a scan"
					);
					helper.assertTrue(
						ReadoutService.owner(owner).get(mob.getUUID()) == null,
						"the opted out player still carried a readout for the mob"
					);
					owner.level().getServer().getCommands().performPrefixedCommand(
						owner.createCommandSourceStack(), "stellaequips readout on"
					);
					helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
						int afterOn = ReadoutService.owner(owner).size();
						helper.assertTrue(
							ReadoutService.owner(owner).enabled(),
							"the opt in command left the readout disabled"
						);
						helper.assertTrue(
							afterOn == before,
							"the opt in restored " + afterOn + " readouts instead of " + before
						);
						StellaEquips.LOGGER.info(
							"[BEHAVIOR] test=readoutOptOut RESULT before={} afterCommand={} afterScan={} afterOptIn={} enabled={} removePackets={} line={}",
							before, afterCommand, afterScan, afterOn, enabled, removedPackets,
							lastFor(REMOVE_LINE, mob.getUUID())
						);
						helper.succeed();
					});
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutLeavesTheMobNameTagAlone(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, TEXT_TEST_X);
		Component tag = Component.literal("Bessie");

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());
			mob.setCustomName(tag);
			mob.setCustomNameVisible(true);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout readout = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(readout != null, "the name tag test spawned no readout");
				String kept = mob.getCustomName() == null ? "none" : mob.getCustomName().getString();
				int mobLevel = PowerTracker.threat(mob, config);
				String health = ReadoutText.shown(mob.getHealth()) + "/" + ReadoutText.shown(mob.getMaxHealth());
				String expected = "Threat " + mobLevel + " Bessie " + health;
				helper.assertTrue(
					"Bessie".equals(kept),
					"the readout replaced the mob name tag with '" + kept + "'"
				);
				helper.assertTrue(mob.isCustomNameVisible(), "the readout hid the mob name tag");
				helper.assertTrue(
					expected.equals(readout.text()),
					"the readout read '" + readout.text() + "' instead of '" + expected + "'"
				);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutNameTag RESULT tag={} tagVisible={} mobLevel={} text={}",
					kept, mob.isCustomNameVisible(), mobLevel, readout.text()
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aReadoutFollowsItsMobWhenTheMobWalks(final GameTestHelper helper) {
		PowerRecorder.install();
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, FOLLOW_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnWalker(helper, owner);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout start = ReadoutService.owner(owner).get(mob.getUUID());
				helper.assertTrue(start != null, "the follow test spawned no readout");
				double startX = mob.getX();
				double startZ = mob.getZ();
				double startLag = horizontalLag(start.display(), mob);
				helper.assertTrue(
					startLag <= LAG_TOLERANCE,
					"the readout sat " + startLag + " blocks from its mob before the walk"
				);
				boolean ordered = mob.getNavigation().moveTo(
					mob.getX() + WALK_DISTANCE, mob.getY(), mob.getZ(), WALK_SPEED
				);
				helper.assertTrue(
					ordered,
					"the mob refused the walk order to " + (mob.getX() + WALK_DISTANCE) + ", so nothing was followed"
				);
				waitForTheWalk(helper, owner, mob, startX, startZ, start.display().getId(), 0);
			});
		});
	}

	private static void waitForTheWalk(
		final GameTestHelper helper,
		final ServerPlayer owner,
		final Mob mob,
		final double startX,
		final double startZ,
		final int displayId,
		final int waited
	) {
		helper.runAfterDelay(WALK_POLL_TICKS, () -> {
			double walked = Math.hypot(mob.getX() - startX, mob.getZ() - startZ);

			if (walked < WALK_MIN && waited < WALK_DEADLINE_TICKS) {
				mob.getNavigation().moveTo(mob.getX() + WALK_DISTANCE, mob.getY(), mob.getZ(), WALK_SPEED);
				waitForTheWalk(helper, owner, mob, startX, startZ, displayId, waited + WALK_POLL_TICKS);
				return;
			}

			ReadoutOwner.Readout readout = ReadoutService.owner(owner).get(mob.getUUID());
			helper.assertTrue(readout != null, "the readout vanished while its mob walked");
			Display.TextDisplay display = readout.display();
			double lag = horizontalLag(display, mob);
			double height = mob.getY() + mob.getBbHeight() + PowerConfigStore.get().readout().heightOffset();
			double heightError = Math.abs(display.getY() - height);
			int moves = PacketRecorder.of(owner).teleportPackets(displayId);
			helper.assertTrue(
				walked >= WALK_MIN,
				"the mob walked only " + walked + " blocks in " + (waited + WALK_POLL_TICKS)
					+ " ticks, so the follow could not be measured"
			);
			helper.assertTrue(
				lag <= LAG_TOLERANCE,
				"the mob walked " + walked + " blocks and left its readout " + lag + " blocks behind"
			);
			helper.assertTrue(
				heightError <= LAG_TOLERANCE,
				"the readout sat " + heightError + " blocks off the mob's head height of " + height
			);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutFollowsMob RESULT walked={} mobX={} mobZ={} displayX={} displayZ={} lag={} heightError={} movePackets={} ticks={} text={}",
				walked, mob.getX(), mob.getZ(), display.getX(), display.getZ(), lag, heightError, moves,
				waited + WALK_POLL_TICKS, readout.text()
			);
			helper.succeed();
		});
	}

	private static double horizontalLag(final Display.TextDisplay display, final Mob mob) {
		return Math.hypot(display.getX() - mob.getX(), display.getZ() - mob.getZ());
	}

	@GameTest
	public void theReadoutConfigKeysParse(final GameTestHelper helper) throws IOException {
		Path file = Files.createTempDirectory("readout-config").resolve(PowerConfigStore.FILE_NAME);
		Files.writeString(file, stagedDocument(), StandardCharsets.UTF_8);
		PowerConfig loaded = PowerConfigStore.loadFrom(file);
		PowerConfigStore.load();
		helper.assertTrue(
			loaded.readout().radius() == STAGED_RADIUS,
			"the staged radius of " + STAGED_RADIUS + " loaded as " + loaded.readout().radius()
		);
		helper.assertTrue(
			loaded.readout().maxPerPlayer() == STAGED_CAP,
			"the staged cap of " + STAGED_CAP + " loaded as " + loaded.readout().maxPerPlayer()
		);
		helper.assertTrue(
			loaded.readout().heightOffset() == STAGED_HEIGHT_OFFSET,
			"the staged height offset of " + STAGED_HEIGHT_OFFSET + " loaded as "
				+ loaded.readout().heightOffset()
		);
		helper.assertTrue(
			loaded.readout().viewRange() == STAGED_VIEW_RANGE,
			"the staged view range of " + STAGED_VIEW_RANGE + " loaded as " + loaded.readout().viewRange()
		);
		helper.assertTrue(
			!loaded.readout().enabledByDefault(),
			"the staged disabled default loaded as " + loaded.readout().enabledByDefault()
		);
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
	public void theReadoutConfigKeysDriveTheScan(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = ownerAt(helper, CONFIG_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			List<Mob> near = new ArrayList<>();

			for (int i = 0; i < 5; i++) {
				near.add(spawnStill(helper, owner.getX() + 5.0 + i * 3.0, owner.getY(), owner.getZ()));
			}

			Mob beyond = spawnStill(helper, owner.getX() + STAGED_RADIUS + 5.0, owner.getY(), owner.getZ());
			Path file = stageDocument();
			PowerConfig staged = PowerConfigStore.loadFrom(file);
			ServerPlayer stagedOwner = ownerAt(helper, CONFIG_TEST_X);
			ReadoutService.scanPlayer(stagedOwner, staged);
			ReadoutOwner ownerState = ReadoutService.owner(stagedOwner);
			int held = ownerState.size();
			boolean beyondPresent = ownerState.get(beyond.getUUID()) != null;
			int viewRangeWrong = 0;
			int heightWrong = 0;
			double firstHeight = 0.0;

			for (ReadoutOwner.Readout readout : ownerState.readouts().values()) {
				if (readout.display().getViewRange() != (float) STAGED_VIEW_RANGE) {
					viewRangeWrong++;
				}
			}

			for (Mob mob : near) {
				ReadoutOwner.Readout readout = ownerState.get(mob.getUUID());

				if (readout == null) {
					continue;
				}

				firstHeight = readout.display().getY();
				double expected = mob.getY() + mob.getBbHeight() + STAGED_HEIGHT_OFFSET;

				if (Math.abs(firstHeight - expected) > LAG_TOLERANCE) {
					heightWrong++;
				}
			}

			PowerConfig disabledConfig = PowerConfigStore.loadFrom(stageDocument(false));
			ServerPlayer disabledOwner = ownerAt(helper, CONFIG_TEST_X);
			ReadoutService.scanPlayer(disabledOwner, disabledConfig);
			int disabledHeld = ReadoutService.owner(disabledOwner).size();
			PowerConfigStore.load();
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutConfigDrives RESULT stagedRadius={} stagedCap={} candidates={} held={} beyondPresent={} viewRangeWrong={} heightWrong={} firstHeight={} enabledByDefault={} disabledHeld={}",
				staged.readout().radius(), staged.readout().maxPerPlayer(), near.size(), held, beyondPresent,
				viewRangeWrong, heightWrong, firstHeight, disabledConfig.readout().enabledByDefault(), disabledHeld
			);
			helper.assertTrue(
				held == STAGED_CAP,
				near.size() + " candidates under a staged cap of " + STAGED_CAP + " produced " + held + " readouts"
			);
			helper.assertTrue(
				!beyondPresent,
				"a mob at " + (STAGED_RADIUS + 5.0) + " blocks carried a readout under a staged radius of "
					+ STAGED_RADIUS
			);
			helper.assertTrue(
				viewRangeWrong == 0,
				viewRangeWrong + " readouts ignored the staged view range of " + STAGED_VIEW_RANGE
			);
			helper.assertTrue(
				heightWrong == 0,
				heightWrong + " readouts ignored the staged height offset of " + STAGED_HEIGHT_OFFSET
			);
			helper.assertTrue(
				disabledHeld == 0,
				"a staged enabled_by_default of false still produced " + disabledHeld + " readouts"
			);
			helper.succeed();
		});
	}

	private static Path stageDocument() {
		return stageDocument(true);
	}

	private static Path stageDocument(final boolean enabledByDefault) {		try {
			Path file = Files.createTempDirectory("readout-staged").resolve(PowerConfigStore.FILE_NAME);
			Files.writeString(file, stagedDocument(enabledByDefault), StandardCharsets.UTF_8);
			return file;
		} catch (IOException e) {
			throw new IllegalStateException("could not stage the readout document: " + e.getMessage(), e);
		}
	}

	private static String stagedDocument() {
		return stagedDocument(false);
	}

	private static String stagedDocument(final boolean enabledByDefault) {
		return PowerConfigStore.DEFAULT_JSON
			.replace("\"enabled_by_default\": true", "\"enabled_by_default\": " + enabledByDefault)
			.replace("\"radius\": 32", "\"radius\": " + (int) STAGED_RADIUS)
			.replace("\"max_per_player\": 40", "\"max_per_player\": " + STAGED_CAP)
			.replace("\"height_offset\": 0.9", "\"height_offset\": " + STAGED_HEIGHT_OFFSET)
			.replace("\"view_range\": 0.5", "\"view_range\": " + STAGED_VIEW_RANGE);
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void aRankChangeRebuildsTheLiveReadoutText(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = ownerAt(helper, RANK_TEST_X);
		Map<UUID, String> ranks = new ConcurrentHashMap<>();
		Function<Entity, String> provider = entity -> ranks.get(entity.getUUID());
		Object previous = FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, provider);
		List<String> texts = new ArrayList<>();

		whenAreaLoaded(helper, owner, () -> {
			Mob mob = spawnStill(helper, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ());
			ranks.put(mob.getUUID(), "normal");

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				texts.add(textOf(owner, mob));
				ranks.put(mob.getUUID(), "elite");

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					texts.add(textOf(owner, mob));
					ranks.put(mob.getUUID(), "boss");

					helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
						try {
							texts.add(textOf(owner, mob));
							boolean normalUntagged = !texts.get(0).contains("<");
							boolean eliteTagged = texts.get(1).contains("<Elite>") && !texts.get(1).contains("<Boss>");
							boolean bossTagged = texts.get(2).contains("<Boss>") && !texts.get(2).contains("<Elite>");
							StellaEquips.LOGGER.info(
								"[BEHAVIOR] test=readoutRankChange RESULT normal={} elite={} boss={} bandLines={}",
								texts.get(0), texts.get(1), texts.get(2), linesFor(BAND_LINE, mob.getUUID())
							);
							helper.assertTrue(
								normalUntagged && eliteTagged && bossTagged,
								"the rank changes normal, elite, boss read " + texts
							);
							helper.succeed();
						} finally {
							restoreProvider(previous);
						}
					});
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void enemyNameIsRedAndPassiveNameIsGreen(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, HOSTILITY_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob husk = spawnKind(helper, EntityTypes.HUSK, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ(), true);
			Mob cow = spawnStill(helper, owner.getX() - NEAR_DISTANCE, owner.getY(), owner.getZ());

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				String huskColor = nameColor(owner, husk);
				String cowColor = nameColor(owner, cow);
				StellaEquips.LOGGER.info(
					"[BEHAVIOR] test=readoutEnemyColor RESULT husk={} cow={}", huskColor, cowColor
				);
				husk.discard();
				cow.discard();
				helper.assertTrue(
					RED.equals(huskColor) && GREEN.equals(cowColor),
					"the husk name read " + huskColor + " and the cow name read " + cowColor
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void attackedGolemTurnsRedBeforeTheNextScan(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, GOLEM_HIT_TEST_X);
		int interval = liveConfig().tracker().scanIntervalTicks();
		Mob[] golem = {null};
		int[] hitAt = {-1};
		int[] redAt = {-1};

		helper.onEachTick(() -> {
			if (golem[0] == null) {
				return;
			}

			int now = ReadoutService.serverTicks();
			if (hitAt[0] < 0 && GREEN.equals(nameColor(owner, golem[0])) && now % interval == 1) {
				hitAt[0] = now;
				golem[0].hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 1.0F);
			} else if (hitAt[0] >= 0 && redAt[0] < 0 && RED.equals(nameColor(owner, golem[0]))) {
				redAt[0] = now;
			}
		});
		whenAreaLoaded(helper, owner, () -> golem[0] = spawnKind(
			helper, EntityTypes.IRON_GOLEM, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ(), false
		));
		helper.runAfterDelay(MAX_TICKS - 10, () -> {
			int delay = redAt[0] < 0 ? -1 : redAt[0] - hitAt[0];
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=readoutGolemHit RESULT hitAt={} redAt={} delay={} interval={} line={}",
				hitAt[0], redAt[0], delay, interval, lastFor("readout hostility", golem[0] == null ? new UUID(0, 0) : golem[0].getUUID())
			);
			if (golem[0] != null) {
				golem[0].discard();
			}
			helper.assertTrue(
				hitAt[0] >= 0 && delay >= 0 && delay < interval - 1,
				"the golem hit at " + hitAt[0] + " turned red after " + delay + " ticks, not before the next scan"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void secondViewerStaysGreenUntilTargeted(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer attacker = survivalOwnerAt(helper, SECOND_VIEWER_TEST_X);
		ServerPlayer bystander = survivalOwnerAt(helper, SECOND_VIEWER_TEST_X);

		whenAreaLoaded(helper, attacker, () -> {
			Mob golem = spawnKind(
				helper, EntityTypes.IRON_GOLEM, attacker.getX() + NEAR_DISTANCE, attacker.getY(), attacker.getZ(), false
			);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1.0F);

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					String attackerColor = nameColor(attacker, golem);
					String bystanderColor = nameColor(bystander, golem);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutSecondViewer RESULT attacker={} bystander={} golemTarget={}",
						attackerColor, bystanderColor, golem.getTarget() == attacker ? "attacker" : String.valueOf(golem.getTarget())
					);
					golem.discard();
					helper.assertTrue(
						RED.equals(attackerColor) && GREEN.equals(bystanderColor),
						"the attacker saw " + attackerColor + " and the bystander saw " + bystanderColor
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void targetChangeWithoutDamageTurnsRedOnScan(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, TARGET_CHANGE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob golem = spawnKind(helper, EntityTypes.IRON_GOLEM, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ(), true);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				String before = nameColor(owner, golem);
				golem.setTarget(owner);

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					String after = nameColor(owner, golem);
					StellaEquips.LOGGER.info(
						"[BEHAVIOR] test=readoutTargetChange RESULT before={} after={} golemTarget={} sameObject={} gameMode={} canAttack={} enemy={} invulnerable={} hostileNow={}",
						before, after, golem.getTarget(), golem.getTarget() == owner, owner.gameMode(), golem.canAttack(owner),
						owner.canBeSeenAsEnemy(), owner.isInvulnerable(), ReadoutService.hostile(golem, owner)
					);
					golem.discard();
					helper.assertTrue(
						GREEN.equals(before) && RED.equals(after),
						"a golem that targeted its viewer read " + before + " then " + after
					);
					helper.succeed();
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void colorOnlyChangeSendsExactlyOnePacket(final GameTestHelper helper) {
		PowerRecorder.install();
		ServerPlayer owner = survivalOwnerAt(helper, COLOR_PACKET_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			Mob golem = spawnKind(helper, EntityTypes.IRON_GOLEM, owner.getX() + NEAR_DISTANCE, owner.getY(), owner.getZ(), true);

			helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
				ReadoutOwner.Readout first = ReadoutService.owner(owner).get(golem.getUUID());
				helper.assertTrue(first != null, "the color packet test spawned no readout");
				int dataBefore = PacketRecorder.of(owner).dataPackets(first.display().getId());
				golem.setTarget(owner);

				helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
					int afterChange = PacketRecorder.of(owner).dataPackets(first.display().getId()) - dataBefore;

					helper.runAfterDelay(SCAN_WINDOW_TICKS, () -> {
						int afterRepeat = PacketRecorder.of(owner).dataPackets(first.display().getId()) - dataBefore;
						StellaEquips.LOGGER.info(
							"[BEHAVIOR] test=readoutColorPacket RESULT afterChange={} afterRepeat={} color={}",
							afterChange, afterRepeat, nameColor(owner, golem)
						);
						golem.discard();
						helper.assertTrue(
							afterChange == 1 && afterRepeat == 1,
							"a color-only change sent " + afterChange + " packets, then " + afterRepeat + " in total"
						);
						helper.succeed();
					});
				});
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void playerOverlayDisplaysItsLevelWithoutHP(final GameTestHelper helper) {
		PowerConfig config = liveConfig();
		ServerPlayer owner = ownerAt(helper, HEALTH_LINE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			PowerTracker.scanPlayer(owner, config);
			int before = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			PowerTracker.sendSelfLine(owner, config);
			int after = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			int level = PowerTracker.playerLevel(owner, config);
			String expected = "Lv " + level;
			String rendered = lastHealthLine(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=selfLineLevel RESULT text={} level={} packets={}", rendered, level, after - before
			);
			helper.assertTrue(after == before + 1, "the level line sent " + (after - before) + " packets");
			helper.assertTrue(
				expected.equals(rendered),
				"the self line read '" + rendered + "' instead of '" + expected + "'"
			);
			helper.assertTrue(
				!rendered.contains("/") && !rendered.contains("HP"),
				"the self line still contains numeric HP: '" + rendered + "'"
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void theHealthLineArrivesWithTheMobReadoutOff(final GameTestHelper helper) {
		ServerPlayer owner = ownerAt(helper, HEALTH_LINE_TEST_X);

		whenAreaLoaded(helper, owner, () -> {
			owner.level().getServer().getCommands().performPrefixedCommand(
				owner.createCommandSourceStack(), "stellaequips readout off"
			);
			boolean enabled = ReadoutService.owner(owner).enabled();
			int before = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			PowerTracker.scanPlayer(owner, liveConfig());
			PowerTracker.sendSelfLine(owner, liveConfig());
			int after = PacketRecorder.of(owner).countMatching(THE_HEALTH_LINE);
			String last = lastHealthLine(owner);
			StellaEquips.LOGGER.info(
				"[BEHAVIOR] test=selfLineOff RESULT enabled={} before={} after={} text={}",
				enabled, before, after, last
			);
			helper.assertTrue(!enabled, "the opt out command left the mob readout enabled");
			helper.assertTrue(
				after > before,
				"the tick path sent no health line to an opted out player (" + before + " to " + after + ")"
			);
			String expected = "Lv " + PowerTracker.playerLevel(owner, liveConfig());
			helper.assertTrue(
				expected.equals(last),
				"the tick path line read '" + last + "' instead of '" + expected + "'"
			);
			helper.assertTrue(
				!last.contains("/") && !last.contains("HP"),
				"the tick path line still contains numeric HP: '" + last + "'"
			);
			helper.succeed();
		});
	}

	private static String lastHealthLine(final ServerPlayer player) {
		String found = "";

		for (Packet<?> packet : PacketRecorder.of(player).snapshot()) {
			if (packet instanceof ClientboundSystemChatPacket line && line.overlay()) {
				found = line.content().getString();
			}
		}

		return found;
	}

	private static PowerConfig liveConfig() {
		PowerConfigStore.load();
		return PowerConfigStore.get();
	}

	private static ServerPlayer ownerAt(final GameTestHelper helper, final double offsetX) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos spawn = helper.getLevel().getRespawnData().pos();
		player.setPos(spawn.getX() + offsetX, spawn.getY() + 1.0, spawn.getZ());
		player.experienceLevel = 0;
		PacketRecorder.attach(player);
		forceChunks(helper.getLevel(), player.getX(), player.getZ(), true);
		return player;
	}

	/**
	 * A moved mock player loads no chunk, so the test area is forced and awaited before any mob spawns.
	 */
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

	private static Mob spawnWalker(final GameTestHelper helper, final ServerPlayer owner) {
		Mob mob = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.setPos(owner.getX() + NEAR_DISTANCE, owner.getY() + 2.0, owner.getZ());
		mob.setPersistenceRequired();
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "the level refused the walking mob");
		return mob;
	}

	private static Mob spawnStill(final GameTestHelper helper, final double x, final double y, final double z) {		Mob mob = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.setPos(x, y, z);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		helper.assertTrue(
			helper.getLevel().addFreshEntity(mob),
			"the level refused the mob at " + x + ", " + y + ", " + z
		);
		return mob;
	}

	private static double horizontalDistance(final Entity owner, final Mob mob) {
		return Math.hypot(mob.getX() - owner.getX(), mob.getZ() - owner.getZ());
	}

	private static int removePacketsFor(final ServerPlayer player, final Set<Integer> entityIds) {
		int total = 0;

		for (int id : entityIds) {
			total += PacketRecorder.of(player).removePackets(id);
		}

		return total;
	}

	private static int linesFor(final String marker, final UUID mobId) {
		String needle = "mob=" + mobId + " ";
		int found = 0;

		for (String line : PowerRecorder.matching(marker)) {
			if (line.contains(needle)) {
				found++;
			}
		}

		return found;
	}

	private static int linesForAny(final String marker, final List<Mob> mobs) {
		Set<String> needles = new LinkedHashSet<>();

		for (Mob mob : mobs) {
			needles.add("mob=" + mob.getUUID() + " ");
		}

		int found = 0;

		for (String line : PowerRecorder.matching(marker)) {
			for (String needle : needles) {
				if (line.contains(needle)) {
					found++;
					break;
				}
			}
		}

		return found;
	}

	private static String lastFor(final String marker, final UUID mobId) {
		String needle = "mob=" + mobId + " ";
		String last = "none";

		for (String line : PowerRecorder.matching(marker)) {
			if (line.contains(needle)) {
				last = line;
			}
		}

		return last;
	}

	private static String textOf(final ServerPlayer owner, final Mob mob) {
		ReadoutOwner.Readout readout = ReadoutService.owner(owner).get(mob.getUUID());
		return readout == null ? "none" : readout.text();
	}

	private static void restoreProvider(final Object previous) {
		if (previous == null) {
			FabricLoader.getInstance().getObjectShare().remove(ReadoutRank.OBJECT_SHARE_KEY);
		} else {
			FabricLoader.getInstance().getObjectShare().put(ReadoutRank.OBJECT_SHARE_KEY, previous);
		}
	}

	// The mob side of the band gap: raw threat mapped onto the player level scale.
	private static int comparedThreat(final Mob mob, final PowerConfig config) {
		return PowerCurve.normalizedThreat(PowerTracker.threat(mob, config), config);
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
		player.experienceLevel = 0;
		PacketRecorder.attach(player);
		forceChunks(helper.getLevel(), player.getX(), player.getZ(), true);
		return player;
	}

	private static Mob spawnKind(
		final GameTestHelper helper,
		final EntityType<? extends Mob> type,
		final double x,
		final double y,
		final double z,
		final boolean still
	) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.setPos(x, y, z);
		mob.setNoAi(still);
		mob.setPersistenceRequired();
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "the level refused the " + type);
		return mob;
	}

	// The color of the readout's name part for this viewer, or "none" without a readout.
	private static String nameColor(final ServerPlayer viewer, final Mob mob) {
		ReadoutOwner.Readout readout = ReadoutService.owner(viewer).get(mob.getUUID());
		if (readout == null) {
			return "none";
		}

		String name = mob.getDisplayName().getString();
		for (Component part : readout.display().getText().toFlatList(Style.EMPTY)) {
			if (part.getString().equals(name)) {
				TextColor color = part.getStyle().getColor();
				return color == null ? "plain" : color.getValue() == RED_VALUE ? RED : color.getValue() == GREEN_VALUE ? GREEN : "other";
			}
		}

		return "missing";
	}

	private static ReadoutOwner.Readout firstReadout(final ReadoutOwner owner) {
		for (ReadoutOwner.Readout readout : owner.readouts().values()) {
			return readout;
		}

		return null;
	}
}
