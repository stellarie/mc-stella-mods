package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A ranged unit shoots from the dark and ducks out of sight. The cycle has four phases.
 *
 * <ul>
 * <li>STALK: the unit fights with its normal weapon goals.
 * <li>HIDE: the unit has loosed a shot that the player did not see. It walks to a cover spot for at most
 * {@link #HIDE_TICKS} ticks.
 * <li>WAIT: the unit holds in cover for {@link #WAIT_MIN_TICKS} to {@link #WAIT_MAX_TICKS} ticks.
 * <li>PEEK: the goal has ended. The weapon goal walks the unit back into view, because the standoff goal holds a unit
 * only while it sees its target. The unit then fires again.
 * </ul>
 *
 * <p>This goal only walks and waits. It holds MOVE and LOOK at rank {@link BehaviorInstaller#AMBUSH_PRIORITY}. The
 * retreat goal and learn's tactic goal can still cut a hide short. {@link AmbushWatchGoal} does the reading: it keeps the
 * seen state, finds the cover spot after an opening shot, and leaves the spot for this goal.
 *
 * <p>A shot that the player saw is a normal shot, and no hide follows (decision D9). A unit that is pursued with no cover
 * near asks for the KITE stance. The request only turns RETREAT into KITE. A healthy unit keeps fighting by the stance
 * script.
 *
 * <p>An elite sniper stray, a piglin, a melee unit, a spellcaster, a rider, and a mob that another mod drives never
 * ambush. The hide ends when the player dies or the unit takes another target. A waiting unit leaves cover when the player
 * comes inside its near band.
 *
 * <p>The unit remembers the player through the hide. The vanilla target goal forgets a player that it has not seen for 60
 * ticks, which is less than the longest wait. This goal sets the target again, so the unit can peek and fire.
 * {@link #repositioning(Mob)} is true during HIDE and WAIT. The standoff goal yields while it is true, because it would
 * stop the hide walk on every tick. Every time counts the unit's own ticks.
 */
public class AmbushGoal<T extends PathfinderMob> extends Goal {
	public static final int HIDE_TICKS = 60;
	public static final int WAIT_MIN_TICKS = 40;
	public static final int WAIT_MAX_TICKS = 100;
	/** Blocks beyond the near band at which a chasing player counts as a pursuer. */
	public static final double PURSUED_MARGIN = 2.0;
	// The retreat goal starts a run at this gap.
	// A unit must count as pursued by then.
	public static final double PURSUED_FLOOR = RetreatGoal.STANCE_GAP;
	public static final int KITE_TICKS = 100;
	/** A found spot waits this long for the goal to take it. */
	public static final int PENDING_TICKS = 20;
	/** A unit searches for cover about this often while it is pursued. */
	public static final int SEARCH_TICKS = 20;
	/** A shot that the watch goal reads later than this is no opening shot. */
	public static final int FRESH_SHOT_TICKS = 40;

	private static final double SPEED_MODIFIER = 1.2;
	private static final double ARRIVED_BLOCKS = 0.6;
	private static final double STOPPED_ARRIVED_BLOCKS = 1.5;
	private static final int REPATH_TICKS = 10;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	/** The cycle of one ambush. */
	public enum Phase {
		STALK,
		HIDE,
		WAIT,
		PEEK
	}

	/** Test seam: each phase change, with the cover spot when the unit starts to hide. */
	@FunctionalInterface
	public interface Recorder {
		void changed(Mob mob, Phase from, Phase to, @Nullable Vec3 spot);
	}

	private static final Recorder SILENT = (mob, from, to, spot) -> {
	};
	private static volatile Recorder recorder = SILENT;

	private final T mob;
	private @Nullable Vec3 spot;
	private @Nullable Player player;
	private int startTick;
	private int repathTick;
	private int waitUntilTick;
	private boolean waiting;
	private boolean failed;

	public AmbushGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public static void setRecorder(final @Nullable Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/**
	 * Adds the watch goal and the hide goal to a unit, once. The watch goal comes first, so a shot that it reads can
	 * start the hide on the same tick.
	 */
	public static void install(final PathfinderMob mob) {
		if (!isUnit(mob) || BossAbilities.copiesOf(mob, AmbushGoal.class) > 0) {
			return;
		}

		mob.getGoalSelector().addGoal(BehaviorInstaller.AMBUSH_PRIORITY, new AmbushWatchGoal<>(mob));
		mob.getGoalSelector().addGoal(BehaviorInstaller.AMBUSH_PRIORITY, new AmbushGoal<>(mob));
	}

	/** A ranged unit by role, or a witch. A piglin runs on its Brain and never ambushes. */
	static boolean isUnit(final Mob mob) {
		return (ReactiveState.get(mob).getRole() == CombatRole.RANGED || mob instanceof Witch) && !(mob instanceof AbstractPiglin);
	}

	/** True while the unit may ambush: no elite sniper, and no mob that another mod drives. */
	static boolean eligible(final Mob mob) {
		return !StrayKit.isSolitary(mob) && !mob.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG);
	}

	/** True during HIDE and WAIT. */
	public static boolean repositioning(final Mob mob) {
		ShotWatch.Record record = ShotWatch.peek(mob);
		return record != null && (record.phase() == Phase.HIDE || record.phase() == Phase.WAIT);
	}

	/**
	 * True when a chasing player stands within the near band plus the margin, or within the retreat gap, whichever is larger.
	 * The floor matters because a retreat run starts at a gap under 8 blocks, and an archer that runs cannot shoot. A unit
	 * must count as pursued before that run can start.
	 */
	static boolean pursued(final Mob mob, final Player player) {
		double near = StanceBands.near(mob, ReactiveState.get(mob).getNearDistance());
		return mob.distanceTo(player) <= Math.max(near + PURSUED_MARGIN, PURSUED_FLOOR) && PlayerReadout.chasing(player, mob);
	}

	/** Moves the unit to a phase, and tells the log and the recorder. */
	static void change(final Mob mob, final ShotWatch.Record record, final Phase to, final @Nullable Vec3 spot) {
		Phase from = record.phase();
		if (from == to) {
			return;
		}

		record.setPhase(to);
		StellaMobs.LOGGER.debug(
			"[BEHAVIOR] test=ambush PHASE mob={} type={} from={} to={} tick={} spot={}",
			mob.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), from, to, mob.tickCount, spot
		);
		recorder.changed(mob, from, to, spot);
	}

	@Override
	public boolean canUse() {
		if (!eligible(this.mob) || this.mob.isPassenger() || !(this.mob.getTarget() instanceof Player player) || !player.isAlive()) {
			return false;
		}

		ShotWatch.Record record = ShotWatch.peek(this.mob);
		return record != null
			&& record.pending(this.mob.tickCount, player.getUUID()) != null
			&& (this.mob.onGround() || this.mob.isInLiquid());
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.mob.getTarget();
		// Null: the target goal forgot the player.
		// Another target: the unit moved on.
		if (this.failed || this.player == null || !this.player.isAlive() || (target != null && target != this.player)) {
			return false;
		}

		if (!this.waiting) {
			return true;
		}

		// A player inside the near band found the unit.
		return this.mob.tickCount < this.waitUntilTick
			&& this.mob.distanceTo(this.player) >= StanceBands.near(this.mob, ReactiveState.get(this.mob).getNearDistance());
	}

	@Override
	public void start() {
		ShotWatch.Record record = ShotWatch.record(this.mob);
		Player player = this.mob.getTarget() instanceof Player target ? target : null;
		this.spot = player == null ? null : record.pending(this.mob.tickCount, player.getUUID());
		record.clearPending();
		this.waiting = false;
		this.failed = this.spot == null;
		if (this.spot == null) {
			return;
		}

		this.player = player;
		this.startTick = this.mob.tickCount;
		this.repathTick = this.mob.tickCount + REPATH_TICKS;
		CoverSearch.claim(this.mob, this.spot);
		change(this.mob, record, Phase.HIDE, this.spot);
		this.walk();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		CoverSearch.release(this.mob);
		if (repositioning(this.mob)) {
			change(this.mob, ShotWatch.record(this.mob), Phase.PEEK, null);
		}

		this.spot = null;
		this.player = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Player player = this.player;
		if (player == null || this.spot == null) {
			return;
		}

		// A wait can outlast the target goal's memory.
		if (this.mob.getTarget() == null) {
			this.mob.setTarget(player);
		}

		this.mob.getLookControl().setLookAt(player, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		if (this.waiting) {
			this.mob.getNavigation().stop();
			return;
		}

		int now = this.mob.tickCount;
		double toSpot = Math.hypot(this.mob.getX() - this.spot.x, this.mob.getZ() - this.spot.z);
		boolean arrived = toSpot <= ARRIVED_BLOCKS || (this.mob.getNavigation().isDone() && toSpot <= STOPPED_ARRIVED_BLOCKS);
		if (arrived) {
			this.beginWait(now);
		} else if (now - this.startTick >= HIDE_TICKS) {
			// Still in the open: the unit fights on.
			this.failed = true;
		} else if (this.mob.getNavigation().isDone() && now >= this.repathTick) {
			this.repathTick = now + REPATH_TICKS;
			this.walk();
		}
	}

	private void walk() {
		// No path: the unit fights on.
		if (this.spot == null || !this.mob.getNavigation().moveTo(this.spot.x, this.spot.y, this.spot.z, 0, SPEED_MODIFIER)) {
			this.failed = true;
		}
	}

	private void beginWait(final int now) {
		this.waiting = true;
		this.waitUntilTick = now + WAIT_MIN_TICKS + this.mob.getRandom().nextInt(WAIT_MAX_TICKS - WAIT_MIN_TICKS + 1);
		this.mob.getNavigation().stop();
		change(this.mob, ShotWatch.record(this.mob), Phase.WAIT, this.spot);
	}

	/**
	 * Reads the seen state, the opening shot, and the pursuit of a unit. The goal carries no flags and never runs. The
	 * selector asks it about every second tick, and each ask runs one reading and answers false. The stance goal polls the
	 * same way.
	 *
	 * <p>The goal keeps the seen counter of its unit in {@link ShotWatch}. A shot taken while unseen is an opening shot.
	 * The goal then searches once for cover. When it finds a spot, it leaves the spot for {@link AmbushGoal}.
	 *
	 * <p>A unit that is pursued with no cover calls {@link Stance#requestKite}. The goal reads the pursuit here, so that
	 * it works while the unit holds no flags. The goal sits before the retreat goal. A pursued archer then kites on the
	 * tick that its stance turns to RETREAT.
	 */
	static final class AmbushWatchGoal<T extends PathfinderMob> extends Goal {
		private final T mob;

		AmbushWatchGoal(final T mob) {
			this.mob = mob;
		}

		@Override
		public boolean canUse() {
			// A mob that never hunted a player has no record.
			Player player = this.mob.getTarget() instanceof Player target && target.isAlive() ? target : null;
			ShotWatch.Record record = player == null ? ShotWatch.peek(this.mob) : ShotWatch.record(this.mob);
			if (record == null || !eligible(this.mob)) {
				return false;
			}

			int now = this.mob.tickCount;
			record.update(now, player == null ? null : player.getUUID(), player != null && Gaze.seenBy(player, this.mob));
			if (player == null) {
				return false;
			}

			this.readShot(record, player, now);
			this.readPursuit(record, player, now);
			return false;
		}

		private void readShot(final ShotWatch.Record record, final Player player, final int now) {
			if (!record.hasUnhandledShot()) {
				return;
			}

			record.handleShots();
			Phase phase = record.phase();
			// A shot loosed in cover is no opening shot.
			if (phase == Phase.HIDE || phase == Phase.WAIT || now - record.lastShotTick() > FRESH_SHOT_TICKS) {
				return;
			}

			// A seen shot is a normal shot (decision D9).
			if (record.lastShotSeen() || Stance.of(this.mob) == Stance.PURSUE) {
				change(this.mob, record, Phase.STALK, null);
				return;
			}

			Optional<Vec3> spot = this.search(record, player, now);
			if (spot.isPresent()) {
				record.pend(spot.get(), player.getUUID(), now + PENDING_TICKS);
			} else {
				change(this.mob, record, Phase.STALK, null);
				if (pursued(this.mob, player)) {
					Stance.requestKite(this.mob, KITE_TICKS);
				}
			}
		}

		private void readPursuit(final ShotWatch.Record record, final Player player, final int now) {
			Phase phase = record.phase();
			if (phase == Phase.HIDE || phase == Phase.WAIT || !record.searchDue(now) || !pursued(this.mob, player)) {
				return;
			}

			if (this.search(record, player, now).isEmpty()) {
				Stance.requestKite(this.mob, KITE_TICKS);
			}
		}

		/** One cover search. It sets the floor for the next one. */
		private Optional<Vec3> search(final ShotWatch.Record record, final Player player, final int now) {
			record.searched(now, SEARCH_TICKS);
			return CoverSearch.hidden(this.mob, player);
		}
	}
}
