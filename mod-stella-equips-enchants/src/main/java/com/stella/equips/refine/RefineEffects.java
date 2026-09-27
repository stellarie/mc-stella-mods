package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import com.stella.equips.mixin.AbstractArrowAccessor;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * What a refined piece does beyond its numbers. A weapon at +10 sets what it strikes alight, at +12 withers
 * it, and at +15 sends a shockwave through the hostiles around it. A refined bow or crossbow adds its bonus
 * to the arrows it looses. Armour at +12 pricks whoever strikes its wearer, and at +15 mends its wearer.
 */
public final class RefineEffects {
	public static final int SEARING_TICKS = 60;
	public static final int RENDING_TICKS = 60;
	public static final double SHOCKWAVE_RADIUS = 3.0;
	public static final double SHOCKWAVE_SHARE = 0.4;
	/** +12 armour: damage each such piece deals back to a melee attacker. */
	public static final float THORN_DAMAGE = 2.0F;
	/** +15 armour: health each such piece restores, every interval. */
	public static final float BLESSING_HEAL = 1.0F;
	public static final int BLESSING_INTERVAL_TICKS = 100;
	private static final String ARROW_TAG = "mod-stella-equips-enchants.refined_arrow";
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final ThreadLocal<Boolean> SHOCKING = new ThreadLocal<>();

	private RefineEffects() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RefineEffects::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RefineEffects::beforeDamage);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow) {
				strengthen(arrow);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RefineEffects::bless);
	}

	/** A refined bow's arrow carries the bow's bonus, once. */
	static void strengthen(final AbstractArrow arrow) {
		ItemStack bow = arrow.getWeaponItem();
		int level = bow == null ? 0 : Refine.level(bow);
		if (level > 0 && Refine.isRanged(bow) && arrow.addTag(ARROW_TAG)) {
			arrow.setBaseDamage(baseDamage(arrow) + level * Refine.DAMAGE_PER_LEVEL);
		}
	}

	/** An arrow's base damage as it stands. */
	public static double baseDamage(final AbstractArrow arrow) {
		return ((AbstractArrowAccessor) arrow).stella$baseDamage();
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		if (blocked || SHOCKING.get() != null || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim
			|| source.is(DamageTypes.THORNS) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack weapon = source.getDirectEntity() == attacker ? attacker.getMainHandItem()
			: source.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : ItemStack.EMPTY;
		int refine = weapon == null ? 0 : Refine.level(weapon);
		if (refine < Refine.SEARING_LEVEL || Refine.isArmor(weapon)) {
			return;
		}
		victim.igniteForTicks(SEARING_TICKS);
		if (refine >= Refine.RENDING_LEVEL) {
			victim.addEffect(new MobEffectInstance(MobEffects.WITHER, RENDING_TICKS, 0), attacker);
		}
		if (refine >= Refine.ASCENDED_LEVEL) {
			shockwave(level, attacker, victim, damage);
		}
	}

	// The blow's echo: a share of its damage to every other hostile close around the one struck.
	private static void shockwave(final ServerLevel level, final LivingEntity attacker, final LivingEntity victim, final float damage) {
		SHOCKING.set(Boolean.TRUE);
		try {
			List<Mob> around = level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(SHOCKWAVE_RADIUS),
				mob -> mob != victim && mob != attacker && mob.isAlive() && mob instanceof Enemy && mob.distanceToSqr(victim) <= SHOCKWAVE_RADIUS * SHOCKWAVE_RADIUS);
			for (Mob mob : around) {
				mob.damageCooldownTime = 0;
				mob.hurtServer(level, level.damageSources().magic(), damage * (float) SHOCKWAVE_SHARE);
			}
			level.sendParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY(0.5), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			StellaEquips.LOGGER.info("[BEHAVIOR] test=refine SHOCKWAVE victim={} struck={}", victim.getUUID(), around.size());
		} finally {
			SHOCKING.remove();
		}
	}

	// Thorned armour answers a melee blow as it lands; a player's own hurt never reaches the after-damage event.
	private static boolean beforeDamage(final LivingEntity wearer, final DamageSource source, final float amount) {
		if (amount <= 0.0F || source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_PROJECTILE)
			|| !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == wearer || !(wearer.level() instanceof ServerLevel level)) {
			return true;
		}
		int thorned = piecesAtLeast(wearer, Refine.RENDING_LEVEL);
		if (thorned > 0) {
			attacker.hurtServer(level, level.damageSources().thorns(wearer), THORN_DAMAGE * thorned);
		}
		return true;
	}

	private static void bless(final MinecraftServer server) {
		if (server.getTickCount() % BLESSING_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int blessed = piecesAtLeast(player, Refine.ASCENDED_LEVEL);
			if (blessed > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
				player.heal(BLESSING_HEAL * blessed);
			}
		}
	}

	static int piecesAtLeast(final LivingEntity wearer, final int level) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack piece = wearer.getItemBySlot(slot);
			if (Refine.isArmor(piece) && Refine.level(piece) >= level) {
				pieces++;
			}
		}
		return pieces;
	}
}
