package com.stella.mobs.entity;

import com.stella.mobs.behavior.EliteState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The blue slime: passive, never elite, never a boss. It attacks only as the escort of a white slime,
 * which the shared summoned flag marks. An escort is also worth no experience.
 */
public final class BlueSlime extends StellaSlime {
	private static final double TARGET_HEIGHT_BAND = 4.0;
	private static final int TARGET_INTERVAL = 10;

	public BlueSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
	}

	/** True for a slime a white slime summoned. Read without creating a state, so a natural slime saves none. */
	public boolean isEscort() {
		EliteState state = ((AttachmentTarget) this).getAttached(EliteState.TYPE);
		return state != null && state.summoned();
	}

	@Override
	protected void addTargetingGoals() {
		// The goal is always present and gated by the flag, because goals register before any flag can exist.
		this.targetSelector.addGoal(
			1,
			new NearestAttackableTargetGoal<>(
				this, Player.class, TARGET_INTERVAL, true, false,
				(target, level) -> isEscort() && Math.abs(target.getY() - getY()) <= TARGET_HEIGHT_BAND
			)
		);
	}

	@Override
	protected boolean canDealDamage() {
		return isEscort() && super.canDealDamage();
	}

	@Override
	protected int getBaseExperienceReward(final ServerLevel level) {
		return isEscort() ? 0 : super.getBaseExperienceReward(level);
	}
}
