package com.stella.rpg;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class RpgAttributes {
	public static final Identifier ATTACK_SPEED_ID = StellaRpg.id("agility_attack_speed");
	private static final int SCAN_INTERVAL = 20;

	private RpgAttributes() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SCAN_INTERVAL == 0) {
				server.getPlayerList().getPlayers().forEach(RpgAttributes::refresh);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
	}

	public static void refresh(final ServerPlayer player) {
		AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
		if (instance == null) {
			return;
		}
		double bonus = RpgCurve.attackSpeedBonus(StatBonuses.effective(player, Stat.AGI));
		if (bonus <= 0.0) {
			instance.removeModifier(ATTACK_SPEED_ID);
			return;
		}
		AttributeModifier existing = instance.getModifier(ATTACK_SPEED_ID);
		if (existing == null || existing.amount() != bonus) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(
				ATTACK_SPEED_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
			));
		}
	}
}
