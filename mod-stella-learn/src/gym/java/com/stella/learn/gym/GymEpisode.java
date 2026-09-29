package com.stella.learn.gym;

import com.stella.learn.LearnLog;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** One fight: a mob that the client drives against a scripted bot, in one arena, until one dies or time runs out. */
public final class GymEpisode {
	public static final int OBSERVATION_SIZE = 16;
	public static final String MOB_DIED = "mobDied";
	public static final String BOT_DIED = "botDied";
	public static final String TIMEOUT = "timeout";
	public static final String ESCAPED = "escaped";
	private static final float MOB_HEALTH_SCALE = 20.0F;
	private static final double MIN_SPAWN_GAP = 6.0;

	private final Arena arena;
	private final GymConfig config;
	private final Bot bot;
	private final SkeletonActions actions = new SkeletonActions();
	private Mob mob;
	private long seed;
	private int episodes;
	private int ticks;
	private double reward;
	private double pendingReward;
	private float lastMobHealth;
	private float lastBotHealth;
	private String endReason;
	private boolean started;

	public GymEpisode(final Arena arena, final GymConfig config) {
		this.arena = arena;
		this.config = config;
		Vec3 spot = arena.randomSpot(RandomSource.create(config.seed() + arena.index()), 3.0);
		this.bot = Bot.spawn(arena.level(), "gym-bot-" + arena.index(), spot, 0.0F, arena.index(), config.sampleTicks());
		this.bot.setBrain(new RusherBrain(() -> mob));
		this.bot.give(0, new ItemStack(Items.IRON_SWORD));
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

	public SkeletonActions actions() {
		return actions;
	}

	public String endReason() {
		return endReason;
	}

	public boolean ended() {
		return endReason != null;
	}

	public boolean truncated() {
		return TIMEOUT.equals(endReason);
	}

	public double reward() {
		return reward;
	}

	public int ticks() {
		return ticks;
	}

	/** Resets the arena and puts a fresh mob and a revived bot in it. */
	public void start(final long newSeed) {
		seed = newSeed;
		episodes++;
		ticks = 0;
		reward = 0.0;
		pendingReward = 0.0;
		endReason = null;
		arena.reset();
		RandomSource random = RandomSource.create(newSeed * 31L + arena.index());
		Vec3 mobSpot = arena.randomSpot(random, 2.0);
		Vec3 botSpot = mobSpot;
		for (int attempt = 0; attempt < 16 && botSpot.distanceTo(mobSpot) < MIN_SPAWN_GAP; attempt++) {
			botSpot = arena.randomSpot(random, 2.0);
		}

		mob = EntityTypes.ZOMBIE.spawn(arena.level(), BlockPos.containing(mobSpot), EntitySpawnReason.COMMAND);
		if (mob == null) {
			GymLog.info(GymLog.EPISODE, "START_FAILED", "arena=" + arena.index() + " seed=" + newSeed + " reason=" + GymLog.SPAWN_FAILED + " at=" + Bot.fmt(mobSpot));
			throw new IllegalStateException("could not spawn the mob in arena " + arena.index());
		}

		mob.snapTo(mobSpot.x, mobSpot.y, mobSpot.z, random.nextFloat() * 360.0F, 0.0F);
		mob.removeAllGoals(goal -> true);
		mob.setPersistenceRequired();
		bot.reset(botSpot, Bot.yawTo(botSpot, mobSpot));
		lastMobHealth = mob.getHealth();
		lastBotHealth = bot.player().getHealth();
		started = true;
		GymLog.info(GymLog.EPISODE, "START", "arena=" + arena.index() + " seed=" + newSeed + " episode=" + episodes
			+ " mob=" + mob.getType().toShortString() + " id=" + mob.getUUID() + " " + bot.who()
			+ " mobAt=" + Bot.fmt(mobSpot) + " botAt=" + Bot.fmt(botSpot));
	}

	/** True once, after a start, so the step frame can flag the first observation of an episode. */
	public boolean consumeStarted() {
		boolean was = started;
		started = false;
		return was;
	}

	public void tickHeld() {
		if (endReason == null) {
			actions.tick(mob);
		}
	}

	/** Advances the episode clock by one decision and settles the reward and the end reason. */
	public void advance(final int decisionTicks) {
		if (endReason != null) {
			return;
		}

		ticks += decisionTicks;
		float mobHealth = mob.isAlive() ? mob.getHealth() : 0.0F;
		float botHealth = bot.player().isAlive() ? bot.player().getHealth() : 0.0F;
		double step = (lastBotHealth - botHealth) / MOB_HEALTH_SCALE - (lastMobHealth - mobHealth) / MOB_HEALTH_SCALE;
		lastMobHealth = mobHealth;
		lastBotHealth = botHealth;
		if (botHealth <= 0.0F) {
			step += 1.0;
			endReason = BOT_DIED;
		} else if (mobHealth <= 0.0F) {
			step -= 1.0;
			endReason = MOB_DIED;
		} else if (!arena.contains(mob.position())) {
			endReason = ESCAPED;
		} else if (ticks >= config.episodeTicks()) {
			endReason = TIMEOUT;
		}

		reward += step;
		pendingReward += step;
		if (endReason != null) {
			GymLog.info(GymLog.EPISODE, "END", "arena=" + arena.index() + " seed=" + seed + " episode=" + episodes + " ticks=" + ticks
				+ " reward=" + LearnLog.num(reward) + " reason=" + endReason + " mobHealth=" + LearnLog.num(mobHealth)
				+ " botHealth=" + LearnLog.num(botHealth));
		}
	}

	/** The reward earned since the last call. */
	public float takeReward() {
		float out = (float) pendingReward;
		pendingReward = 0.0;
		return out;
	}

	/** Writes the mob-side observation: relative positions, both velocities, both health values, and two zeros. */
	public void observe(final float[] out, final int offset) {
		Vec3 mobPos = mob.position();
		Vec3 botPos = bot.player().position();
		Vec3 mobVelocity = mob.getDeltaMovement();
		Vec3 botVelocity = bot.player().getDeltaMovement();
		int i = offset;
		i = put(out, i, mobPos.subtract(botPos));
		i = put(out, i, mobVelocity);
		i = put(out, i, botPos.subtract(mobPos));
		i = put(out, i, botVelocity);
		out[i++] = mob.getHealth() / MOB_HEALTH_SCALE;
		out[i++] = bot.player().getHealth() / MOB_HEALTH_SCALE;
		out[i++] = 0.0F;
		out[i] = 0.0F;
	}

	private static int put(final float[] out, final int at, final Vec3 v) {
		out[at] = (float) v.x;
		out[at + 1] = (float) v.y;
		out[at + 2] = (float) v.z;
		return at + 3;
	}
}
