package com.stella.mobs.scaling;

import com.mojang.serialization.Codec;
import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The highest mob threat level this server has observed, saved on its Overworld. */
public final class WorldThreatPeak {
	public static final AttachmentType<Integer> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "world_threat_peak"), Codec.INT
	);

	private WorldThreatPeak() {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered world threat peak attachment {}", TYPE.identifier());
	}

	public static int highest(final MinecraftServer server) {
		Integer stored = ((AttachmentTarget) server.overworld()).getAttached(TYPE);
		return stored == null ? 0 : Math.max(0, stored);
	}

	public static void observe(final ServerLevel level, final int threat) {
		MinecraftServer server = level.getServer();
		if (threat > highest(server)) {
			((AttachmentTarget) server.overworld()).setAttached(TYPE, threat);
			StellaMobs.LOGGER.info("[BEHAVIOR] event=world_threat_peak level={} dimension={}", threat, level.dimension().identifier());
		}
	}
}
