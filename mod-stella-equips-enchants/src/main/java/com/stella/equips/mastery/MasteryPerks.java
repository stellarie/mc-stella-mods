package com.stella.equips.mastery;

import com.stella.equips.StellaEquips;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mastery IV unlocks one perk per weapon type, on every melee hit its wielder lands:
 * a sword steals life, an axe cleaves the hostiles beside the target, a spear gives momentum, a
 * trident calls the tide, and a mace sends an aftershock through the ground.
 */
public final class MasteryPerks {
	public static final int PERK_TIER = 4;
	public static final float LIFESTEAL_SHARE = 0.03F;
	public static final float CLEAVE_SHARE = 0.5F;
	public static final double CLEAVE_RADIUS = 2.5;
	public static final int MOMENTUM_TICKS = 60;
	public static final int TIDECALLER_TICKS = 100;
	public static final float AFTERSHOCK_SHARE = 0.30F;
	public static final double AFTERSHOCK_RADIUS = 3.0;

	public enum Perk {
		LIFESTEAL("Lifesteal"),
		CLEAVE("Cleave"),
		MOMENTUM("Momentum"),
		TIDECALLER("Tidecaller"),
		AFTERSHOCK("Aftershock");

		private final String title;

		Perk(final String title) {
			this.title = title;
		}

		public String title() {
			return this.title;
		}
	}

	/** Test seam: each perk that fired, keyed by the wielder. */
	public interface Recorder {
		void fired(ServerPlayer player, Perk perk, LivingEntity victim);
	}

	private static final Recorder SILENT = (player, perk, victim) -> {
	};
	private static volatile Recorder recorder = SILENT;
	// A cleave or an aftershock is itself a player hit, so the perks it causes must not fire again.
	private static boolean spreading;

	private MasteryPerks() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MasteryPerks::afterDamage);
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** The perk a weapon type carries at Mastery IV, or null for an item with none. */
	public static @Nullable Perk perkFor(final ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return Perk.LIFESTEAL;
		}
		if (stack.is(ItemTags.AXES)) {
			return Perk.CLEAVE;
		}
		if (stack.is(ItemTags.SPEARS)) {
			return Perk.MOMENTUM;
		}
		if (stack.is(Items.TRIDENT)) {
			return Perk.TIDECALLER;
		}
		if (stack.is(Items.MACE)) {
			return Perk.AFTERSHOCK;
		}
		return null;
	}

	static void afterDamage(final LivingEntity victim, final DamageSource source, final float base, final float taken, final boolean blocked) {
		if (spreading || blocked || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) {
			return;
		}

		ItemStack weapon = player.getMainHandItem();
		Perk perk = perkFor(weapon);
		if (perk == null || WeaponMastery.tier(WeaponMastery.kills(weapon)) < PERK_TIER) {
			return;
		}

		ServerLevel level = player.level();
		switch (perk) {
			case LIFESTEAL -> player.heal(taken * LIFESTEAL_SHARE);
			case CLEAVE -> spread(level, player, victim, CLEAVE_RADIUS, taken * CLEAVE_SHARE);
			case MOMENTUM -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, MOMENTUM_TICKS, 0), player);
			case TIDECALLER -> player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, TIDECALLER_TICKS, 0), player);
			case AFTERSHOCK -> spread(level, player, victim, AFTERSHOCK_RADIUS, taken * AFTERSHOCK_SHARE);
		}

		Vec3 at = victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.1);
		recorder.fired(player, perk, victim);
	}

	// Damages the other hostiles near the victim, as the wielder, without letting those hits spread again.
	private static void spread(final ServerLevel level, final ServerPlayer player, final LivingEntity victim, final double radius, final float damage) {
		spreading = true;
		try {
			for (Mob other : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(radius),
				candidate -> candidate != victim && candidate.isAlive() && candidate instanceof Enemy
					&& candidate.distanceToSqr(victim) <= radius * radius)) {
				other.damageCooldownTime = 0;
				other.hurtServer(level, level.damageSources().playerAttack(player), damage);
			}
		} finally {
			spreading = false;
		}
	}
}
