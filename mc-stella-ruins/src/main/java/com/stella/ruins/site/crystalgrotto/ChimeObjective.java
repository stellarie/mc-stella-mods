package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.SiteRun;
import java.util.Arrays;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The chime-and-strike objective. The crystals chime in an order, and the player strikes them in the same order.
 * There are three rounds, of 4, 5 and 6 chimes. A wrong strike summons Shard Sentinels and starts a new order of
 * the same length. A right-click on the core replays the order, and each replay adds one Prism Slime.
 * <p>
 * The core builds a new instance for every call, so all state lives in {@link #save()}. The orders come from the
 * run random, never from the layout seed, so every reset plays new orders.
 * <p>
 * The core ticks the objective every 20 ticks, so a tick plays one chime and the chimes are 20 ticks apart. A
 * strike arrives between ticks, through {@link ChimeStrikes}, which has no run and so calls {@link #strike}.
 */
public final class ChimeObjective implements Objective {
	public static final int ROUNDS = 3;
	public static final int FIRST_LENGTH = 4;
	/** The tries at a new order before the last chime is changed by hand. */
	private static final int REDRAW_TRIES = 16;

	/** What a strike did. */
	public enum Outcome {
		/** The crystals were chiming, or the objective is done: the strike counted for nothing. */
		IGNORED,
		CORRECT,
		/** The last chime of a round was struck, and the next round's order began. */
		ROUND_CLEARED,
		/** A wrong crystal: new order, same length. The caller summons the sentinels. */
		WRONG,
		/** The last chime of the last round. The core completes on its next pulse. */
		COMPLETE
	}

	private int round;
	private int[] order = new int[0];
	private int progress;
	/** The next chime to play, or -1 while the player's turn is on. */
	private int playIndex = -1;
	private int replays;
	private boolean done;

	public static int lengthOf(final int round) {
		return FIRST_LENGTH + round;
	}

	/** A new order of {@code length} chimes over {@code pillars} crystals, with no crystal twice in a row. */
	public static int[] draw(final int pillars, final int length, final RandomSource random) {
		int[] order = new int[length];
		for (int i = 0; i < length; i++) {
			if (i == 0) {
				order[i] = random.nextInt(pillars);
			} else {
				int pick = random.nextInt(pillars - 1);
				order[i] = pick >= order[i - 1] ? pick + 1 : pick;
			}
		}
		return order;
	}

	/** A new order of the same length that differs from the old one in at least one place. */
	public static int[] redraw(final int[] old, final int pillars, final RandomSource random) {
		for (int attempt = 0; attempt < REDRAW_TRIES; attempt++) {
			int[] next = draw(pillars, old.length, random);
			if (!Arrays.equals(next, old)) {
				return next;
			}
		}
		return forceDifferent(old, pillars, random);
	}

	/** A valid order whose first chime differs from the old order's first chime. */
	static int[] forceDifferent(final int[] old, final int pillars, final RandomSource random) {
		int[] forced = draw(pillars, old.length, random);
		int avoid = forced.length > 1 ? forced[1] : -1;
		for (int value = 0; value < pillars; value++) {
			if (value != old[0] && value != avoid) {
				forced[0] = value;
				break;
			}
		}
		return forced;
	}

	@Override
	public void start(final SiteRun run) {
		this.round = 0;
		this.progress = 0;
		this.replays = 0;
		this.done = false;
		int pillars = run.markers(GrottoMarkers.PILLAR).size();
		if (pillars < 2) {
			this.done = true;
			this.order = new int[0];
			return;
		}
		this.order = draw(pillars, lengthOf(0), run.random());
		this.playIndex = 0;
		resetSpokes(run.level(), run::markers, pillars);
		announce(run.core(), this.round, this.order, "start");
	}

	@Override
	public Progress tick(final SiteRun run) {
		if (this.done) {
			return Progress.COMPLETE;
		}
		if (this.playIndex >= 0 && this.playIndex < this.order.length) {
			playChime(run.level(), run.markers(GrottoMarkers.PILLAR).get(this.order[this.playIndex]));
			this.playIndex++;
		}
		if (this.playIndex >= this.order.length) {
			this.playIndex = -1;
		}
		return Progress.RUNNING;
	}

	/** A right-click on the core while the player's turn is on replays the order. The price is one Prism Slime. */
	@Override
	public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		if (this.done || this.playIndex >= 0 || this.order.length == 0) {
			return false;
		}
		this.playIndex = 0;
		this.progress = 0;
		this.replays++;
		resetSpokes(run.level(), run::markers, run.markers(GrottoMarkers.PILLAR).size());
		List<BlockPos> spots = run.markers(RuinMarkers.SPAWN);
		if (!spots.isEmpty()) {
			BlockPos spot = spots.get(run.random().nextInt(spots.size()));
			run.spawn(GrottoMobs.PRISM_SLIME, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
		}
		player.sendOverlayMessage(Component.translatable("message.mod-stella-ruins.chime.replay"));
		log(run.core(), "replay " + this.replays);
		return true;
	}

	/**
	 * A player struck pillar {@code pillar}. The strike counts only on the player's turn. Sounds and floor spokes
	 * change here; the caller summons sentinels after {@link Outcome#WRONG}.
	 */
	public Outcome strike(final ServerLevel level, final Map<String, List<BlockPos>> markers, final int pillar, final RandomSource random) {
		if (this.done || this.playIndex >= 0 || this.order.length == 0) {
			return Outcome.IGNORED;
		}
		List<BlockPos> pillars = markers.getOrDefault(GrottoMarkers.PILLAR, List.of());
		if (pillar < 0 || pillar >= pillars.size()) {
			return Outcome.IGNORED;
		}
		playChime(level, pillars.get(pillar));
		if (this.order[this.progress] != pillar) {
			level.playSound(null, pillars.get(pillar), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
			level.playSound(null, pillars.get(pillar), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 2.0F, 0.5F);
			this.order = redraw(this.order, pillars.size(), random);
			announce(pillars.get(0), this.round, this.order, "wrong strike");
			this.progress = 0;
			this.playIndex = 0;
			resetSpokes(level, markers::get, pillars.size());
			return Outcome.WRONG;
		}
		setSpoke(level, markers.get(GrottoMarkers.spoke(pillar)), true);
		this.progress++;
		if (this.progress < this.order.length) {
			return Outcome.CORRECT;
		}
		this.round++;
		this.progress = 0;
		this.replays = 0;
		if (this.round >= ROUNDS) {
			this.done = true;
			this.order = new int[0];
			level.playSound(null, pillars.get(pillar), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0F, 1.0F);
			return Outcome.COMPLETE;
		}
		this.order = draw(pillars.size(), lengthOf(this.round), random);
		announce(pillars.get(0), this.round, this.order, "round cleared");
		this.playIndex = 0;
		resetSpokes(level, markers::get, pillars.size());
		level.playSound(null, pillars.get(pillar), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 3.0F, 1.0F);
		return Outcome.ROUND_CLEARED;
	}

	// ---- state ----

	public int round() {
		return this.round;
	}

	public int[] order() {
		return this.order.clone();
	}

	public int progress() {
		return this.progress;
	}

	public boolean playing() {
		return this.playIndex >= 0;
	}

	public int replays() {
		return this.replays;
	}

	public boolean done() {
		return this.done;
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("round", this.round);
		tag.putIntArray("order", this.order);
		tag.putInt("progress", this.progress);
		
		tag.putInt("play", this.playIndex);
		tag.putInt("replays", this.replays);
		tag.putBoolean("done", this.done);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.round = tag.getIntOr("round", 0);
		this.order = tag.getIntArray("order").orElse(new int[0]);
		this.progress = tag.getIntOr("progress", 0);
		this.playIndex = tag.getIntOr("play", -1);
		this.replays = tag.getIntOr("replays", 0);
		this.done = tag.getBooleanOr("done", false);
	}

	// ---- chimes and spokes ----

	/** Plays one pillar's note at its top, with a rising note particle. */
	public static void playChime(final ServerLevel level, final BlockPos foot) {
		BlockPos top = foot;
		while (level.getBlockState(top.above()).is(ResonantCrystalBlock.BLOCK)) {
			top = top.above();
		}
		BlockState state = level.getBlockState(top);
		Tint tint = state.is(ResonantCrystalBlock.BLOCK) ? state.getValue(ResonantCrystalBlock.TINT) : Tint.CYAN;
		level.playSound(null, top, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0F, tint.pitch());
		level.sendParticles(ParticleTypes.NOTE, top.getX() + 0.5, top.getY() + 1.4, top.getZ() + 0.5, 0, (tint.semitones() + 12) / 24.0, 0.0, 0.0, 1.0);
		level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5, top.getY() + 1.2, top.getZ() + 0.5, 6, 0.15, 0.3, 0.15, 0.02);
	}

	private static void setSpoke(final ServerLevel level, final List<BlockPos> cells, final boolean lit) {
		if (cells == null) {
			return;
		}
		BlockState state = lit ? GrottoPlan.SPOKE_LIT : GrottoPlan.SPOKE_UNLIT;
		for (BlockPos cell : cells) {
			if (level.getBlockState(cell) != state) {
				level.setBlock(cell, state, 3);
			}
		}
	}

	private static void resetSpokes(final ServerLevel level, final java.util.function.Function<String, List<BlockPos>> markers, final int pillars) {
		for (int i = 0; i < pillars; i++) {
			setSpoke(level, markers.apply(GrottoMarkers.spoke(i)), false);
		}
	}

	private static void log(final BlockPos core, final String what) {
		StellaRuins.LOGGER.debug("[RUINS] chime core={} {}", core.toShortString(), what);
	}

	/** One line for each new order, so a run can be followed from the log. */
	private static void announce(final BlockPos near, final int round, final int[] order, final String why) {
		StellaRuins.LOGGER.info("[RUINS] chime near={} round={} order={} ({})", near.toShortString(), round + 1, Arrays.toString(order), why);
	}
}
