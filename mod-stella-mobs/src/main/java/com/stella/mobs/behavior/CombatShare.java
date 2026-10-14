package com.stella.mobs.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * The ObjectShare links between this mod and a mod that drives groups of mobs, such as the formations of the learning
 * mod. Neither mod needs a class of the other.
 *
 * <ul>
 * <li>{@link #ROLE_KEY}: this mod puts a {@code Function<Mob, String>} that answers MELEE, RANGED or MAGIC.
 * <li>{@link #BAND_KEY}: this mod puts a {@code Function<Mob, double[]>} that answers the near and far edge of the band.
 * <li>{@link #MOVE_OVERRIDE_KEY}: the other mod puts a {@code Predicate<Mob>} that is true while it walks the mob. The
 * standoff goal yields then, because it would stop the walk on every tick.
 * </ul>
 */
public final class CombatShare {
	public static final String ROLE_KEY = "mod-stella-mobs:combat-role";
	public static final String BAND_KEY = "mod-stella-mobs:combat-band";
	public static final String MOVE_OVERRIDE_KEY = "mod-stella-mobs:move-override";

	private CombatShare() {
	}

	public static void register() {
		FabricLoader.getInstance().getObjectShare().put(ROLE_KEY, (Function<Mob, String>) mob -> ReactiveState.get(mob).getRole().name());
		FabricLoader.getInstance().getObjectShare().put(BAND_KEY, (Function<Mob, double[]>) mob -> {
			ReactiveState state = ReactiveState.get(mob);
			return new double[] {state.getNearDistance(), state.getFarDistance()};
		});
	}

	/** True while another mod walks this mob. False when no mod has put a predicate. */
	@SuppressWarnings("unchecked")
	public static boolean walkedByOther(final Mob mob) {
		Object share = FabricLoader.getInstance().getObjectShare().get(MOVE_OVERRIDE_KEY);
		return share instanceof Predicate<?> predicate && ((Predicate<Mob>) predicate).test(mob);
	}
}
