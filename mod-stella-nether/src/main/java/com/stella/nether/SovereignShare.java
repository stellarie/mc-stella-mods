package com.stella.nether;

import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/** Publishes {@code mod-stella-nether:sovereign}, a {@code Predicate<Entity>} that is true for a Sovereign only. */
public final class SovereignShare {
	public static final String OBJECT_SHARE_KEY = "mod-stella-nether:sovereign";

	private SovereignShare() {
	}

	public static void register() {
		Predicate<Entity> share = BlazeSovereign::isSovereign;
		FabricLoader.getInstance().getObjectShare().put(OBJECT_SHARE_KEY, share);
	}
}
