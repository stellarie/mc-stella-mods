package com.stella.mobs;

import com.stella.mobs.behavior.HoldStandoffGoal;
import com.stella.mobs.behavior.KeepDistanceGoal;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.RetreatGoal;
import com.stella.mobs.behavior.ShieldUseGoal;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * One behavior log per arena. It writes a heartbeat every five ticks, one line on each state change, and
 * a closing summary. It also writes the subject's goal table and every shield gate value, so a report
 * can name the false gate instead of guessing. The log route is the mod logger at INFO.
 */
final class BehaviorLog {
	private static final int HEARTBEAT_TICKS = 5;
	private static final int DETAIL_TICKS = 20;
	private static final double MELEE_PRESSURE_DISTANCE = 3.0;
	private static final double ARROW_BOX = 1.5;
	private static final double SHIELD_THREAT_RADIUS = 8.0;
	// The subject falls two blocks at spawn, and the mod's distance goal needs it on the ground.
	private static final long FIRE_WINDOW_START_TICK = 20L;

	private final GameTestHelper helper;
	private final String test;
	private final Mob subject;
	private final Mob meleeOpponent;
	private final Set<Integer> blockedArrowIds = new HashSet<>();

	private long shieldRaisedAt = -1L;
	private int blockedMelee;
	private int keepDistanceTicks;
	private double minDistance = -1.0;
	private double firedAtDistance = -1.0;
	private long lastFiredTick = -1L;
	private boolean illegalCloseFire;
	private boolean wasDrawing;
	private boolean meleeWasSwinging;
	private String lastState;
	private long lastDetailTick = -1L;

	BehaviorLog(
		final GameTestHelper helper,
		final String test,
		final Mob subject,
		final Mob meleeOpponent,
		final Mob rangedOpponent
	) {
		this.helper = helper;
		this.test = test;
		this.subject = subject;
		this.meleeOpponent = meleeOpponent;
	}

