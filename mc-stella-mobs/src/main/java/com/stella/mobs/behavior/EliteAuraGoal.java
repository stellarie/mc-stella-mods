package com.stella.mobs.behavior;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * W09 and O02/O03: one flagless aura, shared by the elite witch, the elite guardian and the forced
 * elder. It applies its effects to every player inside its radius, refreshed on the mob's own tick
 * counter rather than the level clock, which barely advances under the game test runner. It claims no
 * flag, so it can neither starve nor be starved by a vanilla goal.
 */
public class EliteAuraGoal<T extends Mob> extends Goal {
	/** The witch's 16 blocks, which O02 reuses for the elite guardian. */
	public static final double RADIUS = 16.0;
	public static final int DURATION_TICKS = 100;
	public static final int INTERVAL_TICKS = 20;
	/** Every aura effect lands at this amplifier, so a clause can name it instead of a literal. */
	public static final int AMPLIFIER = 0;
	/** The most players the record keeps, because a guardian writes for as long as it lives. */
	public static final int RECORD_LIMIT = 64;

	private final T mob;
	private final double radius;
	private final List<Holder<MobEffect>> effects;
	/** The players this goal itself applied to, so a clause reads what the aura produced. */
	private final Set<UUID> affected = new LinkedHashSet<>();
	private int nextAuraTick;

	public EliteAuraGoal(final T mob, final double radius, final List<Holder<MobEffect>> effects) {
		this.mob = mob;
		this.radius = radius;
		this.effects = effects;
	}

	/** The players this goal applied to, so a test reads the aura's own product rather than the world. */
	public Set<UUID> affected() {
		return Set.copyOf(this.affected);
	}

	@Override
	public boolean canUse() {
		return Elite.isElite(this.mob) && ReactiveState.get(this.mob).playerInRange(this.mob);
	}

	@Override
	public boolean canContinueToUse() {
		return Elite.isElite(this.mob);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		if (this.mob.tickCount < this.nextAuraTick) {
			return;
		}

		this.nextAuraTick = this.mob.tickCount + INTERVAL_TICKS;
		double radiusSqr = this.radius * this.radius;

		// A box query never returns players, so the aura walks the player list.
		for (Player player : this.mob.level().players()) {
			if (!player.isAlive() || player.distanceToSqr(this.mob) > radiusSqr) {
				continue;
			}

			for (Holder<MobEffect> effect : this.effects) {
				player.addEffect(new MobEffectInstance(effect, DURATION_TICKS, AMPLIFIER), this.mob);
			}

			if (this.affected.size() < RECORD_LIMIT) {
				this.affected.add(player.getUUID());
			}
		}
	}
}
