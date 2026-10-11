package com.stella.tower;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The portal back down. Every biome floor above the first holds one on the arrival's pad, behind the player: a
 * tower frame of the exit portal's own size, a pillar of soul soil and soul fire on each upper corner, and a
 * {@link TowerBackPortalBlock} in the frame. It is set into the floor once the floor is built, and into an
 * older floor the next time anyone arrives, so a floor built before it gains one.
 * <p>
 * The way down lands the player at the foot of the lower floor's exit stair, not at its arrival.
 */
public final class TowerBackPortals {
	/** The frame is this wide and this high; the portal inside is two by three. */
	public static final int FRAME_WIDTH = TowerLayout.PORTAL_INNER_WIDTH + 2;
	public static final int FRAME_HEIGHT = TowerLayout.PORTAL_INNER_HEIGHT + 2;
	/** The blocks the frame stands from the arrival's column, tried in this order. */
	private static final int[] BEHIND = {2, 3, 4};
	/** Where the frame's first block lies along the plane, from the arrival's row. */
	private static final int[] ALONG = {-2, -1, -3};
	private static final Set<Long> CHECKED = ConcurrentHashMap.newKeySet();

	private TowerBackPortals() {
	}

	/** Forgets which floors were looked at, as a stopped server does. */
	static void clearCaches() {
		CHECKED.clear();
	}

	/** Where a frame stands: the column it fills along x, and the row it begins at along z. */
	record Spot(int x, int z) {
	}

	/**
	 * Sets the back portal into a floor built without one, unless it has one or the floor takes none. A floor built now
	 * has it in its volume ({@link TowerVolume}), so this is for the floors built before. True when one was set now.
	 */
	public static boolean place(final ServerLevel tower, final int floor) {
		if (floor < 2) {
			return false;
		}
		TowerPlan plan = TowerPlan.at(tower, floor);
		if (!plan.isBiome() || find(tower, floor) != null || !CHECKED.add(tower.getSeed() * 31L + floor)) {
			return false;
		}
		Spot spot = TowerVolume.of(plan).backPortalSpot();
		if (spot == null || !fits(tower, plan, spot)) {
			StellaTower.LOGGER.warn("Tower floor {}: no room for a back portal beside the arrival", floor);
			return false;
		}
		build(tower, plan, spot);
		StellaTower.LOGGER.debug("[TOWER] floor={} BACK PORTAL at={}", floor, inside(plan, spot));
		return true;
	}

	/** The spots a frame may take, nearest the arrival and best centred first. */
	static java.util.List<Spot> candidates(final TowerPlan plan) {
		int toward = plan.portalX() >= plan.arrivalX() ? 1 : -1;
		java.util.List<Spot> spots = new java.util.ArrayList<>();
		for (int behind : BEHIND) {
			for (int along : ALONG) {
				spots.add(new Spot(plan.arrivalX() - toward * behind, plan.arrivalZ() + along));
			}
		}
		return spots;
	}

	/** The first spot whose ground is bare and whose frame's room is empty in the volume, or null. */
	static @Nullable Spot choose(final TowerPlan plan, final TowerVolume volume) {
		for (Spot spot : candidates(plan)) {
			if (spot.x() < 1 || spot.x() >= plan.width() - 1 || spot.z() < 1 || spot.z() + FRAME_WIDTH > plan.depth() - 1) {
				continue;
			}
			boolean fits = true;
			for (int across = 0; across < FRAME_WIDTH && fits; across++) {
				byte below = volume.kind(spot.x(), -1, spot.z() + across);
				fits = below == TowerVolume.GROUND || below == TowerVolume.BIOME_BLOCK && BiomeBlocks.ground(volume.code(spot.x(), -1, spot.z() + across));
				boolean post = across == 0 || across == FRAME_WIDTH - 1;
				for (int up = 1; up < (post ? FRAME_HEIGHT + 2 : FRAME_HEIGHT) && fits; up++) {
					fits = volume.kind(spot.x(), up - 1, spot.z() + across) == TowerVolume.AIR;
				}
			}
			if (fits) {
				return spot;
			}
		}
		return null;
	}

