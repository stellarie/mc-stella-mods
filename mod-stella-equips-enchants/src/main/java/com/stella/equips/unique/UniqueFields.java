package com.stella.equips.unique;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Dragon-breath fields. A field lasts a number of server ticks and, every 20 ticks, hurts the foes inside it with
 * armor-piercing unique damage. A slice may add its own effect to each pulse through the second-callback.
 */
public final class UniqueFields {
	/** A view of a live field. */
	public record Field(ServerLevel level, Vec3 center, double radius, int ticksLeft, float perSecond) {
	}

	private static final int SECOND = 20;
	private static final int PARTICLE_INTERVAL = 4;

	private static final class Live {
		private final ServerLevel level;
		private final Vec3 center;
		private final double radius;
		private final float perSecond;
		private final ServerPlayer from;
		private final @Nullable Consumer<List<LivingEntity>> onSecond;
		private int age;
		private int ticksLeft;

		private Live(final ServerLevel level, final Vec3 center, final double radius, final int ticks, final float perSecond, final ServerPlayer from,
			final @Nullable Consumer<List<LivingEntity>> onSecond) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.ticksLeft = ticks;
			this.perSecond = perSecond;
			this.from = from;
			this.onSecond = onSecond;
		}
	}

	private static final List<Live> FIELDS = new ArrayList<>();

	private UniqueFields() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(UniqueFields::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIELDS.clear());
	}

	/** A dragon-breath field of the given radius and length that deals {@code perSecond} to foes inside every 20 ticks. */
	public static void breath(final ServerLevel level, final Vec3 center, final double radius, final int ticks, final float perSecond, final ServerPlayer from) {
		field(level, center, radius, ticks, perSecond, from, null);
	}

	/** A breath field that also tells a callback which foes stood inside at each pulse, for an extra effect such as a pull. */
	public static void field(final ServerLevel level, final Vec3 center, final double radius, final int ticks, final float perSecond, final ServerPlayer from,
		final @Nullable Consumer<List<LivingEntity>> onSecond) {
		FIELDS.add(new Live(level, center, radius, ticks, perSecond, from, onSecond));
	}

	public static List<Field> active() {
		List<Field> views = new ArrayList<>();
		for (Live live : FIELDS) {
			views.add(new Field(live.level, live.center, live.radius, live.ticksLeft, live.perSecond));
		}
		return views;
	}

	private static void tick(final MinecraftServer server) {
		if (FIELDS.isEmpty()) {
			return;
		}
		for (Live live : List.copyOf(FIELDS)) {
			live.age++;
			live.ticksLeft--;
			if (live.age % PARTICLE_INTERVAL == 0) {
				live.level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), live.center.x, live.center.y + 0.2, live.center.z,
					Math.max(3, (int) (live.radius * 4)), live.radius / 2.0, 0.1, live.radius / 2.0, 0.01);
			}
			if (live.age % SECOND == 0) {
				pulse(live);
			}
			if (live.ticksLeft <= 0) {
				FIELDS.remove(live);
			}
		}
	}

	private static void pulse(final Live live) {
		double reach = live.radius * live.radius;
		AABB box = new AABB(live.center, live.center).inflate(live.radius);
		List<LivingEntity> foes = live.level.getEntitiesOfClass(LivingEntity.class, box,
			entity -> UniqueTargets.isFoe(live.from, entity) && entity.getBoundingBox().distanceToSqr(live.center) <= reach);
		for (LivingEntity foe : foes) {
			UniqueDamage.deal(live.level, foe, live.from, live.perSecond, true);
		}
		if (live.onSecond != null) {
			live.onSecond.accept(foes);
		}
	}
}
