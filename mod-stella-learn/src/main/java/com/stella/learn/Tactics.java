package com.stella.learn;

import com.stella.mobs.behavior.ShieldUseGoal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Which tactics are open to a mob in a situation. A shield needs a shield in hand. Cover needs a spot that leaves the
 * shooter no nearer. A mob whose shield goal guards against a player keeps its shield and its advance.
 */
public final class Tactics {
	private Tactics() {
	}

	public static boolean hasShield(final Mob mob) {
		return mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/**
	 * True while the mobs mod's shield goal runs against a player.
	 * Its guard walks on under a raised shield.
	 * A shield or a step back from this goal would end that.
	 */
	static boolean guarding(final Mob mob) {
		if (!(mob.getTarget() instanceof Player)) {
			return false;
		}

		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.isRunning() && wrapped.getGoal() instanceof ShieldUseGoal<?>) {
				return true;
			}
		}

		return false;
	}

	public static List<Tactic> available(final Mob mob, final ServerPlayer player, final Situation situation) {
		List<Tactic> open = new ArrayList<>();
		switch (situation) {
			case SWING -> {
				if (hasShield(mob)) {
					open.add(Tactic.SHIELD);
				}

				open.add(Tactic.BACK_OFF);
				open.add(Tactic.TRADE);
			}
			case PROJECTILE -> {
				open.add(Tactic.EVADE);
				if (hasShield(mob)) {
					open.add(Tactic.SHIELD);
				}

				open.add(Tactic.TANK);
			}
			case AFTER_SHOT -> {
				if (Cover.findHide(mob, player) != null) {
					open.add(Tactic.HIDE);
				}

				open.add(Tactic.HOLD);
			}
		}

		if (guarding(mob)) {
			open.removeIf(tactic -> tactic == Tactic.SHIELD || tactic == Tactic.BACK_OFF);
		}

		return open;
	}

	/** True for a tactic the mob has to walk or raise something to carry out; the rest are just carrying on. */
	public static boolean acts(final Tactic tactic) {
		return tactic == Tactic.SHIELD || tactic == Tactic.BACK_OFF || tactic == Tactic.EVADE || tactic == Tactic.HIDE;
	}
}
