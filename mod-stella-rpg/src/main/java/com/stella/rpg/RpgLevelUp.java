package com.stella.rpg;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class RpgLevelUp {
	public static final AttachmentType<Integer> LAST_LEVEL = AttachmentRegistry.create(
		StellaRpg.id("last_level"),
		builder -> builder.persistent(Codec.INT).copyOnDeath()
	);
	public static final int SPEED_TICKS = 600;
	public static final int SPEED_AMPLIFIER = 1;

	private RpgLevelUp() {
	}

	public static void check(final ServerPlayer player) {
		Integer last = player.getAttached(LAST_LEVEL);
		int level = player.experienceLevel;
		if (last != null && last == level) {
			return;
		}
		player.setAttached(LAST_LEVEL, level);
		if (last != null && level > last) {
			player.setHealth(player.getMaxHealth());
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, SPEED_AMPLIFIER));
		}
	}
}
