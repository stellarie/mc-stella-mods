package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;

/** The lingering breath clouds the dragon's fireballs leave: vanilla makes them last 600 ticks, and here they last {@link DragonRules#BREATH_TICKS}. */
final class DragonBreath {
	private static final int KEPT = 256;
	/** A cloud smaller than this radius is gone. */
	private static final float SMALLEST = 0.5F;
	private static final List<UUID> CLOUDS = new ArrayList<>();

	private DragonBreath() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AreaEffectCloud cloud && level.dimension().equals(Level.END) && cloud.getOwner() instanceof EnderDragon
				&& cloud.getWaitTime() + cloud.getDuration() > DragonRules.BREATH_TICKS) {
				limit(cloud);
				CLOUDS.add(cloud.getUUID());
				if (CLOUDS.size() > KEPT) {
					CLOUDS.remove(0);
				}
			}
		});
	}

	/**
	 * Makes a cloud last {@link DragonRules#BREATH_TICKS} ticks from its first, its wait included. A cloud shrinks to
	 * nothing at a radius of one half, so it shrinks at the rate that reaches that radius as its duration ends.
	 */
	static void limit(final AreaEffectCloud cloud) {
		int active = Math.max(1, DragonRules.BREATH_TICKS - cloud.getWaitTime());
		cloud.setDuration(active);
		cloud.setRadiusPerTick(-(cloud.getRadius() - SMALLEST) / active);
	}

	/** The clouds of the dragon's fireballs that this class shortened, in order. */
	static List<UUID> clouds() {
		return List.copyOf(CLOUDS);
	}

	static void reset() {
		CLOUDS.clear();
	}
}
