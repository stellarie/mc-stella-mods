package com.stella.ruins.site.deepforge;

import com.stella.ruins.world.Objective;
import com.stella.ruins.world.Progress;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinData;
import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.SiteRun;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The whole trial of the Deep Forge. Three steps run in order: set three Forge Cores on the core, pull three levers,
 * defeat the Forge Guardian. On top of the steps, every lava vent releases a Cinder Imp now and then while the trial
 * runs. The roster is spawned here, by marker, so a Forge Keeper stands at each core holder.
 */
public final class ForgeObjective implements Objective {
	public static final int CORES = 3;
	public static final int VENT_PERIOD_TICKS = 400;
	public static final int VENT_CAP = 4;
	/** The steps, as saved. */
	public static final int STEP_CORES = 0;
	public static final int STEP_LEVERS = 1;
	public static final int STEP_GUARDIAN = 2;

	private int step;
	private int used;
	private long ventLast;
	private final List<UUID> vented = new ArrayList<>();
	private @Nullable UUID guardianId;

	@Override
	public void start(final SiteRun run) {
		this.step = STEP_CORES;
		this.used = 0;
		this.guardianId = null;
		this.vented.clear();
		this.ventLast = RuinClock.now(run.level());
		for (BlockPos at : run.markers("holder")) {
			run.spawn(DeepForgeMobs.keeper, Vec3.atBottomCenterOf(at));
		}
		for (BlockPos at : run.markers("imp")) {
			run.spawn(DeepForgeMobs.imp, Vec3.atBottomCenterOf(at));
		}
		for (BlockPos at : run.markers("golem")) {
			run.spawn(DeepForgeMobs.golem, Vec3.atBottomCenterOf(at));
		}
	}

	@Override
	public Progress tick(final SiteRun run) {
		vents(run);
		if (this.step == STEP_LEVERS) {
			int pulled = 0;
			List<BlockPos> levers = run.markers("lever");
			for (BlockPos at : levers) {
				if (!run.level().isLoaded(at)) {
					continue;
				}
				BlockState state = run.level().getBlockState(at);
				if (state.getBlock() instanceof ForgeLeverBlock) {
					if (!state.getValue(ForgeLeverBlock.UNLOCKED)) {
						run.level().setBlock(at, state.setValue(ForgeLeverBlock.UNLOCKED, true), Block.UPDATE_ALL);
					}
					if (state.getValue(ForgeLeverBlock.POWERED)) {
						pulled++;
					}
				}
			}
			if (!levers.isEmpty() && pulled >= levers.size()) {
				summonGuardian(run);
			}
		} else if (this.step == STEP_GUARDIAN && guardianGone(run)) {
			finish(run);
			return Progress.COMPLETE;
		}
		return Progress.RUNNING;
	}

	@Override
	public boolean onUse(final SiteRun run, final ServerPlayer player, final ItemStack held) {
		if (this.step != STEP_CORES || !held.is(DeepForgeItems.FORGE_CORE)) {
			return false;
		}
		held.shrink(1);
		this.used++;
		if (this.used >= CORES) {
			this.step = STEP_LEVERS;
			unlockLevers(run);
			tell(run, "message.mod-stella-ruins.forge.levers_free", "The forge shudders. The levers move at last.");
		}
		return true;
	}

	@Override
	public void onKill(final SiteRun run, final LivingEntity mob) {
		this.vented.remove(mob.getUUID());
	}

	private void unlockLevers(final SiteRun run) {
		for (BlockPos at : run.markers("lever")) {
			if (run.level().isLoaded(at)) {
				BlockState state = run.level().getBlockState(at);
				if (state.getBlock() instanceof ForgeLeverBlock) {
					run.level().setBlock(at, state.setValue(ForgeLeverBlock.UNLOCKED, true), Block.UPDATE_ALL);
				}
			}
		}
	}

	private void summonGuardian(final SiteRun run) {
		List<BlockPos> spots = run.markers("guardian");
		if (spots.isEmpty()) {
			return;
		}
		Mob mob = run.spawn(DeepForgeMobs.guardian, Vec3.atBottomCenterOf(spots.get(0)));
		if (mob != null) {
			this.guardianId = mob.getUUID();
			this.step = STEP_GUARDIAN;
			tell(run, "message.mod-stella-ruins.forge.guardian", "The Forge Guardian wakes.");
		}
	}

	/** The guardian is gone from the roster: killed, or removed by the world. The roster drops dead mobs itself. */
	private boolean guardianGone(final SiteRun run) {
		if (this.guardianId == null) {
			return true;
		}
		RuinData.Entry entry = RuinData.get(run.level()).entry(run.core());
		return entry == null || !entry.roster().contains(this.guardianId);
	}

	/** The forge lights for good and the vault's bars open. */
	private void finish(final SiteRun run) {
		ServerLevel level = run.level();
		for (BlockPos at : run.markers("forge")) {
			BlockState state = level.getBlockState(at);
			if (state.getBlock() instanceof DeepForgeBlock) {
				level.setBlock(at, state.setValue(DeepForgeBlock.LIT, true), Block.UPDATE_ALL);
			}
		}
		for (BlockPos at : run.markers("vault_gate")) {
			if (level.getBlockState(at).is(Blocks.IRON_BARS)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		tell(run, "message.mod-stella-ruins.forge.lit_for_good", "The forge burns. The vault gate rises.");
	}

	private void vents(final SiteRun run) {
		long now = RuinClock.now(run.level());
		if (now - this.ventLast < VENT_PERIOD_TICKS) {
			return;
		}
		this.ventLast = now;
		RuinData.Entry entry = RuinData.get(run.level()).entry(run.core());
		if (entry != null) {
			this.vented.removeIf(id -> !entry.roster().contains(id));
		}
		for (BlockPos at : run.markers("imp_vent")) {
			if (this.vented.size() >= VENT_CAP) {
				break;
			}
			Mob mob = run.spawn(DeepForgeMobs.imp, Vec3.atBottomCenterOf(at));
			if (mob != null) {
				this.vented.add(mob.getUUID());
			}
		}
	}

	private static void tell(final SiteRun run, final String key, final String fallback) {
		for (ServerPlayer player : run.playersNear()) {
			player.sendOverlayMessage(Component.translatableWithFallback(key, fallback));
		}
	}

	/** The step the saved state is in, for tests. */
	public static int stepOf(final CompoundTag tag) {
		return tag.getIntOr("step", STEP_CORES);
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("step", this.step);
		tag.putInt("used", this.used);
		tag.putLong("vent_last", this.ventLast);
		StringBuilder joined = new StringBuilder();
		for (UUID id : this.vented) {
			joined.append(joined.length() == 0 ? "" : ",").append(id);
		}
		tag.putString("vented", joined.toString());
		tag.putString("guardian", this.guardianId == null ? "" : this.guardianId.toString());
		return tag;
	}

	@Override
	public void load(final CompoundTag tag) {
		this.step = tag.getIntOr("step", STEP_CORES);
		this.used = tag.getIntOr("used", 0);
		this.ventLast = tag.getLongOr("vent_last", 0L);
		this.vented.clear();
		for (String part : tag.getStringOr("vented", "").split(",")) {
			if (!part.isEmpty()) {
				this.vented.add(UUID.fromString(part));
			}
		}
		String guardian = tag.getStringOr("guardian", "");
		this.guardianId = guardian.isEmpty() ? null : UUID.fromString(guardian);
	}
}
