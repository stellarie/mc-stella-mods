package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** Records killed elite companions by mission, even when their Nemesis is unloaded. */
final class NemesisCompanionDeaths {
	private static final AttachmentType<UUID> MISSION = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "nemesis_companion_mission"), UUIDUtil.CODEC
	);
	private static final AttachmentType<List<UUID>> DEFEATED = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "defeated_nemesis_companions"), UUIDUtil.CODEC.listOf()
	);

	private NemesisCompanionDeaths() {
	}

	static void register() {
		McStellaVillages.LOGGER.debug("Registered companion death attachments {} and {}",
			MISSION.identifier(), DEFEATED.identifier());
	}

	static void attach(final Mob companion, final UUID missionId) {
		((AttachmentTarget) companion).setAttached(MISSION, missionId);
	}

	static @Nullable UUID missionOf(final Mob companion) {
		return ((AttachmentTarget) companion).getAttached(MISSION);
	}

	static boolean isDefeated(final ServerLevel level, final UUID missionId) {
		List<UUID> saved = ((AttachmentTarget) level.getServer().overworld()).getAttached(DEFEATED);
		return saved != null && saved.contains(missionId);
	}

	static void recordDeath(final ServerLevel level, final UUID missionId, final UUID companionId) {
		AttachmentTarget world = (AttachmentTarget) level.getServer().overworld();
		List<UUID> saved = world.getAttached(DEFEATED);
		if (saved != null && saved.contains(missionId)) {
			return;
		}
		List<UUID> updated = new ArrayList<>(saved == null ? List.of() : saved);
		updated.add(missionId);
		world.setAttached(DEFEATED, List.copyOf(updated));
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=nemesis_companion_killed mission={} companion={}",
			McStellaVillages.MOD_ID, missionId, companionId);
	}

	static void clear(final ServerLevel level, final UUID missionId) {
		AttachmentTarget world = (AttachmentTarget) level.getServer().overworld();
		List<UUID> saved = world.getAttached(DEFEATED);
		if (saved == null || !saved.contains(missionId)) {
			return;
		}
		List<UUID> updated = new ArrayList<>(saved);
		updated.remove(missionId);
		world.setAttached(DEFEATED, List.copyOf(updated));
	}
}
