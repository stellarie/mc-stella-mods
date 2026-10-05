package com.stella.ruins.site.sealedvault;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Objectives;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinFrame;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.SiteRun;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Collect three seals and use them on the core. The vault wakes its whole roster at once, at the markers of each
 * kind, and never spawns it again. The seal count is the framework's own keys objective.
 */
public final class VaultObjective implements Objective {
	public static final int SEALS = VaultPlan.SEALS;

	private final Objective keys = Objectives.keys(VaultItems.VAULT_SEAL, SEALS);

	@Override
	public void start(final SiteRun run) {
		this.keys.start(run);
		if (run.level().getBlockEntity(run.core()) instanceof RuinCoreBlockEntity core && core.frame() instanceof RuinFrame frame) {
			VaultRegistry.get(run.level()).add(run.core(), frame.box());
		}
		spawnAll(run, VaultMobs.VAULT_GUARDIAN, VaultMarkers.GUARDIAN, 0.0);
		spawnAll(run, VaultMobs.VOID_WRAITH, VaultMarkers.WRAITH, 0.0);
		spawnAll(run, VaultMobs.VAULT_WARDEN, VaultMarkers.WARDEN, 0.0);
		StellaRuins.LOGGER.debug("[RUINS] sealed vault at {} woke roster={}", run.core().toShortString(), run.alive());
	}

	private static void spawnAll(final SiteRun run, final RuinMobKind kind, final String marker, final double lift) {
		List<BlockPos> spots = run.markers(marker);
		for (BlockPos spot : spots) {
			run.spawn(kind, new Vec3(spot.getX() + 0.5, spot.getY() + lift, spot.getZ() + 0.5));
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		return this.keys.tick(run);
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		this.keys.onKill(run, mob);
	}

	@Override
	public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		return this.keys.onUse(run, player, held);
	}

	@Override
	public CompoundTag save() {
		return this.keys.save();
	}

	@Override
	public void load(final CompoundTag tag) {
		this.keys.load(tag);
	}
}
