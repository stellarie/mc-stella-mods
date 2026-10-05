package com.stella.ruins.site.sealedvault;

import com.stella.ruins.site.sealedvault.VaultPlan.Trap;
import com.stella.ruins.site.sealedvault.VaultPlan.TrapKind;
import com.stella.ruins.site.sealedvault.VaultTestSupport.Arena;
import com.stella.ruins.world.RuinClock;
import com.stella.ruins.world.RuinPhase;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Items 4 and 8, and the wall rule: seals, traps, and walls that open only when the vault is complete. */
public class VaultBlocksGameTest {
	static final long TRAP_SEED = VaultTestSupport.seedWith(plan -> plan.traps().stream().anyMatch(trap -> trap.kind() == TrapKind.ARROW && trap.level() == 1));
	static final long DROP_SEED = VaultTestSupport.seedWith(plan -> plan.traps().stream().anyMatch(trap -> trap.kind() == TrapKind.DROP));
	static final long WALL_SEED = 0x7A11L;

	private static Trap trapOf(final VaultPlan plan, final TrapKind kind) {
		return plan.traps().stream().filter(trap -> trap.kind() == kind && trap.level() == 1).findFirst().orElseThrow();
	}

	private static BlockPos plateOf(final Arena arena, final Trap trap) {
		return arena.local(VaultPlan.cellMinX(trap.at().i()) + 3, VaultPlan.stand(trap.level()), VaultPlan.cellMinZ(trap.at().j()) + 3);
	}

	private static void step(final ServerLevel level, final BlockPos plate, final ServerPlayer player) {
		level.getBlockState(plate).entityInside(level, plate, player, InsideBlockEffectApplier.NOOP, true);
	}

	private static int armedPlates(final Arena arena, final Trap trap) {
		int ax = VaultPlan.cellMinX(trap.at().i());
		int az = VaultPlan.cellMinZ(trap.at().j());
		int armed = 0;
		for (int u = 0; u <= 5; u++) {
			for (int v = 0; v <= 5; v++) {
				BlockState state = arena.level().getBlockState(arena.local(ax + u, VaultPlan.stand(trap.level()), az + v));
				if (state.getBlock() instanceof VaultBlocks.PlateBlock && state.getValue(VaultBlocks.PlateBlock.ARMED)) {
					armed++;
				}
			}
		}
		return armed;
	}

	private static int openCovers(final Arena arena, final Trap trap) {
		int ax = VaultPlan.cellMinX(trap.at().i());
		int az = VaultPlan.cellMinZ(trap.at().j());
		int open = 0;
		for (int u = 0; u <= 5; u++) {
			for (int v = 0; v <= 5; v++) {
				BlockState state = arena.level().getBlockState(arena.local(ax + u, VaultLayout.SLAB, az + v));
				if (state.is(VaultBlocks.PIT_COVER) && state.getValue(VaultBlocks.PitCoverBlock.OPEN)) {
					open++;
				}
			}
		}
		return open;
	}

