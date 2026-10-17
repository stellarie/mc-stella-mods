package com.stella.end;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Writes a castle plan into a level as blocks. The only code that places castle blocks. */
public final class CastleBuilder {
	private CastleBuilder() {
	}

	/**
	 * Builds the castle with its keep corner at {@code min}, at the base of floor 1: it clears the volume, grounds the
	 * zone on the island, and raises the keep, its crown, the yard and the dressing.
	 */
	public static void build(final ServerLevel level, final CastleLayout layout, final BlockPos min) {
		CastleSite site = new CastleSite(level, min, layout);
		CastleGround.load(site);
		CastleGround.clear(site);
		CastleGround.foundation(site);
		CastleGround.paving(site);
		CastleKeep.build(site);
		CastleCourtyard.build(site);
		CastleCrown.build(site);
		CastleYard.build(site);
		CastleDecor.build(site);
		CastleTreasure.place(site);
		site.relink();
	}

	public static void setExitDoor(final ServerLevel level, final CastleLayout layout, final BlockPos min, final boolean open) {
		CastleSite site = new CastleSite(level, min, layout);
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		for (CastleLayout.Door door : layout.floor(EndCastle.FLOORS).doors()) {
			if (!door.barrier()) {
				continue;
			}
			for (int x = door.x0(); x <= door.x1(); x++) {
				for (int z = door.z0(); z <= door.z1(); z++) {
					for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
						site.put(x, y, z, open ? CastleBlocks.AIR : CastleBlocks.BARRIER);
					}
				}
			}
		}
	}
}
