package com.stella.mobs.behavior;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;

/**
 * W09: an elite witch blinds every player within 16 blocks, refreshed every 20 ticks. It claims no
 * flag, so it can never contend with another goal.
 */
public class WitchEliteAuraGoal<T extends Witch> extends Goal {
	private final T witch;
	private long nextAuraTick;

	public WitchEliteAuraGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.witch);
		return state.playerInRange(this.witch) && WitchElite.isElite(this.witch);
	}

	@Override
	public boolean canContinueToUse() {
		return WitchElite.isElite(this.witch);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		long now = this.witch.level().getGameTime();
		if (now < this.nextAuraTick) {
			return;
		}

		this.nextAuraTick = now + WitchElite.AURA_INTERVAL_TICKS;
		double radiusSqr = WitchElite.AURA_RADIUS * WitchElite.AURA_RADIUS;

		// A box query never returns players, so the aura walks the player list.
		for (Player player : this.witch.level().players()) {
			if (player.isAlive() && player.distanceToSqr(this.witch) <= radiusSqr) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, WitchElite.AURA_DURATION_TICKS, 0), this.witch);
			}
		}
	}
}
