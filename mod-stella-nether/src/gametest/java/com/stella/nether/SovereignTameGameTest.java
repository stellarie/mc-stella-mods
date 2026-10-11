package com.stella.nether;

import com.stella.mobs.scaling.SelfScaled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The tame Sovereign: only its charge and its obstruction clearance change blocks, and every hit it lands comes in
 * two halves, one fire and one physical, that add up to the hit it was.
 */
public class SovereignTameGameTest {
	/** A hit big enough to read, small enough for a sponge. */
	static final float HIT = 8.0F;
	static final float TOLERANCE = 0.01F;

	static double factor() {
		return SelfScaled.factor(SovereignScaleGameTest.LEVEL);
	}

	/** The hit the mobs mod's level factor makes of a raw one. */
	static float scaled(final float raw) {
		return (float) (raw * factor());
	}

	/** Every position of a box, floor to ceiling, in a fixed order. */
	static List<BlockPos> box(final BlockPos centre, final int half, final int from, final int to) {
		List<BlockPos> all = new ArrayList<>();
		for (int y = from; y <= to; y++) {
			for (int x = -half; x <= half; x++) {
				for (int z = -half; z <= half; z++) {
					all.add(centre.offset(x, y, z));
				}
			}
		}
		return all;
	}

	static List<BlockState> states(final ServerLevel level, final List<BlockPos> at) {
		List<BlockState> states = new ArrayList<>(at.size());
		for (BlockPos pos : at) {
			states.add(level.getBlockState(pos));
		}
		return states;
	}

	/**
	 * How many of the positions hold something other than what they held before, and the first few. Left out, because no
	 * Sovereign did them: fluid, which flows from the raid's lava lakes into a cleared arena of its own accord, and a
	 * block that ticks at random, such as nylium, which turns to netherrack once a test's stone covers it.
	 */
	static String changes(final ServerLevel level, final List<BlockPos> at, final List<BlockState> before) {
		int changed = 0;
		List<String> first = new ArrayList<>();
		for (int i = 0; i < at.size(); i++) {
			BlockState now = level.getBlockState(at.get(i));
			if (now != before.get(i) && now.getFluidState().isEmpty() && before.get(i).getFluidState().isEmpty() && !before.get(i).isRandomlyTicking()) {
				changed++;
				if (first.size() < 3) {
					first.add(at.get(i).toShortString() + " " + before.get(i).getBlock().getName().getString() + "->" + now.getBlock().getName().getString());
				}
			}
		}
		return changed + " " + first;
	}

	/** Null when a hit came in exactly two halves, one fire and one not, each the same size, adding up to the whole. */
	static String splitProblem(final List<HitLog.Part> parts, final float whole) {
		if (parts.size() != 2 || HitLog.fire(parts) != 1 || HitLog.physical(parts) != 1) {
			return "parts=" + parts.size() + " fire=" + HitLog.fire(parts) + " physical=" + HitLog.physical(parts);
		}
		if (Math.abs(parts.get(0).base() - parts.get(1).base()) > TOLERANCE || Math.abs(HitLog.sum(parts) - whole) > TOLERANCE) {
			return "halves " + parts.get(0).base() + " and " + parts.get(1).base() + " against a whole of " + whole;
		}
		return null;
	}

	static void finish(final GameTestHelper helper, final SovereignSpecialsGameTest.Scene scene, final String problem, final Entity... others) {
		for (Entity other : others) {
			if (other instanceof LivingEntity living) {
				HitLog.forget(living);
			}
		}
		HitLog.forget(scene.player());
		SovereignSpecialsGameTest.end(helper, scene, others);
		helper.assertTrue(problem == null, String.valueOf(problem));
	}

	// ---- Blocks ----

