package com.stella.tower;

import com.stella.mobs.entity.BlueSlime;
import com.stella.mobs.entity.WhiteSlime;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * A white slime keeps five blue escorts and calls a new one for each that dies. A tower white slime takes its escorts with
 * it when it dies, so a floor is not left with harmless blue slimes that nothing counts and nothing clears.
 */
public final class TowerSlimes {
	private TowerSlimes() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof WhiteSlime white && white.level() instanceof ServerLevel level && TowerMobs.stateOf(white) != null) {
				for (BlueSlime escort : white.livingEscorts(level)) {
					escort.discard();
				}
			}
		});
	}
}
