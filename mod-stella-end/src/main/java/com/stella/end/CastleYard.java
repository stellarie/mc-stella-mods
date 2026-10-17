package com.stella.end;

import java.util.Random;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** The outer zone: the paved approach to the gate under its canopy, ruined pillars, a market stall and a lean-to camp. */
final class CastleYard {
	private static final int GROUND = EndCastle.BASE_Y + 1;

	private CastleYard() {
	}

	static void build(final CastleSite site) {
		Random random = site.random(41);
		approach(site);
		canopy(site);
		pillars(site, random);
		stall(site, random);
		camp(site, random);
	}

	/** A paved way from the zone's edge to the gate. */
	private static void approach(final CastleSite site) {
		for (int z = CastleLayout.GATE.z0() + 1; z <= CastleGround.ZONE_TO; z++) {
			for (int x = CastleLayout.GATE.x0() - 1; x <= CastleLayout.GATE.x1() + 1; x++) {
				boolean edge = x == CastleLayout.GATE.x0() - 1 || x == CastleLayout.GATE.x1() + 1;
				site.put(x, EndCastle.BASE_Y, z, edge ? CastleBlocks.BRICKS : CastleBlocks.PURPUR);
			}
		}
	}

	/** A roof on four posts over the gate, with banners hung on the wall either side of the door. */
	private static void canopy(final CastleSite site) {
		int front = CastleLayout.GATE.z0() + 1;
		int left = CastleLayout.GATE.x0() - 3;
		int right = CastleLayout.GATE.x1() + 3;
		for (int x : new int[] {left, right}) {
			for (int z : new int[] {front, front + 3}) {
				for (int up = 0; up < 6; up++) {
					site.put(x, GROUND + up, z, up == 0 ? CastleBlocks.BRICKS : CastleBlocks.PILLAR);
				}
			}
		}
		for (int x = left; x <= right; x++) {
			for (int z = front; z <= front + 3; z++) {
				site.put(x, GROUND + 6, z, CastleBlocks.slab(Blocks.END_STONE_BRICK_SLAB, SlabType.BOTTOM));
			}
		}
		for (int x = left; x <= right; x++) {
			site.put(x, GROUND + 6, front + 3, CastleBlocks.stair(Blocks.PURPUR_STAIRS, Direction.NORTH, Half.TOP));
		}
		BlockState purple = Blocks.WALL_BANNER.purple().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH);
		BlockState black = Blocks.WALL_BANNER.black().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH);
		for (int dx : new int[] {-1, 1}) {
			int x = (CastleLayout.GATE.x0() + CastleLayout.GATE.x1()) / 2 + dx * 2;
			site.put(x, GROUND + 3, front, dx < 0 ? purple : black);
			site.put(x, GROUND + 4, front, dx < 0 ? black : purple);
		}
	}

	/** Pillars in a ring round the keep, none whole, with a few fallen across the ground. */
	private static void pillars(final CastleSite site, final Random random) {
		for (int t = -6; t <= 36; t += 7) {
			for (int[] spot : new int[][] {{t, -6}, {-6, t}, {36, t}}) {
				pillar(site, spot[0], spot[1], 2 + random.nextInt(7), random);
			}
			if (t == -6) {
				pillar(site, t, 36, 2 + random.nextInt(6), random);
			}
		}
		for (int i = 0; i < 6; i++) {
			int x = -5 + random.nextInt(42);
			int z = -5 + random.nextInt(32);
			boolean alongX = random.nextBoolean();
			for (int t = 0; t < 3; t++) {
				int px = alongX ? x + t : x;
				int pz = alongX ? z : z + t;
				if (!CastleGround.inKeep(px, pz) && site.get(px, GROUND, pz).isAir()) {
					site.put(px, GROUND, pz, CastleBlocks.pillar(alongX ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
	}

	private static void pillar(final CastleSite site, final int x, final int z, final int height, final Random random) {
		if (CastleGround.inKeep(x, z) || CastleGround.inKeep(x + 1, z + 1)) {
			return;
		}
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				int cut = height - random.nextInt(3);
				for (int up = 0; up < Math.max(1, cut); up++) {
					site.put(x + dx, GROUND + up, z + dz, CastleBlocks.PILLAR);
				}
				if (cut > 1 && random.nextInt(3) == 0) {
					site.put(x + dx, GROUND + cut, z + dz, CastleBlocks.stair(Blocks.PURPUR_STAIRS, Direction.values()[2 + random.nextInt(4)], Half.BOTTOM));
				}
			}
		}
	}

	/** A market stall left standing on three posts, its roof half gone, with barrels behind the counter. */
	private static void stall(final CastleSite site, final Random random) {
		int bx = 3;
		int bz = 34;
		for (int x = bx; x <= bx + 5; x++) {
			site.put(x, GROUND, bz, CastleBlocks.slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
		}
		int[][] posts = {{bx, bz}, {bx + 5, bz}, {bx, bz + 2}, {bx + 5, bz + 2}};
		for (int i = 0; i < posts.length; i++) {
			int height = i == 3 ? 1 : 4;
			for (int up = 0; up < height; up++) {
				site.putShaped(posts[i][0], GROUND + up, posts[i][1], CastleBlocks.FENCE);
			}
		}
		for (int x = bx - 1; x <= bx + 6; x++) {
			for (int z = bz - 1; z <= bz + 3; z++) {
				int roll = random.nextInt(10);
				if (roll < 4) {
					site.put(x, GROUND + 4, z, CastleBlocks.slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
				} else if (roll < 6) {
					site.put(x, GROUND + 4, z, Blocks.WOOL.red().defaultBlockState());
				}
			}
		}
		BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
		site.put(bx + 1, GROUND, bz + 1, barrel);
		site.put(bx + 1, GROUND + 1, bz + 1, barrel);
		site.put(bx + 4, GROUND, bz + 1, barrel.setValue(BarrelBlock.FACING, Direction.NORTH));
		site.put(bx + 3, GROUND, bz + 2, CastleBlocks.HAY);
	}

	/** A lean-to against a pillar: crates and barrels, a bedroll, a cold fire ring and a cauldron. */
	private static void camp(final CastleSite site, final Random random) {
		int bx = 23;
		int bz = 34;
		for (int z = bz; z <= bz + 4; z++) {
			for (int up = 0; up < 3; up++) {
				site.put(bx + 6, GROUND + up, z, CastleBlocks.PLANKS);
			}
			site.put(bx + 6, GROUND + 3, z, CastleBlocks.slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
			site.put(bx + 5, GROUND + 3, z, CastleBlocks.stair(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
			site.put(bx + 4, GROUND + 2, z, CastleBlocks.stair(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
		}
		for (int z : new int[] {bz, bz + 4}) {
			for (int up = 0; up < 3; up++) {
				site.putShaped(bx + 4, GROUND + up, z, CastleBlocks.FENCE);
			}
		}
		BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
		site.put(bx + 5, GROUND, bz + 1, barrel);
		site.put(bx + 5, GROUND, bz + 2, barrel);
		site.put(bx + 5, GROUND + 1, bz + 1, barrel);
		site.put(bx + 3, GROUND, bz + 4, CastleBlocks.DARK_PLANKS);
		site.put(bx + 2, GROUND, bz + 4, CastleBlocks.DARK_PLANKS);
		site.put(bx + 3, GROUND + 1, bz + 4, CastleBlocks.slab(Blocks.DARK_OAK_SLAB, SlabType.BOTTOM));
		site.put(bx + 5, GROUND, bz + 3, Blocks.CARPET.red().defaultBlockState());
		site.put(bx + 4, GROUND, bz + 3, Blocks.CARPET.red().defaultBlockState());
		site.put(bx + 4, GROUND, bz + 1, CastleBlocks.CAULDRON);
		for (int[] ring : new int[][] {{bx + 1, bz + 1}, {bx + 2, bz + 1}, {bx + 1, bz + 2}, {bx + 2, bz + 2}}) {
			if (random.nextInt(3) > 0) {
				site.put(ring[0], GROUND, ring[1], CastleBlocks.COBBLE);
			}
		}
		site.put(bx, GROUND, bz + 3, CastleBlocks.HAY);
	}
}
