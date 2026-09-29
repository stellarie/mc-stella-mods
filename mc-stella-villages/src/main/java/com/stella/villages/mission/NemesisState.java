package com.stella.villages.mission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.villages.McStellaVillages;
import java.util.UUID;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

public record NemesisState(UUID missionId, boolean rewardPaid, boolean enraged, int buffIndex, Optional<UUID> companionId) {
	public static final Codec<NemesisState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("mission_id").forGetter(NemesisState::missionId),
		Codec.BOOL.optionalFieldOf("reward_paid", false).forGetter(NemesisState::rewardPaid),
		Codec.BOOL.optionalFieldOf("enraged", false).forGetter(NemesisState::enraged),
		Codec.INT.optionalFieldOf("buff_index", 0).forGetter(NemesisState::buffIndex),
		UUIDUtil.CODEC.optionalFieldOf("companion_id").forGetter(NemesisState::companionId)
	).apply(instance, NemesisState::new));
	public static final AttachmentType<NemesisState> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "nemesis_state"), CODEC
	);

	public static void register() {
		McStellaVillages.LOGGER.debug("Registered Nemesis state attachment {}", TYPE.identifier());
	}

	public static @Nullable NemesisState get(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(TYPE);
	}

	public static void attach(final Mob mob, final UUID missionId, final int buffIndex) {
		((AttachmentTarget) mob).setAttached(TYPE, new NemesisState(missionId, false, false, buffIndex, Optional.empty()));
	}

	public static void attach(final Mob mob, final UUID missionId) {
		attach(mob, missionId, 0);
	}

	public static boolean markRewardPaid(final Mob mob) {
		NemesisState state = get(mob);
		if (state == null || state.rewardPaid()) {
			return false;
		}
		((AttachmentTarget) mob).setAttached(TYPE, new NemesisState(state.missionId(), true, state.enraged(), state.buffIndex(), state.companionId()));
		return true;
	}

	public static boolean markEnraged(final Mob mob) {
		NemesisState state = get(mob);
		if (state == null || state.enraged()) {
			return false;
		}
		((AttachmentTarget) mob).setAttached(TYPE, new NemesisState(state.missionId(), state.rewardPaid(), true, state.buffIndex(), state.companionId()));
		return true;
	}

	public static void setCompanion(final Mob mob, final UUID companionId) {
		NemesisState state = get(mob);
		if (state != null) {
			((AttachmentTarget) mob).setAttached(TYPE, new NemesisState(
				state.missionId(), state.rewardPaid(), state.enraged(), state.buffIndex(), Optional.of(companionId)
			));
		}
	}
}
