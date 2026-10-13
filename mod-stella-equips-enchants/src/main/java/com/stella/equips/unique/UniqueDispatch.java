package com.stella.equips.unique;

import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/**
 * Runs the hooks of {@link UniqueEffect}. A weapon hook fires for the unique in the main hand and an armor hook for
 * each unique worn in its own slot. The incoming, every-second and harmful-duration hooks fire for both. An arrow hook
 * fires for the unique that loosed the arrow, even if the holder has put the bow away.
 *
 * <p>A hit is classified once from its damage source:
 * <ul>
 * <li>arrow: the direct entity is an arrow, and the arrow carries a mark of a unique;
 * <li>spell: damage of type {@code minecraft:indirect_magic} or {@code minecraft:magic} whose attacker is the holder,
 *   which is what {@code mod-stella-spells} deals for a player (it calls {@code indirectMagic(caster, caster)});
 * <li>melee: damage in {@code minecraft:is_player_attack} whose direct entity is the holder.
 * </ul>
 * Unique damage and thorns never run the hit hooks, so an extra hit cannot start a chain.
 *
 * <p>The outgoing hooks run from a mixin at the head of {@code LivingEntity.hurtServer}, before blocking, armor and the
 * hurt cooldown. The incoming hook runs from a return hook of {@code getDamageAfterMagicAbsorb}, after armor and
 * enchantment protection. The after hooks run from {@code ServerLivingEntityEvents.AFTER_DAMAGE}.
 */
public final class UniqueDispatch {
	private static final int SECOND = 20;
	private static final ThreadLocal<Boolean> CRITICAL = new ThreadLocal<>();
	// The event reports the hit before armor, so the final damage is kept here when the incoming hook sees it.
	private static LivingEntity mitigatedVictim;
	private static float mitigated;

	private enum Kind { NONE, MELEE, ARROW, SPELL }

	private UniqueDispatch() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(UniqueDispatch::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(UniqueDispatch::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(UniqueDispatch::tick);
	}

	/** Called at the start of a player attack. */
	public static void beginAttack() {
		CRITICAL.set(Boolean.FALSE);
	}

	/** Called with the result of the vanilla critical check of an attack. */
	public static void criticalAttack(final boolean critical) {
		CRITICAL.set(critical);
	}

	/** True while a player attack is in progress and the vanilla check called it a critical hit. */
	public static boolean isCriticalAttack() {
		return Boolean.TRUE.equals(CRITICAL.get());
	}

	public static void endAttack() {
		CRITICAL.remove();
	}

	private static boolean isUniqueDamage(final DamageSource source) {
		return source.is(UniqueDamage.UNIQUE) || source.is(UniqueDamage.UNIQUE_PIERCE);
	}

	private static Kind kind(final ServerPlayer holder, final DamageSource source) {
		if (isUniqueDamage(source) || source.is(DamageTypes.THORNS)) {
			return Kind.NONE;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow) {
			return Kind.ARROW;
		}
		if ((source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.MAGIC)) && source.getEntity() == holder) {
			return Kind.SPELL;
		}
		if (direct == holder && source.is(DamageTypeTags.IS_PLAYER_ATTACK)) {
			return Kind.MELEE;
		}
		return Kind.NONE;
	}

	/** The unique that loosed an arrow, with the context its hooks run for, or null for an arrow of no unique. */
	private static Uniques.@Nullable Active arrowUnique(final ServerPlayer holder, final AbstractArrow arrow) {
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		UniqueDef def = mark == null ? null : Uniques.byId(mark.id());
		if (def == null || arrow.getOwner() != holder) {
			return null;
		}
		return new Uniques.Active(def, new UniqueContext(holder, UniqueShots.weaponOf(def, arrow), mark.refine()));
	}

