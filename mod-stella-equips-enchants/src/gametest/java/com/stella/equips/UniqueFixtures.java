package com.stella.equips;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueEffect;
import com.stella.equips.unique.UniqueJob;
import com.stella.equips.unique.UniqueKind;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;

/**
 * Test-only uniques whose effect records every hook that fires for a holder. Their ids start with {@code test_}, so
 * the registry checks skip them, and their source is no real entity, so nothing drops them.
 */
public final class UniqueFixtures {
	public static final String PREFIX = "test_";
	private static final Identifier NO_SOURCE = Identifier.parse("mod-stella-equips-enchants-test:nobody");

	/** One hook call. The flag is the crit, killed, or power-less marker of the hook. */
	public record Call(String hook, UUID holder, String unique, int refine, float amount, boolean flag, UUID other) {
	}

	private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
	/** The outgoing and incoming multiplier per holder. Absent means 1. */
	private static final Map<UUID, Float> MULTIPLY = new ConcurrentHashMap<>();
	/** The ticks each harmful effect on a holder loses. */
	private static final Map<UUID, Integer> SHORTEN = new ConcurrentHashMap<>();

	public static Item sword;
	public static Item bow;
	public static Item crossbow;
	public static Item helmet;
	public static Item boots;

	private static boolean ready;

	private UniqueFixtures() {
	}

	/** Registers the fixtures. Called from the test mod initializer, before the item registry freezes. */
	public static synchronized void init() {
		if (ready) {
			return;
		}
		ready = true;
		sword = register("test_recorder_sword", UniqueKind.SWORD);
		bow = register("test_recorder_bow", UniqueKind.BOW);
		crossbow = register("test_recorder_crossbow", UniqueKind.CROSSBOW);
		helmet = register("test_recorder_helmet", UniqueKind.HELMET);
		boots = register("test_recorder_boots", UniqueKind.BOOTS);
	}

	private static Item register(final String id, final UniqueKind kind) {
		return Uniques.register(new UniqueDef(id, id, UniqueJob.ARCHER, kind, List.of(NO_SOURCE),
			"base", "seven", "ten", "twelve", "fifteen", null, new Recording(id)));
	}

	public static void multiply(final UUID holder, final float factor) {
		MULTIPLY.put(holder, factor);
	}

	public static void shorten(final UUID holder, final int ticks) {
		SHORTEN.put(holder, ticks);
	}

	public static List<Call> calls(final UUID holder, final String hook) {
		return CALLS.stream().filter(call -> call.holder().equals(holder) && call.hook().equals(hook)).toList();
	}

	public static List<Call> calls(final UUID holder) {
		return CALLS.stream().filter(call -> call.holder().equals(holder)).toList();
	}

	private static float factor(final UniqueContext c) {
		return MULTIPLY.getOrDefault(c.holder().getUUID(), 1.0F);
	}

	private static void record(final String hook, final UniqueContext c, final String unique, final float amount, final boolean flag, final UUID other) {
		CALLS.add(new Call(hook, c.holder().getUUID(), unique, c.refine(), amount, flag, other));
	}

	private static final class Recording implements UniqueEffect {
		private final String id;

		private Recording(final String id) {
			this.id = id;
		}

		@Override
		public float outgoingMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			record("outgoingMelee", c, this.id, amount, false, target.getUUID());
			return amount * factor(c);
		}

		@Override
		public void afterMelee(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean crit, final boolean killed) {
			record("afterMelee", c, this.id, dealt, crit, target.getUUID());
			if (killed) {
				record("afterMeleeKilled", c, this.id, dealt, true, target.getUUID());
			}
		}

		@Override
		public void onShot(final UniqueContext c, final AbstractArrow arrow, final float power) {
			record("onShot", c, this.id, power, false, arrow.getUUID());
		}

		@Override
		public float outgoingArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float amount) {
			record("outgoingArrow", c, this.id, amount, false, target.getUUID());
			return amount * factor(c);
		}

		@Override
		public void afterArrow(final UniqueContext c, final LivingEntity target, final AbstractArrow arrow, final float dealt, final boolean killed) {
			record("afterArrow", c, this.id, dealt, killed, target.getUUID());
		}

		@Override
		public float outgoingSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float amount) {
			record("outgoingSpell", c, this.id, amount, false, target.getUUID());
			return amount * factor(c);
		}

		@Override
		public void afterSpell(final UniqueContext c, final LivingEntity target, final DamageSource s, final float dealt, final boolean killed) {
			record("afterSpell", c, this.id, dealt, killed, target.getUUID());
		}

		@Override
		public float incoming(final UniqueContext c, final DamageSource s, final float amount) {
			record("incoming", c, this.id, amount, false, c.holder().getUUID());
			return amount * factor(c);
		}

		@Override
		public void afterIncoming(final UniqueContext c, final DamageSource s, final float taken) {
			record("afterIncoming", c, this.id, taken, false, c.holder().getUUID());
		}

		@Override
		public void onKill(final UniqueContext c, final LivingEntity victim, final DamageSource s) {
			record("onKill", c, this.id, 0.0F, false, victim.getUUID());
		}

		@Override
		public void everySecond(final UniqueContext c) {
			record("everySecond", c, this.id, c.holder().level().getServer().getTickCount(), false, c.holder().getUUID());
		}

		@Override
		public int harmfulDuration(final UniqueContext c, final MobEffectInstance effect, final int ticks) {
			record("harmfulDuration", c, this.id, ticks, false, c.holder().getUUID());
			return Math.max(1, ticks - SHORTEN.getOrDefault(c.holder().getUUID(), 0));
		}
	}
}
