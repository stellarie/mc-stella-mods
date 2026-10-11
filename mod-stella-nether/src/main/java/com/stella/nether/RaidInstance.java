package com.stella.nether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The raid instances, one a mode: each one's state on the Overworld, its host, where each run lies, and the
 * boons it hands out. Each run lies {@link #RUN_STRIDE} blocks from the last, so a reset leaves the old
 * run's land, and everything dropped on it, behind for good. The normal raid lies on the north side of the
 * raid level (negative z) and the hard raid on the south side, so a position tells which raid it belongs to.
 */
public final class RaidInstance {
	public static final List<Holder<MobEffect>> PLAYER_BOONS = List.of(
		MobEffects.SPEED, MobEffects.HASTE, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.REGENERATION,
		MobEffects.RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.NIGHT_VISION, MobEffects.ABSORPTION,
		MobEffects.HEALTH_BOOST, MobEffects.SLOW_FALLING, MobEffects.LUCK
	);
	public static final int PLAYER_BOONS_EACH = 1;
	public static final List<Holder<MobEffect>> MOB_BOONS = List.of(
		MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE, MobEffects.REGENERATION,
		MobEffects.ABSORPTION, MobEffects.HEALTH_BOOST, MobEffects.FIRE_RESISTANCE
	);
	public static final int MOB_BOONS_EACH = 2;
	/**
	 * Blocks between one run's origin and the next. A site may shift up to {@link RaidSites#MAX_SHIFT} from its
	 * origin, so two runs' sites always lie at least this less twice that apart, far past any view distance.
	 */
	public static final int RUN_STRIDE = 8192;
	/** Runs in a row, and rows from the dividing line out, kept inside the nether's world border of 3.75 million blocks. */
	private static final int RUNS_PER_ROW = 800;
	/** The arrival hall's floor height at a run's origin. */
	public static final int ARRIVAL_Y = 64;
	/** How long the raid must stand empty before it resets. */
	public static final int EMPTY_TICKS = 100;
	/** Runs lie more than twice this apart; it was the reach of the old fortress-and-bastion midpoint rule. */
	public static final int STRUCTURE_REACH = 256;
	/** Thirty minutes: then every raid mob turns on the players. */
	public static final int ENRAGE_TICKS = 30 * 60 * 20;
	/** Speed II and Strength II. */
	public static final int ENRAGE_AMPLIFIER = 1;
	/** How far an enraged mob looks for a player to hunt, and how often it looks. */
	public static final double CHASE_RANGE = 64.0;
	public static final int CHASE_EVERY_TICKS = 20;
	private static final net.minecraft.resources.Identifier CHASE_RANGE_MODIFIER = StellaNether.id("enraged_follow_range");
	/** Every run's arrival lies within this many blocks (horizontal) of its fortress start. */
	public static final int FORTRESS_REACH = 64;
	/** A raid mob's lowest level in normal mode, and in hard mode; a better player raises it. */
	public static final int MIN_LEVEL_NORMAL = 35;
	public static final int MIN_LEVEL_HARD = 45;

	public static int minimumLevel(final RaidMode mode) {
		return mode == RaidMode.HARD ? MIN_LEVEL_HARD : MIN_LEVEL_NORMAL;
	}

	/** The level of a raid mob: its mode's minimum, or the best player's level when that is higher. */
	public static int mobLevel(final RaidMode mode, final java.util.Collection<? extends Player> players) {
		int best = minimumLevel(mode);
		for (Player player : players) {
			best = Math.max(best, player.experienceLevel);
		}
		return best;
	}

	/** The load phase the raid's own rules run in, after every other mod's. */
	private static final net.minecraft.resources.Identifier AFTER_OTHER_MODS = StellaNether.id("after_other_mods");
	/** The run whose level a raid enemy took, so a reloaded one keeps its level and health. */
	public static final AttachmentType<Integer> MOB_LEVELLED = AttachmentRegistry.createPersistent(StellaNether.id("raid_levelled"), Codec.INT);
	/** The run a mob was enraged for. */
	public static final AttachmentType<Integer> MOB_ENRAGED = AttachmentRegistry.createPersistent(StellaNether.id("raid_enraged"), Codec.INT);

	/** Each mode's raid state, kept on the Overworld. */
	private static final Map<RaidMode, AttachmentType<RaidState>> STATES = new EnumMap<>(RaidMode.class);

	static {
		for (RaidMode mode : RaidMode.values()) {
			STATES.put(mode, AttachmentRegistry.createPersistent(StellaNether.id("raid_state_" + mode.name().toLowerCase(Locale.ROOT)), RaidState.CODEC));
		}
	}

	/** The boons a player was handed on entering, and in which raid and run. */
	public record Boons(RaidMode mode, int run, List<Holder<MobEffect>> effects) {
		public static final Codec<Boons> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			RaidMode.CODEC.fieldOf("mode").forGetter(Boons::mode),
			Codec.INT.fieldOf("run").forGetter(Boons::run),
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().fieldOf("effects").forGetter(Boons::effects)
		).apply(instance, Boons::new));
	}

	public static final AttachmentType<Boons> PLAYER_BOONS_HELD = AttachmentRegistry.createPersistent(StellaNether.id("raid_boons"), Boons.CODEC);
	/** The run whose mode a mob was last boosted for, so a reloaded mob is not boosted twice. */
	public static final AttachmentType<Integer> MOB_BOOSTED = AttachmentRegistry.createPersistent(StellaNether.id("raid_boosted"), Codec.INT);

	/** Each run's site, by world seed and run; the search is deterministic, so a restart finds the same one. */
	private static final Map<String, RaidSites.Site> SITES = new ConcurrentHashMap<>();
	/** Ticks each open raid has stood empty; kept in memory, so a restart with nobody inside resets it soon after. */
	private static final int[] EMPTY_FOR = new int[RaidMode.values().length];

	private RaidInstance() {
	}

	/** Which raid a position in the raid level lies in. */
	public static RaidMode modeAt(final BlockPos at) {
		return at.getZ() < 0 ? RaidMode.NORMAL : RaidMode.HARD;
	}

	/** The players standing in one mode's side of the raid level. */
	public static List<ServerPlayer> playersOf(final ServerLevel raid, final RaidMode mode) {
		return raid.players().stream().filter(player -> modeAt(player.blockPosition()) == mode).toList();
	}

	public static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			boolean from = origin.dimension().equals(RaidTravel.RAID);
			boolean to = destination.dimension().equals(RaidTravel.RAID);
			if (to && !from) {
				grantBoons(player);
			} else if (from && !to) {
				takeBoons(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RaidInstance::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		// After every other mod's load handler, so the raid's level replaces the one the mobs mod rolled.
		ServerEntityEvents.ENTITY_LOAD.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, AFTER_OTHER_MODS);
		ServerEntityEvents.ENTITY_LOAD.register(AFTER_OTHER_MODS, (entity, level) -> {
			// The Sovereign keeps the threat level its rise set.
			if (entity instanceof Mob mob && level.dimension().equals(RaidTravel.RAID) && !BlazeSovereign.isSovereign(mob)) {
				if (mob instanceof Enemy) {
					levelMob(level, mob);
				} else {
					clearThreat(mob);
				}
			}
			if (entity instanceof Mob mob && mob instanceof Enemy && level.dimension().equals(RaidTravel.RAID)) {
				boostMob(level, mob);
				RaidState state = stateAt(level, mob.blockPosition());
				if (state.active() && state.elapsed() >= ENRAGE_TICKS) {
					enrage(state.run(), mob);
				}
			}
		});
	}

	public static RaidState state(final MinecraftServer server, final RaidMode mode) {
		RaidState state = ((AttachmentTarget) server.overworld()).getAttached(STATES.get(mode));
		return state == null ? RaidState.empty(mode) : state;
	}

	/** The state of the raid a spot in the raid level lies in. */
	public static RaidState stateAt(final ServerLevel level, final BlockPos at) {
		return state(level.getServer(), modeAt(at));
	}

	static void set(final MinecraftServer server, final RaidState state) {
		((AttachmentTarget) server.overworld()).setAttached(STATES.get(state.mode()), state);
	}

	/** What a raid's window shows: the level its mobs take now, or the level they would take if it opened. */
	public static int displayLevel(final MinecraftServer server, final RaidMode mode) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (!state(server, mode).active() || raid == null) {
			return minimumLevel(mode);
		}
		return mobLevel(mode, playersOf(raid, mode));
	}

	/**
	 * A player who logs in inside the raid nether is sent back to the portal they came in by, whether their run
	 * still lives or has closed. While it lives, they may walk back in through that portal and join it again.
	 */
	public static void onJoin(final ServerPlayer player) {
		if (!player.level().dimension().equals(RaidTravel.RAID)) {
			return;
		}
		RaidState state = stateAt((ServerLevel) player.level(), player.blockPosition());
		Boons held = player.getAttached(PLAYER_BOONS_HELD);
		boolean live = state.active() && held != null && held.mode() == state.mode() && held.run() == state.run();
		BehaviorLog.info("[NETHER] player={} reconnected in {} run={} now={} live={}; sent to their guardian", player.getScoreboardName(),
			state.mode(), held == null ? null : held.run(), state.run(), live);
		player.teleport(RaidTravel.wayBack((ServerLevel) player.level(), player));
	}

	static void tick(final MinecraftServer server) {
		for (RaidMode mode : RaidMode.values()) {
			tick(server, mode);
		}
	}

	/** Counts one open raid's time, and resets it once it has stood empty for {@link #EMPTY_TICKS}. */
	static void tick(final MinecraftServer server, final RaidMode mode) {
		RaidState state = state(server, mode);
		if (!state.active()) {
			EMPTY_FOR[mode.ordinal()] = 0;
			return;
		}
		long elapsed = state.elapsed() + 1;
		state = state.withElapsed(elapsed);
		set(server, state);
		BlazeSovereign.tick(server, state);
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		if (raid != null && elapsed >= ENRAGE_TICKS) {
			if (elapsed == ENRAGE_TICKS) {
				BehaviorLog.info("[NETHER] raid enraged run={}", state.run());
				playersOf(raid, mode).forEach(player -> player.sendSystemMessage(
					Component.translatable("message.mod-stella-nether.enraged").withStyle(ChatFormatting.DARK_RED)));
			}
			if (elapsed == ENRAGE_TICKS || elapsed % CHASE_EVERY_TICKS == 0) {
				for (Entity entity : snapshot(raid)) {
					if (entity instanceof Mob mob && mob instanceof Enemy && mob.isAlive() && modeAt(mob.blockPosition()) == mode) {
						enrage(state.run(), mob);
						chase(raid, mob);
					}
				}
			}
		}
		if (raid != null && !playersOf(raid, mode).isEmpty()) {
			EMPTY_FOR[mode.ordinal()] = 0;
			return;
		}
		if (++EMPTY_FOR[mode.ordinal()] < EMPTY_TICKS) {
			return;
		}
		EMPTY_FOR[mode.ordinal()] = 0;
		int lost = 0;
		if (raid != null) {
			// A snapshot: discarding while walking the level's entity map skips entries, or crashes the tick.
			for (Entity entity : snapshot(raid)) {
				if (entity instanceof ItemEntity item && modeAt(item.blockPosition()) == mode) {
					item.discard();
					lost++;
				}
			}
		}
		reset(server, mode, "empty lost_items=" + lost);
	}

	/** Closes one mode's running raid and moves its next run to fresh land. */
	public static void reset(final MinecraftServer server, final RaidMode mode, final String reason) {
		RaidState old = state(server, mode);
		set(server, new RaidState(old.run() + 1, false, Optional.empty(), mode, 0L, false));
		BlazeSovereign.closeBar(mode);
		BlazeSovereign.forget(mode);
		BehaviorLog.info("[NETHER] raid reset mode={} run={} next={} reason={}", mode, old.run(), old.run() + 1, reason);
	}

	/** Closes both raids. */
	public static void resetAll(final MinecraftServer server, final String reason) {
		for (RaidMode mode : RaidMode.values()) {
			reset(server, mode, reason);
		}
	}

	/** A player crossing in: into a mode with no raid they become its host; otherwise they join the one that runs. */
	public static RaidState enter(final ServerPlayer player, final RaidMode mode) {
		MinecraftServer server = player.level().getServer();
		RaidState state = state(server, mode);
		if (!state.active()) {
			state = new RaidState(state.run(), true, Optional.of(player.getUUID()), mode, 0L, false);
			set(server, state);
			BehaviorLog.info("[NETHER] raid opened mode={} run={} host={}", mode, state.run(), player.getScoreboardName());
		} else {
			BehaviorLog.info("[NETHER] raid joined mode={} run={} player={}", mode, state.run(), player.getScoreboardName());
		}
		return state;
	}

	/** A mode's run origin: the normal raid's runs lie north of the dividing line, the hard raid's south of it. */
	public static BlockPos origin(final RaidMode mode, final int run) {
		int row = run / RUNS_PER_ROW + 1;
		return new BlockPos((run % RUNS_PER_ROW - RUNS_PER_ROW / 2) * RUN_STRIDE, 0, (mode == RaidMode.HARD ? row : -row) * RUN_STRIDE);
	}

	/** Where a player lands in a mode's current run: on the hall's floor, facing its portal out. */
	public static BlockPos arrival(final MinecraftServer server, final RaidMode mode) {
		BlockPos site = site(server, mode).arrival();
		return new BlockPos(site.getX(), ARRIVAL_Y, site.getZ() + 1);
	}

	/** A mode's current run site: its arrival, and the fortress its Sovereign rises from. */
	public static RaidSites.Site site(final MinecraftServer server, final RaidMode mode) {
		ServerLevel raid = server.getLevel(RaidTravel.RAID);
		int run = state(server, mode).run();
		if (raid == null) {
			return new RaidSites.Site(origin(mode, run), origin(mode, run));
		}
		return SITES.computeIfAbsent(raid.getSeed() + ":" + mode + ":" + run, key -> RaidSites.site(raid, origin(mode, run)));
	}

	/** The bottom block of a mode's current portal out. */
	public static BlockPos exitPortal(final MinecraftServer server, final RaidMode mode) {
		return arrival(server, mode).offset(0, 1, -5);
	}

	/** Distinct effects drawn at random from a pool. */
	public static List<Holder<MobEffect>> draw(final List<Holder<MobEffect>> pool, final int count, final RandomSource random) {
		List<Holder<MobEffect>> left = new ArrayList<>(pool);
		List<Holder<MobEffect>> drawn = new ArrayList<>();
		for (int i = 0; i < count && !left.isEmpty(); i++) {
			drawn.add(left.remove(random.nextInt(left.size())));
		}
		return drawn;
	}

	private static MobEffectInstance lasting(final Holder<MobEffect> effect) {
		return new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, true);
	}

	static void grantBoons(final ServerPlayer player) {
		takeBoons(player);
		RaidState state = stateAt((ServerLevel) player.level(), player.blockPosition());
		List<Holder<MobEffect>> boons = draw(PLAYER_BOONS, PLAYER_BOONS_EACH, player.getRandom());
		boons.forEach(effect -> player.addEffect(lasting(effect)));
		player.setAttached(PLAYER_BOONS_HELD, new Boons(state.mode(), state.run(), boons));
		BehaviorLog.info("[NETHER] player={} boons={}", player.getScoreboardName(), boons);
	}

	/** Takes back only a lasting effect, so a potion of the same kind drunk outside is left alone. */
	static void takeBoons(final ServerPlayer player) {
		Boons held = player.removeAttached(PLAYER_BOONS_HELD);
		if (held == null) {
			return;
		}
		for (Holder<MobEffect> effect : held.effects()) {
			MobEffectInstance active = player.getEffect(effect);
			if (active != null && active.isInfiniteDuration()) {
				player.removeEffect(effect);
			}
		}
	}

	private static List<Entity> snapshot(final ServerLevel level) {
		List<Entity> entities = new ArrayList<>();
		level.getAllEntities().forEach(entities::add);
		return entities;
	}

	/**
	 * The raid scales its own mobs, so no threat level reaches them: whatever the mobs mod rolled is set to none,
	 * and the health and damage it scaled by it come off. Runs on every load, after the mobs mod's own.
	 */
	static void clearThreat(final Mob mob) {
		mob.setAttached(ThreatState.TYPE, new ThreatState(0, 0));
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			health.removeModifier(SpawnScaling.HEALTH_ID);
			health.removeModifier(com.stella.mobs.behavior.Bosses.LEVEL_HEALTH_ID);
		}
		if (damage != null) {
			damage.removeModifier(SpawnScaling.DAMAGE_ID);
			damage.removeModifier(com.stella.mobs.behavior.Bosses.LEVEL_DAMAGE_ID);
		}
		// A hardened mob's extra health was sized on the threat's multiplier; it is sized again without it.
		com.stella.mobs.behavior.Traits.refreshScaling(mob);
		if (health != null && mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	/** Endless Speed and Strength, and a long reach for the hunt; once per run. */
	static void enrage(final int run, final Mob mob) {
		Integer enraged = mob.getAttached(MOB_ENRAGED);
		if (enraged != null && enraged == run) {
			return;
		}
		mob.setAttached(MOB_ENRAGED, run);
		mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, ENRAGE_AMPLIFIER, false, true));
		mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, ENRAGE_AMPLIFIER, false, true));
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && !range.hasModifier(CHASE_RANGE_MODIFIER)) {
			range.addPermanentModifier(new AttributeModifier(CHASE_RANGE_MODIFIER, CHASE_RANGE, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** An enraged mob with no player to hunt takes the nearest one in reach; a neutral one stays angry at them. */
	static void chase(final ServerLevel raid, final Mob mob) {
		if (mob.getTarget() instanceof Player current && current.isAlive() && !current.isSpectator() && !current.isCreative()) {
			return;
		}
		Player nearest = raid.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), CHASE_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		if (nearest == null) {
			return;
		}
		mob.setTarget(nearest);
		if (mob instanceof NeutralMob neutral) {
			neutral.setPersistentAngerTarget(EntityReference.of(nearest));
			neutral.startPersistentAngerTimer();
		}
	}

	/**
	 * A raid enemy takes the raid's level once, at its first load: its mode's minimum or the best player's level.
	 * The mobs mod then scales its health and hits by that level, as it does anywhere else. A reload keeps it.
	 */
	static void levelMob(final ServerLevel level, final Mob mob) {
		RaidState state = stateAt(level, mob.blockPosition());
		Integer levelled = mob.getAttached(MOB_LEVELLED);
		if (levelled != null && levelled == state.run()) {
			return;
		}
		if (mob.hasAttached(SovereignSpecials.BROOD_MARK)) {
			// The tag only kept rolls off while it loaded.
			mob.removeTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
		}
		if (mob.entityTags().contains(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG)) {
			return;
		}
		int raidLevel = mobLevel(state.mode(), playersOf(level, state.mode()));
		mob.setAttached(ThreatState.TYPE, new ThreatState(raidLevel, 0));
		SpawnScaling.apply(mob);
		mob.setAttached(MOB_LEVELLED, state.run());
	}


	static void boostMob(final ServerLevel level, final Mob mob) {
		RaidState state = stateAt(level, mob.blockPosition());
		Integer boosted = mob.getAttached(MOB_BOOSTED);
		if (!state.active() || state.mode() != RaidMode.HARD || (boosted != null && boosted == state.run())) {
			return;
		}
		mob.setAttached(MOB_BOOSTED, state.run());
		// Undead refuse regeneration, so only effects this mob can hold are drawn.
		List<Holder<MobEffect>> pool = MOB_BOONS.stream().filter(effect -> mob.canBeAffected(lasting(effect))).toList();
		draw(pool, MOB_BOONS_EACH, mob.getRandom()).forEach(effect -> mob.addEffect(lasting(effect)));
	}
}
