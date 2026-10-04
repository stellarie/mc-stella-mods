package com.stella.ruins.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Items 4 to 7: waking, completing, the locked vault, and the claim and reset clock. */
public class RuinCoreGameTest {
	static final String LEVEL_SHARE = RuinLevels.CONTAINER_LEVEL_KEY;
	static final int TICKS = 6000;

	static int count(final RuinVaultBlockEntity vault, final Item item) {
		int total = 0;
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			if (vault.getItem(slot).is(item)) {
				total += vault.getItem(slot).getCount();
			}
		}
		return total;
	}

	static int stacks(final RuinVaultBlockEntity vault) {
		int total = 0;
		for (int slot = 0; slot < vault.getContainerSize(); slot++) {
			total += vault.getItem(slot).isEmpty() ? 0 : 1;
		}
		return total;
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerInsideTheRadiusWakesTheCoreOnItsOwnClockAndTheRosterSpawnsTaggedAndPersistent(final GameTestHelper helper) {
		WorldTestSupport.Arena arena = WorldTestSupport.arena(helper.getLevel(), WorldTestInit.surface, net.minecraft.world.level.block.Rotation.NONE);
		arena.join(arena.standAt(3, 3));
		helper.succeedWhen(() -> WorldTestSupport.guarded(helper, () -> {
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the core is still " + arena.phase());
			RuinData.Entry entry = arena.entry();
			helper.assertTrue(entry.roster().size() == WorldTestInit.surface.roster().size(), "the roster holds " + entry.roster().size());
			int tagged = 0;
			int persistent = 0;
			int found = 0;
			for (UUID id : entry.roster()) {
				Entity mob = arena.level().getEntity(id);
				if (mob instanceof Mob roster) {
					found++;
					tagged += roster.entityTags().contains(RuinMobKind.TAG_MOB) && roster.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + WorldTestInit.husk.id()) ? 1 : 0;
					persistent += roster.isPersistenceRequired() ? 1 : 0;
				}
			}
			helper.assertTrue(found == entry.roster().size(), "only " + found + " roster mobs are in the level yet");
			WorldTestSupport.result("coreWakes", "phase=" + arena.phase() + " roster=" + entry.roster().size() + " tagged=" + tagged + " persistent=" + persistent);
			helper.assertTrue(tagged == entry.roster().size(), "only " + tagged + " mobs carry both ruin tags");
			helper.assertTrue(persistent == entry.roster().size(), "only " + persistent + " mobs are persistent");
			arena.release();
		}));
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerOutsideTheRadiusOrInSpectatorModeDoesNotWakeTheCore(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			int radius = WorldTestInit.surface.activationRadius();
			ServerPlayer player = arena.join(arena.standAt(radius + 10, 0));
			arena.core().pulse(level);
			helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "a player " + (radius + 10) + " blocks away woke the core");
			Vec3 inside = arena.standAt(4, 4);
			player.setGameMode(GameType.SPECTATOR);
			player.teleportTo(level, inside.x, inside.y, inside.z, java.util.Set.of(), 0.0F, 0.0F, true);
			arena.core().pulse(level);
			helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "a spectator woke the core");
			player.setGameMode(GameType.SURVIVAL);
			arena.core().pulse(level);
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the same player in survival mode did not wake the core, so the checks above prove nothing");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void killingTheWholeRosterCompletesAClearObjectiveAndFillsTheVaultFromTheSiteTable(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			int expectedRolls = RuinConfig.get().vaultRolls(RuinLevels.at(level, Vec3.atCenterOf(arena.corePos())));
			ServerPlayer player = arena.wake();
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().size() == 3, "the run did not start with three mobs");
			helper.assertTrue(arena.vault().sealed(), "the vault was open before the objective");
			arena.freeze();
			int killed = arena.killRoster(player);
			RuinVaultBlockEntity vault = arena.vault();
			WorldTestSupport.result("clearCompletes", "killed=" + killed + " phase=" + arena.phase() + " sealed=" + vault.sealed() + " emeralds=" + count(vault, Items.EMERALD) + " stacks=" + stacks(vault)
				+ " rolls=" + expectedRolls + " claimTime=" + arena.entry().claimTime());
			helper.assertTrue(killed == 3, "killed " + killed + " mobs");
			helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the core is " + arena.phase() + " after the whole roster died");
			helper.assertTrue(!vault.sealed(), "the vault stayed sealed after completion");
			helper.assertTrue(count(vault, Items.EMERALD) == expectedRolls && stacks(vault) == expectedRolls, "the vault holds " + count(vault, Items.EMERALD) + " emeralds in " + stacks(vault) + " stacks, not " + expectedRolls + " from the site table");
			helper.assertTrue(arena.entry().roster().isEmpty(), "dead mobs stayed on the roster");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theCoreStaysActiveWhileOneRosterMobLives(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerPlayer player = arena.wake();
			arena.freeze();
			UUID last = arena.entry().roster().get(2);
			for (UUID id : arena.entry().roster()) {
				if (!id.equals(last) && arena.level().getEntity(id) instanceof Mob mob) {
					mob.hurtServer(arena.level(), arena.level().damageSources().playerAttack(player), 10_000.0F);
				}
			}
			arena.core().pulse(arena.level());
			helper.assertTrue(arena.phase() == RuinPhase.ACTIVE && arena.entry().roster().equals(List.of(last)), "the core is " + arena.phase() + " with roster " + arena.entry().roster());
			helper.assertTrue(arena.vault().sealed(), "the vault opened with a roster mob alive");
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theVaultRollsItsTableOnceForEveryTwentyFiveLevelsUpToTheCap(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, sixty -> {
			Object old = FabricLevelShare.put(LEVEL_SHARE, (BiFunction<ServerLevel, Vec3, Integer>) (level, at) -> 60);
			try {
				sixty.finish(helper);
				helper.assertTrue(sixty.phase() == RuinPhase.COMPLETE, "level 60: the core did not complete");
				int rolls = count(sixty.vault(), Items.EMERALD);
				helper.assertTrue(rolls == 1 + 60 / RuinConfig.get().vaultLevelsPerRoll(), "level 60 rolled " + rolls + " times");
				WorldTestSupport.result("vaultRollsByLevel", "level60=" + rolls + " cap=" + RuinConfig.get().vaultRollsMax());
			} finally {
				FabricLevelShare.restore(LEVEL_SHARE, old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aVaultRollsNoMoreThanTheCapAtAnyLevel(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			Object old = FabricLevelShare.put(LEVEL_SHARE, (BiFunction<ServerLevel, Vec3, Integer>) (level, at) -> 5000);
			try {
				arena.finish(helper);
				int capped = count(arena.vault(), Items.EMERALD);
				WorldTestSupport.result("vaultRollsCapped", "level5000=" + capped + " cap=" + RuinConfig.get().vaultRollsMax());
				helper.assertTrue(capped == RuinConfig.get().vaultRollsMax(), "level 5000 rolled " + capped + " times, not the cap");
			} finally {
				FabricLevelShare.restore(LEVEL_SHARE, old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aLockedVaultCannotBeOpenedOrBrokenByASurvivalPlayer(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt(2, 2));
			BlockPos pos = arena.vaultPos();
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(state.getValue(RuinVaultBlock.LOCKED), "the vault is not locked at the start");
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
			state.useWithoutItem(level, player, hit);
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a locked vault opened a menu");
			helper.assertTrue(state.getDestroySpeed(level, pos) < 0.0F, "a locked vault has a destroy speed of " + state.getDestroySpeed(level, pos));
			helper.assertTrue(state.getDestroyProgress(player, level, pos) == 0.0F, "a survival player makes progress on a locked vault");
			helper.assertTrue(state.getBlock().getExplosionResistance() >= 3_600_000.0F, "a locked vault does not resist explosions");
			RuinVaultBlockEntity vault = arena.vault();
			helper.assertTrue(!vault.canPlaceItem(0, new ItemStack(Items.DIRT)), "a hopper or a hand could put an item into a locked vault");
			vault.setItem(0, new ItemStack(Items.DIAMOND));
			helper.assertTrue(!vault.canTakeItem(vault, 0, vault.getItem(0)), "a hopper could take from a locked vault");
			vault.clearContent();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void anOpenVaultShowsTwentySevenSlotsAndStillCannotBeBroken(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.finish(helper);
			BlockPos pos = arena.vaultPos();
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(!state.getValue(RuinVaultBlock.LOCKED), "the vault is still locked");
			state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			helper.assertTrue(player.containerMenu != player.inventoryMenu && player.containerMenu.getType() == MenuType.GENERIC_9x3, "the open vault did not give a 9x3 menu");
			int vaultSlots = player.containerMenu.slots.size() - 36;
			helper.assertTrue(vaultSlots == RuinVaultBlockEntity.SIZE, "the menu shows " + vaultSlots + " vault slots");
			helper.assertTrue(state.getDestroySpeed(level, pos) < 0.0F, "an open vault can be broken");
			RuinVaultBlockEntity vault = arena.vault();
			helper.assertTrue(vault.canTakeItem(vault, 0, vault.getItem(0)), "a hopper could not take from the open vault");
			WorldTestSupport.result("openVault", "menu=" + player.containerMenu.getType() + " slots=" + vaultSlots);
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aOnceSiteStaysCompleteAfterTheResetDelay(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			AtomicLong clock = new AtomicLong(5_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.finish(helper);
				long claimed = arena.entry().claimTime();
				arena.leave(player);
				clock.addAndGet(1000 * Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				WorldTestSupport.result("onceStaysComplete", "claimTime=" + claimed + " later=" + (clock.get() - claimed) + " phase=" + arena.phase() + " sealed=" + arena.vault().sealed());
				helper.assertTrue(claimed == 5_000_000L, "the claim time is " + claimed + ", not the clock's " + 5_000_000L);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "a once site left COMPLETE after 1000 days: " + arena.phase());
				helper.assertTrue(!arena.vault().sealed(), "a once site sealed its vault again");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aResetSiteRearmsAfterItsDelayThroughTheClockAndKeepsItsVaultContents(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			long start = 7_000_000L;
			AtomicLong clock = new AtomicLong(start);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.finish(helper);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && arena.entry().claimTime() == start, "the reset site did not complete at the clock's tick");
				int before = count(arena.vault(), Items.EMERALD);
				arena.leave(player);
				clock.set(start + Claim.DAY_TICKS - 1);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE && !arena.vault().sealed(), "the site rearmed one tick early");
				clock.set(start + Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				WorldTestSupport.result("resetRearms", "phase=" + arena.phase() + " sealed=" + arena.vault().sealed() + " emeraldsBefore=" + before + " after=" + count(arena.vault(), Items.EMERALD));
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site is " + arena.phase() + " after its delay");
				helper.assertTrue(arena.vault().sealed(), "the vault stayed open after the reset");
				helper.assertTrue(count(arena.vault(), Items.EMERALD) == before && before > 0, "the vault lost its contents over the reset");
				arena.finish(helper);
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the rearmed site did not run again");
				helper.assertTrue(count(arena.vault(), Items.EMERALD) > before, "the second payout added nothing");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aResetSiteTakesItsDelayFromTheConfigThroughTheClock(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			long start = 9_000_000L;
			AtomicLong clock = new AtomicLong(start);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			RuinConfig.Values oldConfig = RuinConfig.replace(RuinConfig.get().withResetDays(Map.of("test_reset", 2)));
			try {
				ServerPlayer player = arena.finish(helper);
				arena.leave(player);
				clock.set(start + Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "the site rearmed after one day although the config says two");
				clock.set(start + 2 * Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site is " + arena.phase() + " after the two days the config names");
			} finally {
				RuinConfig.replace(oldConfig);
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aResetSiteWaitsInCooldownWhileAPlayerStillStandsInTheRadius(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.reset, arena -> {
			long start = 11_000_000L;
			AtomicLong clock = new AtomicLong(start);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.finish(helper);
				clock.set(start + Claim.DAY_TICKS);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COOLDOWN, "the site is " + arena.phase() + ", not COOLDOWN, with a player in the radius");
				helper.assertTrue(arena.vault().sealed(), "the vault stayed open in COOLDOWN");
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.COOLDOWN && arena.entry().roster().isEmpty(), "the site woke again under the player who just looted it");
				arena.leave(player);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.DORMANT, "the site is " + arena.phase() + " after the player left");
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aCoreRecoversItsFrameAndMarkersAfterItsBlockEntityReloads(final GameTestHelper helper) {
		WorldTestSupport.inArena(helper, WorldTestInit.surface, arena -> {
			RuinCoreBlockEntity core = arena.core();
			CompoundTag saved = core.saveWithFullMetadata(arena.level().registryAccess());
			BlockEntity reloaded = BlockEntity.loadStatic(arena.corePos(), arena.level().getBlockState(arena.corePos()), saved, arena.level().registryAccess());
			helper.assertTrue(reloaded instanceof RuinCoreBlockEntity, "the saved core did not load as a core");
			RuinCoreBlockEntity again = (RuinCoreBlockEntity) reloaded;
			helper.assertTrue(arena.frame().equals(again.frame()), "the frame was lost over a reload");
			helper.assertTrue(arena.marks().equals(again.markers()), "the reloaded core found other markers");
			helper.assertTrue(again.markers().get(RuinMarkers.SPAWN).size() == 4, "the reloaded core lost its spawn points");
		});
	}
}
