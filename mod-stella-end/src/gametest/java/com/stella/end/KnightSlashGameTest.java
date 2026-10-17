package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** The charged slash of a Wither Knight: a wind-up of forty ticks, a throw back, and soul fire on a line sixteen blocks long. */
public class KnightSlashGameTest {
	private static final int FLOOR_Y = EndCastle.standY(EndCastle.FLOORS);
	/** The knight stands here in every slash test; its line runs east from this column. */
	private static final int KNIGHT_X = -8;

	/** Whether a cell is clear for a standing creature of the knight's height and has a whole block under it. */
	private static boolean open(final ServerLevel end, final int x, final int z, final int height) {
		BlockPos under = new BlockPos(x, FLOOR_Y - 1, z);
		if (!end.getBlockState(under).isFaceSturdy(end, under, net.minecraft.core.Direction.UP)) {
			return false;
		}
		for (int dy = 0; dy < height; dy++) {
			if (!end.getBlockState(new BlockPos(x, FLOOR_Y + dy, z)).isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A row of the arena that is open for a knight from one column to another, and open at each extra cell, given as a
	 * column and an offset from the row. The nave holds pillars at its edges and pews to the south, so the test names
	 * what it needs instead of asking for a wide clear field.
	 */
	static int clearRow(final ServerLevel end, final int fromX, final int toX, final int[]... extras) {
		for (int z = -5; z <= 5; z++) {
			boolean clear = true;
			for (int x = fromX; x <= toX && clear; x++) {
				clear = open(end, x, z, 5);
			}
			for (int[] extra : extras) {
				clear = clear && open(end, extra[0], z + extra[1], 2);
			}
			if (clear) {
				return z;
			}
		}
		throw new AssertionError("the arena holds no clear row from x " + fromX + " to " + toX);
	}

	private static int flames(final ServerLevel end, final int x0, final int x1, final int z0, final int z1) {
		int count = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				count += end.getBlockState(new BlockPos(x, FLOOR_Y, z)).is(EndBlocks.soulFlame()) ? 1 : 0;
			}
		}
		return count;
	}

	/** A knight standing at the start of the row, with its twin gone so only one creature swings. */
	private static WitherSkeleton lone(final GameTestHelper helper, final ServerLevel end, final int row) {
		List<WitherSkeleton> knights = WitherKnights.raise(end);
		helper.assertTrue(knights.size() == 2, "raised " + knights.size() + " knights, not two");
		knights.get(1).discard();
		WitherSkeleton knight = knights.get(0);
		knight.teleportTo(KNIGHT_X + 0.5, FLOOR_Y, row + 0.5);
		return knight;
	}

	@GameTest(environment = "mod-stella-end-test:knight_slash", maxTicks = 260)
	public void aKnightWindsUpFortyTicksThenThrowsBackThePlayerOnItsLineAndLightsSoulFireOnSixteenCellsAhead(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int row = clearRow(end, KNIGHT_X, KNIGHT_X + KnightSlashGoal.LINE_LENGTH, new int[] {KNIGHT_X + 7, 3}, new int[] {KNIGHT_X + 12, 2});
		ServerPlayer target = EndTestSupport.join(helper, end, new Vec3(KNIGHT_X + 7.5, FLOOR_Y, row + 0.5));
		ServerPlayer bystander = EndTestSupport.join(helper, end, new Vec3(KNIGHT_X + 7.5, FLOOR_Y, row + 3.5));
		Zombie online = EndTestSupport.dummy(end, new Vec3(KNIGHT_X + 12.5, FLOOR_Y, row + 0.5));
		Zombie beside = EndTestSupport.dummy(end, new Vec3(KNIGHT_X + 12.5, FLOOR_Y, row + 2.5));
		WitherSkeleton knight = lone(helper, end, row);
		KnightSlashGoal slash = CastleTeleportGameTest.goal(knight, KnightSlashGoal.class);
		List<String> early = new ArrayList<>();
		Vec3[] pushed = new Vec3[2];
		float[] online0 = {online.getHealth()};
		helper.onEachTick(() -> {
			knight.setTarget(target);
			if (slash.slashes().isEmpty()) {
				int lit = flames(end, KNIGHT_X - 1, KNIGHT_X + 18, row - 2, row + 2);
				if (lit > 0) {
					early.add("tick " + knight.tickCount + " holds " + lit + " flames before the slash fell");
				}
			} else if (pushed[0] == null) {
				pushed[0] = target.getDeltaMovement();
				pushed[1] = bystander.getDeltaMovement();
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!slash.slashes().isEmpty(), "no slash yet; distance " + knight.distanceTo(target) + " at " + knight.position()))
			.thenIdle(6)
			.thenExecute(() -> {
				KnightSlashGoal.Slash made = slash.slashes().get(0);
				int windup = made.released() - made.began();
				List<String> bad = new ArrayList<>();
				for (int i = 0; i < made.lit().size(); i++) {
					BlockPos cell = made.lit().get(i);
					if (cell.getX() != KNIGHT_X + 1 + i || cell.getZ() != row || cell.getY() != FLOOR_Y) {
						bad.add("cell " + i + " is " + cell);
					}
					if (!end.getBlockState(cell).is(EndBlocks.soulFlame())) {
						bad.add("cell " + cell + " holds " + end.getBlockState(cell));
					}
				}
				int beside2 = flames(end, KNIGHT_X, KNIGHT_X + 18, row - 3, row - 1) + flames(end, KNIGHT_X, KNIGHT_X + 18, row + 1, row + 3);
				int onLine = flames(end, KNIGHT_X, KNIGHT_X + 18, row, row);
				List<UUID> struck = made.struck();
				boolean hitTarget = struck.contains(target.getUUID());
				boolean hitDummy = struck.contains(online.getUUID());
				boolean hitOthers = struck.contains(bystander.getUUID()) || struck.contains(beside.getUUID()) || struck.contains(knight.getUUID());
				double along = pushed[0] == null ? -99.0 : pushed[0].x;
				double bystanderAlong = pushed[1] == null ? -99.0 : Math.abs(pushed[1].x) + Math.abs(pushed[1].z);
				EndTestSupport.result("knightSlash", "windup=" + windup + " lit=" + made.lit().size() + " onLine=" + onLine + " besideLine=" + beside2 + " early=" + early.size()
					+ " struck=" + struck.size() + " pushX=" + along + " bystanderPush=" + bystanderAlong + " dummyHealth=" + online.getHealth() + " dummyBurning=" + online.isOnFire()
					+ " aim=" + made.aim());
				helper.assertTrue(KnightSlashGoal.WINDUP == 40 && KnightSlashGoal.LINE_LENGTH == 16, "a slash constant departs from the spec");
				helper.assertTrue(windup == KnightSlashGoal.WINDUP, "the slash fell " + windup + " ticks after it began");
				helper.assertTrue(early.isEmpty(), "fire stood before the wind-up ended: " + early.stream().limit(3).toList());
				helper.assertTrue(made.lit().size() == KnightSlashGoal.LINE_LENGTH && bad.isEmpty(), "the line is not sixteen soul flames along the row: " + bad);
				helper.assertTrue(onLine == KnightSlashGoal.LINE_LENGTH && beside2 == 0, "flames on the row " + onLine + ", flames beside it " + beside2);
				helper.assertTrue(hitTarget && hitDummy && !hitOthers, "the slash struck " + struck.size() + " creatures; target " + hitTarget + ", dummy " + hitDummy + ", others " + hitOthers);
				helper.assertTrue(along > 0.5, "the player was thrown back only " + along + " along the line");
				helper.assertTrue(bystanderAlong < 0.3, "the player off the line was moved " + bystanderAlong);
				helper.assertTrue(online.getHealth() < online0[0] && online.isOnFire(), "the mob on the line took no damage or did not burn: " + online.getHealth() + " of " + online0[0]);
				EndTestSupport.fresh(helper);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_slash_cooldown", maxTicks = 360)
	public void aKnightSlashesAgainOnlyOnceTheCooldownHasRunOnTheEndClock(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int row = clearRow(end, KNIGHT_X, KNIGHT_X + 8);
		ServerPlayer target = EndTestSupport.join(helper, end, new Vec3(KNIGHT_X + 7.5, FLOOR_Y, row + 0.5));
		WitherSkeleton knight = lone(helper, end, row);
		KnightSlashGoal slash = CastleTeleportGameTest.goal(knight, KnightSlashGoal.class);
		helper.onEachTick(() -> {
			knight.setTarget(target);
			if (knight.distanceTo(target) > 7.5) {
				knight.teleportTo(KNIGHT_X + 0.5, FLOOR_Y, row + 0.5);
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(slash.slashes().size() == 1, "no first slash yet"))
			.thenIdle(120)
			.thenExecute(() -> {
				helper.assertTrue(slash.slashes().size() == 1, "the knight slashed again " + slash.slashes().size() + " slashes in, inside its cooldown");
				EndClock.advance(end, KnightSlashGoal.COOLDOWN);
			})
			.thenWaitUntil(() -> helper.assertTrue(slash.slashes().size() >= 2, "no second slash after the cooldown"))
			.thenExecute(() -> {
				long gap = slash.slashes().get(1).clock() - slash.slashes().get(0).clock();
				EndTestSupport.result("knightSlashCooldown", "slashes=" + slash.slashes().size() + " gap=" + gap + " cooldown=" + KnightSlashGoal.COOLDOWN);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gap >= KnightSlashGoal.COOLDOWN, "the second slash came " + gap + " ticks after the first");
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_slash_wall", maxTicks = 200)
	public void aSlashLightsNoFireBeyondAWallInItsPath(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int startX = -4;
		int row = clearRow(end, -11, startX);
		// The line runs west from the knight, and the west wall of the hall stands inside its sixteen cells.
		int open = 0;
		while (open < KnightSlashGoal.LINE_LENGTH && open(end, startX - 1 - open, row, 2)) {
			open++;
		}
		final int expected = open;
		final int wallX = startX - 1 - expected;
		ServerPlayer target = EndTestSupport.join(helper, end, new Vec3(startX - 5.5, FLOOR_Y, row + 0.5));
		WitherSkeleton knight = lone(helper, end, row);
		knight.teleportTo(startX + 0.5, FLOOR_Y, row + 0.5);
		KnightSlashGoal slash = CastleTeleportGameTest.goal(knight, KnightSlashGoal.class);
		helper.onEachTick(() -> knight.setTarget(target));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!slash.slashes().isEmpty(), "no slash yet"))
			.thenExecute(() -> {
				KnightSlashGoal.Slash made = slash.slashes().get(0);
				int past = 0;
				for (BlockPos cell : made.lit()) {
					past += cell.getX() <= wallX ? 1 : 0;
				}
				EndTestSupport.result("knightSlashWall", "wallX=" + wallX + " expectedCells=" + expected + " lit=" + made.lit().size() + " pastTheWall=" + past);
				EndTestSupport.fresh(helper);
				helper.assertTrue(expected > 0 && expected < KnightSlashGoal.LINE_LENGTH, "the test row holds " + expected + " open cells, so it does not meet a wall");
				helper.assertTrue(past == 0, past + " flames stand past the wall at x " + wallX);
				helper.assertTrue(made.lit().size() == expected, "the line holds " + made.lit().size() + " flames, wanted " + expected + " before the wall");
			})
			.thenSucceed();
	}
}
