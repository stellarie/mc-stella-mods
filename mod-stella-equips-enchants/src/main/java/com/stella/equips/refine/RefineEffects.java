package com.stella.equips.refine;

import com.stella.equips.StellaEquips;
import com.stella.equips.enchant.Foes;
import com.stella.equips.enchant.HealthScale;
import com.stella.equips.mixin.AbstractArrowAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What a refined piece does beyond its numbers, by the effects {@link RefineSpecials} lists for it. A weapon
 * strikes for more now and then, sets its target alight, bleeds, slows, withers it, or sends a shockwave. A
 * bow's arrows pierce, wither and come with a second arrow. Armour shields, evades, answers a blow, mends
 * and pricks its wearer's attackers. Every proc is a share of the hit or of max health, so it grows with the
 * game. A chance effect rolls through a {@link Roller}, so a test can force either outcome.
 */
public final class RefineEffects {
	public static final int SEARING_TICKS = 60;
	public static final int NETHERITE_SEARING_TICKS = 100;
	public static final int RENDING_TICKS = 60;
	public static final int FROSTBITE_TICKS = 40;
	public static final int FROSTBITE_AMPLIFIER = 1;
	public static final double SHOCKWAVE_RADIUS = 3.0;
	public static final double SHOCKWAVE_SHARE = 0.4;
	/** +7 weapon: the chance in percent that a hit strikes for {@link #KEEN_MULTIPLIER} times the damage. */
	public static final int KEEN_CHANCE = 5;
	public static final float KEEN_MULTIPLIER = 1.5F;
	/** +10 wood, stone, gold and copper weapon: a share of the hit, each second, for this many seconds. */
	public static final double SPLINTER_SHARE = 0.02;
	public static final int SPLINTER_SECONDS = 3;
	/** +15 bow: the extra arrow's share of the shot's damage, and how far it strays from it. */
	public static final double VOLLEY_SHARE = 0.5;
	public static final double VOLLEY_SPREAD_DEGREES = 6.0;
	/** +7 helmet: the share by which harmful effects on the wearer run shorter. */
	public static final double CLARITY_SHARE = 0.10;
	/** +7 boots: the share of fall damage taken away. */
	public static final double FEATHERFALL_SHARE = 0.30;
	/** +10 leather, per piece: the chance in percent to ignore a hit. */
	public static final int EVASION_CHANCE = 3;
	/** +10 chainmail, per piece: the chance in percent to reflect a share of a melee blow. */
	public static final int RIPOSTE_CHANCE = 10;
	public static final double RIPOSTE_SHARE = 0.20;
	/** +10 gold, per piece: the share of experience from kills added. */
	public static final double FORTUNE_SHARE = 0.05;
	/** +10 diamond, per piece: the share of damage taken away. */
	public static final double BULWARK_SHARE = 0.03;
	/** +10 netherite, per piece: the share of fire damage taken away. */
	public static final double EMBERGUARD_SHARE = 0.125;
	/** +12 armour: the share of a melee blow each such piece deals back. */
	public static final double THORN_SHARE = 0.10;
	/** +15 armour: the share of max health, in vanilla units, each such piece restores every interval. */
	public static final double BLESSING_SHARE = 0.01;
	public static final int BLESSING_INTERVAL_TICKS = 100;
	// Parry shares at +10, +12 and +15.
	public static final double PARRY_SHARE_FIRST = 0.25;
	public static final double PARRY_SHARE_SECOND = 0.50;
	public static final double PARRY_SHARE_THIRD = 0.75;
	public static final String VOLLEY_TAG = "mod-stella-equips-enchants.volley_arrow";
	private static final String ARROW_TAG = "mod-stella-equips-enchants.refined_arrow";
	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final ThreadLocal<Boolean> SHOCKING = new ThreadLocal<>();

	/** Whether a chance effect fires, given its chance in percent; the test seam can force either side. */
	public interface Roller {
		boolean succeeds(RefineSpecials.Special special, LivingEntity entity, RandomSource random, int chancePercent);
	}

	private static final Roller FAIR = (special, entity, random, chance) -> RefineStation.fairRoll(random, chance);
	private static volatile Roller roller = FAIR;

