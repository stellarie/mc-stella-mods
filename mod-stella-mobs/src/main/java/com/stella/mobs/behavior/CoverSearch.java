package com.stella.mobs.behavior;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Finds a hiding spot for a ranged unit that has just shot. The search samples {@link #ANGLES} angles at four radii around
 * the unit, as learn's cover search does. A spot is valid when all of these hold:
 *
 * <ul>
 * <li>The spot has a standable floor, and the unit has a path to it.
 * <li>A ray from the player's eye to the spot, at the unit's eye height, hits a block. Two more rays, a little to each side,
 * hit one too, so the spot lies a step inside the shadow and not on its edge.
 * <li>The spot lies at least {@link #BAND_MARGIN} blocks beyond the near band of the unit and no farther than
 * {@link RangedReach#RANGE} from the player.
 * <li>No other unit has claimed a spot within {@link #CLAIM_SPACING} blocks.
 * </ul>
 *
 * <p>Darker spots rank first, then nearer ones. Only the best few spots are tested for a path, because a path search is the
 * costly step. The caller searches once per opening shot. A claim lasts {@link #CLAIM_TICKS} ticks of the claiming unit's
 * own clock and ends sooner when the unit stops hiding or dies.
 */
public final class CoverSearch {
	public static final int ANGLES = 12;
	/** Blocks beyond the near band, so a hider is out of the player's first rush. */
	public static final double BAND_MARGIN = 2.0;
	public static final double CLAIM_SPACING = 2.0;
	public static final int CLAIM_TICKS = 200;

	private static final double[] RADII = {3.0, 5.0, 7.0, 10.0};
	private static final double SIGHT_MARGIN = 0.3;
	private static final int MAX_PATH_TESTS = 6;
	// A floor one block up or down counts too.
	private static final int[] STEPS = {0, -1, 1};
	private static final Map<Mob, Claim> CLAIMS = new WeakHashMap<>();

	private record Claim(Vec3 spot, int untilTick) {
	}

	private record Spot(Vec3 position, int light, double walk) {
	}

	private CoverSearch() {
	}

	/** The best hiding spot from this player, or empty. The search claims nothing. */
	public static Optional<Vec3> hidden(final Mob mob, final Player player) {
		Level level = mob.level();
		double nearest = StanceBands.near(mob, ReactiveState.get(mob).getNearDistance()) + BAND_MARGIN;
		Vec3 eye = player.getEyePosition();
		double eyeHeight = mob.getEyeHeight();
		List<Spot> spots = new ArrayList<>();
		for (double radius : RADII) {
			for (int index = 0; index < ANGLES; index++) {
				double angle = Math.PI * 2.0 * index / ANGLES;
				BlockPos feet = standing(level, BlockPos.containing(mob.getX() + Math.cos(angle) * radius, mob.getY(), mob.getZ() + Math.sin(angle) * radius));
				if (feet == null) {
					continue;
				}

				Vec3 position = Vec3.atBottomCenterOf(feet);
				double distance = position.distanceTo(player.position());
				if (distance < nearest || distance > RangedReach.RANGE || claimedNear(mob, position)) {
					continue;
				}

				if (inShadow(level, player, eye, position.add(0.0, eyeHeight, 0.0))) {
					spots.add(new Spot(position, level.getMaxLocalRawBrightness(feet), position.distanceToSqr(mob.position())));
				}
			}
		}

		spots.sort(Comparator.comparingInt(Spot::light).thenComparingDouble(Spot::walk));
		int tested = 0;
		for (Spot spot : spots) {
			if (tested++ >= MAX_PATH_TESTS) {
				break;
			}

			// The probe leaves the navigation target set.
			// A hide start sets it again.
			Path path = mob.getNavigation().createPath(BlockPos.containing(spot.position()), 0);
			if (path != null && path.canReach()) {
				return Optional.of(spot.position());
			}
		}

		return Optional.empty();
	}

	/** Reserves the spot for this unit, in place of an earlier claim of the same unit. */
	public static void claim(final Mob mob, final Vec3 spot) {
		CLAIMS.put(mob, new Claim(spot, mob.tickCount + CLAIM_TICKS));
	}

	public static void release(final Mob mob) {
		CLAIMS.remove(mob);
	}

	/** True when another live unit holds a claim within `CLAIM_SPACING` of the spot. */
	private static boolean claimedNear(final Mob mob, final Vec3 spot) {
		double spacingSqr = CLAIM_SPACING * CLAIM_SPACING;
		Iterator<Map.Entry<Mob, Claim>> claims = CLAIMS.entrySet().iterator();
		while (claims.hasNext()) {
			Map.Entry<Mob, Claim> entry = claims.next();
			Mob other = entry.getKey();
			if (other.isRemoved() || !other.isAlive() || other.tickCount >= entry.getValue().untilTick()) {
				claims.remove();
			} else if (other != mob && other.level() == mob.level() && entry.getValue().spot().distanceToSqr(spot) < spacingSqr) {
				return true;
			}
		}

		return false;
	}

	/** The feet position that a unit can stand on in this column, near the unit's own floor. */
	private static @Nullable BlockPos standing(final Level level, final BlockPos column) {
		for (int step : STEPS) {
			BlockPos feet = column.above(step);
			BlockPos floor = feet.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& level.getFluidState(feet).isEmpty()) {
				return feet;
			}
		}

		return null;
	}

	/** True when blocks hide the point, and its two neighbours to the side, from the player's eye. */
	private static boolean inShadow(final Level level, final Player player, final Vec3 eye, final Vec3 point) {
		if (!blocked(level, player, eye, point)) {
			return false;
		}

		Vec3 line = point.subtract(eye);
		double flat = Math.hypot(line.x, line.z);
		if (flat < 1.0E-6) {
			return true;
		}

		Vec3 side = new Vec3(-line.z / flat * SIGHT_MARGIN, 0.0, line.x / flat * SIGHT_MARGIN);
		return blocked(level, player, eye, point.add(side)) && blocked(level, player, eye, point.subtract(side));
	}

	private static boolean blocked(final Level level, final Player player, final Vec3 eye, final Vec3 point) {
		return level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
	}
}
