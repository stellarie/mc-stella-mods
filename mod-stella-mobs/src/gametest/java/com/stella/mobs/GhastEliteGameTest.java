package com.stella.mobs;

import com.stella.mobs.behavior.BehaviorInstaller;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.GhastFireballGoal;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.block.Blocks;

/**
 * Every ghast fires five small shots a second. A shot never explodes: where it lands it leaves a small
 * lingering cloud of Wither, stronger from an elite. A ghast is not a Monster, so it never rolls gear,
 * and the kit drop is vacuous rather than broken.
 */
public class GhastEliteGameTest {
	private static final String TEST = "ghastElite";
	private static final int GHAST_BASE_HEALTH = 10;
	private static final int VICTIM_HEALTH = 400;

	private static final Map<java.util.UUID, List<Integer>> FIRED = new ConcurrentHashMap<>();
	private static final Map<java.util.UUID, List<MobEffectInstance>> CLOUDS = new ConcurrentHashMap<>();
	private static final Map<java.util.UUID, java.util.UUID> CLOUD_OWNER = new ConcurrentHashMap<>();
	private static final Map<java.util.UUID, Float> CLOUD_RADIUS = new ConcurrentHashMap<>();
	private static final Set<java.util.UUID> BLASTED = ConcurrentHashMap.newKeySet();
	private static final Map<java.util.UUID, List<net.minecraft.world.phys.Vec3>> CLOUD_AT = new ConcurrentHashMap<>();

	static {
		GhastFireballGoal.setRecorder(new GhastFireballGoal.Recorder() {
			@Override
			public void fired(final Ghast ghast, final int serverTick) {
				FIRED.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(serverTick);
			}

			@Override
			public void cloud(final Ghast ghast, final AreaEffectCloud cloud, final MobEffectInstance wither) {
				CLOUDS.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(wither);
				CLOUD_OWNER.put(cloud.getUUID(), ghast.getUUID());
				CLOUD_RADIUS.put(cloud.getUUID(), cloud.getRadius());
				CLOUD_AT.computeIfAbsent(ghast.getUUID(), key -> new CopyOnWriteArrayList<>()).add(cloud.position());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
			if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() instanceof Ghast) {
				BLASTED.add(victim.getUUID());
			}
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhastFiresFiveTimesASecond(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 7));
		helper.succeedWhen(() -> {
			List<Integer> ticks = FIRED.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(ticks.size() >= 6, "the ghast has fired " + ticks.size() + " times");
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < ticks.size(); i++) {
				gaps.add(ticks.get(i) - ticks.get(i - 1));
			}
			helper.assertTrue(gaps.stream().allMatch(gap -> gap == GhastFireballGoal.INTERVAL_TICKS), "the ghast fired " + gaps + " ticks apart, not every " + GhastFireballGoal.INTERVAL_TICKS);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastFiresFiveTimesASecond gaps={}", TEST, gaps);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhastShotLeavesAWitherCloudInsteadOfExploding(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		Mob victim = spawnSubject(helper, 4, 1, 7);
		aim(helper, ghast, victim);
		int blocksBefore = solidBlocks(helper, helper.absolutePos(new BlockPos(4, 1, 7)));
		helper.succeedWhen(() -> {
			List<MobEffectInstance> clouds = CLOUDS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!clouds.isEmpty() && victim.hasEffect(MobEffects.WITHER), "no wither cloud has reached the victim yet: clouds at " + CLOUD_AT.getOrDefault(ghast.getUUID(), List.of()).stream().limit(4).map(helper::relativeVec).toList() + " victim at " + helper.relativeVec(victim.position()));
			MobEffectInstance wither = clouds.get(0);
			float radius = CLOUD_RADIUS.entrySet().stream().filter(entry -> ghast.getUUID().equals(CLOUD_OWNER.get(entry.getKey()))).map(Map.Entry::getValue).findFirst().orElse(-1.0F);
			helper.assertTrue(wither.getEffect().equals(MobEffects.WITHER) && wither.getAmplifier() == GhastFireballGoal.ORDINARY_WITHER_AMPLIFIER, "the cloud carries " + wither);
			helper.assertTrue(radius == GhastFireballGoal.CLOUD_RADIUS, "the cloud's radius is " + radius);
			helper.assertFalse(BLASTED.contains(victim.getUUID()), "a ghast shot exploded on the victim");
			int blocksNow = solidBlocks(helper, helper.absolutePos(new BlockPos(4, 1, 7)));
			helper.assertTrue(blocksNow == blocksBefore, "the shot changed the ground: " + blocksBefore + " -> " + blocksNow);
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotLeavesAWitherCloudInsteadOfExploding clouds={} radius={} wither={} blasts=0", TEST, clouds.size(), radius, wither.getAmplifier());
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void anEliteGhastsCloudWithersHarder(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, true);
		aim(helper, ghast, spawnSubject(helper, 4, 1, 7));
		helper.succeedWhen(() -> {
			List<MobEffectInstance> clouds = CLOUDS.getOrDefault(ghast.getUUID(), List.of());
			helper.assertTrue(!clouds.isEmpty(), "the elite ghast has left no cloud yet");
			helper.assertTrue(clouds.get(0).getAmplifier() == GhastFireballGoal.ELITE_WITHER_AMPLIFIER, "the elite cloud carries Wither " + (clouds.get(0).getAmplifier() + 1));
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=anEliteGhastsCloudWithersHarder wither={}", TEST, clouds.get(0).getAmplifier() + 1);
			ghast.discard();
		});
	}

	@GameTest(maxTicks = 200)
	public void aGhastShotThatHitsNothingBurnsOut(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		com.stella.mobs.projectile.GhastWitherShot shot = new com.stella.mobs.projectile.GhastWitherShot(helper.getLevel(), ghast, net.minecraft.world.phys.Vec3.ZERO, 0);
		shot.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 4.0, 4.5)));
		shot.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		helper.getLevel().addFreshEntity(shot);
		ghast.discard();
		helper.runAfterDelay(com.stella.mobs.projectile.StellaSmallFireball.MAX_LIFE_TICKS + 5L, () -> {
			helper.assertTrue(shot.isRemoved(), "a shot that hit nothing is still flying after " + shot.tickCount + " ticks");
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotThatHitsNothingBurnsOut removed=true", TEST);
			helper.succeed();
		});
	}