	/** The damage a hit carries before armor, after the hooks of the attacker's uniques. */
	public static float outgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer holder) || holder == victim) {
			return amount;
		}
		float result = amount;
		switch (kind(holder, source)) {
			case ARROW -> {
				AbstractArrow arrow = (AbstractArrow) source.getDirectEntity();
				Uniques.Active active = arrowUnique(holder, arrow);
				if (active != null) {
					result = active.def().effect().outgoingArrow(active.context(), victim, arrow, result);
				}
			}
			case SPELL -> {
				for (Uniques.Active active : Uniques.active(holder)) {
					result = active.def().effect().outgoingSpell(active.context(), victim, source, result);
				}
			}
			case MELEE -> {
				for (Uniques.Active active : Uniques.active(holder)) {
					result = active.def().effect().outgoingMelee(active.context(), victim, source, result);
				}
			}
			default -> {
			}
		}
		return Float.isNaN(result) ? amount : Math.max(0.0F, result);
	}

	/** The damage the holder is about to take, after armor and enchantment protection. */
	public static float incoming(final LivingEntity victim, final DamageSource source, final float damage) {
		float result = damage;
		if (damage > 0.0F && victim instanceof ServerPlayer holder) {
			for (Uniques.Active active : Uniques.active(holder)) {
				result = active.def().effect().incoming(active.context(), source, result);
			}
			result = Float.isNaN(result) ? damage : Math.max(0.0F, result);
		}
		mitigatedVictim = victim;
		mitigated = result;
		return result;
	}

	/** The damage a victim took after armor and protection, when the last hit on it passed the incoming hook. */
	public static float taken(final LivingEntity victim, final float reported) {
		return mitigatedVictim == victim ? mitigated : reported;
	}

	/** A harmful effect that the holder is about to receive, with its duration changed by the hooks. */
	public static MobEffectInstance harmful(final LivingEntity victim, final MobEffectInstance effect) {
		if (!(victim instanceof ServerPlayer holder) || effect == null || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL
			|| effect.isInfiniteDuration() || effect.getEffect().value().isInstantaneous()) {
			return effect;
		}
		int ticks = effect.getDuration();
		for (Uniques.Active active : Uniques.active(holder)) {
			ticks = active.def().effect().harmfulDuration(active.context(), effect, ticks);
		}
		ticks = Math.max(1, ticks);
		if (ticks == effect.getDuration()) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float reported, final boolean blocked) {
		if (!blocked) {
			afterHit(victim, source, taken(victim, reported), false);
		}
	}

	// The damage event does not fire for a hit that kills, so the death event runs the after hooks of that hit.
	private static void afterDeath(final LivingEntity victim, final DamageSource source) {
		afterHit(victim, source, taken(victim, 0.0F), true);
		if (source.getEntity() instanceof ServerPlayer holder && holder != victim) {
			for (Uniques.Active active : Uniques.active(holder)) {
				active.def().effect().onKill(active.context(), victim, source);
			}
		}
	}

	private static void afterHit(final LivingEntity victim, final DamageSource source, final float damage, final boolean killed) {
		if (victim instanceof ServerPlayer wearer) {
			for (Uniques.Active active : Uniques.active(wearer)) {
				active.def().effect().afterIncoming(active.context(), source, damage);
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer holder) || holder == victim) {
			return;
		}
		switch (kind(holder, source)) {
			case ARROW -> {
				AbstractArrow arrow = (AbstractArrow) source.getDirectEntity();
				Uniques.Active active = arrowUnique(holder, arrow);
				if (active != null) {
					active.def().effect().afterArrow(active.context(), victim, arrow, damage, killed);
				}
			}
			case SPELL -> {
				for (Uniques.Active active : Uniques.active(holder)) {
					active.def().effect().afterSpell(active.context(), victim, source, damage, killed);
				}
			}
			case MELEE -> {
				boolean crit = Boolean.TRUE.equals(CRITICAL.get());
				for (Uniques.Active active : Uniques.active(holder)) {
					active.def().effect().afterMelee(active.context(), victim, source, damage, crit, killed);
				}
			}
			default -> {
			}
		}
	}

	// Counted on the server tick, never on the game time: the game time of a test level barely moves.
	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SECOND != 0) {
			return;
		}
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (!player.isAlive()) {
				continue;
			}
			for (Uniques.Active active : Uniques.active(player)) {
				active.def().effect().everySecond(active.context());
			}
		}
	}
}
