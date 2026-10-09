package com.stella.ruins.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a player must do to open a vault. The core builds a new instance from the site on every call and calls
 * {@link #load} on it, so an objective keeps all of its state in its saved tag and none that must outlive a call.
 */
public interface Objective {
	/** Called once, when a player wakes the core and the site roster has spawned. */
	void start(SiteRun run);

	/** Called every 20 ticks while the core is active. */
	Progress tick(SiteRun run);

	/** Called when a roster mob dies, by any cause. */
	default void onKill(final SiteRun run, final LivingEntity mob) {
	}

	/** Called when a player uses the core with an item. Return true when the use counted. */
	default boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		return false;
	}

	/** Vault rolls this run earns on top of the usual count, such as a harder rite the player chose. */
	default int extraRolls() {
		return 0;
	}

	CompoundTag save();

	void load(CompoundTag tag);
}
