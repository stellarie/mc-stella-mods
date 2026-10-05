package com.stella.ruins.world;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The core's own memory is its frame, so it can redraw its site in a dry run and find its spawn points and its
 * vaults. Everything that changes lives in {@link RuinData}.
 */
public final class RuinCoreBlockEntity extends BlockEntity {
	/** Ticks between two checks of the core. */
	public static final int PULSE_TICKS = 20;

	private @Nullable RuinFrame frame;
	private int counter;
	private @Nullable Map<String, List<BlockPos>> markers;

	public RuinCoreBlockEntity(final BlockPos pos, final BlockState state) {
		super(RuinBlocks.CORE_ENTITY, pos, state);
	}

	/** Called by the layout when it places the core. */
	public void init(final RuinFrame placed) {
		this.frame = placed;
		this.markers = null;
		setChanged();
	}

	public @Nullable RuinFrame frame() {
		return this.frame;
	}

	void serverTick(final ServerLevel level) {
		if (++this.counter % PULSE_TICKS == 0) {
			pulse(level);
		}
	}

	/** One check of the core: wake for a player, tick the objective, or rearm. Tests call it directly. */
	public void pulse(final ServerLevel level) {
		RuinRuns.pulse(level, this);
	}

	/** The markers of this core's site, recovered by drawing it dry. Empty when the site is gone. */
	public Map<String, List<BlockPos>> markers() {
		if (this.markers == null) {
			RuinSite site = this.frame == null ? null : this.frame.siteOrNull();
			if (site == null) {
				return Map.of();
			}
			LayoutContext ctx = LayoutContext.forDryRun(this.frame);
			site.layout().draw(ctx);
			this.markers = ctx.markers();
		}
		return this.markers;
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		if (this.frame != null) {
			output.store("Frame", RuinFrame.CODEC, this.frame);
		}
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.frame = input.read("Frame", RuinFrame.CODEC).orElse(null);
		this.markers = null;
	}
}
