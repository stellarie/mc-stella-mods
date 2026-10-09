package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Grows the nest back. The site's rearm hook calls it: the sacs, the webs, and the cocoons grow back into air, at
 * the cells of the plan. Rock that a player mined stays mined.
 */
public final class NestRegrowth {
	private NestRegrowth() {
	}

	/** Draws the growing blocks of the core's nest into the world. Returns false when the core has no frame yet. */
	public static boolean regrow(final ServerLevel level, final BlockPos core) {
		if (!(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block) || block.frame() == null) {
			return false;
		}
		RuinFrame frame = block.frame();
		NestLayout.regrow(LayoutContext.forWorld(frame, level, frame.box()));
		StellaRuins.LOGGER.info("[RUINS] nest core={} grew back", core.toShortString());
		return true;
	}
}
