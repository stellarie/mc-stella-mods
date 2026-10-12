package com.stella.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ten priest books and everything they leave running: the holy damage source, the holy bonus a holder
 * of Holy Arms deals, the blow a Guardian Angel shares with its priest, and the ground Consecrate keeps.
 * <p>
 * Two rules hold for every path here. {@link MoreSpells#isAlly} decides who may be helped, and it asks
 * {@link Friends#isFoe} first, so a hostile-tagged mob is never heartened. And no path damages an undead
 * before asking {@link SummonSkeleton#isServant}: a summoned servant is a skeleton, so the undead tag alone
 * would hand this stage's doubled holy damage back to the servant W023 made immune to it.
 */
public final class PriestSkills {
	/** The holy damage type, which the rpg mod reads as magic and vanilla lets past armor. */
	public static final ResourceKey<DamageType> HOLY = ResourceKey.create(
		Registries.DAMAGE_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "holy")
	);

	/** Holy Light: the flat heal, and the ward an awakened overheal becomes. */
	public static final float HOLY_LIGHT_HEAL = 6.0F;
	public static final double HOLY_LIGHT_WARD_SHARE = 0.5;
	/** Prayer of Mending: one heal a jump, and how long a jump waits. */
	public static final float PRAYER_HEAL = 3.0F;
	public static final int PRAYER_JUMPS = 4;
	public static final int PRAYER_AWAKENED_JUMPS = 12;
	public static final int PRAYER_JUMP_TICKS = 10;
	public static final double PRAYER_RADIUS = 8.0;
	/** Renew: a pulse a period, for a span. */
	public static final float RENEW_HEAL = 1.0F;
	public static final int RENEW_PERIOD_TICKS = 40;
	public static final int RENEW_TICKS = 600;
	public static final double RENEW_AWAKENED_RADIUS = 6.0;
	public static final int RENEW_AWAKENED_ALLIES = 3;
	/** Divine Shield: a ward of this many heals, for this long. */
	public static final float DIVINE_SHIELD_WARD = 8.0F;
	public static final int DIVINE_SHIELD_TICKS = 600;
	public static final double DIVINE_SHIELD_AWAKENED_RADIUS = 5.0;
	/** Holy Arms: the bonus a hit carries, twice against the undead, over this reach and this long. */
	public static final float HOLY_ARMS_BONUS = 1.0F;
	public static final float HOLY_ARMS_UNDEAD_FACTOR = 2.0F;
	public static final double HOLY_ARMS_RADIUS = 8.0;
	public static final int HOLY_ARMS_TICKS = 36_000;
	public static final int HOLY_ARMS_NOVA_EVERY = 5;
	public static final double HOLY_ARMS_NOVA_RADIUS = 3.0;
	public static final float HOLY_ARMS_NOVA_DAMAGE = 1.0F;
	public static final float HOLY_ARMS_AWAKENED_HEAL_SHARE = 0.10F;
	/** Blessing of Fortitude: Health Boost II, and on the awakened cast two more. */
	public static final int FORTITUDE_TICKS = 36_000;
	public static final int FORTITUDE_AMPLIFIER = 1;
	public static final double FORTITUDE_RADIUS = 8.0;
	/** Smite: the holy hit, twice against the undead, and the chain an awakened cast adds. */
	public static final float SMITE_DAMAGE = 4.0F;
	public static final float SMITE_UNDEAD_FACTOR = 2.0F;
	public static final double SMITE_CHAIN_RADIUS = 8.0;
	public static final int SMITE_CHAIN_TARGETS = 3;
	/** Resurrection Ward: how long it waits, and what is left of the ally it saves. */
	public static final int RESURRECTION_WARD_TICKS = 6000;
	public static final float RESURRECTION_WARD_HEALTH_SHARE = 0.30F;
	/** Consecrate: the ground, its reach, its span, and what a pulse does. */
	public static final double CONSECRATE_RADIUS = 4.0;
	public static final int CONSECRATE_TICKS = 600;
	public static final int CONSECRATE_PERIOD_TICKS = 20;
	public static final float CONSECRATE_HEAL = 1.0F;
	public static final float CONSECRATE_DAMAGE = 2.0F;
	/** Guardian Angel: the share of a blow the priest takes, and how long the link holds. */
	public static final float GUARDIAN_SHARE = 0.30F;
	/** The most of a blow a refined Guardian Angel hands to its priest. */
	static final double GUARDIAN_MAX_SHARE = 0.60;
	public static final int GUARDIAN_ANGEL_TICKS = 600;

	/** A holder's holy bonus, and the tick it lapses. Keyed by holder, cleared on death and on server stop. */
	private record HolyBonus(float amount, int endsAt) {
	}

	/** A blow one ally takes for another: a share of it goes to the priest. */
	private record GuardianLink(UUID priest, float share, int endsAt) {
	}

	/** Holy ground, and the tick of its next pulse. */
	record Ground(ResourceKey<net.minecraft.world.level.Level> dimension, UUID caster, Vec3 center, double radius,
		int endsAt, int nextPulse, double power) {
	}

	/** A renew running on one ally, and the tick of its next pulse. */
	record Renew(UUID caster, float heal, int endsAt, int nextPulse) {
	}

	private static final Map<UUID, HolyBonus> HOLY_ARMS = new ConcurrentHashMap<>();
	private static final Map<UUID, GuardianLink> GUARDIANS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> WARDS = new ConcurrentHashMap<>();
	private static final List<Ground> GROUNDS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final Map<UUID, Renew> RENEWS = new ConcurrentHashMap<>();
	/** How many holy bonuses each holder has dealt; every fifth releases the awakened nova. */
	private static final Map<UUID, Integer> HOLY_HITS = new ConcurrentHashMap<>();
	/** Guards a redirected blow from being redirected again: one blow, one share. */
	private static final ThreadLocal<Boolean> REDIRECTING = new ThreadLocal<>();

	private PriestSkills() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PriestSkills::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PriestSkills::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PriestSkills::allowDeath);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> forget());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> HOLY_ARMS.remove(entity.getUUID()));
	}

	/** Every table this class keeps, dropped when the server stops. */
	static void forget() {
		HOLY_ARMS.clear();
		GUARDIANS.clear();
		WARDS.clear();
		GROUNDS.clear();
		RENEWS.clear();
		HOLY_HITS.clear();
	}

	/** The holy source of a hit: named for its caster, so the rpg mod reads penetration and crit from them. */
	static net.minecraft.world.damagesource.DamageSource holy(final LivingEntity caster) {
		Holder<DamageType> type = caster.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HOLY);
		return new net.minecraft.world.damagesource.DamageSource(type, caster, caster);
	}

	/**
	 * Whether this path may damage this body with holy or undead work. A servant is a skeleton, so the undead
	 * tag matches it; W023 made a servant immune, and this is the one gate that keeps it so.
	 */
	static boolean mayDamage(final LivingEntity target) {
		return !SummonSkeleton.isServant(target);
	}

	/** A holy hit: the caster's VIT at the flat number, doubled against the undead, never on a servant. */
	static float holyHit(final LivingEntity caster, final LivingEntity target, final float flat, final double power) {
		if (!mayDamage(target)) {
			return 0.0F;
		}
		return RpgBridge.skillHeal(caster, School.PRIEST, flat, power) * undeadFactor(target);
	}

	/** Twice against the undead, once against anything else. */
	static float undeadFactor(final LivingEntity target) {
		return target.is(EntityTypeTags.UNDEAD) ? HOLY_ARMS_UNDEAD_FACTOR : 1.0F;
	}

	// ---- the books ----

	/** Holy Light: heals the ally; an awakened overheal becomes a ward of half its max health. */
	static boolean applyHolyLight(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		float heal = RpgBridge.skillHeal(caster, School.PRIEST, HOLY_LIGHT_HEAL, modifiers.powerMultiplier());
		float before = ally.getHealth();
		CastHits.heal(caster, ally, heal);
		if (modifiers.awakened() && ally.getHealth() >= ally.getMaxHealth()) {
			float spare = heal - (ally.getMaxHealth() - before);
			if (spare > 0.0F) {
				float cap = (float) (ally.getMaxHealth() * HOLY_LIGHT_WARD_SHARE);
				SpellStatusApi.ward(ally, Math.min(spare, cap), DIVINE_SHIELD_TICKS);
			}
		}
		return true;
	}

	/** Prayer of Mending: heals the ally, then bounces to the most wounded ally near, a jump at a time. */
	static boolean applyPrayerOfMending(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		int jumps = modifiers.awakened() ? PRAYER_AWAKENED_JUMPS : PRAYER_JUMPS;
		int now = caster.level().getServer().getTickCount();
		PRAYERS.add(new Prayer(caster.getUUID(), ally.getUUID(), RpgBridge.skillHeal(caster, School.PRIEST, PRAYER_HEAL, modifiers.powerMultiplier()),
			jumps, now + PRAYER_JUMP_TICKS, now, modifiers.awakened()));
		return true;
	}

	/** Renew: heals the ally every period for the span; an awakened cast renews the nearest allies too. */
	static boolean applyRenew(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		float heal = RpgBridge.skillHeal(caster, School.PRIEST, RENEW_HEAL, modifiers.powerMultiplier());
		int now = caster.level().getServer().getTickCount();
		renew(caster, ally, heal, now);
		if (modifiers.awakened()) {
			List<LivingEntity> near = SkillTargeting.alliesAround(caster, RENEW_AWAKENED_RADIUS);
			int done = 0;
			for (LivingEntity other : near) {
				if (other != ally && done < RENEW_AWAKENED_ALLIES) {
					renew(caster, other, heal, now);
					done++;
				}
			}
		}
		return true;
	}

	private static void renew(final ServerPlayer caster, final LivingEntity ally, final float heal, final int now) {
		RENEWS.put(ally.getUUID(), new Renew(caster.getUUID(), heal, now + RENEW_TICKS, now + RENEW_PERIOD_TICKS));
	}

	/** Divine Shield: a ward on the ally; an awakened shield heals its neighbours when the ward ends. */
	static boolean applyDivineShield(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		float amount = RpgBridge.skillHeal(caster, School.PRIEST, DIVINE_SHIELD_WARD, modifiers.powerMultiplier());
		SpellStatusApi.ward(ally, amount, DIVINE_SHIELD_TICKS);
		if (modifiers.awakened()) {
			SHIELDS.put(ally.getUUID(), new Shield(caster.getUUID(), DIVINE_SHIELD_TICKS));
		} else {
			SHIELDS.remove(ally.getUUID());
		}
		return true;
	}

	/** Holy Arms: the caster and every ally near carry the holy bonus; an awakened one novas and mends. */
	static boolean applyHolyArms(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		int now = caster.level().getServer().getTickCount();
		float bonus = RpgBridge.skillHeal(caster, School.PRIEST, HOLY_ARMS_BONUS, modifiers.powerMultiplier());
		giveHolyArms(caster, bonus, now);
		for (LivingEntity ally : SkillTargeting.alliesAround(caster, HOLY_ARMS_RADIUS)) {
			giveHolyArms(ally, bonus, now);
		}
		caster.level().playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		return true;
	}

	private static void giveHolyArms(final LivingEntity holder, final float bonus, final int now) {
		holder.addEffect(new MobEffectInstance(SpellStatus.HOLY_ARMS, HOLY_ARMS_TICKS, 0, false, true), holder);
		HOLY_ARMS.put(holder.getUUID(), new HolyBonus(bonus, now + HOLY_ARMS_TICKS));
	}

	/** Blessing of Fortitude: Health Boost II on the caster and every ally near; awakened adds two more. */
	static boolean applyBlessingOfFortitude(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		bless(caster, caster, modifiers);
		for (LivingEntity ally : SkillTargeting.alliesAround(caster, FORTITUDE_RADIUS)) {
			bless(caster, ally, modifiers);
		}
		return true;
	}

	private static void bless(final ServerPlayer caster, final LivingEntity ally, final SpellModifiers.CastModifiers modifiers) {
		ally.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, FORTITUDE_TICKS, FORTITUDE_AMPLIFIER), caster);
		if (modifiers.awakened()) {
			ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, FORTITUDE_TICKS, 0), caster);
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FORTITUDE_TICKS, 0), caster);
		}
	}

	/** Smite: a holy hit, doubled against the undead; an awakened cast chains to undead near the mark. */
	static boolean applySmite(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (target == null || !mayDamage(target)) {
			return false;
		}
		hitHoly(caster, target, SMITE_DAMAGE, modifiers);
		if (modifiers.awakened()) {
			List<LivingEntity> chain = new ArrayList<>(caster.level().getEntitiesOfClass(
				LivingEntity.class,
				target.getBoundingBox().inflate(SMITE_CHAIN_RADIUS),
				candidate -> candidate != target && candidate.isAlive() && candidate.is(EntityTypeTags.UNDEAD)
					&& mayDamage(candidate) && candidate.distanceToSqr(target) <= SMITE_CHAIN_RADIUS * SMITE_CHAIN_RADIUS
			));
			chain.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(target)));
			for (int i = 0; i < Math.min(SMITE_CHAIN_TARGETS, chain.size()); i++) {
				hitHoly(caster, chain.get(i), SMITE_DAMAGE, modifiers);
			}
		}
		return true;
	}

	/** One holy hit through the shared gate. */
	static void hitHoly(final ServerPlayer caster, final LivingEntity target, final float flat, final SpellModifiers.CastModifiers modifiers) {
		if (!mayDamage(target)) {
			return;
		}
		target.hurtServer((ServerLevel) caster.level(), holy(caster), holyHit(caster, target, flat, modifiers.powerMultiplier()));
	}

	/** Resurrection Ward: the ally's first lethal blow leaves them standing, and spends the ward. */
	static boolean applyResurrectionWard(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		int now = caster.level().getServer().getTickCount();
		ally.addEffect(new MobEffectInstance(SpellStatus.RESURRECTION_WARD, RESURRECTION_WARD_TICKS, 0), caster);
		WARDS.put(ally.getUUID(), now + RESURRECTION_WARD_TICKS);
		if (modifiers.awakened()) {
			FULL_WARDS.add(ally.getUUID());
		} else {
			FULL_WARDS.remove(ally.getUUID());
		}
		return true;
	}

	/** Guardian Angel: a share of every blow on the ally goes to the priest; awakened, it mends the priest. */
	static boolean applyGuardianAngel(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		LivingEntity ally = CastContext.ally();
		if (ally == null) {
			return false;
		}
		int now = caster.level().getServer().getTickCount();
		ally.addEffect(new MobEffectInstance(SpellStatus.GUARDIAN_ANGEL, GUARDIAN_ANGEL_TICKS, 0), caster);
		float share = (float) Math.min(GUARDIAN_MAX_SHARE, GUARDIAN_SHARE * BookRefine.statFactor(modifiers.refineLevel()));
		GUARDIANS.put(ally.getUUID(), new GuardianLink(caster.getUUID(), share, now + GUARDIAN_ANGEL_TICKS));
		if (modifiers.awakened()) {
			MENDING_LINKS.add(ally.getUUID());
		} else {
			MENDING_LINKS.remove(ally.getUUID());
		}
		return true;
	}

	/** Consecrate: holy ground where the caster aims; an awakened ground follows the caster. */
	static boolean applyConsecrate(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		if (point == null) {
			return false;
		}
		int now = caster.level().getServer().getTickCount();
		GROUNDS.add(new Ground(caster.level().dimension(), caster.getUUID(), point, CONSECRATE_RADIUS,
			now + CONSECRATE_TICKS, now + CONSECRATE_PERIOD_TICKS, modifiers.powerMultiplier()));
		if (modifiers.awakened()) {
			FOLLOWING_GROUNDS.add(caster.getUUID());
		} else {
			FOLLOWING_GROUNDS.remove(caster.getUUID());
		}
		caster.level().playSound(null, net.minecraft.core.BlockPos.containing(point), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.1F);
		return true;
	}

	// ---- what the books leave running ----

	private record Prayer(UUID caster, UUID target, float heal, int jumpsLeft, int nextJump, int startedAt, boolean awakened) {
	}

	private record Shield(UUID caster, int ticksLeft) {
	}

	private static final List<Prayer> PRAYERS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final Map<UUID, Shield> SHIELDS = new ConcurrentHashMap<>();
	private static final java.util.Set<UUID> FULL_WARDS = ConcurrentHashMap.newKeySet();
	private static final java.util.Set<UUID> MENDING_LINKS = ConcurrentHashMap.newKeySet();
	private static final java.util.Set<UUID> FOLLOWING_GROUNDS = ConcurrentHashMap.newKeySet();

	private static void tick(final MinecraftServer server) {
		tick(server, server.getTickCount());
	}

	private static void tick(final MinecraftServer server, final int now) {
		tickPrayers(server, now);
		tickRenews(server, now);
		tickGrounds(server, now);
		tickShields(server, now);
		HOLY_ARMS.entrySet().removeIf(entry -> now >= entry.getValue().endsAt());
		GUARDIANS.entrySet().removeIf(entry -> now >= entry.getValue().endsAt());
		WARDS.entrySet().removeIf(entry -> now >= entry.getValue());
	}
	/** A jump: the most wounded ally within reach of the last one, every jump period. */
	private static void tickPrayers(final MinecraftServer server, final int now) {
		for (Prayer prayer : new ArrayList<>(PRAYERS)) {
			if (now < prayer.nextJump()) {
				continue;
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(prayer.caster());
			if (caster == null || prayer.jumpsLeft() <= 0) {
				PRAYERS.remove(prayer);
				continue;
			}
			LivingEntity target = findJumpTarget(server, prayer);
			if (target == null) {
				PRAYERS.remove(prayer);
				continue;
			}
			CastHits.heal(caster, target, prayer.heal());
			PRAYERS.remove(prayer);
			PRAYERS.add(new Prayer(prayer.caster(), target.getUUID(), prayer.heal(), prayer.jumpsLeft() - 1,
				now + PRAYER_JUMP_TICKS, prayer.startedAt(), prayer.awakened()));
		}
	}

	/** The ally the next jump reaches: a wounded ally near the last one, or none to stop on. */
	private static @Nullable LivingEntity findJumpTarget(final MinecraftServer server, final Prayer prayer) {
		for (var level : server.getAllLevels()) {
			var last = level.getEntity(prayer.target());
			if (!(last instanceof LivingEntity body)) {
				continue;
			}
			List<LivingEntity> near = new ArrayList<>(level.getEntitiesOfClass(
				LivingEntity.class,
				body.getBoundingBox().inflate(PRAYER_RADIUS),
				candidate -> candidate.isAlive() && candidate.getHealth() < candidate.getMaxHealth()
					&& candidate.position().distanceToSqr(body.position()) <= PRAYER_RADIUS * PRAYER_RADIUS
			));
			near.sort(Comparator.comparingDouble(LivingEntity::getHealth));
			return near.isEmpty() ? null : near.get(0);
		}
		return null;
	}

	private static void tickRenews(final MinecraftServer server, final int now) {
		for (Map.Entry<UUID, Renew> entry : RENEWS.entrySet()) {
			Renew renew = entry.getValue();
			if (now >= renew.endsAt()) {
				RENEWS.remove(entry.getKey());
				continue;
			}
			if (now < renew.nextPulse()) {
				continue;
			}
			RENEWS.put(entry.getKey(), new Renew(renew.caster(), renew.heal(), renew.endsAt(), now + RENEW_PERIOD_TICKS));
			ServerPlayer caster = server.getPlayerList().getPlayer(renew.caster());
			LivingEntity ally = findAnywhere(server, entry.getKey());
			if (caster != null && ally != null && ally.isAlive()) {
				CastHits.heal(caster, ally, renew.heal());
			}
		}
	}

	private static void tickGrounds(final MinecraftServer server, final int now) {
		for (Ground ground : new ArrayList<>(GROUNDS)) {
			if (now >= ground.endsAt()) {
				GROUNDS.remove(ground);
				CONTINUED.remove(ground);
				continue;
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(ground.caster());
			if (caster == null) {
				GROUNDS.remove(ground);
				CONTINUED.remove(ground);
				continue;
			}
			if (FOLLOWING_GROUNDS.contains(ground.caster())) {
				Ground moved = new Ground(ground.dimension(), ground.caster(), caster.position(), ground.radius(), ground.endsAt(), ground.nextPulse(), ground.power());
				if (!ground.equals(moved)) {
					GROUNDS.remove(ground);
					CONTINUED.remove(ground);
					GROUNDS.add(moved);
					ground = moved;
				}
			}
			if (now < ground.nextPulse()) {
				continue;
			}
			Ground pulsed = new Ground(ground.dimension(), ground.caster(), ground.center(), ground.radius(), ground.endsAt(), now + CONSECRATE_PERIOD_TICKS, ground.power());
			GROUNDS.remove(ground);
			CONTINUED.remove(ground);
			GROUNDS.add(pulsed);
			pulseGround(caster, pulsed);
		}
	}

	private static final java.util.Set<Ground> CONTINUED = ConcurrentHashMap.newKeySet();

	/** One pulse of holy ground: allies mend, undead burn, and a servant is left out of both. */
	private static void pulseGround(final ServerPlayer caster, final Ground ground) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return;
		}
		AABB box = new AABB(ground.center(), ground.center()).inflate(ground.radius());
		for (LivingEntity found : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (found.position().distanceToSqr(ground.center()) > ground.radius() * ground.radius()) {
				continue;
			}
			if (SkillTargeting.isAlly(caster, found)) {
				CastHits.heal(caster, found, RpgBridge.skillHeal(caster, School.PRIEST, CONSECRATE_HEAL, ground.power()));
				level.sendParticles(ParticleTypes.END_ROD, found.getX(), found.getY() + 1.0, found.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
				continue;
			}
			if (Friends.isFoe(found) && mayDamage(found)) {
				found.hurtServer(level, holy(caster), holyHit(caster, found, CONSECRATE_DAMAGE, ground.power()));
			}
		}
	}

	/** An awakened Divine Shield heals the ally's neighbours the moment its ward ends. */
	private static void tickShields(final MinecraftServer server, final int now) {
		for (Map.Entry<UUID, Shield> entry : SHIELDS.entrySet()) {
			LivingEntity ally = findAnywhere(server, entry.getKey());
			if (ally == null || ally.hasEffect(SpellStatus.ARCANE_WARD)) {
				continue;
			}
			SHIELDS.remove(entry.getKey());
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getValue().caster());
			if (caster != null && ally.isAlive()) {
				for (LivingEntity near : SkillTargeting.alliesAround(caster, DIVINE_SHIELD_AWAKENED_RADIUS)) {
					CastHits.heal(caster, near, RpgBridge.skillHeal(caster, School.PRIEST, DIVINE_SHIELD_WARD, 1.0));
				}
			}
		}
	}

	private static @Nullable LivingEntity findAnywhere(final MinecraftServer server, final UUID id) {
		for (var level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof LivingEntity found) {
				return found;
			}
		}
		return null;
	}

	/** A servant is a skeleton: no holy path touches one, whatever the undead tag says. */
	static boolean servantSafe(final LivingEntity target) {
		return !SummonSkeleton.isServant(target);
	}

	// ---- the holy bonus, the shared blow, and the ward ----

	private static void afterDamage(final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source, final float taken, final float blocked, final boolean blockedByShield) {
		holyArmsBonus(victim, source, taken);
	}

	/** A holder's own hit carries the holy bonus; a holy hit never triggers another, so it cannot recurse. */
	private static void holyArmsBonus(final LivingEntity victim, final net.minecraft.world.damagesource.DamageSource source, final float taken) {
		if (taken <= 0.0F || source.is(HOLY) || !(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}
		HolyBonus bonus = HOLY_ARMS.get(attacker.getUUID());
		if (bonus == null || !attacker.hasEffect(SpellStatus.HOLY_ARMS)) {
			return;
		}
		if (!mayDamage(victim) || !(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		float hit = bonus.amount() * undeadFactor(victim);
		victim.hurtServer(level, holy(attacker), hit);
		int hits = HOLY_HITS.merge(attacker.getUUID(), 1, Integer::sum);
		if (hits % HOLY_ARMS_NOVA_EVERY == 0 && attacker instanceof ServerPlayer priest) {
			holyNova(priest, victim);
		}
	}

	/** Every fifth holy hit releases a nova on the foes near the mark; an awakened Arms mends its holder. */
	private static void holyNova(final ServerPlayer priest, final LivingEntity mark) {
		ServerLevel level = (ServerLevel) priest.level();
		for (LivingEntity foe : level.getEntitiesOfClass(
			LivingEntity.class,
			mark.getBoundingBox().inflate(HOLY_ARMS_NOVA_RADIUS),
			candidate -> candidate.isAlive() && Friends.isFoe(candidate) && mayDamage(candidate)
				&& candidate.position().distanceToSqr(mark.position()) <= HOLY_ARMS_NOVA_RADIUS * HOLY_ARMS_NOVA_RADIUS
		)) {
			foe.hurtServer(level, holy(priest), RpgBridge.skillHeal(priest, School.PRIEST, HOLY_ARMS_NOVA_DAMAGE, 1.0));
		}
		// The nova is a light, and a light is drawn where it lands: one burst at the mark, seen by everyone near.
		level.sendParticles(ParticleTypes.END_ROD, mark.getX(), mark.getY() + 1.0, mark.getZ(), 24, 0.6, 0.6, 0.6, 0.05);
		if (MENDING_LINKS.contains(priest.getUUID())) {
			CastHits.heal(priest, priest, RpgBridge.skillHeal(priest, School.PRIEST, HOLY_ARMS_NOVA_DAMAGE, 1.0) * HOLY_ARMS_AWAKENED_HEAL_SHARE);
		}
	}

	/** An ally under a Guardian Angel shares this blow with its priest, through the damage path. */
	static float guardianShare(final LivingEntity victim, final ServerLevel level, final net.minecraft.world.damagesource.DamageSource source, final float amount) {
		if (amount <= 0.0F || Boolean.TRUE.equals(REDIRECTING.get())) {
			return amount;
		}
		GuardianLink link = GUARDIANS.get(victim.getUUID());
		if (link == null || !victim.hasEffect(SpellStatus.GUARDIAN_ANGEL)) {
			return amount;
		}
		ServerPlayer priest = level.getServer().getPlayerList().getPlayer(link.priest());
		if (priest == null || !priest.isAlive() || priest.level() != victim.level()) {
			return amount;
		}
		float share = amount * link.share();
		REDIRECTING.set(Boolean.TRUE);
		try {
			if (MENDING_LINKS.contains(victim.getUUID())) {
				CastHits.heal(priest, priest, share);
			} else {
				priest.hurtServer(level, holy(priest), share);
			}
		} finally {
			REDIRECTING.remove();
		}
		return amount - share;
	}

	/** The first lethal blow on a warded ally leaves them standing, and spends the ward. */
	private static boolean allowDeath(final LivingEntity entity, final net.minecraft.world.damagesource.DamageSource source, final float amount) {
		Integer endsAt = WARDS.get(entity.getUUID());
		if (endsAt == null || !entity.hasEffect(SpellStatus.RESURRECTION_WARD)) {
			return true;
		}
		WARDS.remove(entity.getUUID());
		boolean full = FULL_WARDS.remove(entity.getUUID());
		entity.removeEffect(SpellStatus.RESURRECTION_WARD);
		float health = full ? entity.getMaxHealth() : entity.getMaxHealth() * RESURRECTION_WARD_HEALTH_SHARE;
		entity.setHealth(Math.max(1.0F, health));
		if (full) {
			for (MobEffectInstance instance : List.copyOf(entity.getActiveEffects())) {
				if (instance.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
					entity.removeEffect(instance.getEffect());
				}
			}
		}
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, entity.getX(), entity.getY() + 1.0, entity.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
		}
		return false;
	}

	/** Whether this ally is warded right now; a test seam. */
	static boolean warded(final UUID ally) {
		return WARDS.containsKey(ally);
	}

	/** Whether this holder carries the holy bonus right now; a test seam. */
	static boolean holyArmed(final UUID holder) {
		return HOLY_ARMS.containsKey(holder);
	}

	/** Whether this ally has a Guardian Angel over it right now; a test seam. */
	static boolean guarded(final UUID ally) {
		return GUARDIANS.containsKey(ally);
	}

	/** The ground running now; a test seam. */
	static List<Ground> grounds() {
		return List.copyOf(GROUNDS);
	}

	/** The renews running now; a test seam. */
	static Map<UUID, Renew> renews() {
		return Map.copyOf(RENEWS);
	}

	/** A player who is never a foe of the caster, for a case that needs an ally in the world. */
	static boolean allyOf(final ServerPlayer caster, final LivingEntity candidate) {
		return SkillTargeting.isAlly(caster, candidate);
	}

	/** The ally one cast would resolve, for a case that reads the rule without casting. */
	static LivingEntity resolvedAlly(final ServerPlayer caster, final int range) {
		return SpellCasting.findAlly(caster, range);
	}

	/** Unused parameter kept for a readable call site: the mob a case passes is not the ally. */
	static boolean heals(final LivingEntity ally) {
		return ally instanceof Player || MoreSpells.isAlly(ally);
	}

	/** Runs one tick of everything the books leave behind the given moment. */
	static void tickAt(final MinecraftServer server, final int tick) {
		tick(server, tick);
	}

	/** Unbroken Vow: the caster's gear spends one durability per level of Vitality and Intelligence of wear. */
	static boolean applyUnbrokenVow(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(new MobEffectInstance(SpellStatus.UNBROKEN_VOW, UnbrokenVow.VOW_TICKS, 0), caster);
		return true;
	}

	/** Steadfast Prayer: no harmful status lands on the caster, and the caster's blows are worth a tenth. */
	static boolean applySteadfastPrayer(final ServerPlayer caster, final Mob target, final Vec3 point, final SpellModifiers.CastModifiers modifiers) {
		caster.addEffect(new MobEffectInstance(SpellStatus.STEADFAST_PRAYER, SteadfastPrayer.PRAYER_TICKS, 0), caster);
		return true;
	}
}
