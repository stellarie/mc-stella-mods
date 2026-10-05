package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.reward.RuinSets;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 4-piece bonus of the Starweave set: magic damage the wearer deals is 15 percent higher. A Fabric event
 * carries it, on indirect magic and plain magic sources only. The extra hit never starts another bonus.
 */
public final class StarweaveMagic {
	private static boolean adding;

	private StarweaveMagic() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StarweaveMagic::onDamage);
	}

	private static void onDamage(final LivingEntity victim, final DamageSource source, final float baseDamage, final float damageTaken, final boolean blocked) {
		if (adding || blocked || damageTaken <= 0.0F || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		if (!source.is(DamageTypes.INDIRECT_MAGIC) && !source.is(DamageTypes.MAGIC)) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity wearer) || RuinSets.wornPieces(wearer, CraterItems.SET_ID) < 4) {
			return;
		}
		adding = true;
		try {
			victim.damageCooldownTime = 0;
			victim.hurtServer(level, source, (float) (damageTaken * CraterItems.FOUR_PIECE_MAGIC));
		} finally {
			adding = false;
		}
	}
}
