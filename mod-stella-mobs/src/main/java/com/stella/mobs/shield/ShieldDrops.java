package com.stella.mobs.shield;

import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.WitherEncounter;
import com.stella.mobs.behavior.WitherSpawnlings;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Any killed mob may leave a Shield Generator. A mob that drops nothing by design never does: a Wither minion, a
 * small Wither, and a summoned mob.
 */
public final class ShieldDrops {
	private ShieldDrops() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(ShieldDrops::onDeath);
	}

	/** One roll against {@code chance}. */
	public static boolean roll(final RandomSource random, final double chance) {
		return chance > 0.0 && random.nextDouble() < chance;
	}

	/** True when {@code mob} drops no loot by design. */
	public static boolean dropsNothing(final Mob mob) {
		return WitherEncounter.isMinion(mob) || WitherSpawnlings.isSmall(mob) || EliteState.isSummoned(mob);
	}

	private static void onDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || dropsNothing(mob)) {
			return;
		}
		if (roll(mob.getRandom(), ShieldConfig.dropChance())) {
			mob.spawnAtLocation(level, new ItemStack(ShieldBlocks.ITEM));
		}
	}
}
