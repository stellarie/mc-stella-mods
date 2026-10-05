package com.stella.ruins.site.ambushcamp;

import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.SiteRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Find and kill the hidden Bandit Chief. The camp wakes with the Chief invisible in one tent, a decoy bandit in every
 * other tent, patrols in the yard, and lookouts on the towers. A lit candle at the door of the Chief's tent, and one
 * at a random decoy tent, are the only tell. They go out when the core completes.
 * <p>
 * The core builds a new instance for every call, so all state lives in {@link #save()}. The Chief's tent comes from
 * the run random and never repeats the last tent of the same camp.
 * <p>
 * The Chief's reveal happens in {@link CampMobs}, between ticks, and writes the reveal time into the saved state.
 * The next tick after {@link #REINFORCE_DELAY} ticks sends reinforcements through the trail gap.
 */
public final class ChiefObjective implements Objective {
	/** Bandits that arrive through the trail gap after the reveal. */
	public static final int REINFORCEMENTS = 2;
	/** Ticks between the reveal and the reinforcements, on the {@link RuinClock}. */
	public static final int REINFORCE_DELAY = 100;

	private int chiefTent = -1;
	private int tellTent = -1;
	private boolean done;
	private boolean reinforced;
	private long revealedAt = -1L;

	/** The tent for the next Chief: random, and never {@code last} when another tent exists. */
	public static int pick(final RandomSource random, final int tents, final int last) {
		if (tents <= 1) {
			return 0;
		}
		boolean skip = last >= 0 && last < tents;
		int index = random.nextInt(skip ? tents - 1 : tents);
		return skip && index >= last ? index + 1 : index;
	}

	/** How many tents the run's layout drew. */
	public static int tentCount(final SiteRun run) {
		int count = 0;
		while (!run.markers(CampMarkers.tent(count + 1)).isEmpty()) {
			count++;
		}
		return count;
	}

	private static Vec3 at(final BlockPos spot) {
		return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	public int chiefTent() {
		return this.chiefTent;
	}

	public int tellTent() {
		return this.tellTent;
	}

	public boolean isDone() {
		return this.done;
	}

	public boolean hasReinforced() {
		return this.reinforced;
	}

	public long revealedAt() {
		return this.revealedAt;
	}

	@Override
	public void start(final SiteRun run) {
		this.done = false;
		this.reinforced = false;
		this.revealedAt = -1L;
		int tents = tentCount(run);
		if (tents == 0) {
			this.done = true;
			return;
		}
		ServerLevel level = run.level();
		CampMemory memory = CampMemory.get(level);
		this.chiefTent = pick(run.random(), tents, memory.last(run.core()));
		memory.remember(run.core(), this.chiefTent);
		List<Integer> decoys = new ArrayList<>();
		for (int tent = 0; tent < tents; tent++) {
			if (tent != this.chiefTent) {
				decoys.add(tent);
				run.spawn(CampMobs.BANDIT, at(run.markers(CampMarkers.tent(tent + 1)).get(0)));
			}
		}
		Mob chief = run.spawn(CampMobs.CHIEF, at(run.markers(CampMarkers.tent(this.chiefTent + 1)).get(0)));
		if (chief != null) {
			CampMobs.hide(chief);
		}
		for (BlockPos spot : run.markers(CampMarkers.PATROL)) {
			run.spawn(CampMobs.BANDIT, at(spot));
		}
		for (BlockPos spot : run.markers(CampMarkers.LOOKOUT)) {
			run.spawn(CampMobs.LOOKOUT, at(spot));
		}
		this.tellTent = decoys.isEmpty() ? -1 : decoys.get(run.random().nextInt(decoys.size()));
		light(run, this.chiefTent);
		if (this.tellTent >= 0) {
			light(run, this.tellTent);
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		if (this.done) {
			snuff(run);
			return Progress.COMPLETE;
		}
		if (this.revealedAt >= 0L && !this.reinforced && RuinClock.now(run.level()) - this.revealedAt >= REINFORCE_DELAY) {
			this.reinforced = true;
			reinforce(run);
		}
		return Progress.RUNNING;
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		if (CampMobs.isChief(mob)) {
			this.done = true;
		}
	}

	private void reinforce(final SiteRun run) {
		List<BlockPos> trail = run.markers(CampMarkers.TRAIL);
		if (trail.isEmpty()) {
			return;
		}
		for (int i = 0; i < REINFORCEMENTS; i++) {
			Mob mob = run.spawn(CampMobs.BANDIT, at(trail.get(0)).add(i - 0.5, 0.0, 0.0));
			if (mob != null) {
				mob.setTarget(run.level().getNearestPlayer(mob, CampMobs.CALL_RADIUS));
			}
		}
	}

	private static BlockState candle() {
		return Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
	}

	private void light(final SiteRun run, final int tent) {
		List<BlockPos> door = run.markers(CampMarkers.door(tent + 1));
		if (!door.isEmpty() && run.level().getBlockState(door.get(0)).isAir()) {
			run.level().setBlockAndUpdate(door.get(0), candle());
		}
	}

	private void snuff(final SiteRun run) {
		for (int tent : new int[] {this.chiefTent, this.tellTent}) {
			if (tent < 0) {
				continue;
			}
			List<BlockPos> door = run.markers(CampMarkers.door(tent + 1));
			if (!door.isEmpty() && run.level().getBlockState(door.get(0)).is(Blocks.CANDLE)) {
				run.level().setBlockAndUpdate(door.get(0), Blocks.AIR.defaultBlockState());
			}
		}
	}

	/**
	 * Called when the Chief is revealed. It writes the reveal time into the saved state of the Chief's core, because
	 * the reveal happens between ticks and has no run.
	 */
	static void noteReveal(final ServerLevel level, final Mob chief) {
		RuinData data = RuinData.get(level);
		BlockPos core = data.coreOf(chief.getUUID());
		RuinData.Entry entry = core == null ? null : data.entry(core);
		if (entry == null) {
			return;
		}
		CompoundTag tag = entry.objective();
		if (tag.getLongOr("revealedAt", -1L) < 0L) {
			tag.putLong("revealedAt", RuinClock.now(level));
			entry.setObjective(tag);
		}
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("chief", this.chiefTent);
		tag.putInt("tell", this.tellTent);
		tag.putBoolean("done", this.done);
		tag.putBoolean("reinforced", this.reinforced);
		tag.putLong("revealedAt", this.revealedAt);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.chiefTent = tag.getIntOr("chief", -1);
		this.tellTent = tag.getIntOr("tell", -1);
		this.done = tag.getBooleanOr("done", false);
		this.reinforced = tag.getBooleanOr("reinforced", false);
		this.revealedAt = tag.getLongOr("revealedAt", -1L);
	}
}
