package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turns a player's attack on a crystal shaft into a strike. A Fabric event carries it: the library adds no mixin.
 * The event always cancels, so a pillar never breaks, in creative mode too.
 */
public final class ChimeStrikes {
	/** How many Shard Sentinels a wrong strike summons. */
	public static final int SENTINELS_PER_MISTAKE = 2;
	/** How far from a struck shaft the core can stand. */
	private static final int SCAN_XZ = 8;
	private static final int SCAN_DOWN = 6;

	private ChimeStrikes() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register(ChimeStrikes::onAttack);
	}

	static InteractionResult onAttack(final Player player, final Level level, final InteractionHand hand, final BlockPos pos, final Direction side) {
		if (!level.getBlockState(pos).is(ResonantCrystalBlock.BLOCK)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer && !player.isSpectator()) {
			handle(server, pos);
		}
		return InteractionResult.FAIL;
	}

	/** Reads one strike on the shaft at {@code pos} against the objective of the core beside it. */
	public static ChimeObjective.@Nullable Outcome handle(final ServerLevel level, final BlockPos pos) {
		BlockPos core = findCore(level, pos);
		if (core == null) {
			return null;
		}
		RuinData.Entry entry = RuinData.get(level).entry(core);
		RuinSite site = entry == null ? null : RuinSites.get(entry.site());
		if (entry == null || site == null || entry.phase() != RuinPhase.ACTIVE || !(site.newObjective() instanceof ChimeObjective objective)) {
			return null;
		}
		if (!(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block)) {
			return null;
		}
		Map<String, List<BlockPos>> markers = block.markers();
		int pillar = pillarAt(markers.getOrDefault(GrottoMarkers.PILLAR, List.of()), pos);
		if (pillar < 0) {
			return null;
		}
		objective.load(entry.objective());
		ChimeObjective.Outcome outcome = objective.strike(level, markers, pillar, level.getRandom());
		entry.setObjective(objective.save());
		if (outcome == ChimeObjective.Outcome.WRONG) {
			summonSentinels(level, entry, markers.getOrDefault(GrottoMarkers.SENTINEL, List.of()));
		}
		return outcome;
	}

	/** The index of the pillar whose shaft stands in the column of {@code pos}, or -1. */
	static int pillarAt(final List<BlockPos> feet, final BlockPos pos) {
		for (int i = 0; i < feet.size(); i++) {
			BlockPos foot = feet.get(i);
			if (foot.getX() == pos.getX() && foot.getZ() == pos.getZ() && pos.getY() >= foot.getY()) {
				return i;
			}
		}
		return -1;
	}

	/** The nearest core within reach of a shaft. A pillar stands within 6 blocks of its core. */
	static @Nullable BlockPos findCore(final ServerLevel level, final BlockPos pos) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-SCAN_XZ, -SCAN_DOWN, -SCAN_XZ), pos.offset(SCAN_XZ, 1, SCAN_XZ))) {
			if (level.getBlockState(probe).is(RuinBlocks.CORE)) {
				double distance = probe.distSqr(pos);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = probe.immutable();
				}
			}
		}
		return best;
	}

	private static void summonSentinels(final ServerLevel level, final RuinData.Entry entry, final List<BlockPos> spots) {
		List<BlockPos> pool = new ArrayList<>(spots);
		for (int i = 0; i < SENTINELS_PER_MISTAKE && !pool.isEmpty(); i++) {
			BlockPos spot = pool.remove(level.getRandom().nextInt(pool.size()));
			Mob mob = GrottoMobs.SHARD_SENTINEL.spawn(level, new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5));
			if (mob != null) {
				entry.addMob(mob.getUUID());
			}
		}
	}
}
