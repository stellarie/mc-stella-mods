package com.stella.rpg;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import java.util.function.Predicate;
import java.util.List;
import java.util.ArrayList;
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
 * Once per cooldown, a hit that would kill a player who wears or holds a piece with the equips mod's Second Wind
 * enchantment restores the player to full health, clears every harmful effect, and grants a short Resistance V.
 * Without that enchantment nothing is caught. A held totem of undying keeps its own job, and damage that bypasses
 * invulnerability, such as the void or a kill command, is never caught.
 */
public final class SecondWind {
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.create(
		StellaRpg.id("second_wind_ready"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath()
	);
	public static final long COOLDOWN_TICKS = 300L * 20L;
	public static final int RESIST_TICKS = 40;
	public static final int RESIST_AMPLIFIER = 4;
	/** The equips mod's enchantment, read by id so this mod loads without it. */
	public static final String ENCHANTMENT_ID = "mod-stella-equips-enchants:armor/second_wind";
	private static volatile Predicate<ServerPlayer> enchanted = SecondWind::wearsEnchantment;

	private SecondWind() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register(SecondWind::allowDeath);
	}

	private static boolean allowDeath(final LivingEntity entity, final DamageSource source, final float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || holdsTotem(player)
			|| !enchanted.test(player)) {
			return true;
		}
		long now = player.level().getGameTime();
		Long ready = player.getAttached(READY_AT);
		if (ready != null && now < ready) {
			return true;
		}
		player.setAttached(READY_AT, now + COOLDOWN_TICKS);
		player.setHealth(player.getMaxHealth());
		List<Holder<MobEffect>> harmful = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				harmful.add(effect.getEffect());
			}
		}
		harmful.forEach(player::removeEffect);
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RESIST_TICKS, RESIST_AMPLIFIER));
		player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.6F);
		player.sendSystemMessage(Component.translatable("message.mod-stella-rpg.second_wind").withStyle(ChatFormatting.GOLD), true);
		return false;
	}

	/** True when any worn or held piece carries the Second Wind enchantment. */
	private static boolean wearsEnchantment(final ServerPlayer player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			for (Holder<Enchantment> enchantment : EnchantmentHelper.getEnchantmentsForCrafting(player.getItemBySlot(slot)).keySet()) {
				if (enchantment.unwrapKey().map(key -> key.identifier().toString().equals(ENCHANTMENT_ID)).orElse(false)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Test seam: who counts as enchanted. A null check returns to the worn-gear rule. */
	static void setEnchantCheck(final Predicate<ServerPlayer> check) {
		enchanted = check == null ? SecondWind::wearsEnchantment : check;
	}

	private static boolean holdsTotem(final ServerPlayer player) {
		return player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}
}
