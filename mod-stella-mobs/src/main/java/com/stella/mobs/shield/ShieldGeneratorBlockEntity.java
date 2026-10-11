package com.stella.mobs.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Marks the edges of its box with green sparkles, to every player within {@link #VIEW_RANGE} blocks. */
public final class ShieldGeneratorBlockEntity extends BlockEntity {
	/** Sparkle points drawn on the edges each tick. */
	public static final int POINTS_PER_TICK = 4;
	/** The farthest a player is from the generator and still sees the edges. */
	public static final double VIEW_RANGE = 48.0;
	private static final int EDGES = 12;
	private static final int SIDE = 2 * ShieldZones.HALF_SIZE + 1;

	/** Tests read each sparkle point as it goes out. */
	public interface Recorder {
		void sent(ShieldGeneratorBlockEntity generator, double x, double y, double z);
	}

	private static volatile Recorder recorder;

	// Its own source, so a generator never shifts the level's dice that every other system draws from.
	private final RandomSource random = RandomSource.create();
	private long sparklesSent;

	public ShieldGeneratorBlockEntity(final BlockPos pos, final BlockState state) {
		super(ShieldBlocks.ENTITY, pos, state);
	}

	public static void setRecorder(final Recorder value) {
		recorder = value;
	}

	/** Sparkle packets this generator has sent to players since it loaded. */
	public long sparklesSent() {
		return this.sparklesSent;
	}

	void serverTick(final ServerLevel level) {
		BlockPos at = getBlockPos();
		double rangeSquared = VIEW_RANGE * VIEW_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) > rangeSquared) {
				continue;
			}
			for (int i = 0; i < POINTS_PER_TICK; i++) {
				double[] point = edgePoint(at, this.random.nextInt(EDGES), this.random.nextDouble());
				if (level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, true, point[0], point[1], point[2], 1, 0.0, 0.0, 0.0, 0.0)) {
					this.sparklesSent++;
					Recorder watcher = recorder;
					if (watcher != null) {
						watcher.sent(this, point[0], point[1], point[2]);
					}
				}
			}
		}
	}

	/**
	 * A point on one of the twelve edges of the box. {@code edge} picks the axis along the edge (0 to 2) and which of the
	 * four parallel edges it is. {@code along} runs from 0 to 1 over the edge.
	 */
	static double[] edgePoint(final BlockPos center, final int edge, final double along) {
		int axis = edge % 3;
		int corner = edge / 3;
		double[] low = {center.getX() - ShieldZones.HALF_SIZE, center.getY() - ShieldZones.HALF_SIZE, center.getZ() - ShieldZones.HALF_SIZE};
		double[] point = new double[3];
		int bit = 0;
		for (int dimension = 0; dimension < 3; dimension++) {
			if (dimension == axis) {
				point[dimension] = low[dimension] + along * SIDE;
			} else {
				point[dimension] = low[dimension] + (((corner >> bit++) & 1) == 0 ? 0.0 : SIDE);
			}
		}
		return point;
	}
}
