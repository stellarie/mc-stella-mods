package com.stella.tower;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A lantern of full light. Every second it makes the invisible mobs within {@value #RANGE} blocks glow, so their outlines
 * show through the air they hide in.
 */
public class GhostLanternBlock extends LanternBlock {
	public static final double RANGE = 16.0;
	public static final int SCAN_TICKS = 20;
	/** How long a reveal lasts: more than a scan, so a mob in reach glows without a break. */
	public static final int GLOW_TICKS = SCAN_TICKS + 20;

	public GhostLanternBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState, final boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel server) {
			server.scheduleTick(pos, this, SCAN_TICKS);
		}
	}

	@Override
	protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		reveal(level, Vec3.atCenterOf(pos));
		level.scheduleTick(pos, this, SCAN_TICKS);
	}

	/** Makes every invisible mob within reach of a spot glow; the mobs it touched. */
	public static List<Mob> reveal(final ServerLevel level, final Vec3 center) {
		List<Mob> hidden = level.getEntitiesOfClass(Mob.class, AABB.ofSize(center, 2.0 * RANGE, 2.0 * RANGE, 2.0 * RANGE),
			mob -> mob.isAlive() && (mob.isInvisible() || mob.hasEffect(MobEffects.INVISIBILITY)) && mob.position().distanceToSqr(center) <= RANGE * RANGE);
		for (Mob mob : hidden) {
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, false));
		}
		return hidden;
	}
}