	/** The block at a place in the frame: across it from 0, and up it from 0 at the ground block the frame sinks into. */
	private static BlockPos at(final TowerPlan plan, final Spot spot, final int across, final int up) {
		BlockPos origin = plan.origin();
		return new BlockPos(origin.getX() + spot.x(), TowerLayout.BASE_Y - 1 + up, origin.getZ() + spot.z() + across);
	}

	/** The portal's lower inside block, where a body steps in. */
	static BlockPos inside(final TowerPlan plan, final Spot spot) {
		return at(plan, spot, 1, 1);
	}

	private static boolean fits(final ServerLevel tower, final TowerPlan plan, final Spot spot) {
		for (int across = 0; across < FRAME_WIDTH; across++) {
			BlockState ground = tower.getBlockState(at(plan, spot, across, 0));
			if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.is(TowerBlocks.TOWER_CHEST)) {
				return false;
			}
			boolean post = across == 0 || across == FRAME_WIDTH - 1;
			// The posts carry a pillar of two more blocks above the frame.
			for (int up = 1; up < (post ? FRAME_HEIGHT + 2 : FRAME_HEIGHT); up++) {
				BlockState state = tower.getBlockState(at(plan, spot, across, up));
				if (!state.isAir() && !state.canBeReplaced()) {
					return false;
				}
			}
		}
		return true;
	}

	private static void build(final ServerLevel tower, final TowerPlan plan, final Spot spot) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockState frame = TowerBlocks.TOWER_FRAME.defaultBlockState();
		BlockState portal = TowerBlocks.TOWER_BACK_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
		for (int across = 0; across < FRAME_WIDTH; across++) {
			boolean post = across == 0 || across == FRAME_WIDTH - 1;
			for (int up = 0; up < FRAME_HEIGHT; up++) {
				boolean edge = post || up == 0 || up == FRAME_HEIGHT - 1;
				tower.setBlock(at(plan, spot, across, up), edge ? frame : portal, flags);
			}
			if (post) {
				tower.setBlock(at(plan, spot, across, FRAME_HEIGHT), Blocks.SOUL_SOIL.defaultBlockState(), flags);
				tower.setBlock(at(plan, spot, across, FRAME_HEIGHT + 1), Blocks.SOUL_FIRE.defaultBlockState(), flags);
			}
		}
	}

	/** Where the way down puts a player: the foot of the floor's exit stair, facing the exit; the arrival where that is not open. */
	public static Landing landing(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		Landing arrival = new Landing(plan.arrivalPos(), plan.arrivalYaw());
		if (!plan.isBiome()) {
			return arrival;
		}
		BlockPos origin = plan.origin();
		int side = plan.portalFacing() >= 0 ? 1 : -1;
		int x = plan.portalX() + side * (2 + plan.lift());
		int z = plan.portalZ();
		int stand = plan.standY(x, z);
		if (stand == BiomeDraft.NONE) {
			return arrival;
		}
		BlockPos feet = new BlockPos(origin.getX() + x, TowerLayout.BASE_Y + stand, origin.getZ() + z);
		boolean open = tower.getBlockState(feet).getCollisionShape(tower, feet).isEmpty()
			&& tower.getBlockState(feet.above()).getCollisionShape(tower, feet.above()).isEmpty()
			&& tower.getBlockState(feet.below()).isFaceSturdy(tower, feet.below(), Direction.UP);
		if (!open) {
			return arrival;
		}
		// The portal is two columns wide, so the middle of the two is the line it is walked to along.
		return new Landing(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 1.0), 90.0F * side);
	}

	/** Where a player lands, and the way they face. */
	public record Landing(Vec3 position, float yaw) {
	}

	/** The back portal's lower inside block on a built floor, or null when it has none. */
	public static @Nullable BlockPos find(final ServerLevel tower, final int floor) {
		TowerPlan plan = TowerPlan.at(tower, floor);
		for (Spot spot : candidates(plan)) {
			BlockPos inside = inside(plan, spot);
			if (tower.getBlockState(inside).is(TowerBlocks.TOWER_BACK_PORTAL)) {
				return inside;
			}
		}
		return null;
	}
}
