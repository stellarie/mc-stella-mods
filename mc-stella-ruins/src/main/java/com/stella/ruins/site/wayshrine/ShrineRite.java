package com.stella.ruins.site.wayshrine;

import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.SiteRun;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The whole rite: the offering, then the waves, under one watch. If no player stays within {@link #WATCH_RADIUS}
 * blocks of the core for {@link #ABANDON_TICKS} ticks, the wave mobs vanish and the core goes back to dormant. The
 * offering is spent, so the next try needs a new one.
 */
public final class ShrineRite implements Objective {
	public static final int WATCH_RADIUS = 32;
	public static final long ABANDON_TICKS = 600L;
	private static final int SWEEP = 48;

	private final Supplier<Objective> factory;
	private Objective inner;
	private long seen;

	public ShrineRite(final Supplier<Objective> factory) {
		this.factory = factory;
		this.inner = factory.get();
	}

	@Override
	public void start(final SiteRun run) {
		this.seen = RuinClock.now(run.level());
		this.inner.start(run);
	}

	@Override
	public Progress tick(final SiteRun run) {
		long now = RuinClock.now(run.level());
		if (watched(run)) {
			this.seen = now;
		} else if (now - this.seen >= ABANDON_TICKS) {
			abandon(run);
			return Progress.RUNNING;
		}
		return this.inner.tick(run);
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		this.inner.onKill(run, mob);
	}

	@Override
	public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		return this.inner.onUse(run, player, held);
	}

	private static boolean watched(final SiteRun run) {
		Vec3 core = Vec3.atCenterOf(run.core());
		double limit = (double) WATCH_RADIUS * WATCH_RADIUS;
		for (ServerPlayer player : run.level().players()) {
			if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(core) <= limit) {
				return true;
			}
		}
		return false;
	}

	/** Removes the roster and any stray mob of the shrine, and puts the core back to sleep. */
	private void abandon(final SiteRun run) {
		ServerLevel level = run.level();
		RuinData.Entry entry = RuinData.get(level).entry(run.core());
		if (entry != null) {
			for (UUID id : entry.roster()) {
				Entity mob = level.getEntity(id);
				if (mob != null) {
					mob.discard();
				}
			}
			entry.clearRoster();
			entry.setPhase(RuinPhase.DORMANT);
		}
		AABB area = new AABB(run.core()).inflate(SWEEP);
		for (Mob stray : level.getEntitiesOfClass(Mob.class, area, WayshrineMobs::isShrineMob)) {
			stray.discard();
		}
		this.inner = this.factory.get();
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("inner", this.inner.save());
		tag.putLong("seen", this.seen);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.inner.load(tag.getCompoundOrEmpty("inner"));
		this.seen = tag.getLongOr("seen", 0L);
	}
}
