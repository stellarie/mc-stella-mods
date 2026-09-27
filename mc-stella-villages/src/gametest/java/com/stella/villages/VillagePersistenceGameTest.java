package com.stella.villages;

import com.mojang.serialization.Codec;
import com.stella.villages.data.VillageWorldData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.saveddata.maps.MapId;

public final class VillagePersistenceGameTest {
	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void legacyCompleteRosterReopensOnLoad(final GameTestHelper helper) {
		// A pre-W021 save carries no roster version, so a sealed roster must reopen to gain its new Pillagers.
		boolean started = sealedRosterAfterLoad(helper, true);
		PatrolTestSupport.result("legacyCompleteRosterReopensOnLoad", "rosterStartedAfterLoad=" + started);
		helper.assertFalse(started, "a legacy sealed roster stayed sealed, so its Pillagers can never be topped up");
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void currentRosterStaysSealedOnLoad(final GameTestHelper helper) {
		boolean started = sealedRosterAfterLoad(helper, false);
		PatrolTestSupport.result("currentRosterStaysSealedOnLoad", "rosterStartedAfterLoad=" + started);
		helper.assertTrue(started, "a current sealed roster reopened on load and would respawn its dead");
		helper.succeed();
	}

	/** Seals a pre-W021 roster, saves it, optionally strips the roster version as an old save lacks it, and reloads. */
	private static boolean sealedRosterAfterLoad(final GameTestHelper helper, final boolean stripVersion) {
		VillageWorldData data = VillageWorldData.empty();
		VillageWorldData.VillageRecord village = data.findOrCreate(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3)));
		for (String role : List.of("HEAD", "VINDICATOR", "VINDICATOR", "VINDICATOR", "PILLAGER", "PILLAGER", "PILLAGER", "RAVAGER", "EVOKER", "WITCH")) {
			data.addMember(village, UUID.randomUUID(), role, helper.absolutePos(new BlockPos(4, 1, 4)));
		}
		data.markRosterStarted(village);
		CompoundTag saved = data.serializeState();
		if (stripVersion) {
			for (Tag entry : saved.getListOrEmpty("villages")) {
				if (entry instanceof CompoundTag villageTag) {
					villageTag.remove("roster_version");
				}
			}
		}
		return VillageWorldData.loadState(saved).village(village.id()).map(VillageWorldData.VillageRecord::rosterStarted).orElse(true);
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void villageStateRoundTrips(final GameTestHelper helper) {
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
			Object record = dataClass.getMethod("findOrCreate", ResourceKey.class, BlockPos.class)
				.invoke(original, helper.getLevel().dimension(), anchor);
			Object id = record.getClass().getMethod("id").invoke(record);
			CompoundTag before = (CompoundTag) dataClass.getMethod("serializeState").invoke(original);
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			Tag encoded = codec.encodeStart(ops, original).getOrThrow();
			Object decoded = codec.parse(ops, encoded).getOrThrow();
			CompoundTag after = (CompoundTag) dataClass.getMethod("serializeState").invoke(decoded);

			PatrolTestSupport.result("villageStateRoundTrips", "villageId=" + id + " anchor=" + anchor);
			helper.assertValueEqual(after, before, "village IDs and anchor positions after codec round-trip");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result("villageStateRoundTrips", "codec=MISSING detail=" + error);
			helper.fail("missing_saved_data_codec: " + error);
		}
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void villageMembersAndThreatsRoundTrip(final GameTestHelper helper) {
		String test = "villageMembersAndThreatsRoundTrip";
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
			BlockPos memberPosition = helper.absolutePos(new BlockPos(12, 1, 7));
			Object village = dataClass.getMethod("findOrCreate", ResourceKey.class, BlockPos.class)
				.invoke(original, helper.getLevel().dimension(), anchor);
			Class<?> villageClass = village.getClass();
			UUID villageId = (UUID) villageClass.getMethod("id").invoke(village);
			UUID memberId = UUID.randomUUID();
			UUID deadMemberId = UUID.randomUUID();
			UUID attackerId = UUID.randomUUID();
			BlockPos deadMemberPosition = helper.absolutePos(new BlockPos(9, 1, 12));

			dataClass.getMethod("markRosterStarted", villageClass).invoke(original, village);
			dataClass.getMethod("addMember", villageClass, UUID.class, String.class, BlockPos.class)
				.invoke(original, village, memberId, "PILLAGER", memberPosition);
			dataClass.getMethod("addMember", villageClass, UUID.class, String.class, BlockPos.class)
				.invoke(original, village, deadMemberId, "WITCH", deadMemberPosition);
			dataClass.getMethod("markMemberDead", UUID.class).invoke(original, deadMemberId);
			dataClass.getMethod("startThreat", villageClass, UUID.class).invoke(original, village, attackerId);
			dataClass.getMethod("updateThreat", villageClass, UUID.class, int.class)
				.invoke(original, village, attackerId, 37);

			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			Tag encoded = codec.encodeStart(ops, original).getOrThrow();
			Object decoded = codec.parse(ops, encoded).getOrThrow();
			Object optionalValue = dataClass.getMethod("village", UUID.class).invoke(decoded, villageId);
			if (!(optionalValue instanceof Optional<?> restoredOption) || restoredOption.isEmpty()) {
				PatrolTestSupport.result(test, "villageId=" + villageId + " restored=false");
				helper.fail("village record did not survive member and threat codec round-trip");
				return;
			}

			Object restoredVillage = restoredOption.get();
			Class<?> restoredClass = restoredVillage.getClass();
			List<?> restoredMembers = (List<?>) restoredClass.getMethod("members").invoke(restoredVillage);
			List<?> restoredThreats = (List<?>) restoredClass.getMethod("threats").invoke(restoredVillage);
			Object restoredMember = null;
			Object restoredDeadMember = null;
			for (Object member : restoredMembers) {
				UUID restoredId = (UUID) member.getClass().getMethod("id").invoke(member);
				if (memberId.equals(restoredId)) {
					restoredMember = member;
				} else if (deadMemberId.equals(restoredId)) {
					restoredDeadMember = member;
				}
			}
			Object restoredThreat = null;
			for (Object threat : restoredThreats) {
				if (attackerId.equals(threat.getClass().getMethod("attackerId").invoke(threat))) {
					restoredThreat = threat;
					break;
				}
			}

			boolean memberMatches = restoredMember != null
				&& "PILLAGER".equals(restoredMember.getClass().getMethod("role").invoke(restoredMember))
				&& memberPosition.equals(restoredMember.getClass().getMethod("lastPosition").invoke(restoredMember))
				&& Boolean.TRUE.equals(restoredMember.getClass().getMethod("alive").invoke(restoredMember));
			boolean deadMemberMatches = restoredDeadMember != null
				&& "WITCH".equals(restoredDeadMember.getClass().getMethod("role").invoke(restoredDeadMember))
				&& deadMemberPosition.equals(restoredDeadMember.getClass().getMethod("lastPosition").invoke(restoredDeadMember))
				&& Boolean.FALSE.equals(restoredDeadMember.getClass().getMethod("alive").invoke(restoredDeadMember));
			boolean threatMatches = restoredThreat != null
				&& attackerId.equals(restoredThreat.getClass().getMethod("attackerId").invoke(restoredThreat))
				&& Integer.valueOf(37).equals(restoredThreat.getClass().getMethod("outsideTicks").invoke(restoredThreat));
			UUID restoredVillageId = (UUID) restoredClass.getMethod("id").invoke(restoredVillage);
			BlockPos restoredAnchor = (BlockPos) restoredClass.getMethod("anchor").invoke(restoredVillage);
			boolean rosterStarted = (Boolean) restoredClass.getMethod("rosterStarted").invoke(restoredVillage);
			boolean stateMatches = villageId.equals(restoredVillageId) && anchor.equals(restoredAnchor)
				&& rosterStarted && restoredMembers.size() == 2 && restoredThreats.size() == 1
				&& memberMatches && deadMemberMatches && threatMatches;

			PatrolTestSupport.result(test, "villageId=" + restoredVillageId + " anchor=" + restoredAnchor
				+ " rosterStarted=" + rosterStarted + " memberCount=" + restoredMembers.size()
				+ " memberId=" + memberId + " memberRole=" + (restoredMember == null ? "missing"
					: restoredMember.getClass().getMethod("role").invoke(restoredMember))
				+ " memberPosition=" + (restoredMember == null ? "missing"
					: restoredMember.getClass().getMethod("lastPosition").invoke(restoredMember))
				+ " memberAlive=" + (restoredMember == null ? "missing"
					: restoredMember.getClass().getMethod("alive").invoke(restoredMember))
				+ " memberMatches=" + memberMatches + " deadMemberId=" + deadMemberId
				+ " deadMemberRole=" + (restoredDeadMember == null ? "missing"
					: restoredDeadMember.getClass().getMethod("role").invoke(restoredDeadMember))
				+ " deadMemberPosition=" + (restoredDeadMember == null ? "missing"
					: restoredDeadMember.getClass().getMethod("lastPosition").invoke(restoredDeadMember))
				+ " deadMemberAlive=" + (restoredDeadMember == null ? "missing"
					: restoredDeadMember.getClass().getMethod("alive").invoke(restoredDeadMember))
				+ " deadMemberMatches=" + deadMemberMatches + " threatCount=" + restoredThreats.size()
				+ " attackerId=" + attackerId + " outsideTicks=" + (restoredThreat == null ? "missing"
					: restoredThreat.getClass().getMethod("outsideTicks").invoke(restoredThreat))
				+ " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "village codec did not preserve member and threat state");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "codec=MISSING detail=" + error);
			helper.fail("missing_saved_data_codec: " + error);
		}
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void missionLedgerRoundTrips(final GameTestHelper helper) {
		String test = "missionLedgerRoundTrips";
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			Class<?> missionClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionRecord");
			Class<?> statusClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionStatus");
			java.lang.reflect.Constructor<?> constructor = missionClass.getConstructor(
				UUID.class, UUID.class, Identifier.class, BlockPos.class, Optional.class, statusClass
			);
			Object pendingStatus = statusClass.getField("TARGET_PENDING").get(null);
			Object activeStatus = statusClass.getField("ACTIVE").get(null);

			UUID pendingId = UUID.randomUUID();
			UUID pendingOwner = UUID.randomUUID();
			UUID activeId = UUID.randomUUID();
			UUID activeOwner = UUID.randomUUID();
			UUID activeEntityId = UUID.randomUUID();
			Identifier pendingType = Identifier.parse("minecraft:zombie");
			Identifier activeType = Identifier.parse("minecraft:pillager");
			BlockPos pendingPosition = helper.absolutePos(new BlockPos(7, 1, 5));
			BlockPos activePosition = helper.absolutePos(new BlockPos(13, 1, 9));
			Object pending = constructor.newInstance(
				pendingId, pendingOwner, pendingType, pendingPosition, Optional.empty(), pendingStatus
			);
			Object active = constructor.newInstance(
				activeId, activeOwner, activeType, activePosition, Optional.of(activeEntityId), activeStatus
			);
			dataClass.getMethod("addMission", missionClass).invoke(original, pending);
			dataClass.getMethod("addMission", missionClass).invoke(original, active);

			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			Tag encoded = codec.encodeStart(ops, original).getOrThrow();
			Object decoded = codec.parse(ops, encoded).getOrThrow();
			List<?> restoredMissions = (List<?>) dataClass.getMethod("missions").invoke(decoded);
			Object restoredPending = null;
			Object restoredActive = null;
			for (Object mission : restoredMissions) {
				UUID id = (UUID) missionClass.getMethod("id").invoke(mission);
				if (pendingId.equals(id)) {
					restoredPending = mission;
				} else if (activeId.equals(id)) {
					restoredActive = mission;
				}
			}

			boolean pendingMatches = restoredPending != null
				&& pendingOwner.equals(missionClass.getMethod("ownerId").invoke(restoredPending))
				&& pendingType.equals(missionClass.getMethod("targetType").invoke(restoredPending))
				&& pendingPosition.equals(missionClass.getMethod("targetPosition").invoke(restoredPending))
				&& Optional.empty().equals(missionClass.getMethod("entityId").invoke(restoredPending))
				&& pendingStatus.equals(missionClass.getMethod("status").invoke(restoredPending));
			boolean activeMatches = restoredActive != null
				&& activeOwner.equals(missionClass.getMethod("ownerId").invoke(restoredActive))
				&& activeType.equals(missionClass.getMethod("targetType").invoke(restoredActive))
				&& activePosition.equals(missionClass.getMethod("targetPosition").invoke(restoredActive))
				&& Optional.of(activeEntityId).equals(missionClass.getMethod("entityId").invoke(restoredActive))
				&& activeStatus.equals(missionClass.getMethod("status").invoke(restoredActive));
			boolean stateMatches = restoredMissions.size() == 2 && pendingMatches && activeMatches;

			PatrolTestSupport.result(test, "missionCount=" + restoredMissions.size()
				+ " pendingId=" + pendingId + " pendingOwner=" + pendingOwner + " pendingType=" + pendingType
				+ " pendingPosition=" + pendingPosition + " pendingEntityId=none pendingStatus=" + pendingStatus
				+ " pendingMatches=" + pendingMatches + " activeId=" + activeId + " activeOwner=" + activeOwner
				+ " activeType=" + activeType + " activePosition=" + activePosition
				+ " activeEntityId=" + activeEntityId + " activeStatus=" + activeStatus
				+ " activeMatches=" + activeMatches + " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "mission ledger codec did not preserve mission state");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "ledger=MISSING detail=" + error);
			helper.fail("missing_mission_ledger: " + error);
		}
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void missionLedgerSkipsInvalidTargetPositions(final GameTestHelper helper) {
		String test = "missionLedgerSkipsInvalidTargetPositions";
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			Class<?> missionClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionRecord");
			Class<?> statusClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionStatus");
			java.lang.reflect.Constructor<?> constructor = missionClass.getConstructor(
				UUID.class, UUID.class, Identifier.class, BlockPos.class, Optional.class, statusClass
			);
			Object activeStatus = statusClass.getField("ACTIVE").get(null);
			UUID validId = UUID.randomUUID();
			UUID missingPositionId = UUID.randomUUID();
			UUID malformedPositionId = UUID.randomUUID();
			UUID ownerId = UUID.randomUUID();
			UUID entityId = UUID.randomUUID();
			BlockPos validPosition = helper.absolutePos(new BlockPos(11, 1, 9));
			BlockPos invalidPosition = helper.absolutePos(new BlockPos(15, 1, 13));
			Identifier targetType = Identifier.parse("minecraft:zombie");
			Object validMission = constructor.newInstance(
				validId, ownerId, targetType, validPosition, Optional.of(entityId), activeStatus
			);
			Object missingPositionMission = constructor.newInstance(
				missingPositionId, ownerId, targetType, invalidPosition, Optional.empty(), activeStatus
			);
			Object malformedPositionMission = constructor.newInstance(
				malformedPositionId, ownerId, targetType, invalidPosition, Optional.empty(), activeStatus
			);
			dataClass.getMethod("addMission", missionClass).invoke(original, validMission);
			dataClass.getMethod("addMission", missionClass).invoke(original, missingPositionMission);
			dataClass.getMethod("addMission", missionClass).invoke(original, malformedPositionMission);

			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			CompoundTag missingPositionRoot = (CompoundTag) codec.encodeStart(ops, original).getOrThrow();
			CompoundTag malformedPositionRoot = (CompoundTag) codec.encodeStart(ops, original).getOrThrow();
			CompoundTag missingPositionTag = null;
			for (Tag value : missingPositionRoot.getListOrEmpty("missions")) {
				if (value instanceof CompoundTag missionTag
					&& missingPositionId.toString().equals(missionTag.getStringOr("id", ""))) {
					missingPositionTag = missionTag;
					break;
				}
			}
			CompoundTag malformedPositionTag = null;
			for (Tag value : malformedPositionRoot.getListOrEmpty("missions")) {
				if (value instanceof CompoundTag missionTag
					&& malformedPositionId.toString().equals(missionTag.getStringOr("id", ""))) {
					malformedPositionTag = missionTag;
					break;
				}
			}
			if (missingPositionTag == null || malformedPositionTag == null) {
				PatrolTestSupport.result(test, "missingEntry=" + (missingPositionTag != null)
					+ " malformedEntry=" + (malformedPositionTag != null));
				helper.fail("mission codec did not serialize corrupt-position fixtures");
				return;
			}
			missingPositionTag.remove("target_position");
			malformedPositionTag.putString("target_position", "not a block position");

			Object decodedMissingPosition = codec.parse(ops, missingPositionRoot).getOrThrow();
			Object decodedMalformedPosition = codec.parse(ops, malformedPositionRoot).getOrThrow();
			List<?> missingMissions = (List<?>) dataClass.getMethod("missions").invoke(decodedMissingPosition);
			List<?> malformedMissions = (List<?>) dataClass.getMethod("missions").invoke(decodedMalformedPosition);
			Optional<?> validAfterMissing = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMissingPosition, validId);
			Optional<?> validAfterMalformed = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMalformedPosition, validId);
			Optional<?> missingPositionMissionAfterDecode = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMissingPosition, missingPositionId);
			Optional<?> malformedPositionMissionAfterDecode = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMalformedPosition, malformedPositionId);
			Optional<?> malformedPositionInMissingRoot = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMissingPosition, malformedPositionId);
			Optional<?> missingPositionInMalformedRoot = (Optional<?>) dataClass.getMethod("mission", UUID.class)
				.invoke(decodedMalformedPosition, missingPositionId);
			Object missingRecord = missingPositionMissionAfterDecode.orElse(null);
			Object malformedRecord = malformedPositionMissionAfterDecode.orElse(null);
			Object missingPositionValue = missingRecord == null ? "missing"
				: missionClass.getMethod("targetPosition").invoke(missingRecord);
			Object malformedPositionValue = malformedRecord == null ? "missing"
				: missionClass.getMethod("targetPosition").invoke(malformedRecord);
			boolean validMissionRetained = validAfterMissing.isPresent() && validAfterMalformed.isPresent();
			boolean unmodifiedMissionRetained = malformedPositionInMissingRoot.isPresent()
				&& missingPositionInMalformedRoot.isPresent();
			boolean stateMatches = missingMissions.size() == 2 && malformedMissions.size() == 2
				&& validMissionRetained && unmodifiedMissionRetained
				&& missingPositionMissionAfterDecode.isEmpty() && malformedPositionMissionAfterDecode.isEmpty();

			PatrolTestSupport.result(test, "missingPositionCount=" + missingMissions.size()
				+ " missingPositionRetained=" + missingPositionMissionAfterDecode.isPresent()
				+ " missingPosition=" + missingPositionValue
				+ " malformedPositionInMissingRoot=" + malformedPositionInMissingRoot.isPresent()
				+ " malformedPositionCount=" + malformedMissions.size()
				+ " malformedPositionRetained=" + malformedPositionMissionAfterDecode.isPresent()
				+ " malformedPosition=" + malformedPositionValue
				+ " missingPositionInMalformedRoot=" + missingPositionInMalformedRoot.isPresent()
				+ " validMissionRetained=" + validMissionRetained
				+ " unmodifiedMissionRetained=" + unmodifiedMissionRetained + " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "mission codec retained a missing or malformed target position");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "ledger=MISSING detail=" + error);
			helper.fail("missing_mission_ledger: " + error);
		}
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void missionMapIdPersists(final GameTestHelper helper) {
		String test = "missionMapIdPersists";
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			Class<?> missionClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionRecord");
			Class<?> statusClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionStatus");
			java.lang.reflect.Constructor<?> constructor = missionClass.getConstructor(
				UUID.class, UUID.class, Identifier.class, BlockPos.class, Optional.class, statusClass, Optional.class
			);
			UUID missionId = UUID.randomUUID();
			MapId mapId = new MapId(6231);
			Object mission = constructor.newInstance(
				missionId, UUID.randomUUID(), Identifier.parse("minecraft:zombie"),
				helper.absolutePos(new BlockPos(9, 1, 7)), Optional.empty(),
				statusClass.getField("TARGET_PENDING").get(null), Optional.of(mapId)
			);
			dataClass.getMethod("addMission", missionClass).invoke(original, mission);

			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			Object decoded = codec.parse(ops, codec.encodeStart(ops, original).getOrThrow()).getOrThrow();
			Optional<?> restored = (Optional<?>) dataClass.getMethod("mission", UUID.class).invoke(decoded, missionId);
			Object restoredMission = restored.orElse(null);
			Object restoredMapId = restoredMission == null ? null : missionClass.getMethod("mapId").invoke(restoredMission);
			boolean stateMatches = Optional.of(mapId).equals(restoredMapId);

			PatrolTestSupport.result(test, "mapId=" + mapId + " restoredMapId=" + restoredMapId
				+ " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "mission saved data did not preserve its map ID");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "mapId=MISSING detail=" + error);
			helper.fail("missing_mission_map_id: " + error);
		}
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	@SuppressWarnings("unchecked")
	public void missionDimensionPersists(final GameTestHelper helper) {
		String test = "missionDimensionPersists";
		try {
			Class<?> dataClass = Class.forName("com.stella.villages.data.VillageWorldData");
			Codec<Object> codec = (Codec<Object>) dataClass.getField("CODEC").get(null);
			Object original = dataClass.getMethod("empty").invoke(null);
			Class<?> missionClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionRecord");
			Class<?> statusClass = Class.forName("com.stella.villages.data.VillageWorldData$MissionStatus");
			java.lang.reflect.Constructor<?> constructor = missionClass.getConstructor(
				UUID.class, UUID.class, Identifier.class, BlockPos.class, Optional.class,
				statusClass, Optional.class, ResourceKey.class
			);
			UUID missionId = UUID.randomUUID();
			ResourceKey<Level> targetDimension = helper.getLevel().dimension();
			Object mission = constructor.newInstance(
				missionId, UUID.randomUUID(), Identifier.parse("minecraft:zombie"),
				helper.absolutePos(new BlockPos(9, 1, 7)), Optional.empty(),
				statusClass.getField("TARGET_PENDING").get(null), Optional.empty(), targetDimension
			);
			dataClass.getMethod("addMission", missionClass).invoke(original, mission);

			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
			Object decoded = codec.parse(ops, codec.encodeStart(ops, original).getOrThrow()).getOrThrow();
			Optional<?> restored = (Optional<?>) dataClass.getMethod("mission", UUID.class).invoke(decoded, missionId);
			Object restoredMission = restored.orElse(null);
			Object restoredDimension = restoredMission == null ? null
				: missionClass.getMethod("dimension").invoke(restoredMission);
			boolean stateMatches = targetDimension.equals(restoredDimension);

			PatrolTestSupport.result(test, "targetDimension=" + targetDimension
				+ " restoredDimension=" + restoredDimension + " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "mission SavedData did not preserve its target dimension");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "dimension=MISSING detail=" + error);
			helper.fail("missing_mission_target_dimension: " + error);
		}
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nemesisNbtPersists(final GameTestHelper helper) {
		String test = "nemesisNbtPersists";
		try {
			Class<?> stateClass = Class.forName("com.stella.villages.mission.NemesisState");
			Mob original = EntityTypes.VINDICATOR.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			if (original == null) {
				PatrolTestSupport.result(test, "nemesis=MISSING");
				helper.fail("could not create a Nemesis NBT fixture");
				return;
			}
			UUID missionId = UUID.randomUUID();
			original.setPersistenceRequired();
			stateClass.getMethod("attach", Mob.class, UUID.class).invoke(null, original, missionId);
			stateClass.getMethod("markRewardPaid", Mob.class).invoke(null, original);
			stateClass.getMethod("markEnraged", Mob.class).invoke(null, original);

			TagValueOutput output = TagValueOutput.createWithContext(
				ProblemReporter.DISCARDING, helper.getLevel().registryAccess()
			);
			original.saveWithoutId(output);
			Mob restored = EntityTypes.VINDICATOR.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			if (restored == null) {
				PatrolTestSupport.result(test, "restoredNemesis=MISSING");
				helper.fail("could not create a restored Nemesis NBT fixture");
				return;
			}
			restored.load(TagValueInput.create(
				ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()
			));

			Object state = stateClass.getMethod("get", Mob.class).invoke(null, restored);
			UUID restoredMissionId = (UUID) stateClass.getMethod("missionId").invoke(state);
			boolean rewardPaid = (Boolean) stateClass.getMethod("rewardPaid").invoke(state);
			boolean enraged = (Boolean) stateClass.getMethod("enraged").invoke(state);
			boolean persistenceRequired = restored.isPersistenceRequired();
			boolean stateMatches = missionId.equals(restoredMissionId) && rewardPaid && enraged && persistenceRequired;

			PatrolTestSupport.result(test, "missionId=" + restoredMissionId + " rewardPaid=" + rewardPaid
				+ " enraged=" + enraged + " persistenceRequired=" + persistenceRequired + " stateMatches=" + stateMatches);
			helper.assertTrue(stateMatches, "Nemesis NBT did not preserve mission state");
			helper.succeed();
		} catch (ReflectiveOperationException | LinkageError error) {
			PatrolTestSupport.result(test, "nemesisState=MISSING detail=" + error);
			helper.fail("missing_nemesis_state: " + error);
		}
	}
}
