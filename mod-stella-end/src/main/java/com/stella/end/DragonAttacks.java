package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The dragon's four attacks: a swoop through a player, a breath barrage at the roof, a dive onto it, and an airdrop of
 * skeletons. The swoop and the dive begin with a wind-up in which the dragon hovers.
 */
final class DragonAttacks {
	private static final double STRIKE_HALF = 5.0;
	private static final double STRIKE_BELOW = 2.0;
	private static final double STRIKE_ABOVE = 7.0;
	private static final double RUN_UP = 14.0;
	private static final double RUN_OUT = 26.0;
	private static final double CLIMB = 18.0;
	private static final int SWOOP_LIMIT = 160;
	private static final int DIVE_LIMIT = 100;
	private static final int DIVE_REST = 25;
	/** The dive lands no nearer the edge than this many blocks from the roof's middle, so its body stays over the deck. */
	private static final double DIVE_REACH = 10.0;
	private static final double BARRAGE_SCATTER = 7.0;
	/** The barrage lands no nearer the rim of the deck disc than this, so its fire stays inside the parapet. */
	private static final double BARRAGE_REACH = 19.0;
	private static final double SPIRAL_RADIUS = 9.0;
	private static final double SPIRAL_TURN = 0.35;
	private static final int SPIRAL_ARMS = 2;
	private static final int SPIRAL_RISE = 50;
	/** The dragon brakes from its cruise in this many ticks, and hangs still after. */
	private static final int BRAKE_TICKS = 20;
	private static final double SPIRAL_HEIGHT = 8.0;
	private static final double AIRDROP_REACH = 4.0;
	private static final int AIRDROP_LIMIT = 150;
	private static final int AIRDROP_REST = 30;

	private DragonAttacks() {
	}


	/** Picks and begins an attack at the nearest player on the roof. Does nothing when nobody stands there. */
	static void begin(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		ServerPlayer target = nearest(dragon, Rooftop.players(end));
		if (target == null) {
			return;
		}
		DragonState.Attack attack = state.forced != null ? state.forced : state.pick(end.getRandom().nextInt(state.totalWeight()));
		state.forced = null;
		state.current = attack;
		state.attackStartedAt = now;
		state.pathIndex = 0;
		state.path.clear();
		state.attacks.add(new DragonState.AttackRecord(attack, now));
		state.nextShotAt = now;
		state.landedAt = EndState.NEVER;
		state.droppedAt = EndState.NEVER;
		state.impact = null;
		state.launchAt = EndState.NEVER;
		state.hold = null;
		switch (attack) {
			case SWOOP, DIVE -> {
				state.launchAt = now + DragonRules.WINDUP_TICKS;
				state.windups.add(new DragonState.Windup(attack, now));
			}
			case BARRAGE -> state.shotsLeft = DragonRules.barrageShots(state.buffs);
			case AIRDROP -> state.path.add(Rooftop.hoverPoint());
		}
		BehaviorLog.info("[END] dragon attack={} at={} target={}", attack, now, target.getName().getString());
	}

