package com.stella.tower.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's target selector, which vanilla keeps out of reach: a guardian drops every goal that picks it a target. */
@Mixin(Mob.class)
public interface MobSelectorAccessor {
	@Accessor("targetSelector")
	GoalSelector stellaTower$targetSelector();
}
