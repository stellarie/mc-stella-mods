package com.stella.mobs;

import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RoleSpec;
import com.stella.mobs.scaling.ThreatState;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;

/**
 * The two facts about a mob that {@code mod-stella-rpg} reads by name: its threat level and its combat role.
 * The rpg mod finds this class by reflection, so keep both signatures public and static.
 */
public final class RpgFacts {
	private RpgFacts() {
	}

	public static int threat(final Mob mob) {
		return ThreatState.level(mob);
	}

	public static String role(final Mob mob) {
		RoleSpec configured = GearConfigStore.get().roleFor(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
		return configured != null ? configured.role().name().toLowerCase(Locale.ROOT) : "melee";
	}
}
