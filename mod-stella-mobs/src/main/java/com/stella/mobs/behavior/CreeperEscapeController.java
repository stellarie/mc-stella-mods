package com.stella.mobs.behavior;

import com.stella.mobs.mixin.CubeMoveControlInvoker;
import com.stella.mobs.mixin.PhantomMoveTargetAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class CreeperEscapeController {
	public enum EscapeDecision {
		NO_THREAT, STATE_GATED, NO_SAFE_ROUTE, COMMAND_SET
	}

	public static final String NO_THREAT = EscapeDecision.NO_THREAT.name();
	public static final String STATE_GATED = EscapeDecision.STATE_GATED.name();
	public static final String NO_SAFE_ROUTE = EscapeDecision.NO_SAFE_ROUTE.name();
	public static final String COMMAND_SET = EscapeDecision.COMMAND_SET.name();
	private static final double SPEED = 3.0;
	private static final int ROUTE_RETRY_TICKS = 10;

	private CreeperEscapeController() {
	}

	public static EscapeDecision apply(final Mob mob) {
		ReactiveState state = ReactiveState.get(mob);
		if (mob instanceof Creeper) {
			state.setR02Result(STATE_GATED, "creeperSubject");
			return EscapeDecision.STATE_GATED;
		}

		Creeper threat = CreeperEscapePositions.threatFor(mob);
		String gate = stateGate(mob);
		if (gate != null) {
			state.clearR02Route();
			state.setR02Result(STATE_GATED, gate);
			return EscapeDecision.STATE_GATED;
		}

		if (threat == null) {
			state.clearR02Route();
			state.setR02Result(NO_THREAT, "");
			return EscapeDecision.NO_THREAT;
		}

		if (CreeperEscapePositions.isSafe(mob.position(), threat)) {
			state.setR02Result(COMMAND_SET, "");
			return EscapeDecision.COMMAND_SET;
		}

		if (mob instanceof Shulker shulker) {
			escapeShulker(shulker, threat, state);
		} else if (mob instanceof Dolphin dolphin) {
			escapeDolphin(dolphin, threat, state);
		} else if (mob instanceof AbstractCubeMob cube) {
			escapeCube(cube, threat, state);
		} else if (mob instanceof Phantom phantom) {
			escapePhantom(phantom, threat, state);
		} else if (mob instanceof Allay allay) {
			escapePathfinder(allay, threat, state);
		} else if (mob instanceof Bat bat) {
			escapeBat(bat, threat, state);
		} else if (mob instanceof Ghast || mob instanceof HappyGhast) {
			escapeFlyer(mob, threat, state);
		} else if (mob instanceof PathfinderMob pathfinder) {
			escapePathfinder(pathfinder, threat, state);
		} else {
			escapeFlyer(mob, threat, state);
		}

		return EscapeDecision.valueOf(state.getR02Decision());
	}

	public static String nativeCommand(final MoveControl control) {
		return control.getClass().getName()
			+ "|" + control.getWantedX()
			+ "|" + control.getWantedY()
			+ "|" + control.getWantedZ()
			+ "|" + control.getSpeedModifier();
	}

	public static boolean pauseDragonPhase(final EnderDragon dragon, final DragonPhaseInstance phase) {
		ReactiveState state = ReactiveState.get(dragon);
		Creeper threat = CreeperEscapePositions.threatFor(dragon);
		if (threat == null) {
			state.setR02DragonTarget(null);
			state.setR02Result(NO_THREAT, "");
			return false;
		}

		String gate = stateGate(dragon);
		boolean nonFlightPhase = phase.isSitting() && phase.getPhase() != EnderDragonPhase.HOVERING;
		if (gate != null || nonFlightPhase) {
			state.setR02DragonTarget(null);
			state.setR02Result(STATE_GATED, gate == null ? "controllerState" : gate);
			return false;
		}

		Vec3 target = state.getR02DragonTarget();
		if (target == null || !CreeperEscapePositions.isSafe(target, threat)) {
			target = CreeperEscapePositions.findFlightTarget(dragon, threat);
		}
		if (target == null) {
			state.setR02DragonTarget(null);
			state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
			return false;
		}

		state.setR02DragonTarget(target);
		state.setR02Result(COMMAND_SET, "");
		return true;
	}

	public static Vec3 dragonTarget(final EnderDragon dragon, final DragonPhaseInstance phase) {
		Vec3 target = ReactiveState.get(dragon).getR02DragonTarget();
		return target == null ? phase.getFlyTargetLocation() : target;
	}

	private static @org.jspecify.annotations.Nullable String stateGate(final Mob mob) {
		if (mob.isNoAi()) {
			return "noAI";
		}

		if (mob.isPassenger()) {
			return "passenger";
		}

		if (mob instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0) {
			return "controllerState";
		}

		if (mob instanceof Warden warden
			&& (warden.getPose() == Pose.DIGGING || warden.getPose() == Pose.EMERGING)) {
			return "controllerState";
		}

		if (mob instanceof Creaking creaking && !creaking.canMove()) {
			return "controllerState";
		}

		if (mob instanceof Fox fox && (fox.isSitting() || fox.isSleeping() || fox.isFaceplanted())) {
			return "controllerState";
		}

		if (mob instanceof Panda panda && panda.isOnBack()) {
			return "controllerState";
		}

		if (mob instanceof SulfurCube cube && !cube.getItemBySlot(EquipmentSlot.BODY).isEmpty()) {
			return "controllerState";
		}

		if (mob instanceof HappyGhast ghast && ghast.isOnStillTimeout()) {
			return "controllerState";
		}

		return null;
	}

	private static void escapePathfinder(
		final PathfinderMob mob, final Creeper threat, final ReactiveState state
	) {
		int now = mob.tickCount;
		if (mob.getNavigation().canNavigateGround() && !mob.onGround() && !mob.isInLiquid()) {
			if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
				state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
				return;
			}

			Vec3 target = state.cachedR02Route(threat.getUUID(), now);
			if (target == null || !CreeperEscapePositions.isSafe(target, threat)) {
				target = CreeperEscapePositions.findDirectSupportedTarget(mob, threat);
			}
			if (target == null) {
				state.cacheR02RouteFailure(threat.getUUID(), NO_SAFE_ROUTE, now + 2);
				state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
				return;
			}

			state.cacheR02Route(threat.getUUID(), target, now + ROUTE_RETRY_TICKS);
			mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);
			state.setR02Result(COMMAND_SET, "");
			return;
		}

		if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
			state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
			return;
		}

		Path path = null;
		Vec3 cached = state.cachedR02Route(threat.getUUID(), now);
		if (cached != null && CreeperEscapePositions.isSafe(cached, threat)) {
			path = mob.getNavigation().createPath(BlockPos.containing(cached), 0);
		}
		if (path == null || !path.canReach()) {
			path = CreeperEscapePositions.findNavigationPath(mob, threat);
		}
		Path activePath = mob.getNavigation().getPath();
		boolean continuingPath = path != null
			&& path.canReach()
			&& activePath != null
			&& !activePath.isDone()
			&& path.getTarget().equals(activePath.getTarget());
		if (path == null || (!continuingPath && !mob.getNavigation().moveTo(path, SPEED))) {
			state.cacheR02RouteFailure(threat.getUUID(), NO_SAFE_ROUTE, now + ROUTE_RETRY_TICKS);
			state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
			return;
		}

		if (continuingPath) {
			path = activePath;
		}

		Vec3 target = Vec3.atBottomCenterOf(path.getTarget());
		state.cacheR02Route(threat.getUUID(), target, now + ROUTE_RETRY_TICKS);
		if (!continuingPath) {
			int waypointIndex = Math.max(0, Math.min(1, path.getNodeCount() - 1));
			Vec3 waypoint = path.getEntityPosAtNode(mob, waypointIndex);
			mob.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, SPEED);
		}
		state.setR02Result(COMMAND_SET, "");
	}

	private static void escapeDolphin(
		final Dolphin dolphin, final Creeper threat, final ReactiveState state
	) {
		int now = dolphin.tickCount;
		if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
			state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
			return;
		}

		var navigation = dolphin.getNavigation();
		Vec3 cached = state.cachedR02Route(threat.getUUID(), now);
		Path path = null;
		if (cached != null && CreeperEscapePositions.isSafe(cached, threat)) {
			path = navigation.createPath(BlockPos.containing(cached), 0);
		}
		if (path == null || !path.canReach()
			|| !CreeperEscapePositions.isSafe(Vec3.atBottomCenterOf(path.getTarget()), threat)) {
			path = CreeperEscapePositions.findDolphinPath(dolphin, threat);
		}

		Path activePath = navigation.getPath();
		boolean continuingPath = path != null
			&& path.canReach()
			&& activePath != null
			&& !activePath.isDone()
			&& path.getTarget().equals(activePath.getTarget());
		if (path == null || (!continuingPath && !navigation.moveTo(path, SPEED))) {
			state.cacheR02RouteFailure(threat.getUUID(), "NO_JOINT_LIQUID_ROUTE", now + ROUTE_RETRY_TICKS);
			state.setR02Result(NO_SAFE_ROUTE, "NO_JOINT_LIQUID_ROUTE");
			return;
		}

		if (continuingPath) {
			path = activePath;
		}
		state.cacheR02Route(threat.getUUID(), Vec3.atBottomCenterOf(path.getTarget()), now + ROUTE_RETRY_TICKS);
		state.setR02Result(COMMAND_SET, "");
	}

	private static void escapeCube(
		final AbstractCubeMob cube, final Creeper threat, final ReactiveState state
	) {
		int now = cube.tickCount;
		if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
			state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
			return;
		}

		Vec3 target = state.cachedR02Route(threat.getUUID(), now);
		if (target == null || !CreeperEscapePositions.isSafe(target, threat)) {
			target = CreeperEscapePositions.findSupportedTarget(cube, threat);
		}
		if (target == null) {
			state.cacheR02RouteFailure(threat.getUUID(), NO_SAFE_ROUTE, now + ROUTE_RETRY_TICKS);
			state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
			return;
		}

		state.cacheR02Route(threat.getUUID(), target, now + ROUTE_RETRY_TICKS);
		Vec3 delta = target.subtract(cube.position());
		float direction = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
		CubeMoveControlInvoker control = (CubeMoveControlInvoker)(Object)cube.getMoveControl();
		control.stella$setDirection(direction, true);
		control.stella$setWantedMovement(SPEED);
		state.setR02Result(COMMAND_SET, "");
	}

	private static void escapePhantom(
		final Phantom phantom, final Creeper threat, final ReactiveState state
	) {
		int now = phantom.tickCount;
		if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
			state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
			return;
		}

		Vec3 target = state.cachedR02Route(threat.getUUID(), now);
		if (target == null || !CreeperEscapePositions.isSafe(target, threat)) {
			target = CreeperEscapePositions.findFlightTarget(phantom, threat);
		}
		if (target == null) {
			state.cacheR02RouteFailure(threat.getUUID(), NO_SAFE_ROUTE, now + ROUTE_RETRY_TICKS);
			state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
			return;
		}

		state.cacheR02Route(threat.getUUID(), target, now + ROUTE_RETRY_TICKS);
		((PhantomMoveTargetAccessor)(Object)phantom).stella$setMoveTargetPoint(target);
		state.setR02Result(COMMAND_SET, "");
	}

	private static void escapeBat(final Bat bat, final Creeper threat, final ReactiveState state) {
		if (bat.isResting()) {
			bat.setResting(false);
		}

		escapeFlyer(bat, threat, state);
	}

	private static void escapeFlyer(final Mob mob, final Creeper threat, final ReactiveState state) {
		int now = mob.tickCount;
		if (state.hasCachedR02RouteFailure(threat.getUUID(), now)) {
			state.setR02Result(NO_SAFE_ROUTE, state.cachedR02RouteFailure(threat.getUUID(), now));
			return;
		}

		Vec3 target = state.cachedR02Route(threat.getUUID(), now);
		if (target == null || !CreeperEscapePositions.isSafe(target, threat)) {
			target = CreeperEscapePositions.findFlightTarget(mob, threat);
		}
		if (target == null) {
			state.cacheR02RouteFailure(threat.getUUID(), NO_SAFE_ROUTE, now + ROUTE_RETRY_TICKS);
			state.setR02Result(NO_SAFE_ROUTE, NO_SAFE_ROUTE);
			return;
		}

		state.cacheR02Route(threat.getUUID(), target, now + ROUTE_RETRY_TICKS);
		mob.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);
		state.setR02Result(COMMAND_SET, "");
	}

	private static void escapeShulker(
		final Shulker shulker, final Creeper threat, final ReactiveState state
	) {
		if (CreeperEscapePositions.isSafe(shulker.position(), threat)) {
			state.setR02Result(COMMAND_SET, "");
			return;
		}

		Vec3 target = CreeperEscapePositions.findSupportedTarget(shulker, threat);
		if (target == null) {
			state.setR02Result(NO_SAFE_ROUTE, "NO_SHULKER_DESTINATION");
			return;
		}

		BlockPos original = shulker.blockPosition();
		state.setR02ShulkerTarget(BlockPos.containing(target));
		try {
			shulker.move(MoverType.SHULKER_BOX, Vec3.ZERO);
		} finally {
			state.setR02ShulkerTarget(null);
		}

		boolean teleported = !shulker.blockPosition().equals(original);
		state.setR02Result(
			teleported ? COMMAND_SET : NO_SAFE_ROUTE,
			teleported ? "" : "SHULKER_TELEPORT_REJECTED"
		);
	}
}
