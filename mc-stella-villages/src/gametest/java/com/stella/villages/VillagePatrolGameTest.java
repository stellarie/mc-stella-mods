package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.patrol.MobPatrolAccess;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class VillagePatrolGameTest {
	private static final int PATROL_COUNT = PatrolService.PATROL_MEMBER_COUNT;
	private static final int TARGET_SELECTION_WINDOW = 200;
	private static final int THREAT_TEST_LIMIT = PatrolService.PLAYER_THREAT_CLEAR_TICKS + 220;

	@GameTest(maxTicks = 100, padding = 64)
	public void vacantVillagePoiDoesNotRegister(final GameTestHelper helper) {
		String test = "vacantVillagePoiDoesNotRegister";
		BlockPos poi = helper.absolutePos(new BlockPos(2, 1, 2));
		helper.setBlock(2, 0, 2, Blocks.STONE);
		helper.setBlock(2, 1, 2, Blocks.BELL);

		var poiManager = helper.getLevel().getPoiManager();
		ChunkPos chunk = new ChunkPos(poi.getX() >> 4, poi.getZ() >> 4);
		long allRecords = poiManager
			.getInChunk(holder -> holder.is(PoiTypeTags.VILLAGE), chunk, PoiManager.Occupancy.ANY)
			.filter(record -> record.getPos().equals(poi))
			.count();
		long occupiedRecords = poiManager
			.getInChunk(holder -> holder.is(PoiTypeTags.VILLAGE), chunk, PoiManager.Occupancy.IS_OCCUPIED)
			.filter(record -> record.getPos().equals(poi))
			.count();
		PatrolTestSupport.detail(test, "poi=" + poi + " any=" + allRecords + " occupied=" + occupiedRecords);
		helper.assertTrue(allRecords > 0, "test bell did not create a village POI record");
		helper.assertValueEqual(Long.valueOf(occupiedRecords), Long.valueOf(0), "vacant village POI count");

		List<UUID> registrations = PatrolTestSupport.scanChunkIds(helper, test, poi);
		if (registrations == null) {
			return;
		}

		PatrolTestSupport.result(test, "any=" + allRecords + " occupied=" + occupiedRecords + " registrations=" + registrations.size());
		helper.assertTrue(registrations.isEmpty(), "vacant village POI created a patrol village");
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void diagonalAnchorsUseChebyshevSectionDistance(final GameTestHelper helper) {
		if (!PatrolTestSupport.diagonalAnchorIndex(helper, "diagonalAnchorsUseChebyshevSectionDistance")) {
			return;
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void poiAnchorsRespectBoundsTiesAndDimensions(final GameTestHelper helper) {
		if (!PatrolTestSupport.anchorIndex(helper, "poiAnchorsRespectBoundsTiesAndDimensions")) {
			return;
		}
		if (!PatrolTestSupport.loadVillage(helper, "poiAnchorsRespectBoundsTiesAndDimensions", true)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		PatrolTestSupport.result("poiAnchorsRespectBoundsTiesAndDimensions", "nearbyPoints=2 patrols=" + patrols.size());
		helper.assertValueEqual(Integer.valueOf(patrols.size()), Integer.valueOf(PATROL_COUNT), "patrols for nearby POIs");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void rosterHasRequiredMembers(final GameTestHelper helper) {
		if (!PatrolTestSupport.loadVillage(helper, "rosterHasRequiredMembers", false)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		long vindicators = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:vindicator")).count();
		long pillagers = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:pillager")).count();
		long evokers = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:evoker")).count();
		long ravagers = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:ravager")).count();
		long witches = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:witch")).count();
		long crossbowPillagers = patrols.stream()
			.filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:pillager"))
			.filter(mob -> mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
				.is(net.minecraft.world.item.Items.CROSSBOW))
			.count();
		Mob ravager = patrols.stream().filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:ravager"))
			.findFirst().orElse(null);
		boolean ravagerUnmounted = ravager != null && ravager.getVehicle() == null && ravager.getPassengers().isEmpty();
		boolean markers = patrols.stream().allMatch(PatrolTestSupport::marker);
		PatrolTestSupport.result(
			"rosterHasRequiredMembers",
			"count=" + patrols.size() + " vindicators=" + vindicators + " pillagers=" + pillagers
				+ " crossbowPillagers=" + crossbowPillagers + " evokers=" + evokers + " ravagers=" + ravagers
				+ " witches=" + witches + " ravagerUnmounted=" + ravagerUnmounted + " markers=" + markers
		);
		helper.assertValueEqual(Integer.valueOf(patrols.size()), Integer.valueOf(PATROL_COUNT), "patrol roster size");
		helper.assertValueEqual(Long.valueOf(vindicators), Long.valueOf(4), "vindicator count");
		helper.assertValueEqual(Long.valueOf(pillagers), Long.valueOf(5), "pillager count");
		helper.assertValueEqual(Long.valueOf(evokers), Long.valueOf(0), "evoker count");
		helper.assertValueEqual(Long.valueOf(ravagers), Long.valueOf(1), "ravager count");
		helper.assertValueEqual(Long.valueOf(witches), Long.valueOf(0), "witch count");
		helper.assertValueEqual(Long.valueOf(crossbowPillagers), Long.valueOf(5), "crossbow Pillager count");
		helper.assertTrue(ravagerUnmounted, "patrol Ravager was mounted");
		helper.assertTrue(markers, "a patrol marker was not set before entity insertion");
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void patrolHealthMatchesVanillaTypeBaselines(final GameTestHelper helper) {
		String test = "patrolHealthMatchesVanillaTypeBaselines";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		List<String> ratios = new ArrayList<>();
		boolean matches = patrols.size() == PATROL_COUNT;
		for (Mob patrol : patrols) {
			var created = patrol.getType().create(helper.getLevel(), EntitySpawnReason.COMMAND);
			if (!(created instanceof Mob baseline)) {
				ratios.add(PatrolTestSupport.typeId(patrol) + "=missing");
				matches = false;
				continue;
			}
			baseline.snapTo(patrol.position(), patrol.getYRot(), patrol.getXRot());
			double ratio = patrol.getMaxHealth() / baseline.getMaxHealth();
			ratios.add(PatrolTestSupport.typeId(patrol) + "=" + ratio);
			matches &= Math.abs(ratio - PatrolService.PATROL_HEALTH_MULTIPLIER) < 1.0e-6;
		}

		PatrolTestSupport.result(
			test,
			"count=" + patrols.size() + " multiplier=" + PatrolService.PATROL_HEALTH_MULTIPLIER
				+ " ratios=" + ratios + " matches=" + matches
		);
		helper.assertValueEqual(Integer.valueOf(patrols.size()), Integer.valueOf(PATROL_COUNT), "patrol count for health baseline");
		helper.assertTrue(matches, "a patrol member differs from its same-type health baseline");
		helper.succeed();
	}

	@GameTest(maxTicks = 800, padding = 64)
	public void rosterDoesNotDuplicateAfterChunkReload(final GameTestHelper helper) {
		String test = "rosterDoesNotDuplicateAfterChunkReload";
		BlockPos villageAnchor = PatrolTestSupport.findDistantVillageAnchor(helper, test);
		if (villageAnchor == null) {
			return;
		}

		int villageChunkX = SectionPos.blockToSectionCoord(villageAnchor.getX());
		int villageChunkZ = SectionPos.blockToSectionCoord(villageAnchor.getZ());
		helper.getLevel().setChunkForced(villageChunkX, villageChunkZ, true);
		helper.getLevel().getChunkAt(villageAnchor);
		if (!PatrolTestSupport.loadVillageAt(helper, test, villageAnchor)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.activePatrols(helper, test);
		Mob head = PatrolTestSupport.activeRole(helper, test, "HEAD");
		Mob ravager = PatrolTestSupport.activeRole(helper, test, "RAVAGER");
		UUID villageId = PatrolTestSupport.villageId(helper);
		PatrolTestSupport.detail(test, "village=" + villageId + " patrolCount=" + patrols.size()
			+ " activePositions=" + patrols.stream().map(Mob::blockPosition).toList()
			+ " headFound=" + (head != null) + " ravagerFound=" + (ravager != null)
			+ " ravagerPassengers=" + (ravager == null ? -1 : ravager.getPassengers().size()));
		if (patrols.size() != PATROL_COUNT || head == null || ravager == null) {
			helper.fail("complete patrol roster or its Ravager is missing");
			return;
		}

		UUID headId = head.getUUID();
		UUID ravagerId = ravager.getUUID();
		List<UUID> before = patrols.stream()
			.map(Mob::getUUID).sorted(Comparator.comparing(UUID::toString)).toList();
		if (before.size() != PATROL_COUNT) {
			helper.fail("patrol active-member index is incomplete");
			return;
		}

		int headChunkX = villageChunkX + SectionPos.blockToSectionCoord(PatrolService.PATROL_DETECTION_RANGE) + 1;
		int headChunkZ = villageChunkZ;
		BlockPos headPosition = new BlockPos(
			SectionPos.sectionToBlockCoord(headChunkX) + 2,
			villageAnchor.getY(),
			SectionPos.sectionToBlockCoord(headChunkZ) + 8
		);
		prepareForcedChunkSite(helper, headPosition, headChunkX, headChunkZ);
		for (Mob member : patrols) {
			if (member != head) {
				member.setNoAi(true);
			}
			member.getNavigation().stop();
		}
		head.getNavigation().stop();
		head.snapTo(Vec3.atBottomCenterOf(headPosition.above(2)), head.getYRot(), head.getXRot());
		double[] nearestOtherDistance = {-1.0};

		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		BlockPos playerHome = helper.absolutePos(new BlockPos(5, 1, 5));
		attacker.snapTo(Vec3.atBottomCenterOf(playerHome), attacker.getYRot(), attacker.getXRot());
		UUID attackerId = attacker.getUUID();
		boolean[] resultLogged = {false};
		int[] stage = {0};
		int[] activeDuringUnload = {-1};
		int[] outsideTicks = {-1};
		boolean[] entitiesAbsent = {false};
		boolean[] savedPosition = {false};
		boolean[] threatRangeRetained = {false};
		boolean[] sameIdsAfterReload = {false};
		boolean[] ravagerUnmountedAfterReload = {false};
		boolean[] rosterStartedAfterReload = {false};

		PatrolTestSupport.detail(test, "village=" + villageId + " villageAnchor=" + villageAnchor
			+ " headId=" + headId + " headPosition=" + headPosition + " nearestOtherDistance=" + nearestOtherDistance[0]
			+ " rosterIds=" + before);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(head.chunkPosition().equals(new ChunkPos(headChunkX, headChunkZ)),
					"head did not enter the isolated unload chunk");
				helper.assertTrue(head.onGround(), "head did not settle in the unload chunk");
				helper.assertTrue(unmounted(ravager), "the patrol Ravager carried a rider before unload");
			})
			.thenExecute(() -> {
				head.setNoAi(true);
				head.getNavigation().stop();
				nearestOtherDistance[0] = patrols.stream()
					.filter(member -> member != head)
					.mapToDouble(head::distanceTo)
					.min()
					.orElse(Double.MAX_VALUE);
				helper.assertTrue(head.blockPosition().equals(headPosition), "head did not land on its saved-position block");
				helper.assertTrue(nearestOtherDistance[0] > PatrolService.PATROL_DETECTION_RANGE
					&& nearestOtherDistance[0] <= PatrolService.PATROL_RADIUS,
					"unloaded head is not isolated within its patrol radius");
				stage[0] = 1;
				boolean villageWasForced = helper.getLevel().setChunkForced(villageChunkX, villageChunkZ, false);
				boolean headWasForced = helper.getLevel().setChunkForced(headChunkX, headChunkZ, false);
				PatrolTestSupport.detail(test, "villageForcedBeforeUnload=" + villageWasForced
					+ " headForcedBeforeUnload=" + headWasForced);
				helper.assertTrue(villageWasForced && headWasForced,
					"village or head chunk was not forced before unload");
			})
			.thenWaitUntil(() -> helper.assertTrue(
				!helper.getLevel().getChunkSource().hasChunk(villageChunkX, villageChunkZ)
					&& !helper.getLevel().getChunkSource().hasChunk(headChunkX, headChunkZ),
				"village and head chunks did not unload after ticket removal"
			))
			.thenExecute(() -> {
				stage[0] = 2;
				List<Mob> active = PatrolTestSupport.activePatrols(helper, test);
				activeDuringUnload[0] = active.size();
				entitiesAbsent[0] = before.stream()
					.allMatch(id -> helper.getLevel().getEntityInAnyDimension(id) == null);
				BlockPos rememberedHeadPosition = PatrolTestSupport.memberLastPosition(helper, test, headId);
				savedPosition[0] = headPosition.equals(rememberedHeadPosition);
				helper.assertTrue(entitiesAbsent[0] && active.isEmpty(),
					"unloaded patrol entities remained in the world or active index");
				helper.assertTrue(savedPosition[0], "unloaded head position was not saved");

				boolean threatStarted = PatrolTestSupport.startThreat(helper, test, attackerId);
				attacker.setPos(Vec3.atCenterOf(headPosition));
				PatrolTestSupport.updateThreats(helper, test);
				Integer ticks = PatrolTestSupport.threatOutsideTicks(helper, test, attackerId);
				outsideTicks[0] = ticks == null ? -1 : ticks;
				threatRangeRetained[0] = threatStarted && outsideTicks[0] == 0
					&& !helper.getLevel().getChunkSource().hasChunk(headChunkX, headChunkZ);
				PatrolTestSupport.detail(test, "entitiesAbsent=" + entitiesAbsent[0]
					+ " activeDuringUnload=" + activeDuringUnload[0] + " savedHeadPosition=" + rememberedHeadPosition
					+ " expectedHeadPosition=" + headPosition + " attackerInSavedRange=" + threatRangeRetained[0]
					+ " outsideTicks=" + outsideTicks[0]);
				helper.assertTrue(threatRangeRetained[0],
					"threat check did not retain the unloaded head's saved position");

				attacker.setPos(Vec3.atBottomCenterOf(playerHome));
				helper.getLevel().setChunkForced(villageChunkX, villageChunkZ, true);
				helper.getLevel().getChunkAt(villageAnchor);
				helper.getLevel().setChunkForced(headChunkX, headChunkZ, true);
				helper.getLevel().getChunkAt(headPosition);
				stage[0] = 3;
			})
			.thenWaitUntil(() -> {
				List<Mob> active = PatrolTestSupport.activePatrols(helper, test);
				List<UUID> after = active.stream().map(Mob::getUUID)
					.sorted(Comparator.comparing(UUID::toString)).toList();
				sameIdsAfterReload[0] = before.equals(after) && active.size() == PATROL_COUNT;
				var loadedRavager = helper.getLevel().getEntityInAnyDimension(ravagerId);
				ravagerUnmountedAfterReload[0] = loadedRavager != null && unmounted(loadedRavager);
				helper.assertTrue(sameIdsAfterReload[0], "patrol UUID set changed after village chunk reload");
				helper.assertTrue(ravagerUnmountedAfterReload[0], "the patrol Ravager reloaded with a rider");
			})
			.thenExecute(() -> {
				stage[0] = 4;
				boolean poiClaimed = PatrolTestSupport.claimVillagePoi(helper, test, villageAnchor);
				boolean reopened = PatrolTestSupport.setRosterStarted(helper, test, false);
				boolean scanned = poiClaimed && reopened && PatrolTestSupport.scanChunk(helper, test);
				List<Mob> activeAfterScan = PatrolTestSupport.activePatrols(helper, test);
				List<UUID> idsAfterScan = activeAfterScan.stream().map(Mob::getUUID)
					.sorted(Comparator.comparing(UUID::toString)).toList();
				sameIdsAfterReload[0] &= scanned && activeAfterScan.size() == PATROL_COUNT
					&& before.equals(idsAfterScan);
				rosterStartedAfterReload[0] = PatrolTestSupport.rosterStarted(helper, test);
				PatrolTestSupport.detail(test, "reopenedRoster=" + reopened + " poiClaimed=" + poiClaimed
					+ " rescanned=" + scanned + " activeAfterRescan=" + activeAfterScan.size()
					+ " idsUnchanged=" + sameIdsAfterReload[0]
					+ " rosterStartedAfterRescan=" + rosterStartedAfterReload[0]);
				helper.assertTrue(sameIdsAfterReload[0], "chunk reload rescan duplicated or replaced patrols");
				helper.assertTrue(rosterStartedAfterReload[0], "chunk reload rescan did not seal the roster");
			})
			.thenExecute(() -> {
				boolean passed = before.size() == PATROL_COUNT && activeDuringUnload[0] == 0
					&& entitiesAbsent[0] && savedPosition[0] && threatRangeRetained[0]
					&& sameIdsAfterReload[0] && ravagerUnmountedAfterReload[0] && rosterStartedAfterReload[0];
				resultLogged[0] = true;
				PatrolTestSupport.result(test, "passed=" + passed + " count=" + before.size()
					+ " activeDuringUnload=" + activeDuringUnload[0] + " entitiesAbsent=" + entitiesAbsent[0]
					+ " savedPosition=" + savedPosition[0] + " outsideTicks=" + outsideTicks[0]
					+ " sameIds=" + sameIdsAfterReload[0] + " ravagerUnmounted=" + ravagerUnmountedAfterReload[0]
					+ " rosterStarted=" + rosterStartedAfterReload[0]);
				helper.assertTrue(passed, "patrol roster or threat persistence failed after chunk reload");
			})
			.thenSucceed();
		helper.runAfterDelay(799, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "passed=false stage=" + stage[0]
					+ " villageChunkLoaded=" + helper.getLevel().getChunkSource().hasChunk(villageChunkX, villageChunkZ)
					+ " headChunkLoaded=" + helper.getLevel().getChunkSource().hasChunk(headChunkX, headChunkZ)
					+ " activeDuringUnload=" + activeDuringUnload[0] + " entitiesAbsent=" + entitiesAbsent[0]
					+ " savedPosition=" + savedPosition[0] + " threatRangeRetained=" + threatRangeRetained[0]
					+ " outsideTicks=" + outsideTicks[0] + " sameIds=" + sameIdsAfterReload[0]);
			}
		});
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void patrolHeadReturnsWithinVillageRadius(final GameTestHelper helper) {
		String test = "patrolHeadReturnsWithinVillageRadius";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		if (head == null) {
			helper.fail("patrol head is missing");
			return;
		}

		int radius = PatrolService.PATROL_RADIUS;
		BlockPos anchorPosition = PatrolTestSupport.anchor(helper);
		Vec3 anchor = Vec3.atCenterOf(anchorPosition);
		prepareMovementLane(helper, anchorPosition, radius + 8);
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != head) {
				member.setNoAi(true);
			}
		}
		head.getGoalSelector().removeAllGoals(
			goal -> !goal.getClass().getSimpleName().equals("VillageWanderGoal")
		);
		BlockPos outside = anchorPosition.offset(radius + 4, 0, 0);
		head.snapTo(Vec3.atBottomCenterOf(outside), head.getYRot(), head.getXRot());
		double headStartDistance = head.position().distanceTo(anchor);
		boolean[] resultLogged = {false};

		PatrolTestSupport.detail(test, "anchor=" + anchorPosition + " headStartDistance=" + headStartDistance
			+ " radius=" + radius);
		helper.assertTrue(headStartDistance > radius, "movement fixture did not place the head outside its radius");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(
				head.position().distanceTo(anchor) <= radius,
				"patrol head did not return within its village radius"
			))
			.thenExecute(() -> {
				resultLogged[0] = true;
				PatrolTestSupport.result(test, "passed=true headStartDistance=" + headStartDistance
					+ " headEndDistance=" + head.position().distanceTo(anchor) + " radius=" + radius);
			})
			.thenSucceed();
		helper.runAfterDelay(199, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "passed=false headDistance=" + head.position().distanceTo(anchor)
					+ " headPosition=" + head.position() + " radius=" + radius);
			}
		});
	}

	@GameTest(maxTicks = 240, padding = 64)
	public void patrolFollowerReturnsWithinFollowDistance(final GameTestHelper helper) {
		String test = "patrolFollowerReturnsWithinFollowDistance";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		Mob follower = PatrolTestSupport.role(helper, test, "PILLAGER");
		if (head == null || follower == null) {
			helper.fail("patrol head or follower is missing");
			return;
		}

		int followDistance = PatrolService.PATROL_FOLLOW_DISTANCE;
		BlockPos anchor = PatrolTestSupport.anchor(helper);
		prepareMovementLane(helper, anchor, followDistance + 20);
		BlockPos headPosition = anchor.offset(-2, 0, -2);
		BlockPos nearbyFollower = headPosition.east();
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != head && member != follower) {
				member.setNoAi(true);
			}
		}
		head.snapTo(Vec3.atBottomCenterOf(headPosition), head.getYRot(), head.getXRot());
		follower.snapTo(Vec3.atBottomCenterOf(nearbyFollower), follower.getYRot(), follower.getXRot());
		follower.getNavigation().stop();
		follower.getGoalSelector().removeAllGoals(
			goal -> !goal.getClass().getSimpleName().equals("PatrolFollowGoal")
		);
		boolean[] resultLogged = {false};
		int[] stage = {0};
		final double[] startDistance = {-1.0};

		PatrolTestSupport.detail(test, "initialHeadPosition=" + headPosition + " initialFollowerPosition=" + nearbyFollower
			+ " headOnGround=" + head.onGround() + " followerOnGround=" + follower.onGround());
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(head.onGround(), "patrol head did not settle on the runway");
				helper.assertTrue(follower.onGround(), "patrol follower did not settle on the runway");
			})
			.thenExecute(() -> {
				stage[0] = 1;
				head.getGoalSelector().removeAllGoals(goal -> true);
				head.getNavigation().stop();
				head.setNoAi(true);
				BlockPos followerStart = headPosition.offset(followDistance + 8, 0, 0);
				follower.snapTo(Vec3.atBottomCenterOf(followerStart), follower.getYRot(), follower.getXRot());
				follower.getNavigation().stop();
				startDistance[0] = follower.distanceTo(head);
				String goals = follower.getGoalSelector().getAvailableGoals().stream()
					.map(goal -> goal.getGoal().getClass().getSimpleName() + "@" + goal.getPriority() + "@" + goal.isRunning())
					.toList().toString();
				PatrolTestSupport.detail(test, "headPosition=" + headPosition + " followerStart=" + followerStart
					+ " startDistance=" + startDistance[0] + " followerGoals=" + goals
					+ " followerOnGround=" + follower.onGround() + " followDistance=" + followDistance);
				helper.assertTrue(startDistance[0] > followDistance,
					"follower fixture is not outside its follow distance");
			})
			.thenWaitUntil(() -> helper.assertTrue(
				follower.distanceTo(head) <= followDistance,
				"patrol follower did not return within the follow distance"
			))
			.thenExecute(() -> {
				resultLogged[0] = true;
				PatrolTestSupport.result(test, "passed=true startDistance=" + startDistance[0]
					+ " endDistance=" + follower.distanceTo(head) + " followDistance=" + followDistance);
			})
			.thenSucceed();
		helper.runAfterDelay(239, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "passed=false stage=" + (stage[0] == 0 ? "grounding" : "follower_return")
					+ " startDistance=" + startDistance[0] + " endDistance=" + follower.distanceTo(head)
					+ " headOnGround=" + head.onGround() + " followerOnGround=" + follower.onGround()
					+ " followerPosition=" + follower.position()
					+ " followerNavigationDone=" + follower.getNavigation().isDone()
					+ " followerGoals=" + follower.getGoalSelector().getAvailableGoals().stream()
						.map(goal -> goal.getGoal().getClass().getSimpleName() + "@" + goal.getPriority() + "@" + goal.isRunning())
						.toList());
			}
		});
	}

	@GameTest(maxTicks = 800, padding = 64)
	public void ravagerStaysUnmountedThroughMovementAndReload(final GameTestHelper helper) {
		String test = "ravagerStaysUnmountedThroughMovementAndReload";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		Mob ravager = PatrolTestSupport.role(helper, test, "RAVAGER");
		if (head == null || ravager == null) {
			helper.fail("patrol head or Ravager is missing");
			return;
		}

		int followDistance = PatrolService.PATROL_FOLLOW_DISTANCE;
		BlockPos anchor = PatrolTestSupport.anchor(helper);
		prepareMovementLane(helper, anchor, followDistance + 20);
		BlockPos headPosition = anchor.offset(-2, 0, -2);
		BlockPos ravagerStart = headPosition.offset(followDistance + 8, 0, 0);
		Vec3 ravagerStartPosition = Vec3.atBottomCenterOf(ravagerStart);
		int farChunkX = SectionPos.blockToSectionCoord(anchor.getX()) + 64;
		int farChunkZ = SectionPos.blockToSectionCoord(anchor.getZ()) + 64;
		BlockPos farPosition = new BlockPos(
			SectionPos.sectionToBlockCoord(farChunkX) + 8,
			anchor.getY(),
			SectionPos.sectionToBlockCoord(farChunkZ) + 8
		);
		ChunkPos farChunk = new ChunkPos(farChunkX, farChunkZ);
		prepareForcedChunkSite(helper, farPosition, farChunkX, farChunkZ);
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != head && member != ravager) {
				member.setNoAi(true);
			}
		}

		UUID ravagerId = ravager.getUUID();
		boolean[] resultLogged = {false};
		int[] stage = {0};
		final double[] movementDistance = {-1.0};
		final boolean[] absentOnUnload = {false};

		PatrolTestSupport.detail(test, "anchor=" + anchor + " ravagerStart=" + ravagerStart
			+ " farChunk=" + farChunk + " farPosition=" + farPosition);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(head.onGround() && ravager.onGround(), "head or Ravager did not settle before movement");
				helper.assertTrue(unmounted(ravager), "the patrol Ravager carried a rider before movement");
			})
			.thenExecute(() -> {
				stage[0] = 1;
				head.snapTo(Vec3.atBottomCenterOf(headPosition), head.getYRot(), head.getXRot());
				head.getNavigation().stop();
				head.setNoAi(true);
				ravager.snapTo(ravagerStartPosition, ravager.getYRot(), ravager.getXRot());
				ravager.getNavigation().stop();
				PatrolTestSupport.detail(test, "startDistance=" + ravager.distanceTo(head) + " ravagerOnGround=" + ravager.onGround());
			})
			.thenWaitUntil(() -> helper.assertTrue(
				ravager.position().distanceTo(ravagerStartPosition) > 1.0 && unmounted(ravager),
				"the Ravager did not follow its head without a rider"
			))
			.thenExecute(() -> {
				stage[0] = 2;
				movementDistance[0] = ravager.position().distanceTo(ravagerStartPosition);
				helper.getLevel().setChunkForced(farChunkX, farChunkZ, true);
				Vec3 remotePosition = Vec3.atBottomCenterOf(farPosition);
				ravager.teleportTo(remotePosition.x, remotePosition.y, remotePosition.z);
			})
			.thenWaitUntil(() -> helper.assertTrue(ravager.chunkPosition().equals(farChunk), "the Ravager did not enter the reload chunk"))
			.thenExecute(() -> {
				stage[0] = 3;
				boolean forced = helper.getLevel().setChunkForced(farChunkX, farChunkZ, false);
				PatrolTestSupport.detail(test, "farChunkForcedBeforeUnload=" + forced + " ravager=" + ravagerId);
			})
			.thenWaitUntil(() -> helper.assertTrue(
				!helper.getLevel().getChunkSource().hasChunk(farChunkX, farChunkZ),
				"reload chunk did not unload after ticket removal"
			))
			.thenExecute(() -> {
				absentOnUnload[0] = helper.getLevel().getEntityInAnyDimension(ravagerId) == null;
				helper.assertTrue(absentOnUnload[0], "the Ravager stayed active after chunk unload");
				stage[0] = 4;
				helper.getLevel().setChunkForced(farChunkX, farChunkZ, true);
				helper.getLevel().getChunkAt(farPosition);
			})
			.thenWaitUntil(() -> {
				var loaded = helper.getLevel().getEntityInAnyDimension(ravagerId);
				helper.assertTrue(loaded instanceof Mob && unmounted(loaded), "the Ravager did not reload unmounted");
			})
			.thenExecute(() -> {
				resultLogged[0] = true;
				PatrolTestSupport.result(test, "passed=true movementDistance=" + movementDistance[0]
					+ " chunkUnloaded=" + absentOnUnload[0] + " ravagerId=" + ravagerId);
			})
			.thenSucceed();
		helper.runAfterDelay(799, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "passed=false stage=" + stage[0]
					+ " movementDistance=" + movementDistance[0]
					+ " chunkLoaded=" + helper.getLevel().getChunkSource().hasChunk(farChunkX, farChunkZ)
					+ " ravagerPresent=" + (helper.getLevel().getEntityInAnyDimension(ravagerId) != null)
					+ " ravagerPassengers=" + ravager.getPassengers().size());
			}
		});
	}

	/** A Ravager counts as unmounted when it rides nothing and carries nobody. */
	private static boolean unmounted(final net.minecraft.world.entity.Entity entity) {
		return entity.getVehicle() == null && entity.getPassengers().isEmpty();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void targetRangeHonorsPatrolDetection(final GameTestHelper helper) {
		if (!PatrolTestSupport.loadVillage(helper, "targetRangeHonorsPatrolDetection", false)) {
			return;
		}

		int detectionRange = PatrolTestSupport.intConstant(helper, "targetRangeHonorsPatrolDetection", "PATROL_DETECTION_RANGE");
		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		boolean allMatch = patrols.stream().allMatch(mob -> Math.abs(PatrolTestSupport.followRange(mob) - detectionRange) < 1.0e-6);
		PatrolTestSupport.result(
			"targetRangeHonorsPatrolDetection", "count=" + patrols.size() + " detectionRange=" + detectionRange + " allMatch=" + allMatch
		);
		helper.assertValueEqual(Integer.valueOf(patrols.size()), Integer.valueOf(PATROL_COUNT), "patrol count for detection range");
		helper.assertTrue(allMatch, "a patrol member has the wrong follow range");
		helper.succeed();
	}

	@GameTest(maxTicks = TARGET_SELECTION_WINDOW + 20, padding = 64)
	public void patrolTargetsHostilesInsideDetectionRange(final GameTestHelper helper) {
		String test = "patrolTargetsHostilesInsideDetectionRange";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		if (head == null) {
			helper.fail("patrol head is missing for inside-range target test");
			return;
		}

		int detectionRange = PatrolService.PATROL_DETECTION_RANGE;
		Mob hostile = spawnTestCreeper(helper, test, head, detectionRange - 1.0);
		if (hostile == null) {
			return;
		}
		double initialDistance = head.distanceTo(hostile);
		int headTicksAtStart = head.tickCount;
		BlockPos headBlock = head.blockPosition();
		BlockPos hostileBlock = hostile.blockPosition();
		List<Integer> blockedColumn = new ArrayList<>();
		for (int y = headBlock.getY() + 1; y < hostileBlock.getY(); y++) {
			if (!helper.getLevel().getBlockState(new BlockPos(headBlock.getX(), y, headBlock.getZ())).isAir()) {
				blockedColumn.add(y);
			}
		}
		PatrolTestSupport.detail(
			test, "headBlock=" + headBlock + " hostileBlock=" + hostileBlock + " blockedColumnY=" + blockedColumn
		);
		String targetGoals = ((MobPatrolAccess) head).patrolTargetSelector().getAvailableGoals().stream()
			.map(wrapped -> wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority() + "@" + wrapped.isRunning())
			.toList()
			.toString();
		PatrolTestSupport.detail(
			test,
			"targetGoals=" + targetGoals + " hostileType=" + PatrolTestSupport.typeId(hostile)
				+ " hostileVisible=" + hostile.canBeSeenAsEnemy() + " canAttack=" + head.canAttack(hostile)
				+ " lineOfSight=" + head.hasLineOfSight(hostile)
				+ " inRangeBox=" + head.getBoundingBox().inflate(detectionRange).contains(hostile.position())
				+ " nearby=" + PatrolTestSupport.mobs(helper).contains(hostile)
				+ " followRange=" + PatrolTestSupport.followRange(head) + " difficulty=" + helper.getLevel().getDifficulty()
		);
		boolean[] acquired = {false};
		boolean[] visible = {false};
		boolean[] nearestGoalRunning = {false};
		boolean[] resultLogged = {false};
		helper.onEachTick(() -> {
			head.getNavigation().stop();
			if (head.hasLineOfSight(hostile)) {
				visible[0] = true;
			}
			for (var wrapped : ((MobPatrolAccess) head).patrolTargetSelector().getAvailableGoals()) {
				if (wrapped.getGoal().getClass().getSimpleName().equals("NearestAttackableTargetGoal") && wrapped.isRunning()) {
					nearestGoalRunning[0] = true;
				}
			}
			if (head.getTarget() == hostile) {
				acquired[0] = true;
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(
				acquired[0], "patrol head did not target a hostile inside detection range"
			))
			.thenExecute(() -> {
				resultLogged[0] = true;
				PatrolTestSupport.result(test, "range=" + detectionRange + " initialDistance=" + initialDistance
					+ " visible=" + visible[0] + " acquired=" + acquired[0]
					+ " nearestGoalRunning=" + nearestGoalRunning[0]);
			})
			.thenSucceed();
		helper.runAfterDelay(TARGET_SELECTION_WINDOW + 10, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "range=" + detectionRange + " initialDistance=" + initialDistance
					+ " visible=" + visible[0] + " acquired=" + acquired[0]
					+ " nearestGoalRunning=" + nearestGoalRunning[0]
					+ " currentTarget=" + (head.getTarget() == null ? "none" : PatrolTestSupport.typeId(head.getTarget()))
					+ " head=" + head.getUUID() + " headNoAi=" + head.isNoAi() + " headAlive=" + head.isAlive()
					+ " headVillage=" + PatrolState.villageId(head) + " testVillage=" + PatrolTestSupport.villageId(helper)
					+ " finalDistance=" + head.distanceTo(hostile) + " hostileAlive=" + hostile.isAlive()
					+ " headTicked=" + (head.tickCount - headTicksAtStart)
					+ " headEntityTicking=" + helper.getLevel().isPositionEntityTicking(head.blockPosition())
					+ " hostileEntityTicking=" + helper.getLevel().isPositionEntityTicking(hostile.blockPosition()));
				helper.assertTrue(acquired[0], "patrol head did not target a hostile inside detection range");
			}
		});
	}

	@GameTest(dimension = "minecraft:the_end", maxTicks = TARGET_SELECTION_WINDOW + 20, padding = 64)
	public void patrolDoesNotTargetHostilesOutsideDetectionRange(final GameTestHelper helper) {
		String test = "patrolDoesNotTargetHostilesOutsideDetectionRange";
		BlockPos isolatedAnchor = PatrolTestSupport.findDistantVillageAnchor(helper, test);
		if (isolatedAnchor == null || !PatrolTestSupport.loadVillageAt(helper, test, isolatedAnchor)) {
			return;
		}

		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		if (head == null) {
			helper.fail("patrol head is missing for outside-range target test");
			return;
		}

		int detectionRange = PatrolService.PATROL_DETECTION_RANGE;
		Mob hostile = spawnTestCreeper(helper, test, head, detectionRange + 1.0);
		if (hostile == null) {
			return;
		}
		double initialDistance = head.distanceTo(hostile);
		PatrolTestSupport.detail(test, "lineOfSight=" + head.hasLineOfSight(hostile) + " canAttack=" + head.canAttack(hostile));
		boolean[] acquired = {false};
		boolean[] visible = {false};
		helper.onEachTick(() -> {
			head.getNavigation().stop();
			if (head.hasLineOfSight(hostile)) {
				visible[0] = true;
			}
			if (head.getTarget() == hostile) {
				acquired[0] = true;
			}
		});
		helper.runAfterDelay(TARGET_SELECTION_WINDOW, () -> {
			PatrolTestSupport.result(
				test,
				"range=" + detectionRange + " initialDistance=" + initialDistance + " visible=" + visible[0]
					+ " acquired=" + acquired[0]
			);
			helper.assertTrue(visible[0], "outside-range hostile never became visible to the patrol head");
			helper.assertFalse(acquired[0], "patrol head targeted a hostile outside detection range");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = THREAT_TEST_LIMIT, padding = 64)
	public void playerThreatResetsAndClears(final GameTestHelper helper) {
		if (!PatrolTestSupport.loadVillage(helper, "playerThreatResetsAndClears", false)) {
			return;
		}

		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		attacker.setGameMode(GameType.SURVIVAL);
		attacker.getAbilities().instabuild = false;
		attacker.getAbilities().invulnerable = false;
		PatrolTestSupport.detail(
			"playerThreatResetsAndClears",
			"difficulty=" + helper.getLevel().getDifficulty() + " gameMode=" + attacker.gameMode()
				+ " invulnerable=" + attacker.isInvulnerable() + " abilitiesInvulnerable=" + attacker.getAbilities().invulnerable
				+ " seenAsEnemy=" + attacker.canBeSeenAsEnemy()
		);
		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		Mob guard = patrols.stream().findFirst().orElse(null);
		Mob villager = PatrolTestSupport.villager(helper);
		long villagerCount = PatrolTestSupport.mobs(helper).stream()
			.filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:villager"))
			.count();
		PatrolTestSupport.detail(
			"playerThreatResetsAndClears",
			"anchor=" + PatrolTestSupport.anchor(helper) + " village=" + PatrolTestSupport.villageId(helper)
				+ " guards=" + patrols.size() + " villager=" + (villager != null) + " villagers=" + villagerCount
		);
		if (guard == null || villager == null) {
			helper.fail("patrol guard or village villager is missing for threat test");
			return;
		}

		attacker.setPos(villager.getX() + 2.0, villager.getY(), villager.getZ() + 2.0);
		if (!PatrolTestSupport.startThreat(helper, "playerThreatResetsAndClears", attacker.getUUID())) {
			return;
		}

		Vec3 outside = helper.absoluteVec(new Vec3(160.0, 1.0, 160.0));
		attacker.setPos(outside.x, outside.y, outside.z);
		helper.startSequence()
			.thenIdle(198)
			.thenExecute(() -> attacker.setPos(guard.getX() + 2.0, guard.getY(), guard.getZ() + 2.0))
			.thenIdle(1)
			.thenExecute(() -> {
				boolean active = PatrolTestSupport.hasThreat(helper, "playerThreatResetsAndClears", attacker.getUUID());
				PatrolTestSupport.detail("playerThreatResetsAndClears", "tick=" + helper.getTick() + " activeAfterReentry=" + active);
				helper.assertTrue(active, "threat expired during the re-entry reset");
			})
			.thenExecute(() -> attacker.setPos(outside.x, outside.y, outside.z))
			.thenIdle(10)
			.thenExecute(() -> helper.assertTrue(
				PatrolTestSupport.hasThreat(helper, "playerThreatResetsAndClears", attacker.getUUID()),
				"threat expired before the reset timeout"
			))
			.thenIdle(190)
			.thenExecute(() -> {
				boolean active = PatrolTestSupport.hasThreat(helper, "playerThreatResetsAndClears", attacker.getUUID());
				PatrolTestSupport.result("playerThreatResetsAndClears", "activeAfter200OutsideTicks=" + active);
				helper.assertFalse(active, "patrol threat did not clear after 200 outside ticks");
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = THREAT_TEST_LIMIT, padding = 64)
	public void playerThreatExpiresAfterDimensionTransfer(final GameTestHelper helper) {
		String test = "playerThreatExpiresAfterDimensionTransfer";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		Mob guard = patrols.stream().findFirst().orElse(null);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		if (guard == null || nether == null) {
			helper.fail("patrol guard or nether level is missing for the dimension threat test");
			return;
		}

		patrols.forEach(mob -> mob.setNoAi(true));
		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		attacker.setPos(guard.getX(), guard.getY(), guard.getZ());
		if (!PatrolTestSupport.startThreat(helper, test, attacker.getUUID())) {
			return;
		}

		attacker.setServerLevel(nether);
		attacker.setPos(guard.getX(), guard.getY(), guard.getZ());
		boolean samePosition = attacker.position().equals(guard.position());
		boolean differentDimension = attacker.level() != guard.level();
		PatrolTestSupport.detail(
			test,
			"guardDimension=" + guard.level().dimension().identifier() + " attackerDimension="
				+ attacker.level().dimension().identifier() + " samePosition=" + samePosition
		);
		helper.assertTrue(samePosition && differentDimension, "attacker transfer did not preserve its guard coordinates");

		int timeout = PatrolTestSupport.intConstant(helper, test, "PLAYER_THREAT_CLEAR_TICKS");
		helper.startSequence()
			.thenIdle(1)
			.thenExecute(() -> helper.assertTrue(
				PatrolTestSupport.hasThreat(helper, test, attacker.getUUID()),
				"threat cleared before the cross-dimension timeout"
			))
			.thenIdle(timeout)
			.thenExecute(() -> {
				boolean active = PatrolTestSupport.hasThreat(helper, test, attacker.getUUID());
				PatrolTestSupport.result(test, "waitedTicks=" + timeout + " activeAfterTransfer=" + active);
				helper.assertFalse(active, "threat did not clear after the attacker changed dimensions");
			})
			.thenSucceed();
	}

	@GameTest(padding = 64)
	public void cachedThreatTargetStopsAfterDimensionTransfer(final GameTestHelper helper) {
		String test = "cachedThreatTargetStopsAfterDimensionTransfer";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob guard = PatrolTestSupport.patrols(helper).stream().findFirst().orElse(null);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		if (guard == null || nether == null) {
			helper.fail("patrol guard or nether level is missing for cached target test");
			return;
		}

		guard.setNoAi(true);
		ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
		attacker.setPos(guard.getX(), guard.getY(), guard.getZ());
		if (!PatrolTestSupport.startThreat(helper, test, attacker.getUUID())) {
			return;
		}

		Boolean sameDimensionContinues = PatrolTestSupport.targetGoalContinues(helper, test, guard, attacker);
		if (sameDimensionContinues == null) {
			return;
		}

		guard.setTarget(attacker);
		attacker.setServerLevel(nether);
		attacker.setPos(guard.getX(), guard.getY(), guard.getZ());
		Boolean crossDimensionContinues = PatrolTestSupport.targetGoalContinues(helper, test, guard, attacker);
		if (crossDimensionContinues == null) {
			return;
		}

		boolean samePosition = attacker.position().equals(guard.position());
		PatrolTestSupport.result(
			test,
			"samePosition=" + samePosition + " sameDimensionContinues=" + sameDimensionContinues
				+ " crossDimensionContinues=" + crossDimensionContinues
		);
		helper.assertTrue(Boolean.TRUE.equals(sameDimensionContinues), "target fixture cannot continue in the guard's dimension");
		helper.assertTrue(samePosition, "attacker transfer did not preserve its guard coordinates");
		helper.assertFalse(Boolean.TRUE.equals(crossDimensionContinues), "cached patrol target continued across dimensions");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void retriesFailedPatrolInsertionWithoutRespawningDeadMembers(final GameTestHelper helper) {
		String test = "retriesFailedPatrolInsertionWithoutRespawningDeadMembers";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}

		int rejectedInsertions;
		PatrolInsertionTestGate.rejectNextPatrolInsertion();
		try {
			if (!PatrolTestSupport.scanChunk(helper, test)) {
				return;
			}
			rejectedInsertions = PatrolInsertionTestGate.rejectedPatrolInsertions();
		} finally {
			PatrolInsertionTestGate.clear();
		}

		List<UUID> beforeRetry = PatrolTestSupport.patrols(helper).stream().map(Mob::getUUID).toList();
		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		List<Mob> afterRetry = PatrolTestSupport.patrols(helper);
		List<UUID> afterRetryIds = afterRetry.stream().map(Mob::getUUID).toList();
		Mob survivor = afterRetry.stream().findFirst().orElse(null);
		if (survivor == null) {
			helper.fail("no patrol member survived the rejected insertion");
			return;
		}

		UUID survivorId = survivor.getUUID();
		survivor.kill(helper.getLevel());
		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		List<UUID> afterDeathIds = PatrolTestSupport.patrols(helper).stream().map(Mob::getUUID).toList();
		boolean retained = afterRetryIds.containsAll(beforeRetry);
		boolean deadMemberRespawned = afterDeathIds.contains(survivorId);
		PatrolTestSupport.result(
			test,
			"rejectedInsertions=" + rejectedInsertions + " beforeRetry=" + beforeRetry.size()
				+ " afterRetry=" + afterRetryIds.size() + " afterDeath=" + afterDeathIds.size()
				+ " retained=" + retained + " deadMemberRespawned=" + deadMemberRespawned
		);
		helper.assertValueEqual(Integer.valueOf(rejectedInsertions), Integer.valueOf(1), "rejected patrol insertions");
		helper.assertValueEqual(
			Integer.valueOf(beforeRetry.size()), Integer.valueOf(PATROL_COUNT - 1), "members after rejected insertion"
		);
		helper.assertValueEqual(Integer.valueOf(afterRetryIds.size()), Integer.valueOf(PATROL_COUNT), "roster after insertion retry");
		helper.assertTrue(retained, "a patrol member changed identity during insertion retry");
		helper.assertValueEqual(
			Integer.valueOf(afterDeathIds.size()), Integer.valueOf(PATROL_COUNT - 1), "roster after one member died"
		);
		helper.assertFalse(deadMemberRespawned, "a dead patrol member respawned after a later chunk scan");
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void patrolDoesNotSpawnWhenNoSafeLoadedPositionExists(final GameTestHelper helper) {
		String test = "patrolDoesNotSpawnWhenNoSafeLoadedPositionExists";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}

		BlockPos anchor = PatrolTestSupport.anchor(helper);
		Mob villager = PatrolTestSupport.villager(helper);
		if (villager == null) {
			helper.fail("test villager is missing before safe-position setup");
			return;
		}
		villager.kill(helper.getLevel());

		int minX = SectionPos.blockToSectionCoord(anchor.getX()) * 16;
		int minZ = SectionPos.blockToSectionCoord(anchor.getZ()) * 16;
		for (int x = minX; x < minX + 16; x++) {
			for (int z = minZ; z < minZ + 16; z++) {
				BlockPos floor = new BlockPos(x, anchor.getY() - 1, z);
				BlockPos spawn = new BlockPos(x, anchor.getY(), z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				if (!spawn.equals(anchor)) {
					helper.getLevel().setBlockAndUpdate(spawn, Blocks.STONE.defaultBlockState());
				}
			}
		}

		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}
		int membersWithoutSafePosition = PatrolTestSupport.patrols(helper).size();

		for (int x = minX; x < minX + 16; x++) {
			for (int z = minZ; z < minZ + 16; z++) {
				BlockPos spawn = new BlockPos(x, anchor.getY(), z);
				if (!spawn.equals(anchor)) {
					helper.getLevel().setBlockAndUpdate(spawn, Blocks.AIR.defaultBlockState());
				}
			}
		}
		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		int membersAfterSitesReturn = PatrolTestSupport.patrols(helper).size();
		PatrolTestSupport.result(
			test,
			"membersWithoutSafePosition=" + membersWithoutSafePosition + " membersAfterSitesReturn=" + membersAfterSitesReturn
		);
		helper.assertValueEqual(
			Integer.valueOf(membersWithoutSafePosition), Integer.valueOf(0), "patrols with no safe loaded spawn position"
		);
		helper.assertValueEqual(
			Integer.valueOf(membersAfterSitesReturn), Integer.valueOf(PATROL_COUNT), "patrols after safe sites return"
		);
		helper.succeed();
	}

	@GameTest(padding = 64)
	public void patrolRejectsLowCeilingSpawnSites(final GameTestHelper helper) {
		String test = "patrolRejectsLowCeilingSpawnSites";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}

		BlockPos anchor = PatrolTestSupport.anchor(helper);
		Mob villager = PatrolTestSupport.villager(helper);
		if (villager == null) {
			helper.fail("test villager is missing before low-ceiling setup");
			return;
		}
		villager.kill(helper.getLevel());

		BlockPos candidate = candidateInsideAnchorChunk(anchor);
		boolean candidateInAnchorChunk = sameChunk(anchor, candidate);
		PatrolTestSupport.detail(test, "anchor=" + anchor + " candidate=" + candidate + " insideAnchorChunk=" + candidateInAnchorChunk);
		helper.assertTrue(candidateInAnchorChunk, "low-ceiling candidate is outside the anchor chunk");
		prepareSingleSpawnSite(helper, anchor, candidate, Blocks.STONE, Blocks.STONE);
		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		int membersAtLowCeiling = PatrolTestSupport.patrols(helper).size();
		PatrolTestSupport.result(test, "membersAtLowCeiling=" + membersAtLowCeiling + " candidate=" + candidate);
		helper.assertValueEqual(Integer.valueOf(membersAtLowCeiling), Integer.valueOf(0), "patrols spawned below a low ceiling");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end", padding = 64)
	public void patrolRejectsUnsafeSpawnSupport(final GameTestHelper helper) {
		String test = "patrolRejectsUnsafeSpawnSupport";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}

		BlockPos anchor = PatrolTestSupport.anchor(helper);
		Mob villager = PatrolTestSupport.villager(helper);
		if (villager == null) {
			helper.fail("test villager is missing before unsafe-support setup");
			return;
		}
		villager.kill(helper.getLevel());

		BlockPos candidate = candidateInsideAnchorChunk(anchor);
		boolean candidateInAnchorChunk = sameChunk(anchor, candidate);
		PatrolTestSupport.detail(test, "anchor=" + anchor + " candidate=" + candidate + " insideAnchorChunk=" + candidateInAnchorChunk);
		helper.assertTrue(candidateInAnchorChunk, "unsafe-support candidate is outside the anchor chunk");
		prepareSingleSpawnSite(helper, anchor, candidate, Blocks.POWDER_SNOW, Blocks.AIR);
		if (!PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		int membersAtUnsafeSupport = PatrolTestSupport.patrols(helper).size();
		PatrolTestSupport.result(test, "membersAtUnsafeSupport=" + membersAtUnsafeSupport + " candidate=" + candidate);
		helper.assertValueEqual(Integer.valueOf(membersAtUnsafeSupport), Integer.valueOf(0), "patrols spawned on unsafe support");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void lastGuardDeathClearsWithoutRespawn(final GameTestHelper helper) {
		if (!PatrolTestSupport.loadVillage(helper, "lastGuardDeathClearsWithoutRespawn", false)) {
			return;
		}

		List<Mob> patrols = PatrolTestSupport.patrols(helper);
		helper.assertValueEqual(Integer.valueOf(patrols.size()), Integer.valueOf(PATROL_COUNT), "initial patrol size");
		UUID attackerId = UUID.randomUUID();
		if (!PatrolTestSupport.startThreat(helper, "lastGuardDeathClearsWithoutRespawn", attackerId)) {
			return;
		}
		Mob finalGuard = patrols.getLast();
		patrols.subList(0, patrols.size() - 1).forEach(mob -> mob.kill(helper.getLevel()));
		helper.assertTrue(finalGuard.isAlive(), "the last guard died before the final kill");
		finalGuard.kill(helper.getLevel());
		if (!PatrolTestSupport.scanChunk(helper, "lastGuardDeathClearsWithoutRespawn")) {
			return;
		}

		int remaining = PatrolTestSupport.patrols(helper).size();
		boolean threatActive = PatrolTestSupport.hasThreat(helper, "lastGuardDeathClearsWithoutRespawn", attackerId);
		PatrolTestSupport.result("lastGuardDeathClearsWithoutRespawn",
			"remaining=" + remaining + " threatActive=" + threatActive);
		helper.assertValueEqual(Integer.valueOf(remaining), Integer.valueOf(0), "guards after final death and chunk scan");
		helper.assertFalse(threatActive, "threat remained after the last guard died");
		helper.succeed();
	}

	@GameTest(maxTicks = 800, padding = 64)
	public void pillagerCrossbowsSurviveChunkReload(final GameTestHelper helper) {
		String test = "pillagerCrossbowsSurviveChunkReload";
		BlockPos anchor = PatrolTestSupport.findDistantVillageAnchor(helper, test);
		if (anchor == null) {
			return;
		}

		int chunkX = SectionPos.blockToSectionCoord(anchor.getX());
		int chunkZ = SectionPos.blockToSectionCoord(anchor.getZ());
		helper.getLevel().setChunkForced(chunkX, chunkZ, true);
		helper.getLevel().getChunkAt(anchor);
		if (!PatrolTestSupport.loadVillageAt(helper, test, anchor)) {
			return;
		}

		List<Mob> beforeMembers = PatrolTestSupport.activePatrols(helper, test);
		List<Mob> beforePillagers = beforeMembers.stream()
			.filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:pillager"))
			.toList();
		List<UUID> beforeIds = beforePillagers.stream().map(Mob::getUUID)
			.sorted(Comparator.comparing(UUID::toString)).toList();
		boolean crossbowsBefore = beforePillagers.size() == 5 && beforePillagers.stream()
			.allMatch(mob -> mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
				.is(net.minecraft.world.item.Items.CROSSBOW));
		for (Mob member : beforeMembers) {
			member.setNoAi(true);
			member.getNavigation().stop();
		}
		PatrolTestSupport.detail(test, "membersBefore=" + beforeMembers.size()
			+ " pillagersBefore=" + beforePillagers.size() + " crossbowsBefore=" + crossbowsBefore);
		helper.assertValueEqual(Integer.valueOf(beforeMembers.size()), Integer.valueOf(PATROL_COUNT), "initial patrol size");
		helper.assertTrue(crossbowsBefore, "not all five Pillagers held crossbows before chunk unload");

		UUID villageId = PatrolTestSupport.villageId(helper);
		boolean[] resultLogged = {false};
		int[] stage = {0};
		boolean[] entitiesAbsent = {false};
		boolean[] idsMatch = {false};
		boolean[] crossbowsAfter = {false};
		boolean forced = helper.getLevel().setChunkForced(chunkX, chunkZ, false);
		PatrolTestSupport.detail(test, "village=" + villageId + " chunk=" + chunkX + "," + chunkZ
			+ " forcedBeforeUnload=" + forced + " pillagerIds=" + beforeIds);
		helper.assertTrue(forced, "village chunk was not forced before unload");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertFalse(
				helper.getLevel().getChunkSource().hasChunk(chunkX, chunkZ),
				"village chunk did not unload after ticket removal"
			))
			.thenExecute(() -> {
				stage[0] = 1;
				entitiesAbsent[0] = beforeIds.stream()
					.allMatch(id -> helper.getLevel().getEntityInAnyDimension(id) == null);
				helper.assertTrue(entitiesAbsent[0], "a Pillager remained loaded after chunk unload");
				helper.getLevel().setChunkForced(chunkX, chunkZ, true);
				helper.getLevel().getChunkAt(anchor);
				stage[0] = 2;
			})
			.thenWaitUntil(() -> {
				List<Mob> afterMembers = PatrolTestSupport.activePatrols(helper, test);
				List<Mob> afterPillagers = afterMembers.stream()
					.filter(mob -> PatrolTestSupport.typeId(mob).equals("minecraft:pillager"))
					.toList();
				List<UUID> afterIds = afterPillagers.stream().map(Mob::getUUID)
					.sorted(Comparator.comparing(UUID::toString)).toList();
				idsMatch[0] = beforeIds.equals(afterIds) && afterPillagers.size() == 5;
				crossbowsAfter[0] = afterPillagers.stream()
					.allMatch(mob -> mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
						.is(net.minecraft.world.item.Items.CROSSBOW));
				helper.assertTrue(idsMatch[0], "Pillager UUIDs changed after chunk reload");
				helper.assertTrue(crossbowsAfter[0], "Pillager crossbows changed after chunk reload");
				PatrolTestSupport.result(test, "passed=true pillagerCount=" + afterPillagers.size()
					+ " sameIds=" + idsMatch[0] + " crossbowsBefore=" + crossbowsBefore
					+ " crossbowsAfter=" + crossbowsAfter[0] + " entitiesAbsent=" + entitiesAbsent[0]);
				resultLogged[0] = true;
			})
			.thenSucceed();
		helper.runAfterDelay(799, () -> {
			if (!resultLogged[0]) {
				PatrolTestSupport.result(test, "passed=false stage=" + stage[0]
					+ " chunkLoaded=" + helper.getLevel().getChunkSource().hasChunk(chunkX, chunkZ)
					+ " entitiesAbsent=" + entitiesAbsent[0] + " idsMatch=" + idsMatch[0]
					+ " crossbowsBefore=" + crossbowsBefore + " crossbowsAfter=" + crossbowsAfter[0]);
			}
		});
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void staleEvokerLeavesWhenItLoads(final GameTestHelper helper) {
		String test = "staleEvokerLeavesWhenItLoads";
		Mob member = loadLegacyMember(helper, test, EntityTypes.EVOKER, PatrolState.Role.EVOKER);
		if (member == null) {
			return;
		}

		UUID id = member.getUUID();
		helper.succeedWhen(() -> {
			boolean recordRemoved = VillageWorldData.get(helper.getLevel()).villageForMember(id).isEmpty();
			helper.assertTrue(member.isRemoved() && recordRemoved,
				"the pre-W021 Evoker stayed: removed=" + member.isRemoved() + " recordRemoved=" + recordRemoved);
			PatrolTestSupport.result(test, "removed=true recordRemoved=true tick=" + helper.getTick());
		});
	}

	@GameTest(maxTicks = 200, padding = 64)
	public void patrolPillagerStaysWhenItLoads(final GameTestHelper helper) {
		String test = "patrolPillagerStaysWhenItLoads";
		Mob member = loadLegacyMember(helper, test, EntityTypes.PILLAGER, PatrolState.Role.PILLAGER);
		if (member == null) {
			return;
		}

		// The same load with a W021 role must keep the member, so only the retired roles are removed.
		helper.runAfterDelay(20, () -> {
			boolean recordKept = VillageWorldData.get(helper.getLevel()).villageForMember(member.getUUID()).isPresent();
			boolean alive = !member.isRemoved();
			member.discard();
			PatrolTestSupport.result(test, "alive=" + alive + " recordKept=" + recordKept);
			helper.assertTrue(alive && recordKept, "a patrol Pillager was removed on load: alive=" + alive + " recordKept=" + recordKept);
			helper.succeed();
		});
	}

	/** Adds one marked patrol member of a given role to a loaded village, the way a saved member reloads. */
	private static @Nullable Mob loadLegacyMember(
		final GameTestHelper helper,
		final String test,
		final net.minecraft.world.entity.EntityType<? extends Mob> type,
		final PatrolState.Role role
	) {
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return null;
		}

		UUID villageId = PatrolTestSupport.villageId(helper);
		VillageWorldData data = VillageWorldData.get(helper.getLevel());
		VillageWorldData.VillageRecord village = data.village(villageId).orElse(null);
		if (village == null) {
			helper.fail("the loaded village has no record");
			return null;
		}

		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.EVENT);
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setPersistenceRequired();
		PatrolState.attach(mob, villageId, role);
		((MobPatrolAccess) mob).setPatrolMarker(true);
		data.addMember(village, mob.getUUID(), role.name(), mob.blockPosition());
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	@GameTest(maxTicks = 5, required = false, padding = 64)
	public void patrolRetaliatesAgainstSurvivalPlayerRequiresManualTest(final GameTestHelper helper) {
		PatrolTestSupport.result("patrolRetaliatesAgainstSurvivalPlayerRequiresManualTest", "manual=survival_player_required");
		McStellaVillages.LOGGER.info(
			"[MANUAL] case=patrolRetaliatesAgainstSurvivalPlayerRequiresManualTest gap=gametest_mock_player_is_creative"
		);
		helper.fail("[MANUAL] use a survival player to attack a village villager or golem");
	}

	private static @Nullable Mob spawnTestCreeper(
		final GameTestHelper helper,
		final String test,
		final Mob guard,
		final double distance
	) {
		var created = EntityTypes.CREEPER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (!(created instanceof Mob hostile)) {
			helper.fail("test creeper could not be created");
			return null;
		}

		hostile.setNoAi(true);
		hostile.setNoGravity(true);
		hostile.setPersistenceRequired();
		hostile.setPos(guard.getX(), guard.getY() + distance, guard.getZ());
		BlockPos guardBlock = guard.blockPosition();
		BlockPos hostileBlock = hostile.blockPosition();
		int clearedColumnBlocks = 0;
		for (int y = guardBlock.getY() + 1; y <= hostileBlock.getY() + 2; y++) {
			BlockPos columnBlock = new BlockPos(guardBlock.getX(), y, guardBlock.getZ());
			if (!helper.getLevel().getBlockState(columnBlock).isAir()) {
				helper.getLevel().setBlockAndUpdate(columnBlock, Blocks.AIR.defaultBlockState());
				clearedColumnBlocks++;
			}
		}
		boolean inserted = helper.getLevel().addFreshEntity(hostile);
		PatrolTestSupport.detail(
			test,
			"hostile=" + hostile.getUUID() + " distance=" + guard.distanceTo(hostile)
				+ " clearedColumnBlocks=" + clearedColumnBlocks + " inserted=" + inserted
		);
		if (!inserted) {
			helper.fail("test creeper could not be inserted");
			return null;
		}
		return hostile;
	}

	private static void prepareMovementLane(
		final GameTestHelper helper,
		final BlockPos anchor,
		final int maxOffset
	) {
		int minX = anchor.getX() - 8;
		int maxX = anchor.getX() + maxOffset;
		int minZ = anchor.getZ() - 3;
		int maxZ = anchor.getZ() + 3;
		int minChunkX = SectionPos.blockToSectionCoord(minX);
		int maxChunkX = SectionPos.blockToSectionCoord(maxX);
		int minChunkZ = SectionPos.blockToSectionCoord(minZ);
		int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				helper.getLevel().setChunkForced(chunkX, chunkZ, true);
			}
		}

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos floor = new BlockPos(x, anchor.getY() - 1, z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int y = 0; y < 3; y++) {
					BlockPos clear = floor.above(y + 1);
					if (!clear.equals(anchor)) {
						helper.getLevel().setBlockAndUpdate(clear, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void prepareForcedChunkSite(
		final GameTestHelper helper,
		final BlockPos center,
		final int chunkX,
		final int chunkZ
	) {
		helper.getLevel().setChunkForced(chunkX, chunkZ, true);
		helper.getLevel().getChunkAt(center);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos floor = center.offset(dx, -1, dz);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int y = 0; y < 3; y++) {
					helper.getLevel().setBlockAndUpdate(
						floor.above(y + 1), Blocks.AIR.defaultBlockState()
					);
				}
			}
		}
	}

	private static boolean sameChunk(final BlockPos first, final BlockPos second) {
		return SectionPos.blockToSectionCoord(first.getX()) == SectionPos.blockToSectionCoord(second.getX())
			&& SectionPos.blockToSectionCoord(first.getZ()) == SectionPos.blockToSectionCoord(second.getZ());
	}

	private static BlockPos candidateInsideAnchorChunk(final BlockPos anchor) {
		int offsetX = SectionPos.sectionRelative(anchor.getX()) < SectionPos.SECTION_HALF_SIZE ? 1 : -1;
		int offsetZ = SectionPos.sectionRelative(anchor.getZ()) < SectionPos.SECTION_HALF_SIZE ? 1 : -1;
		return anchor.offset(offsetX, 0, offsetZ);
	}

	private static void prepareSingleSpawnSite(
		final GameTestHelper helper,
		final BlockPos anchor,
		final BlockPos candidate,
		final Block support,
		final Block ceiling
	) {
		int chunkMinX = SectionPos.blockToSectionCoord(anchor.getX()) * 16;
		int chunkMinZ = SectionPos.blockToSectionCoord(anchor.getZ()) * 16;
		for (int x = chunkMinX; x < chunkMinX + 16; x++) {
			for (int z = chunkMinZ; z < chunkMinZ + 16; z++) {
				BlockPos floor = new BlockPos(x, anchor.getY() - 1, z);
				BlockPos spawn = new BlockPos(x, anchor.getY(), z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				if (!spawn.equals(anchor)) {
					helper.getLevel().setBlockAndUpdate(
						spawn, spawn.equals(candidate) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState()
					);
				}
				helper.getLevel().setBlockAndUpdate(
					spawn.above(), spawn.equals(candidate) ? ceiling.defaultBlockState() : Blocks.STONE.defaultBlockState()
				);
			}
		}
		helper.getLevel().setBlockAndUpdate(candidate.below(), support.defaultBlockState());
	}
}
