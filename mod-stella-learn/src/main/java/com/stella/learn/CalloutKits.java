package com.stella.learn;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The callout types beyond the rally: focus fire, back off, heal, buff and debuff. Healing, buffing and debuffing
 * are things only a caster can do, so only a witch or a spellcasting illager accepts them.
 */
public final class CalloutKits {
	public static final Identifier FOCUS = StellaLearn.id("focus");
	public static final Identifier BACK_OFF = StellaLearn.id("back_off");
	public static final Identifier HEAL = StellaLearn.id("heal");
	public static final Identifier BUFF = StellaLearn.id("buff");
	public static final Identifier DEBUFF = StellaLearn.id("debuff");
	/** Hit points a healer gives the caller. */
	public static final float HEAL_AMOUNT = 6.0F;
	/** Blocks within which a debuff caster can reach the player. */
	public static final double DEBUFF_REACH = 16.0;
	public static final int BUFF_TICKS = 200;
	public static final int DEBUFF_TICKS = 100;
	/** Blocks from the player a mob told to back off tries to stand. */
	public static final double BACK_OFF_DISTANCE = 10.0;

	private CalloutKits() {
	}

	public static void register() {
		CalloutTypes.register(FOCUS, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return true;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				mob.setTarget(player);
			}
		});
		CalloutTypes.register(BACK_OFF, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return true;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				Vec3 away = new Vec3(mob.getX() - player.getX(), 0.0, mob.getZ() - player.getZ());
				away = away.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				((AttachmentTarget) mob).setAttached(Rally.SLOT, player.position().add(away.scale(BACK_OFF_DISTANCE)));
			}
		});
		CalloutTypes.register(HEAL, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return mob instanceof Witch && callerOf(mob, callout) != null;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				LivingEntity caller = callerOf(mob, callout);
				if (caller != null) {
					caller.heal(HEAL_AMOUNT);
				}
			}
		});
		CalloutTypes.register(BUFF, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return isCaster(mob) && effectOf(callout) != null && callerOf(mob, callout) != null;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				LivingEntity caller = callerOf(mob, callout);
				Holder<MobEffect> effect = effectOf(callout);
				if (caller != null && effect != null) {
					caller.addEffect(new MobEffectInstance(effect, BUFF_TICKS, 0));
				}
			}
		});
		CalloutTypes.register(DEBUFF, new CalloutHandler() {
			@Override
			public boolean canPerform(final Mob mob, final ServerPlayer player, final Callout callout) {
				return isCaster(mob) && effectOf(callout) != null && mob.distanceTo(player) <= DEBUFF_REACH;
			}

			@Override
			public void perform(final Mob mob, final ServerPlayer player, final Callout callout, final int index) {
				Holder<MobEffect> effect = effectOf(callout);
				if (effect != null) {
					player.addEffect(new MobEffectInstance(effect, DEBUFF_TICKS, 0));
				}
			}
		});
	}

	private static boolean isCaster(final Mob mob) {
		return mob instanceof Witch || mob instanceof SpellcasterIllager;
	}

	private static @Nullable LivingEntity callerOf(final Mob mob, final Callout callout) {
		return mob.level() instanceof ServerLevel level && level.getEntity(callout.caller()) instanceof LivingEntity caller && caller.isAlive()
			? caller : null;
	}

	private static @Nullable Holder<MobEffect> effectOf(final Callout callout) {
		Identifier id = callout.detail();
		return id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id).map(holder -> (Holder<MobEffect>) holder).orElse(null);
	}
}
