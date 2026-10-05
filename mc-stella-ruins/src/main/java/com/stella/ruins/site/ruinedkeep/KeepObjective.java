package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinMarkers;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinVaultBlockEntity;
import com.stella.ruins.world.SiteRun;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The climb and the throne. Floor 1 spawns when the core wakes. Each higher floor spawns when a player first stands
 * on it. Three key fragments used on the core open the throne door and spawn the Castellan. The Castellan's death
 * completes the objective and puts the named weapon and the Keep Sigil affix into the vault.
 */
public final class KeepObjective implements Objective {
	public static final int KEYS_NEEDED = 3;
	/** How far below a floor's walking level a climber already counts as on it: the last step of its stair. */
	private static final double ENTER_MARGIN = 0.6;
	/** The farthest a floor's player may stand from the core, flat: the corner of the footprint. */
	private static final double FOOTPRINT_RANGE = 42.0;
	private static final int OATH_SLOT = 13;
	private static final int SIGIL_SLOT = 4;

	private int spawned;
	private int used;
	private boolean open;
	private boolean castellanDead;
	private boolean rewarded;

	@Override
	public void start(final SiteRun run) {
		this.spawned = 0;
		this.used = 0;
		this.open = false;
		this.castellanDead = false;
		this.rewarded = false;
		spawnFloor(run, 0);
	}

	@Override
	public Progress tick(final SiteRun run) {
		int baseY = run.core().getY() - KeepLayout.CORE[1];
		int reached = -1;
		for (ServerPlayer player : run.level().players()) {
			if (player.isSpectator() || !player.isAlive() || Math.hypot(player.getX() - run.core().getX() - 0.5, player.getZ() - run.core().getZ() - 0.5) > FOOTPRINT_RANGE) {
				continue;
			}
			double rel = player.getY() - baseY;
			for (int f = 0; f < 4; f++) {
				if (rel >= KeepPlan.STAND[f] - ENTER_MARGIN) {
					reached = Math.max(reached, f);
				}
			}
		}
		for (int f = 1; f <= reached; f++) {
			spawnFloor(run, f);
		}
		if (this.castellanDead) {
			reward(run);
			return Progress.COMPLETE;
		}
		return Progress.RUNNING;
	}

	/** Spawns a floor's roster once: its sentinels, its knights, and its warden. The throne floor has no warden. */
	private void spawnFloor(final SiteRun run, final int floor) {
		int bit = 1 << floor;
		if ((this.spawned & bit) != 0) {
			return;
		}
		this.spawned |= bit;
		String n = Integer.toString(floor + 1);
		for (BlockPos spot : run.markers(KeepLayout.M_SENTINEL + n)) {
			run.spawn(KeepMobs.sentinel, Vec3.atBottomCenterOf(spot));
		}
		for (BlockPos spot : run.markers(KeepLayout.M_KNIGHT + n)) {
			run.spawn(KeepMobs.knight, Vec3.atBottomCenterOf(spot));
		}
		for (BlockPos spot : run.markers(KeepLayout.M_WARDEN + n)) {
			run.spawn(KeepMobs.warden, Vec3.atBottomCenterOf(spot));
		}
		StellaRuins.LOGGER.debug("[RUINS] keep floor {} roster spawned, roster now {}", floor + 1, run.alive());
	}

	@Override
	public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		if (this.open || this.used >= KEYS_NEEDED || held.isEmpty() || !held.is(KeepRewards.keyFragment)) {
			return false;
		}
		held.shrink(1);
		this.used++;
		if (this.used >= KEYS_NEEDED) {
			openThrone(run);
		}
		return true;
	}

	/** The throne door goes to air and the Castellan stands at his marker. */
	private void openThrone(final SiteRun run) {
		this.open = true;
		ServerLevel level = run.level();
		for (BlockPos spot : run.markers(KeepLayout.M_THRONE_DOOR)) {
			level.setBlock(spot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (BlockPos spot : run.markers(KeepLayout.M_CASTELLAN)) {
			run.spawn(KeepMobs.castellan, Vec3.atBottomCenterOf(spot));
		}
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		if (mob.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + KeepMobs.castellan.id())) {
			this.castellanDead = true;
		}
	}

	/** The vault holds the named weapon and the affix, once each, whatever number of times its table rolls. */
	private void reward(final SiteRun run) {
		if (this.rewarded) {
			return;
		}
		this.rewarded = true;
		for (BlockPos spot : run.markers(RuinMarkers.VAULT)) {
			BlockEntity be = run.level().getBlockEntity(spot);
			if (be instanceof RuinVaultBlockEntity vault) {
				vault.setItem(OATH_SLOT, new ItemStack(KeepRewards.castellansOath));
				vault.setItem(SIGIL_SLOT, new ItemStack(KeepRewards.sigil.template()));
				vault.setChanged();
			}
		}
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("spawned", this.spawned);
		tag.putInt("used", this.used);
		tag.putBoolean("open", this.open);
		tag.putBoolean("castellan_dead", this.castellanDead);
		tag.putBoolean("rewarded", this.rewarded);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.spawned = tag.getIntOr("spawned", 0);
		this.used = tag.getIntOr("used", 0);
		this.open = tag.getBooleanOr("open", false);
		this.castellanDead = tag.getBooleanOr("castellan_dead", false);
		this.rewarded = tag.getBooleanOr("rewarded", false);
	}
}
