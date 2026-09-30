package com.stella.mobs.api;

import com.stella.mobs.behavior.EliteState;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class ReadoutRankProvider {
	private static final String OBJECT_SHARE_KEY = "mod-stella-mobs:readout-rank";
	private static final String NORMAL = "normal";
	private static final String ELITE = "elite";
	private static final String BOSS = "boss";

	private ReadoutRankProvider() {
	}

	public static void register() {
		Function<Entity, String> provider = ReadoutRankProvider::rank;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}

	private static String rank(final Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return NORMAL;
		}

		if (EliteState.hasBossFlag(mob)) {
			return BOSS;
		}

		if (EliteState.hasEliteFlag(mob)) {
			return ELITE;
		}

		return NORMAL;
	}
}
