package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A weapon rune's signature, by its element: what a hit does beyond its tier. Fire builds heat to a burst,
 * Ice and Wind strike harder at the slowed and the airborne, Water soaks, Lightning stores charges for a
 * spell, Void shrinks, Holy strips a boss floor's buff, and Dark shreds armor.
 */
public final class RuneEffects {
	public static final int HEAT_STACKS = 5;
	public static final int COMBUST_REST_TICKS = 60;
	public static final int WET_TICKS = 100;
	public static final double WET_LIGHTNING = 1.25;
	public static final double SHRINK_CAP = 0.5;
	public static final int SHRED_STACKS = 10;
	public static final int HOLY_REST_TICKS = 200;
	public static final int MAX_CHARGES = 5;

	private static final AttachmentType<Integer> HEAT = AttachmentRegistry.create(StellaTower.id("rune_heat"));
	private static final AttachmentType<Integer> BURST_AT = AttachmentRegistry.create(StellaTower.id("rune_burst_at"));
	private static final AttachmentType<Integer> WET_UNTIL = AttachmentRegistry.create(StellaTower.id("rune_wet_until"));
	private static final AttachmentType<Integer> CHARGES = AttachmentRegistry.create(StellaTower.id("rune_charges"));
	private static final AttachmentType<Integer> SHRINK = AttachmentRegistry.create(StellaTower.id("rune_shrink"));
	private static final AttachmentType<Integer> SHRED = AttachmentRegistry.create(StellaTower.id("rune_shred"));
	private static final AttachmentType<Integer> HOLY_READY = AttachmentRegistry.create(StellaTower.id("rune_holy_ready"));
	private static final AttachmentType<List<TowerBuffs.MobBuff>> STRIPPED = AttachmentRegistry.create(StellaTower.id("rune_stripped"));
	private static final Identifier SHRINK_ID = StellaTower.id("void_shrink");
	private static final Identifier SHRED_ID = StellaTower.id("dark_shred");

	private RuneEffects() {
	}

