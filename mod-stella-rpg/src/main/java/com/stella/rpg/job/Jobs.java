package com.stella.rpg.job;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stella.rpg.RpgAttributes;
import com.stella.rpg.SpellsBridge;
import com.stella.rpg.Stat;
import com.stella.rpg.StatBonuses;
import com.stella.rpg.StellaRpg;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The job of a player. A player chooses once, from level {@link #MIN_LEVEL}, and the job is permanent. The first change to a job adds {@link #CHANGE_BONUS} to each of three stats of that job; changing to the same job again adds nothing. The stat bonus
 * counts every level the player has, also the levels before the choice. A boss trial at level 40, 70, 100, and so on
 * adds a fixed bonus once. Another mod reaches this through the object share {@link #SHARE_KEY}, a
 * {@code Map<String, Object>} of functions: {@code get}, {@code choose}, {@code grantItems}, {@code grantTrial},
 * {@code trialsDone}, and {@code innate}, which answers the key of the innate skill of the job, such as
 * {@code falcon_guard}.
 */
public final class Jobs implements StatBonuses.Provider {
	public static final String SHARE_KEY = "mod-stella-rpg:jobs";
	public static final int MIN_LEVEL = 10;
	public static final int FIRST_TRIAL_LEVEL = 40;
	public static final int TRIAL_STEP = 30;
	public static final int TRIAL_MAIN_BONUS = 5;
	public static final int TRIAL_OTHER_BONUS = 1;
	/** Added once per job, to each of the three stats in {@link Job#bonusStats()}, at the first change to that job. */
	public static final int CHANGE_BONUS = 3;

	/** The job is saved by key, so a renamed constant cannot break a save. */
	public record State(Job job, List<Integer> trials, List<String> bonusJobs) {
		public static final State EMPTY = new State(Job.NONE, List.of(), List.of());

		public State(final Job job, final List<Integer> trials) {
			this(job, trials, List.of());
		}

		private static final Codec<Job> JOB = Codec.STRING.xmap(key -> Job.byKey(key).orElse(Job.NONE), Job::key);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			JOB.optionalFieldOf("job", Job.NONE).forGetter(State::job),
			Codec.INT.listOf().optionalFieldOf("trials", List.of()).forGetter(State::trials),
			Codec.STRING.listOf().optionalFieldOf("bonus_jobs", List.of()).forGetter(State::bonusJobs)
		).apply(instance, State::new));
	}

	public static final AttachmentType<State> ATTACHMENT = AttachmentRegistry.create(
		StellaRpg.id("job"),
		builder -> builder.persistent(State.CODEC).copyOnDeath()
	);

	private Jobs() {
	}

	public static void register() {
		StatBonuses.register(new Jobs());
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, share());
	}

	public static Job of(final ServerPlayer player) {
		return state(player).job();
	}

	public static Set<Integer> trialsDone(final ServerPlayer player) {
		return Set.copyOf(state(player).trials());
	}

	/** Sets the job once. False below level 10, for NONE, or when a job is already set. */
	public static boolean choose(final ServerPlayer player, final Job job) {
		if (job == Job.NONE || player.experienceLevel < MIN_LEVEL || of(player) != Job.NONE) {
			return false;
		}
		State before = state(player);
		List<String> bonusJobs = new java.util.ArrayList<>(before.bonusJobs());
		if (!bonusJobs.contains(job.key())) {
			bonusJobs.add(job.key());
		}
		player.setAttached(ATTACHMENT, new State(job, before.trials(), List.copyOf(bonusJobs)));
		RpgAttributes.refresh(player);
		StellaRpg.LOGGER.info("{} chose the job {}", player.getGameProfile().name(), job.key());
		return true;
	}

	public static boolean isTrialLevel(final int level) {
		return level >= FIRST_TRIAL_LEVEL && (level - FIRST_TRIAL_LEVEL) % TRIAL_STEP == 0;
	}

	/** Records a trial once. False without a job, for a level that is no trial, or for a trial already done. */
	public static boolean grantTrial(final ServerPlayer player, final int level) {
		State state = state(player);
		if (state.job() == Job.NONE || !isTrialLevel(level) || state.trials().contains(level)) {
			return false;
		}
		Set<Integer> trials = new TreeSet<>(state.trials());
		trials.add(level);
		player.setAttached(ATTACHMENT, new State(state.job(), List.copyOf(trials), state.bonusJobs()));
		RpgAttributes.refresh(player);
		StellaRpg.LOGGER.info("{} finished the trial {}", player.getGameProfile().name(), level);
		return true;
	}

	/** Gives the item of the player's job. False without a job or when nothing was given. */
	public static boolean grantItems(final ServerPlayer player) {
		Job job = of(player);
		if (job == Job.NONE) {
			return false;
		}
		boolean kit = JobItems.grant(player, job);
		// An archer and a mage also get their starter books.
		boolean starter = job == Job.ARCHER && SpellsBridge.grantArcherStarter(player)
			|| job == Job.MAGE && SpellsBridge.grantMageStarter(player);
		return kit || starter;
	}

	@Override
	public int bonus(final LivingEntity entity, final Stat stat) {
		if (!(entity instanceof ServerPlayer player)) {
			return 0;
		}
		State state = state(player);
		long sum = changeBonus(state, stat);
		if (state.job() == Job.NONE) {
			return (int) sum;
		}
		long levels = Math.max(0, player.experienceLevel);
		sum += levels * state.job().perLevel(stat);
		int trialBonus = stat == state.job().mainStat() ? TRIAL_MAIN_BONUS : TRIAL_OTHER_BONUS;
		sum += (long) state.trials().size() * trialBonus;
		return (int) Math.min(Integer.MAX_VALUE, sum);
	}

	/** The one-time bonus: 3 for the stat of each job the player ever changed to, once per job. */
	private static int changeBonus(final State state, final Stat stat) {
		int sum = 0;
		for (String key : state.bonusJobs()) {
			if (Job.byKey(key).map(job -> job.bonusStats().contains(stat)).orElse(false)) {
				sum += CHANGE_BONUS;
			}
		}
		return sum;
	}

	private static State state(final ServerPlayer player) {
		return player.getAttachedOrElse(ATTACHMENT, State.EMPTY);
	}

	private static Map<String, Object> share() {
		Function<ServerPlayer, String> get = player -> of(player).key();
		BiFunction<ServerPlayer, String, Boolean> choose =
			(player, key) -> key != null && Job.byKey(key).map(job -> choose(player, job)).orElse(false);
		Function<ServerPlayer, Boolean> grantItems = Jobs::grantItems;
		BiFunction<ServerPlayer, Integer, Boolean> grantTrial = (player, level) -> level != null && grantTrial(player, level);
		Function<ServerPlayer, Set<Integer>> trialsDone = Jobs::trialsDone;
		Function<ServerPlayer, String> innate = player -> of(player).innate().key();
		return Map.of(
			"get", get, "choose", choose, "grantItems", grantItems, "grantTrial", grantTrial, "trialsDone", trialsDone,
			"innate", innate
		);
	}
}
