package com.stella.ruins.site.sealedvault;

import com.stella.ruins.StellaRuins;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a trap plate does. An arrow plate fires every dart vent near it. A drop plate opens the pit under its row.
 * Either way the whole row of plates goes dark, and after {@link #RESET_TICKS} the pit closes and the row rearms.
 * <p>
 * The reset has two clocks. The server tick counter of this class always runs. A scheduled block tick also runs on a
 * real server, in case a restart lost the counter. The reset is safe to run twice.
 */
public final class VaultTraps {
	public static final int RESET_TICKS = 100;
	/** How far from a plate a vent still takes part. */
	public static final int VENT_REACH = 8;
	public static final float ARROW_SPEED = 1.6F;
	public static final double ARROW_DAMAGE = 2.0;
	/** The most blocks one flood visits. A pit is 18 covers and a row is 6 plates. */
	private static final int FLOOD_LIMIT = 64;

	private record Pending(long due, ServerLevel level, BlockPos plate) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static long ticks;
	private static int arrowsFired;
	private static int pitsOpened;

	private VaultTraps() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** Arrows fired since the server started. Tests read it. */
	public static int arrowsFired() {
		return arrowsFired;
	}

	public static int pitsOpened() {
		return pitsOpened;
	}

	/** Fires the trap of an armed plate. Does nothing for a plate that is dark. */
	public static void fire(final ServerLevel level, final BlockPos plate, final BlockState state) {
		if (!(state.getBlock() instanceof VaultBlocks.PlateBlock) || !state.getValue(VaultBlocks.PlateBlock.ARMED)) {
			return;
		}
		setRow(level, plate, false);
		if (state.is(VaultBlocks.ARROW_PLATE)) {
			shoot(level, plate);
		} else {
			setPit(level, plate.below(), true);
			pitsOpened++;
		}
		level.playSound(null, plate, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5F, 0.6F);
		synchronized (PENDING) {
			PENDING.add(new Pending(ticks + RESET_TICKS, level, plate.immutable()));
		}
		level.scheduleTick(plate, state.getBlock(), RESET_TICKS);
		StellaRuins.LOGGER.debug("[RUINS] vault plate {} fired {}", plate.toShortString(), state.is(VaultBlocks.ARROW_PLATE) ? "arrows" : "pit");
	}

	/** Closes the pit under a plate and arms its row again. */
	public static void reset(final ServerLevel level, final BlockPos plate) {
		if (!level.isLoaded(plate) || !(level.getBlockState(plate).getBlock() instanceof VaultBlocks.PlateBlock)) {
			return;
		}
		setPit(level, plate.below(), false);
		setRow(level, plate, true);
	}

	private static void tick() {
		ticks++;
		List<Pending> due = new ArrayList<>();
		synchronized (PENDING) {
			PENDING.removeIf(pending -> {
				if (pending.due() <= ticks) {
					due.add(pending);
					return true;
				}
				return false;
			});
		}
		for (Pending pending : due) {
			reset(pending.level(), pending.plate());
		}
	}

	/** The plates that touch each other from one plate, on one level, of the same kind. */
	static List<BlockPos> row(final ServerLevel level, final BlockPos plate) {
		Block kind = level.getBlockState(plate).getBlock();
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(plate.immutable());
		seen.add(plate.immutable());
		while (!queue.isEmpty() && found.size() < FLOOD_LIMIT) {
			BlockPos at = queue.poll();
			found.add(at);
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(side);
				if (seen.add(next) && level.getBlockState(next).is(kind)) {
					queue.add(next);
				}
			}
		}
		return found;
	}

	private static void setRow(final ServerLevel level, final BlockPos plate, final boolean armed) {
		for (BlockPos at : row(level, plate)) {
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof VaultBlocks.PlateBlock && state.getValue(VaultBlocks.PlateBlock.ARMED) != armed) {
				level.setBlock(at, state.setValue(VaultBlocks.PlateBlock.ARMED, armed), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Opens or closes every cover that touches the cover under a plate. */
	static int setPit(final ServerLevel level, final BlockPos start, final boolean open) {
		int changed = 0;
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		if (level.getBlockState(start).is(VaultBlocks.PIT_COVER)) {
			queue.add(start.immutable());
			seen.add(start.immutable());
		}
		int visited = 0;
		while (!queue.isEmpty() && visited++ < FLOOD_LIMIT) {
			BlockPos at = queue.poll();
			BlockState state = level.getBlockState(at);
			if (state.getValue(VaultBlocks.PitCoverBlock.OPEN) != open) {
				level.setBlock(at, state.setValue(VaultBlocks.PitCoverBlock.OPEN, open), Block.UPDATE_CLIENTS);
				changed++;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(side);
				if (seen.add(next) && level.getBlockState(next).is(VaultBlocks.PIT_COVER)) {
					queue.add(next);
				}
			}
		}
		return changed;
	}

	/** Every dart vent near the plate shoots one arrow along its facing. Returns how many fired. */
	static int shoot(final ServerLevel level, final BlockPos plate) {
		int fired = 0;
		for (BlockPos at : BlockPos.betweenClosed(plate.offset(-VENT_REACH, 0, -VENT_REACH), plate.offset(VENT_REACH, 3, VENT_REACH))) {
			BlockState state = level.getBlockState(at);
			if (!state.is(VaultBlocks.VENT)) {
				continue;
			}
			Direction facing = state.getValue(VaultBlocks.VentBlock.FACING);
			Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
			if (arrow == null) {
				continue;
			}
			arrow.setPos(at.getX() + 0.5 + facing.getStepX() * 0.7, at.getY() + 0.2, at.getZ() + 0.5 + facing.getStepZ() * 0.7);
			arrow.setBaseDamage(ARROW_DAMAGE);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.addTag("mod-stella-ruins.vault_arrow");
			arrow.shoot(facing.getStepX(), 0.02, facing.getStepZ(), ARROW_SPEED, 0.5F);
			level.addFreshEntity(arrow);
			fired++;
			arrowsFired++;
		}
		return fired;
	}
}
