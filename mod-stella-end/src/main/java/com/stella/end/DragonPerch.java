package com.stella.end;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * The perch: every 600 to 900 ticks the dragon lands on the portal for 200 ticks and rains breath clouds over the deck.
 * It takes normal damage there. The timer runs while the dragon is awake or passive, but a perch starts only while it is awake. It stands still during a roost.
 */
final class DragonPerch {
	/** The dragon has touched down within this many blocks of the portal point. */
	private static final double TOUCHDOWN = 2.0;
	/** The clouds fall no nearer the rim of the deck disc than this, so they stay inside the parapet. */
	private static final double CLOUD_REACH = 19.0;

	private DragonPerch() {
	}

	/** The delay to the next perch. */
	static int draw(final RandomSource random) {
		return DragonRules.PERCH_MIN + random.nextInt(DragonRules.PERCH_MAX - DragonRules.PERCH_MIN + 1);
	}

	/** Sets the next perch a drawn delay after {@code now}. */
	static void schedule(final DragonState state, final long now, final RandomSource random) {
		state.perchDelay = draw(random);
		state.perchAt = now + state.perchDelay;
	}

	/** Whether the dragon is free to perch and its time has come. */
	static boolean due(final DragonState state, final long now) {
		boolean free = state.mode == DragonState.Mode.PASSIVE || state.mode == DragonState.Mode.AWAKE;
		return free && state.current == null && now >= state.perchAt;
	}

	static void begin(final DragonState state, final long now) {
		state.mode = DragonState.Mode.PERCH;
		state.modeSince = now;
		state.current = null;
		state.launchAt = EndState.NEVER;
		state.path.clear();
		state.perches.add(new DragonState.Perch(now));
		BehaviorLog.info("[END] dragon perch begins clock={}", now);
	}

	private static DragonState.Perch last(final DragonState state) {
		return state.perches.get(state.perches.size() - 1);
	}

	/** Whether the perch has run its 200 ticks from touchdown, or never touched down in time. */
	static boolean over(final DragonState state, final long now) {
		DragonState.Perch perch = last(state);
		return perch.landedAt != EndState.NEVER ? now - perch.landedAt >= DragonRules.PERCH_TICKS : now - perch.startedAt >= DragonRules.PERCH_LAND_LIMIT;
	}

	/** Ends a perch that is under way and sets the next one. */
	static void finish(final DragonState state, final long now, final RandomSource random) {
		interrupt(state, now);
		schedule(state, now, random);
	}

	/** Marks the perch ended, as a roost does when it takes the dragon from the portal. */
	static void interrupt(final DragonState state, final long now) {
		if (state.mode == DragonState.Mode.PERCH && last(state).endedAt == EndState.NEVER) {
			last(state).endedAt = now;
			BehaviorLog.info("[END] dragon perch ends clock={} sat={}", now, last(state).landedAt == EndState.NEVER ? -1 : now - last(state).landedAt);
		}
	}

	/** Flies to the portal and sits there; once down, rains breath clouds over the deck on its schedule. */
	static void tick(final ServerLevel end, final EnderDragon dragon, final DragonState state, final long now) {
		Vec3 portal = Rooftop.roostPoint(end);
		DragonFlight.arrive(dragon, state, portal, DragonFlight.CRUISE * DragonRules.speedMultiplier(state.buffs));
		DragonState.Perch perch = last(state);
		if (perch.landedAt == EndState.NEVER && dragon.position().distanceTo(portal) < TOUCHDOWN) {
			perch.landedAt = now;
			state.nextCloudAt = now;
			BehaviorLog.info("[END] dragon touches down clock={}", now);
		}
		if (perch.landedAt != EndState.NEVER && now >= state.nextCloudAt) {
			state.nextCloudAt = now + DragonRules.PERCH_CLOUD_PERIOD;
			rain(end, dragon, perch);
		}
	}

	private static void rain(final ServerLevel end, final EnderDragon dragon, final DragonState.Perch perch) {
		for (int i = 0; i < DragonRules.PERCH_CLOUD_COUNT; i++) {
			double angle = end.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = CLOUD_REACH * Math.sqrt(end.getRandom().nextDouble());
			AreaEffectCloud cloud = new AreaEffectCloud(end, Math.cos(angle) * reach, Rooftop.DECK_Y + 1.0, Math.sin(angle) * reach);
			cloud.setOwner(dragon);
			cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
			cloud.setRadius(DragonRules.CLOUD_RADIUS);
			DragonBreath.limit(cloud);
			cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
			if (end.addFreshEntity(cloud)) {
				perch.clouds.add(cloud.getUUID());
			}
		}
	}
}
