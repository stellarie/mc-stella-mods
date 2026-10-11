package com.stella.tower;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The tower's peace: its mobs are one side, whatever their kinds' quarrels in the game. A tower mob never takes another
 * for its target, and no blow, shot or blast of one hurts another. A ghost is a tower mob by record but not by side: it
 * hunts the tower's mobs and may be hunted back, and it never hurts a player.
 */
public final class TowerPeace {
	private TowerPeace() {
	}

	/** Whether the entity is a mob of the tower's side: a tower mob that is no ghost. */
	public static boolean ofTheTower(final @Nullable Entity entity) {
		return entity instanceof LivingEntity living && !(entity instanceof Ghostly) && TowerMobs.stateOf(living) != null;
	}

	/** Whether two entities are different mobs of the tower's side, and so spare each other. */
	public static boolean fellows(final @Nullable Entity a, final @Nullable Entity b) {
		return a != b && ofTheTower(a) && ofTheTower(b);
	}

	/**
	 * Whether a hit on the victim is turned away: the mob that caused it, the shooter of a shot or the maker of a blast,
	 * is a fellow of the victim; or a ghost is the cause and the victim is a player or a fellow ghost.
	 */
	public static boolean spares(final LivingEntity victim, final DamageSource source) {
		Entity cause = source.getEntity();
		return fellows(victim, cause) || (victim instanceof Player || victim instanceof Ghostly) && cause instanceof Ghostly;
	}
}
