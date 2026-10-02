package com.stella.mobs;

import com.stella.mobs.behavior.PlayerReadout;
import com.stella.mobs.behavior.Stance;
import com.stella.mobs.behavior.StanceRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * The stance table. Every row calls the pure rules with plain numbers and compares the answer with the stance that the
 * plan names. The rows cover the five quadrants with chasing on and off, the zombie rule, a target that is not a
 * player, the hysteresis of both low flags, the morale line, the shield bonus, and the chase test. No row needs a
 * world, so a wrong row names itself and the case reports every wrong row at once.
 */
public class StanceRulesGameTest {
	private static final String TEST = "stanceRules";
	private static final double EPSILON = 1.0E-9;
	// 30 percent: low alone, high behind a shield.
	private static final double SHIELDED_PLAYER = 0.30;
	private static final double FAR_AWAY = 20.0;

	@GameTest
	public void table(final GameTestHelper helper) {
		Rows rows = new Rows();
		decisions(rows);
		zombieRule(rows);
		nonPlayerTarget(rows);
		playerHysteresis(rows);
		mobMorale(rows);
		mobHysteresis(rows);
		shieldBonus(rows);
		chaseTest(rows);

		StellaMobs.LOGGER.info("[BEHAVIOR] test={}.table RESULT rows={} wrong={}", TEST, rows.count, rows.wrong.size());
		helper.assertTrue(rows.wrong.isEmpty(), rows.wrong.size() + " of " + rows.count + " rows are wrong: " + rows.wrong);
		helper.succeed();
	}

	private static void decisions(final Rows rows) {
		// A chaser makes a low mob retreat.
		// A player who stays back lets it kite.
		rows.stance("low mob, high player, chasing", Stance.RETREAT, StanceRules.decide(true, false, true, false));
		rows.stance("low mob, high player, not chasing", Stance.KITE, StanceRules.decide(true, false, false, false));
		rows.stance("high mob, high player, chasing", Stance.FIGHT, StanceRules.decide(false, false, true, false));
		rows.stance("high mob, high player, not chasing", Stance.FIGHT, StanceRules.decide(false, false, false, false));
		rows.stance("low mob, low player, chasing", Stance.FIGHT, StanceRules.decide(true, true, true, false));
		rows.stance("low mob, low player, not chasing", Stance.FIGHT, StanceRules.decide(true, true, false, false));
		rows.stance("high mob, low player, chasing", Stance.PURSUE, StanceRules.decide(false, true, true, false));
		rows.stance("high mob, low player, not chasing", Stance.PURSUE, StanceRules.decide(false, true, false, false));
	}

	private static void zombieRule(final Rows rows) {
		rows.stance("zombie, low mob, high player, chasing", Stance.FIGHT, StanceRules.decide(true, false, true, true));
		rows.stance("zombie, low mob, high player, not chasing", Stance.FIGHT, StanceRules.decide(true, false, false, true));
		rows.stance("zombie, high mob, low player", Stance.PURSUE, StanceRules.decide(false, true, false, true));
		rows.stance("zombie, high mob, high player", Stance.FIGHT, StanceRules.decide(false, false, true, true));
		rows.stance("zombie, low mob, low player", Stance.FIGHT, StanceRules.decide(true, true, true, true));
	}

	private static void nonPlayerTarget(final Rows rows) {
		// Every mix of the other four inputs.
		// The target decides before any of them.
		for (int bits = 0; bits < 16; bits++) {
			boolean mobLow = (bits & 1) != 0;
			boolean playerLow = (bits & 2) != 0;
			boolean chasing = (bits & 4) != 0;
			boolean zombie = (bits & 8) != 0;
			rows.stance(
				"non player target, bits " + bits, Stance.NORMAL, StanceRules.decide(false, mobLow, playerLow, chasing, zombie)
			);
		}

		rows.stance("player target, same call", Stance.RETREAT, StanceRules.decide(true, true, false, true, false));
	}

	private static void playerHysteresis(final Rows rows) {
		rows.flag("player 0.34 enters low", true, StanceRules.playerLow(false, 0.34));
		rows.flag("player at the enter line enters low", true, StanceRules.playerLow(false, StanceRules.PLAYER_LOW_ENTER));
		rows.flag("player 0.36 stays high", false, StanceRules.playerLow(false, StanceRules.PLAYER_LOW_ENTER + 0.01));
		rows.flag("player 0.45 holds low", true, StanceRules.playerLow(true, 0.45));
		rows.flag("player 0.45 holds high", false, StanceRules.playerLow(false, 0.45));
		rows.flag("player 0.51 leaves low", false, StanceRules.playerLow(true, 0.51));
		rows.flag("player at the leave line leaves low", false, StanceRules.playerLow(true, StanceRules.PLAYER_LOW_LEAVE));
		rows.flag("player just under the leave line holds low", true, StanceRules.playerLow(true, StanceRules.PLAYER_LOW_LEAVE - 0.01));
	}

