package com.stella.ruins.site.spidernest;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.RuinConfig;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinLevels;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinVaultBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The smaller vault table for a hatched nest. The framework fills a vault from the one table the site names, so
 * when the Matriarch's death completes the core, this swaps that fill for {@code chests/spider_nest_hatched}.
 * <p>
 * The swap runs at the end of the same server tick that completed the core, so no player can take a stack first.
 */
public final class NestVaults {
	private record Pending(ServerLevel level, BlockPos core) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private NestVaults() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NestVaults::settle);
	}

	/** Marks a core whose vault holds the full table but should hold the hatched one. */
	public static void queue(final ServerLevel level, final BlockPos core) {
		synchronized (PENDING) {
			PENDING.add(new Pending(level, core.immutable()));
		}
	}

	/** True while the swap of one core waits. */
	public static boolean queued(final ServerLevel level, final BlockPos core) {
		synchronized (PENDING) {
			return PENDING.stream().anyMatch(pending -> pending.level() == level && pending.core().equals(core));
		}
	}

	/** Swaps the vault of every queued core that has completed. A core that has not completed yet waits. */
	public static void settle(final MinecraftServer server) {
		List<Pending> due = new ArrayList<>();
		synchronized (PENDING) {
			PENDING.removeIf(pending -> {
				RuinData.Entry entry = RuinData.get(pending.level()).entry(pending.core());
				if (entry == null) {
					return true;
				}
				if (entry.phase() == RuinPhase.COMPLETE) {
					due.add(pending);
					return true;
				}
				return false;
			});
		}
		for (Pending pending : due) {
			swap(pending.level(), pending.core());
		}
	}

	private static void swap(final ServerLevel level, final BlockPos core) {
		if (!(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block)) {
			return;
		}
		int rolls = RuinConfig.get().vaultRolls(RuinLevels.at(level, Vec3.atCenterOf(core)));
		int swapped = 0;
		for (BlockPos vault : block.markers().getOrDefault(RuinMarkers.VAULT, List.of())) {
			if (level.getBlockEntity(vault) instanceof RuinVaultBlockEntity chest) {
				chest.clearContent();
				chest.fill(level, NestMobs.HATCHED_VAULT_LOOT, rolls);
				swapped++;
			}
		}
		StellaRuins.LOGGER.info("[RUINS] nest core={} vault swapped to the hatched table vaults={} rolls={}", core.toShortString(), swapped, rolls);
	}
}
