package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The soul charges of the Sovereign and its undead blazes: ordinary small fireballs, marked, slowed to a share of the
 * vanilla speed, and given a wither on hit. A blaze fires its own with its own goal, so a charge is told by its owner.
 */
public final class SoulCharges {
	public static final String TAG = "mod-stella-end.soul_charge";
	/** The acceleration a vanilla small fireball flies with. */
	public static final double VANILLA_ACCELERATION = 0.1;
	public static final double UNDEAD_FACTOR = 0.5;
	public static final double SOVEREIGN_FACTOR = 1.0;
	private static final int KEPT = 256;

	private static final List<UUID> FIRED = new ArrayList<>();

	private SoulCharges() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof SmallFireball fireball && !is(fireball)) {
				Entity owner = fireball.getOwner();
				if (UndeadBlazes.is(owner)) {
					mark(fireball, UNDEAD_FACTOR);
				} else if (UndeadSovereign.is(owner)) {
					mark(fireball, SOVEREIGN_FACTOR);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamageTaken, damageTaken, blocked) -> {
			if (blocked || baseDamageTaken <= 0.0F) {
				return;
			}
			Entity attacker = source.getEntity();
			if (UndeadSovereign.is(attacker)) {
				wither(victim, UndeadSovereign.WITHER_AMPLIFIER, UndeadSovereign.WITHER_TICKS);
			} else if (UndeadBlazes.is(attacker) && is(source.getDirectEntity())) {
				wither(victim, UndeadBlazes.WITHER_AMPLIFIER, UndeadBlazes.WITHER_TICKS);
			}
		});
	}

	private static void wither(final LivingEntity victim, final int amplifier, final int ticks) {
		victim.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, amplifier));
		BehaviorLog.info("[END] wither victim={} amplifier={} ticks={}", victim.getType().getDescriptionId(), amplifier, ticks);
	}

	/** Slows a fresh fireball to a share of the vanilla speed and gives it the look and the mark of a soul charge. */
	private static void mark(final SmallFireball fireball, final double factor) {
		fireball.addTag(TAG);
		fireball.accelerationPower = VANILLA_ACCELERATION * factor;
		fireball.setDeltaMovement(fireball.getDeltaMovement().scale(factor));
		fireball.setItem(new ItemStack(Items.SOUL_TORCH));
		FIRED.add(fireball.getUUID());
		if (FIRED.size() > KEPT) {
			FIRED.remove(0);
		}
	}

	/** Fires a charge at a speed share of the vanilla one. Null when the level refuses it. */
	public static @Nullable SmallFireball fire(final ServerLevel end, final LivingEntity shooter, final Vec3 direction, final double factor) {
		SmallFireball charge = new SmallFireball(end, shooter, direction.normalize());
		charge.setPos(shooter.getX(), shooter.getY() + shooter.getBbHeight() * 0.5, shooter.getZ());
		if (!end.addFreshEntity(charge)) {
			return null;
		}
		if (!is(charge)) {
			mark(charge, factor);
		}
		return charge;
	}

	public static boolean is(final @Nullable Entity entity) {
		return entity != null && entity.entityTags().contains(TAG);
	}

	/** Every charge marked since the last reset, in order. */
	public static List<UUID> fired() {
		return List.copyOf(FIRED);
	}

	public static void reset() {
		FIRED.clear();
	}
}
