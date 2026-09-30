package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * R09: a mountable mob spawns with a rider. The rider is always added to the level explicitly, because
 * the carrier pass has already run by the time the load event fires, and the seating waits one tick so
 * the mount's own carrier pass is closed first.
 */
public final class RiderSquad {
	public static final Map<EntityType<?>, EntityType<? extends Mob>> RIDER_TYPES = Map.of(
		EntityTypes.CHICKEN, EntityTypes.ZOMBIE,
		EntityTypes.SPIDER, EntityTypes.SKELETON,
		EntityTypes.STRIDER, EntityTypes.ZOMBIFIED_PIGLIN
	);
	private static final AttachmentType<List<Pending>> QUEUE = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "rider_queue"), ArrayList::new
	);

	private RiderSquad() {
	}

	private record Pending(Mob mount, EntityType<? extends Mob> riderType, boolean armed) {
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the rider queue attachment {}", QUEUE.identifier());
	}

	public static boolean canMount(final Mob mob) {
		return RIDER_TYPES.containsKey(mob.getType()) && !mob.isBaby();
	}

	public static void queue(final Mob mount, final EntityType<? extends Mob> riderType) {
		queue(mount, riderType, false);
	}

	/** R23: the guaranteed rider takes the same path, with a sword placed before the installer reads it. */
	public static void queueArmed(final Mob mount, final EntityType<? extends Mob> riderType) {
		queue(mount, riderType, true);
	}

	private static void queue(final Mob mount, final EntityType<? extends Mob> riderType, final boolean armed) {
		if (mount.level() instanceof ServerLevel level) {
			((AttachmentTarget) level).getAttachedOrCreate(QUEUE).add(new Pending(mount, riderType, armed));
		}
	}

	/** Seats every rider queued on the previous tick, once the level's own entity pass has finished. */
	public static void drain(final ServerLevel level) {
		List<Pending> pending = ((AttachmentTarget) level).getAttached(QUEUE);
		if (pending == null || pending.isEmpty()) {
			return;
		}

		List<Pending> batch = List.copyOf(pending);
		pending.clear();

		for (Pending item : batch) {
			if (item.mount().isAlive() && !item.mount().isRemoved()) {
				seat(level, item.mount(), item.riderType(), item.armed());
			}
		}
	}

	private static void seat(
		final ServerLevel level, final Mob mount, final EntityType<? extends Mob> riderType, final boolean armed
	) {
		Mob rider = riderType.create(level, EntitySpawnReason.JOCKEY);
		if (rider == null) {
			return;
		}

		rider.snapTo(mount.getX(), mount.getY(), mount.getZ(), mount.getYRot(), 0.0F);
		rider.finalizeSpawn(
			level, level.getCurrentDifficultyAt(rider.blockPosition()), EntitySpawnReason.JOCKEY, null
		);
		// The sword is placed before the installer reads the hand, so the role resolves as melee.
		if (armed) {
			rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}

		// Both flags must exist before the add: ENTITY_LOAD fires inside addFreshEntity.
		EliteState.setSummoned(rider, true);
		ReactiveState.get(rider).setGearApplied(true);
		BehaviorInstaller.install(rider);
		level.addFreshEntity(rider);
		rider.startRiding(mount, true, false);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=riders SEAT mount={} rider={} seatPos={},{},{} vehicle={} passengers={}",
			mount.getType(),
			rider.getType(),
			String.format(java.util.Locale.ROOT, "%.2f", rider.getX()),
			String.format(java.util.Locale.ROOT, "%.2f", rider.getY()),
			String.format(java.util.Locale.ROOT, "%.2f", rider.getZ()),
			rider.getVehicle() == mount,
			mount.getPassengers().size()
		);
	}
}
