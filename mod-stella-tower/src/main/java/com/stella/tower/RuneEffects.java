package com.stella.tower;

import com.stella.tower.RuneStats.Stat;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A weapon rune's signature, by its element. Each reads the wielder's stats, so it works on any mob in any
 * dimension: Fire stacks heat to a combustion, Ice chills and then strikes the slowed, Water soaks for
 * more damage, Lightning chains, Wind favors the airborne, Void shreds armor, Holy heals, Dark finishes.
 */
public final class RuneEffects {
	public static final int HEAT_STACKS = 5;
	public static final int WET_TICKS = 100;
	public static final int CHILL_TICKS = 40;
	public static final int CHAIN_EVERY = 4;
	public static final int CHAIN_TARGETS = 2;
	public static final double CHAIN_RANGE = 4.0;
	public static final double SHRED_PER_HIT = 2.0;
	public static final double LOW_HEALTH = 0.30;
	public static final double WIND_KNOCKBACK = 0.5;
	public static final int MAX_CHARGES = 5;

	/** Damage the runes deal themselves: it triggers no rune effect, so a chain cannot start a chain. */
	public static final ResourceKey<DamageType> BURST = ResourceKey.create(Registries.DAMAGE_TYPE, StellaTower.id("rune_burst"));

	private record Soak(int until, double percent) {
	}

	private static final AttachmentType<Integer> HEAT = AttachmentRegistry.create(StellaTower.id("rune_heat"));
	private static final AttachmentType<Soak> SOAK = AttachmentRegistry.create(StellaTower.id("rune_soak"));
	private static final AttachmentType<Integer> SHRED = AttachmentRegistry.create(StellaTower.id("rune_shred"));
	private static final AttachmentType<Integer> HITS = AttachmentRegistry.create(StellaTower.id("rune_hits"));
	private static final AttachmentType<Integer> CHARGES = AttachmentRegistry.create(StellaTower.id("rune_charges"));
	private static final Identifier SHRED_ID = StellaTower.id("void_shred");

	private static @Nullable Double pinnedRoll;
	private static boolean dealing;

	private RuneEffects() {
	}

