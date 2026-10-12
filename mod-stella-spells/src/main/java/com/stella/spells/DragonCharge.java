package com.stella.spells;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The fire charge Dragonfall drops: a vanilla item display of a fire charge, twice its model size, that the server
 * lets fall onto the spot over a few ticks and trails with flames. Any client draws it. The spell's damage waits
 * for it: whatever the cast hands over runs on the tick the charge lands, once, and the charge goes with it.
 */
final class DragonCharge {
	/** How much larger than the item model the charge is drawn. */
	static final float SCALE = 2.0F;
	/** How far above the spot the charge starts, and how many ticks it takes to fall. */
	static final double FALL_BLOCKS = 14.0;
	static final int FALL_TICKS = 12;
	/** Marks a charge, so one that a save kept is removed when its chunk loads. */
	static final String TAG = Spellbooks.MOD_ID + ".dragon_charge";
	/** The charge is centred this far over the ground when it lands, which is about its own radius. */
	private static final double CENTRE_LIFT = 1.0;

	private static final List<Falling> FALLING = new CopyOnWriteArrayList<>();
	private static int clock;

	private static final class Falling {
		final ServerLevel level;
		final Display.ItemDisplay charge;
		final UUID owner;
		final Vec3 at;
		final int startedAt;
		final List<Runnable> landing = new ArrayList<>();
		boolean released;

		Falling(final ServerLevel level, final Display.ItemDisplay charge, final UUID owner, final Vec3 at, final int startedAt) {
			this.level = level;
			this.charge = charge;
			this.owner = owner;
			this.at = at;
			this.startedAt = startedAt;
		}
	}

	private DragonCharge() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DragonCharge::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(TAG)
				&& FALLING.stream().noneMatch(falling -> falling.charge == display)) {
				display.discard();
			}
		});
	}

	static void clear() {
		FALLING.forEach(falling -> falling.charge.discard());
		FALLING.clear();
		clock = 0;
	}

	/** Drops a charge for this caster onto this spot; {@code onLand} runs on the tick the charge lands. */
	static void fall(final ServerLevel level, final ServerPlayer caster, final Vec3 at, final Runnable onLand) {
		Display.ItemDisplay charge = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (charge == null) {
			onLand.run();
			return;
		}
		charge.setItemStack(new ItemStack(Items.FIRE_CHARGE));
		charge.setItemTransform(ItemDisplayContext.FIXED);
		charge.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		charge.setBrightnessOverride(Brightness.FULL_BRIGHT);
		charge.setTransformation(offset(FALL_BLOCKS));
		charge.addTag(TAG);
		charge.setPos(at.x, at.y + CENTRE_LIFT, at.z);
		Falling falling = new Falling(level, charge, caster.getUUID(), at, clock);
		falling.landing.add(onLand);
		FALLING.add(falling);
		level.addFreshEntity(charge);
	}

	/**
	 * Adds work to the newest charge this caster has in the air, so it lands right after the spell's own hit.
	 * False when no charge is falling, and then the caller does the work itself.
	 */
	static boolean thenOnLanding(final ServerPlayer caster, final Runnable work) {
		for (int index = FALLING.size() - 1; index >= 0; index--) {
			Falling falling = FALLING.get(index);
			if (falling.owner.equals(caster.getUUID())) {
				falling.landing.add(work);
				return true;
			}
		}
		return false;
	}

	/** Test seam: lands every charge this caster has in the air at once. */
	static void landNow(final ServerPlayer caster) {
		for (Falling falling : FALLING) {
			if (falling.owner.equals(caster.getUUID())) {
				FALLING.remove(falling);
				land(falling);
			}
		}
	}

	// The charge at this height over its landing, at twice the model's size.
	private static Transformation offset(final double height) {
		return new Transformation(new Vector3f(0.0F, (float) height, 0.0F), new Quaternionf(), new Vector3f(SCALE, SCALE, SCALE), new Quaternionf());
	}

	private static void tick(final MinecraftServer server) {
		int now = ++clock;
		for (Falling falling : FALLING) {
			int age = now - falling.startedAt;
			if (falling.level.getServer() != server) {
				FALLING.remove(falling);
				continue;
			}
			// A charge whose picture is gone still lands: the spell's hit does not depend on it.
			boolean drawn = !falling.charge.isRemoved();
			// The first tick sends the charge as it starts; the next tells the client to carry it down in one slide.
			if (drawn && !falling.released && age >= 1) {
				falling.charge.setTransformationInterpolationDelay(0);
				falling.charge.setTransformationInterpolationDuration(FALL_TICKS);
				falling.charge.setTransformation(offset(0.0));
				falling.released = true;
			}
			double fallen = Math.min(1.0, Math.max(0, age - 1) / (double) FALL_TICKS);
			Vec3 centre = falling.at.add(0.0, CENTRE_LIFT + FALL_BLOCKS * (1.0 - fallen), 0.0);
			if (drawn) {
				falling.level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 6, 0.5, 0.5, 0.5, 0.02);
				falling.level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y + 0.6, centre.z, 3, 0.4, 0.3, 0.4, 0.01);
				falling.level.sendParticles(ParticleTypes.LAVA, centre.x, centre.y, centre.z, 1, 0.4, 0.4, 0.4, 0.0);
			}
			if (age >= 1 + FALL_TICKS) {
				FALLING.remove(falling);
				land(falling);
			}
		}
	}

	// The hit lands first, so the burst on screen is the burst of the damage.
	private static void land(final Falling falling) {
		try {
			falling.landing.forEach(Runnable::run);
		} finally {
			Vec3 at = falling.at;
			falling.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			falling.level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 1.2, 0.3, 1.2, 0.08);
			falling.level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
			falling.charge.discard();
		}
	}
}
