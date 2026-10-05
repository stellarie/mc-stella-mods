package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.world.LayoutContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.SlabBlock;

/** Draws the plan: the shaft, its stair, its breach and its rubble, over a foundation that reaches the ground. */
public final class WatchtowerLayout {
	private WatchtowerLayout() {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/** A local direction code as the direction a block faces in the local frame. */
	static Direction facing(final int code) {
		return switch (code) {
			case 0 -> Direction.EAST;
			case 1 -> Direction.SOUTH;
			case 2 -> Direction.WEST;
			default -> Direction.NORTH;
		};
	}

	/** The block a plan cell becomes. The core, the vault and the chest are placed by the context instead. */
	static BlockState stateOf(final byte id, final int code) {
		return switch (id) {
			case WatchtowerPlan.WALL, WatchtowerPlan.PLINTH -> Blocks.STONE_BRICKS.defaultBlockState();
			case WatchtowerPlan.WALL_CRACKED, WatchtowerPlan.RUBBLE_CRACKED -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			case WatchtowerPlan.WALL_MOSSY -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case WatchtowerPlan.FLOOR -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case WatchtowerPlan.STONE_FLOOR -> Blocks.POLISHED_ANDESITE.defaultBlockState();
			case WatchtowerPlan.STAIR -> Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing(code));
			case WatchtowerPlan.CORBEL -> Blocks.STONE_BRICK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, facing(code)).setValue(StairBlock.HALF, Half.TOP);
			case WatchtowerPlan.RUBBLE_COBBLE -> Blocks.COBBLESTONE.defaultBlockState();
			case WatchtowerPlan.RUBBLE_MOSSY -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case WatchtowerPlan.RUBBLE_SLAB -> Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			case WatchtowerPlan.BED_FOOT -> Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, facing(code));
			case WatchtowerPlan.BED_HEAD -> Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, facing(code));
			case WatchtowerPlan.BARREL -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
			case WatchtowerPlan.HAY -> Blocks.HAY_BLOCK.defaultBlockState();
			case WatchtowerPlan.FLETCHING -> Blocks.FLETCHING_TABLE.defaultBlockState();
			case WatchtowerPlan.GRINDSTONE -> Blocks.GRINDSTONE.defaultBlockState();
			case WatchtowerPlan.RACK -> Blocks.SPRUCE_FENCE.defaultBlockState();
			case WatchtowerPlan.WEB -> Blocks.COBWEB.defaultBlockState();
			case WatchtowerPlan.TABLE -> Blocks.CRAFTING_TABLE.defaultBlockState();
			case WatchtowerPlan.WATCH_FIRE -> Blocks.CAMPFIRE.defaultBlockState();
			case WatchtowerPlan.DAIS -> Blocks.SMOOTH_STONE.defaultBlockState();
			default -> AIR;
		};
	}

	public static void draw(final LayoutContext ctx) {
		WatchtowerPlan plan = WatchtowerPlan.derive(ctx.frame().seed());
		int below = Math.min(ctx.foundation(), 4);
		// Clear the footprint and the door strip, then lay the foundation down to the ground.
		ctx.fill(WatchtowerPlan.OUTER_LOW, 0, WatchtowerPlan.OUTER_LOW, WatchtowerPlan.OUTER_HIGH, WatchtowerPlan.SIZE_Y - 1, WatchtowerPlan.OUTER_HIGH, AIR);
		ctx.fill(9, 0, 0, 11, 3, 4, AIR);
		if (below > 0) {
			ctx.fill(WatchtowerPlan.OUTER_LOW, -below, WatchtowerPlan.OUTER_LOW, WatchtowerPlan.OUTER_HIGH, -1, WatchtowerPlan.OUTER_HIGH, Blocks.STONE_BRICKS.defaultBlockState());
		}
		// The door steps run down to the ground, a block each, so a tower on a slope still has a way in.
		BlockState step = stateOf(WatchtowerPlan.STAIR, 1);
		for (int i = 1; i <= 4; i++) {
			int z = 4 - i;
			int y = -i;
			if (y < -below) {
				break;
			}
			ctx.fill(9, y + 1, z, 11, 3, z, AIR);
			ctx.fill(9, y, z, 11, y, z, step);
			if (y - 1 >= -below) {
				ctx.fill(9, -below, z, 11, y - 1, z, Blocks.COBBLESTONE.defaultBlockState());
			}
		}
		for (int y = 0; y < WatchtowerPlan.SIZE_Y; y++) {
			for (int z = 0; z < WatchtowerPlan.SIZE_Z; z++) {
				for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
					byte id = plan.cell(x, y, z);
					if (id == WatchtowerPlan.AIR) {
						continue;
					}
					if (id == WatchtowerPlan.CORE || id == WatchtowerPlan.VAULT || id == WatchtowerPlan.CHEST) {
						continue;
					}
					ctx.set(x, y, z, stateOf(id, plan.dir(x, y, z)));
					if (y == 0 && WatchtowerPlan.isRubble(id) && below > 0 && !inFootprint(x, z)) {
						ctx.fill(x, -below, z, x, -1, z, Blocks.COBBLESTONE.defaultBlockState());
					}
				}
			}
		}
		for (int y = 0; y < WatchtowerPlan.SIZE_Y; y++) {
			for (int z = 0; z < WatchtowerPlan.SIZE_Z; z++) {
				for (int x = 0; x < WatchtowerPlan.SIZE_X; x++) {
					byte id = plan.cell(x, y, z);
					if (id == WatchtowerPlan.CHEST) {
						ctx.placeChest(x, y, z, facing(plan.dir(x, y, z)), WatchtowerMobs.STORE_LOOT);
					}
				}
			}
		}
		int[] core = plan.core();
		int[] vault = plan.vault();
		ctx.placeCore(core[0], core[1], core[2]);
		ctx.placeVault(vault[0], vault[1], vault[2]);
		for (WatchtowerPlan.Marker marker : plan.markers) {
			ctx.marker(marker.name(), marker.x(), marker.y(), marker.z());
		}
		ctx.marker(WatchtowerMobs.BOUND_MIN, WatchtowerPlan.IN_LOW, 0, WatchtowerPlan.IN_LOW);
		ctx.marker(WatchtowerMobs.BOUND_MAX, WatchtowerPlan.IN_HIGH, 0, WatchtowerPlan.IN_HIGH);
	}

	private static boolean inFootprint(final int x, final int z) {
		return x >= WatchtowerPlan.OUTER_LOW && x <= WatchtowerPlan.OUTER_HIGH && z >= WatchtowerPlan.OUTER_LOW && z <= WatchtowerPlan.OUTER_HIGH;
	}
}