	public static void register() {
		// What a hit leaves behind waits until it has landed, so a blow turned aside builds nothing.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (dealing || blocked || taken <= 0.0F || !(entity.level() instanceof ServerLevel level)
				|| !(source.getEntity() instanceof ServerPlayer player) || !TowerRunes.struckWith(source)
				|| TowerRunes.slotOf(TowerRunes.weaponOf(source)) != TowerRunes.Slot.WEAPON) {
				return;
			}
			RuneData rune = TowerRunes.runeOf(TowerRunes.weaponOf(source));
			if (rune != null) {
				afterHit(level, entity, player, rune.element(), taken);
			}
		});
	}

	private static int now(final LivingEntity entity) {
		return entity.level().getServer() == null ? entity.tickCount : entity.level().getServer().getTickCount();
	}

	/** Whether a rune is dealing damage of its own right now. */
	public static boolean dealing() {
		return dealing;
	}

	/** The test seam: a pinned roll in [0, 1) replaces every chance roll; null restores chance. */
	public static void pinRoll(final @Nullable Double roll) {
		pinnedRoll = roll;
	}

	/** Whether a chance in percent comes up for this entity. */
	public static boolean rolls(final LivingEntity roller, final double percent) {
		if (percent <= 0.0) {
			return false;
		}
		Double pin = pinnedRoll;
		return (pin != null ? pin : roller.getRandom().nextDouble()) * 100.0 < percent;
	}

	private static void afterHit(final ServerLevel level, final LivingEntity target, final ServerPlayer player, final TowerElement element, final float taken) {
		int now = now(target);
		switch (element) {
			case FIRE -> {
				int heat = target.getAttachedOrElse(HEAT, 0) + 1;
				if (heat >= HEAT_STACKS) {
					target.removeAttached(HEAT);
					double share = RuneValues.fireCombust(RuneStats.of(player, Stat.STR)) / 100.0;
					deal(level, target, player, (float) (taken * share));
					level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1.0, target.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
					level.playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
				} else {
					target.setAttached(HEAT, heat);
				}
			}
			case ICE -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 0), player);
			case WATER -> {
				target.setAttached(SOAK, new Soak(now + WET_TICKS, RuneValues.waterSoak(RuneStats.of(player, Stat.LUK))));
				level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
			}
			case LIGHTNING -> {
				int hits = player.getAttachedOrElse(HITS, 0) + 1;
				player.setAttached(HITS, hits);
				if (hits % CHAIN_EVERY == 0) {
					chain(level, target, player, (float) (taken * RuneValues.lightningChain(RuneStats.of(player, Stat.DEX)) / 100.0));
				}
			}
			case WIND -> shove(target, player);
			case VOID -> {
				int stacks = target.getAttachedOrElse(SHRED, 0) + 1;
				target.setAttached(SHRED, stacks);
				double cut = Math.min(stacks * SHRED_PER_HIT, RuneValues.voidShredCap(RuneStats.of(player, Stat.INT)));
				AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
				if (armor != null) {
					armor.removeModifier(SHRED_ID);
					armor.addTransientModifier(new AttributeModifier(SHRED_ID, -cut / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			}
			case HOLY -> {
				double percent = RuneValues.holyHeal(RuneStats.of(player, Stat.VIT));
				if (target.is(EntityTypeTags.UNDEAD)) {
					percent *= RuneValues.HEAL_UNDEAD_FACTOR;
				}
				if (percent > 0.0) {
					player.heal((float) (taken * percent / 100.0));
					level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.6, 0.3, 0.05);
				}
			}
			case DARK -> {
			}
		}
	}

	/**
	 * A blow of the rune's own: not fire and not armed, so no armor or enchantment blunts it and it
	 * triggers nothing. The blow that set it off keeps its hold on the target, so the next hits land as they would.
	 */
	public static void deal(final ServerLevel level, final LivingEntity target, final @Nullable LivingEntity credit, final float amount) {
		if (amount <= 0.0F || !target.isAlive()) {
			return;
		}
		int cooldown = target.damageCooldownTime;
		float lastHurt = ((com.stella.tower.mixin.LivingEntityAccessor) target).stellaTower$lastHurt();
		boolean was = dealing;
		dealing = true;
		try {
			DamageSource burst = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BURST), credit);
			target.hurtServer(level, burst, amount);
		} finally {
			dealing = was;
		}
		target.damageCooldownTime = cooldown;
		((com.stella.tower.mixin.LivingEntityAccessor) target).stellaTower$setLastHurt(lastHurt);
	}

	private static void chain(final ServerLevel level, final LivingEntity from, final ServerPlayer player, final float amount) {
		if (amount <= 0.0F) {
			return;
		}
		List<Mob> near = level.getEntitiesOfClass(Mob.class, from.getBoundingBox().inflate(CHAIN_RANGE),
			mob -> mob != from && mob.isAlive() && Hostiles.isHostile(mob) && mob.distanceToSqr(from) <= CHAIN_RANGE * CHAIN_RANGE);
		near.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(from)));
		for (Mob mob : near.subList(0, Math.min(CHAIN_TARGETS, near.size()))) {
			deal(level, mob, player, amount);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + 1.0, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
		}
	}

	/** A hit's damage once the weapon rune's signature has had its say. */
	public static float modify(final LivingEntity victim, final DamageSource source, final float amount, final RuneData rune) {
		if (dealing || !(source.getEntity() instanceof Player player)) {
			return amount;
		}
		double percent = switch (rune.element()) {
			case ICE -> victim.hasEffect(MobEffects.SLOWNESS) || victim.getTicksFrozen() > 0 ? RuneValues.iceBonus(RuneStats.of(player, Stat.INT)) : 0.0;
			case WIND -> !victim.onGround() ? RuneValues.windAirborne(RuneStats.of(player, Stat.AGI)) : 0.0;
			case DARK -> victim.getHealth() < LOW_HEALTH * victim.getMaxHealth() ? RuneValues.darkLowHealth(RuneStats.of(player, Stat.STR)) : 0.0;
			default -> 0.0;
		};
		return (float) (amount * (1.0 + percent / 100.0));
	}

	/** Damage from every source on a soaked target, for the wielder's LUK that soaked it. */
	public static float soaked(final LivingEntity victim, final DamageSource source, final float amount) {
		Soak soak = victim.getAttached(SOAK);
		return soak != null && now(victim) < soak.until() ? (float) (amount * (1.0 + soak.percent() / 100.0)) : amount;
	}

	public static boolean isWet(final LivingEntity target) {
		Soak soak = target.getAttached(SOAK);
		return soak != null && now(target) < soak.until();
	}

	public static int heatOf(final LivingEntity target) {
		return target.getAttachedOrElse(HEAT, 0);
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

	/** Extra knockback: a push away from the wielder, on top of the blow's own. */
	private static void shove(final LivingEntity target, final Player player) {
		double dx = target.getX() - player.getX();
		double dz = target.getZ() - player.getZ();
		double length = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
		target.push(dx / length * WIND_KNOCKBACK, 0.1, dz / length * WIND_KNOCKBACK);
	}
}
