package com.stella.learn;

import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The links to the mobs mod. They go through the ObjectShare keys of that mod and need none of its classes. With the
 * keys absent, the role comes from the held gear and the band from fixed numbers.
 */
final class MobShare {
	static final String ROLE_KEY = "mod-stella-mobs:combat-role";
	static final String BAND_KEY = "mod-stella-mobs:combat-band";
	static final String MOVE_OVERRIDE_KEY = "mod-stella-mobs:move-override";
	static final String MELEE = "MELEE";
	private static final double FALLBACK_NEAR = 5.0;
	private static final double FALLBACK_FAR = 24.0;

	private MobShare() {
	}

	/** Tells the mobs mod which mobs the formation goal walks, so its standoff goal does not stop them. */
	static void publish() {
		FabricLoader.getInstance().getObjectShare().put(MOVE_OVERRIDE_KEY, (Predicate<Mob>) FormationGoal::walking);
	}

	/** MELEE, RANGED or MAGIC. */
	@SuppressWarnings("unchecked")
	static String role(final Mob mob) {
		Object share = FabricLoader.getInstance().getObjectShare().get(ROLE_KEY);
		if (share instanceof Function<?, ?> function && ((Function<Mob, ?>) function).apply(mob) instanceof String role) {
			return role;
		}

		if (mob instanceof SpellcasterIllager) {
			return "MAGIC";
		}

		return mob instanceof Witch || shoots(mob.getMainHandItem()) || shoots(mob.getOffhandItem()) ? "RANGED" : MELEE;
	}

	/** The near and far edge of the firing band, in blocks. */
	@SuppressWarnings("unchecked")
	static double[] band(final Mob mob) {
		Object share = FabricLoader.getInstance().getObjectShare().get(BAND_KEY);
		if (share instanceof Function<?, ?> function && ((Function<Mob, ?>) function).apply(mob) instanceof double[] band && band.length == 2) {
			return band;
		}

		return new double[] {FALLBACK_NEAR, FALLBACK_FAR};
	}

	private static boolean shoots(final ItemStack stack) {
		return stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT);
	}
}
