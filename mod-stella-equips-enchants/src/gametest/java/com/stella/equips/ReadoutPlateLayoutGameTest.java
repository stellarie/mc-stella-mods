package com.stella.equips;

import com.stella.equips.readout.ReadoutPlateLayout;
import com.stella.equips.readout.ReadoutPlateLayout.Plan;
import com.stella.equips.readout.ReadoutPlateLayout.Rect;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class ReadoutPlateLayoutGameTest {
	private static final int RING_COLOR = 0xFFFF5555;
	private static final int NAME_WIDTH = 70;
	private static final float MAX_HEALTH = 20.0F;
	private static final int YELLOW_MIN_CHANNEL = 200;
	private static final int YELLOW_MAX_BLUE = 100;

	private static Plan withAbsorption() {
		return ReadoutPlateLayout.plan(RING_COLOR, 15.0F, MAX_HEALTH, 4.0F, NAME_WIDTH);
	}

	private static Plan withoutAbsorption() {
		return ReadoutPlateLayout.plan(RING_COLOR, 15.0F, MAX_HEALTH, 0.0F, NAME_WIDTH);
	}

	@GameTest
	public void theHpBarSitsBelowTheNameAndTheAbsorptionBarSitsBetween(final GameTestHelper helper) {
		Plan plan = withAbsorption();
		helper.assertTrue(plan.absorptionFill() != null, "no absorption bar for absorption above zero");
		helper.assertTrue(
			plan.absorptionFill().y0() >= plan.nameBack().y1(),
			"the absorption bar starts at " + plan.absorptionFill().y0() + " above the name bottom " + plan.nameBack().y1()
		);
		helper.assertTrue(
			plan.hpFill().y0() >= plan.absorptionBack().y1(),
			"the HP bar starts at " + plan.hpFill().y0() + " above the absorption bar bottom " + plan.absorptionBack().y1()
		);
		helper.assertTrue(
			plan.hpFill().x0() == plan.nameX() && plan.absorptionFill().x0() == plan.nameX(),
			"the bars do not line up under the name"
		);
		helper.succeed();
	}

	@GameTest
	public void theAbsorptionBarIsSmallerAndYellowAndTheHpBarIsGreen(final GameTestHelper helper) {
		Plan plan = withAbsorption();
		int hpWidth = plan.hpFill().width() + plan.hpBack().width();
		int absorptionWidth = plan.absorptionFill().width() + plan.absorptionBack().width();
		int yellow = plan.absorptionFill().color();
		int green = plan.hpFill().color();
		helper.assertTrue(
			absorptionWidth < hpWidth && plan.absorptionFill().height() < plan.hpFill().height(),
			"the absorption bar is " + absorptionWidth + "x" + plan.absorptionFill().height()
				+ " against the HP bar " + hpWidth + "x" + plan.hpFill().height()
		);
		helper.assertTrue(
			red(yellow) > YELLOW_MIN_CHANNEL && green(yellow) > YELLOW_MIN_CHANNEL && blue(yellow) < YELLOW_MAX_BLUE,
			"the absorption fill is not yellow"
		);
		helper.assertTrue(
			green(green) > red(green) && green(green) > blue(green),
			"the HP fill is not green"
		);
		helper.succeed();
	}

	@GameTest
	public void noAbsorptionBarAppearsAtZeroAbsorption(final GameTestHelper helper) {
		Plan plan = withoutAbsorption();
		boolean yellow = plan.all().stream().anyMatch(rect -> rect.color() == ReadoutPlateLayout.ABSORPTION_FILL_COLOR);
		helper.assertTrue(plan.absorptionFill() == null && plan.absorptionBack() == null, "an absorption bar exists at zero");
		helper.assertTrue(!yellow, "a yellow rectangle is drawn at zero absorption");
		helper.assertTrue(
			plan.hpFill().y0() == withAbsorption().hpFill().y0() - ReadoutPlateLayout.ABSORPTION_BAR_HEIGHT - ReadoutPlateLayout.BAR_GAP,
			"the HP bar keeps the absorption slot at zero absorption"
		);
		helper.succeed();
	}

	@GameTest
	public void theThreatRingSitsLeftOfTheNameAndHoldsTheNumber(final GameTestHelper helper) {
		Plan plan = withAbsorption();
		int ringRight = plan.ring().stream().mapToInt(Rect::x1).max().orElse(-1);
		int ringLeft = plan.ring().stream().mapToInt(Rect::x0).min().orElse(-1);
		int ringTop = plan.ring().stream().mapToInt(Rect::y0).min().orElse(-1);
		int ringBottom = plan.ring().stream().mapToInt(Rect::y1).max().orElse(-1);
		helper.assertTrue(!plan.ring().isEmpty(), "the ring has no rows");
		helper.assertTrue(
			ringRight <= plan.nameBack().x0(),
			"the ring reaches x=" + ringRight + " into the name at x=" + plan.nameBack().x0()
		);
		helper.assertTrue(
			plan.numberCenterX() > ringLeft && plan.numberCenterX() < ringRight
				&& plan.numberCenterY() > ringTop && plan.numberCenterY() < ringBottom,
			"the number center sits outside the ring"
		);
		helper.assertTrue(
			plan.ring().stream().anyMatch(rect -> rect.color() == RING_COLOR),
			"the ring does not carry the band color"
		);
		helper.succeed();
	}

	@GameTest
	public void noRectanglesOverlap(final GameTestHelper helper) {
		for (Plan plan : List.of(withAbsorption(), withoutAbsorption())) {
			List<Rect> all = plan.all();

			for (int i = 0; i < all.size(); i++) {
				for (int j = i + 1; j < all.size(); j++) {
					helper.assertTrue(!all.get(i).overlaps(all.get(j)), all.get(i) + " overlaps " + all.get(j));
				}
			}
		}

		helper.succeed();
	}

	@GameTest
	public void barFillFollowsTheRatioAndKeepsOnePixelWhileAlive(final GameTestHelper helper) {
		Plan half = ReadoutPlateLayout.plan(RING_COLOR, 10.0F, MAX_HEALTH, 0.0F, NAME_WIDTH);
		Plan sliver = ReadoutPlateLayout.plan(RING_COLOR, 0.01F, MAX_HEALTH, 0.0F, NAME_WIDTH);
		Plan full = ReadoutPlateLayout.plan(RING_COLOR, MAX_HEALTH, MAX_HEALTH, 0.0F, NAME_WIDTH);
		helper.assertTrue(
			half.hpFill().width() == ReadoutPlateLayout.HP_BAR_WIDTH / 2,
			"half health filled " + half.hpFill().width() + " pixels"
		);
		helper.assertTrue(sliver.hpFill().width() == 1, "a sliver of health filled " + sliver.hpFill().width() + " pixels");
		helper.assertTrue(
			full.hpFill().width() == ReadoutPlateLayout.HP_BAR_WIDTH && full.hpBack().width() == 0,
			"full health filled " + full.hpFill().width() + " pixels"
		);
		Plan overAbsorbed = ReadoutPlateLayout.plan(RING_COLOR, MAX_HEALTH, MAX_HEALTH, 40.0F, NAME_WIDTH);
		helper.assertTrue(
			overAbsorbed.absorptionFill().width() == ReadoutPlateLayout.ABSORPTION_BAR_WIDTH,
			"absorption above max health filled " + overAbsorbed.absorptionFill().width() + " pixels"
		);
		helper.succeed();
	}

	private static int red(final int color) {
		return color >> 16 & 0xFF;
	}

	private static int green(final int color) {
		return color >> 8 & 0xFF;
	}

	private static int blue(final int color) {
		return color & 0xFF;
	}
}
