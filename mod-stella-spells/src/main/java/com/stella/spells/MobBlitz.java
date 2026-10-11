package com.stella.spells;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/**
 * Blitz Beat for a mob. Another mod hands a mob the buff through the {@code mod-stella-spells:mob-blitz-beat}
 * share, a {@code Consumer<Mob>}. The buff then never ends. The mob keeps a falcon, its own melee and arrow
 * hits roll the falcon at the base chance of a player's, and each strike lands on the struck foe at the mob
 * attack damage. A strike never lands on the mob itself or on an ally of the mob.
 */
final class MobBlitz {
	static final String SHARE_KEY = "mod-stella-spells:mob-blitz-beat";
	/** A projectile with this tag never calls the falcon, so a mob's volley can stay out of the roll. */
	public static final String NO_BLITZ_TAG = "mod-stella-spells.no_blitz";
	private static final int CHECK_INTERVAL_TICKS = 10;

	/** Mobs that hold the buff for good, by id; the mob itself is the truth, this only finds it. */
	private static final Set<UUID> HOLDERS = ConcurrentHashMap.newKeySet();
	private static final List<Strike> STRIKES = new CopyOnWriteArrayList<>();
	/** Set while a falcon strike lands, so the strike is never read as a fresh blow of the mob. */
	private static final ThreadLocal<Boolean> STRIKING = new ThreadLocal<>();
	private static volatile int clock;
	/** The test seam for the falcon roll, per mob so side-by-side cases cannot clear each other. */
	private static final java.util.Map<UUID, RandomSource> ROLLS = new ConcurrentHashMap<>();

	private record Strike(UUID owner, UUID foe, int dueTick, float amount) {
	}

	private MobBlitz() {
	}