	private static final class Bleed {
		private final LivingEntity victim;
		private final float damage;
		private int ticksLeft = SPLINTER_SECONDS * 20;

		private Bleed(final LivingEntity victim, final float damage) {
			this.victim = victim;
			this.damage = damage;
		}
	}

	private record Volley(ServerLevel level, Vec3 position, Vec3 velocity, Entity owner, ItemStack weapon, ItemStack ammo, double baseDamage, boolean crit) {
	}

	// One bleed to a victim: a new hit refreshes it rather than stacking.
	private static final List<Bleed> BLEEDS = new ArrayList<>();
	private static final List<Volley> VOLLEYS = new ArrayList<>();

	private RefineEffects() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RefineEffects::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RefineEffects::allowDamage);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow) {
				strengthen(arrow);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RefineEffects::bless);
		ServerTickEvents.END_SERVER_TICK.register(RefineEffects::tickBleeds);
		ServerTickEvents.END_SERVER_TICK.register(RefineEffects::loosenVolleys);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BLEEDS.clear();
			VOLLEYS.clear();
		});
	}

	public static void setRoller(final Roller newRoller) {
		roller = newRoller == null ? FAIR : newRoller;
	}

	public static double parryShare(final int level) {
		if (level >= Refine.ASCENDED_LEVEL) {
			return PARRY_SHARE_THIRD;
		}
		if (level >= Refine.MILESTONE_SECOND) {
			return PARRY_SHARE_SECOND;
		}
		return level >= Refine.MILESTONE_FIRST ? PARRY_SHARE_FIRST : 0.0;
	}

	private static boolean roll(final RefineSpecials.Special special, final LivingEntity entity, final int chancePercent) {
		return roller.succeeds(special, entity, entity.getRandom(), Math.min(100, chancePercent));
	}

	/** A refined bow's arrow carries the bow's bonus, once. */
	static void strengthen(final AbstractArrow arrow) {
		ItemStack bow = arrow.getWeaponItem();
		int level = bow == null ? 0 : Refine.level(bow);
		if (level > 0 && Refine.isRanged(bow) && arrow.addTag(ARROW_TAG)) {
			arrow.setBaseDamage(baseDamage(arrow) + level * Refine.DAMAGE_PER_LEVEL);
			List<RefineSpecials.Special> specials = RefineSpecials.of(bow, level);
			if (specials.contains(RefineSpecials.Special.PIERCING)) {
				((AbstractArrowAccessor) arrow).stella$setPierceLevel((byte) (arrow.getPierceLevel() + 1));
			}
			if (specials.contains(RefineSpecials.Special.VOLLEY) && arrow instanceof Arrow && arrow.level() instanceof ServerLevel world && arrow.getOwner() != null) {
				VOLLEYS.add(new Volley(world, arrow.position(), arrow.getDeltaMovement(), arrow.getOwner(), bow.copy(), arrow.getPickupItemStackOrigin(),
					baseDamage(arrow) * VOLLEY_SHARE, arrow.isCritArrow()));
			}
		}
	}

	/** An arrow's base damage as it stands. */
	public static double baseDamage(final AbstractArrow arrow) {
		return ((AbstractArrowAccessor) arrow).stella$baseDamage();
	}

	// The second arrow leaves a tick after the first: an entity added while another loads is not safe.
	private static void loosenVolleys(final MinecraftServer server) {
		if (VOLLEYS.isEmpty()) {
			return;
		}
		List<Volley> due = List.copyOf(VOLLEYS);
		VOLLEYS.clear();
		for (Volley volley : due) {
			Arrow extra = new Arrow(volley.level(), volley.position().x, volley.position().y, volley.position().z, volley.ammo(), volley.weapon());
			extra.addTag(ARROW_TAG);
			extra.addTag(VOLLEY_TAG);
			extra.setOwner(volley.owner());
			extra.pickup = AbstractArrow.Pickup.DISALLOWED;
			extra.setBaseDamage(volley.baseDamage());
			extra.setCritArrow(volley.crit());
			extra.setDeltaMovement(volley.velocity().yRot((float) Math.toRadians(VOLLEY_SPREAD_DEGREES)));
			volley.level().addFreshEntity(extra);
		}
	}

	/** A hit the attacker's weapon lands: its main hand for a blow, or the weapon that loosed the arrow. */
	private static ItemStack weaponOf(final DamageSource source, final LivingEntity attacker) {
		Entity direct = source.getDirectEntity();
		if (direct == attacker) {
			ItemStack held = attacker.getMainHandItem();
			return Refine.isRanged(held) ? ItemStack.EMPTY : held;
		}
		if (direct instanceof AbstractArrow arrow) {
			ItemStack weapon = arrow.getWeaponItem();
			return weapon == null ? ItemStack.EMPTY : weapon;
		}
		return ItemStack.EMPTY;
	}

	/** The hit a victim is about to take, after the wielder's Keen has had its chance to make it stronger. */
	public static float outgoing(final LivingEntity victim, final DamageSource source, final float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim || source.is(DamageTypes.THORNS)) {
			return amount;
		}
		ItemStack weapon = weaponOf(source, attacker);
		if (weapon.isEmpty() || !RefineSpecials.has(weapon, RefineSpecials.Special.KEEN)) {
			return amount;
		}
		return roll(RefineSpecials.Special.KEEN, attacker, KEEN_CHANCE) ? amount * KEEN_MULTIPLIER : amount;
	}

	/** The damage a victim finally takes, after the armour it wears has taken its share away. */
	public static float incoming(final LivingEntity victim, final DamageSource source, final float damage) {
		if (damage <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return damage;
		}
		double factor = 1.0;
		factor *= keep(BULWARK_SHARE, pieces(victim, RefineSpecials.Special.BULWARK));
		if (source.is(DamageTypeTags.IS_FIRE)) {
			factor *= keep(EMBERGUARD_SHARE, pieces(victim, RefineSpecials.Special.EMBERGUARD));
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			factor *= keep(FEATHERFALL_SHARE, pieces(victim, RefineSpecials.Special.FEATHERFALL));
		}
		return (float) (damage * factor);
	}

	private static double keep(final double sharePerPiece, final int pieces) {
		return Math.max(0.0, 1.0 - sharePerPiece * pieces);
	}

	/** A harmful effect the wearer of Clarity is about to suffer, shortened. */
	public static MobEffectInstance shorten(final LivingEntity wearer, final MobEffectInstance effect) {
		if (effect == null || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL || effect.isInfiniteDuration()
			|| effect.getEffect().value().isInstantaneous()) {
			return effect;
		}
		int pieces = pieces(wearer, RefineSpecials.Special.CLARITY);
		if (pieces == 0) {
			return effect;
		}
		int ticks = (int) Math.max(1L, Math.round(effect.getDuration() * keep(CLARITY_SHARE, pieces)));
		return new MobEffectInstance(effect.getEffect(), ticks, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** The experience a kill gives, with the killer's Fortune added, rounded at random so a small kill still gains. */
	public static int experience(final Entity killer, final int base) {
		if (base <= 0 || !(killer instanceof LivingEntity wearer)) {
			return base;
		}
		int pieces = pieces(wearer, RefineSpecials.Special.FORTUNE);
		if (pieces == 0) {
			return base;
		}
		double scaled = base * (1.0 + FORTUNE_SHARE * pieces);
		int whole = (int) scaled;
		return whole + (wearer.getRandom().nextDouble() < scaled - whole ? 1 : 0);
	}

	// Evasion: a hit from something is ignored whole, before it can hurt or knock back.
	private static boolean allowDamage(final LivingEntity wearer, final DamageSource source, final float amount) {
		if (amount <= 0.0F || source.getEntity() == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		int pieces = pieces(wearer, RefineSpecials.Special.EVASION);
		return pieces == 0 || !roll(RefineSpecials.Special.EVASION, wearer, EVASION_CHANCE * pieces);
	}

	private static void afterDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damage, final boolean blocked) {
		answer(victim, source, damage, blocked);
		if (blocked || SHOCKING.get() != null || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim
			|| source.is(DamageTypes.THORNS) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack weapon = weaponOf(source, attacker);
		int refine = Refine.level(weapon);
		if (refine <= 0) {
			return;
		}
		for (RefineSpecials.Special special : RefineSpecials.of(weapon, refine)) {
			switch (special) {
				case SEARING -> victim.igniteForTicks(RefineSpecials.searingTicks(weapon));
				case SPLINTER -> bleed(victim, damage * (float) SPLINTER_SHARE);
				case FROSTBITE -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROSTBITE_TICKS, FROSTBITE_AMPLIFIER), attacker);
				case RENDING -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, RENDING_TICKS, 0), attacker);
				case ASCENDED -> shockwave(level, attacker, victim, damage);
				default -> {
				}
			}
		}
	}

	private static void bleed(final LivingEntity victim, final float damage) {
		if (damage <= 0.0F || !victim.isAlive()) {
			return;
		}
		BLEEDS.removeIf(bleed -> bleed.victim == victim);
		BLEEDS.add(new Bleed(victim, damage));
	}

	// Each second, the bleed's damage. It is magic, so it neither reads as a hit from the wielder nor waits out invulnerability.
	private static void tickBleeds(final MinecraftServer server) {
		if (BLEEDS.isEmpty()) {
			return;
		}
		for (Bleed bleed : List.copyOf(BLEEDS)) {
			LivingEntity victim = bleed.victim;
			if (!victim.isAlive() || !(victim.level() instanceof ServerLevel level)) {
				BLEEDS.remove(bleed);
				continue;
			}
			bleed.ticksLeft--;
			if (bleed.ticksLeft % 20 == 0) {
				victim.hurtServer(level, level.damageSources().magic(), bleed.damage);
			}
			if (bleed.ticksLeft <= 0) {
				BLEEDS.remove(bleed);
			}
		}
	}

	// The blow's echo: a share of its damage to every other hostile close around the one struck.
	private static void shockwave(final ServerLevel level, final LivingEntity attacker, final LivingEntity victim, final float damage) {
		SHOCKING.set(Boolean.TRUE);
		try {
			List<Mob> around = level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(SHOCKWAVE_RADIUS),
				mob -> mob != victim && Foes.isFoe(attacker, mob) && mob.distanceToSqr(victim) <= SHOCKWAVE_RADIUS * SHOCKWAVE_RADIUS);
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

	// Thorned and Riposte armour answer a melee blow that landed: not one a shield stopped, nor one inside the
	// wearer's invulnerability after a hit, which never reaches this event. Both answer in one hit, or the
	// attacker's own invulnerability would swallow the second.
	private static void answer(final LivingEntity wearer, final DamageSource source, final float damage, final boolean blocked) {
		if (blocked || damage <= 0.0F || source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_PROJECTILE)
			|| !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == wearer || !(wearer.level() instanceof ServerLevel level)) {
			return;
		}
		float back = damage * (float) THORN_SHARE * pieces(wearer, RefineSpecials.Special.THORNED);
		int riposte = pieces(wearer, RefineSpecials.Special.RIPOSTE);
		if (riposte > 0 && roll(RefineSpecials.Special.RIPOSTE, wearer, RIPOSTE_CHANCE * riposte)) {
			back += damage * (float) RIPOSTE_SHARE;
		}
		if (back > 0.0F) {
			attacker.hurtServer(level, level.damageSources().thorns(wearer), back);
		}
	}

	private static void bless(final MinecraftServer server) {
		if (server.getTickCount() % BLESSING_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			blessOnce(player);
		}
	}

	/** One interval's mending for a wearer of Blessed armour: a share of max health, in vanilla units, for each piece. */
	public static void blessOnce(final LivingEntity wearer) {
		int blessed = pieces(wearer, RefineSpecials.Special.BLESSED);
		if (blessed > 0 && wearer.isAlive() && wearer.getHealth() < wearer.getMaxHealth()) {
			wearer.heal(HealthScale.vanillaMaxHealth(wearer) * (float) BLESSING_SHARE * blessed);
		}
	}

	/** How many of the pieces an entity wears, each in its own slot, have the effect at their refine level. */
	public static int pieces(final LivingEntity wearer, final RefineSpecials.Special special) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack piece = wearer.getItemBySlot(slot);
			if (!piece.isEmpty() && Refine.isArmor(piece) && Refine.armorSlotOf(piece) == slot && RefineSpecials.has(piece, special)) {
				pieces++;
			}
		}
		return pieces;
	}
}
