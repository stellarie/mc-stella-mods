package com.stella.spells;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The +15 signature of each of the 81 books: one effect that belongs to that book alone. A signature runs
 * once, right after the cast, from the finished cast's own aim point, target and struck foes, and it reads the
 * same stats as the book. The name and the text of each sit in the language file under
 * {@code spellbook.mod-stella-spells.sig.<book>} and {@code ....sig.<book>.text}.
 */
public final class BookSignatures {
	/** What a signature does. */
	@FunctionalInterface
	interface Action {
		void run(RefineCast cast);
	}

	/** A book's signature. */
	record Signature(Action action) {
	}

	private static final Map<String, Signature> BY_BOOK = new LinkedHashMap<>();
	/** The wards of a signature, in vanilla health units before the health scale. */
	private static final float SMALL_WARD = 4.0F;
	private static final float BIG_WARD = 6.0F;

	static {
		mage();
		fighter();
		archer();
		priest();
	}

	private BookSignatures() {
	}

	static @Nullable Signature of(final String path) {
		return BY_BOOK.get(path);
	}

	/** Every book with a signature, in catalog order. */
	static java.util.Set<String> books() {
		return java.util.Collections.unmodifiableSet(BY_BOOK.keySet());
	}

	private static void sig(final String path, final Action action) {
		BY_BOOK.put(path, new Signature(action));
	}

	// ---- the mage books ----

