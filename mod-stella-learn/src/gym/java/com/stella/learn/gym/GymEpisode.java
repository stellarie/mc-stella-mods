package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import com.stella.learn.gym.brains.Brains;
import com.stella.learn.model.ModelDriver;
import com.stella.mobs.StellaMobs;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * One fight: a bow skeleton that the model drives against a scripted bot, in one arena, until one dies or time runs out.
 * The trainer starts each episode with a Reset frame that names the seed, the brain, and the stage. The episode then
 * waits for the archer's goal to make its first decision, reports it as a start, and reports each decision after it
 * until the fight ends.
 *
 * Reward: the bot dying is a win (+1), the mob dying is a loss (-1), and a timeout is half the difference of the health
 * fractions. The damage difference of each decision travels apart as `shaping`, so the trainer sets its scale.
 */
public final class GymEpisode {
	public static final String MOB_DIED = "mobDied";
	public static final String BOT_DIED = "botDied";
	public static final String TIMEOUT = "timeout";
	public static final String ESCAPED = "escaped";
	public static final int START_TIMEOUT_TICKS = 200;
	public static final double CREEPER_SHARE = 0.10;
	/** Terminal rewards: a kill of the bot pays most, a loss costs less, and running out of time pays nothing. */
	private static final float WIN_REWARD = 5.0F;
	private static final float LOSS_REWARD = -2.0F;
	/** Shaping per decision: damage dealt to the bot, and a small pay for a decision in which the archer was not hit. */
	private static final double DAMAGE_REWARD_PER_HP = 0.005;
	private static final double UNHIT_REWARD = 0.0002;
	private static final int START_RETRIES = 3;
	/** Ticks between two sweeps of the archer's arrows that lie still. */
	private static final int SWEEP_TICKS = 20;
	private static final double STILL_SPEED_SQR = 1.0E-4;

	/** Where the episode is in its life. */
	public enum State {
		IDLE,
		STARTING,
		LIVE
	}

	/** What one step reports about the episode. */
	public record Frame(int arena, int flags, float reward, float shaping, float[] observation) {
	}

	/** Archers of running episodes by id, so an arrow in the world counts for its shooter's episode. */
	private static final Map<UUID, GymEpisode> SHOOTERS = new ConcurrentHashMap<>();

