package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The floor 6 seal, which needs the Sovereign and both knights dead, and what each of the three leaves behind. */
public class KnightSealGameTest {
	/** The cells of the barrier door that shuts the way to the rest area. */
	static List<BlockPos> doorCells(final ServerLevel end) {
		List<BlockPos> door = new ArrayList<>();
		int slab = EndCastle.slabY(EndCastle.FLOORS);
		for (CastleLayout.Door cells : EndCastles.layout(end).floor(EndCastle.FLOORS).doors()) {
			if (cells.barrier()) {
				for (int x = cells.x0(); x <= cells.x1(); x++) {
					for (int z = cells.z0(); z <= cells.z1(); z++) {
						for (int y = slab + 1; y <= slab + CastleLayout.DOOR_HEIGHT; y++) {
							door.add(EndTestSupport.world(EndCastle.min(), x, y, z));
						}
					}
				}
			}
		}
		return door;
	}

	private static long shut(final ServerLevel end, final List<BlockPos> door) {
		return door.stream().filter(pos -> end.getBlockState(pos).is(Blocks.BARRIER)).count();
	}

	/** The three bosses of the floor, by name, as one list a test can kill in any order. */
	record Trio(Blaze sovereign, WitherSkeleton chill, WitherSkeleton waveform) {
		LivingEntity get(final int index) {
			return switch (index) {
				case 0 -> this.sovereign;
				case 1 -> this.chill;
				default -> this.waveform;
			};
		}

		String name(final int index) {
			return switch (index) {
				case 0 -> "the Sovereign";
				case 1 -> "the chill knight";
				default -> "the waveform knight";
			};
		}
	}

	static Trio raise(final GameTestHelper helper, final ServerLevel end) {
		Blaze sovereign = UndeadSovereign.spawn(end);
		helper.assertTrue(sovereign != null, "the Sovereign was not raised");
		WitherSkeleton chill = null;
		WitherSkeleton waveform = null;
		for (WitherSkeleton knight : WitherKnights.alive(end)) {
			if (WitherKnights.kindOf(knight) == WitherKnights.Kind.CHILL) {
				chill = knight;
			} else {
				waveform = knight;
			}
		}
		helper.assertTrue(chill != null && waveform != null, "the Sovereign did not raise both knights");
		return new Trio(sovereign, chill, waveform);
	}

