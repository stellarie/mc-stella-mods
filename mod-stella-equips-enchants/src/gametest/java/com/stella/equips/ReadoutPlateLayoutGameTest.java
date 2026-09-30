package com.stella.equips;

import com.stella.equips.readout.ReadoutPlateLayout;
import com.stella.equips.readout.ReadoutPlateLayout.Plan;
import com.stella.equips.readout.ReadoutPlateLayout.Rect;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class ReadoutPlateLayoutGameTest {
	private static final int NAME_WIDTH = 70;
	private static final float MAX_HEALTH = 20.0F;
	private static final double FRACTION_TOLERANCE = 0.06;
	private static final int ALPHA_FLOOR = 0x20;
	private static final int ALPHA_CEILING = 0xC0;

	private static Plan plan(final float health, final float absorption) {
		return ReadoutPlateLayout.plan(health, MAX_HEALTH, absorption, NAME_WIDTH);
	}

	@GameTest
	public void theArcFractionsFollowHealthAndAbsorption(final GameTestHelper helper) {
		helper.assertTrue(ReadoutPlateLayout.hpFraction(10.0F, MAX_HEALTH) == 0.5F, "half health is not half the ring");
		helper.assertTrue(ReadoutPlateLayout.hpFraction(0.0F, MAX_HEALTH) == 0.0F, "zero health draws an arc");
		helper.assertTrue(ReadoutPlateLayout.hpFraction(30.0F, MAX_HEALTH) == 1.0F, "health above max is not capped");
		helper.assertTrue(
			ReadoutPlateLayout.hpFraction(0.01F, MAX_HEALTH) >= ReadoutPlateLayout.MIN_ARC,
			"a live mob draws no arc"
		);
		helper.assertTrue(ReadoutPlateLayout.absorptionFraction(5.0F, MAX_HEALTH) == 0.25F, "absorption over max health is wrong");
		helper.assertTrue(ReadoutPlateLayout.absorptionFraction(0.0F, MAX_HEALTH) == 0.0F, "zero absorption draws an arc");
		helper.assertTrue(
			ReadoutPlateLayout.absorptionFraction(MAX_HEALTH * 3.0F, MAX_HEALTH) == 1.0F,
			"absorption above max health is not capped at a full circle"
		);
		helper.succeed();
	}

	@GameTest
	public void theRedArcCoversTheHealthShareOfTheRing(final GameTestHelper helper) {
		for (float share : new float[] {0.25F, 0.5F, 0.75F}) {
			Plan plan = plan(MAX_HEALTH * share, 0.0F);
			double red = area(plan, ReadoutPlateLayout.HP_COLOR);
			double ring = red + area(plan, ReadoutPlateLayout.HP_TRACK_COLOR);
			helper.assertTrue(
				Math.abs(red / ring - share) < FRACTION_TOLERANCE,
				"health share " + share + " drew " + red / ring + " of the ring"
			);
		}

		helper.succeed();
	}

	@GameTest
	public void theYellowRingIsAbsentAtZeroAndCappedAtFull(final GameTestHelper helper) {
		double none = area(plan(MAX_HEALTH, 0.0F), ReadoutPlateLayout.ABSORPTION_COLOR);
		double half = area(plan(MAX_HEALTH, MAX_HEALTH / 2.0F), ReadoutPlateLayout.ABSORPTION_COLOR);
		double full = area(plan(MAX_HEALTH, MAX_HEALTH), ReadoutPlateLayout.ABSORPTION_COLOR);
		double over = area(plan(MAX_HEALTH, MAX_HEALTH * 3.0F), ReadoutPlateLayout.ABSORPTION_COLOR);
		helper.assertTrue(none == 0.0, "yellow cells exist at zero absorption");
		helper.assertTrue(Math.abs(half / full - 0.5) < FRACTION_TOLERANCE, "half absorption drew " + half / full + " of the full ring");
		helper.assertTrue(over == full, "absorption above max health grew the ring past a full circle");
		helper.succeed();
	}

	@GameTest
	public void theYellowRingSitsOutsideTheRedRing(final GameTestHelper helper) {
		Plan plan = plan(MAX_HEALTH, MAX_HEALTH);

		for (Rect rect : plan.badge()) {
			if (rect.color() == ReadoutPlateLayout.ABSORPTION_COLOR) {
				helper.assertTrue(
					minDistance(rect) >= ReadoutPlateLayout.HP_RING_OUTER,
					"a yellow cell sits inside the red ring at " + minDistance(rect)
				);
			} else if (rect.color() == ReadoutPlateLayout.HP_COLOR) {
				helper.assertTrue(
					minDistance(rect) >= ReadoutPlateLayout.DISC_RADIUS && maxDistance(rect) < ReadoutPlateLayout.HP_RING_OUTER,
					"a red cell sits off the ring at " + minDistance(rect) + ".." + maxDistance(rect)
				);
			}
		}

		helper.succeed();
	}

	@GameTest
	public void theNumberSitsInTheCenterOfTheBadge(final GameTestHelper helper) {
		Plan plan = plan(MAX_HEALTH, MAX_HEALTH);
		int left = plan.badge().stream().mapToInt(Rect::x0).min().orElse(-1);
		int right = plan.badge().stream().mapToInt(Rect::x1).max().orElse(-1);
		int top = plan.badge().stream().mapToInt(Rect::y0).min().orElse(-1);
		int bottom = plan.badge().stream().mapToInt(Rect::y1).max().orElse(-1);
		helper.assertTrue(
			plan.numberCenterX() * 2 == left + right && plan.numberCenterY() * 2 == top + bottom,
			"the number center " + plan.numberCenterX() + "," + plan.numberCenterY() + " is off the badge " + left + ".." + right
		);
		helper.succeed();
	}

	@GameTest
	public void theNameSitsRightOfTheBadgeAndCenteredOnIt(final GameTestHelper helper) {
		Plan plan = plan(MAX_HEALTH, MAX_HEALTH);
		int badgeRight = plan.badge().stream().mapToInt(Rect::x1).max().orElse(-1);
		double nameCenter = (plan.nameBounds().y0() + plan.nameBounds().y1()) / 2.0;
		helper.assertTrue(plan.nameBounds().x0() >= badgeRight, "the name starts at " + plan.nameBounds().x0() + " inside the badge");
		helper.assertTrue(
			Math.abs(nameCenter - plan.numberCenterY()) <= 1.0,
			"the name center " + nameCenter + " is off the badge center " + plan.numberCenterY()
		);
		helper.succeed();
	}

	@GameTest
	public void onlyRingsDiscAndBackgroundRemain(final GameTestHelper helper) {
		Set<Integer> allowed = Set.of(
			ReadoutPlateLayout.HP_COLOR, ReadoutPlateLayout.HP_TRACK_COLOR, ReadoutPlateLayout.ABSORPTION_COLOR,
			ReadoutPlateLayout.DISC_COLOR, ReadoutPlateLayout.BACKGROUND_COLOR
		);

		for (Rect rect : plan(MAX_HEALTH / 2.0F, 5.0F).all()) {
			helper.assertTrue(allowed.contains(rect.color()), "a bar color is still drawn: " + Integer.toHexString(rect.color()));
		}

		helper.succeed();
	}

	@GameTest
	public void theBackgroundHoldsTheBadgeAndTheNameAndOverlapsNothing(final GameTestHelper helper) {
		Plan plan = plan(MAX_HEALTH / 2.0F, 5.0F);
		Rect bounds = plan.bounds();
		int alpha = ReadoutPlateLayout.BACKGROUND_COLOR >>> 24;
		int red = ReadoutPlateLayout.BACKGROUND_COLOR >> 16 & 0xFF;
		int green = ReadoutPlateLayout.BACKGROUND_COLOR >> 8 & 0xFF;
		int blue = ReadoutPlateLayout.BACKGROUND_COLOR & 0xFF;
		helper.assertTrue(alpha > ALPHA_FLOOR && alpha < ALPHA_CEILING, "the background alpha " + alpha + " is not semi-transparent");
		helper.assertTrue(red == green && green == blue, "the background is not gray");

		for (Rect rect : plan.badge()) {
			helper.assertTrue(contains(bounds, rect), "the background misses badge cell " + rect);
		}

		helper.assertTrue(contains(bounds, plan.nameBounds()), "the background misses the name " + plan.nameBounds());
		helper.assertTrue(
			bounds.x0() < plan.badge().stream().mapToInt(Rect::x0).min().orElse(0)
				&& bounds.y0() < plan.badge().stream().mapToInt(Rect::y0).min().orElse(0)
				&& bounds.x1() > plan.nameBounds().x1() && bounds.y1() > plan.badge().stream().mapToInt(Rect::y1).max().orElse(0),
			"the background has no padding around the plate"
		);
		List<Rect> all = plan.all();
		long covered = all.stream().mapToLong(rect -> (long) rect.width() * rect.height()).sum();
		helper.assertTrue(
			covered == (long) bounds.width() * bounds.height(),
			"the drawn rectangles cover " + covered + " of " + (long) bounds.width() * bounds.height() + " cells"
		);

		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				helper.assertTrue(!all.get(i).overlaps(all.get(j)), all.get(i) + " overlaps " + all.get(j));
			}
		}

		helper.succeed();
	}

	private static boolean contains(final Rect outer, final Rect inner) {
		return outer.x0() <= inner.x0() && outer.y0() <= inner.y0() && outer.x1() >= inner.x1() && outer.y1() >= inner.y1();
	}

	private static double area(final Plan plan, final int color) {
		return plan.badge().stream().filter(rect -> rect.color() == color).mapToDouble(rect -> rect.width() * rect.height()).sum();
	}

	/** Distance from the badge center of the nearest cell of a one-row run. */
	private static double minDistance(final Rect rect) {
		double dy = rect.y0() + 0.5 - ReadoutPlateLayout.BADGE_CENTER;
		double nearest = Math.max(rect.x0() + 0.5, Math.min(ReadoutPlateLayout.BADGE_CENTER, rect.x1() - 0.5));
		return Math.hypot(nearest - ReadoutPlateLayout.BADGE_CENTER, dy);
	}

	/** Distance from the badge center of the farthest cell of a one-row run. */
	private static double maxDistance(final Rect rect) {
		double dy = rect.y0() + 0.5 - ReadoutPlateLayout.BADGE_CENTER;
		double far = Math.max(
			Math.abs(rect.x0() + 0.5 - ReadoutPlateLayout.BADGE_CENTER), Math.abs(rect.x1() - 0.5 - ReadoutPlateLayout.BADGE_CENTER)
		);
		return Math.hypot(far, dy);
	}
}
