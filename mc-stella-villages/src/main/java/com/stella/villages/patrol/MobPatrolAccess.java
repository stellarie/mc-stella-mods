package com.stella.villages.patrol;

import net.minecraft.world.entity.ai.goal.GoalSelector;

public interface MobPatrolAccess {
	void setPatrolMarker(boolean marked);

	boolean isPatrolMarker();

	GoalSelector patrolTargetSelector();
}
