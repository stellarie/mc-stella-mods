package com.stella.spells.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's target selector, which the game keeps to the mob itself. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("targetSelector")
	GoalSelector stellaSpells$targetSelector();
}