	@GameTest
	public void aGhastShotIsNeverSaved(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		com.stella.mobs.projectile.GhastWitherShot shot = new com.stella.mobs.projectile.GhastWitherShot(helper.getLevel(), ghast, net.minecraft.world.phys.Vec3.ZERO, 0);
		boolean saved = shot.shouldBeSaved();
		ghast.discard();
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastShotIsNeverSaved saved={}", TEST, saved);
		helper.assertFalse(saved, "a ghast shot would be saved with its chunk and reload as a vanilla fireball");
		helper.succeed();
	}

	// Alone in its own batch, so no neighbouring test's barrier ceiling lies across the long sight line.
	@GameTest(environment = "mod-stella-mobs-test:ghast_range", maxTicks = 200, padding = 96)
	public void aGhastHoldsFireBeyondItsRange(final GameTestHelper helper) {
		Ghast ghast = spawnGhast(helper, false);
		// Low enough that its eyes sit below the test's barrier ceiling, so it sees out across the open floor.
		ghast.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1.0, 4.5)), 0.0F, 0.0F);
		ghast.setNoGravity(true);
		// A full-size ghast's eyes stand in the barrier ceiling, so this one is shrunk to see under it.
		ghast.getAttribute(Attributes.SCALE).setBaseValue(0.25);
		ghast.refreshDimensions();
		net.minecraft.world.phys.Vec3 far = ghast.position().add(GhastFireballGoal.MAX_RANGE + 6.0, 0.0, 0.0);
		int chunkX = net.minecraft.core.SectionPos.blockToSectionCoord(far.x);
		int chunkZ = net.minecraft.core.SectionPos.blockToSectionCoord(far.z);
		boolean forced = helper.getLevel().setChunkForced(chunkX, chunkZ, true);
		Mob victim = net.minecraft.world.entity.EntityTypes.COW.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		victim.snapTo(far, 0.0F, 0.0F);
		victim.setNoAi(true);
		victim.setNoGravity(true);
		victim.setPersistenceRequired();
		helper.getLevel().addFreshEntity(victim);
		// The test area is walled with barrier; the few wall blocks on the sight line are opened so the ghast sees out.
		for (int opened = 0; opened < 4; opened++) {
			net.minecraft.world.phys.BlockHitResult wall = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(ghast.getEyePosition(), victim.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ghast));
			if (wall.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !helper.getLevel().getBlockState(wall.getBlockPos()).is(Blocks.BARRIER)) {
				break;
			}
			helper.getLevel().setBlockAndUpdate(wall.getBlockPos(), Blocks.AIR.defaultBlockState());
		}
		aim(helper, ghast, victim);
		helper.runAfterDelay(60L, () -> {
			int shots = FIRED.getOrDefault(ghast.getUUID(), List.of()).size();
			boolean sees = ghast.hasLineOfSight(victim);
			net.minecraft.world.phys.BlockHitResult clip = helper.getLevel().clip(new net.minecraft.world.level.ClipContext(ghast.getEyePosition(), victim.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, ghast));
			double distance = ghast.distanceTo(victim);
			ghast.discard();
			victim.discard();
			if (forced) {
				helper.getLevel().setChunkForced(chunkX, chunkZ, false);
			}
			StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test=aGhastHoldsFireBeyondItsRange shots={} distance={} sees={}", TEST, shots, distance, sees);
			helper.assertTrue(sees, "the ghast cannot see the far target, so the case proves nothing: the sight line stops at " + clip.getBlockPos() + " " + helper.getLevel().getBlockState(clip.getBlockPos()) + " from eye " + ghast.getEyePosition() + " to " + victim.getEyePosition() + " in bounds " + helper.getBounds());
			helper.assertTrue(shots == 0, "the ghast fired " + shots + " times at a target " + distance + " blocks away");
			helper.succeed();
		});
	}

	// A hurt victim carries immunity frames, and setTarget refuses an invulnerable target, so they are cleared first.
	private static void aim(final GameTestHelper helper, final Ghast ghast, final Mob victim) {
		helper.onEachTick(() -> {
			victim.setInvulnerableTime(0);
			ghast.setTarget(victim);
		});
	}

	@GameTest(maxTicks = 200)
	public void eliteGhastCarriesNoGear(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Ghast ghast = spawnGhast(helper, true);

		helper.runAfterDelay(4L, () -> {
			int slots = 0;

			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (!ghast.getItemBySlot(slot).isEmpty()) {
					slots++;
				}
			}

			double scale = ghast.getAttribute(Attributes.SCALE) == null ? 1.0 : ghast.getAttribute(Attributes.SCALE).getValue();
			helper.assertValueEqual(Integer.valueOf(slots), Integer.valueOf(0), "gear slots filled on an elite ghast");
			helper.assertTrue(
				Math.abs(scale - (1.0 + Elite.SCALE_BONUS)) < 1.0e-6,
				"the elite ghast scale was " + scale + " against " + (1.0 + Elite.SCALE_BONUS)
			);
			helper.assertTrue(
				ghast.getMaxHealth() >= GHAST_BASE_HEALTH * Elite.HEALTH_MULTIPLIER,
				"the elite ghast health was " + ghast.getMaxHealth() + ", below the elite doubling of "
					+ (GHAST_BASE_HEALTH * Elite.HEALTH_MULTIPLIER)
			);
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test={} RESULT slots={} scale={} health={} drops={}",
				TEST, slots, String.format(java.util.Locale.ROOT, "%.2f", scale), ghast.getMaxHealth(), 0
			);
			helper.succeed();
		});
	}

	/** Marked and installed by hand, per the fixture rule: no config write, no chance pin. */
	private static Ghast spawnGhast(final GameTestHelper helper, final boolean elite) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		// The fixture takes none of the natural spawn rolls, so an ordinary ghast can never roll elite or boss.
		Ghast ghast = EntityTypes.GHAST.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
		ghast.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		ghast.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 3.0, 0.5)), 0.0F, 0.0F);
		ghast.setPersistenceRequired();
		helper.getLevel().addFreshEntity(ghast);
		// A ghast drifts upward until its eyes sit in the test's barrier ceiling and it sees nothing, so it stays put.
		ghast.getGoalSelector().removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.monster.Ghast.RandomFloatAroundGoal);

		if (elite) {
			Elite.mark(ghast, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		}

		BehaviorInstaller.installGhastFireball(ghast);
		return ghast;
	}

	/** A cow: not undead, so Poison is accepted, and not a Monster, so no gear path touches it. */
	private static Mob spawnSubject(final GameTestHelper helper, final int x, final int y, final int z) {
		Mob victim = helper.spawnMob(EntityTypes.COW, x, y, z).withNoFreeWill().spawn();
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(VICTIM_HEALTH);
		victim.setHealth(VICTIM_HEALTH);
		victim.setPersistenceRequired();
		return victim;
	}

	private static int solidBlocks(final GameTestHelper helper, final BlockPos centre) {
		int solid = 0;

		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);

					if (!helper.getLevel().getBlockState(pos).is(Blocks.AIR)) {
						solid++;
					}
				}
			}
		}

		return solid;
	}
}
