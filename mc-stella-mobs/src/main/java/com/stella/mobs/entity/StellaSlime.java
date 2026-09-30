package com.stella.mobs.entity;

import net.minecraft.core.Holder;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The shared body of the three colored slimes: always the medium size, and never splitting on death.
 * A subclass sets its own stats in {@link #applyStats()}, because vanilla {@code setSize} resets the
 * health, speed and damage of the slime every time it runs.
 */
public abstract class StellaSlime extends Slime {
	/** The medium slime: vanilla small is 1 and big is 4. */
	public static final int SIZE = 2;

	protected StellaSlime(final EntityType<? extends Slime> type, final Level level) {
		super(type, level);
		// A spawn path that skips finalizeSpawn must still give a medium slime.
		setSize(SIZE, true);
	}

	@Override
	public void setSize(final int size, final boolean heal) {
		super.setSize(SIZE, false);
		applyStats();
		if (heal) {
			setHealth(getMaxHealth());
		}
	}

	/** Runs after every size change, so a stat set here survives the reset that vanilla does. */
	protected void applyStats() {
	}

	protected final void setBase(final Holder<Attribute> attribute, final double value) {
		AttributeInstance instance = getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	protected void setSpawnSize(final ServerLevelAccessor level, final DifficultyInstance difficulty) {
		setSize(SIZE, true);
	}

	@Override
	protected int getSplitCount() {
		return 0;
	}
}
