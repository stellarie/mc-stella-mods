package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** The mission an elite or kill quest mob belongs to, saved with the mob so a mob of a finished mission can be cleared. */
final class QuestMobState {
	private static final AttachmentType<UUID> MISSION = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "quest_mob_mission"), UUIDUtil.CODEC
	);

	private QuestMobState() {
	}

	static void register() {
		McStellaVillages.LOGGER.debug("Registered quest mob attachment {}", MISSION.identifier());
	}

	static void attach(final Mob mob, final UUID missionId) {
		((AttachmentTarget) mob).setAttached(MISSION, missionId);
	}

	static @Nullable UUID missionOf(final Mob mob) {
		return ((AttachmentTarget) mob).getAttached(MISSION);
	}
}
