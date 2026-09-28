package com.stella.nether;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** What the Flame Mantle does worn: faster healing, fire for whoever strikes its wearer, and little harm from fire. */
public final class FlameMantle {
	/** A wearer below full health heals this much this often, on top of what they heal anyway. */
	public static final int REGEN_EVERY_TICKS = 40;
	public static final float REGEN_AMOUNT = 1.0F;
	public static final int ATTACKER_BURN_SECONDS = 4;
	/** The share of fire damage the Mantle takes away. */
	public static final float FIRE_REDUCTION = 0.8F;

	private FlameMantle() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REGEN_EVERY_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (worn(player) && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
					player.heal(REGEN_AMOUNT);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (worn(entity) && source.getEntity() instanceof LivingEntity attacker && attacker != entity) {
				attacker.igniteForSeconds(ATTACKER_BURN_SECONDS);
			}
		});
	}

	public static boolean worn(final LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.CHEST).is(NetherItems.FLAME_MANTLE);
	}

	/** Fire harms a wearer a fifth as much. */
	public static float scaleDamageTaken(final LivingEntity entity, final DamageSource source, final float amount) {
		return source.is(DamageTypeTags.IS_FIRE) && worn(entity) ? amount * (1.0F - FIRE_REDUCTION) : amount;
	}
}
