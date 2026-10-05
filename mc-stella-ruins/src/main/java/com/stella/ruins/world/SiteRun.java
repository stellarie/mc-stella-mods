package com.stella.ruins.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What an objective sees of its run: the level, the core, the roster and the players nearby. */
public final class SiteRun {
	private final ServerLevel level;
	private final BlockPos core;
	private final RuinSite site;
	private final RuinData data;
	private final Map<String, List<BlockPos>> markers;

	SiteRun(final ServerLevel level, final BlockPos core, final RuinSite site, final RuinData data, final Map<String, List<BlockPos>> markers) {
		this.level = level;
		this.core = core;
		this.site = site;
		this.data = data;
		this.markers = markers;
	}

	public ServerLevel level() {
		return this.level;
	}

	public BlockPos core() {
		return this.core;
	}

	public RuinSite site() {
		return this.site;
	}

	/** The mobs-mod container level at the core, or 0 without that mod. */
	public int containerLevel() {
		return RuinLevels.at(this.level, Vec3.atCenterOf(this.core));
	}

	/** The players who count for activation: alive, not spectating, inside the site's radius. */
	public List<ServerPlayer> playersNear() {
		return playersNear(this.level, this.core, this.site.activationRadius());
	}

	static List<ServerPlayer> playersNear(final ServerLevel level, final BlockPos core, final int radius) {
		double limit = (double) radius * radius;
		Vec3 center = Vec3.atCenterOf(core);
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(center) <= limit) {
				near.add(player);
			}
		}
		return near;
	}

	/** How many mobs of this run's roster still live. */
	public int alive() {
		RuinData.Entry entry = this.data.entry(this.core);
		return entry == null ? 0 : entry.roster().size();
	}

	/** The world spots a layout named, in the order named. */
	public List<BlockPos> markers(final String name) {
		return this.markers.getOrDefault(name, List.of());
	}

	public RandomSource random() {
		return this.level.getRandom();
	}

	/** Spawns one mob of a kind and adds it to the roster. Returns null when the kind could not spawn. */
	public @Nullable Mob spawn(final RuinMobKind kind, final Vec3 at) {
		Mob mob = kind.spawn(this.level, at);
		if (mob != null) {
			mob.addTag(RuinMobs.coreTag(this.core.getX(), this.core.getY(), this.core.getZ()));
			RuinData.Entry entry = this.data.entry(this.core);
			if (entry != null) {
				entry.addMob(mob.getUUID());
			}
		}
		return mob;
	}

	/** Spawns a whole roster at the {@code spawn} markers, or in a ring round the core when the site has none. */
	public List<Mob> spawn(final Roster roster) {
		List<Mob> spawned = new ArrayList<>();
		List<BlockPos> spots = markers(RuinMarkers.SPAWN);
		RandomSource random = random();
		int index = spots.isEmpty() ? 0 : random.nextInt(spots.size());
		for (Roster.Entry entry : roster.entries()) {
			for (int i = 0; i < entry.count(); i++) {
				Vec3 at;
				if (spots.isEmpty()) {
					double angle = random.nextDouble() * Math.PI * 2.0;
					double radius = 3.0 + random.nextDouble() * 3.0;
					at = new Vec3(this.core.getX() + 0.5 + Math.cos(angle) * radius, this.core.getY() + 1.0, this.core.getZ() + 0.5 + Math.sin(angle) * radius);
				} else {
					BlockPos spot = spots.get(index++ % spots.size());
					at = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				}
				Mob mob = spawn(entry.kind(), at);
				if (mob != null) {
					spawned.add(mob);
				}
			}
		}
		return spawned;
	}
}
