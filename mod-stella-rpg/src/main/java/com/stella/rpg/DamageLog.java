package com.stella.rpg;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * An opt-in chat log of damage dealt and taken, toggled with {@code /rpgstats dmgcounter on|off}. The amount is
 * the health and absorption actually lost, in K units. Damage-over-time ticks are left out, so poison does not
 * flood the chat.
 */
public final class DamageLog {
	public static final AttachmentType<Boolean> ENABLED = AttachmentRegistry.create(
		StellaRpg.id("damage_log"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);
	private static final Map<LivingEntity, Float> BEFORE = new WeakHashMap<>();

	private DamageLog() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			synchronized (BEFORE) {
				BEFORE.put(entity, vitality(entity));
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> report(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(DamageLog::report);
	}

	public static boolean enabled(final ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(ENABLED));
	}

	public static void set(final ServerPlayer player, final boolean on) {
		player.setAttached(ENABLED, on);
	}

	public static String dealt(final int amount, final String victim) {
		return "You dealt " + amount + " damage to " + victim + ".";
	}

	public static String taken(final int amount, final String attacker) {
		return attacker == null ? "You took " + amount + " damage." : attacker + " dealt " + amount + " damage to you.";
	}

	private static void report(final LivingEntity victim, final DamageSource source) {
		Float before;
		synchronized (BEFORE) {
			before = BEFORE.remove(victim);
		}
		if (before == null || source.is(RpgDamageTypes.NO_HURT_SHAKE)) {
			return;
		}
		int lost = Math.round(before - vitality(victim));
		if (lost <= 0) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && enabled(attacker)) {
			attacker.sendSystemMessage(Component.literal(dealt(lost, victim.getDisplayName().getString())));
		}
		if (victim instanceof ServerPlayer player && enabled(player)) {
			String attacker = source.getEntity() == null || source.getEntity() == victim
				? null
				: source.getEntity().getDisplayName().getString();
			player.sendSystemMessage(Component.literal(taken(lost, attacker)));
		}
	}

	private static float vitality(final LivingEntity entity) {
		return Math.max(0.0F, entity.getHealth()) + entity.getAbsorptionAmount();
	}
}
