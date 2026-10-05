package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Remembers which core the lamp answers to, finds it once, and follows its phase. The lamp burns while the core is
 * dormant or active. It is dark while the core is complete or cooling down.
 */
public final class WayshrineLampEntity extends BlockEntity {
	/** Ticks between two looks at the core. */
	public static final int PERIOD = 20;
	/** How far from the lamp the core is searched, in blocks. */
	public static final int SEARCH = 12;
	private static final int SEARCH_DOWN = 16;

	private @Nullable BlockPos core;
	private int counter;

	public WayshrineLampEntity(final BlockPos pos, final BlockState state) {
		super(WayshrineLamp.ENTITY, pos, state);
	}

	public @Nullable BlockPos core() {
		return this.core;
	}

	void serverTick(final ServerLevel level) {
		if (++this.counter % PERIOD == 0) {
			refresh(level);
		}
	}

	/** One look at the core. Tests call it directly. */
	public void refresh(final ServerLevel level) {
		if (this.core == null || !level.getBlockState(this.core).is(RuinBlocks.CORE)) {
			this.core = find(level);
			setChanged();
			if (this.core == null) {
				return;
			}
		}
		RuinData.Entry entry = RuinData.get(level).entry(this.core);
		RuinPhase phase = entry == null ? RuinPhase.DORMANT : entry.phase();
		boolean armed = phase == RuinPhase.DORMANT || phase == RuinPhase.ACTIVE;
		BlockState state = getBlockState();
		if (state.getValue(WayshrineLamp.LIT) != armed) {
			level.setBlock(this.worldPosition, state.setValue(WayshrineLamp.LIT, armed), Block.UPDATE_ALL);
		}
	}

	/** The nearest core inside the search box, in the chunks that are loaded. */
	private @Nullable BlockPos find(final ServerLevel level) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dz = -SEARCH; dz <= SEARCH; dz++) {
				for (int dy = -SEARCH_DOWN; dy <= 2; dy++) {
					BlockPos at = this.worldPosition.offset(dx, dy, dz);
					if (level.hasChunkAt(at) && level.getBlockState(at).is(RuinBlocks.CORE)) {
						double distance = at.distSqr(this.worldPosition);
						if (distance < bestDistance) {
							bestDistance = distance;
							best = at.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		if (this.core != null) {
			output.store("Core", BlockPos.CODEC, this.core);
		}
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.core = input.read("Core", BlockPos.CODEC).orElse(null);
	}
}
