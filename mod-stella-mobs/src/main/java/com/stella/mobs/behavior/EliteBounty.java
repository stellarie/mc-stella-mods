package com.stella.mobs.behavior;

import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An elite that a player kills drops a bounty of emeralds, more for a higher level. A boss drops four times the bounty.
 * A kill by another mob, the void, or a command pays nothing.
 */
public final class EliteBounty {
	public static final int BASE = 2;
	public static final int LEVELS_PER_EMERALD = 10;
	public static final int CAP = 32;
	public static final int BOSS_FACTOR = 4;

	private EliteBounty() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(EliteBounty::afterDeath);
	}

	/** The emeralds for an elite of a level: 2 plus one per 10 levels, at most 32. */
	public static int emeralds(final int level, final boolean boss) {
		int bounty = Math.min(CAP, BASE + Math.max(0, level) / LEVELS_PER_EMERALD);
		return boss ? bounty * BOSS_FACTOR : bounty;
	}

	private static void afterDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !(source.getEntity() instanceof Player) || !(mob.level() instanceof ServerLevel level)
			|| !EliteState.hasEliteFlag(mob)) {
			return;
		}
		int count = emeralds(ThreatState.level(mob), EliteState.isBoss(mob));
		while (count > 0) {
			int stack = Math.min(count, Items.EMERALD.getDefaultMaxStackSize());
			mob.spawnAtLocation(level, new ItemStack(Items.EMERALD, stack));
			count -= stack;
		}
	}
}
