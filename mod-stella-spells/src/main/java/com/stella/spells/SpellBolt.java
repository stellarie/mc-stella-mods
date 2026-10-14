package com.stella.spells;

import com.mojang.math.Transformation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The body a bolt spell shows in flight: a vanilla item display that the server slides from the caster to the end
 * of the line, with a thin trail of particles behind it. Any client draws it. It is only a picture. The spell has
 * already hit by the time the body starts, and nothing here changes damage, range, cooldown or targets.
 */
final class SpellBolt {
	/** How large the body is drawn against the item model. */
	static final float SCALE = 0.7F;
	static final double BLOCKS_PER_TICK = 3.0;
	static final int MIN_FLIGHT_TICKS = 2;
	static final int MAX_FLIGHT_TICKS = 8;
	/** Marks a body, so one that a save kept is removed when its chunk loads. */
	static final String TAG = Spellbooks.MOD_ID + ".bolt";
	private static final int MAX_IN_FLIGHT = 64;
	private static final double TRAIL_STEP_BLOCKS = 0.5;
	/** The body leaves the caster this far ahead, so it does not start inside the caster's head. */
	private static final double MUZZLE_BLOCKS = 0.5;

	private static final List<Flying> FLYING = new CopyOnWriteArrayList<>();

	private static final class Flying {
		final ServerLevel level;
		final ServerPlayer caster;
		final Identifier spell;
		final Display.ItemDisplay body;
		final ParticleOptions trail;
		final Vec3 from;
		final Vec3 delta;
		final int ticks;
		int age;

		Flying(
			final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Display.ItemDisplay body,
			final ParticleOptions trail, final Vec3 from, final Vec3 delta, final int ticks
		) {
			this.level = level;
			this.caster = caster;
			this.spell = spell;
			this.body = body;
			this.trail = trail;
			this.from = from;
			this.delta = delta;
			this.ticks = ticks;
		}
	}

	private SpellBolt() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpellBolt::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(TAG)
				&& FLYING.stream().noneMatch(flying -> flying.body == display)) {
				display.discard();
			}
		});
	}

	static void clear() {
		FLYING.forEach(flying -> flying.body.discard());
		FLYING.clear();
	}

	/** Flies the body of this item from just ahead of {@code eye} to {@code end}, trailed by {@code trail}. */
	static void fly(
		final ServerLevel level, final ServerPlayer caster, final Identifier spell, final Item item,
		final ParticleOptions trail, final Vec3 eye, final Vec3 end
	) {
		Vec3 line = end.subtract(eye);
		double length = line.length();
		if (length < 1.0e-3 || FLYING.size() >= MAX_IN_FLIGHT) {
			return;
		}
		Vec3 from = eye.add(line.scale(Math.min(MUZZLE_BLOCKS, length) / length));
		Vec3 delta = end.subtract(from);
		Display.ItemDisplay body = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (body == null) {
			return;
		}
		int ticks = Math.max(MIN_FLIGHT_TICKS, Math.min(MAX_FLIGHT_TICKS, (int) Math.ceil(delta.length() / BLOCKS_PER_TICK)));
		body.setItemStack(new ItemStack(item));
		body.setItemTransform(ItemDisplayContext.FIXED);
		body.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		body.setBrightnessOverride(Brightness.FULL_BRIGHT);
		body.setTransformation(shift(Vec3.ZERO));
		body.addTag(TAG);
		body.setPos(from.x, from.y, from.z);
		FLYING.add(new Flying(level, caster, spell, body, trail, from, delta, ticks));
		level.addFreshEntity(body);
	}

	private static Transformation shift(final Vec3 offset) {
		return new Transformation(
			new Vector3f((float) offset.x, (float) offset.y, (float) offset.z), new Quaternionf(),
			new Vector3f(SCALE, SCALE, SCALE), new Quaternionf()
		);
	}

	private static void tick(final MinecraftServer server) {
		for (Flying flying : FLYING) {
			if (flying.level.getServer() != server) {
				FLYING.remove(flying);
				flying.body.discard();
				continue;
			}
			flying.age++;
			boolean drawn = !flying.body.isRemoved();
			// The first tick sends the body as it starts. The next tells the client to carry it along in one slide.
			if (drawn && flying.age == 1) {
				flying.body.setTransformationInterpolationDelay(0);
				flying.body.setTransformationInterpolationDuration(flying.ticks);
				flying.body.setTransformation(shift(flying.delta));
			}
			if (drawn) {
				trail(flying);
			}
			if (flying.age >= 1 + flying.ticks) {
				FLYING.remove(flying);
				flying.body.discard();
			}
		}
	}

	// Points along the stretch the body covered this tick, so the trail has no gaps at speed.
	private static void trail(final Flying flying) {
		double before = Math.min(1.0, Math.max(0, flying.age - 2) / (double) flying.ticks);
		double now = Math.min(1.0, Math.max(0, flying.age - 1) / (double) flying.ticks);
		double stretch = flying.delta.length() * (now - before);
		int points = Math.max(1, (int) Math.ceil(stretch / TRAIL_STEP_BLOCKS));
		for (int i = 1; i <= points; i++) {
			double fraction = before + (now - before) * i / points;
			SpellVisuals.particle(flying.level, flying.caster, flying.spell, flying.trail,
				flying.from.add(flying.delta.scale(fraction)), 1, 0.03, 0.0);
		}
	}
}
