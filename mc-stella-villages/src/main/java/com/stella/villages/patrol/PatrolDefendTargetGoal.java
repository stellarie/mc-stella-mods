package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * A patrol defends: any mob that has a player or a villager as its target becomes the patrol's target, of
 * whatever kind it is, a slime or a wolf as much as a zombie. Fellow members, the mobs mod's friendly
 * patrols and golems are never taken.
 */
final class PatrolDefendTargetGoal extends TargetGoal {
	/** The mobs mod's friendly pillager pairs, named by their tag so this mod needs no compile-time link. */
	static final String FRIENDLY_PATROL_TAG = "mod-stella-mobs.friendly_patrol";
	private static final int SEARCH_INTERVAL = 10;
	private final Mob guard;
	private final UUID villageId;

	PatrolDefendTargetGoal(final Mob guard, final UUID villageId) {
		super(guard, false);
		this.guard = guard;
		this.villageId = villageId;
	}

	@Override
	public boolean canUse() {
		// A random beat, as vanilla's own target search keeps, so a patrol never searches all on one tick.
		if (this.guard.getRandom().nextInt(reducedTickDelay(SEARCH_INTERVAL)) != 0) {
			return false;
		}
		double range = PatrolService.PATROL_DETECTION_RANGE;
		AABB box = this.guard.getBoundingBox().inflate(range);
		this.targetMob = this.guard.level().getEntitiesOfClass(Mob.class, box, PatrolDefendTargetGoal::isThreat).stream()
			.filter(threat -> threat.distanceToSqr(this.guard) <= range * range)
			.filter(threat -> PatrolService.withinHeadBell(this.guard,
				PatrolService.bellFor(this.guard, this.villageId), threat.position()))
			.min(Comparator.comparingDouble(threat -> threat.distanceToSqr(this.guard)))
			.orElse(null);
		return this.targetMob != null;
	}

	static boolean isThreat(final Mob mob) {
		LivingEntity victim = mob.getTarget();
		return mob.isAlive() && victim != null && victim.isAlive()
			&& (victim instanceof Player || victim instanceof AbstractVillager)
			&& !PatrolState.isMarked(mob) && !mob.entityTags().contains(FRIENDLY_PATROL_TAG) && !(mob instanceof AbstractGolem);
	}

	@Override
	public void start() {
		this.guard.setTarget(this.targetMob);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=patrol_defends village={} guard={} threat={} type={} victim={}",
			McStellaVillages.MOD_ID, this.villageId, this.guard.getUUID(), this.targetMob.getUUID(),
			this.targetMob.getType(), this.targetMob instanceof Mob threat && threat.getTarget() != null ? threat.getTarget().getType() : null
		);
		super.start();
	}
}
