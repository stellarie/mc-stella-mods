package com.stella.learn.model;

import net.minecraft.world.entity.Mob;

/**
 * Where a model-driven archer gets its next action. The default source runs the loaded network; the gym sets its own
 * per mob through {@link ModelDriver#setActionSource}. `observation` is the contract-sized, scaled vector and is
 * reused by the caller, so a source must not keep it.
 */
@FunctionalInterface
public interface ActionSource {
	ArcherAction decide(Mob mob, float[] observation);
}
