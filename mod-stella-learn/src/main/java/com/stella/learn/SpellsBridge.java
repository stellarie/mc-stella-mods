package com.stella.learn;

import com.stella.spells.MobSpells;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The one door to the optional spells mod. Every call checks that the mod is loaded first and only then reaches the
 * nested class that names its classes, so this mod loads and runs without it: nothing outside `Impl` refers to it.
 */
public final class SpellsBridge {
	public static final String SPELLS_MOD = "mod-stella-spells";
	/** Blocks within which a support spell reaches the caller: inside the smallest support radius the spells mod uses. */
	public static final double SUPPORT_REACH = 6.0;

	private SpellsBridge() {
	}

	public static boolean present() {
		return FabricLoader.getInstance().isModLoaded(SPELLS_MOD);
	}

	/** True when this mob carries a support spell and the caller stands where the spell reaches. */
	public static boolean canSupport(final Mob mob, final LivingEntity caller) {
		return present() && Impl.canSupport(mob, caller);
	}

	public static void support(final Mob mob) {
		if (present()) {
			Impl.support(mob);
		}
	}

	/** True when this mob carries a striking spell and the player is within its casting range. */
	public static boolean canStrike(final Mob mob, final ServerPlayer player) {
		return present() && Impl.canStrike(mob, player);
	}

	public static void strike(final Mob mob, final ServerPlayer player) {
		if (present()) {
			Impl.strike(mob, player);
		}
	}

	private static final class Impl {
		static boolean canSupport(final Mob mob, final LivingEntity caller) {
			return !MobSpells.supportOf(mob).isEmpty() && mob.distanceTo(caller) <= SUPPORT_REACH;
		}

		static void support(final Mob mob) {
			for (String spell : MobSpells.supportOf(mob)) {
				if (MobSpells.support(mob, spell)) {
					return;
				}
			}
		}

		static boolean canStrike(final Mob mob, final ServerPlayer player) {
			return !MobSpells.offensiveOf(mob).isEmpty() && mob.distanceTo(player) <= MobSpells.CAST_RANGE;
		}

		static void strike(final Mob mob, final ServerPlayer player) {
			MobSpells.castAt(mob, MobSpells.offensiveOf(mob).get(0), player);
		}
	}
}
