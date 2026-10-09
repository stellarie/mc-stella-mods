package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinMarkers;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the camp from CampPlan. The mouth band opens to
 * cave air; the walk, the camp and the markers follow it.
 */
public final class CampLayout {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private CampLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		CampPlan plan = CampPlan.derive(ctx.random());
		BlockState lining = rock(ctx);
		drawMouth(ctx, plan, lining);
		for (Map.Entry<Integer, BlockState> cell : plan.blocks().entrySet()) {
			CampPlan.Spot spot = CampPlan.unpack(cell.getKey());
			ctx.set(spot.x(), spot.y(), spot.z(), cell.getValue());
		}
		sealFluids(ctx, plan, lining);
		CampPlan.Spot core = plan.core();
		CampPlan.Spot vault = plan.vault();
		ctx.placeCore(core.x(), core.y(), core.z());
		ctx.placeVault(vault.x(), vault.y(), vault.z());
		markers(ctx, plan);
	}

	/** The mouth band: the bore stays open, and the rock round it is lined. */
	private static void drawMouth(final LayoutContext ctx, final CampPlan plan, final BlockState lining) {
		int half = CampPlan.TUNNEL_WIDTH / 2;
		for (CampPlan.Spot spot : plan.centre()) {
			if (spot.z() >= CampPlan.MOUTH) {
				break;
			}
			for (int dx = -half - 1; dx <= half + 1; dx++) {
				for (int dy = -1; dy <= CampPlan.TUNNEL_HEIGHT + 1; dy++) {
					int x = spot.x() + dx;
					int y = spot.y() + dy;
					boolean bore = Math.abs(dx) <= half && dy >= 0 && dy < CampPlan.TUNNEL_HEIGHT;
					if (bore) {
						ctx.set(x, y, spot.z(), AIR);
					} else if (!ctx.get(x, y, spot.z()).isAir()) {
						ctx.set(x, y, spot.z(), lining);
					}
				}
			}
		}
	}

	/** Seals fluid out of the walls and the roof of the walk and the camp. */
	private static void sealFluids(final LayoutContext ctx, final CampPlan plan, final BlockState lining) {
		for (Map.Entry<Integer, BlockState> cell : plan.blocks().entrySet()) {
			if (!cell.getValue().isAir()) {
				continue;
			}
			CampPlan.Spot spot = CampPlan.unpack(cell.getKey());
			for (Direction side : Direction.values()) {
				int x = spot.x() + side.getStepX();
				int y = spot.y() + side.getStepY();
				int z = spot.z() + side.getStepZ();
				if (plan.stateAt(x, y, z) != null) {
					continue;
				}
				if (!ctx.get(x, y, z).getFluidState().isEmpty()) {
					ctx.set(x, y, z, lining);
				}
			}
		}
	}

	private static BlockState rock(final LayoutContext ctx) {
		return ctx.frame().min().getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	private static void markers(final LayoutContext ctx, final CampPlan plan) {
		CampPlan.Spot mouth = plan.mouth();
		ctx.marker(CampMarkers.MOUTH, mouth.x(), mouth.y(), mouth.z());
		CampPlan.Spot lectern = plan.lectern();
		ctx.marker(CampMarkers.LECTERN, lectern.x(), lectern.y() + 1, lectern.z());
		CampPlan.Spot tent = plan.tent();
		ctx.marker(CampMarkers.TENT, tent.x(), tent.y(), tent.z());
		CampPlan.Spot fire = plan.fire();
		ctx.marker(CampMarkers.FIRE, fire.x(), fire.y(), fire.z());
		for (CampPlan.Spot spot : plan.lanterns()) {
			ctx.marker(CampMarkers.LANTERN, spot.x(), spot.y(), spot.z());
		}
		for (CampPlan.Spot spot : plan.miners()) {
			ctx.marker(CampMarkers.MINER, spot.x(), spot.y(), spot.z());
			ctx.marker(RuinMarkers.SPAWN, spot.x(), spot.y(), spot.z());
		}
		for (CampPlan.Spot spot : plan.rats()) {
			ctx.marker(CampMarkers.RAT, spot.x(), spot.y(), spot.z());
			ctx.marker(RuinMarkers.SPAWN, spot.x(), spot.y(), spot.z());
		}
	}
}
