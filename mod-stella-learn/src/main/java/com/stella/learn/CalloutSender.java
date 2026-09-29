package com.stella.learn;

import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;

/**
 * A monster hurt by a player calls. What it asks for follows its health and what the player is known to do: a heal
 * when it is badly hurt, a debuff against a turtle, a buff against a rusher, and a rally otherwise. The caller's own
 * cooldown, in the board, keeps one fight from calling on every hit.
 */
public final class CalloutSender {
	/** A caller below this share of its health asks for a heal. */
	public static final float HURT_SHARE = 0.5F;
	/** The share of a full rusher at which a caller asks casters to strengthen it. */
	public static final double RUSH_SHARE = 0.5;
	public static final int CALL_TICKS = 200;
	public static final int HEAL_PRIORITY = 3;
	public static final int EFFECT_PRIORITY = 2;
	public static final int RALLY_PRIORITY = 1;
	private static final Identifier SLOWNESS = Identifier.withDefaultNamespace("slowness");
	private static final Identifier STRENGTH = Identifier.withDefaultNamespace("strength");

	private CalloutSender() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CalloutSender::afterDamage);
	}

	static void afterDamage(
		final LivingEntity entity, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked
	) {
		if (!(entity instanceof Monster caller) || damageTaken <= 0.0F || caller.entityTags().contains(StellaMobs.NO_BEHAVIOR_TAG)) {
			return;
		}

		if (source.getEntity() instanceof ServerPlayer player) {
			CalloutBoard.send(caller, player, choose(caller, player), LearnConfigStore.get());
		}
	}

	public static Callout choose(final Monster caller, final ServerPlayer player) {
		if (caller.getHealth() < caller.getMaxHealth() * HURT_SHARE) {
			return call(CalloutKits.HEAL, caller, player, HEAL_PRIORITY, null);
		}

		if (WaitOutShieldGoal.shieldPressure(player) >= LearnConfigStore.get().shieldThreshold()) {
			return call(CalloutKits.DEBUFF, caller, player, EFFECT_PRIORITY, SLOWNESS);
		}

		if (LearnedBand.rushPressure(player) >= RUSH_SHARE) {
			return call(CalloutKits.BUFF, caller, player, EFFECT_PRIORITY, STRENGTH);
		}

		return call(Rally.TYPE, caller, player, RALLY_PRIORITY, null);
	}

	private static Callout call(final Identifier type, final Monster caller, final ServerPlayer player, final int priority, final Identifier detail) {
		return new Callout(type, caller.getUUID(), player.getUUID(), priority, CALL_TICKS, null, detail);
	}
}
