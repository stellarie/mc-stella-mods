package com.stella.tower;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Ectoplasm Charm cheats death once, carried anywhere in the inventory: a blow that would kill its bearer leaves them on
 * half their health instead, and the charm cannot save them again for {@value #COOLDOWN_TICKS} ticks. Only a blow that
 * bypasses invulnerability, the void or a command, still kills.
 */
public final class EctoplasmCharm {
	/** Ten minutes. */
	public static final int COOLDOWN_TICKS = 10 * 60 * 20;
	/** The share of its health a saved bearer keeps. */
	public static final float HEALTH_SHARE = 0.5F;
	public static final int GRACE_TICKS = 5 * 20;

	private EctoplasmCharm() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			ItemStack charm = held(player);
			if (charm == null || player.getCooldowns().isOnCooldown(charm)) {
				return true;
			}
			player.getCooldowns().addCooldown(charm, COOLDOWN_TICKS);
			player.setHealth(Math.max(1.0F, player.getMaxHealth() * HEALTH_SHARE));
			player.removeAllEffects();
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, GRACE_TICKS, 2));
			if (player.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
				level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			StellaTower.LOGGER.debug("[TOWER] player={} was saved by an ectoplasm charm", player.getScoreboardName());
			return false;
		});
	}

	/** A charm the player carries, or null. */
	public static @Nullable ItemStack held(final ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(MerchantItems.ECTOPLASM_CHARM)) {
				return stack;
			}
		}
		return null;
	}
}
