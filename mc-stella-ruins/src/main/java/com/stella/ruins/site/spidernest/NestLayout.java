package com.stella.ruins.site.spidernest;

import com.stella.ruins.world.LayoutContext;
import com.stella.ruins.world.RuinMarkers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the spider nest: a web-choked cave of domed chambers, egg sacs on the walls and under the roof, hanging
 * cocoons, bone piles, and a mouth tunnel that reaches out toward cave air and ends in a plug of cobweb.
 * <p>
 * The whole plan is a function of the piece seed, so every chunk draws the same nest. Two passes look at the world.
 * The tunnel lining stays out of cells that are already air, so a tunnel that meets a cave simply ends there. The
 * shell pass seals fluid out of the walls, and seals the floor against a cave below it.
 */
public final class NestLayout {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WEB = Blocks.COBWEB.defaultBlockState();
	private static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();
	private static final BlockState SKULL = Blocks.SKELETON_SKULL.defaultBlockState();
	private static final BlockState COCOON = Blocks.WOOL.white().defaultBlockState();

	private NestLayout() {
	}

	public static void draw(final LayoutContext ctx) {
		NestPlan plan = NestPlan.of(ctx.frame().seed());
		BlockState lining = rock(ctx);
		drawTunnel(ctx, plan, lining);
		drawShell(ctx, plan, lining);
		for (int y = 0; y < NestPlan.SIZE_Y; y++) {
			for (int z = 0; z < NestPlan.NEST_Z; z++) {
				for (int x = 0; x < NestPlan.SIZE_X; x++) {
					byte id = plan.cell(x, y, z);
					if (id == NestPlan.ROCK || id == NestPlan.CORE || id == NestPlan.VAULT) {
						continue;
					}
					ctx.set(x, y, z + NestPlan.EXTENSION, stateOf(id));
				}
			}
		}
		for (NestPlan.Lichen lichen : plan.lichens()) {
			Direction face = Direction.getApproximateNearest(lichen.faceX(), 0.0F, lichen.faceZ());
			BlockState state = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(face), true);
			ctx.set(lichen.x(), lichen.y(), lichen.z() + NestPlan.EXTENSION, state);
		}
		NestPlan.Spot core = plan.core();
		NestPlan.Spot vault = plan.vault();
		ctx.placeCore(core.x(), core.y(), core.z() + NestPlan.EXTENSION);
		ctx.placeVault(vault.x(), vault.y(), vault.z() + NestPlan.EXTENSION);
		markers(ctx, plan);
	}

	private static void markers(final LayoutContext ctx, final NestPlan plan) {
		NestPlan.Spot entrance = plan.entrance();
		ctx.marker(NestMarkers.ENTRANCE, entrance.x(), entrance.y(), entrance.z() + NestPlan.EXTENSION);
		for (NestPlan.Spot spot : plan.weavers()) {
			ctx.marker(RuinMarkers.SPAWN, spot.x(), spot.y(), spot.z() + NestPlan.EXTENSION);
		}
		for (NestPlan.Spot spot : plan.crawlers()) {
			ctx.marker(NestMarkers.CRAWLER, spot.x(), spot.y(), spot.z() + NestPlan.EXTENSION);
		}
		NestPlan.Spot mother = plan.matriarch();
		ctx.marker(NestMarkers.MATRIARCH, mother.x(), mother.y(), mother.z() + NestPlan.EXTENSION);
		for (NestPlan.Sac sac : plan.sacs()) {
			ctx.marker(NestMarkers.SAC, sac.x, sac.y, sac.z + NestPlan.EXTENSION);
		}
	}

	/** The blocks that grow back when the nest rearms: sacs, webs, and cocoons, into air only. Mined rock stays mined. */
	public static void regrow(final LayoutContext ctx) {
		NestPlan plan = NestPlan.of(ctx.frame().seed());
		for (int y = 0; y < NestPlan.SIZE_Y; y++) {
			for (int z = 0; z < NestPlan.NEST_Z; z++) {
				for (int x = 0; x < NestPlan.SIZE_X; x++) {
					byte id = plan.cell(x, y, z);
					if ((id == NestPlan.WEB || id == NestPlan.SAC || id == NestPlan.COCOON) && ctx.get(x, y, z + NestPlan.EXTENSION).isAir()) {
						ctx.set(x, y, z + NestPlan.EXTENSION, stateOf(id));
					}
				}
			}
		}
	}

	static BlockState stateOf(final byte id) {
		return switch (id) {
			case NestPlan.WEB -> WEB;
			case NestPlan.SAC -> EggSacBlock.BLOCK.defaultBlockState();
			case NestPlan.BONE -> BONE;
			case NestPlan.COCOON -> COCOON;
			case NestPlan.SKULL -> SKULL;
			default -> AIR;
		};
	}

	private static BlockState rock(final LayoutContext ctx) {
		return ctx.frame().min().getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** The mouth tunnel: 3 wide, 3 high, out to {@link NestPlan#TUNNEL_START}, lined with rock that clings to rock only. */
	private static void drawTunnel(final LayoutContext ctx, final NestPlan plan, final BlockState lining) {
		int mx = plan.mouthX();
		for (int z = NestPlan.TUNNEL_START; z < 0; z++) {
			int local = z + NestPlan.EXTENSION;
			for (int x = mx - 2; x <= mx + 2; x++) {
				for (int y = NestPlan.TUNNEL_FLOOR - 1; y <= NestPlan.TUNNEL_FLOOR + NestPlan.TUNNEL_HEIGHT; y++) {
					boolean inside = Math.abs(x - mx) <= 1 && y >= NestPlan.TUNNEL_FLOOR && y < NestPlan.TUNNEL_FLOOR + NestPlan.TUNNEL_HEIGHT;
					if (x < 0 || x >= NestPlan.SIZE_X) {
						continue;
					}
					if (inside) {
						ctx.set(x, y, local, AIR);
					} else if (!ctx.get(x, y, local).isAir()) {
						ctx.set(x, y, local, lining);
					}
				}
			}
		}
	}

	/** Seals fluid out of the nest's walls, and the floor against a cave below it. */
	private static void drawShell(final LayoutContext ctx, final NestPlan plan, final BlockState lining) {
		for (int y = 0; y < NestPlan.SIZE_Y; y++) {
			for (int z = 0; z < NestPlan.NEST_Z; z++) {
				for (int x = 0; x < NestPlan.SIZE_X; x++) {
					if (plan.cell(x, y, z) != NestPlan.ROCK || !touchesOpen(plan, x, y, z)) {
						continue;
					}
					BlockState current = ctx.get(x, y, z + NestPlan.EXTENSION);
					boolean floor = plan.cell(x, y + 1, z) != NestPlan.ROCK;
					if (!current.getFluidState().isEmpty() || floor && current.isAir()) {
						ctx.set(x, y, z + NestPlan.EXTENSION, lining);
					}
				}
			}
		}
	}

	private static boolean touchesOpen(final NestPlan plan, final int x, final int y, final int z) {
		return plan.cell(x + 1, y, z) != NestPlan.ROCK && NestPlan.inside(x + 1, y, z)
			|| plan.cell(x - 1, y, z) != NestPlan.ROCK && NestPlan.inside(x - 1, y, z)
			|| plan.cell(x, y + 1, z) != NestPlan.ROCK && NestPlan.inside(x, y + 1, z)
			|| plan.cell(x, y - 1, z) != NestPlan.ROCK && NestPlan.inside(x, y - 1, z)
			|| plan.cell(x, y, z + 1) != NestPlan.ROCK && NestPlan.inside(x, y, z + 1)
			|| plan.cell(x, y, z - 1) != NestPlan.ROCK && NestPlan.inside(x, y, z - 1);
	}
}
