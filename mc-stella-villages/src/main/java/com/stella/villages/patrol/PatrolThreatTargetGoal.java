package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

final class PatrolThreatTargetGoal extends TargetGoal {
	private final Mob guard;
	private final UUID villageId;

	PatrolThreatTargetGoal(final Mob guard, final UUID villageId) {
		super(guard, true);
		this.guard = guard;
		this.villageId = villageId;
		setUnseenMemoryTicks(40);
	}

	@Override
	public boolean canUse() {
		this.targetMob = PatrolService.threatenedPlayer(this.guard, this.villageId);
		return this.targetMob != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.targetMob != null
			&& this.targetMob.isAlive()
			&& this.targetMob.level() == this.guard.level()
			&& this.guard.distanceToSqr(this.targetMob)
				<= PatrolService.PATROL_DETECTION_RANGE * PatrolService.PATROL_DETECTION_RANGE
			&& PatrolService.hasThreat((ServerLevel) this.guard.level(), this.villageId, this.targetMob.getUUID());
	}

	@Override
	public void start() {
		super.start();
		if (this.targetMob != null) {
			this.guard.setTarget(this.targetMob);
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=threat_targeted village={} guard={} attacker={} distance={} accepted={} can_attack={} enemy={} invulnerable={} creative={} spectator={} difficulty={}",
				McStellaVillages.MOD_ID, this.villageId, this.guard.getUUID(), this.targetMob.getUUID(),
				this.guard.distanceTo(this.targetMob), this.guard.getTarget() == this.targetMob,
				this.guard.canAttack(this.targetMob), this.targetMob.canBeSeenAsEnemy(), this.targetMob.isInvulnerable(),
				this.targetMob instanceof Player player && player.isCreative(),
				this.targetMob instanceof Player player && player.isSpectator(),
				this.guard.level().getDifficulty()
			);
		}
	}

	@Override
	public void stop() {
		@Nullable UUID attackerId = this.targetMob == null ? null : this.targetMob.getUUID();
		super.stop();
		if (attackerId != null) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=threat_target_stopped village={} guard={} attacker={}",
				McStellaVillages.MOD_ID, this.villageId, this.guard.getUUID(), attackerId
			);
		}
	}
}
