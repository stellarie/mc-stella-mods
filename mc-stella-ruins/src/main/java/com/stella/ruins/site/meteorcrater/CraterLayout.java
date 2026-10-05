package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.LayoutContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the crater from its plan. Every column the plan touches is written whole, from the foundation to the top
 * of the box: solid below the surface, air above it. So rock, caves, water and trees in the old ground never
 * survive inside the bowl. The plan is a function of the piece seed, so every chunk draws the same crater.
 */
public final class CraterLayout {
	private CraterLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		CraterPlan plan = CraterPlan.derive(ctx.random());
		for (int x = 0; x < CraterPlan.SIZE_X; x++) {
			for (int z = 0; z < CraterPlan.SIZE_Z; z++) {
				if (!plan.affected(x, z)) {
					continue;
				}
				for (int y = CraterPlan.MIN_Y; y < CraterPlan.SIZE_Y; y++) {
					BlockState state = plan.stateAt(x, y, z);
					if (state != null) {
						ctx.set(x, y, z, state);
					}
				}
			}
		}
		CraterPlan.Spot core = plan.core();
		CraterPlan.Spot vault = plan.vault();
		ctx.placeCore(core.x(), core.y(), core.z());
		ctx.placeVault(vault.x(), vault.y(), vault.z());
		ctx.marker(CraterMarkers.ENTRANCE, CraterPlan.CENTER, CraterPlan.GROUND + 1, 0);
		ctx.marker(CraterMarkers.NOTCH, CraterPlan.CENTER, core.y(), CraterPlan.NOTCH_Z);
		for (CraterPlan.Spot ore : plan.ore()) {
			ctx.marker(CraterMarkers.ORE, ore.x(), ore.y(), ore.z());
		}
		for (CraterPlan.Spot golem : plan.golems()) {
			ctx.marker(CraterMarkers.GOLEM, golem.x(), golem.y(), golem.z());
		}
		for (CraterPlan.Spot slime : plan.slimes()) {
			ctx.marker(CraterMarkers.SLIME, slime.x(), slime.y(), slime.z());
		}
	}
}
