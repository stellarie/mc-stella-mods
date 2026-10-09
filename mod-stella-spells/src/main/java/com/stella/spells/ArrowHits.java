package com.stella.spells;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import com.stella.spells.mixin.AbstractArrowAccessor;

/** What a crafted arrow does when it lands. */
public final class ArrowHits {
	/** A blast arrow's burst: how far it reaches, and what it deals to each hostile in it. */
	static final double BLAST_RADIUS = 2.0;
	static final float BLAST_DAMAGE = 3.0F;
	static final int FIRE_SECONDS = 4;
	/** The glow a spectral arrow gives, as the vanilla arrow does. */
	static final int SPECTRAL_GLOW_TICKS = 200;
	static final int FROST_TICKS = 60;
	static final int FROST_AMPLIFIER = 1;
	static final int VENOM_TICKS = 80;
	static final int VENOM_AMPLIFIER = 0;
	/** A diamond arrow pierces this many foes beyond the first. */
	static final int DIAMOND_PIERCE = 1;

	private ArrowHits() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArrowHits::afterDamage);
	}

	/** Sets the kind's launch traits on a fresh arrow entity. */
	static void prepare(final AbstractArrow arrow, final ArrowKind kind) {
		switch (kind) {
			case KEEN -> arrow.setCritArrow(true);
			case DIAMOND -> ((AbstractArrowAccessor) arrow).stellaSpells$setPierceLevel((byte) DIAMOND_PIERCE);
			default -> {
			}
		}
	}

	/** The kind of a crafted arrow, or null for a vanilla one. */
	static @Nullable ArrowKind kindOf(final @Nullable Entity entity) {
		if (entity instanceof AbstractArrow arrow && arrow.getPickupItemStackOrigin().getItem() instanceof CraftedArrowItem crafted) {
			return crafted.kind();
		}
		return null;
	}

	/** The share of damage the arrow in this hit keeps; 1.0 for every arrow that is not a crafted kind. */
	public static float damageFactor(final DamageSource source) {
		ArrowKind kind = kindOf(source.getDirectEntity());
		return kind == null ? 1.0F : kind.damageFactor();
	}

	// The effect lands only on a hit that got through, so a shield or an invulnerable target takes none.
	private static void afterDamage(
		final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked
	) {
		if (blocked || damage <= 0.0F || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		ArrowKind kind = kindOf(source.getDirectEntity());
		if (kind == null) {
			return;
		}
		applyEffect(level, kind, victim, ((AbstractArrow) source.getDirectEntity()).getOwner());
	}

	// One place for what a kind does to a foe, shared by an arrow in flight and an archer skill that spent one.
	private static void applyEffect(final ServerLevel level, final ArrowKind kind, final LivingEntity victim, final @Nullable Entity owner) {
		switch (kind) {
			case FIRE -> victim.igniteForSeconds(FIRE_SECONDS);
			case FROST -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_TICKS, FROST_AMPLIFIER), owner);
			case VENOM -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, VENOM_AMPLIFIER), owner);
			case BLAST -> burst(level, victim.getBoundingBox().getCenter(), owner, victim);
			default -> {
			}
		}
	}

	/** The share of damage an archer skill hit keeps for the arrow it spent; 1.0 for no arrow or a vanilla one. */
	static float skillFactor(final ItemStack arrow) {
		return arrow.getItem() instanceof CraftedArrowItem crafted ? crafted.kind().damageFactor() : 1.0F;
	}

	/** What the arrow an archer skill spent does to the foe that skill hit. */
	static void skillEffect(final ServerPlayer caster, final LivingEntity victim, final ItemStack arrow) {
		if (arrow.isEmpty() || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		if (arrow.getItem() instanceof CraftedArrowItem crafted) {
			applyEffect(level, crafted.kind(), victim, caster);
		} else if (arrow.is(Items.SPECTRAL_ARROW)) {
			victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, SPECTRAL_GLOW_TICKS), caster);
		}
	}

	/** A blast arrow that struck a block bursts where it struck. */
	public static void onHitBlock(final AbstractArrow arrow, final Vec3 at) {
		if (kindOf(arrow) == ArrowKind.BLAST && arrow.level() instanceof ServerLevel level) {
			burst(level, at, arrow.getOwner(), null);
		}
	}

	// The mod never calls level.explode, so the burst breaks no block; it only hurts hostiles.
	private static void burst(final ServerLevel level, final Vec3 at, final @Nullable Entity owner, final @Nullable Entity struck) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
		DamageSource source = level.damageSources().explosion(null, owner);
		for (Mob foe : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(BLAST_RADIUS),
			candidate -> candidate != struck && candidate.isAlive() && Friends.isFoe(candidate)
				&& candidate.getBoundingBox().distanceToSqr(at) <= BLAST_RADIUS * BLAST_RADIUS)) {
			foe.hurtServer(level, source, BLAST_DAMAGE);
		}
	}
}
