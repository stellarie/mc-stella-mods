package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteRun;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The cooling objective. The meteor is hot. A player who steps into the bowl, or who mines the first ore, starts a
 * timer of 2400 ticks. While it runs the golems strike and each mined ore wakes an Ember Slime. When it ends, the
 * ore that is left turns to cooled meteorite, the slimes die in smoke, and the objective is done.
 * <p>
 * A fight on the rim does not start the timer: only the bowl does, within 12 blocks of the core in all three
 * dimensions. The timer reads {@link RuinClock}, so a test moves it by replacing the clock.
 */
public final class CoolingObjective implements Objective {
	public static final int COOL_TICKS = 2400;
	public static final double BOWL_RADIUS = 12.0;
	public static final int SLIME_CAP = 6;

	private boolean started;
	private long startTick;

	@Override
	public void start(final SiteRun run) {
		this.started = false;
		this.startTick = 0L;
		List<BlockPos> golemSpots = run.markers(CraterMarkers.GOLEM);
		for (int i = 0; i < golemSpots.size(); i++) {
			Mob golem = run.spawn(CraterMobs.STAR_GOLEM, Vec3.atBottomCenterOf(golemSpots.get(i)));
			if (golem != null) {
				golem.addTag(CraterMobs.SLOT_TAG_PREFIX + i);
			}
		}
		for (BlockPos spot : run.markers(CraterMarkers.SLIME)) {
			Mob slime = run.spawn(CraterMobs.EMBER_SLIME, Vec3.atBottomCenterOf(spot));
			if (slime != null) {
				CraterMobs.size(slime);
			}
		}
		StellaRuins.LOGGER.info("[RUINS] meteor core={} woke golems={} slimes={}", run.core().toShortString(), golemSpots.size(), run.markers(CraterMarkers.SLIME).size());
	}

	@Override
	public Progress tick(final SiteRun run) {
		ServerLevel level = run.level();
		if (!this.started && playerInBowl(level, run.core())) {
			begin(level, run.core(), "bowl");
		}
		if (this.started && RuinClock.now(level) - this.startTick >= COOL_TICKS) {
			cool(run);
			return Progress.COMPLETE;
		}
		return Progress.RUNNING;
	}

	/** True when a player who counts stands within the bowl radius of the core, in 3D. */
	public static boolean playerInBowl(final ServerLevel level, final BlockPos core) {
		Vec3 centre = Vec3.atCenterOf(core);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(centre) <= BOWL_RADIUS * BOWL_RADIUS) {
				return true;
			}
		}
		return false;
	}

	private void begin(final ServerLevel level, final BlockPos core, final String why) {
		this.started = true;
		this.startTick = RuinClock.now(level);
		StellaRuins.LOGGER.info("[RUINS] meteor core={} timer started ({}) at tick {}", core.toShortString(), why, this.startTick);
	}

	/** A player mined a hot ore block. The first one starts the timer. Returns true when a slime woke. */
	public boolean oreMined(final ServerLevel level, final RuinData.Entry entry, final BlockPos ore) {
		if (!this.started) {
			begin(level, entry.pos(), "ore");
		}
		if (aliveSlimes(level, entry) >= SLIME_CAP) {
			return false;
		}
		Mob slime = CraterMobs.EMBER_SLIME.spawn(level, Vec3.atBottomCenterOf(ore));
		if (slime == null) {
			return false;
		}
		CraterMobs.size(slime);
		entry.addMob(slime.getUUID());
		return true;
	}

	/** How many Ember Slimes of this core's roster still live. */
	public static int aliveSlimes(final ServerLevel level, final RuinData.Entry entry) {
		int count = 0;
		for (UUID id : entry.roster()) {
			Entity mob = level.getEntity(id);
			if (mob != null && mob.isAlive() && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "ember_slime")) {
				count++;
			}
		}
		return count;
	}

	private void cool(final SiteRun run) {
		ServerLevel level = run.level();
		int cooled = 0;
		for (BlockPos ore : run.markers(CraterMarkers.ORE)) {
			if (level.getBlockState(ore).is(MeteorBlocks.STARMETAL_ORE)) {
				level.setBlock(ore, MeteorBlocks.COOLED_METEORITE.defaultBlockState(), 3);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, ore.getX() + 0.5, ore.getY() + 1.0, ore.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
				cooled++;
			}
		}
		level.playSound(null, run.core(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 3.0F, 0.6F);
		RuinData.Entry entry = RuinData.get(level).entry(run.core());
		int dead = 0;
		if (entry != null) {
			for (UUID id : entry.roster()) {
				Entity mob = level.getEntity(id);
				if (mob != null && mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "ember_slime")) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.03);
					mob.discard();
					dead++;
				}
			}
		}
		StellaRuins.LOGGER.info("[RUINS] meteor core={} cooled ore={} slimesGone={}", run.core().toShortString(), cooled, dead);
	}

	public boolean started() {
		return this.started;
	}

	public long startTick() {
		return this.startTick;
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("started", this.started);
		tag.putLong("start", this.startTick);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.started = tag.getBooleanOr("started", false);
		this.startTick = tag.getLongOr("start", 0L);
	}

	/** The site of this objective, for events that look one up. */
	static boolean isCrater(final RuinSite site) {
		return site != null && MeteorCraterSite.ID.equals(site.id());
	}
}
