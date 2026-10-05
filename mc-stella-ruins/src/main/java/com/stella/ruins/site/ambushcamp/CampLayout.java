package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.LayoutContext;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Draws the camp from its plan. The plan holds every random choice, so every chunk draws the same camp. */
public final class CampLayout {
	private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	private CampLayout() {
	}

	/** The wool block of one of the 16 dye colours. */
	public static Block wool(final int color) {
		return Blocks.WOOL.pick(DyeColor.byId(color));
	}

	public static void draw(final LayoutContext ctx) {
		CampPlan plan = CampPlan.derive(ctx.random());
		ctx.fill(0, -ctx.foundation(), 0, CampPlan.SIZE_X - 1, -1, CampPlan.SIZE_Z - 1, Blocks.DIRT.defaultBlockState());
		ctx.fill(0, 1, 0, CampPlan.SIZE_X - 1, CampPlan.SIZE_Y - 1, CampPlan.SIZE_Z - 1, Blocks.AIR.defaultBlockState());
		for (int y = 0; y < CampPlan.SIZE_Y; y++) {
			for (int z = 0; z < CampPlan.SIZE_Z; z++) {
				for (int x = 0; x < CampPlan.SIZE_X; x++) {
					int kind = plan.kind(x, y, z);
					if (kind == CampPlan.AIR || kind == CampPlan.CORE || kind == CampPlan.VAULT) {
						continue;
					}
					ctx.set(x, y, z, state(plan, kind, x, y, z));
				}
			}
		}
		CampPlan.Cell core = plan.core();
		ctx.placeCore(core.x(), 1, core.z());
		CampPlan.Cell vault = plan.vault();
		ctx.placeVault(vault.x(), 1, vault.z());
		markers(ctx, plan);
	}

	private static void markers(final LayoutContext ctx, final CampPlan plan) {
		for (CampPlan.Tent tent : plan.tents()) {
			ctx.marker(CampMarkers.tent(tent.id()), tent.marker().x(), 1, tent.marker().z());
			ctx.marker(CampMarkers.door(tent.id()), tent.step().x(), 1, tent.step().z());
		}
		for (int i = 0; i < plan.patrol().size(); i++) {
			CampPlan.Cell cell = plan.patrol().get(i);
			ctx.marker(CampMarkers.PATROL, cell.x(), 1, cell.z());
		}
		for (CampPlan.Tower tower : plan.towers()) {
			ctx.marker(CampMarkers.LOOKOUT, tower.cx(), CampPlan.lookoutY(), tower.cz());
		}
		ctx.marker(CampMarkers.TRAIL, plan.trailStart().x(), 1, plan.trailStart().z());
	}

	private static BlockState state(final CampPlan plan, final int kind, final int x, final int y, final int z) {
		int data = plan.data(x, y, z);
		return switch (kind) {
			case CampPlan.GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
			case CampPlan.COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
			case CampPlan.PATH -> Blocks.DIRT_PATH.defaultBlockState();
			case CampPlan.COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
			case CampPlan.LOG, CampPlan.TLOG -> Blocks.SPRUCE_LOG.defaultBlockState();
			case CampPlan.LANTERN -> Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false);
			case CampPlan.LANTERN_HANGING -> Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
			case CampPlan.WOOL -> wool(data).defaultBlockState();
			case CampPlan.BED_FOOT -> bed(data, BedPart.FOOT, plan, x, z);
			case CampPlan.BED_HEAD -> bed(data, BedPart.HEAD, plan, x, z);
			case CampPlan.BARREL -> Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
			case CampPlan.HAY -> Blocks.HAY_BLOCK.defaultBlockState();
			case CampPlan.SEAT -> Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
			case CampPlan.FIRE -> Blocks.CAMPFIRE.defaultBlockState();
			case CampPlan.FENCE -> fence(plan, x, y, z);
			case CampPlan.GRIND -> Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[data]);
			case CampPlan.PLANK -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case CampPlan.LADDER -> Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[data]);
			case CampPlan.BANNER -> wool(CampPlan.RED).defaultBlockState();
			default -> Blocks.AIR.defaultBlockState();
		};
	}

	private static BlockState bed(final int facing, final BedPart part, final CampPlan plan, final int x, final int z) {
		int color = 0;
		for (CampPlan.Tent tent : plan.tents()) {
			if (Math.abs(tent.cx() - x) <= CampPlan.TENT_HALF && Math.abs(tent.cz() - z) <= CampPlan.TENT_HALF) {
				color = tent.color();
			}
		}
		return Blocks.BED.pick(DyeColor.byId(color)).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, FACINGS[facing])
			.setValue(BlockStateProperties.BED_PART, part);
	}

	private static boolean joins(final int kind) {
		return kind == CampPlan.FENCE || kind == CampPlan.PLANK || kind == CampPlan.TLOG || kind == CampPlan.LOG || kind == CampPlan.GRIND;
	}

	/** A fence reaches every fence, plank or log beside it, as the game would connect it. */
	private static BlockState fence(final CampPlan plan, final int x, final int y, final int z) {
		BlockState state = Blocks.SPRUCE_FENCE.defaultBlockState();
		state = state.setValue(BlockStateProperties.NORTH, joins(plan.kind(x, y, z - 1)));
		state = state.setValue(BlockStateProperties.EAST, joins(plan.kind(x + 1, y, z)));
		state = state.setValue(BlockStateProperties.SOUTH, joins(plan.kind(x, y, z + 1)));
		return state.setValue(BlockStateProperties.WEST, joins(plan.kind(x - 1, y, z)));
	}
}
