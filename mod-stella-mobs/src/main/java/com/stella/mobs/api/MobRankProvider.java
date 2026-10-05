package com.stella.mobs.api;

import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import com.stella.mobs.behavior.EliteState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Publishes a mob's rank under {@code mod-stella-mobs:mob-rank} as a {@code Function<Entity, String>}.
 * The values are {@link #SUMMONED}, {@link #BOSS}, {@link #ELITE}, and {@link #NORMAL}, checked in that order.
 */
public final class MobRankProvider {
	public static final String OBJECT_SHARE_KEY = "mod-stella-mobs:mob-rank";
	public static final String SUMMONED = "summoned";
	public static final String BOSS = "boss";
	public static final String ELITE = "elite";
	public static final String NORMAL = "normal";

	private MobRankProvider() {
	}

	public static void register() {
		Function<Entity, String> provider = MobRankProvider::rank;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, provider);
	}

	/** Reads the flags without creating a state, so a plain mob's save stays clean. */
	public static String rank(final Entity entity) {
		if (!(entity instanceof Mob mob)) {
			return NORMAL;
		}
		EliteState state = ((AttachmentTarget) mob).getAttached(EliteState.TYPE);
		if (state == null) {
			return NORMAL;
		}
		if (state.summoned()) {
			return SUMMONED;
		}
		if (state.boss()) {
			return BOSS;
		}
		return state.elite() ? ELITE : NORMAL;
	}
}