	private static void mage() {
		sig("spark_shot", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			List<Mob> near = c.foesBeside(target, 5.0);
			for (int i = 0; i < Math.min(2, near.size()); i++) {
				c.hit(near.get(i), c.flat(3.0F) * 0.5F);
				near.get(i).igniteForTicks(ticks(4));
			}
		});
		sig("frostbind", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			SpellStatusApi.freeze(target, ticks(2), c.caster());
			for (Mob foe : c.foesBeside(target, 4.0)) {
				SpellStatusApi.chill(foe, ticks(6), SpellStatusApi.CHILL_FREEZE_TICKS, c.caster());
			}
		});
		sig("gale_step", c -> {
			give(c.caster(), MobEffects.SPEED, 5, 1);
			for (Mob foe : MoreSpells.hostiles(c.caster().level(), c.departure(), 4.0)) {
				throwFrom(foe, c.departure(), 1.0, 0.3);
			}
		});
		sig("mending_word", c -> BookRefine.later(c.caster(), ticks(2),
			() -> c.heal(c.caster(), c.healAmount(SpellNumbers.MENDING_WORD.heal()) * 0.5F)));
		sig("stoneguard", c -> {
			give(c.caster(), MobEffects.FIRE_RESISTANCE, 30, 0);
			SpellStatusApi.ward(c.caster(), SMALL_WARD, ticks(20));
		});
		sig("static_lash", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			SpellStatusApi.stagger(target, ticks(2), c.caster());
			for (Mob foe : c.foesBeside(target, 4.0)) {
				SpellStatusApi.stagger(foe, ticks(1), c.caster());
			}
		});
		sig("briar_snare", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 3.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 3, SpellNumbers.SLOWNESS_ROOT_AMPLIFIER);
			}
			SpellStatusApi.bleed(target, ticks(5), 0, c.caster());
		});
		sig("tidal_push", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 4.0)) {
				SpellStatusApi.soak(foe, ticks(10), c.caster());
				throwFrom(foe, c.caster().position(), 0.8, 0.2);
			}
		});
		sig("owls_gaze", c -> {
			for (Mob foe : c.foesAroundCaster(20.0)) {
				inflict(c, foe, MobEffects.GLOWING, 10, 0);
			}
		});
		sig("slimebound", c -> {
			for (Mob foe : c.foesAroundCaster(4.0)) {
				launch(foe, 0.8);
			}
			give(c.caster(), MobEffects.RESISTANCE, 10, 0);
		});
		sig("chainstorm", c -> {
			for (Mob foe : c.foes(5.0)) {
				SpellStatusApi.soak(foe, ticks(10), c.caster());
				c.hit(foe, c.flat(6.0F) * 0.5F);
			}
		});
		sig("riftwalk", c -> {
			give(c.caster(), MobEffects.SPEED, 3, 1);
			for (Mob foe : MoreSpells.hostiles(c.caster().level(), c.departure(), 4.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 3, 1);
			}
		});
		sig("phoenix_mantle", c -> {
			for (Mob foe : c.foesAroundCaster(4.0)) {
				foe.igniteForTicks(ticks(5));
			}
			SpellStatusApi.ward(c.caster(), BIG_WARD, ticks(20));
		});
		sig("life_bloom", c -> {
			for (LivingEntity ally : c.allies(6.0)) {
				give(ally, MobEffects.REGENERATION, 10, 0);
			}
		});
		sig("void_well", c -> {
			for (Mob foe : c.foes(5.0)) {
				inflict(c, foe, MobEffects.WEAKNESS, 8, 1);
				inflict(c, foe, MobEffects.LEVITATION, 1, 0);
			}
		});
		sig("wardens_roar", c -> {
			for (Mob foe : c.foesAroundCaster(5.0)) {
				inflict(c, foe, MobEffects.DARKNESS, 6, 0);
				inflict(c, foe, MobEffects.WEAKNESS, 6, 0);
			}
		});
		sig("aegis_of_the_deep", c -> {
			for (LivingEntity ally : c.allies(6.0)) {
				give(ally, MobEffects.ABSORPTION, 20, 0);
			}
		});
		sig("wither_seal", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 4.0)) {
				inflict(c, foe, MobEffects.WITHER, 6, 0);
			}
		});
		sig("time_lock", c -> {
			for (Mob foe : c.foes(5.0)) {
				SpellStatusApi.freeze(foe, ticks(1), c.caster());
			}
		});
		sig("dragonfall", c -> {
			for (Mob foe : c.foes(5.0)) {
				launch(foe, 1.0);
				foe.igniteForTicks(ticks(6));
			}
		});
		sig("ice_lance", c -> {
			for (Mob foe : struck(c)) {
				SpellStatusApi.brittle(foe, ticks(6), c.caster());
			}
		});
		sig("soul_siphon", c -> {
			SpellStatusApi.ward(c.caster(), SMALL_WARD, ticks(10));
			give(c.caster(), MobEffects.REGENERATION, 5, 0);
		});
		sig("venom_dart", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 4.0)) {
				inflict(c, foe, MobEffects.POISON, 6, 1);
			}
		});
		sig("sapping_hex", c -> {
			int hexed = 0;
			for (Mob foe : c.foesAroundCaster(MoreSpells.HEX_RADIUS)) {
				hexed += foe.hasEffect(MobEffects.WEAKNESS) ? 1 : 0;
			}
			if (hexed > 0) {
				c.heal(c.caster(), (float) hexed);
			}
			give(c.caster(), MobEffects.REGENERATION, 5, 0);
		});
		sig("blinding_flash", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 5.0)) {
				inflict(c, foe, MobEffects.BLINDNESS, 4, 0);
			}
		});
		sig("ember_brand", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 3.0)) {
				c.hit(foe, c.flat(2.0F) * 0.5F);
				SpellStatusApi.scorch(foe, ticks(5), c.caster());
			}
		});
		sig("quicksilver", c -> give(c.caster(), MobEffects.SPEED, 5, 2));
		sig("tide_lungs", c -> give(c.caster(), MobEffects.CONDUIT_POWER, 60, 0));
		sig("berserk", c -> give(c.caster(), MobEffects.STRENGTH, 8, 2));
		sig("haste_rune", c -> {
			give(c.caster(), MobEffects.HASTE, 10, 3);
			give(c.caster(), MobEffects.SPEED, 10, 0);
		});
		sig("shadow_veil", c -> {
			for (Mob foe : c.foesAroundCaster(5.0)) {
				inflict(c, foe, MobEffects.BLINDNESS, 3, 0);
			}
		});
		sig("sanctuary", c -> {
			for (Mob foe : c.foesAroundCaster(MoreSpells.SANCTUARY_RADIUS)) {
				if (foe.is(EntityTypeTags.UNDEAD)) {
					SpellStatusApi.stagger(foe, ticks(2), c.caster());
					foe.igniteForTicks(ticks(4));
				}
			}
		});
		sig("purify", c -> {
			for (LivingEntity who : c.party(6.0)) {
				give(who, MobEffects.REGENERATION, 5, 1);
			}
		});
		sig("frost_nova", c -> {
			for (Mob foe : c.foesAroundCaster(5.0)) {
				SpellStatusApi.freeze(foe, ticks(1.5), c.caster());
			}
		});
		sig("earthspike", c -> {
			for (Mob foe : c.foes(3.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 3, SpellNumbers.SLOWNESS_ROOT_AMPLIFIER);
			}
		});
		sig("plague_cloud", c -> {
			for (Mob foe : c.foes(3.0)) {
				inflict(c, foe, MobEffects.WEAKNESS, 8, 1);
			}
		});
		sig("flame_ring", c -> {
			for (Mob foe : c.foesAroundCaster(MoreSpells.FLAME_RING_RADIUS)) {
				c.hit(foe, c.flat(3.0F));
				throwFrom(foe, c.caster().position(), 0.8, 0.3);
			}
		});
		sig("gravity", c -> {
			for (Mob foe : c.foesAroundCaster(MoreSpells.GRAVITY_RADIUS)) {
				c.hit(foe, 2.0F);
				inflict(c, foe, MobEffects.SLOWNESS, 4, 3);
			}
		});
		sig("bulwark_hymn", c -> {
			for (LivingEntity who : c.party(8.0)) {
				give(who, MobEffects.RESISTANCE, 10, 0);
			}
		});
		sig("tailwind", c -> {
			for (LivingEntity who : c.party(8.0)) {
				give(who, MobEffects.JUMP_BOOST, 20, 1);
				give(who, MobEffects.SLOW_FALLING, 20, 0);
			}
		});
		sig("battle_hymn", c -> {
			for (LivingEntity who : c.party(8.0)) {
				give(who, MobEffects.REGENERATION, 8, 0);
				give(who, MobEffects.HASTE, 20, 0);
			}
		});
		sig("summon_skeleton", c -> {
			Mob servant = SummonSkeleton.find(c.caster().level().getServer(), c.caster().getUUID());
			if (servant != null) {
				give(servant, MobEffects.RESISTANCE, 20, 1);
				give(servant, MobEffects.STRENGTH, 20, 0);
			}
		});
	}

	// ---- the fighter books ----

	private static void fighter() {
		sig("cleave", c -> {
			for (Mob foe : struck(c)) {
				SpellStatusApi.mark(foe, ticks(6), c.caster());
			}
		});
		sig("leap_slam", c -> {
			for (Mob foe : c.foesAroundCaster(5.0)) {
				SpellStatusApi.stagger(foe, ticks(1.5), c.caster());
			}
		});
		sig("whirlwind", c -> {
			for (Mob foe : c.foesAroundCaster(6.0)) {
				pull(foe, c.caster().position(), 2.0);
				inflict(c, foe, MobEffects.SLOWNESS, 3, 1);
			}
		});
		sig("shield_bash", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			inflict(c, target, MobEffects.WEAKNESS, 6, 1);
			SpellStatusApi.bleed(target, ticks(5), 0, c.caster());
		});
		sig("charge", c -> {
			give(c.caster(), MobEffects.SPEED, 4, 1);
			for (Mob foe : c.foesAroundCaster(4.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 3, 1);
			}
		});
		sig("execute", c -> {
			Mob target = c.target();
			if (target != null && target.isAlive() && target.getHealth() <= target.getMaxHealth() * FighterSkills.EXECUTE_THRESHOLD) {
				c.hit(target, SpellNumbers.EXECUTE.damage() * FighterSkills.EXECUTE_LOW_FACTOR);
			}
		});
		sig("war_cry", c -> {
			for (LivingEntity who : c.party(8.0)) {
				give(who, MobEffects.STRENGTH, 10, 0);
				give(who, MobEffects.ABSORPTION, 10, 0);
			}
		});
		sig("counter_stance", c -> {
			give(c.caster(), MobEffects.STRENGTH, 5, 1);
			give(c.caster(), MobEffects.SPEED, 5, 0);
		});
		sig("iron_skin", c -> give(c.caster(), MobEffects.ABSORPTION, 20, 2));
		sig("earthsplitter", c -> {
			for (Mob foe : struck(c)) {
				launch(foe, 1.0);
				inflict(c, foe, MobEffects.SLOWNESS, 3, 1);
			}
		});
	}

	// ---- the archer books ----

	private static void archer() {
		sig("arrow_crafting", c -> {
			ServerPlayer caster = c.caster();
			ItemStack arrows = new ItemStack(Items.ARROW, 8);
			caster.getInventory().add(arrows);
			if (!arrows.isEmpty()) {
				caster.spawnAtLocation((ServerLevel) caster.level(), arrows);
			}
		});
		sig("multishot", c -> {
			for (Mob foe : c.foesAroundCaster(4.0)) {
				SpellStatusApi.stagger(foe, ticks(1.5), c.caster());
			}
		});
		sig("piercing_shot", c -> {
			for (Mob foe : struck(c)) {
				SpellStatusApi.mark(foe, ticks(6), c.caster());
			}
		});
		sig("arrow_rain", c -> {
			for (Mob foe : c.foes(6.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 4, 1);
			}
		});
		sig("rapid_fire", c -> {
			Mob target = c.target();
			if (target != null) {
				c.hit(target, SpellNumbers.RAPID_FIRE.damage());
			}
		});
		sig("hunters_mark", c -> {
			give(c.caster(), MobEffects.SPEED, 5, 1);
			c.foesAroundCaster(12.0).stream()
				.min(java.util.Comparator.comparingDouble((Mob foe) -> foe.distanceToSqr(c.caster())).thenComparingInt(Mob::getId))
				.ifPresent(foe -> SpellStatusApi.mark(foe, ticks(10), c.caster()));
		});
		sig("ankle_snare", c -> {
			for (Mob foe : c.foes(4.0)) {
				inflict(c, foe, MobEffects.SLOWNESS, 2, SpellNumbers.SLOWNESS_ROOT_AMPLIFIER);
				SpellStatusApi.bleed(foe, ticks(5), 0, c.caster());
			}
		});
		sig("arrow_repel", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			for (Mob foe : c.foesBeside(target, 5.0)) {
				throwFrom(foe, c.caster().position(), 1.2, 0.3);
				inflict(c, foe, MobEffects.SLOWNESS, 3, 1);
			}
		});
		sig("explosive_arrow", c -> ArcherSkills.upgradeShot(c.caster()));
		sig("disengage", c -> {
			for (Mob foe : c.foesAroundCaster(5.0)) {
				inflict(c, foe, MobEffects.BLINDNESS, 2, 0);
			}
			give(c.caster(), MobEffects.INVISIBILITY, 3, 0);
		});
		sig("snare_trap", c -> SpellStatusApi.ward(c.caster(), SMALL_WARD, ticks(10)));
		sig("aimed_bolt", c -> give(c.caster(), SpellStatus.CONCENTRATE, 20, 0));
		sig("triangle_shot", c -> raise(c.caster(), SpellStatus.TRIANGLE_SHOT, ArcherSkills.TRIANGLE_SHOT_OFFER_TICKS));
		sig("eagle_eye", c -> {
			for (Mob foe : c.foesAroundCaster(24.0)) {
				inflict(c, foe, MobEffects.GLOWING, 6, 0);
			}
		});
		sig("elemental_quiver", c -> raise(c.caster(), SpellStatus.SPECTRAL_QUIVER, ArcherSkills.SPECTRAL_QUIVER_TICKS));
		sig("vultures_eye", c -> raise(c.caster(), SpellStatus.VULTURES_EYE, ArcherSkills.VULTURES_EYE_TICKS));
		sig("summon_wolf", c -> {
			LivingEntity wolf = SummonWolf.find(c.caster().level().getServer(), c.caster().getUUID());
			if (wolf != null) {
				give(wolf, MobEffects.STRENGTH, 20, 0);
				give(wolf, MobEffects.SPEED, 20, 0);
			}
		});
	}

	// ---- the priest books ----

	private static void priest() {
		sig("holy_light", c -> {
			LivingEntity healed = c.ally() != null ? c.ally() : c.caster();
			float share = c.healAmount(PriestSkills.HOLY_LIGHT_HEAL) / 3.0F;
			for (LivingEntity near : c.alliesBeside(healed, 5.0)) {
				c.heal(near, share);
			}
		});
		sig("prayer_of_mending", c -> c.heal(c.caster(), c.healAmount(PriestSkills.PRAYER_HEAL) * 0.5F));
		sig("renew", c -> give(c.ally() != null ? c.ally() : c.caster(), MobEffects.REGENERATION, 6, 1));
		sig("divine_shield", c -> {
			for (LivingEntity who : c.party(5.0)) {
				SpellStatusApi.ward(who, SMALL_WARD, ticks(15));
			}
		});
		sig("holy_arms", c -> {
			for (Mob foe : c.foesAroundCaster(PriestSkills.HOLY_ARMS_RADIUS)) {
				SpellStatusApi.mark(foe, ticks(6), c.caster());
			}
		});
		sig("blessing_of_fortitude", c -> {
			for (LivingEntity who : c.party(8.0)) {
				give(who, MobEffects.REGENERATION, 10, 0);
			}
		});
		sig("smite", c -> {
			Mob target = c.target();
			if (target == null) {
				return;
			}
			c.hit(target, PriestSkills.SMITE_DAMAGE * 0.5F);
			target.igniteForTicks(ticks(4));
		});
		sig("resurrection_ward", c -> give(c.ally() != null ? c.ally() : c.caster(), MobEffects.ABSORPTION, 20, 2));
		sig("consecrate", c -> {
			for (LivingEntity who : c.partyAt(c.focus(), PriestSkills.CONSECRATE_RADIUS)) {
				give(who, MobEffects.RESISTANCE, 10, 0);
			}
		});
		sig("guardian_angel", c -> {
			give(c.caster(), MobEffects.RESISTANCE, 10, 0);
			if (c.ally() != null) {
				give(c.ally(), MobEffects.RESISTANCE, 10, 0);
			}
		});
		sig("unbroken_vow", c -> give(c.caster(), MobEffects.HEALTH_BOOST, 30, 1));
		sig("steadfast_prayer", c -> {
			for (MobEffectInstance effect : List.copyOf(c.caster().getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					c.caster().removeEffect(effect.getEffect());
				}
			}
			give(c.caster(), MobEffects.REGENERATION, 8, 0);
		});
	}

	// ---- helpers ----

	private static int ticks(final double seconds) {
		return (int) Math.round(seconds * 20.0);
	}

	/** An effect on a friendly body; no source, so it never counts as a hit of the cast. */
	private static void give(final LivingEntity who, final Holder<MobEffect> effect, final double seconds, final int amplifier) {
		who.addEffect(new MobEffectInstance(effect, ticks(seconds), amplifier));
	}

	/** An effect on a foe, credited to the caster. */
	private static void inflict(final RefineCast c, final Mob foe, final Holder<MobEffect> effect, final double seconds, final int amplifier) {
		foe.addEffect(new MobEffectInstance(effect, ticks(seconds), amplifier), c.caster());
	}

	/** The effect at a higher amplifier for what is left of it, or for the fallback when the caster has none. */
	private static void raise(final ServerPlayer caster, final Holder<MobEffect> effect, final int fallbackTicks) {
		MobEffectInstance current = caster.getEffect(effect);
		caster.addEffect(new MobEffectInstance(effect, current == null ? fallbackTicks : current.getDuration(), 1));
	}

	/** The foes the cast struck, else its target. */
	private static List<Mob> struck(final RefineCast c) {
		if (!c.victims().isEmpty()) {
			return c.victims();
		}
		return c.target() == null ? List.of() : List.of(c.target());
	}

	private static void launch(final Mob foe, final double up) {
		Vec3 motion = foe.getDeltaMovement();
		foe.setDeltaMovement(motion.x, up, motion.z);
		foe.needsSync = true;
	}

	private static void throwFrom(final Mob foe, final Vec3 from, final double strength, final double lift) {
		Vec3 away = foe.position().subtract(from);
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		Vec3 push = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize().scale(strength);
		foe.setDeltaMovement(push.x, lift, push.z);
		foe.needsSync = true;
	}

	private static void pull(final Mob foe, final Vec3 to, final double blocks) {
		Vec3 toward = to.subtract(foe.position());
		Vec3 flat = new Vec3(toward.x, 0.0, toward.z);
		if (flat.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 next = foe.position().add(flat.normalize().scale(Math.min(blocks, flat.length())));
		foe.teleportTo(next.x, next.y, next.z);
	}
}
