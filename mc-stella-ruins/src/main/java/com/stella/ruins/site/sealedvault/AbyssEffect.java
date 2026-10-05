package com.stella.ruins.site.sealedvault;

import com.stella.ruins.reward.EffectHook;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Abyssal Edge's effect. A kill leaves a void rift for 60 ticks. The rift pulls hostile mobs within 4 blocks
 * toward it. The rift waits on its own tick counter, because the clock of a game test level barely advances.
 */
public final class AbyssEffect implements EffectHook {
	public static final String ID = "abyss";
	public static final int RIFT_TICKS = 60;
	public static final double RADIUS = 4.0;
	public static final double PULL = 0.16;

	/** A live rift. */
	public record Rift(ServerLevel level, Vec3 at, long until) {
	}

	private static final List<Rift> RIFTS = new ArrayList<>();
	private static long ticks;

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AbyssEffect::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(AbyssEffect::onServerStopping);
	}

	@Override
	public void onKill(final LivingEntity user, final LivingEntity victim) {
		if (victim.level() instanceof ServerLevel level) {
			open(level, victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
		}
	}

	/** Opens a rift at a spot. */
	public static Rift open(final ServerLevel level, final Vec3 at) {
		Rift rift = new Rift(level, at, ticks + RIFT_TICKS);
		synchronized (RIFTS) {
			RIFTS.add(rift);
		}
		return rift;
	}

	public static int openRifts() {
		synchronized (RIFTS) {
			return RIFTS.size();
		}
	}

	public static void onServerStopping(final MinecraftServer server) {
		synchronized (RIFTS) {
			RIFTS.removeIf(rift -> rift.level().getServer() == server);
		}
	}

	/** One tick of every rift. Tests call it directly. */
	public static void tick() {
		tick(null);
	}

	private static void tick(final MinecraftServer server) {
		ticks++;
		List<Rift> live;
		synchronized (RIFTS) {
			RIFTS.removeIf(rift -> rift.until() <= ticks
				|| (server != null && rift.level().getServer() != server));
			live = List.copyOf(RIFTS);
		}
		for (Rift rift : live) {
			pull(rift);
		}
	}

	private static void pull(final Rift rift) {
		AABB area = new AABB(rift.at(), rift.at()).inflate(RADIUS);
		for (Mob mob : rift.level().getEntitiesOfClass(Mob.class, area, entity -> entity instanceof Enemy)) {
			Vec3 toward = rift.at().subtract(mob.position().add(0.0, mob.getBbHeight() / 2.0, 0.0));
			if (toward.lengthSqr() > RADIUS * RADIUS || toward.lengthSqr() < 0.04) {
				continue;
			}
			mob.setDeltaMovement(mob.getDeltaMovement().add(toward.normalize().scale(PULL)));
			mob.needsSync = true;
		}
		rift.level().sendParticles(ParticleTypes.REVERSE_PORTAL, rift.at().x, rift.at().y, rift.at().z, 6, 0.5, 0.5, 0.5, 0.2);
	}
}