	void tick() {
		long now = this.helper.getTick();
		boolean blocking = this.subject.isBlocking();
		LivingEntity target = this.subject.getTarget();
		double distance = target == null ? -1.0 : this.subject.distanceTo(target);

		if (blocking && this.shieldRaisedAt < 0L) {
			this.shieldRaisedAt = now;
		}

		if (blocking) {
			countBlockedMelee();
			countBlockedArrows();
		}

		if (isRunning(this.subject, KeepDistanceGoal.class)) {
			this.keepDistanceTicks++;
		}

		if (distance >= 0.0) {
			this.minDistance = this.minDistance < 0.0 ? distance : Math.min(this.minDistance, distance);
		}

		boolean drawing = isDrawing();

		if (drawing && !this.wasDrawing && now >= FIRE_WINDOW_START_TICK && distance >= 0.0) {
			this.firedAtDistance = this.firedAtDistance < 0.0 ? distance : Math.min(this.firedAtDistance, distance);
			this.lastFiredTick = now;

			if (distance < ReactiveState.get(this.subject).getNearDistance() && !isRetreatExhausted()) {
				this.illegalCloseFire = true;
			}
		}

		this.wasDrawing = drawing;

		String state = state(blocking, target);

		if (!state.equals(this.lastState) || now % HEARTBEAT_TICKS == 0L) {
			this.lastState = state;
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} t={} dist={} target={} using={} blocking={} hp={} held={}|{}",
				this.test, now, number(distance), typeName(target), using(), blocking,
				number(this.subject.getHealth()), itemName(this.subject.getMainHandItem()),
				itemName(this.subject.getOffhandItem())
			);
		}

		if (this.lastDetailTick < 0L || now - this.lastDetailTick >= DETAIL_TICKS) {
			this.lastDetailTick = now;
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} t={} GOALS {}", this.test, now, goals());
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} t={} GATES {}", this.test, now, gates());
		}
	}

	void result() {
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test={} RESULT shieldRaisedAt={} blockedMelee={} blockedArrows={} minDist={} firedAtDist={}",
			this.test, this.shieldRaisedAt < 0L ? "never" : Long.toString(this.shieldRaisedAt), this.blockedMelee,
			this.blockedArrowIds.size(), number(this.minDistance),
			this.firedAtDistance < 0.0 ? "never" : number(this.firedAtDistance)
		);
	}

	long shieldRaisedAt() {
		return this.shieldRaisedAt;
	}

	double firedAtDistance() {
		return this.firedAtDistance;
	}

	long lastFiredTick() {
		return this.lastFiredTick;
	}

	int keepDistanceTicks() {
		return this.keepDistanceTicks;
	}

	boolean illegalCloseFire() {
		return this.illegalCloseFire;
	}

	private boolean isRetreatExhausted() {
		for (WrappedGoal wrapped : this.subject.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof KeepDistanceGoal<?> goal) {
				return goal.isRetreatExhausted();
			}
		}

		return false;
	}

	String summary() {
		return "shieldRaisedAt=" + (this.shieldRaisedAt < 0L ? "never" : Long.toString(this.shieldRaisedAt))
			+ " blockedMelee=" + this.blockedMelee
			+ " blockedArrows=" + this.blockedArrowIds.size()
			+ " minDist=" + number(this.minDistance)
			+ " firedAtDist=" + (this.firedAtDistance < 0.0 ? "never" : number(this.firedAtDistance));
	}

	String goalsText() {
		return goals();
	}

	private String goals() {
		StringBuilder text = new StringBuilder();

		for (WrappedGoal wrapped : this.subject.getGoalSelector().getAvailableGoals()) {
			text.append(wrapped.getGoal().getClass().getSimpleName())
				.append(':')
				.append(wrapped.getPriority())
				.append(wrapped.getFlags().toString().replace(" ", ""))
				.append(wrapped.isRunning() ? ":run " : ":idle ");
		}

		return text.length() == 0 ? "none" : text.toString();
	}

	private String gates() {
		ShieldUseGoal<?> shield = null;
		KeepDistanceGoal<?> keeper = null;

		for (WrappedGoal wrapped : this.subject.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof ShieldUseGoal<?> goal) {
				shield = goal;
			}

			if (wrapped.getGoal() instanceof KeepDistanceGoal<?> goal) {
				keeper = goal;
			}
		}

		ReactiveState state = ReactiveState.get(this.subject);
		LivingEntity target = this.subject.getTarget();
		boolean targetOk = target != null && target.isAlive();
		double distance = targetOk ? this.subject.distanceTo(target) : -1.0;
		boolean threat = targetOk && this.subject.distanceToSqr(target) <= SHIELD_THREAT_RADIUS * SHIELD_THREAT_RADIUS;
		boolean healthOk = this.subject.getHealth() > this.subject.getMaxHealth() * RetreatGoal.HEALTH_FRACTION;
		boolean airborne = !this.subject.onGround() && !this.subject.isInLiquid() && !this.subject.isPassenger();

		return "role=" + state.getRole()
			+ " band=" + number(state.getNearDistance()) + ".." + number(state.getFarDistance())
			+ " shieldInstalled=" + (shield != null)
			+ " shieldCanUse=" + (shield != null && shield.canUse())
			+ " shieldOffhandBlocks=" + this.subject.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)
			+ " shieldUsing=" + this.subject.isUsingItem()
			+ " shieldCooldownReady=" + (this.subject.level().getGameTime() >= state.getShieldReadyTick())
			+ " shieldHealthOk=" + healthOk
			+ " shieldThreatInEight=" + threat
			+ " shieldLineOfSight=" + (threat && this.subject.getSensing().hasLineOfSight(target))
			+ " keeperInstalled=" + (keeper != null)
			+ " keeperCanUse=" + (keeper != null && keeper.canUse())
			+ " keeperRunning=" + isRunning(this.subject, KeepDistanceGoal.class)
			+ " keeperReady=" + (this.subject.level().getGameTime() >= state.getKeepDistanceReadyTick())
			+ " keeperHealthOk=" + healthOk
			+ " keeperAirborne=" + airborne
			+ " keeperTargetOk=" + targetOk
			+ " keeperDist=" + number(distance)
			+ " standoffRunning=" + isRunning(this.subject, HoldStandoffGoal.class)
			+ " retreatExhausted=" + isRetreatExhausted()
			+ " illegalCloseFire=" + this.illegalCloseFire
			+ " subjectPos=" + blockPos(this.subject.position())
			+ " navTarget=" + (this.subject.getNavigation().getTargetPos() == null
				? "none"
				: blockPos(this.subject.getNavigation().getTargetPos()))
			+ " navDone=" + this.subject.getNavigation().isDone()
			+ " playerNearby=" + state.playerInRange(this.subject)
			+ " keepDistanceTicks=" + this.keepDistanceTicks
			+ " lastFiredTick=" + this.lastFiredTick
			+ playerGates();
	}

	private String playerGates() {
		net.minecraft.world.entity.player.Player player = this.subject.level().getNearestPlayer(this.subject, 64.0);

		if (player == null) {
			return " playerTarget=none";
		}

		return " playerTarget=" + player.getScoreboardName()
			+ " playerCreative=" + player.isCreative()
			+ " playerSpectator=" + player.isSpectator()
			+ " playerInvulnerable=" + player.isInvulnerable()
			+ " playerAbilitiesInvulnerable=" + player.getAbilities().invulnerable
			+ " playerSeenAsEnemy=" + player.canBeSeenAsEnemy()
			+ " playerAlive=" + player.isAlive()
			+ " playerHealth=" + number(player.getHealth())
			+ " canAttackPlayer=" + this.subject.canAttack(player)
			+ " difficulty=" + this.subject.level().getDifficulty();
	}

	private static boolean isRunning(final Mob mob, final Class<? extends Goal> goalClass) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (goalClass.isInstance(wrapped.getGoal()) && wrapped.isRunning()) {
				return true;
			}
		}

		return false;
	}

	private void countBlockedMelee() {
		boolean swinging = this.meleeOpponent.isSwinging();

		if (swinging && !this.meleeWasSwinging
			&& this.meleeOpponent.distanceTo(this.subject) <= MELEE_PRESSURE_DISTANCE) {
			this.blockedMelee++;
		}

		this.meleeWasSwinging = swinging;
	}

	private void countBlockedArrows() {
		for (AbstractArrow arrow : this.subject.level().getEntitiesOfClass(
			AbstractArrow.class, this.subject.getBoundingBox().inflate(ARROW_BOX), candidate -> candidate.getOwner() != this.subject
		)) {
			this.blockedArrowIds.add(arrow.getId());
		}
	}

	private boolean isDrawing() {
		ItemStack useItem = this.subject.getUseItem();
		return this.subject.isUsingItem() && (useItem.is(Items.BOW) || useItem.is(Items.CROSSBOW));
	}

	private String state(final boolean blocking, final Entity target) {
		return blocking + "|" + using() + "|" + typeName(target) + "|" + itemName(this.subject.getMainHandItem())
			+ "|" + itemName(this.subject.getOffhandItem());
	}

	private String using() {
		if (!this.subject.isUsingItem()) {
			return "none";
		}

		return this.subject.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? "off_hand" : "main_hand";
	}

	private static String typeName(final Entity entity) {
		return entity == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	private static String itemName(final ItemStack stack) {
		return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String number(final double value) {
		return value < 0.0 ? "none" : String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private static String number(final float value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private static String blockPos(final net.minecraft.world.phys.Vec3 position) {
		return String.format(java.util.Locale.ROOT, "%.1f/%.1f/%.1f", position.x, position.y, position.z);
	}

	private static String blockPos(final net.minecraft.core.BlockPos position) {
		return position.getX() + "/" + position.getY() + "/" + position.getZ();
	}
}
