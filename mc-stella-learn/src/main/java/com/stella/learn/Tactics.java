package com.stella.learn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** Which tactics are open to a mob in a situation: a shield needs a shield in hand, and cover needs somewhere to go. */
public final class Tactics {
	private Tactics() {
	}

	public static boolean hasShield(final Mob mob) {
		return mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
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
				if (Cover.find(mob, player) != null) {
					open.add(Tactic.HIDE);
				}

				open.add(Tactic.HOLD);
			}
		}

		return open;
	}

	/** True for a tactic the mob has to walk or raise something to carry out; the rest are just carrying on. */
	public static boolean acts(final Tactic tactic) {
		return tactic == Tactic.SHIELD || tactic == Tactic.BACK_OFF || tactic == Tactic.EVADE || tactic == Tactic.HIDE;
	}
}
