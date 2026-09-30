package com.stella.learn.model;

import com.stella.learn.AnswerRallyGoal;
import com.stella.learn.DodgeCritGoal;
import com.stella.learn.HoldAtChokeGoal;
import com.stella.learn.InterceptGoal;
import com.stella.learn.LearnConfigStore;
import com.stella.learn.LearnedBandGoal;
import com.stella.learn.TacticGoal;
import com.stella.learn.TakeCoverGoal;
import com.stella.learn.WaitOutShieldGoal;
import com.stella.mobs.StellaMobs;
import com.stella.mobs.behavior.ArcherKit;
import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.jspecify.annotations.Nullable;

/**
 * Makes bow archers model driven and hands them back. A loaded archer waits one server tick, so the mobs mod and this
 * mod have installed their goals whatever the listener order. Install swaps the archer's goals for one
 * {@link LearnedArcherGoal}; a fall back puts the very same goal objects back at their old ranks. Goal changes happen at
 * the end of a server tick, never inside a goal selector's tick.
 *
 * The policy loads once at server start. A missing file or a header that fails the contract disables the model for the
 * session, and every archer keeps its goals.
 */
public final class ModelDriver {
	public static final String POLICY_RESOURCE = "policies/archer-v1.bin";
	public static final String STATUS_OK = "ok";
	public static final String STATUS_MISSING = "missing";

	private record Fallback(Mob mob, String reason) {
	}

	private static final Queue<Mob> LOADED = new ConcurrentLinkedQueue<>();
	private static final Queue<Fallback> FALLBACKS = new ConcurrentLinkedQueue<>();
	private static final Map<UUID, ArcherState> DRIVEN = new ConcurrentHashMap<>();
	private static final Map<UUID, ActionSource> SOURCES = new ConcurrentHashMap<>();
	private static final Set<UUID> FELL_BACK = ConcurrentHashMap.newKeySet();
	private static volatile String status = STATUS_MISSING;
	private static volatile @Nullable ActionSource defaultSource;
	private static volatile boolean pinned;
	/** A bad policy would write one FALLBACK line per archer spawn, so only the first few per load are written at INFO. */
	private static final int LOUD_FALLBACKS = 5;
	private static final AtomicInteger loudFallbacks = new AtomicInteger();

