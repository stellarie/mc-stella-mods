package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinConfig;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinTrophies;
import com.stella.ruins.world.RuinVaultBlockEntity;
import com.stella.ruins.world.Roster;
import com.stella.ruins.world.SiteRun;
import com.stella.ruins.world.WavePlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The whole contest of the proving grounds. The core stays active for good; this objective never completes, because
 * the framework core keeps one claim for the whole site and a tier must claim alone. It runs one tier at a time.
 * <p>
 * Order of play: a player rings the gong, which arms the lecterns for {@link #ARM_TICKS} ticks. A lectern then picks a
 * tier: the core records it, the main portcullis drops, wave 1 spawns, and the braziers or grates of the tier start.
 * Each wave starts {@link #WAVE_PAUSE_TICKS} ticks after the last one dies. Clearing the last wave fills the vault from
 * the tier's table and claims the tier for a day.
 * <p>
 * A run fails when a run player dies, or stays outside the bounds for {@link #LEAVE_TICKS} ticks. The wave mobs vanish,
 * the portcullis rises, and the tier can start again. No claim is set.
 * <p>
 * The core builds a new instance for every call, so all state lives in {@link #save()}. The claims of the tiers live
 * in {@link ProvingData}.
 */
public final class TierObjective implements Objective {
	public static final int TIERS = 3;
	public static final long ARM_TICKS = 6000L;
	public static final long WAVE_PAUSE_TICKS = 100L;
	public static final long LEAVE_TICKS = 600L;
	/** The box grows this far on the four sides and below. Above, it ends at the box. */
	public static final int BOUNDS_MARGIN = 2;
	/** A tier that has braziers: from this tier up. */
	public static final int BRAZIER_TIER = 2;
	/** A tier that has fire grates: from this tier up. */
	public static final int GRATE_TIER = 3;
	/** The first grate opens this long after the tier starts. */
	public static final long GRATE_FIRST_TICKS = 200L;
	public static final long GRATE_PERIOD_TICKS = 400L;
	public static final long GRATE_OPEN_TICKS = 60L;
	public static final int PORTCULLIS_HEIGHT = 3;

	/** What a try on the gong or a lectern did. */
	public enum Result {
		/** The core is not awake, or the objective is not this one. */
		SLEEPING,
		ARMED,
		/** A lectern was used before the gong rang, or after the arming ran out. */
		NOT_ARMED,
		/** The tier paid out less than a day ago. */
		CLAIMED,
		/** A tier is running already. */
		BUSY,
		STARTED
	}

	/** What the objective sees of a core: its level, its record and its markers. A handler builds one without a run. */
	public record Arena(ServerLevel level, BlockPos core, RuinData.Entry entry, Function<String, List<BlockPos>> markers) {
		public static Arena of(final SiteRun run) {
			ServerLevel level = run.level();
			return new Arena(level, run.core(), RuinData.get(level).entry(run.core()), run::markers);
		}

		/** An arena for a core block entity that holds an active record, or null. */
		public static @Nullable Arena of(final ServerLevel level, final RuinCoreBlockEntity core) {
			RuinData.Entry entry = RuinData.get(level).entry(core.getBlockPos());
			if (entry == null) {
				return null;
			}
			Map<String, List<BlockPos>> marks = core.markers();
			return new Arena(level, core.getBlockPos(), entry, name -> marks.getOrDefault(name, List.of()));
		}

		int alive() {
			return this.entry.roster().size();
		}

		List<BlockPos> markers(final String name) {
			return this.markers.apply(name);
		}
	}

	/** One player who runs the tier: the deaths they had at the start, and when they went outside the bounds. */
	private record Runner(UUID id, int deaths, long outside) {
	}

	private long armedUntil = -1L;
	private int tier;
	private int wave;
	private long nextWaveAt = -1L;
	private long grateNext = -1L;
	private long grateUntil = -1L;
	private List<Runner> runners = new ArrayList<>();

	/** The number of waves of a tier. A tier is 1 to 3. */
	public static int waveCount(final int tier) {
		return ProvingGroundsSite.waves(tier).waves().size();
	}

	/** The tier now running, or 0. */
	public int tier() {
		return this.tier;
	}

	/** The wave now running, counted from 0. */
	public int wave() {
		return this.wave;
	}

	public boolean running() {
		return this.tier > 0;
	}

	public boolean armed(final ServerLevel level) {
		return this.armedUntil >= 0 && RuinClock.now(level) < this.armedUntil;
	}

	public boolean grateOpen() {
		return this.grateUntil >= 0;
	}

	@Override
	public void start(final SiteRun run) {
		this.armedUntil = -1L;
		this.tier = 0;
		this.wave = 0;
		this.nextWaveAt = -1L;
		this.grateNext = -1L;
		this.grateUntil = -1L;
		this.runners = new ArrayList<>();
	}

	@Override
	public Progress tick(final SiteRun run) {
		tick(Arena.of(run));
		return Progress.RUNNING;
	}

	/** One check of the run. Also called after a roster mob dies, through the core. */
	public void tick(final Arena arena) {
		if (this.tier == 0) {
			return;
		}
		ServerLevel level = arena.level();
		long now = RuinClock.now(level);
		String broken = broken(arena, now);
		if (broken != null) {
			fail(arena, broken);
			return;
		}
		hazards(arena, now);
		if (arena.alive() > 0) {
			return;
		}
		if (this.nextWaveAt < 0) {
			Cheer.cheer(level, arena.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.RETIARIUS));
			if (this.wave + 1 >= waveCount(this.tier)) {
				complete(arena, now);
				return;
			}
			this.nextWaveAt = now + WAVE_PAUSE_TICKS;
		}
		if (now >= this.nextWaveAt) {
			this.wave++;
			this.nextWaveAt = -1L;
			spawnWave(arena);
		}
	}

	/** The rite of the gong. The lecterns light up. Needs an awake core. */
	public Result ring(final Arena arena) {
		if (this.tier > 0) {
			return Result.BUSY;
		}
		this.armedUntil = RuinClock.now(arena.level()) + ARM_TICKS;
		return Result.ARMED;
	}

	/** A lectern picks a tier. Starts the run when the gong rang, the tier is free and no tier runs. */
	public Result pick(final Arena arena, final ServerPlayer player, final int picked) {
		ServerLevel level = arena.level();
		long now = RuinClock.now(level);
		if (this.tier > 0) {
			return Result.BUSY;
		}
		if (!armed(level)) {
			return Result.NOT_ARMED;
		}
		if (ProvingData.get(level).claimed(arena.core(), picked, now, claimDelay())) {
			return Result.CLAIMED;
		}
		this.tier = picked;
		this.wave = 0;
		this.nextWaveAt = -1L;
		this.armedUntil = -1L;
		this.grateNext = picked >= GRATE_TIER ? now + GRATE_FIRST_TICKS : -1L;
		this.grateUntil = -1L;
		this.runners = new ArrayList<>();
		AABB bounds = bounds(arena);
		for (ServerPlayer near : level.players()) {
			if (near == player || (!near.isSpectator() && near.isAlive() && bounds != null && bounds.contains(near.position()))) {
				this.runners.add(new Runner(near.getUUID(), deathsOf(near), -1L));
			}
		}
		portcullis(arena, true);
		braziers(arena, picked >= BRAZIER_TIER);
		spawnWave(arena);
		StellaRuins.LOGGER.debug("[RUINS] proving grounds {} tier {} started by {}", arena.core().toShortString(), picked, player.getName().getString());
		return Result.STARTED;
	}

	/** The delay a tier's claim holds, in ticks. The config's reset_days entry for the site wins, as for any site. */
	static long claimDelay() {
		return RuinConfig.get().resetDays().getOrDefault(ProvingGroundsSite.ID, ProvingGroundsSite.RESET_DAYS) * Claim.DAY_TICKS;
	}

	private static int deathsOf(final ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
	}

	/** The box a run player must stay in: the site's box grown by two on the sides and below. */
	public static @Nullable AABB bounds(final Arena arena) {
		if (!(arena.level().getBlockEntity(arena.core()) instanceof RuinCoreBlockEntity core) || core.frame() == null) {
			return null;
		}
		RuinFrame frame = core.frame();
		var box = frame.box();
		return new AABB(box.minX() - BOUNDS_MARGIN, box.minY() - BOUNDS_MARGIN, box.minZ() - BOUNDS_MARGIN,
			box.maxX() + 1 + BOUNDS_MARGIN, box.maxY() + 1, box.maxZ() + 1 + BOUNDS_MARGIN);
	}

	/** Why the run is broken: a run player died, or one stayed outside too long. Null while it holds. */
	private @Nullable String broken(final Arena arena, final long now) {
		ServerLevel level = arena.level();
		AABB bounds = bounds(arena);
		List<Runner> next = new ArrayList<>();
		String reason = null;
		for (Runner runner : this.runners) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(runner.id());
			if (player != null && player.isSpectator()) {
				next.add(runner);
				continue;
			}
			if (player != null && (!player.isAlive() || deathsOf(player) > runner.deaths())) {
				reason = "death";
			}
			boolean inside = player != null && bounds != null && player.level() == level && bounds.contains(player.position());
			long outside = runner.outside();
			if (inside) {
				outside = -1L;
			} else if (outside < 0) {
				outside = now;
			} else if (now - outside >= LEAVE_TICKS && reason == null) {
				reason = "left";
			}
			next.add(new Runner(runner.id(), runner.deaths(), outside));
		}
		this.runners = next;
		return reason;
	}

	private void spawnWave(final Arena arena) {
		Roster roster = ProvingGroundsSite.waves(this.tier).waves().get(this.wave);
		Map<String, Integer> used = new HashMap<>();
		for (Roster.Entry entry : roster.entries()) {
			String kind = entry.kind().id();
			List<BlockPos> spots = arena.markers(ArenaPlan.SPAWN_PREFIX + kind);
			for (int i = 0; i < entry.count(); i++) {
				int n = used.merge(kind, 1, Integer::sum) - 1;
				Vec3 at = spots.isEmpty() ? around(arena, n) : atSpot(spots.get(n % spots.size()));
				Mob mob = entry.kind().spawn(arena.level(), at);
				if (mob != null) {
					arena.entry().addMob(mob.getUUID());
				}
			}
		}
		if (roster.entries().stream().anyMatch(entry -> entry.kind() == GroundsMobs.beast())) {
			hatch(arena);
		}
	}

	private static Vec3 atSpot(final BlockPos spot) {
		return new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	private static Vec3 around(final Arena arena, final int n) {
		double angle = n * Math.PI / 4.0;
		BlockPos core = arena.core();
		return new Vec3(core.getX() + 0.5 + Math.cos(angle) * 3.0, core.getY() + 1.0, core.getZ() + 0.5 + Math.sin(angle) * 3.0);
	}

	/** The hatch bursts with smoke when the Pit Beast arrives. */
	private static void hatch(final Arena arena) {
		for (BlockPos spot : arena.markers(ArenaPlan.SPAWN_PREFIX + GroundsMobs.PIT_BEAST)) {
			arena.level().sendParticles(ParticleTypes.LARGE_SMOKE, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 60, 1.5, 0.4, 0.6, 0.05);
			arena.level().playSound(null, spot, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
		}
	}

	private void complete(final Arena arena, final long now) {
		ServerLevel level = arena.level();
		int finished = this.tier;
		portcullis(arena, false);
		braziers(arena, false);
		grates(arena, false);
		ResourceKey<LootTable> table = ProvingGroundsSite.vaultTable(finished);
		int rolls = RuinConfig.get().vaultRolls(containerLevel(arena));
		int opened = 0;
		for (BlockPos vault : arena.markers(RuinMarkers.VAULT)) {
			if (level.getBlockEntity(vault) instanceof RuinVaultBlockEntity chest) {
				chest.setSealed(level, false);
				chest.fill(level, table, rolls);
				if (opened == 0 && finished == TIERS) {
					RuinTrophies.award(level, ProvingGroundsSite.site(), chest);
				}
				opened++;
			}
		}
		ProvingData.get(level).claim(arena.core(), finished, now);
		StellaRuins.LOGGER.debug("[RUINS] proving grounds {} tier {} complete rolls={} vaultsOpened={}", arena.core().toShortString(), finished, rolls, opened);
		for (Runner runner : this.runners) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(runner.id());
			if (player != null) {
				player.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.proving_grounds.won", "The crowd is on its feet. Tier " + finished + " is yours."));
			}
		}
		reset();
	}

	private static int containerLevel(final Arena arena) {
		return com.stella.ruins.world.RuinLevels.at(arena.level(), Vec3.atCenterOf(arena.core()));
	}

	private void fail(final Arena arena, final String reason) {
		ServerLevel level = arena.level();
		for (java.util.UUID id : arena.entry().roster()) {
			Entity mob = level.getEntity(id);
			if (mob != null) {
				mob.discard();
			}
		}
		arena.entry().clearRoster();
		AABB area = new AABB(arena.core()).inflate(ArenaPlan.SIZE);
		for (Mob stray : level.getEntitiesOfClass(Mob.class, area, GroundsMobs::isGroundsMob)) {
			stray.discard();
		}
		portcullis(arena, false);
		braziers(arena, false);
		grates(arena, false);
		StellaRuins.LOGGER.debug("[RUINS] proving grounds {} tier {} failed: {}", arena.core().toShortString(), this.tier, reason);
		for (Runner runner : this.runners) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(runner.id());
			if (player != null) {
				player.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.proving_grounds.lost", "The crowd boos. The tier is lost."));
			}
		}
		reset();
	}

	private void reset() {
		this.tier = 0;
		this.wave = 0;
		this.nextWaveAt = -1L;
		this.armedUntil = -1L;
		this.grateNext = -1L;
		this.grateUntil = -1L;
		this.runners = new ArrayList<>();
	}

	private void hazards(final Arena arena, final long now) {
		if (this.tier < GRATE_TIER) {
			return;
		}
		if (this.grateUntil >= 0 && now >= this.grateUntil) {
			grates(arena, false);
			this.grateUntil = -1L;
		}
		if (this.grateUntil < 0 && this.grateNext >= 0 && now >= this.grateNext) {
			grates(arena, true);
			this.grateUntil = now + GRATE_OPEN_TICKS;
			this.grateNext = now + GRATE_PERIOD_TICKS;
		}
	}

	private static void portcullis(final Arena arena, final boolean down) {
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		for (BlockPos base : arena.markers(ArenaPlan.PORTCULLIS)) {
			for (int i = 0; i < PORTCULLIS_HEIGHT; i++) {
				arena.level().setBlock(base.above(i), down ? bars : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static void braziers(final Arena arena, final boolean lit) {
		for (BlockPos spot : arena.markers(ArenaPlan.BRAZIER)) {
			BlockState state = arena.level().getBlockState(spot);
			if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) != lit) {
				arena.level().setBlock(spot, state.setValue(CampfireBlock.LIT, lit), Block.UPDATE_ALL);
			}
		}
	}

	/** Opens or shuts every fire grate. An open grate shows its fire, and a player who steps in burns. */
	private void grates(final Arena arena, final boolean open) {
		for (int g = 1; g <= ArenaPlan.GRATES; g++) {
			for (BlockPos spot : arena.markers(ArenaPlan.GRATE + g)) {
				BlockState door = arena.level().getBlockState(spot);
				if (door.is(Blocks.IRON_TRAPDOOR)) {
					arena.level().setBlock(spot, door.setValue(TrapDoorBlock.OPEN, open), Block.UPDATE_ALL);
				}
				arena.level().setBlock(spot.below(), open ? Blocks.FIRE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("armed_until", this.armedUntil);
		tag.putInt("tier", this.tier);
		tag.putInt("wave", this.wave);
		tag.putLong("next_wave_at", this.nextWaveAt);
		tag.putLong("grate_next", this.grateNext);
		tag.putLong("grate_until", this.grateUntil);
		tag.putInt("runners", this.runners.size());
		for (int i = 0; i < this.runners.size(); i++) {
			Runner runner = this.runners.get(i);
			CompoundTag entry = new CompoundTag();
			entry.putString("id", runner.id().toString());
			entry.putInt("deaths", runner.deaths());
			entry.putLong("outside", runner.outside());
			tag.put("runner" + i, entry);
		}
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.armedUntil = tag.getLongOr("armed_until", -1L);
		this.tier = tag.getIntOr("tier", 0);
		this.wave = tag.getIntOr("wave", 0);
		this.nextWaveAt = tag.getLongOr("next_wave_at", -1L);
		this.grateNext = tag.getLongOr("grate_next", -1L);
		this.grateUntil = tag.getLongOr("grate_until", -1L);
		this.runners = new ArrayList<>();
		int count = tag.getIntOr("runners", 0);
		for (int i = 0; i < count; i++) {
			CompoundTag entry = tag.getCompoundOrEmpty("runner" + i);
			this.runners.add(new Runner(UUID.fromString(entry.getStringOr("id", new UUID(0L, 0L).toString())), entry.getIntOr("deaths", 0), entry.getLongOr("outside", -1L)));
		}
	}

	/** The wave plan a tier runs, as the site registered it. */
	static WavePlan plan(final int tier) {
		return ProvingGroundsSite.waves(tier);
	}
}
