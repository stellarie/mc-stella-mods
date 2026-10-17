package com.stella.end;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Where a player lands when the End portal sends them: inside floor 1, in place of the obsidian platform. */
public final class CastleArrival {
	/** Facing into the castle from the north-west corner. */
	private static final float YAW = -45.0F;

	private CastleArrival() {
	}

	/** The crossing into the castle, building the castle first when the world has none. */
	public static TeleportTransition transition(final ServerLevel end) {
		EndCastles.ensureBuilt(end);
		CastleLayout.FloorPlan plan = EndCastles.layout(end).floor(1);
		Vec3 at = new Vec3(EndCastle.MIN_X + plan.landingX() + 0.5, EndCastle.standY(1), EndCastle.MIN_Z + plan.landingZ() + 0.5);
		return new TeleportTransition(end, at, Vec3.ZERO, YAW, 0.0F, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
	}
}