	@GameTest(maxTicks = 6000)
	public void aSealBreaksIntoOneSealItem(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, WALL_SEED, arena -> {
			ServerPlayer player = arena.join(arena.standNearCore());
			BlockPos seal = arena.markers(VaultMarkers.SEAL).get(0);
			helper.assertTrue(arena.level().getBlockState(seal).is(VaultBlocks.SEAL), "no seal block at the first seal marker");
			arena.level().destroyBlock(seal, true, player);
			AABB area = new AABB(seal).inflate(2.0);
			List<ItemEntity> drops = arena.level().getEntitiesOfClass(ItemEntity.class, area);
			int count = drops.stream().filter(drop -> drop.getItem().is(VaultItems.VAULT_SEAL)).mapToInt(drop -> drop.getItem().getCount()).sum();
			VaultTestSupport.result("sealDrop", "items=" + drops.size() + " seals=" + count + " blockNow=" + arena.level().getBlockState(seal).getBlock());
			helper.assertTrue(count == 1 && drops.size() == 1, "the seal dropped " + count + " seals in " + drops.size() + " stacks");
			helper.assertTrue(arena.level().getBlockState(seal).isAir(), "the seal block is still there");
			drops.forEach(ItemEntity::discard);
		});
	}

	@GameTest(maxTicks = 6000)
	public void explosionsLeaveTheSealsAndTheWallsStanding(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, WALL_SEED, arena -> {
			BlockPos seal = arena.markers(VaultMarkers.SEAL).get(0);
			BlockPos wall = arena.probeWall();
			BlockPos floor = arena.probeFloor();
			helper.assertTrue(arena.level().getBlockState(wall).is(VaultBlocks.WALL) && arena.level().getBlockState(floor).is(VaultBlocks.FLOOR), "the probe spots are not vault blocks");
			Vec3 at = Vec3.atCenterOf(seal);
			arena.level().explode(null, at.x, at.y, at.z, 8.0F, Level.ExplosionInteraction.TNT);
			Vec3 near = Vec3.atCenterOf(wall);
			arena.level().explode(null, near.x, near.y, near.z, 8.0F, Level.ExplosionInteraction.TNT);
			Vec3 low = Vec3.atCenterOf(floor);
			arena.level().explode(null, low.x, low.y + 1, low.z, 8.0F, Level.ExplosionInteraction.TNT);
			VaultTestSupport.result("explosions", "seal=" + arena.level().getBlockState(seal).getBlock() + " wall=" + arena.level().getBlockState(wall).getBlock() + " floor=" + arena.level().getBlockState(floor).getBlock());
			helper.assertTrue(arena.level().getBlockState(seal).is(VaultBlocks.SEAL), "an explosion broke a seal");
			helper.assertTrue(arena.level().getBlockState(wall).is(VaultBlocks.WALL), "an explosion broke a wall");
			helper.assertTrue(arena.level().getBlockState(floor).is(VaultBlocks.FLOOR), "an explosion broke a floor");
		});
	}

	@GameTest(maxTicks = 6000)
	public void aWallCannotBeMinedUntilTheVaultIsCompleteAndThenMinesLikeDeepslate(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, WALL_SEED, arena -> {
			AtomicLong clock = new AtomicLong(3_000_000L);
			RuinClock.Source old = RuinClock.replace(level -> clock.get());
			try {
				ServerPlayer player = arena.join(arena.standNearCore());
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
				BlockPos wall = arena.probeWall();
				BlockPos floor = arena.probeFloor();
				float deepslate = Blocks.DEEPSLATE.defaultBlockState().getDestroyProgress(player, arena.level(), wall);
				helper.assertTrue(deepslate > 0.0F, "the reference progress is " + deepslate);
				float before = arena.level().getBlockState(wall).getDestroyProgress(player, arena.level(), wall);
				helper.assertTrue(before == 0.0F, "a wall can be mined before the vault woke: " + before);
				arena.core().pulse(arena.level());
				helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the vault did not wake");
				float active = arena.level().getBlockState(wall).getDestroyProgress(player, arena.level(), wall);
				float floorActive = arena.level().getBlockState(floor).getDestroyProgress(player, arena.level(), floor);
				helper.assertTrue(active == 0.0F && floorActive == 0.0F, "a wall or floor can be mined while the vault is active: " + active + " " + floorActive);
				arena.freeze();
				for (int i = 0; i < VaultPlan.SEALS; i++) {
					helper.assertTrue(arena.phase() == RuinPhase.ACTIVE, "the vault completed after " + i + " seals");
					arena.useCore(player, new ItemStack(VaultItems.VAULT_SEAL));
				}
				helper.assertTrue(arena.phase() == RuinPhase.COMPLETE, "three seals did not complete the vault: " + arena.phase());
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
				float after = arena.level().getBlockState(wall).getDestroyProgress(player, arena.level(), wall);
				float floorAfter = arena.level().getBlockState(floor).getDestroyProgress(player, arena.level(), floor);
				VaultTestSupport.result("wallGate", "before=" + before + " active=" + active + " after=" + after + " deepslate=" + deepslate);
				helper.assertTrue(Math.abs(after - deepslate) < 1.0E-6F && Math.abs(floorAfter - deepslate) < 1.0E-6F, "the wall progress " + after + " is not deepslate " + deepslate);
			} finally {
				RuinClock.replace(old);
			}
		});
	}

	@GameTest(maxTicks = 6000)
	public void anArrowPlateFiresEveryVentOnceAndGoesDarkForTheWholeRow(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, TRAP_SEED, arena -> {
			Trap trap = trapOf(arena.plan(), TrapKind.ARROW);
			BlockPos plate = plateOf(arena, trap);
			ServerPlayer player = arena.join(Vec3.atBottomCenterOf(plate));
			helper.assertTrue(armedPlates(arena, trap) == 6, "the row has " + armedPlates(arena, trap) + " armed plates");
			int before = VaultTraps.arrowsFired();
			step(arena.level(), plate, player);
			int fired = VaultTraps.arrowsFired() - before;
			AABB area = new AABB(plate).inflate(10.0, 4.0, 10.0);
			int arrows = arena.level().getEntitiesOfClass(Arrow.class, area, arrow -> arrow.entityTags().contains("mod-stella-ruins.vault_arrow")).size();
			VaultTestSupport.result("arrowPlate", "fired=" + fired + " arrowsInWorld=" + arrows + " armedAfter=" + armedPlates(arena, trap));
			helper.assertTrue(fired == 4 && arrows == 4, "the plate fired " + fired + " arrows and the world holds " + arrows);
			helper.assertTrue(armedPlates(arena, trap) == 0, "the row stayed armed after the shot");
			step(arena.level(), plate, player);
			helper.assertTrue(VaultTraps.arrowsFired() - before == 4, "a dark plate fired again");
			VaultTraps.reset(arena.level(), plate);
			helper.assertTrue(armedPlates(arena, trap) == 6, "the row did not rearm");
			step(arena.level(), plate, player);
			helper.assertTrue(VaultTraps.arrowsFired() - before == 8, "a rearmed plate did not fire");
		});
	}

	@GameTest(maxTicks = 6000)
	public void aDropPlateOpensThePitAndTheCoversCloseAgain(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, DROP_SEED, arena -> {
			Trap trap = trapOf(arena.plan(), TrapKind.DROP);
			BlockPos plate = plateOf(arena, trap);
			ServerPlayer player = arena.join(Vec3.atBottomCenterOf(plate));
			BlockPos cover = plate.below();
			helper.assertTrue(arena.level().getBlockState(cover).is(VaultBlocks.PIT_COVER) && openCovers(arena, trap) == 0, "the pit is not closed to begin with");
			helper.assertTrue(!arena.level().getBlockState(cover).getCollisionShape(arena.level(), cover).isEmpty(), "a closed cover has no collision");
			step(arena.level(), plate, player);
			VaultTestSupport.result("dropPlate", "openCovers=" + openCovers(arena, trap) + " armedAfter=" + armedPlates(arena, trap));
			helper.assertTrue(openCovers(arena, trap) == 18, "the pit opened " + openCovers(arena, trap) + " of 18 covers");
			helper.assertTrue(arena.level().getBlockState(cover).getCollisionShape(arena.level(), cover).isEmpty(), "an open cover still has collision");
			helper.assertTrue(armedPlates(arena, trap) == 0, "the row stayed armed");
			VaultTraps.reset(arena.level(), plate);
			helper.assertTrue(openCovers(arena, trap) == 0 && armedPlates(arena, trap) == 6, "the pit did not close and rearm");
		});
	}

	@GameTest(maxTicks = 6000)
	public void whatFallsThroughAnOpenPitLandsThreeBlocksDownOnTheDais(final GameTestHelper helper) {
		VaultTestSupport.inArena(helper, DROP_SEED, arena -> {
			Trap trap = trapOf(arena.plan(), TrapKind.DROP);
			BlockPos plate = plateOf(arena, trap);
			ServerPlayer player = arena.join(Vec3.atBottomCenterOf(plate));
			step(arena.level(), plate, player);
			ItemEntity stick = new ItemEntity(arena.level(), plate.getX() + 0.5, plate.getY() + 0.1, plate.getZ() + 0.5, new ItemStack(Items.STICK));
			stick.setNoPickUpDelay();
			// A new item entity gets a random sideways push, which can carry it out of the pit.
			stick.setDeltaMovement(Vec3.ZERO);
			arena.level().addFreshEntity(stick);
			double top = arena.frame().min().getY() + 6;
			double start = stick.getY();
			// The test clock stands still, so the item falls by ticking it by hand.
			for (int i = 0; i < 40; i++) {
				stick.tick();
			}
			VaultTestSupport.result("dropFall", "start=" + start + " landed=" + stick.getY() + " daisTop=" + top + " fall=" + (start - stick.getY()));
			StringBuilder column = new StringBuilder();
			for (int y = 8; y >= 2; y--) {
				column.append(" y").append(y).append('=').append(arena.level().getBlockState(BlockPos.containing(stick.getX(), arena.frame().min().getY() + y, stick.getZ())).getBlock());
			}
			helper.assertTrue(Math.abs(stick.getY() - top) < 0.2, "the item stopped at " + stick.getY() + " at " + stick.position() + ", the dais top is " + top + ", plate " + plate + ", column" + column);
			helper.assertTrue(start - stick.getY() <= 3.3 && start - stick.getY() >= 2.7, "the fall was " + (start - stick.getY()) + " blocks, not 3");
			stick.discard();
		});
	}

	@GameTest(maxTicks = 400)
	@SuppressWarnings("unchecked")
	public void stoppingTheServerCancelsPendingPlateResets(final GameTestHelper helper) throws ReflectiveOperationException {
		ServerLevel level = helper.getLevel();
		BlockPos plate = helper.absolutePos(new BlockPos(1, 2, 1));
		java.lang.reflect.Field pendingField = VaultTraps.class.getDeclaredField("PENDING");
		java.lang.reflect.Field ticksField = VaultTraps.class.getDeclaredField("ticks");
		java.lang.reflect.Method tick = VaultTraps.class.getDeclaredMethod("tick");
		pendingField.setAccessible(true);
		ticksField.setAccessible(true);
		tick.setAccessible(true);
		List<Object> pending = (List<Object>) pendingField.get(null);
		List<Object> previous = List.copyOf(pending);
		long oldTicks = ticksField.getLong(null);
		pending.clear();
		try {
			level.setBlock(plate, VaultBlocks.DROP_PLATE.defaultBlockState(), 3);
			VaultTraps.fire(level, plate, level.getBlockState(plate));
			helper.assertTrue(!level.getBlockState(plate).getValue(VaultBlocks.PlateBlock.ARMED), "the control plate never fired");
			for (int i = 0; i <= VaultTraps.RESET_TICKS; i++) {
				tick.invoke(null);
			}
			helper.assertTrue(level.getBlockState(plate).getValue(VaultBlocks.PlateBlock.ARMED), "the live control reset never ran");
			VaultTraps.fire(level, plate, level.getBlockState(plate));
			VaultTraps.onServerStopping(level.getServer());
			for (int i = 0; i <= VaultTraps.RESET_TICKS; i++) {
				tick.invoke(null);
			}
			boolean armed = level.getBlockState(plate).getValue(VaultBlocks.PlateBlock.ARMED);
			VaultTestSupport.result("trapStop", "rearmedAfterStop=" + armed);
			helper.assertTrue(!armed, "the stopped server's pending reset changed its old plate");
		} finally {
			pending.clear();
			pending.addAll(previous);
			ticksField.setLong(null, oldTicks);
			level.setBlock(plate, Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void aFiredPlateRearmsOnItsOwnClockAfterOneHundredTicks(final GameTestHelper helper) {
		Arena arena = VaultTestSupport.arena(helper.getLevel(), Rotation.NONE, DROP_SEED);
		Trap trap = trapOf(arena.plan(), TrapKind.DROP);
		BlockPos plate = plateOf(arena, trap);
		ServerPlayer player = arena.join(Vec3.atBottomCenterOf(plate));
		step(arena.level(), plate, player);
		helper.assertTrue(openCovers(arena, trap) == 18 && armedPlates(arena, trap) == 0, "the trap did not fire");
		helper.startSequence()
			.thenIdle(VaultTraps.RESET_TICKS / 2)
			.thenExecute(() -> helper.assertTrue(openCovers(arena, trap) == 18, "the pit closed after only half the delay"))
			.thenWaitUntil(() -> helper.assertTrue(openCovers(arena, trap) == 0 && armedPlates(arena, trap) == 6, "the pit has not closed yet: " + openCovers(arena, trap)))
			.thenExecute(arena::release)
			.thenSucceed();
	}
}
