package com.stella.mobs.behavior;

import com.stella.mobs.mixin.WitherBossInvoker;
import com.stella.mobs.projectile.WitherSkullShot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Looses the skulls of an encounter Wither and keeps the identities of the ones still in the air. */
public final class WitherShots {
	private static final ThreadLocal<WitherSkullShot.Spec> PENDING = new ThreadLocal<>();
	private static final ThreadLocal<WitherSkullShot> LAST = new ThreadLocal<>();
	private static final Map<UUID, List<WitherSkullShot>> LIVE = new HashMap<>();

	private WitherShots() {
	}

	/** Called by the skull redirect inside the vanilla shot. It builds the skull the pending shot asks for. */
	public static WitherSkullShot create(final Level level, final LivingEntity owner, final Vec3 direction) {
		WitherSkullShot skull = new WitherSkullShot(level, owner, direction);
		skull.configure(PENDING.get(), owner.position());
		LAST.set(skull);
		return skull;
	}

	/** The shot through a vanilla head toward a point. Returns the skull, or null when the shot made none. */
	public static @Nullable WitherSkullShot fire(final WitherBoss wither, final int head, final Vec3 at, final WitherSkullShot.Spec spec) {
		PENDING.set(spec);
		LAST.remove();
		try {
			((WitherBossInvoker) wither).stellaMobs$shoot(head, at.x, at.y, at.z, spec.dangerous());
			WitherSkullShot skull = LAST.get();
			if (skull != null) {
				track(wither, skull);
			}
			return skull;
		} finally {
			PENDING.remove();
			LAST.remove();
		}
	}

	/** True while a shot is pending, which is when the redirect builds an encounter skull. */
	public static boolean pending() {
		return PENDING.get() != null;
	}

	/** Skulls of this Wither still in the world. A skull that hit or burnt out leaves the count. */
	public static int live(final WitherBoss wither) {
		List<WitherSkullShot> skulls = LIVE.get(wither.getUUID());
		if (skulls == null) {
			return 0;
		}
		skulls.removeIf(WitherSkullShot::isRemoved);
		return skulls.size();
	}

	/** A copy of the Wither's skulls in the air, for a test to watch. */
	public static List<WitherSkullShot> snapshot(final WitherBoss wither) {
		List<WitherSkullShot> skulls = LIVE.get(wither.getUUID());
		return skulls == null ? List.of() : List.copyOf(skulls);
	}

	public static void discardAll(final WitherBoss wither) {
		List<WitherSkullShot> skulls = LIVE.remove(wither.getUUID());
		if (skulls != null) {
			skulls.forEach(WitherSkullShot::discard);
		}
	}

	/** Adds a skull the caller built itself, so it counts against the cap. */
	public static void track(final WitherBoss wither, final WitherSkullShot skull) {
		LIVE.computeIfAbsent(wither.getUUID(), id -> new ArrayList<>()).add(skull);
	}
}