	private ModelDriver() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> unloaded(entity));
		ServerTickEvents.END_SERVER_TICK.register(server -> endTick());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (!pinned) {
				load(POLICY_RESOURCE);
			}
		});
	}

	/** The status of the loaded policy: `ok`, `missing`, `contract` or `format`. */
	public static String status() {
		return status;
	}

	public static boolean isDriven(final Mob mob) {
		return DRIVEN.containsKey(mob.getUUID());
	}

	/** How many archers are model driven right now. */
	public static int drivenCount() {
		return DRIVEN.size();
	}

	/** An archer type that can be installed now: a kit archer, the switch on, a valid policy, and open to behavior. */
	public static boolean eligible(final Mob mob) {
		return skipReason(mob) == null;
	}

	/** Why `mob` is not installed, or null when it should be. */
	private static @Nullable String skipReason(final Mob mob) {
		if (!ArcherKit.isKitArcher(mob)) {
			return "notArcher";
		}

		if (mob.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)) {
			return "noBehavior";
		}

		if (!LearnConfigStore.get().modelArcher()) {
			return "switch";
		}

		if (FELL_BACK.contains(mob.getUUID())) {
			return "fellBack";
		}

		return STATUS_OK.equals(status) ? null : status;
	}

	public static void setActionSource(final Mob mob, final ActionSource source) {
		SOURCES.put(mob.getUUID(), source);
	}

	public static void clearActionSource(final Mob mob) {
		SOURCES.remove(mob.getUUID());
	}

	static @Nullable ActionSource sourceFor(final Mob mob) {
		ActionSource own = SOURCES.get(mob.getUUID());
		return own != null ? own : defaultSource;
	}

	/** Loads the policy at `resource` and pins it, so a server start does not replace it. Meant for tests and the gym. */
	public static void reloadPolicy(final String resource) {
		pinned = true;
		load(resource);
	}

	public static void useTestPolicy(final PolicyFile file) {
		pinned = true;
		loudFallbacks.set(0);
		defaultSource = new MlpSource(new Mlp(file));
		status = STATUS_OK;
		ModelLog.load(STATUS_OK, contractHash(), file.size(), "path=test");
	}

	/** Makes every archer eligible with `source` as the default answer. The gym uses it, and sets its own per mob. */
	public static void useSource(final ActionSource source) {
		pinned = true;
		loudFallbacks.set(0);
		defaultSource = source;
		status = STATUS_OK;
		ModelLog.load(STATUS_OK, contractHash(), 0, "path=source");
	}

	public static void useNoPolicy() {
		pinned = true;
		loudFallbacks.set(0);
		defaultSource = null;
		status = STATUS_MISSING;
		ModelLog.load(STATUS_MISSING, contractHash(), 0, "path=none");
	}

	private static String contractHash() {
		try {
			return Contract.archer().hashHex().substring(0, 8);
		} catch (IllegalStateException exception) {
			return "none";
		}
	}

	private static void load(final String resource) {
		Contract contract;
		try {
			contract = Contract.archer();
		} catch (IllegalStateException exception) {
			refuse("contract", 0, "path=" + resource + " detail=" + exception.getMessage());
			return;
		}

		String mismatch = ArcherObservation.mismatch(contract);
		if (mismatch != null) {
			refuse("contract", 0, "path=" + resource + " detail=" + mismatch);
			return;
		}

		try {
			PolicyFile file = PolicyFile.loadResource(resource, contract);
			loudFallbacks.set(0);
			defaultSource = new MlpSource(new Mlp(file));
			status = STATUS_OK;
			ModelLog.load(STATUS_OK, contract.hashHex().substring(0, 8), file.size(), "path=" + resource);
		} catch (IOException exception) {
			refuse(STATUS_MISSING, 0, "path=" + resource);
		} catch (PolicyFile.Rejected exception) {
			refuse(exception.reason(), 0, "path=" + resource + " detail=" + exception.getMessage());
		}
	}

	private static void refuse(final String reason, final int size, final String detail) {
		loudFallbacks.set(0);
		defaultSource = null;
		status = reason;
		ModelLog.load(reason, contractHash(), size, detail);
	}

	private static void loaded(final Entity entity) {
		PlayerArrows.loaded(entity);
		if (entity instanceof AbstractSkeleton skeleton) {
			LOADED.add(skeleton);
		}
	}

	private static void unloaded(final Entity entity) {
		PlayerArrows.unloaded(entity);
		if (entity instanceof Mob mob) {
			DRIVEN.remove(mob.getUUID());
			SOURCES.remove(mob.getUUID());
		}
	}

	private static void endTick() {
		ModelPerf.endTick();
		Rays.clear();
		Fallback pending;
		while ((pending = FALLBACKS.poll()) != null) {
			uninstall(pending.mob(), pending.reason());
		}

		Mob mob;
		while ((mob = LOADED.poll()) != null) {
			process(mob);
		}
	}

	private static void process(final Mob mob) {
		if (mob.isRemoved() || !mob.isAlive() || DRIVEN.containsKey(mob.getUUID())) {
			return;
		}

		// The tag is saved with the mob, but the goals are not, so a tag on an archer nobody drives is stale.
		if (ArcherKit.isKitArcher(mob)) {
			mob.removeTag(StellaMobs.MODEL_DRIVEN_TAG);
		}

		String reason = skipReason(mob);
		if (reason == null) {
			install(mob);
		} else if ("missing".equals(reason) || "contract".equals(reason) || "format".equals(reason)) {
			if (loudFallbacks.incrementAndGet() <= LOUD_FALLBACKS) {
				ModelLog.fallback(mob, reason, "installed=false");
			} else {
				ModelLog.skip(mob, reason);
			}
		} else {
			ModelLog.skip(mob, reason);
		}
	}

	private static void install(final Mob mob) {
		List<WrappedGoal> removed = new ArrayList<>();
		for (WrappedGoal wrapped : List.copyOf(mob.getGoalSelector().getAvailableGoals())) {
			if (replaced(wrapped.getGoal())) {
				removed.add(wrapped);
			}
		}

		List<String> names = new ArrayList<>();
		for (WrappedGoal wrapped : removed) {
			mob.getGoalSelector().removeGoal(wrapped.getGoal());
			names.add(wrapped.getGoal().getClass().getSimpleName() + "@" + wrapped.getPriority());
		}

		ArcherState state = new ArcherState();
		state.removed = List.copyOf(removed);
		state.goal = new LearnedArcherGoal(mob, state);
		mob.addTag(StellaMobs.MODEL_DRIVEN_TAG);
		mob.getGoalSelector().addGoal(LearnedArcherGoal.PRIORITY, state.goal);
		DRIVEN.put(mob.getUUID(), state);
		ModelLog.install(mob, names, SOURCES.containsKey(mob.getUUID()) ? "custom" : "policy");
	}

	/** Queues the mob's return to its goals for the end of this tick. A mob that is not driven is left alone. */
	public static void fallBack(final Mob mob, final String reason) {
		ArcherState state = DRIVEN.get(mob.getUUID());
		if (state == null || state.retiring) {
			return;
		}

		state.retiring = true;
		FALLBACKS.add(new Fallback(mob, reason));
	}

	private static void uninstall(final Mob mob, final String reason) {
		ArcherState state = DRIVEN.remove(mob.getUUID());
		if (state == null) {
			return;
		}

		FELL_BACK.add(mob.getUUID());
		mob.removeTag(StellaMobs.MODEL_DRIVEN_TAG);
		if (mob.isRemoved()) {
			ModelLog.fallback(mob, reason, "restored=0 removed=true");
			return;
		}

		mob.getGoalSelector().removeGoal(state.goal);
		for (WrappedGoal wrapped : state.removed) {
			mob.getGoalSelector().addGoal(wrapped.getPriority(), wrapped.getGoal());
		}

		ModelLog.fallback(mob, reason, "restored=" + state.removed.size() + " releases=" + state.releases + " goal_ticks=" + state.goalTicks);
	}

	/** The goals a model-driven archer gives up. Target selection and flagless goals such as the pearl throw stay. */
	static boolean replaced(final Goal goal) {
		return goal instanceof RetreatGoal<?>
			|| goal instanceof KeepDistanceGoal<?>
			|| goal instanceof HoldStandoffGoal<?>
			|| goal instanceof SkeletonVolleyGoal
			|| goal instanceof RangedBowAttackGoal<?>
			|| goal instanceof TacticGoal
			|| goal instanceof LearnedBandGoal
			|| goal instanceof WaitOutShieldGoal
			|| goal instanceof AnswerRallyGoal
			|| goal instanceof TakeCoverGoal
			|| goal instanceof HoldAtChokeGoal
			|| goal instanceof InterceptGoal
			|| goal instanceof DodgeCritGoal;
	}
}
