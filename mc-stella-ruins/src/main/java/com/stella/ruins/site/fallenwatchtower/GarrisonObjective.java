package com.stella.ruins.site.fallenwatchtower;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import com.stella.ruins.world.SiteRun;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kill the whole garrison. The objective spawns one mob at each marker of the tower: the markers decide how many,
 * because the tower has 4 to 6 floors and a breach that costs it posts. Each mob is tagged with the box of the
 * shaft's inside and its own post, so that it cannot be carried out through the breach.
 */
public final class GarrisonObjective implements Objective {
	/** Ticks a living lookout must see a player before it lights the signal. */
	public static final long SIGNAL_TICKS = 600L;
	/** Crossbowmen of the late squad. */
	public static final int LATE_SQUAD = 3;
	public static final String LOOKOUT_TAG = "mod-stella-ruins.lookout";

	private long seen;
	private long last = -1L;
	private boolean signalled;

	@Override
	public void start(final SiteRun run) {
		this.seen = 0L;
		this.last = -1L;
		this.signalled = false;
		List<BlockPos> low = run.markers(WatchtowerMobs.BOUND_MIN);
		List<BlockPos> high = run.markers(WatchtowerMobs.BOUND_MAX);
		BlockPos a = low.isEmpty() || high.isEmpty() ? null : low.get(0);
		BlockPos b = a == null ? null : high.get(0);
		List<Mob> archers = spawnAll(run, WatchtowerPlan.ARCHER, WatchtowerMobs.WATCH_ARCHER, a, b);
		if (!archers.isEmpty()) {
			archers.get(0).addTag(LOOKOUT_TAG);
		}
		spawnAll(run, WatchtowerPlan.CROSSBOW, WatchtowerMobs.TOWER_CROSSBOWMAN, a, b);
		spawnAll(run, WatchtowerPlan.CAPTAIN, WatchtowerMobs.WATCH_CAPTAIN, a, b);
	}

	private static List<Mob> spawnAll(final SiteRun run, final String marker, final RuinMobKind kind, final BlockPos low, final BlockPos high) {
		List<Mob> spawned = new ArrayList<>();
		for (BlockPos spot : run.markers(marker)) {
			Mob mob = run.spawn(kind, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
			if (mob != null && low != null) {
				mob.addTag(WatchtowerMobs.garrisonTag(
					Math.min(low.getX(), high.getX()), Math.min(low.getZ(), high.getZ()),
					Math.max(low.getX(), high.getX()), Math.max(low.getZ(), high.getZ()),
					spot.getX(), spot.getY(), spot.getZ()));
			}
			if (mob != null) {
				spawned.add(mob);
			}
		}
		return spawned;
	}

	@Override
	public Progress tick(final SiteRun run) {
		watch(run);
		return run.alive() == 0 ? Progress.COMPLETE : Progress.RUNNING;
	}

	/** Counts the ticks a living lookout has a player in sight. At {@link #SIGNAL_TICKS} it lights the signal once. */
	private void watch(final SiteRun run) {
		long now = RuinClock.now(run.level());
		long gone = this.last < 0L ? 0L : Math.max(0L, now - this.last);
		this.last = now;
		if (this.signalled || !lookoutAlive(run) || run.playersNear().isEmpty()) {
			return;
		}
		this.seen += gone;
		if (this.seen >= SIGNAL_TICKS) {
			signal(run);
		}
	}

	private static boolean lookoutAlive(final SiteRun run) {
		String core = RuinMobs.coreTag(run.core().getX(), run.core().getY(), run.core().getZ());
		return !run.level().getEntitiesOfClass(Mob.class, new AABB(run.core()).inflate(WatchtowerPlan.SIZE_Y),
			mob -> mob.isAlive() && mob.entityTags().contains(LOOKOUT_TAG) && mob.entityTags().contains(core)).isEmpty();
	}

	/** The signal fire burns on the roof and a squad of crossbowmen arrives at the foot of the tower. */
	private void signal(final SiteRun run) {
		this.signalled = true;
		for (BlockPos roof : run.markers(WatchtowerPlan.CAPTAIN)) {
			if (run.level().getBlockState(roof).isAir()) {
				run.level().setBlock(roof, Blocks.SOUL_CAMPFIRE.defaultBlockState(), 3);
			}
			break;
		}
		BlockPos core = run.core();
		for (int i = 0; i < LATE_SQUAD; i++) {
			double angle = i * Math.PI * 2.0 / LATE_SQUAD;
			run.spawn(WatchtowerMobs.TOWER_CROSSBOWMAN, new Vec3(core.getX() + 0.5 + Math.cos(angle) * 3.0, core.getY() + 1.0, core.getZ() + 0.5 + Math.sin(angle) * 3.0));
		}
		for (ServerPlayer player : run.playersNear()) {
			player.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.watchtower.signal", "A fire flares on the roof. A squad rides for the gate."));
		}
		StellaRuins.LOGGER.info("[RUINS] watchtower core={} signal lit, late squad {}", core.toShortString(), LATE_SQUAD);
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("seen", this.seen);
		tag.putLong("last", this.last);
		tag.putBoolean("signalled", this.signalled);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.seen = tag.getLongOr("seen", 0L);
		this.last = tag.getLongOr("last", -1L);
		this.signalled = tag.getBooleanOr("signalled", false);
	}
}
