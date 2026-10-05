package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinMarkers;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the crystal grotto: a geode of amethyst, calcite and smooth basalt around a chamber of singing pillars,
 * with a vault niche behind the core and a tunnel that reaches out toward cave air.
 * <p>
 * The whole plan is a function of the piece seed, so every chunk draws the same grotto. The tunnel lining is the
 * one block that looks at the world: it stays out of cells that are already air, and it reads only the cell it
 * writes, in the chunk being drawn.
 */
public final class GrottoLayout {
	private GrottoLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		GrottoPlan plan = GrottoPlan.derive(ctx.random());
		for (int x = 0; x < GrottoPlan.SIZE_X; x++) {
			for (int y = 0; y < GrottoPlan.SIZE_Y; y++) {
				for (int z = GrottoPlan.TUNNEL_START; z < GrottoPlan.GROTTO_Z; z++) {
					BlockState state = plan.stateAt(x, y, z);
					if (state == null) {
						continue;
					}
					int local = z + GrottoPlan.EXTENSION;
					if (z < 0 && !state.isAir() && ctx.get(x, y, local).isAir()) {
						continue;
					}
					ctx.set(x, y, local, state);
				}
			}
		}
		ctx.placeCore(GrottoPlan.CENTER_X, GrottoPlan.CORE_Y, GrottoPlan.CENTER_Z + GrottoPlan.EXTENSION);
		ctx.placeVault(GrottoPlan.VAULT_X, GrottoPlan.CORE_Y, GrottoPlan.VAULT_Z + GrottoPlan.EXTENSION);
		ctx.marker(GrottoMarkers.ENTRANCE, GrottoPlan.CENTER_X, GrottoPlan.FLOOR, 0);
		for (GrottoPlan.Pillar pillar : plan.pillars()) {
			ctx.marker(GrottoMarkers.PILLAR, pillar.x(), GrottoPlan.FLOOR + 1, pillar.z() + GrottoPlan.EXTENSION);
		}
		for (GrottoPlan.Pillar pillar : plan.pillars()) {
			for (GrottoPlan.Cell cell : pillar.spoke()) {
				ctx.marker(GrottoMarkers.spoke(pillar.index()), cell.x(), 1, cell.z() + GrottoPlan.EXTENSION);
			}
		}
		for (GrottoPlan.Cell cell : plan.sentinelCells()) {
			ctx.marker(GrottoMarkers.SENTINEL, cell.x(), GrottoPlan.FLOOR, cell.z() + GrottoPlan.EXTENSION);
		}
		for (GrottoPlan.Cell cell : plan.slimeCells()) {
			ctx.marker(RuinMarkers.SPAWN, cell.x(), GrottoPlan.FLOOR, cell.z() + GrottoPlan.EXTENSION);
		}
	}
}
