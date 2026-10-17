package com.stella.end;

import com.stella.end.mixin.EnderDragonFightAccessor;
import com.stella.end.test.mixin.FightAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

/** Reaches the vanilla dragon fight of the shared test End, to start a flow from a world where no dragon has died. */
final class FightHandle {
	private FightHandle() {
	}

	/**
	 * Puts the fight back to a world it has already scanned and where no dragon has died. The legacy scan stays off: it
	 * reads the gateways an earlier kill left (they extend the portal block entity) as proof that a dragon died.
	 */
	static void reset(final ServerLevel end) {
		EnderDragonFight fight = end.getDragonFight();
		if (fight == null) {
			return;
		}
		FightAccessor access = (FightAccessor) fight;
		access.stellaEndTest$setNeedsStateScanning(false);
		access.stellaEndTest$setDragonKilled(false);
		access.stellaEndTest$setPreviouslyKilled(false);
		access.stellaEndTest$setDragonUuid(null);
		access.stellaEndTest$setExitPortal(null);
		access.stellaEndTest$setRespawnStage(null);
		access.stellaEndTest$setTicksSinceDragonSeen(0);
		// A kill places an end gateway 96 blocks out.
		// Its chunk stays loaded for a while.
		// An enderman could then teleport there.
		access.stellaEndTest$gateways().clear();
	}

	/** Builds the shut portal podium on the deck again, as the fight does for a world that has not seen a kill. */
	static void podium(final ServerLevel end) {
		EnderDragonFight fight = end.getDragonFight();
		if (fight != null) {
			((EnderDragonFightAccessor) fight).stellaEnd$spawnExitPortal(false);
		}
	}
}