	@GameTest(environment = "mod-stella-end-test:knight_seal", maxTicks = 200)
	public void theExitStaysShutUntilTheSovereignAndBothKnightsAreDeadInAnyOrderOfDeath(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<BlockPos> door = doorCells(end);
		helper.assertTrue(!door.isEmpty(), "floor 6 has no barrier door");
		int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
		List<String> bad = new ArrayList<>();
		StringBuilder seen = new StringBuilder();
		for (int[] order : orders) {
			Trio trio = raise(helper, end);
			long before = shut(end, door);
			if (before != door.size()) {
				bad.add("order " + java.util.Arrays.toString(order) + ": the door was not shut when the bosses rose: " + before + " of " + door.size());
			}
			for (int step = 0; step < order.length; step++) {
				EndTestSupport.slay(end, trio.get(order[step]));
				long open = door.stream().filter(pos -> end.getBlockState(pos).isAir()).count();
				boolean last = step == order.length - 1;
				if (!last && shut(end, door) != door.size()) {
					bad.add("order " + java.util.Arrays.toString(order) + ": the door opened after " + trio.name(order[step]) + " with " + (order.length - 1 - step) + " bosses alive: " + open + " cells open");
				}
				if (last && open != door.size()) {
					bad.add("order " + java.util.Arrays.toString(order) + ": the door stayed shut after the last boss, " + open + " of " + door.size() + " cells are open");
				}
			}
			seen.append(' ').append(java.util.Arrays.toString(order)).append("=").append(door.stream().filter(pos -> end.getBlockState(pos).isAir()).count());
			EndTestSupport.fresh(helper);
		}
		EndTestSupport.result("knightSeal", "cells=" + door.size() + " openAfterLast=" + seen + " bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "the seal broke in these orders: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_seal_state", maxTicks = 40)
	public void theSavedStateKeepsWhichKnightsStandAsTheyDie(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Trio trio = raise(helper, end);
		int all = EndCastles.state(end).knights();
		EndTestSupport.slay(end, trio.chill());
		int afterChill = EndCastles.state(end).knights();
		EndTestSupport.slay(end, trio.waveform());
		int afterBoth = EndCastles.state(end).knights();
		EndTestSupport.result("knightSealState", "all=" + all + " afterChill=" + afterChill + " afterBoth=" + afterBoth);
		EndTestSupport.fresh(helper);
		helper.assertTrue(all == (WitherKnights.Kind.CHILL.bit() | WitherKnights.Kind.WAVEFORM.bit()), "the state holds bits " + all + " with both knights standing");
		helper.assertTrue(afterChill == WitherKnights.Kind.WAVEFORM.bit(), "the state holds bits " + afterChill + " after the chill knight died");
		helper.assertTrue(afterBoth == 0, "the state holds bits " + afterBoth + " after both died");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_respawn", maxTicks = 200)
	public void aKnightThatOutlivesTheSovereignStaysAndTheMissingKnightRisesWithTheNextSovereignAndTheExitShutsAgain(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		EndTestSupport.join(helper, end, Vec3.atBottomCenterOf(CastleLayout.sovereignTrigger().above()));
		Trio first = raise(helper, end);
		UUID waveformId = first.waveform().getUUID();
		List<BlockPos> door = doorCells(end);
		EndTestSupport.slay(end, first.sovereign());
		EndTestSupport.slay(end, first.chill());
		boolean[] result = new boolean[3];
		long[] diedAt = new long[1];
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				diedAt[0] = EndCastles.state(end).sovereignDeadAt();
				helper.assertTrue(diedAt[0] != EndState.NEVER, "the Sovereign's death was not stored");
				result[0] = shut(end, door) == door.size();
				EndClock.advance(end, UndeadSovereign.RESPAWN_TICKS - (EndClock.now(end) - diedAt[0]));
			})
			.thenWaitUntil(() -> helper.assertTrue(UndeadSovereign.current(end) != null, "the Sovereign has not risen again"))
			.thenIdle(3)
			.thenExecute(() -> {
				List<WitherSkeleton> standing = WitherKnights.alive(end);
				boolean oldOne = standing.stream().anyMatch(knight -> knight.getUUID().equals(waveformId));
				boolean newChill = standing.stream().anyMatch(knight -> WitherKnights.kindOf(knight) == WitherKnights.Kind.CHILL && !knight.getUUID().equals(first.chill().getUUID()));
				result[1] = oldOne && newChill && standing.size() == 2;
				result[2] = shut(end, door) == door.size();
				EndTestSupport.result("knightRespawn", "doorShutWhileOneKnightStood=" + result[0] + " standing=" + standing.size() + " oldKept=" + oldOne + " newChill=" + newChill
					+ " doorShutAfterRise=" + result[2]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(result[0], "the door opened while a knight still stood");
				helper.assertTrue(result[1], "after the next rising " + standing.size() + " knights stand; the old waveform knight kept " + oldOne + ", a new chill knight " + newChill);
				helper.assertTrue(result[2], "the door stayed open behind the new Sovereign");
			})
			.thenSucceed();
	}

	/** The items an entity left behind, as the stacks that joined the level when it died. */
	static List<ItemStack> leftBy(final ServerLevel end, final LivingEntity boss) {
		Set<UUID> before = WitheringArmsGameTest.itemIds(end);
		EndTestSupport.slay(end, boss);
		return WitheringArmsGameTest.droppedSince(end, before);
	}

	@GameTest(environment = "mod-stella-end-test:knight_cores", maxTicks = 40)
	public void theSovereignDropsOneDarkBlazeCoreAndEachKnightOneWitherCoreAndNoPartOfItsKit(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Trio trio = raise(helper, end);
		List<ItemStack> sovereign = leftBy(end, trio.sovereign());
		List<ItemStack> chill = leftBy(end, trio.chill());
		List<ItemStack> waveform = leftBy(end, trio.waveform());
		long darkCores = sovereign.stream().filter(stack -> stack.is(EndItems.darkBlazeCore())).mapToInt(ItemStack::getCount).sum();
		long chillCores = chill.stream().filter(stack -> stack.is(EndItems.witherCore())).mapToInt(ItemStack::getCount).sum();
		long waveformCores = waveform.stream().filter(stack -> stack.is(EndItems.witherCore())).mapToInt(ItemStack::getCount).sum();
		long witherInSovereign = sovereign.stream().filter(stack -> stack.is(EndItems.witherCore())).count();
		long darkInKnights = java.util.stream.Stream.concat(chill.stream(), waveform.stream()).filter(stack -> stack.is(EndItems.darkBlazeCore())).count();
		List<ItemStack> kit = new ArrayList<>();
		for (List<ItemStack> dropped : List.of(chill, waveform)) {
			for (ItemStack stack : dropped) {
				String id = stack.getItem().toString();
				if (id.contains("netherite") || WitheringArms.is(stack)) {
					kit.add(stack);
				}
			}
		}
		EndTestSupport.result("knightCores", "darkCores=" + darkCores + " chillCores=" + chillCores + " waveformCores=" + waveformCores + " strayCores=" + (witherInSovereign + darkInKnights)
			+ " kitDropped=" + kit.size());
		EndTestSupport.fresh(helper);
		helper.assertTrue(darkCores == 1, "the Sovereign dropped " + darkCores + " dark blaze cores: " + sovereign);
		helper.assertTrue(chillCores == 1 && waveformCores == 1, "the knights dropped " + chillCores + " and " + waveformCores + " wither cores");
		helper.assertTrue(witherInSovereign == 0 && darkInKnights == 0, "a core fell from the wrong boss");
		helper.assertTrue(kit.isEmpty(), "a knight dropped its kit: " + kit);
		helper.succeed();
	}
}
