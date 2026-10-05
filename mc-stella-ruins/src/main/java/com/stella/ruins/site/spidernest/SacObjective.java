package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.SiteRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Break every egg sac before the clock runs out. The first sac that breaks starts the clock. When it runs out, the
 * rest of the sacs burst, the Nest Matriarch rises in the brood pit, and the Brood Crawlers swarm. Her death still
 * completes the core, with the smaller vault table.
 * <p>
 * The core builds a new instance for every call, so all state lives in {@link #save()}. The clock reads
 * {@link RuinClock}, so a test can move it. A break arrives between ticks, through {@link SacBreaks}, which has no
 * run and so calls {@link #sacBroken}. The core completes on its next pulse, up to one second later.
 */
public final class SacObjective implements Objective {
	/** The clock for a nest with {@link #BASE_SACS} sacs, in ticks. */
	public static final int BASE_TICKS = 1800;
	/** Added ticks for each sac above {@link #BASE_SACS}. */
	public static final int TICKS_PER_EXTRA_SAC = 60;
	public static final int BASE_SACS = 6;
	/** A player inside the bounds sees the sacs left and the seconds left. */
	public static final String CLOCK_MESSAGE = "message.mod-stella-ruins.nest.clock";

	/** Where the run stands. */
	public enum Stage {
		/** No sac has broken yet. */
		ARMED,
		TIMING,
		/** The clock ran out. The Matriarch lives. */
		HATCHED,
		/** The last sac broke, or the Matriarch died. The core completes on its next pulse. */
		DONE
	}

	/** What a break did. */
	public enum Outcome {
		/** The nest is not timing, or it already hatched. */
		IGNORED,
		/** The first sac: the clock started. */
		STARTED,
		COUNTED,
		/** The last sac. The core completes on its next pulse. */
		LAST
	}

	private Stage stage = Stage.ARMED;
	private long deadline;
	private int total;
	private boolean hatched;

	/** The clock for a nest of {@code sacs} sacs. */
	public static int durationFor(final int sacs) {
		return BASE_TICKS + TICKS_PER_EXTRA_SAC * Math.max(0, sacs - BASE_SACS);
	}

	/** How many of the nest's sacs still stand. */
	public static int countSacs(final ServerLevel level, final List<BlockPos> sacs) {
		int count = 0;
		for (BlockPos pos : sacs) {
			if (level.getBlockState(pos).is(EggSacBlock.BLOCK)) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void start(final SiteRun run) {
		this.stage = Stage.ARMED;
		this.deadline = 0L;
		this.hatched = false;
		this.total = run.markers(NestMarkers.SAC).size();
		NestHeartbeat.clear(run.level(), run.core());
		StellaRuins.LOGGER.info("[RUINS] nest core={} armed sacs={}", run.core().toShortString(), this.total);
	}

	@Override
	public Progress tick(final SiteRun run) {
		ServerLevel level = run.level();
		List<BlockPos> sacs = run.markers(NestMarkers.SAC);
		long now = RuinClock.now(level);
		int left = countSacs(level, sacs);
		if (this.stage == Stage.ARMED && left < this.total) {
			begin(run.core(), now);
		}
		if (this.stage == Stage.TIMING) {
			if (left == 0) {
				this.stage = Stage.DONE;
			} else if (now >= this.deadline) {
				hatch(run, sacs);
			} else {
				announce(level, run.core(), left, this.deadline - now);
				NestHeartbeat.track(level, run.core(), standing(level, sacs), this.deadline);
			}
		}
		if (this.stage == Stage.DONE) {
			finish(level, run.core());
			return Progress.COMPLETE;
		}
		return Progress.RUNNING;
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		if (this.stage == Stage.HATCHED && RuinMobs.kindOf(mob) == NestMobs.NEST_MATRIARCH) {
			this.stage = Stage.DONE;
			StellaRuins.LOGGER.info("[RUINS] nest core={} the Matriarch died", run.core().toShortString());
		}
	}

	/**
	 * A sac was removed from the world. The first one starts the clock. The caller has no run, so it passes the
	 * markers and the tick.
	 */
	public Outcome sacBroken(final ServerLevel level, final BlockPos core, final Map<String, List<BlockPos>> markers, final long now) {
		if (this.stage != Stage.ARMED && this.stage != Stage.TIMING) {
			return Outcome.IGNORED;
		}
		Outcome outcome = Outcome.COUNTED;
		if (this.stage == Stage.ARMED) {
			begin(core, now);
			outcome = Outcome.STARTED;
		}
		if (countSacs(level, markers.getOrDefault(NestMarkers.SAC, List.of())) == 0) {
			this.stage = Stage.DONE;
			NestHeartbeat.clear(level, core);
			outcome = Outcome.LAST;
		}
		return outcome;
	}

	private void begin(final BlockPos core, final long now) {
		this.stage = Stage.TIMING;
		this.deadline = now + durationFor(this.total);
		StellaRuins.LOGGER.info("[RUINS] nest core={} clock started ticks={} sacs={}", core.toShortString(), durationFor(this.total), this.total);
	}

	/**
	 * The clock ran out. The sacs that stand burst, and the Matriarch and her swarm come. The burst calls the break hook
	 * of each sac, but the objective saves its own stage after this tick and so overwrites what the hook wrote.
	 */
	private void hatch(final SiteRun run, final List<BlockPos> sacs) {
		ServerLevel level = run.level();
		this.stage = Stage.HATCHED;
		this.hatched = true;
		NestHeartbeat.clear(level, run.core());
		for (BlockPos pos : standing(level, sacs)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			level.sendParticles(ParticleTypes.ITEM_SLIME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, pos, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
		}
		int mothers = 0;
		for (BlockPos spot : run.markers(NestMarkers.MATRIARCH)) {
			if (run.spawn(NestMobs.NEST_MATRIARCH, Vec3.atBottomCenterOf(spot)) != null) {
				mothers++;
			}
		}
		int crawlers = 0;
		for (BlockPos spot : run.markers(NestMarkers.CRAWLER)) {
			if (run.spawn(NestMobs.BROOD_CRAWLER, Vec3.atBottomCenterOf(spot)) != null) {
				crawlers++;
			}
		}
		for (ServerPlayer player : playersIn(level, run.core())) {
			player.sendOverlayMessage(Component.translatable("message.mod-stella-ruins.nest.hatched"));
		}
		StellaRuins.LOGGER.info("[RUINS] nest core={} hatched matriarchs={} crawlers={}", run.core().toShortString(), mothers, crawlers);
	}

	private void finish(final ServerLevel level, final BlockPos core) {
		NestHeartbeat.clear(level, core);
		NestRegrowth.remember(level, core);
		if (this.hatched) {
			NestVaults.queue(level, core);
		}
	}

	private static List<BlockPos> standing(final ServerLevel level, final List<BlockPos> sacs) {
		List<BlockPos> list = new ArrayList<>();
		for (BlockPos pos : sacs) {
			if (level.getBlockState(pos).is(EggSacBlock.BLOCK)) {
				list.add(pos);
			}
		}
		return list;
	}

	// ---- the action bar ----

	/** The box of a core's site, or null when the core has no frame. */
	static BoundingBox boxOf(final ServerLevel level, final BlockPos core) {
		if (level.getBlockEntity(core) instanceof RuinCoreBlockEntity block) {
			RuinFrame frame = block.frame();
			return frame == null ? null : frame.box();
		}
		return null;
	}

	static List<ServerPlayer> playersIn(final ServerLevel level, final BlockPos core) {
		BoundingBox box = boxOf(level, core);
		List<ServerPlayer> inside = new ArrayList<>();
		if (box == null) {
			return inside;
		}
		for (ServerPlayer player : level.players()) {
			if (box.isInside(player.blockPosition())) {
				inside.add(player);
			}
		}
		return inside;
	}

	/** Shows the sacs left and the time left to every player inside the nest's bounds. Returns how many saw it. */
	public static int announce(final ServerLevel level, final BlockPos core, final int sacsLeft, final long ticksLeft) {
		List<ServerPlayer> inside = playersIn(level, core);
		long seconds = Math.max(0L, (ticksLeft + 19L) / 20L);
		Component message = Component.translatable(CLOCK_MESSAGE, sacsLeft, String.format("%d:%02d", seconds / 60L, seconds % 60L));
		for (ServerPlayer player : inside) {
			player.sendOverlayMessage(message);
		}
		return inside.size();
	}

	// ---- state ----

	public Stage stage() {
		return this.stage;
	}

	public long deadline() {
		return this.deadline;
	}

	public int total() {
		return this.total;
	}

	public boolean hatched() {
		return this.hatched;
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("stage", this.stage.name());
		tag.putLong("deadline", this.deadline);
		tag.putInt("total", this.total);
		tag.putBoolean("hatched", this.hatched);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.stage = Stage.ARMED;
		String name = tag.getStringOr("stage", Stage.ARMED.name());
		for (Stage candidate : Stage.values()) {
			if (candidate.name().equals(name)) {
				this.stage = candidate;
			}
		}
		this.deadline = tag.getLongOr("deadline", 0L);
		this.total = tag.getIntOr("total", 0);
		this.hatched = tag.getBooleanOr("hatched", false);
	}
}
