package com.stella.mobs.behavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * How a mob fights the player it hunts. {@link StanceGoal} reads the player and sets the stance, and the other goals read
 * it back. NORMAL is the mob's old behavior: no stance for a target that is not a player, and no stance yet for a player
 * that the mob has not read.
 *
 * <ul>
 * <li>RETREAT: a hurt mob runs from a healthy player who chases it.
 * <li>KITE: a hurt mob keeps its distance and strikes from it.
 * <li>PURSUE: a healthy mob presses a hurt player.
 * <li>FIGHT: the mob fights to the end, whatever its health.
 * </ul>
 */
public enum Stance {
	NORMAL,
	RETREAT,
	KITE,
	PURSUE,
	FIGHT;

	/** The stance now. A mob without state, and a mob that hunts no player, is NORMAL. */
	public static Stance of(final Mob mob) {
		StanceState state = StanceState.peek(mob);
		return state == null || !(mob.getTarget() instanceof Player) ? NORMAL : state.stance();
	}

	/**
	 * True while the mob must not hold a band, a shield, or a standoff. RETREAT suppresses them. NORMAL keeps the old rule
	 * of a mob at 35 percent health or less. FIGHT, KITE, and PURSUE keep them even at low health: the mob fights on.
	 */
	public static boolean suppresses(final Mob mob) {
		Stance stance = of(mob);
		if (stance == RETREAT) {
			return true;
		}

		return stance == NORMAL && mob.getHealth() <= mob.getMaxHealth() * RetreatGoal.HEALTH_FRACTION;
	}

	/**
	 * Holds the mob in KITE for this many of its own ticks. A mob in RETREAT moves to KITE at once. Cover logic calls it
	 * when a pursued unit finds nowhere to hide.
	 */
	public static void requestKite(final Mob mob, final int ticks) {
		StanceState.get(mob).holdKite(mob, mob.tickCount + ticks);
	}

	/** Called by the retreat goal when a stance retreat ends. The mob then kites. */
	static void retreatEnded(final Mob mob) {
		if (of(mob) == RETREAT) {
			StanceState.get(mob).endRetreat(mob);
		}
	}
}