	private static @Nullable ServerPlayer nearest(final EnderDragon dragon, final List<ServerPlayer> players) {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : players) {
			double distance = player.distanceToSqr(dragon);
			if (distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	static boolean step(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		if (state.current == null) {
			return false;
		}
		double speed = DragonRules.speedMultiplier(state.buffs);
		if (state.launchAt != EndState.NEVER) {
			if (now < state.launchAt) {
				windUp(end, dragon, state, now, speed);
				return true;
			}
			if (!launch(end, dragon, state, now)) {
				state.current = null;
				state.path.clear();
				return false;
			}
		}
		boolean going = switch (state.current) {
			case SWOOP -> swoop(end, dragon, state, now, speed);
			case BARRAGE -> barrage(end, dragon, state, now, speed);
			case DIVE -> dive(end, dragon, state, now, speed);
			case AIRDROP -> airdrop(end, dragon, state, now, speed);
		};
		if (!going) {
			state.current = null;
			state.path.clear();
		}
		return going;
	}


	/** The dragon hangs where it is and winds a spiral of End particles round itself until the attack starts. */
	private static void windUp(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final double speed) {
		DragonState.Windup windup = state.windups.get(state.windups.size() - 1);
		long age = now - windup.startedAt;
		if (age < BRAKE_TICKS) {
			DragonFlight.arrive(dragon, state, dragon.position(), DragonFlight.CRUISE * speed);
		} else {
			// The game's own hover phase keeps pulling it a little toward its first hover point.
			// So the controller holds it where the braking left it.
			if (state.hold == null) {
				state.hold = dragon.position();
			}
			dragon.setPos(state.hold);
			state.velocity = Vec3.ZERO;
			dragon.setDeltaMovement(Vec3.ZERO);
		}
		Vec3 centre = dragon.position();
		int reached = 0;
		for (int arm = 0; arm < SPIRAL_ARMS; arm++) {
			double angle = age * SPIRAL_TURN + Math.PI * 2.0 * arm / SPIRAL_ARMS;
			double tighten = 1.0 - 0.5 * Math.min(1.0, (double) age / DragonRules.WINDUP_TICKS);
			double rise = (age % SPIRAL_RISE) / (double) SPIRAL_RISE * SPIRAL_HEIGHT;
			double x = centre.x + Math.cos(angle) * SPIRAL_RADIUS * tighten;
			double y = centre.y - 2.0 + rise;
			double z = centre.z + Math.sin(angle) * SPIRAL_RADIUS * tighten;
			reached += end.sendParticles(ParticleTypes.PORTAL, true, true, x, y, z, 6, 0.3, 0.3, 0.3, 0.05);
			reached += end.sendParticles(ParticleTypes.END_ROD, true, true, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		windup.bursts++;
		windup.sent += reached;
	}

	/** Starts the swoop or the dive the wind-up led to, at the player nearest now. False, and nothing started, when nobody stands on the roof. */
	private static boolean launch(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		state.launchAt = EndState.NEVER;
		state.hold = null;
		ServerPlayer target = nearest(dragon, Rooftop.players(end));
		if (target == null) {
			return false;
		}
		state.windups.get(state.windups.size() - 1).launchedAt = now;
		state.attackStartedAt = now;
		state.pathIndex = 0;
		state.path.clear();
		state.nextShotAt = now;
		switch (state.current) {
			case SWOOP -> beginSwoop(state, dragon, target, now);
			case DIVE -> beginDive(state, target);
			default -> {
			}
		}
		BehaviorLog.info("[END] dragon {} starts after its wind-up at={} target={}", state.current, now, target.getName().getString());
		return true;
	}

	private static void beginSwoop(final DragonState state, final EnderDragon dragon, final ServerPlayer target, final long now) {
		Vec3 aim = new Vec3(target.getX(), Math.max(target.getY() + 1.0, Rooftop.DECK_Y + 2.0), target.getZ());
		Vec3 run = new Vec3(aim.x - dragon.getX(), 0.0, aim.z - dragon.getZ());
		run = run.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : run.normalize();
		state.path.add(new Vec3(aim.x - run.x * RUN_UP, aim.y + 2.0, aim.z - run.z * RUN_UP));
		state.path.add(aim);
		state.path.add(new Vec3(aim.x + run.x * RUN_OUT, aim.y, aim.z + run.z * RUN_OUT));
		state.path.add(new Vec3(aim.x + run.x * (RUN_OUT + 12.0), aim.y + CLIMB, aim.z + run.z * (RUN_OUT + 12.0)));
		state.struck = new ArrayList<>();
		state.swoops.add(new DragonState.Swoop(now, state.struck, new ArrayList<>(), DragonRules.swoopSlownessAmplifier(state.buffs)));
	}

	private static boolean swoop(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final double speed) {
		if (state.pathIndex >= state.path.size() || now - state.attackStartedAt > SWOOP_LIMIT) {
			return false;
		}
		Vec3 waypoint = state.path.get(state.pathIndex);
		DragonFlight.rush(dragon, state, waypoint, DragonFlight.RUSH * speed);
		if (dragon.position().distanceTo(waypoint) < DragonFlight.RUSH * speed * 1.2) {
			state.pathIndex++;
		}
		strike(end, dragon, state);
		return true;
	}

	private static void strike(final ServerLevel end, final EnderDragon dragon, final DragonState state) {
		DragonState.Swoop swoop = state.swoops.get(state.swoops.size() - 1);
		Vec3 at = dragon.position();
		AABB body = new AABB(at.x - STRIKE_HALF, at.y - STRIKE_BELOW, at.z - STRIKE_HALF, at.x + STRIKE_HALF, at.y + STRIKE_ABOVE, at.z + STRIKE_HALF);
		for (LivingEntity victim : end.getEntitiesOfClass(LivingEntity.class, body, entity -> fair(entity) && !swoop.struck().contains(entity.getUUID()))) {
			float blow = DragonRules.SWOOP_DAMAGE * DragonRules.damageMultiplier(state.buffs);
			victim.hurtServer(end, end.damageSources().mobAttack(dragon), blow);
			swoop.blows().add(blow);
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DragonRules.SWOOP_SLOW_TICKS, swoop.slownessAmplifier()), dragon);
			swoop.struck().add(victim.getUUID());
		}
	}

	/** Whether the dragon's attacks touch a creature: not its own side, and not a player the game keeps out of fights. */
	static boolean fair(final LivingEntity entity) {
		if (!entity.isAlive() || entity.entityTags().contains(EndDragons.SIDE_TAG) || entity instanceof EnderDragon) {
			return false;
		}
		return !(entity instanceof ServerPlayer player) || !(player.isSpectator() || player.isCreative());
	}


	private static boolean barrage(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final double speed) {
		DragonFlight.arrive(dragon, state, dragon.position(), DragonFlight.CRUISE * speed);
		if (state.shotsLeft <= 0) {
			return false;
		}
		if (now >= state.nextShotAt) {
			Vec3 mouth = dragon.head.position();
			Vec3 point = roofPoint(end);
			DragonFireball fireball = new DragonFireball(end, dragon, point.subtract(mouth).normalize());
			fireball.setPos(mouth);
			if (end.addFreshEntity(fireball)) {
				state.shots.add(new DragonState.Shot(fireball.getUUID(), now));
			}
			state.shotsLeft--;
			state.nextShotAt = now + DragonRules.BARRAGE_GAP;
		}
		return true;
	}

	/** A point on the deck disc near a player on the roof, or anywhere on the disc when nobody stands there. */
	private static Vec3 roofPoint(final ServerLevel end) {
		List<ServerPlayer> players = Rooftop.players(end);
		double x;
		double z;
		if (players.isEmpty()) {
			double angle = end.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = BARRAGE_REACH * Math.sqrt(end.getRandom().nextDouble());
			x = Math.cos(angle) * reach;
			z = Math.sin(angle) * reach;
		} else {
			ServerPlayer near = players.get(end.getRandom().nextInt(players.size()));
			x = near.getX() + end.getRandom().triangle(0.0, BARRAGE_SCATTER);
			z = near.getZ() + end.getRandom().triangle(0.0, BARRAGE_SCATTER);
		}
		return onDisc(x, z, BARRAGE_REACH);
	}

	/** A deck point pulled in along its line to the middle until it lies within {@code reach} of it. */
	private static Vec3 onDisc(final double x, final double z, final double reach) {
		double distance = Math.hypot(x, z);
		double pull = distance > reach ? reach / distance : 1.0;
		return new Vec3(x * pull, Rooftop.DECK_Y + 1.0, z * pull);
	}


	private static void beginDive(final DragonState state, final ServerPlayer target) {
		state.path.add(onDisc(target.getX(), target.getZ(), DIVE_REACH));
	}

	/** The dragon flies over the middle of the roof, lets go of its mobs there, and hangs a moment. */
	private static boolean airdrop(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final double speed) {
		if (state.droppedAt != EndState.NEVER) {
			DragonFlight.arrive(dragon, state, dragon.position(), DragonFlight.CRUISE * speed);
			return now - state.droppedAt < AIRDROP_REST;
		}
		Vec3 over = state.path.get(0);
		DragonFlight.arrive(dragon, state, over, DragonFlight.CRUISE * speed);
		if (Math.hypot(dragon.getX() - over.x, dragon.getZ() - over.z) < AIRDROP_REACH || now - state.attackStartedAt > AIRDROP_LIMIT) {
			state.droppedAt = now;
			DragonAirdrop.drop(end, dragon, state, now);
		}
		return true;
	}

	private static boolean dive(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final double speed) {
		if (state.landedAt != EndState.NEVER) {
			DragonFlight.arrive(dragon, state, state.impact, DragonFlight.CRUISE * speed);
			return now - state.landedAt < DIVE_REST;
		}
		Vec3 ground = state.path.get(0);
		DragonFlight.rush(dragon, state, ground, DragonFlight.RUSH * speed);
		boolean arrived = dragon.position().distanceTo(ground) < 3.0 || dragon.getY() <= Rooftop.DECK_Y + 2.5 || now - state.attackStartedAt > DIVE_LIMIT;
		if (arrived) {
			land(end, dragon, state, now, ground);
		}
		return true;
	}

	/** The dragon hits the roof: everything within eight blocks is thrown up, and with the crystal fire flies out. */
	private static void land(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now, final Vec3 ground) {
		state.landedAt = now;
		state.impact = dragon.position();
		state.velocity = Vec3.ZERO;
		Vec3 impact = state.impact;
		AABB reach = new AABB(impact.x - DragonRules.DIVE_RADIUS, impact.y - DragonRules.DIVE_RADIUS, impact.z - DragonRules.DIVE_RADIUS,
			impact.x + DragonRules.DIVE_RADIUS, impact.y + DragonRules.DIVE_RADIUS, impact.z + DragonRules.DIVE_RADIUS);
		List<UUID> lifted = new ArrayList<>();
		for (LivingEntity entity : end.getEntitiesOfClass(LivingEntity.class, reach, DragonAttacks::fair)) {
			if (Math.hypot(entity.getX() - impact.x, entity.getZ() - impact.z) <= DragonRules.DIVE_RADIUS) {
				Vec3 motion = entity.getDeltaMovement();
				entity.setDeltaMovement(motion.x, DragonRules.DIVE_LIFT, motion.z);
				entity.needsSync = true;
				lifted.add(entity.getUUID());
			}
		}
		List<UUID> charges = new ArrayList<>();
		if (DragonRules.divesThrowFire(state.buffs)) {
			for (int i = 0; i < DragonRules.DIVE_CHARGES; i++) {
				double angle = Math.PI * 2.0 * i / DragonRules.DIVE_CHARGES;
				SmallFireball charge = new SmallFireball(end, dragon, new Vec3(Math.cos(angle), 0.15, Math.sin(angle)));
				charge.setPos(impact.x, impact.y + 1.5, impact.z);
				if (end.addFreshEntity(charge)) {
					charges.add(charge.getUUID());
				}
			}
		}
		state.dives.add(new DragonState.Dive(now, impact, lifted, charges));
		BehaviorLog.info("[END] dragon dive landed at={} lifted={} charges={}", impact, lifted.size(), charges.size());
	}
}
