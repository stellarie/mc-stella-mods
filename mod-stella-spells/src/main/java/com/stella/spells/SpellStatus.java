package com.stella.spells;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The nine status effects of the spells, and the rules they add to damage and healing. A spell reaches them
 * only through {@link SpellStatusApi}; the damage mixin reaches {@link #onHurt} and {@link #onHeal}.
 */
public final class SpellStatus {
	/** Marks a mob whose NoAI a stun set, so the stun's end clears only what the stun itself set. */
	public static final String STUN_TAG = Spellbooks.MOD_ID + ".stun_noai";
	/** The pool of an Arcane Ward; the effect is its timer and its icon. */
	public static final AttachmentType<Float> WARD_POOL = AttachmentRegistry.createPersistent(id("arcane_ward"), Codec.FLOAT);
	public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("bleeding"));

	/** A player hit on a Frozen or Brittle mob deals this many times its damage. */
	static final float SHATTER_FACTOR = 1.5F;
	static final float MARKED_FACTOR = 1.2F;
	static final float SOAKED_LIGHTNING_FACTOR = 1.5F;
	static final float SCORCHED_HEAL_FACTOR = 0.5F;
	static final float BLEED_PERCENT_PER_LEVEL = 0.02F;
	static final float BLEED_MINIMUM = 0.5F;
	static final int BLEED_INTERVAL_TICKS = 20;

	public static final Holder<MobEffect> CHILLED = register("chilled", new Status(MobEffectCategory.HARMFUL, 0xA8E4F0)
		.addAttributeModifier(Attributes.MOVEMENT_SPEED, id("effect/chilled"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final Holder<MobEffect> FROZEN = register("frozen", new Stun(0x6FC8FF, "frozen"));
	public static final Holder<MobEffect> SOAKED = register("soaked", new Status(MobEffectCategory.HARMFUL, 0x2E6FD8));
	public static final Holder<MobEffect> SCORCHED = register("scorched", new Status(MobEffectCategory.HARMFUL, 0xD9541E));
	public static final Holder<MobEffect> BRITTLE = register("brittle", new Status(MobEffectCategory.HARMFUL, 0xC9C3B0));
	public static final Holder<MobEffect> MARKED = register("marked", new Status(MobEffectCategory.HARMFUL, 0xE0303A));
	public static final Holder<MobEffect> STAGGERED = register("staggered", new Stun(0xE8C547, "staggered"));
	public static final Holder<MobEffect> ARCANE_WARD = register("arcane_ward", new Ward());
	public static final Holder<MobEffect> BLEEDING = register("bleeding", new Bleeding());
	/** The archer's own eye: a mark that its skill hits are worth more, read by {@link ArcherSkills#hit}. */
	public static final Holder<MobEffect> BLITZ_BEAT = register("blitz_beat", new Status(MobEffectCategory.BENEFICIAL, 0xE0303A));
	public static final Holder<MobEffect> EAGLE_EYE = register("eagle_eye", new Status(MobEffectCategory.BENEFICIAL, 0x6FB34A));
	/** The reworked archer buffs: a steadied aim, a quiver that needs no arrow, and a faster step. */
	public static final Holder<MobEffect> CONCENTRATE = register("concentrate", new Status(MobEffectCategory.BENEFICIAL, 0xD8B44A));
	public static final Holder<MobEffect> SPECTRAL_QUIVER = register("spectral_quiver", new Status(MobEffectCategory.BENEFICIAL, 0xA8E4F0));
	public static final Holder<MobEffect> WIND_WALK = register("wind_walk", new WindWalk());
	public static final Holder<MobEffect> TRIANGLE_SHOT = register("triangle_shot", new Status(MobEffectCategory.BENEFICIAL, 0x9BE8A8));
	/** The movement modifier Wind Walk adds, and how much faster its holder walks. */
	public static final Identifier WIND_WALK_ID = id("effect/wind_walk");
	public static final double WIND_WALK_SPEED = 1.25;
	/** A priest's own three: the holy bonus, the death ward, and the link that shares a blow. */
	public static final Holder<MobEffect> HOLY_ARMS = register("holy_arms", new Status(MobEffectCategory.BENEFICIAL, 0xFFE08A));
	public static final Holder<MobEffect> RESURRECTION_WARD = register("resurrection_ward", new Status(MobEffectCategory.BENEFICIAL, 0xF2E2A8));
	public static final Holder<MobEffect> GUARDIAN_ANGEL = register("guardian_angel", new Status(MobEffectCategory.BENEFICIAL, 0xFFF4C2));
	/** The last two priest skills: the wear the vow carries, and the prayer that refuses harm. */
	public static final Holder<MobEffect> UNBROKEN_VOW = register("unbroken_vow", new Status(MobEffectCategory.BENEFICIAL, 0xC8D2DE));
	public static final Holder<MobEffect> STEADFAST_PRAYER = register("steadfast_prayer", new Status(MobEffectCategory.BENEFICIAL, 0xF0E6B8));

	/** Who made a mob bleed, so the damage can credit them. Weak, and gone with the effect or the mob. */
	private static final Map<LivingEntity, UUID> BLEED_SOURCES = Collections.synchronizedMap(new WeakHashMap<>());

	private SpellStatus() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> recoverStun(entity));
	}

	static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	/** A save made mid-stun: the tag and NoAI survive, the effect does not. Wake the mob. */
	static void recoverStun(final Entity entity) {
		if (entity instanceof Mob mob && mob.entityTags().contains(STUN_TAG) && !mob.hasEffect(FROZEN) && !mob.hasEffect(STAGGERED)) {
			wake(mob);
		}
	}

	static void rememberBleeder(final LivingEntity victim, final @Nullable Entity source) {
		if (source instanceof LivingEntity) {
			BLEED_SOURCES.put(victim, source.getUUID());
		} else {
			BLEED_SOURCES.remove(victim);
		}
	}

	/** The damage a hit really deals once the ward and the player-hit effects have their say. Called by the damage mixin. */
	public static float onHurt(final LivingEntity victim, final ServerLevel level, final DamageSource source, final float amount) {
		if (amount <= 0.0F || victim.isDeadOrDying() || victim.isInvulnerableTo(level, source)) {
			return amount;
		}
		// A shot that owes a book pays for it here, so the whole hit of that arrow is worth the book's number.
		float result = amount * ArcherSkills.projectileDamage(victim, source) * ArrowHits.damageFactor(source);
		result = victim.hasEffect(ARCANE_WARD) ? absorb(victim, result) : result;
		// A Guardian Angel shares the blow with its priest here, in the one path every hit takes, so the
		// shared part is taken before any other effect scales what is left.
		result = PriestSkills.guardianShare(victim, level, source, result);
		if (result <= 0.0F || source.is(BLEEDING_DAMAGE) || !(source.getEntity() instanceof Player)) {
			return result;
		}
		if (victim.hasEffect(FROZEN) && isMelee(source)) {
			result *= SHATTER_FACTOR;
			victim.removeEffect(FROZEN);
		}
		if (victim.hasEffect(BRITTLE)) {
			result *= SHATTER_FACTOR;
			victim.removeEffect(BRITTLE);
		}
		if (victim.hasEffect(MARKED)) {
			result *= MARKED_FACTOR;
		}
		return result;
	}

	/** The healing an entity really receives; Scorched halves it. Called by the damage mixin. */
	public static float onHeal(final LivingEntity entity, final float amount) {
		float result = entity.hasEffect(SCORCHED) ? amount * SCORCHED_HEAL_FACTOR : amount;
		return result;
	}

	static boolean isMelee(final DamageSource source) {
		return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.SPEAR) || source.is(DamageTypes.MACE_SMASH);
	}

	private static float absorb(final LivingEntity victim, final float amount) {
		Float pool = ((AttachmentTarget) victim).getAttached(WARD_POOL);
		if (pool == null || pool <= 0.0F) {
			victim.removeEffect(ARCANE_WARD);
			return amount;
		}
		if (pool > amount) {
			((AttachmentTarget) victim).setAttached(WARD_POOL, pool - amount);
			return 0.0F;
		}
		victim.removeEffect(ARCANE_WARD);
		return amount - pool;
	}

	private static void wake(final Mob mob) {
		mob.setNoAi(false);
		mob.removeTag(STUN_TAG);
	}

	private static Holder<MobEffect> register(final String path, final MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id(path), effect);
	}

	private static class Status extends MobEffect {
		Status(final MobEffectCategory category, final int color) {
			super(category, color);
		}
	}

	/** Frozen and Staggered: no movement, and a mob loses its AI too, so it cannot attack. */
	private static final class Stun extends Status {
		Stun(final int color, final String path) {
			super(MobEffectCategory.HARMFUL, color);
			this.addAttributeModifier(Attributes.MOVEMENT_SPEED, id("effect/" + path), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}

		@Override
		public void onEffectAdded(final MobEffectInstance instance, final LivingEntity entity) {
			if (entity instanceof Mob mob && !mob.level().isClientSide() && !mob.isNoAi()) {
				mob.setNoAi(true);
				mob.addTag(STUN_TAG);
			}
		}

		@Override
		public void onEffectRemoved(final MobEffectInstance instance, final LivingEntity entity) {
			if (!(entity instanceof Mob mob) || mob.level().isClientSide() || !mob.entityTags().contains(STUN_TAG)) {
				return;
			}
			Holder<MobEffect> other = instance.is(FROZEN) ? STAGGERED : FROZEN;
			if (!mob.hasEffect(other)) {
				wake(mob);
			}
		}
	}

	private static final class Ward extends Status {
		Ward() {
			super(MobEffectCategory.BENEFICIAL, 0x9C6BFF);
		}

		@Override
		public void onEffectRemoved(final MobEffectInstance instance, final LivingEntity entity) {
			((AttachmentTarget) entity).removeAttached(WARD_POOL);
		}
	}

	/** Wind Walk: a quarter more walking speed, granted and dropped with the effect itself. */
	private static final class WindWalk extends Status {
		WindWalk() {
			super(MobEffectCategory.BENEFICIAL, 0xC8F0B4);
		}

		@Override
		public void onEffectAdded(final MobEffectInstance instance, final LivingEntity entity) {
			AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed == null) {
				return;
			}
			speed.removeModifier(WIND_WALK_ID);
			speed.addTransientModifier(new AttributeModifier(
				WIND_WALK_ID, WIND_WALK_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
			));
		}

		@Override
		public void onEffectRemoved(final MobEffectInstance instance, final LivingEntity entity) {
			AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(WIND_WALK_ID);
			}
		}
	}

	private static final class Bleeding extends Status {
		Bleeding() {
			super(MobEffectCategory.HARMFUL, 0x9E1B1B);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
			return duration % BLEED_INTERVAL_TICKS == 1;
		}

		@Override
		public boolean applyEffectTick(final ServerLevel level, final LivingEntity entity, final int amplifier) {
			float percent = BLEED_PERCENT_PER_LEVEL * (amplifier + 1);
			entity.hurtServer(level, source(level, entity), Math.max(BLEED_MINIMUM, entity.getHealth() * percent));
			return true;
		}

		@Override
		public void onEffectRemoved(final MobEffectInstance instance, final LivingEntity entity) {
			BLEED_SOURCES.remove(entity);
		}

		// No direct entity: bleeding pushes nobody, yet the killer still gets the credit.
		private static DamageSource source(final ServerLevel level, final LivingEntity victim) {
			Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLEEDING_DAMAGE);
			UUID bleeder = BLEED_SOURCES.get(victim);
			Entity cause = bleeder == null ? null : level.getEntity(bleeder);
			return cause instanceof LivingEntity living && living.isAlive() ? new DamageSource(type, null, living) : new DamageSource(type);
		}
	}
}
