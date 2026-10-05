package com.stella.ruins.site.spidernest;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bow strung with silkweave. It draws 20 percent faster: a draw of 16 ticks gives the power of a draw of 20 ticks
 * on a plain bow. It scales the ticks the player held, then lets the plain bow shoot, so a mixin is not needed.
 */
public final class SilkstringBow extends BowItem {
	/** The share of the draw time that the silk saves, as a factor on the held ticks. */
	public static final double DRAW_FACTOR = 1.2;

	public SilkstringBow(final Properties properties) {
		super(properties);
	}

	/** The ticks of a plain draw that a draw of {@code held} ticks counts as. */
	public static int effectiveTicks(final int held) {
		return (int) Math.ceil(held * DRAW_FACTOR);
	}

	@Override
	public boolean releaseUsing(final ItemStack stack, final Level level, final LivingEntity entity, final int timeLeft) {
		int duration = getUseDuration(stack, entity);
		int held = duration - timeLeft;
		return super.releaseUsing(stack, level, entity, duration - effectiveTicks(held));
	}
}
