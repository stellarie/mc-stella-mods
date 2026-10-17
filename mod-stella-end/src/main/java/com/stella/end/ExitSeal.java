package com.stella.end;

import net.minecraft.server.level.ServerLevel;

/**
 * The barrier door that shuts floor 6 off from the rest area. It opens when the Sovereign is dead and no Wither Knight
 * stands, and it shuts again when the Sovereign rises. Each of the three deaths asks here.
 */
public final class ExitSeal {
	private ExitSeal() {
	}

	/** Whether the saved state holds the Sovereign dead and both knights down. */
	public static boolean allDown(final EndState state) {
		return state.sovereignDeadAt() != EndState.NEVER && state.knights() == 0;
	}

	/** Opens the door when the three are down. It never shuts it: the rising of the Sovereign does. */
	public static void update(final ServerLevel end) {
		if (allDown(EndCastles.state(end))) {
			CastleBuilder.setExitDoor(end, EndCastles.layout(end), EndCastle.min(), true);
			BehaviorLog.info("[END] exit seal opened");
		}
	}
}