	static void register() {
		FabricLoader.getInstance().getObjectShare().put(SHARE_KEY, (Consumer<Mob>) MobBlitz::grant);
		ServerTickEvents.END_SERVER_TICK.register(MobBlitz::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isPermanent(mob)) {
				HOLDERS.add(mob.getUUID());
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (HOLDERS.remove(entity.getUUID())) {
				Falcons.forget(entity.getUUID());
			}
		});
	}

	static void clear() {
		HOLDERS.clear();
		STRIKES.clear();
		ROLLS.clear();
		clock = 0;
	}

	/** Pins the roll of one mob; a null source returns it to its own. */
	static void setRandom(final Mob mob, final @Nullable RandomSource source) {
		if (source == null) {
			ROLLS.remove(mob.getUUID());
		} else {
			ROLLS.put(mob.getUUID(), source);
		}
	}

	static boolean tracks(final UUID mob) {
		return HOLDERS.contains(mob);
	}

	private static boolean isPermanent(final Mob mob) {
		MobEffectInstance buff = mob.getEffect(SpellStatus.BLITZ_BEAT);
		return buff != null && buff.isInfiniteDuration();
	}

	/** The share: the mob holds Blitz Beat for good and gets its falcon. */
	static void grant(final Mob mob) {
		mob.addEffect(new MobEffectInstance(SpellStatus.BLITZ_BEAT, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		HOLDERS.add(mob.getUUID());
		// A mob not yet in its level gets its falcon from the load event and the next check.
		if (mob.level() instanceof ServerLevel level && level.getEntity(mob.getUUID()) == mob) {
			Falcons.ensure(mob);
		}
		Spellbooks.LOGGER.info("[SPELL] event=MOB_BLITZ_BEAT mob={} type={}", mob.getUUID(), mob.getType());
	}

	private static void tick(final MinecraftServer server) {
		int now = ++clock;
		for (Strike strike : List.copyOf(STRIKES)) {
			if (now >= strike.dueTick()) {
				STRIKES.remove(strike);
				run(server, strike);
			}
		}
		if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		for (UUID id : Set.copyOf(HOLDERS)) {
			Mob mob = find(server, id);
			if (mob == null) {
				continue;
			}
			if (!mob.isAlive() || !mob.hasEffect(SpellStatus.BLITZ_BEAT)) {
				HOLDERS.remove(id);
				Falcons.forget(id);
			} else {
				Falcons.ensure(mob);
			}
		}
	}

	private static @Nullable Mob find(final MinecraftServer server, final UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Mob mob) {
				return mob;
			}
		}
		return null;
	}

	/**
	 * A blow of the mob own, melee or arrow, may call the falcon: the same roll, strike count and chance as a
	 * player's, with the mob attack damage for each strike. Skill damage and the falcon strike itself never do.
	 */
	static void afterBlow(final Mob owner, final LivingEntity victim, final DamageSource source) {
		if (STRIKING.get() != null || MobImbue.casting() || !owner.isAlive() || !victim.isAlive() || !owner.hasEffect(SpellStatus.BLITZ_BEAT)) {
			return;
		}
		if (source.getDirectEntity() != null && source.getDirectEntity().entityTags().contains(NO_BLITZ_TAG)) {
			return;
		}
		boolean arrow = source.getDirectEntity() instanceof AbstractArrow shot && shot.getOwner() == owner;
		boolean melee = source.getDirectEntity() == owner && (SpellStatus.isMelee(source) || source.is(DamageTypes.MOB_ATTACK));
		if ((!arrow && !melee) || allied(owner, victim) || !(owner.level() instanceof ServerLevel level)) {
			return;
		}
		MobEffectInstance buff = owner.getEffect(SpellStatus.BLITZ_BEAT);
		RandomSource roll = ROLLS.getOrDefault(owner.getUUID(), owner.getRandom());
		if (roll.nextDouble() >= ArcherSkills.blitzChance(owner, arrow)) {
			return;
		}
		int base = arrow ? ArcherSkills.BLITZ_BEAT_BOW_STRIKES : ArcherSkills.BLITZ_BEAT_STRIKES;
		int strikes = buff.getAmplifier() >= 1 ? base * ArcherSkills.AWAKENED_BLITZ_BEAT_FACTOR : base;
		float amount = strikeDamage(owner);
		for (int strike = 0; strike < strikes; strike++) {
			STRIKES.add(new Strike(owner.getUUID(), victim.getUUID(), clock + 1 + strike * ArcherSkills.BLITZ_BEAT_INTERVAL_TICKS, amount));
		}
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.8F, 1.6F);
		Spellbooks.LOGGER.info("[SPELL] event=MOB_BLITZ_BEAT_CALL mob={} foe={} strikes={} ranged={} amount={}",
			owner.getUUID(), victim.getUUID(), strikes, arrow, amount);
	}

	/** What one falcon strike deals: the mob attack damage, else the book own number for a mob that has none. */
	static float strikeDamage(final Mob owner) {
		return owner.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
			? (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
			: SpellNumbers.HUNTERS_MARK.damage();
	}

	/**
	 * Who the falcon spares: the mob itself, a teammate, and any other foe of the players while the mob is one
	 * too. A friend of the village is a foe of such a mob, so the falcon strikes it.
	 */
	static boolean allied(final Mob owner, final LivingEntity victim) {
		return victim == owner || owner.isAlliedTo(victim) || (Friends.isFoe(owner) && Friends.isFoe(victim));
	}

	private static void run(final MinecraftServer server, final Strike strike) {
		Mob owner = find(server, strike.owner());
		if (owner == null || !owner.isAlive() || !owner.hasEffect(SpellStatus.BLITZ_BEAT) || !(owner.level() instanceof ServerLevel level)) {
			return;
		}
		Entity found = level.getEntity(strike.foe());
		if (!(found instanceof LivingEntity foe) || !foe.isAlive() || allied(owner, foe) || (foe instanceof Player player && player.isSpectator())) {
			return;
		}
		var at = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.1, 0.3, 0.1);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 0.4F, 1.8F);
		if (foe.damageCooldownTime > 0) {
			foe.damageCooldownTime = 0;
		}
		Falcons.swoop(owner, foe);
		STRIKING.set(Boolean.TRUE);
		try {
			foe.hurtServer(level, level.damageSources().mobAttack(owner), strike.amount());
		} finally {
			STRIKING.remove();
		}
	}
}