	private static void mobMorale(final Rows rows) {
		rows.number("alone line", StanceRules.MORALE_ALONE, StanceRules.mobLowLine(0));
		// Each ally lowers the line one step.
		// The floor stops it.
		for (int allies = 1; StanceRules.MORALE_ALONE - StanceRules.MORALE_STEP * allies > StanceRules.MORALE_FLOOR + EPSILON; allies++) {
			double step = StanceRules.mobLowLine(allies - 1) - StanceRules.mobLowLine(allies);
			rows.number("each ally lowers the line, ally " + allies, StanceRules.MORALE_STEP, step);
		}

		rows.number("the floor holds at 12 allies", StanceRules.MORALE_FLOOR, StanceRules.mobLowLine(12));
		rows.flag("30 percent alone is low", true, StanceRules.mobLow(false, 0.30, 0));
		rows.flag("30 percent with 4 allies is not low", false, StanceRules.mobLow(false, 0.30, 4));
		rows.flag("14 percent with 4 allies is low", true, StanceRules.mobLow(false, 0.14, 4));
		rows.flag("negative allies count as none", true, StanceRules.mobLow(false, 0.30, -3));
	}

	private static void mobHysteresis(final Rows rows) {
		for (int allies : new int[] {0, 2, 4}) {
			double line = StanceRules.mobLowLine(allies);
			String at = " at " + allies + " allies";
			rows.flag("mob just under the line enters low" + at, true, StanceRules.mobLow(false, line - 0.01, allies));
			rows.flag("mob just over the line stays high" + at, false, StanceRules.mobLow(false, line + 0.01, allies));
			rows.flag("mob in the band holds low" + at, true, StanceRules.mobLow(true, line + StanceRules.HYSTERESIS - 0.01, allies));
			rows.flag("mob in the band holds high" + at, false, StanceRules.mobLow(false, line + StanceRules.HYSTERESIS - 0.01, allies));
			rows.flag("mob at the leave line leaves low" + at, false, StanceRules.mobLow(true, line + StanceRules.HYSTERESIS, allies));
		}

		// Four allies make a low mob brave.
		rows.flag("allies end a low flag", false, StanceRules.mobLow(true, 0.40, 4));
	}

	private static void shieldBonus(final Rows rows) {
		rows.number("shield raises the share", SHIELDED_PLAYER + PlayerReadout.SHIELD_BONUS, PlayerReadout.effectiveShare(SHIELDED_PLAYER, true));
		rows.number("no shield keeps the share", SHIELDED_PLAYER, PlayerReadout.effectiveShare(SHIELDED_PLAYER, false));
		boolean shielded = StanceRules.playerLow(false, PlayerReadout.effectiveShare(SHIELDED_PLAYER, true));
		boolean bare = StanceRules.playerLow(false, PlayerReadout.effectiveShare(SHIELDED_PLAYER, false));
		rows.flag("shielded 30 percent player reads high", false, shielded);
		rows.flag("bare 30 percent player reads low", true, bare);
		rows.stance("high mob presses a bare 30 percent player", Stance.PURSUE, StanceRules.decide(false, bare, false, false));
		rows.stance("high mob fights a shielded 30 percent player", Stance.FIGHT, StanceRules.decide(false, shielded, false, false));

		Vec3 north = new Vec3(0.0, 0.0, -1.0);
		rows.flag("view toward the mob faces it", true, PlayerReadout.facing(north, new Vec3(0.0, 0.0, -5.0)));
		rows.flag("view away from the mob does not face it", false, PlayerReadout.facing(north, new Vec3(0.0, 0.0, 5.0)));
		rows.flag("a view across the mob does not face it", false, PlayerReadout.facing(north, new Vec3(5.0, 0.0, 0.0)));
		rows.flag("a tilted view still faces the mob", true, PlayerReadout.facing(new Vec3(0.0, -0.8, -0.2), new Vec3(0.0, 0.0, -5.0)));
		rows.flag("a view straight down faces nothing", false, PlayerReadout.facing(new Vec3(0.0, -1.0, 0.0), new Vec3(0.0, 0.0, -5.0)));
	}

	private static void chaseTest(final Rows rows) {
		rows.flag("close player chases", true, PlayerReadout.chasing(0.0, PlayerReadout.CHASE_RANGE - 0.1));
		rows.flag("a player at the range line does not chase", false, PlayerReadout.chasing(0.0, PlayerReadout.CHASE_RANGE));
		rows.flag("fast approach chases", true, PlayerReadout.chasing(PlayerReadout.CHASE_SPEED, FAR_AWAY));
		rows.flag("slow approach does not chase", false, PlayerReadout.chasing(PlayerReadout.CHASE_SPEED - 0.01, FAR_AWAY));
		rows.flag("retreating player does not chase", false, PlayerReadout.chasing(-0.5, FAR_AWAY));
		rows.flag("still far player does not chase", false, PlayerReadout.chasing(0.0, FAR_AWAY));
	}

	/** The rows seen, and the wrong ones. */
	private static final class Rows {
		private final List<String> wrong = new ArrayList<>();
		private int count;

		private void stance(final String name, final Stance expected, final Stance actual) {
			this.count++;
			if (expected != actual) {
				this.wrong.add(name + ": expected " + expected + ", got " + actual);
			}
		}

		private void flag(final String name, final boolean expected, final boolean actual) {
			this.count++;
			if (expected != actual) {
				this.wrong.add(name + ": expected " + expected + ", got " + actual);
			}
		}

		private void number(final String name, final double expected, final double actual) {
			this.count++;
			if (Math.abs(expected - actual) > EPSILON) {
				this.wrong.add(name + ": expected " + expected + ", got " + actual);
			}
		}
	}
}
