package com.stella.villages;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

public final class VillagePersistenceGameTest {
	@GameTest
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

	@GameTest
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
}