	public static void register() {
		// What a hit leaves behind waits until it has landed, so a blow turned aside builds nothing.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked || taken <= 0.0F || !(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)
				|| !TowerRunes.struckWith(source) || TowerRunes.slotOf(source.getWeaponItem()) != TowerRunes.Slot.WEAPON) {
				return;
			}
			RuneData rune = TowerRunes.runeOf(source.getWeaponItem());
			if (rune != null) {
				afterHit(level, entity, player, rune, source.getWeaponItem());
			}
		});
	}

	private static int now(final LivingEntity entity) {
		return entity.level().getServer() == null ? entity.tickCount : entity.level().getServer().getTickCount();
	}

	private static void afterHit(final ServerLevel level, final LivingEntity target, final ServerPlayer player, final RuneData rune, final net.minecraft.world.item.ItemStack weapon) {
		double power = TowerRunes.power(weapon, rune);
		int now = now(target);
		switch (rune.element()) {
			case FIRE -> {
				int heat = Math.min(HEAT_STACKS, target.getAttachedOrElse(HEAT, 0) + 1);
				Integer burstAt = target.getAttached(BURST_AT);
				if (heat >= HEAT_STACKS && (burstAt == null || now - burstAt >= COMBUST_REST_TICKS)) {
					target.setAttached(HEAT, 0);
					target.setAttached(BURST_AT, now);
					burst(level, target, combustShare(power));
				} else {
					target.setAttached(HEAT, heat);
				}
			}
			case WATER -> {
				target.setAttached(WET_UNTIL, now + WET_TICKS);
				target.clearFire();
				level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
			}
			case LIGHTNING -> addCharge(player);
			case VOID -> {
				int stacks = target.getAttachedOrElse(SHRINK, 0) + 1;
				target.setAttached(SHRINK, stacks);
				double shrink = Math.min(SHRINK_CAP, stacks * shrinkPerHit(power));
				AttributeInstance health = target.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.removeModifier(SHRINK_ID);
					health.addTransientModifier(new AttributeModifier(SHRINK_ID, -shrink, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					if (target.getHealth() > target.getMaxHealth()) {
						target.setHealth(target.getMaxHealth());
					}
				}
			}
			case DARK -> {
				int stacks = Math.min(SHRED_STACKS, target.getAttachedOrElse(SHRED, 0) + 1);
				target.setAttached(SHRED, stacks);
				AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
				if (armor != null) {
					armor.removeModifier(SHRED_ID);
					armor.addTransientModifier(new AttributeModifier(SHRED_ID, -stacks * shredPerHit(power), AttributeModifier.Operation.ADD_VALUE));
				}
			}
			case HOLY -> {
				if (now < player.getAttachedOrElse(HOLY_READY, Integer.MIN_VALUE)) {
					return;
				}
				List<TowerBuffs.MobBuff> buffs = TowerBuffs.mobBuffsOf(target);
				if (!buffs.isEmpty()) {
					TowerBuffs.MobBuff struck = buffs.get(0);
					List<TowerBuffs.MobBuff> stripped = new ArrayList<>(strippedFrom(target));
					stripped.add(struck);
					target.setAttached(STRIPPED, List.copyOf(stripped));
					if (struck.effect() != null) {
						target.removeEffect(struck.effect());
					}
				} else {
					// Anywhere else, the strongest boon the target holds.
					MobEffectInstance boon = target.getActiveEffects().stream()
						.filter(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL)
						.max(java.util.Comparator.comparingInt(MobEffectInstance::getAmplifier)).orElse(null);
					if (boon == null) {
						return;
					}
					target.removeEffect(boon.getEffect());
				}
				player.setAttached(HOLY_READY, now + HOLY_REST_TICKS);
				level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 16, 0.3, 0.6, 0.3, 0.05);
			}
			default -> {
			}
		}
	}

	/** Fire's burst: its own blow, a share of the target's whole health, the tower's size included. */
	public static final ResourceKey<DamageType> BURST = ResourceKey.create(Registries.DAMAGE_TYPE, StellaTower.id("rune_burst"));

	/**
	 * Fire's burst: a share of the target's whole health, the tower's size included, as a blow of its own.
	 * It is not fire, so the fire-born burn too, and armor, resistance and enchantments do not blunt it. The
	 * blow that set it off keeps its hold on the target, so the next hits land as they would have.
	 */
	private static void burst(final ServerLevel level, final LivingEntity target, final double share) {
		TowerMobs.State state = TowerMobs.stateOf(target);
		double whole = target.getMaxHealth() * (state == null ? 1.0 : state.healthDivisor() * TowerBuffs.healthFactor(target));
		int cooldown = target.damageCooldownTime;
		float lastHurt = ((com.stella.tower.mixin.LivingEntityAccessor) target).stellaTower$lastHurt();
		target.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BURST)), (float) (share * whole));
		target.damageCooldownTime = cooldown;
		((com.stella.tower.mixin.LivingEntityAccessor) target).stellaTower$setLastHurt(lastHurt);
		level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Lightning on the wet: a quarter more. */
	public static float soaked(final LivingEntity victim, final DamageSource source, final float amount) {
		return isWet(victim) && source.is(DamageTypeTags.IS_LIGHTNING) ? (float) (amount * WET_LIGHTNING) : amount;
	}

	public static double combustShare(final double power) {
		return 0.05 + power / 2000.0;
	}

	public static double iceBonus(final double power) {
		return 0.20 + power / 1000.0;
	}

	public static double windBonus(final double power) {
		return 0.20 + power / 1000.0;
	}

	public static double shrinkPerHit(final double power) {
		return 0.005 + power / 20000.0;
	}

	public static double shredPerHit(final double power) {
		return 1.0 + power / 50.0;
	}

	public static int heatOf(final LivingEntity target) {
		return target.getAttachedOrElse(HEAT, 0);
	}

	public static boolean isWet(final LivingEntity target) {
		Integer until = target.getAttached(WET_UNTIL);
		return until != null && now(target) < until;
	}

	public static int chargesOf(final Player player) {
		return player.getAttachedOrElse(CHARGES, 0);
	}

	/** Stores one more Lightning charge, up to the most a player holds. */
	public static void addCharge(final Player player) {
		player.setAttached(CHARGES, Math.min(MAX_CHARGES, chargesOf(player) + 1));
	}

	/** Takes up to all of a player's stored Lightning charges, for a spell to spend. */
	public static int spendCharges(final Player player) {
		int charges = chargesOf(player);
		player.removeAttached(CHARGES);
		return charges;
	}

	/** A hit's damage once the weapon rune's signature has had its say: Ice at the slowed, Wind at the airborne. */
	public static float modify(final LivingEntity victim, final DamageSource source, final float amount, final RuneData rune, final double power) {
		return switch (rune.element()) {
			case ICE -> victim.hasEffect(MobEffects.SLOWNESS) || victim.getTicksFrozen() > 0 ? (float) (amount * (1.0 + iceBonus(power))) : amount;
			case WIND -> !victim.onGround() ? (float) (amount * (1.0 + windBonus(power))) : amount;
			default -> amount;
		};
	}

	/** The boss floor buffs a Holy rune has struck from a mob. */
	public static List<TowerBuffs.MobBuff> strippedFrom(final LivingEntity mob) {
		return mob.getAttachedOrElse(STRIPPED, List.of());
	}
}
