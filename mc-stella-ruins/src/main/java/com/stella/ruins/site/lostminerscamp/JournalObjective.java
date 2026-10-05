package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Objectives;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinPhase;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.RuinSites;
import com.stella.ruins.world.SiteRun;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The camp's goal: clear the roster, then read the journal at
 * the lectern. A read counts only while the camp is quiet.
 */
public final class JournalObjective implements Objective {
	public static final String READ_KEY = "message.mod-stella-ruins.camp.journal_read";
	public static final String GUARDED_KEY = "message.mod-stella-ruins.camp.journal_guarded";
	/** How far from the lectern the core of the camp may stand. */
	private static final int SCAN_XZ = CampPlan.SIZE_X / 2;
	private static final int SCAN_Y = CampPlan.SIZE_Y / 2;

	/** What one use of a lectern did. */
	public enum Outcome {
		/** No camp core owns the block. */
		IGNORED,
		/** A roster mob still lives. */
		GUARDED,
		READ
	}

	/** The clear step the plan puts before the journal. */
	private final Objective clear = Objectives.clear();
	private boolean read;

	public static void register() {
		UseBlockCallback.EVENT.register(JournalObjective::onUseBlock);
	}

	private static InteractionResult onUseBlock(final Player player, final Level level, final InteractionHand hand, final BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (hand != InteractionHand.MAIN_HAND || !level.getBlockState(pos).is(Blocks.LECTERN)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		return read(server, pos, serverPlayer) == Outcome.IGNORED ? InteractionResult.PASS : InteractionResult.SUCCESS_SERVER;
	}

	/** Reads the journal of the camp whose lectern stands at {@code pos}. */
	public static Outcome read(final ServerLevel level, final BlockPos pos, final ServerPlayer player) {
		BlockPos core = findCore(level, pos);
		if (core == null || !(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block)) {
			return Outcome.IGNORED;
		}
		if (!block.markers().getOrDefault(CampMarkers.LECTERN, List.of()).contains(pos)) {
			return Outcome.IGNORED;
		}
		RuinData.Entry entry = RuinData.get(level).entry(core);
		RuinSite site = entry == null ? null : RuinSites.get(entry.site());
		if (site == null || entry.phase() != RuinPhase.ACTIVE || !(site.newObjective() instanceof JournalObjective objective)) {
			return Outcome.IGNORED;
		}
		objective.load(entry.objective());
		Outcome outcome = objective.journalRead(level, player, entry.roster().isEmpty());
		entry.setObjective(objective.save());
		return outcome;
	}

	/** The nearest camp core within reach of a lectern. */
	static BlockPos findCore(final ServerLevel level, final BlockPos pos) {
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

	/** One read. It counts only when no roster mob lives, and it hands over the compass. */
	Outcome journalRead(final ServerLevel level, final ServerPlayer player, final boolean quiet) {
		if (this.read) {
			return Outcome.READ;
		}
		if (!quiet) {
			player.sendOverlayMessage(Component.translatableWithFallback(GUARDED_KEY, "The miners still walk. The cache stays shut."));
			return Outcome.GUARDED;
		}
		this.read = true;
		give(level, player);
		player.sendOverlayMessage(Component.translatableWithFallback(READ_KEY, "The journal ends here. The cache opens."));
		StellaRuins.LOGGER.info("[RUINS] camp journal read by {} at {}", player.getName().getString(), player.blockPosition().toShortString());
		return Outcome.READ;
	}

	/** Hands the reader one compass, or drops it when the pack is full. */
	private static void give(final ServerLevel level, final ServerPlayer player) {
		ItemStack compass = new ItemStack(CampItems.MINERS_COMPASS);
		Inventory inventory = player.getInventory();
		if (!inventory.add(compass) && !compass.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), compass));
		}
	}

	@Override
	public void start(final SiteRun run) {
		this.clear.start(run);
		this.read = false;
		StellaRuins.LOGGER.info("[RUINS] camp core={} armed roster={}", run.core().toShortString(), run.alive());
	}

	@Override
	public Progress tick(final SiteRun run) {
		if (!this.read || this.clear.tick(run) != Progress.COMPLETE) {
			return Progress.RUNNING;
		}
		return Progress.COMPLETE;
	}

	public boolean hasRead() {
		return this.read;
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = this.clear.save();
		tag.putBoolean("read", this.read);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.clear.load(tag);
		this.read = tag.getBooleanOr("read", false);
	}
}
