package com.stella.mobs.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;

/**
 * The red slime: a beginner mob that attacks players on sight. It never splits and is never elite.
 * Mob levels scale its health and damage from the base values here.
 */
public final class RedSlime extends StellaSlime {
	public static final double BASE_HEALTH = 12.0;
	public static final double ATTACK_DAMAGE = 2.0;

	public RedSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
	}

	@Override
	protected void applyStats() {
		setBase(Attributes.MAX_HEALTH, BASE_HEALTH);
		setBase(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
	}
}
