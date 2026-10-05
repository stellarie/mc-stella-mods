package com.stella.ruins.site.provinggrounds;

import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turns a player's use of the gong or of a tier lectern into the contest's moves. A Fabric event carries it: the
 * library adds no mixin. The core is found beside the block, and the block is matched against the layout markers.
 */
public final class GroundsUse {
	/** How far from the gong or a lectern the core can stand. */
	private static final int SCAN_XZ = 8;
	private static final int SCAN_Y = 4;

	private GroundsUse() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(GroundsUse::onUse);
	}

	private static InteractionResult onUse(final Player player, final Level level, final InteractionHand hand, final BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		boolean lectern = level.getBlockState(pos).is(Blocks.LECTERN);
		boolean bell = level.getBlockState(pos).is(Blocks.BELL);
		if ((!lectern && !bell) || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		TierObjective.Result result = use(server, pos, serverPlayer);
		if (result == null) {
			return InteractionResult.PASS;
		}
		// The bell still rings for the player, as a bell does. A lectern has nothing else to do.
		return bell ? InteractionResult.PASS : InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Plays one use of the block at {@code pos} against the core beside it. Returns null when the block belongs to no
	 * proving grounds core, so ordinary lecterns and bells are left alone.
	 */
	public static TierObjective.@Nullable Result use(final ServerLevel level, final BlockPos pos, final ServerPlayer player) {
		BlockPos corePos = findCore(level, pos);
		if (corePos == null || !(level.getBlockEntity(corePos) instanceof RuinCoreBlockEntity core)) {
			return null;
		}
		RuinSite site = core.frame() == null ? null : core.frame().siteOrNull();
		if (site == null || !(site.newObjective() instanceof TierObjective objective)) {
			return null;
		}
		var markers = core.markers();
		boolean gong = markers.getOrDefault(ArenaPlan.GONG, List.of()).contains(pos);
		int tier = 0;
		for (int t = 1; t <= TierObjective.TIERS; t++) {
			if (markers.getOrDefault(ArenaPlan.LECTERN + t, List.of()).contains(pos)) {
				tier = t;
			}
		}
		if (!gong && tier == 0) {
			return null;
		}
		RuinData.Entry entry = RuinData.get(level).entry(corePos);
		if (entry == null || entry.phase() != RuinPhase.ACTIVE) {
			say(player, "sleeping", "The grounds are still waking. Come closer.");
			return TierObjective.Result.SLEEPING;
		}
		TierObjective.Arena arena = TierObjective.Arena.of(level, core);
		objective.load(entry.objective());
		TierObjective.Result result = gong ? objective.ring(arena) : objective.pick(arena, player, tier);
		entry.setObjective(objective.save());
		switch (result) {
			case ARMED -> say(player, "armed", "The gong rings out. Pick a tier at a lectern.");
			case NOT_ARMED -> say(player, "not_armed", "Ring the gong first.");
			case CLAIMED -> say(player, "claimed", "Rest, champion. This tier paid out today.");
			case BUSY -> say(player, "busy", "A contest is under way.");
			case STARTED -> say(player, "started", "Tier " + tier + " begins. The crowd roars.");
			default -> {
			}
		}
		return result;
	}

	private static void say(final ServerPlayer player, final String key, final String fallback) {
		player.sendOverlayMessage(Component.translatableWithFallback("message.mod-stella-ruins.proving_grounds." + key, fallback));
	}

	private static @Nullable BlockPos findCore(final ServerLevel level, final BlockPos pos) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-SCAN_XZ, -SCAN_Y, -SCAN_XZ), pos.offset(SCAN_XZ, SCAN_Y, SCAN_XZ))) {
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
}
