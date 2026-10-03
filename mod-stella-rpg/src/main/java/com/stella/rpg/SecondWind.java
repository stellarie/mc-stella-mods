package com.stella.rpg;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * Once per cooldown, a hit that would kill a player leaves the player at 1 health with a short Resistance V. A held
 * totem of undying keeps its own job, and damage that bypasses invulnerability, such as the void or a kill command,
 * is never caught.
 */
public final class SecondWind {
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.create(
		StellaRpg.id("second_wind_ready"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath()
	);
	public static final long COOLDOWN_TICKS = 300L * 20L;
	public static final int RESIST_TICKS = 40;
	public static final int RESIST_AMPLIFIER = 4;

	private SecondWind() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(SecondWind::allowDeath);
	}

	private static boolean allowDeath(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || holdsTotem(player)) {
			return true;
		}
		long now = player.level().getGameTime();
		Long ready = player.getAttached(READY_AT);
		if (ready != null && now < ready) {
			return true;
		}
		player.setAttached(READY_AT, now + COOLDOWN_TICKS);
		player.setHealth(1.0F);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RESIST_TICKS, RESIST_AMPLIFIER));
		player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
		player.sendSystemMessage(Component.translatable("message.mod-stella-rpg.second_wind").withStyle(ChatFormatting.GOLD), true);
		return false;
	}

	private static boolean holdsTotem(final ServerPlayer player) {
		return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}
}
