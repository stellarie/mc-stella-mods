package com.stella.mobs.entity;

import com.stella.mobs.StellaMobs;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * The skeleton worker: a mineshaft skeleton with a gold helmet and an iron pickaxe. Its melee hit
 * freezes the target in place for {@link #SLOW_TICKS} ticks.
 */
public final class SkeletonWorker extends Skeleton {
	/** A quarter of a second. */
	public static final int SLOW_TICKS = 5;
	/** Multiplies the movement speed by zero. */
	public static final double SLOW_AMOUNT = -1.0;
	public static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "worker_full_stop");

	private static final Map<UUID, Slowed> SLOWED = new HashMap<>();

	private static final class Slowed {
		private final LivingEntity entity;
		private int ticksLeft;

		private Slowed(final LivingEntity entity) {
			this.entity = entity;
			this.ticksLeft = SLOW_TICKS;
		}
	}

	public SkeletonWorker(final EntityType<? extends Skeleton> type, final Level level) {
		super(type, level);
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SkeletonWorker::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> tickSlowed());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SLOWED.clear());
	}

	/** Whether the entity holds the full-stop modifier now. */
	public static boolean isHeld(final LivingEntity entity) {
		return SLOWED.containsKey(entity.getUUID());
	}

	@Override
	protected void populateDefaultEquipmentSlots(final RandomSource random, final DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
	}

	@Override
	protected void populateDefaultEquipmentEnchantments(
		final ServerLevelAccessor level, final RandomSource random, final DifficultyInstance difficulty
	) {
	}

	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float dealt, final float taken, final boolean blocked
	) {
		if (!(source.getDirectEntity() instanceof SkeletonWorker) || taken <= 0.0F || victim.level().isClientSide()) {
			return;
		}

		AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.addOrUpdateTransientModifier(
			new AttributeModifier(SLOW_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
		);
		SLOWED.put(victim.getUUID(), new Slowed(victim));
	}

	private static void tickSlowed() {
		Iterator<Slowed> iterator = SLOWED.values().iterator();
		while (iterator.hasNext()) {
			Slowed slowed = iterator.next();
			LivingEntity entity = slowed.entity;
			if (entity.isRemoved()) {
				iterator.remove();
				continue;
			}

			// The modifier zeroes walking. The clamp also stops a push that arrived in the same tick.
			Vec3 motion = entity.getDeltaMovement();
			entity.setDeltaMovement(0.0, motion.y, 0.0);
			slowed.ticksLeft--;
			if (slowed.ticksLeft <= 0) {
				AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(SLOW_ID);
				}
				iterator.remove();
			}
		}
	}
}
