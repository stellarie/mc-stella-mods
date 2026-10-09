package com.stella.ruins.world;

import com.stella.ruins.StellaRuins;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The life of a core: waking, running the objective, paying out, and rearming. */
final class RuinRuns {
	private RuinRuns() {
	}

	static void pulse(final ServerLevel level, final RuinCoreBlockEntity core) {
		RuinFrame frame = core.frame();
		RuinSite site = frame == null ? null : frame.siteOrNull();
		if (site == null) {
			return;
		}
		BlockPos pos = core.getBlockPos();
		RuinData data = RuinData.get(level);
		RuinData.Entry entry = data.entryFor(pos, site.id());
		prune(level, data, entry);
		switch (entry.phase()) {
			case DORMANT -> {
				if (!SiteRun.playersNear(level, pos, site.activationRadius()).isEmpty()) {
					activate(level, site, data, entry, core.markers());
				}
			}
			case ACTIVE -> {
				if (site.omens()) {
					Omens.pulse(SiteRun.playersNear(level, pos, site.activationRadius()), Omens.of(entry.omens()));
				}
				tickObjective(level, site, data, entry, core.markers());
			}
			case COMPLETE -> {
				long delay = site.claim().resetTicks(site.id());
				if (delay >= 0 && RuinClock.now(level) - entry.claimTime() >= delay) {
					rearm(level, site, data, entry, core.markers());
				}
			}
			case COOLDOWN -> {
				if (SiteRun.playersNear(level, pos, site.activationRadius()).isEmpty()) {
					entry.setPhase(RuinPhase.DORMANT);
					StellaRuins.LOGGER.debug("[RUINS] core {} {} cooled down to dormant", site.id(), pos.toShortString());
				}
			}
		}
	}

	/** A mob of the roster died, by any cause. The objective hears of it, and may complete at once. */
	static void onMobDeath(final ServerLevel level, final LivingEntity mob) {
		RuinData data = RuinData.get(level);
		RuinData.Entry entry = data.removeMob(mob.getUUID());
		if (entry == null || entry.phase() != RuinPhase.ACTIVE) {
			return;
		}
		RuinSite site = RuinSites.get(entry.site());
		if (site == null) {
			return;
		}
		Map<String, List<BlockPos>> markers = markersAt(level, entry.pos());
		SiteRun run = new SiteRun(level, entry.pos(), site, data, markers);
		Objective objective = site.newObjective();
		objective.load(entry.objective());
		objective.onKill(run, mob);
		entry.setObjective(objective.save());
		tickObjective(level, site, data, entry, markers);
	}

	/** A player used the core with an item. Returns true when the objective took it. */
	static boolean use(final ServerLevel level, final BlockPos pos, final ServerPlayer player, final ItemStack held) {
		RuinData data = RuinData.get(level);
		RuinData.Entry entry = data.entry(pos);
		if (entry == null || entry.phase() != RuinPhase.ACTIVE) {
			return false;
		}
		RuinSite site = RuinSites.get(entry.site());
		if (site == null) {
			return false;
		}
		Map<String, List<BlockPos>> markers = markersAt(level, pos);
		SiteRun run = new SiteRun(level, pos, site, data, markers);
		Objective objective = site.newObjective();
		objective.load(entry.objective());
		boolean used = objective.onUse(run, player, held);
		entry.setObjective(objective.save());
		if (used) {
			tickObjective(level, site, data, entry, markers);
		}
		return used;
	}

	private static void activate(final ServerLevel level, final RuinSite site, final RuinData data, final RuinData.Entry entry, final Map<String, List<BlockPos>> markers) {
		entry.clearRoster();
		entry.setClaimTime(-1L);
		entry.setPhase(RuinPhase.ACTIVE);
		List<Omen> omens = site.omens() ? Omens.roll(level.getRandom(), entry.tier()) : List.of();
		entry.setOmens(Omens.ids(omens));
		Omens.announce(SiteRun.playersNear(level, entry.pos(), site.activationRadius()), omens);
		SiteRun run = new SiteRun(level, entry.pos(), site, data, markers);
		run.spawn(site.roster());
		Objective objective = site.newObjective();
		objective.start(run);
		entry.setObjective(objective.save());
		StellaRuins.LOGGER.debug("[RUINS] core {} {} woke roster={}", site.id(), entry.pos().toShortString(), entry.roster().size());
	}

	private static void tickObjective(final ServerLevel level, final RuinSite site, final RuinData data, final RuinData.Entry entry, final Map<String, List<BlockPos>> markers) {
		SiteRun run = new SiteRun(level, entry.pos(), site, data, markers);
		Objective objective = site.newObjective();
		objective.load(entry.objective());
		Progress progress = objective.tick(run);
		entry.setObjective(objective.save());
		if (progress == Progress.COMPLETE) {
			complete(level, site, entry, run);
		}
	}

	private static void complete(final ServerLevel level, final RuinSite site, final RuinData.Entry entry, final SiteRun run) {
		entry.setPhase(RuinPhase.COMPLETE);
		entry.setClaimTime(RuinClock.now(level));
		int mobLevel = run.containerLevel();
		int rolls = RuinConfig.get().vaultRolls(mobLevel) + entry.tier() + entry.omens().size();
		Objective objective = site.newObjective();
		objective.load(entry.objective());
		rolls += objective.extraRolls();
		int opened = 0;
		for (BlockPos vault : run.markers(RuinMarkers.VAULT)) {
			if (level.getBlockEntity(vault) instanceof RuinVaultBlockEntity chest) {
				chest.setSealed(level, false);
				chest.fill(level, site.vaultLoot(), rolls);
				if (opened == 0) {
					RuinTrophies.award(level, site, chest);
				}
				opened++;
			}
		}
		StellaRuins.LOGGER.debug("[RUINS] core {} {} complete level={} rolls={} vaultsOpened={}", site.id(), entry.pos().toShortString(), mobLevel, rolls, opened);
	}

	private static void rearm(final ServerLevel level, final RuinSite site, final RuinData data, final RuinData.Entry entry, final Map<String, List<BlockPos>> markers) {
		for (BlockPos vault : markers.getOrDefault(RuinMarkers.VAULT, List.of())) {
			if (level.getBlockEntity(vault) instanceof RuinVaultBlockEntity chest) {
				chest.setSealed(level, true);
			}
		}
		entry.clearRoster();
		entry.setObjective(new CompoundTag());
		entry.setClaimTime(-1L);
		entry.setOmens(List.of());
		entry.setTier(Math.min(RuinConfig.get().tierCap(), entry.tier() + 1));
		boolean watched = !SiteRun.playersNear(level, entry.pos(), site.activationRadius()).isEmpty();
		entry.setPhase(watched ? RuinPhase.COOLDOWN : RuinPhase.DORMANT);
		StellaRuins.LOGGER.debug("[RUINS] core {} {} rearmed phase={} tier={}", site.id(), entry.pos().toShortString(), entry.phase(), entry.tier());
		if (site.rearmHook() != null) {
			site.rearmHook().onRearm(level, entry.pos(), markers);
		}
	}

	/** Drops roster mobs the world can show are gone: found, and removed or dead. */
	private static void prune(final ServerLevel level, final RuinData data, final RuinData.Entry entry) {
		for (UUID id : entry.roster()) {
			Entity mob = level.getEntity(id);
			if (mob != null && (mob.isRemoved() || !mob.isAlive())) {
				data.removeMob(id);
			}
		}
	}

	private static Map<String, List<BlockPos>> markersAt(final ServerLevel level, final BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof RuinCoreBlockEntity core ? core.markers() : Map.of();
	}
}
