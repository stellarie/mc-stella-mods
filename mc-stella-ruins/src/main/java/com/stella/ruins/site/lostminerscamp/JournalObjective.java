package com.stella.ruins.site.lostminerscamp;

import com.stella.ruins.StellaRuins;
import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Objectives;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinBlocks;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinCoreBlockEntity;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinMobs;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

	/** Ticks between two miners that come while a lantern is dark. */
	public static final long DARK_SPAWN_TICKS = 400L;
	/** The camp sends no more help while this many of its mobs live. */
	public static final int DARK_CAP = 16;

	public static final String DARK_KEY = "message.mod-stella-ruins.camp.journal_dark";

	/** True for a lantern that burns. A lantern that is gone cannot be lit again, so it counts as lit. */
	static boolean burns(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.is(Blocks.CAMPFIRE) || state.getValue(CampfireBlock.LIT);
	}

	/** Lights a dark lantern of a camp with a torch. Returns true when it lit. */
	public static boolean light(final ServerLevel level, final BlockPos pos, final ServerPlayer player, final ItemStack held) {
		if (!held.is(Items.TORCH) || !level.getBlockState(pos).is(Blocks.CAMPFIRE) || level.getBlockState(pos).getValue(CampfireBlock.LIT)) {
			return false;
		}
		BlockPos core = findCore(level, pos);
		if (core == null || !(level.getBlockEntity(core) instanceof RuinCoreBlockEntity block) || !block.markers().getOrDefault(CampMarkers.LANTERN, List.of()).contains(pos)) {
			return false;
		}
		level.setBlock(pos, level.getBlockState(pos).setValue(CampfireBlock.LIT, true), 3);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		return true;
	}

	/** The clear step the plan puts before the journal. */
	private final Objective clear = Objectives.clear();
	private boolean read;
	/** When the next miner comes while a lantern is dark, or -1 while none is. */
	private long nextCall = -1L;
	private int calls;

	public static void register() {
		UseBlockCallback.EVENT.register(JournalObjective::onUseBlock);
	}

	private static InteractionResult onUseBlock(final Player player, final Level level, final InteractionHand hand, final BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (level.getBlockState(pos).is(Blocks.CAMPFIRE)) {
			return light(server, pos, serverPlayer, player.getMainHandItem()) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		if (!level.getBlockState(pos).is(Blocks.LECTERN)) {
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
		boolean lit = block.markers().getOrDefault(CampMarkers.LANTERN, List.of()).stream().allMatch(lantern -> burns(level, lantern));
		Outcome outcome = objective.journalRead(level, player, entry.roster().isEmpty(), lit);
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
	Outcome journalRead(final ServerLevel level, final ServerPlayer player, final boolean quiet, final boolean lit) {
		if (this.read) {
			return Outcome.READ;
		}
		if (!quiet) {
			player.sendOverlayMessage(Component.translatableWithFallback(GUARDED_KEY, "The miners still walk. The cache stays shut."));
			return Outcome.GUARDED;
		}
		if (!lit) {
			player.sendOverlayMessage(Component.translatableWithFallback(DARK_KEY, "The camp is dark. Light the three lanterns first."));
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

	/** While a lantern is dark, the camp sends a rat or a miner every {@link #DARK_SPAWN_TICKS} ticks, up to a cap. */
	private void callHelp(final SiteRun run) {
		ServerLevel level = run.level();
		List<BlockPos> dark = run.markers(CampMarkers.LANTERN).stream().filter(lantern -> !burns(level, lantern)).toList();
		if (this.read || dark.isEmpty()) {
			this.nextCall = -1L;
			return;
		}
		long now = RuinClock.now(level);
		if (this.nextCall < 0L) {
			this.nextCall = now + DARK_SPAWN_TICKS;
			return;
		}
		if (now < this.nextCall) {
			return;
		}
		this.nextCall = now + DARK_SPAWN_TICKS;
		if (run.alive() >= DARK_CAP) {
			return;
		}
		BlockPos at = dark.get(this.calls % dark.size());
		Mob mob = run.spawn(this.calls % 2 == 0 ? CampMobs.TUNNEL_RAT : CampMobs.RESTLESS_MINER, new Vec3(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5));
		this.calls++;
		if (mob != null) {
			RuinMobs.markSummoned(mob);
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		callHelp(run);
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
		tag.putLong("next_call", this.nextCall);
		tag.putInt("calls", this.calls);
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.clear.load(tag);
		this.read = tag.getBooleanOr("read", false);
		this.nextCall = tag.getLongOr("next_call", -1L);
		this.calls = tag.getIntOr("calls", 0);
	}
}