	static {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() != null) {
				GymEpisode episode = SHOOTERS.get(arrow.getOwner().getUUID());
				if (episode != null) {
					episode.arrows++;
					double power = arrow.getDeltaMovement().length() / 1.6;
					episode.powerBuckets[power < 0.25 ? 0 : power < 0.5 ? 1 : power < 0.75 ? 2 : 3]++;
				}
			}
		});
	}

	private final Arena arena;
	private final GymConfig config;
	private final Bot bot;
	private final BotInput.Builder builder = new BotInput.Builder();
	private final int observationSize = com.stella.learn.model.Contract.archer().observationSize();
	private State state = State.IDLE;
	private Mob mob;
	private Mob creeper;
	private GymSource source;
	private BotBrain brain;
	private BotView view;
	private Skill skill;
	private Body body;
	private Body.Range bodyRange = Body.Range.FULL;
	private long seed;
	private int brainIndex;
	private int stage;
	private int episodes;
	private int ticks;
	private int startWait;
	private int startAttempts;
	private int arrows;
	private final int[] powerBuckets = new int[4];
	private int tickLimit;
	private int sweepClock;
	private double shaping;
	private double dealt;
	private double unhit;
	private float noRegenHealth = Float.MAX_VALUE;
	private double taken;
	private float lastMobHealth;
	private float lastBotHealth;
	private String endReason;
	private String pendingError;
	private boolean startFlag;
	private RuntimeException brainFault;
	private Set<String> activeBrains = new LinkedHashSet<>();

	public GymEpisode(final Arena arena, final GymConfig config) {
		this.arena = arena;
		this.config = config;
		Vec3 spot = arena.randomSpot(RandomSource.create(config.seed() + arena.index()), 3.0);
		this.bot = Bot.spawn(arena.level(), "gym-bot-" + arena.index(), spot, 0.0F, arena.index(), config.sampleTicks());
		this.bot.setOpponent(() -> mob);
		this.bot.setBrain(this::botTurn);
	}

	public Arena arena() {
		return arena;
	}

	public Bot bot() {
		return bot;
	}

	public Mob mob() {
		return mob;
	}

	public State state() {
		return state;
	}

	public long seed() {
		return seed;
	}

	public int brainIndex() {
		return brainIndex;
	}

	public int stage() {
		return stage;
	}

	public int ticks() {
		return ticks;
	}

	public BotBrain brain() {
		return brain;
	}

	public Body body() {
		return body;
	}

	public Skill skill() {
		return skill;
	}

	public String endReason() {
		return endReason;
	}

	public boolean ended() {
		return endReason != null;
	}

	/** The names of the brains that have run so far; a meta brain adds the styles it handed over to. */
	public Set<String> activeBrains() {
		return activeBrains;
	}

	// -- start ------------------------------------------------------------------------------------------------------

	/** Resets the arena to the layout of `newSeed` and puts a fresh archer and a revived bot in it. */
	public void start(final long newSeed, final int brainChoice, final int stageChoice) {
		start(newSeed, brainChoice, stageChoice, 0);
	}

	/** `limit` is the episode length in ticks; 0 keeps the server's own. */
	public void start(final long newSeed, final int brainChoice, final int stageChoice, final int limit) {
		start(newSeed, brainChoice, stageChoice, limit, Body.Range.FULL);
	}

	public void start(final long newSeed, final int brainChoice, final int stageChoice, final int limit, final Body.Range range) {
		bodyRange = range;
		startAttempts = 0;
		tickLimit = limit > 0 ? limit : config.episodeTicks();
		launch(newSeed, brainChoice, stageChoice);
	}

	private void launch(final long newSeed, final int brainChoice, final int stageChoice) {
		finish();
		seed = newSeed;
		stage = stageChoice < 0 ? config.stage() : stageChoice;
		episodes++;
		ticks = 0;
		startWait = 0;
		shaping = 0.0;
		dealt = 0.0;
		taken = 0.0;
		unhit = 0.0;
		noRegenHealth = Float.MAX_VALUE;
		arrows = 0;
		java.util.Arrays.fill(powerBuckets, 0);
		endReason = null;
		pendingError = null;
		brainFault = null;
		startFlag = false;
		activeBrains = new LinkedHashSet<>();
		Terrain terrain = Terrain.generate(newSeed, stage);
		arena.reset(terrain);
		RandomSource random = RandomSource.create(newSeed * 31L);
		skill = Skill.draw(random, stage);
		int index = brainChoice;
		if (index < 0 || index >= Brains.COUNT) {
			if (brainChoice >= Brains.COUNT) {
				GymLog.info(GymLog.EPISODE, "BAD_RESET", "arena=" + arena.index() + " brain=" + brainChoice + " reason=" + GymLog.BAD_RESET);
			}

			index = random.nextInt(Brains.COUNT);
		}

		brainIndex = index;
		brain = Brains.create(index, random);
		Vec3 mobSpot = center(terrain.mobSpawn());
		Vec3 botSpot = center(terrain.botSpawn());
		mob = spawnArcher(mobSpot, random);
		if (mob == null) {
			GymLog.info(GymLog.EPISODE, "START_FAILED", "arena=" + arena.index() + " seed=" + newSeed + " reason=" + GymLog.SPAWN_FAILED + " at=" + Bot.fmt(mobSpot));
			pendingError = GymLog.SPAWN_FAILED;
			state = State.STARTING;
			return;
		}

		SHOOTERS.put(mob.getUUID(), this);
		bot.reset(botSpot, Bot.yawTo(botSpot, mobSpot));
		body = Body.draw(newSeed, bodyRange);
		BotLoadout.equip(bot, skill.gear(), body.protection());
		body.apply(bot.player());
		ProfileNoise.apply(bot.player(), random, stage == Terrain.C, arena.index(), newSeed);
		if (stage == Terrain.C && random.nextDouble() < CREEPER_SHARE) {
			spawnCreeper(terrain, random);
		}

		view = new BotView(bot, arena, skill, random);
		lastMobHealth = mob.getHealth();
		lastBotHealth = bot.player().getHealth();
		mob.setTarget(bot.player());
		state = State.STARTING;
		startFlag = true;
		GymLog.info(GymLog.BOT, "BRAIN", "arena=" + arena.index() + " seed=" + newSeed + " episode=" + episodes + " brain=" + Brains.name(index)
			+ " index=" + index + " stage=" + GymConfig.stageName(stage) + " " + skill.detail() + " " + body.detail() + " " + LearnLog.who(mob) + " " + bot.who());
		GymLog.info(GymLog.EPISODE, "START", "arena=" + arena.index() + " seed=" + newSeed + " episode=" + episodes + " brain=" + Brains.name(index)
			+ " stage=" + GymConfig.stageName(stage) + " " + LearnLog.who(mob) + " " + bot.who() + " mobAt=" + Bot.fmt(mobSpot) + " botAt=" + Bot.fmt(botSpot)
			+ " features=" + terrain.features() + " baseline=" + config.baseline());
	}

	private Vec3 center(final BlockPos cell) {
		BlockPos origin = arena.origin();
		return new Vec3(origin.getX() + cell.getX() + 0.5, origin.getY() + cell.getY(), origin.getZ() + cell.getZ() + 0.5);
	}

	private Mob spawnArcher(final Vec3 at, final RandomSource random) {
		Mob archer = EntityTypes.SKELETON.create(arena.level(), EntitySpawnReason.NATURAL);
		if (archer == null) {
			return null;
		}

		archer.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		archer.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		archer.setPersistenceRequired();
		archer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		source = null;
		if (!config.baseline()) {
			source = new GymSource();
			ModelDriver.setActionSource(archer, source);
		}

		arena.level().addFreshEntity(archer);
		return archer;
	}

	private void spawnCreeper(final Terrain terrain, final RandomSource random) {
		var spots = terrain.spots();
		if (spots.isEmpty()) {
			return;
		}

		BlockPos cell = spots.get(random.nextInt(spots.size()));
		Vec3 at = center(cell);
		Mob boom = EntityTypes.CREEPER.create(arena.level(), EntitySpawnReason.NATURAL);
		if (boom == null) {
			return;
		}

		boom.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		boom.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		boom.setPersistenceRequired();
		arena.level().addFreshEntity(boom);
		boom.setTarget(bot.player());
		creeper = boom;
		GymLog.info(GymLog.EPISODE, "CREEPER", "arena=" + arena.index() + " seed=" + seed + " " + LearnLog.who(boom) + " at=" + Bot.fmt(at));
	}

	/** Lets a test run the bot's brain against a mob that no goal drives. */
	public void forceLive() {
		state = State.LIVE;
	}

	// -- the bot's turn ---------------------------------------------------------------------------------------------

	private BotInput botTurn(final Bot b) {
		float yaw = b.player().getYRot();
		float pitch = b.player().getXRot();
		if (state != State.LIVE || brain == null || brainFault != null) {
			return BotInput.IDLE.facing(yaw, pitch);
		}

		try {
			view.update(mob);
			builder.clear(yaw, pitch, b.player().getInventory().getSelectedSlot());
			brain.tick(view, builder);
			brain.active(activeBrains);
			BotInput input = builder.build();
			if (view.tick() % config.sampleTicks() == 0) {
				GymLog.debug(GymLog.BOT, "ACTION", "arena=" + arena.index() + " brain=" + Brains.name(brainIndex) + " tick=" + view.tick() + " " + input);
			}

			return input;
		} catch (RuntimeException fault) {
			brainFault = fault;
			GymLog.info(GymLog.BOT, "ERROR", "arena=" + arena.index() + " seed=" + seed + " brain=" + Brains.name(brainIndex) + " reason=" + GymLog.BRAIN_ERROR
				+ " error=" + fault);
			com.stella.learn.StellaLearn.LOGGER.error("The brain {} failed", Brains.name(brainIndex), fault);
			return BotInput.IDLE.facing(yaw, pitch);
		}
	}

	/** Runs once a server tick before the mobs move: keeps the archer aimed at its enemy and times out a slow start. */
	public void tickHeld() {
		if (state == State.IDLE) {
			return;
		}

		if (mob != null && mob.isAlive() && bot.player().isAlive() && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
			mob.setTarget(bot.player());
		}

		if (creeper != null && creeper.isAlive() && bot.player().isAlive() && creeper.getTarget() == null) {
			creeper.setTarget(bot.player());
		}

		if (state == State.STARTING) {
			startWait++;
		} else if (state == State.LIVE && stage == Terrain.A && bot.player().isAlive()) {
			// Stage A bots do not heal: a health rise since the last tick is undone.
			float health = bot.player().getHealth();
			if (health > noRegenHealth) {
				bot.player().setHealth(noRegenHealth);
			} else {
				noRegenHealth = health;
			}
		}

		if (mob != null && ++sweepClock % SWEEP_TICKS == 0) {
			sweepArrows();
		}
	}

	/**
	 * An arrow stuck in the ground or a wall does nothing more, but it stays a minute and ticks all the while. A policy
	 * that looses one every few ticks would leave hundreds in each arena, so the still ones go.
	 */
	private void sweepArrows() {
		for (AbstractArrow arrow : arena.level().getEntitiesOfClass(AbstractArrow.class, arena.bounds().inflate(1.0),
			a -> a.getOwner() == mob && a.getDeltaMovement().lengthSqr() < STILL_SPEED_SQR && a.tickCount > 2)) {
			arrow.discard();
		}
	}

	// -- frames -----------------------------------------------------------------------------------------------------

	/**
	 * Advances the episode by one decision and returns what to report, or null when there is nothing to report. The
	 * first report of an episode carries the start flag.
	 */
	public Frame poll(final int decisionTicks) {
		switch (state) {
			case IDLE -> {
				return null;
			}
			case STARTING -> {
				return pollStarting();
			}
			default -> {
				return pollLive(decisionTicks);
			}
		}
	}

	private Frame pollStarting() {
		if (pendingError != null) {
			return errorFrame(pendingError, Bridge.FLAG_START);
		}

		// With no model, nothing decides, so the fight is live at once.
		if (config.baseline() || source != null && source.captured()) {
			state = State.LIVE;
			ticks = 0;
			return frame(Bridge.FLAG_START, 0.0F, 0.0F);
		}

		if (startWait <= START_TIMEOUT_TICKS) {
			return null;
		}

		GymLog.info(GymLog.EPISODE, "START_FAILED", "arena=" + arena.index() + " seed=" + seed + " reason=" + GymLog.NOT_DRIVEN + " waited=" + startWait
			+ " attempt=" + startAttempts + " " + LearnLog.who(mob) + " status=" + ModelDriver.status() + " driven=" + ModelDriver.isDriven(mob) + " " + diagnose());
		if (++startAttempts < START_RETRIES) {
			launch(seed, brainIndex, stage);
			return null;
		}

		return errorFrame(GymLog.NOT_DRIVEN, Bridge.FLAG_START);
	}

	private Frame pollLive(final int decisionTicks) {
		ticks += decisionTicks;
		float mobHealth = mob.isAlive() ? mob.getHealth() : 0.0F;
		float botHealth = bot.player().isAlive() ? bot.player().getHealth() : 0.0F;
		double maxHealth = bot.player().getMaxHealth();
		double dealtNow = Math.max(0.0, lastBotHealth - botHealth);
		double takenNow = Math.max(0.0, lastMobHealth - mobHealth);
		dealt += dealtNow;
		taken += takenNow;
		// A decision without a hit on the archer pays a little; the damage the archer takes has no term of its own.
		double bonus = takenNow == 0.0 ? UNHIT_REWARD : 0.0;
		unhit += bonus;
		double step = dealtNow * DAMAGE_REWARD_PER_HP + bonus;
		lastMobHealth = mobHealth;
		lastBotHealth = botHealth;
		int flags = 0;
		float reward = 0.0F;
		if (brainFault != null) {
			endReason = GymLog.BRAIN_ERROR;
			flags = Bridge.FLAG_TERMINAL | Bridge.FLAG_TRUNCATED | Bridge.FLAG_ERROR;
		} else if (botHealth <= 0.0F) {
			endReason = BOT_DIED;
			reward = WIN_REWARD;
			flags = Bridge.FLAG_TERMINAL | Bridge.FLAG_WIN;
		} else if (mobHealth <= 0.0F) {
			endReason = MOB_DIED;
			reward = LOSS_REWARD;
			flags = Bridge.FLAG_TERMINAL;
		} else if (!arena.contains(mob.position())) {
			endReason = ESCAPED;
			flags = Bridge.FLAG_TERMINAL | Bridge.FLAG_TRUNCATED | Bridge.FLAG_ERROR;
		} else if (ticks >= episodeLimit()) {
			endReason = TIMEOUT;
			flags = Bridge.FLAG_TERMINAL | Bridge.FLAG_TRUNCATED;
		}

		Frame frame = frame(flags, reward, (float) step);
		if (endReason != null) {
			logEnd(reward, mobHealth, botHealth);
			EvalMode.result(config, this);
			finish();
		}

		return frame;
	}

	private int episodeLimit() {
		return tickLimit;
	}

	private Frame frame(final int flags, final float reward, final float shapingStep) {
		float[] observation = new float[observationSize];
		if (source != null) {
			System.arraycopy(source.observation(), 0, observation, 0, observationSize);
		}

		return new Frame(arena.index(), flags, reward, shapingStep, observation);
	}

	private Frame errorFrame(final String reason, final int extra) {
		endReason = reason;
		Frame frame = new Frame(arena.index(), extra | Bridge.FLAG_TERMINAL | Bridge.FLAG_TRUNCATED | Bridge.FLAG_ERROR, 0.0F, 0.0F, new float[observationSize]);
		logEnd(0.0F, lastMobHealth, lastBotHealth);
		EvalMode.result(config, this);
		finish();
		return frame;
	}

	private void logEnd(final float reward, final float mobHealth, final float botHealth) {
		GymLog.info(GymLog.EPISODE, "END", "arena=" + arena.index() + " seed=" + seed + " episode=" + episodes + " brain=" + (brain == null ? "none" : Brains.name(brainIndex))
			+ " stage=" + GymConfig.stageName(stage) + " ticks=" + ticks + " limit=" + tickLimit + " reason=" + endReason + " terminal=" + LearnLog.num(reward)
			+ " damageDealt=" + LearnLog.num(dealt) + " damageTaken=" + LearnLog.num(taken)
			+ " damageReward=" + LearnLog.num(dealt * DAMAGE_REWARD_PER_HP) + " unhitReward=" + LearnLog.num(unhit) + " mobHealth=" + LearnLog.num(mobHealth)
			+ " botHealth=" + LearnLog.num(botHealth) + " arrows=" + arrows + " arrowPower=" + java.util.Arrays.toString(powerBuckets) + " decisions=" + (source == null ? 0 : source.decisions()) + " bot=" + bot.stats().summary());
	}

	/** Takes the archer and the bot out of play. The arena keeps its blocks until the next start. */
	private void finish() {
		if (mob != null) {
			SHOOTERS.remove(mob.getUUID());
			ModelDriver.clearActionSource(mob);
			mob.discard();
			mob = null;
		}

		if (creeper != null) {
			creeper.discard();
			creeper = null;
		}

		if (state != State.IDLE) {
			bot.setInput(BotInput.IDLE);
		}

		source = null;
		state = State.IDLE;
	}

	/** What decides whether the archer's goal can start: its target, both healths, its goals, and its chunk. */
	private String diagnose() {
		var target = mob.getTarget();
		StringBuilder goals = new StringBuilder();
		mob.getGoalSelector().getAvailableGoals().forEach(w -> goals.append(w.getGoal().getClass().getSimpleName()).append(w.isRunning() ? "*" : "").append('@').append(w.getPriority()).append(','));
		return "target=" + (target == null ? "none" : target.getType().toShortString() + (target.isAlive() ? ":alive" : ":dead")) + " botAlive=" + bot.player().isAlive()
			+ " botRemoved=" + bot.player().isRemoved() + " botHealth=" + LearnLog.num(bot.player().getHealth()) + " mobAlive=" + mob.isAlive() + " mobRemoved=" + mob.isRemoved()
			+ " noAi=" + mob.isNoAi() + " ticking=" + arena.level().isPositionEntityTicking(mob.blockPosition()) + " tag=" + mob.entityTags().contains(StellaMobs.MODEL_DRIVEN_TAG)
			+ " tickCount=" + mob.tickCount + " sourceCalls=" + (source == null ? -1 : source.decisions()) + " goals=" + goals;
	}

	/** Ends the episode without a report, when the session that started it is gone. */
	public void abandon() {
		finish();
	}

	/** Applies the trainer's action for the next decision. An episode that is not live ignores it. */
	public void act(final float[] action) {
		if (state == State.LIVE && source != null) {
			source.set(action);
		}
	}

	public String stats() {
		return bot.stats().summary();
	}
}