	@GameTest(environment = "mod-stella-nether-test:tame_dive_blocks", maxTicks = 1200)
	public void theDiveChangesNoBlockAndStillHurtsAndIgnites(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 16);
		Zombie struck = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		List<BlockPos> room = box(BlockPos.containing(scene.floor()), 7, -1, 15);
		AtomicReference<List<BlockState>> before = new AtomicReference<>();
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		float[] health = new float[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, struck), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(struck, false);
				HitLog.watch(struck);
				before.set(states(scene.raid(), room));
				health[0] = struck.getHealth();
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.DIVE, struck);
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].done(), "the dive has not landed"))
			.thenExecute(() -> {
				String changed = changes(scene.raid(), room, before.get());
				boolean hit = cast[0].hits().contains(struck.getUUID());
				boolean burning = struck.getRemainingFireTicks() > 0;
				float lost = health[0] - struck.getHealth();
				String split = splitProblem(HitLog.from(struck, scene.sovereign()), scaled(SovereignSpecials.DIVE_DAMAGE));
				NetherTestSupport.result("tameDive", "changed=" + changed + " hit=" + hit + " burning=" + burning + " lost=" + lost + " split=" + split);
				finish(helper, scene, !changed.startsWith("0 ") ? "the dive changed blocks: " + changed
					: !hit || !burning || lost <= 0.0F ? "dive hit=" + hit + " burning=" + burning + " lost=" + lost : split, struck);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_vortex_sphere", maxTicks = 1500)
	public void theVortexBlastChangesNoBlockOfItsSphere(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		// The whole sphere stone but a hollow round the Sovereign, so it is not shut in and the clearance stays out of it.
		Vec3 centre = new Vec3(scene.sovereign().getX(), scene.sovereign().getY() + scene.sovereign().getBbHeight() * 0.5, scene.sovereign().getZ());
		int reach = (int) Math.ceil(SovereignSpecials.VORTEX_RADIUS);
		BlockPos base = BlockPos.containing(centre);
		List<BlockPos> sphere = new ArrayList<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dy = -reach; dy <= reach; dy++) {
				for (int dz = -reach; dz <= reach; dz++) {
					BlockPos at = base.offset(dx, dy, dz);
					double distance = Vec3.atCenterOf(at).distanceTo(centre);
					if (distance <= SovereignSpecials.VORTEX_RADIUS && !scene.raid().isOutsideBuildHeight(at)) {
						sphere.add(at);
						if (distance > 5.0 && scene.raid().getBlockState(at).isAir()) {
							scene.raid().setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		AtomicReference<List<BlockState>> before = new AtomicReference<>();
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				before.set(states(scene.raid(), sphere));
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player());
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(cast[0].done(), "the channel is not over");
			})
			.thenExecute(() -> {
				String changed = changes(scene.raid(), sphere, before.get());
				NetherTestSupport.result("tameVortexSphere", "positions=" + sphere.size() + " blasted=" + cast[0].blasted() + " changed=" + changed);
				finish(helper, scene, !cast[0].blasted() ? "the vortex did not burst" : !changed.startsWith("0 ") ? "the vortex blast changed blocks: " + changed : null);
			})
			.thenSucceed();
	}

	// ---- Damage ----

	@GameTest(environment = "mod-stella-nether-test:tame_split", maxTicks = 200)
	public void aSovereignHitComesInTwoEqualHalvesThatAddUpToTheHit(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(target, false);
				HitLog.watch(target);
				float before = target.getHealth();
				target.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				float lost = before - target.getHealth();
				int window = target.damageCooldownTime;
				List<HitLog.Part> parts = HitLog.from(target, scene.sovereign());
				String split = splitProblem(parts, scaled(HIT));
				// The same hit again inside the window adds nothing, as one whole hit would add nothing.
				target.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				float lostAgain = before - target.getHealth();
				int partsAfter = HitLog.from(target, scene.sovereign()).size();
				NetherTestSupport.result("tameSplit", "lost=" + lost + " whole=" + scaled(HIT) + " window=" + window + " lostAgain=" + lostAgain + " parts=" + partsAfter + " split=" + split);
				finish(helper, scene, split != null ? split
					: Math.abs(lost - scaled(HIT)) > TOLERANCE ? "the target lost " + lost + " of " + scaled(HIT)
					: window != 20 ? "the window after the hit is " + window
					: Math.abs(lostAgain - lost) > TOLERANCE || partsAfter != 2 ? "the same hit again added " + (lostAgain - lost) + " in " + (partsAfter - 2) + " more parts" : null, target);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_fireball", maxTicks = 200)
	public void aFireballIsHalvedOnceThenSplit(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(target, false);
				HitLog.watch(target);
				float before = target.getHealth();
				new SovereignScaleGameTest.Striker(scene.raid(), scene.sovereign()).strike(target);
				float lost = before - target.getHealth();
				float whole = scaled(SovereignScaleGameTest.VANILLA_FIREBALL * BlazeSovereign.FIREBALL_SHARE);
				String split = splitProblem(HitLog.from(target, scene.sovereign()), whole);
				NetherTestSupport.result("tameFireball", "lost=" + lost + " whole=" + whole + " split=" + split);
				finish(helper, scene, split != null ? split : Math.abs(lost - whole) > TOLERANCE ? "the fireball took " + lost + " of " + whole : null, target);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_fireproof", maxTicks = 200)
	public void aFireResistantVictimTakesTheHalfThatIsNotFire(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie struck = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), true, helper);
		Zombie burned = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 4.0), true, helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, struck, burned), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(struck, true);
				SovereignScaleGameTest.settle(burned, true);
				HitLog.watch(struck);
				HitLog.watch(burned);
				float struckBefore = struck.getHealth();
				float burnedBefore = burned.getHealth();
				struck.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				new SovereignScaleGameTest.Striker(scene.raid(), scene.sovereign()).strike(burned);
				float hitLost = struckBefore - struck.getHealth();
				float ballLost = burnedBefore - burned.getHealth();
				int window = struck.damageCooldownTime;
				List<HitLog.Part> hitParts = HitLog.from(struck, scene.sovereign());
				List<HitLog.Part> ballParts = HitLog.from(burned, scene.sovereign());
				float hitHalf = scaled(HIT) / 2.0F;
				float ballHalf = scaled(SovereignScaleGameTest.VANILLA_FIREBALL * BlazeSovereign.FIREBALL_SHARE) / 2.0F;
				// The refused fire half must not leave the window the physical half opened closed.
				struck.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				float again = struckBefore - struck.getHealth();
				NetherTestSupport.result("tameFireproof", "hitLost=" + hitLost + " half=" + hitHalf + " ballLost=" + ballLost + " ballHalf=" + ballHalf
					+ " window=" + window + " again=" + again + " hitParts=" + hitParts.size() + " ballParts=" + ballParts.size());
				finish(helper, scene, Math.abs(hitLost - hitHalf) > TOLERANCE || hitParts.size() != 1 || HitLog.fire(hitParts) != 0
						? "a fire-resistant target lost " + hitLost + " of the physical half " + hitHalf + " in " + hitParts.size() + " parts"
					: Math.abs(ballLost - ballHalf) > TOLERANCE || ballParts.size() != 1 || HitLog.fire(ballParts) != 0
						? "a fire-resistant target lost " + ballLost + " of the fireball's physical half " + ballHalf + " in " + ballParts.size() + " parts"
					: window != 20 || Math.abs(again - hitLost) > TOLERANCE ? "the window after the hit is " + window + " and the same hit again added " + (again - hitLost)
					: null, struck, burned);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_divisor", maxTicks = 200)
	public void theHealthDivisorAppliesOnceToASplitHit(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie target = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		Zombie shot = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 4.0), false, helper);
		double divisor = 4.0;
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, target, shot), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(target, false);
				SovereignScaleGameTest.settle(shot, false);
				target.setAttached(BlazeSovereign.DIVISOR, divisor);
				shot.setAttached(BlazeSovereign.DIVISOR, divisor);
				float targetBefore = target.getHealth();
				float shotBefore = shot.getHealth();
				target.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				new SovereignScaleGameTest.Striker(scene.raid(), scene.sovereign()).strike(shot);
				float hitLost = targetBefore - target.getHealth();
				float ballLost = shotBefore - shot.getHealth();
				float hitWhole = (float) (scaled(HIT) / divisor);
				float ballWhole = (float) (scaled(SovereignScaleGameTest.VANILLA_FIREBALL * BlazeSovereign.FIREBALL_SHARE) / divisor);
				NetherTestSupport.result("tameDivisor", "hitLost=" + hitLost + " hitWhole=" + hitWhole + " ballLost=" + ballLost + " ballWhole=" + ballWhole);
				finish(helper, scene, Math.abs(hitLost - hitWhole) > TOLERANCE ? "the hit took " + hitLost + " of " + hitWhole
					: Math.abs(ballLost - ballWhole) > TOLERANCE ? "the fireball took " + ballLost + " of " + ballWhole : null, target, shot);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_immune", maxTicks = 200)
	public void aFireImmuneVictimIsHurtAsItWasAndFireballsStillSpareIt(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Blaze brood = EntityTypes.BLAZE.create(scene.raid(), EntitySpawnReason.MOB_SUMMONED);
		brood.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
		brood.snapTo(scene.floor().x + 4.0, scene.floor().y + 0.5, scene.floor().z, 0.0F, 0.0F);
		brood.setNoAi(true);
		brood.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000.0);
		brood.setHealth(5000.0F);
		scene.raid().addFreshEntity(brood);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, brood), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				brood.removeAllEffects();
				brood.setAbsorptionAmount(0.0F);
				brood.setHealth(brood.getMaxHealth());
				HitLog.watch(brood);
				float before = brood.getHealth();
				new SovereignScaleGameTest.Striker(scene.raid(), scene.sovereign()).strike(brood);
				float ballLost = before - brood.getHealth();
				int ballParts = HitLog.from(brood, scene.sovereign()).size();
				brood.damageCooldownTime = 0;
				brood.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				float hitLost = before - brood.getHealth();
				List<HitLog.Part> parts = HitLog.from(brood, scene.sovereign());
				NetherTestSupport.result("tameImmune", "ballLost=" + ballLost + " ballParts=" + ballParts + " hitLost=" + hitLost + " parts=" + parts.size());
				finish(helper, scene, ballLost != 0.0F || ballParts != 0 ? "a fireball hurt a fire-immune blaze: " + ballLost + " in " + ballParts + " parts"
					: Math.abs(hitLost - scaled(HIT)) > TOLERANCE || parts.size() != 1 || HitLog.fire(parts) != 0
						? "a fire-immune blaze took " + hitLost + " of " + scaled(HIT) + " in " + parts.size() + " parts" : null, brood);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_push", maxTicks = 200)
	public void aSplitHitPushesAsFarAsOneHit(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie struck = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 0.0), false, helper);
		Zombie control = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, 5.0), false, helper);
		Blaze plain = EntityTypes.BLAZE.create(scene.raid(), EntitySpawnReason.MOB_SUMMONED);
		plain.addTag(com.stella.mobs.StellaMobs.NO_SPAWN_ROLL_TAG);
		plain.snapTo(scene.floor().x, scene.floor().y, scene.floor().z + 5.0, 0.0F, 0.0F);
		plain.setNoAi(true);
		scene.raid().addFreshEntity(plain);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, struck, control, plain), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(struck, false);
				SovereignScaleGameTest.settle(control, false);
				struck.setDeltaMovement(Vec3.ZERO);
				control.setDeltaMovement(Vec3.ZERO);
				struck.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				control.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(plain), HIT);
				double pushed = struck.getDeltaMovement().horizontalDistance();
				double once = control.getDeltaMovement().horizontalDistance();
				NetherTestSupport.result("tamePush", "pushed=" + pushed + " once=" + once);
				plain.discard();
				finish(helper, scene, once <= 0.0 || Math.abs(pushed - once) > 1.0E-4 ? "a split hit pushed " + pushed + " where one hit pushes " + once : null, struck, control);
			})
			.thenSucceed();
	}

	/** A shield held up by a creature facing the Sovereign, long enough for its block delay to have passed. */
	static void raiseShield(final Zombie zombie) {
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		zombie.startUsingItem(InteractionHand.OFF_HAND);
		try {
			java.lang.reflect.Field remaining = LivingEntity.class.getDeclaredField("useItemRemaining");
			remaining.setAccessible(true);
			remaining.setInt(zombie, zombie.getUseItem().getUseDuration(zombie) - 20);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
	}

	@GameTest(environment = "mod-stella-nether-test:tame_shield", maxTicks = 200)
	public void aShieldBlocksBothHalvesOfASovereignHit(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		// The zombie faces south and the Sovereign stands south of it.
		Zombie guarded = SovereignScaleGameTest.sponge(scene, scene.floor().add(0.0, 0.0, -4.0), false, helper);
		Zombie bare = SovereignScaleGameTest.sponge(scene, scene.floor().add(4.0, 0.0, -4.0), false, helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, guarded, bare), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(guarded, false);
				SovereignScaleGameTest.settle(bare, false);
				raiseShield(guarded);
				HitLog.watch(guarded);
				float guardedBefore = guarded.getHealth();
				float bareBefore = bare.getHealth();
				boolean landed = guarded.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				bare.hurtServer(scene.raid(), scene.raid().damageSources().mobAttack(scene.sovereign()), HIT);
				float guardedLost = guardedBefore - guarded.getHealth();
				float bareLost = bareBefore - bare.getHealth();
				List<HitLog.Part> parts = HitLog.from(guarded, scene.sovereign());
				boolean allBlocked = parts.size() == 2 && parts.stream().allMatch(HitLog.Part::blocked);
				NetherTestSupport.result("tameShield", "landed=" + landed + " guardedLost=" + guardedLost + " bareLost=" + bareLost + " parts=" + parts.size() + " allBlocked=" + allBlocked);
				finish(helper, scene, bareLost <= 0.0F ? "the unshielded control lost " + bareLost
					: landed || guardedLost != 0.0F || !allBlocked ? "the shield let through " + guardedLost + " (landed=" + landed + ", blocked parts=" + allBlocked + ")" : null, guarded, bare);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_burst", maxTicks = 600)
	public void theSuffocationBurstsExplosionHurtsInTwoHalves(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		Zombie near = SovereignScaleGameTest.sponge(scene, scene.floor().add(3.0, 0.0, 0.0), false, helper);
		int[] bursts = new int[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene, near), "the arena's entities are not loaded"))
			.thenExecute(() -> {
				SovereignScaleGameTest.settle(near, false);
				HitLog.watch(near);
				bursts[0] = SovereignBody.burstsOf(scene.sovereign());
				SovereignBodyGameTest.fill(scene, scene.sovereign().getBoundingBox().deflate(0.05));
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene, near);
				helper.assertTrue(SovereignBody.burstsOf(scene.sovereign()) > bursts[0], "it has not burst out");
			})
			.thenExecute(() -> {
				List<HitLog.Part> parts = HitLog.from(near, scene.sovereign());
				int burst = SovereignBody.burstsOf(scene.sovereign()) - bursts[0];
				boolean evenSplit = !parts.isEmpty() && parts.size() % 2 == 0 && HitLog.fire(parts) == HitLog.physical(parts)
					&& Math.abs(parts.get(0).base() - parts.get(1).base()) < TOLERANCE;
				NetherTestSupport.result("tameBurst", "bursts=" + burst + " parts=" + parts.size() + " fire=" + HitLog.fire(parts) + " physical=" + HitLog.physical(parts));
				finish(helper, scene, evenSplit ? null : "the burst's explosion hurt the neighbour in " + parts.size() + " parts (" + HitLog.fire(parts) + " fire, " + HitLog.physical(parts) + " physical)", near);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_magma", maxTicks = 200)
	public void aSovereignHitHardensMagmaArmorOnceNotTwice(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SovereignSpecialsGameTest.scene(helper, 10);
		net.minecraft.server.level.ServerPlayer wearer = BlazeEnchantmentGameTest.wearer(helper, true);
		// A hit of this size would kill a player, and a creature that dies is not counted as hit.
		wearer.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(5000.0);
		int hits = 5;
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				float first = 0.0F;
				float last = 0.0F;
				for (int i = 0; i < hits; i++) {
					wearer.setHealth(wearer.getMaxHealth());
					wearer.damageCooldownTime = 0;
					wearer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(scene.sovereign()), HIT);
					last = wearer.getMaxHealth() - wearer.getHealth();
					first = i == 0 ? last : first;
				}
				double reduction = BlazeEnchantments.magmaReduction(wearer);
				NetherTestSupport.result("tameMagma", "reduction=" + reduction + " first=" + first + " last=" + last);
				NetherTestSupport.leave(helper, wearer);
				finish(helper, scene, Math.abs(reduction - BlazeEnchantments.MAGMA_PER_HIT * hits) > 1.0E-6
					? "after " + hits + " Sovereign hits the armor spares " + reduction + ", one stack a hit is " + BlazeEnchantments.MAGMA_PER_HIT * hits : null);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-nether-test:tame_flame_hit", maxTicks = 1200)
	public void aYellowVortexFlameHitsInTwoHalves(final GameTestHelper helper) {
		SovereignSpecialsGameTest.Scene scene = SoulNovaGameTest.wideScene(helper);
		// The player stands at the sphere's very edge, where a flame sets out. The vortex's own random flames fly inward from
		// there, so the only flame that can reach the player is the one sent along the line through them.
		double edge = Math.sqrt(Math.pow(SovereignSpecials.VORTEX_RADIUS - 0.1, 2) - Math.pow(scene.sovereign().getBbHeight() * 0.5 - scene.player().getBbHeight() * 0.5, 2));
		BlazeSovereignGameTest.moveTo(scene.player(), scene.floor().x + edge, scene.floor().y, scene.floor().z);
		NetherTestSupport.vulnerable(scene.player());
		// The raid hands every raider a random boon, and Fire Resistance among them would refuse the fire half.
		scene.player().removeAllEffects();
		scene.player().getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		scene.player().setHealth(1000.0F);
		SovereignSpecials.Cast[] cast = new SovereignSpecials.Cast[1];
		SovereignSpecials.Bullet[] sent = new SovereignSpecials.Bullet[1];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SovereignSpecialsGameTest.ready(scene), "the Sovereign is not loaded"))
			.thenExecute(() -> {
				HitLog.watch(scene.player());
				cast[0] = SovereignSpecials.cast(scene.sovereign(), SovereignSpecials.Special.VORTEX, scene.player());
			})
			.thenWaitUntil(() -> helper.assertTrue(cast[0].age() >= 1, "the channel has not begun"))
			.thenExecute(() -> {
				Vec3 through = scene.player().getBoundingBox().getCenter().subtract(cast[0].vortexCentre());
				sent[0] = SovereignSpecials.vortexBullet(cast[0], through, false);
			})
			.thenWaitUntil(() -> {
				SovereignSpecialsGameTest.ready(scene);
				helper.assertTrue(sent[0].end() != null, "the flame is still flying");
			})
			.thenExecute(() -> {
				List<HitLog.Part> parts = HitLog.from(scene.player(), scene.sovereign());
				boolean landed = sent[0].end() == SovereignSpecials.BulletEnd.HIT;
				String split = splitProblem(parts, scaled(SovereignSpecials.VORTEX_BULLET_DAMAGE));
				NetherTestSupport.result("tameFlameHit", "end=" + sent[0].end() + " parts=" + parts.size() + " split=" + split);
				finish(helper, scene, !landed ? "the flame ended " + sent[0].end() : split);
			})
			.thenSucceed();
	}
}
