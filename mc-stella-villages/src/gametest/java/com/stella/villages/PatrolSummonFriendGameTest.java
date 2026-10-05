package com.stella.villages;

import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.VillageWitchSplash;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mob wearing the pack's friendly-summon mark is one of the village's own: neither the duke and his
 * guards nor the patrol's defence takes it, and the village witch's harmful splash passes it by.
 */
public class PatrolSummonFriendGameTest {
	private static final long WATCH_TICKS = 140L;
	/** The summon stands this close, where a goal that read the raw list would take it first. */
	private static final double NEAR = 2.0;
	/** The control stands further out, and must still be taken. */
	private static final double FAR = 5.0;

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aGuardNeverTakesAFriendlySummonAsQuarry(final GameTestHelper helper) {
		String test = "aGuardNeverTakesAFriendlySummonAsQuarry";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob duke = PatrolTestSupport.role(helper, test, PatrolState.Role.HEAD.name());
		helper.assertTrue(duke != null, "the village has no duke");
		for (Mob other : PatrolTestSupport.patrols(helper)) {
			if (other != duke) {
				other.setNoAi(true);
			}
		}
		duke.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		BlockPos bell = PatrolTestSupport.anchor(helper);
		Vec3 at = Vec3.atBottomCenterOf(bell);
		duke.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		Mob summon = skeleton(helper, test, duke, 1, NEAR);
		helper.assertTrue(summon != null, "the summon could not be placed");
		Mob wild = skeleton(helper, test, duke, -1, FAR);
		helper.assertTrue(wild != null, "the control could not be placed");
		// The summon stands nearer, so a goal that read the raw target would take it first.
		helper.assertTrue(summon.distanceTo(duke) <= wild.distanceTo(duke),
			"the summon stands no nearer than the control, so a miss proves nothing: summon="
				+ summon.distanceTo(duke) + " wild=" + wild.distanceTo(duke));
		helper.assertTrue(subject(duke, summon), "the summon has no clear line to the duke, so the case proves nothing");
		helper.assertTrue(subject(duke, wild), "the control has no clear line to the duke, so the case proves nothing");
		int[] onSummon = {0};
		int[] onWild = {0};
		helper.onEachTick(() -> {
			if (duke.getTarget() == summon) {
				onSummon[0]++;
			}
			if (duke.getTarget() == wild) {
				onWild[0]++;
			}
		});
		helper.runAfterDelay(WATCH_TICKS, () -> {
			PatrolTestSupport.result(test, "onSummon=" + onSummon[0] + " onWild=" + onWild[0]
				+ " summonDistance=" + summon.distanceTo(duke) + " wildDistance=" + wild.distanceTo(duke));
			helper.assertTrue(onWild[0] > 0, "the duke never took the untagged skeleton beside it, so the case proves nothing");
			helper.assertTrue(onSummon[0] == 0, "the duke held the summoned skeleton as its quarry for " + onSummon[0] + " ticks");
			summon.discard();
			wild.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aDefendingPatrolNeverTakesAFriendlySummonAsAThreat(final GameTestHelper helper) {
		String test = "aDefendingPatrolNeverTakesAFriendlySummonAsAThreat";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}
		Mob summon = plainSkeleton(helper, "servant");
		summon.addTag(VillageWitchSplash.SUMMONED_TAG);
		Mob wild = plainSkeleton(helper, "stranger");
		Mob victim = helper.spawnMob(EntityTypes.VILLAGER, 6, 1, 6).withNoFreeWill().spawn();
		summon.setTarget(victim);
		wild.setTarget(victim);
		Boolean summonThreat = PatrolTestSupport.defendSeesThreat(summon);
		Boolean wildThreat = PatrolTestSupport.defendSeesThreat(wild);
		Boolean untargeted = PatrolTestSupport.defendSeesThreat(plainSkeleton(helper, "idle"));
		PatrolTestSupport.result(test, "summon=" + summonThreat + " wild=" + wildThreat + " idle=" + untargeted
			+ " mark=" + summon.entityTags().contains(VillageWitchSplash.SUMMONED_TAG));
		helper.assertTrue(wildThreat != null && wildThreat, "the control skeleton is not a threat either, so the case proves nothing");
		helper.assertTrue(untargeted != null && !untargeted, "a skeleton with no target is a threat, so the case proves nothing");
		helper.assertTrue(summonThreat != null && !summonThreat, "the patrol takes a summoned skeleton as a threat");
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theWitchSplashNeverTouchesAFriendlySummon(final GameTestHelper helper) {
		String test = "theWitchSplashNeverTouchesAFriendlySummon";
		if (!PatrolTestSupport.prepareVillage(helper, test, false)) {
			return;
		}
		Mob summon = plainSkeleton(helper, "servant");
		summon.addTag(VillageWitchSplash.SUMMONED_TAG);
		Mob wild = plainSkeleton(helper, "stranger");
		boolean summonEligible = VillageWitchSplash.hostile(summon);
		boolean wildEligible = VillageWitchSplash.hostile(wild);
		Vec3 splashAt = summon.position();
		wild.setPos(splashAt.x, splashAt.y, splashAt.z);
		Mob owner = plainSkeleton(helper, "thrower");
		ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
		summon.setHealth(summon.getMaxHealth());
		wild.setHealth(wild.getMaxHealth());
		var splash = new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion(
			helper.getLevel(), owner, stack);
		splash.addTag(VillageWitchSplash.OFFENSIVE_TAG);
		splash.setPos(splashAt.x, splashAt.y + 1.0, splashAt.z);
		splash.onHitAsPotion(helper.getLevel(), stack,
			new net.minecraft.world.phys.BlockHitResult(splash.position(), net.minecraft.core.Direction.UP,
				splash.blockPosition(), false));
		float summonLost = summon.getMaxHealth() - summon.getHealth();
		float wildLost = wild.getMaxHealth() - wild.getHealth();
		PatrolTestSupport.result(test, "summonEligible=" + summonEligible + " wildEligible=" + wildEligible
			+ " summonLost=" + summonLost + " wildLost=" + wildLost);
		helper.assertTrue(wildEligible && summonEligible == false,
			"the splash calls the summon eligible=" + summonEligible + " and the stranger eligible=" + wildEligible);
		helper.assertTrue(wildLost > 0.0F, "the splash left the untagged skeleton untouched, so the case proves nothing");
		helper.assertTrue(summonLost == 0.0F, "the splash took " + summonLost + " from the summoned skeleton");
		helper.assertTrue(summon.getHealth() == summon.getMaxHealth()
			&& summon.getActiveEffects().stream().noneMatch(effect -> effect.getEffect().value().getCategory()
				== net.minecraft.world.effect.MobEffectCategory.HARMFUL),
			"the summoned skeleton carries a harmful effect: " + summon.getActiveEffects());
		helper.succeed();
	}

	private static Skeleton plainSkeleton(final GameTestHelper helper, final String name) {
		var created = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(created instanceof Skeleton, "the level refused a skeleton");
		Skeleton skeleton = (Skeleton) created;
		skeleton.setNoAi(true);
		skeleton.setPersistenceRequired();
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		skeleton.setCustomName(net.minecraft.network.chat.Component.literal(name));
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	/** A marked (side 1) or unmarked (side -1) skeleton on cleared ground where the duke can see it. */
	private static @Nullable Skeleton skeleton(
		final GameTestHelper helper, final String test, final Mob duke, final int side, final double wanted
	) {
		int nearest = (int) Math.round(wanted);
		int[] distances = wanted <= 0.0
			? new int[] {nearest, nearest + 1, nearest - 1}
			: new int[] {nearest, nearest + 1, nearest - 1};
		for (int distance : distances) {
			if (distance < 1) {
				continue;
			}
			for (double dz : new double[] {0.0, 1.0, -1.0, 2.0, -2.0}) {
				Vec3 at = duke.position().add(side * distance, 0.0, dz);
				clearCell(helper, BlockPos.containing(at));
				var created = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
				if (!(created instanceof Skeleton candidate)) {
					return null;
				}
				candidate.setNoAi(true);
				candidate.setNoGravity(true);
				candidate.setPersistenceRequired();
				candidate.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
				if (side > 0) {
					candidate.addTag(VillageWitchSplash.SUMMONED_TAG);
				}
				if (helper.getLevel().addFreshEntity(candidate) && subject(duke, candidate)) {
					PatrolTestSupport.detail(test, "placed side=" + side + " distance=" + candidate.distanceTo(duke)
						+ " dz=" + dz + " sight=" + duke.hasLineOfSight(candidate));
					return candidate;
				}
				candidate.discard();
			}
		}
		return null;
	}

	// The two blocks a standing body needs, and one above for the sight line.
	private static void clearCell(final GameTestHelper helper, final BlockPos feet) {
		for (int dy = 0; dy <= 2; dy++) {
			helper.getLevel().setBlockAndUpdate(feet.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		}
	}

	/** True when the duke can see the subject, clearing the line between their bodies. */
	private static boolean subject(final Mob duke, final Mob target) {
		if (duke.hasLineOfSight(target)) {
			return true;
		}
		Vec3 from = duke.position();
		Vec3 to = target.position();
		int steps = (int) Math.ceil(from.distanceTo(to)) + 1;
		for (int step = 0; step <= steps; step++) {
			Vec3 point = from.lerp(to, (double) step / steps);
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos block = BlockPos.containing(point).above(dy);
				if (!duke.level().getBlockState(block).isAir()) {
					duke.level().setBlockAndUpdate(block, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		return duke.hasLineOfSight(target);
	}
}
