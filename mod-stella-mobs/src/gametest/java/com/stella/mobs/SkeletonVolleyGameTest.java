package com.stella.mobs;

import com.stella.mobs.behavior.BossAbilities;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.SkeletonVolley;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearRoller;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * A skeleton archer's single shot slows what it hits for two seconds. Whenever its burst is ready it looses a
 * burst instead: separate arrows ten ticks apart, three from a plain skeleton, five from an elite and seven
 * from a boss, each aimed at the target as it leaves the bow with a few degrees of spread. No skeleton arrow
 * burns, and none leaves a cloud. A sword skeleton never bursts.
 *
 * <p>Everything asserted is read off the arrows, the target and the level, never off the goal's own state. The
 * request's numbers are literals here, so a retune of the goal edits this file on purpose.
 */
public class SkeletonVolleyGameTest {
	private static final double ANGLE_TOLERANCE_DEGREES = 0.05;
	/** The request's numbers as literals, so a test cannot inherit a typo from the code. */
	private static final int SPEC_ORDINARY_ARROWS = 3;
	private static final int SPEC_ELITE_ARROWS = 5;
	private static final int SPEC_BOSS_ARROWS = 7;
	private static final int SPEC_SHOT_GAP_TICKS = 10;
	private static final int SPEC_COOLDOWN_TICKS = 200;
	private static final int SPEC_SINGLE_PACE_TICKS = 40;
	private static final double SPEC_SPREAD_DEGREES = 3.0;
	private static final double SPEC_RANGE = 24.0;
	private static final double SPEC_FOLLOW_RANGE = 32.0;
	/** The target steps about 26 degrees of bearing, so half of that proves the arrow followed. */
	private static final double MIN_TURN_DEGREES = 13.0;
	/** Time for a cut burst's next arrows to show. */
	private static final int QUIET_TICKS = SPEC_ORDINARY_ARROWS * SPEC_SHOT_GAP_TICKS
		+ SkeletonVolleyGoal.DRAW_TICKS;
	private static final int REACH_SAMPLES = 10_000;
	private static final long REACH_SEED = 20_260_926L;
	private static final String QUIET = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";

	static {
		VolleyRecord.install();
	}

	@GameTest(maxTicks = 200)
	public void anOrdinaryBurstLoosesThreeArrowsTenTicksApart(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> firstBurst(helper, skeleton, SPEC_ORDINARY_ARROWS, "burstOrdinary"));
	}

	@GameTest(maxTicks = 200)
	public void anEliteBurstsFiveAndItsSingleShotSlowsHarder(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		Elite.mark(skeleton, GearTier.IRON, 1.0);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			firstBurst(helper, skeleton, SPEC_ELITE_ARROWS, "burstElite");
			helper.assertTrue(!VolleyRecord.slows(skeleton).isEmpty(), "the single shot has slowed nothing yet");
			int amplifier = VolleyRecord.slows(skeleton).get(0).getAmplifier();
			helper.assertTrue(amplifier == SkeletonVolley.ELITE_SLOW_AMPLIFIER, "the elite's shot slowed at amplifier " + amplifier);
			result("eliteSlow", "slowAmplifier=" + amplifier);
		});
	}

	@GameTest(maxTicks = 200)
	public void aBossBurstsSeven(final GameTestHelper helper) {
		Skeleton boss = archer(helper, new ItemStack(Items.BOW));
		Bosses.blessAsBoss(boss, helper.getLevel(), 1.0);
		helper.assertTrue(boss.isHolding(Items.BOW), "the boss kit left no bow, so the case proves nothing: " + boss.getMainHandItem());
		boss.setTarget(target(helper));

		helper.succeedWhen(() -> firstBurst(helper, boss, SPEC_BOSS_ARROWS, "burstBoss"));
	}

	@GameTest(maxTicks = 400)
	public void aSecondBurstStartsTwoHundredTicksAfterTheFirstEnds(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			List<List<VolleyRecord.Release>> bursts = VolleyRecord.bursts(skeleton);
			helper.assertTrue(bursts.size() >= 2, "only " + bursts.size() + " bursts so far");
			List<VolleyRecord.Release> first = bursts.get(0);
			List<VolleyRecord.Release> second = bursts.get(1);
			int firstEnd = last(first).serverTick();
			int secondEnd = last(second).serverTick();
			helper.assertTrue(
				VolleyRecord.shots(skeleton).stream().anyMatch(shot -> shot.serverTick() > secondEnd), "the second burst is not over yet"
			);

			int gap = second.get(0).serverTick() - firstEnd;
			helper.assertTrue(gap >= SPEC_COOLDOWN_TICKS, "the next burst came " + gap + " ticks after the last arrow");
			helper.assertTrue(
				gap <= SPEC_COOLDOWN_TICKS + SPEC_SINGLE_PACE_TICKS,
				"the next burst came " + gap + " ticks after the last arrow, so the bursts have stalled"
			);
			helper.assertTrue(second.size() == SPEC_ORDINARY_ARROWS, "the second burst held " + second.size() + " arrows");

			List<Integer> between = VolleyRecord.shots(skeleton).stream()
				.map(VolleyRecord.Release::serverTick)
				.filter(tick -> tick > firstEnd && tick < second.get(0).serverTick())
				.toList();
			helper.assertTrue(!between.isEmpty(), "no single shot came between the bursts");
			List<Integer> paces = new ArrayList<>();
			int before = firstEnd;
			for (int tick : between) {
				paces.add(tick - before);
				before = tick;
			}

			helper.assertTrue(
				paces.stream().allMatch(pace -> pace == SPEC_SINGLE_PACE_TICKS),
				"single shots came " + paces + " ticks apart, not " + SPEC_SINGLE_PACE_TICKS
			);
			result("burstCadence", "gap=" + gap + " cooldown=" + SPEC_COOLDOWN_TICKS + " singlePaces=" + paces);
		});
	}

	@GameTest(maxTicks = 200)
	public void aSingleShotSlowsForTwoSeconds(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			List<MobEffectInstance> slows = VolleyRecord.slows(skeleton);
			helper.assertTrue(!slows.isEmpty(), "no single shot has slowed anything yet");
			MobEffectInstance slow = slows.get(0);
			helper.assertTrue(slow.getDuration() == SkeletonVolley.SLOW_TICKS && slow.getAmplifier() == SkeletonVolley.SLOW_AMPLIFIER, "the shot slowed with " + slow);
			result("singleShotSlow", "ticks=" + slow.getDuration() + " amplifier=" + slow.getAmplifier());
		});
	}

	@GameTest(maxTicks = 200)
	public void eachBurstArrowAimsWhereTheTargetStandsAsItLeaves(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		Zombie zombie = zombieTarget(helper);
		skeleton.setTarget(zombie);
		boolean[] moved = new boolean[1];
		// Between the first and second arrow the target steps aside.
		helper.onEachTick(() -> {
			if (!moved[0] && VolleyRecord.burstArrows(skeleton).size() == 1) {
				Vec3 aside = helper.absoluteVec(new Vec3(6.5, 1.0, 7.5));
				zombie.snapTo(aside.x, aside.y, aside.z, 0.0F, 0.0F);
				moved[0] = true;
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(!VolleyRecord.shots(skeleton).isEmpty(), "no single shot yet, so the burst is not over");
			helper.assertTrue(!VolleyRecord.bursts(skeleton).isEmpty(), "no burst yet");
			List<VolleyRecord.Release> burst = VolleyRecord.bursts(skeleton).get(0);
			helper.assertTrue(burst.size() == SPEC_ORDINARY_ARROWS, "the burst loosed " + burst.size() + " arrows");
			List<Double> offsets = yawOffsets(burst);
			helper.assertTrue(
				offsets.stream().allMatch(yaw -> Math.abs(yaw) <= SPEC_SPREAD_DEGREES + ANGLE_TOLERANCE_DEGREES),
				"an arrow flew at where the target no longer stood: " + offsets + " degrees off its bearing"
			);
			double turn = Math.abs(Math.IEEEremainder(yawDegrees(burst.get(1).velocity()) - yawDegrees(burst.get(0).velocity()), 360.0));
			helper.assertTrue(turn > MIN_TURN_DEGREES, "the second arrow kept the first one's bearing, turned only " + turn + " degrees");
			result("burstFollowsTarget", "offsets=" + offsets + " turn=" + turn);
		});
	}

	@GameTest(maxTicks = 200)
	public void aBurstArrowHurtsButSlowsNothing(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		boolean[] hurt = new boolean[1];
		boolean[] slowed = new boolean[1];
		// Sampled before any single shot flies,
		// so a slow here came from a burst arrow.
		helper.onEachTick(() -> {
			if (VolleyRecord.shots(skeleton).isEmpty()) {
				hurt[0] |= golem.getHealth() < golem.getMaxHealth();
				slowed[0] |= golem.hasEffect(MobEffects.SLOWNESS);
			}
		});

		helper.succeedWhen(() -> {
			helper.assertTrue(!VolleyRecord.shots(skeleton).isEmpty(), "no single shot yet, so the burst is not over");
			helper.assertTrue(hurt[0], "the burst arrows never hurt the target");
			helper.assertTrue(!slowed[0], "a burst arrow slowed the target");
			result("burstNoSlow", "hurt=" + hurt[0] + " slowed=" + slowed[0]);
		});
	}

	@GameTest(maxTicks = 200)
	public void noSkeletonArrowBurns(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		Watch watch = watch(helper, skeleton, golem);

		helper.succeedWhen(() -> watch.assertNothingBurnedAfterAHit(helper, skeleton, "noBurnPlain"));
	}

	/** A boss's bow carries every enchantment a bow takes, Flame among them, so it is the case that can burn. */
	@GameTest(maxTicks = 200)
	public void noBossArrowBurnsEither(final GameTestHelper helper) {
		Skeleton boss = archer(helper, new ItemStack(Items.BOW));
		Bosses.blessAsBoss(boss, helper.getLevel(), 1.0);
		IronGolem golem = target(helper);
		boss.setTarget(golem);
		Watch watch = watch(helper, boss, golem);

		helper.succeedWhen(() -> watch.assertNothingBurnedAfterAHit(helper, boss, "noBurnBoss"));
	}

	@GameTest(maxTicks = 200)
	public void aBurstLeavesNoCloudWhereItLands(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		Watch watch = watch(helper, skeleton, golem);

		helper.succeedWhen(() -> watch.assertNoCloudAfterAHit(helper, skeleton, "noCloudPlain"));
	}

	@GameTest(maxTicks = 200)
	public void aTippedQuiverLeavesNoCloudEither(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 64);
		tipped.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, tipped);
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		Watch watch = watch(helper, skeleton, golem);

		helper.succeedWhen(() -> watch.assertNoCloudAfterAHit(helper, skeleton, "noCloudTipped"));
	}

	@GameTest(maxTicks = 200)
	public void everyReleaseSpendsOneAmmoItem(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		ItemStack quiver = new ItemStack(Items.ARROW, 64);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, quiver);
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			int loosed = VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size();
			helper.assertTrue(loosed > SPEC_ORDINARY_ARROWS, "only " + loosed + " arrows loosed, so no single shot has followed the burst");
			helper.assertTrue(quiver.getCount() == 64 - loosed, loosed + " arrows left " + quiver.getCount() + " of 64 in the quiver");
			result("ammo", "loosed=" + loosed + " left=" + quiver.getCount());
		});
	}

	@GameTest(maxTicks = 250)
	public void aBurstCutShortByALostTargetDoesNotResume(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		Zombie zombie = zombieTarget(helper);
		skeleton.setTarget(zombie);
		int[] phase = new int[1];
		long[] mark = new long[1];

		// The skeleton's goals never pick a zombie,
		// so a cleared target stays cleared.
		helper.onEachTick(() -> {
			if (phase[0] == 0 && VolleyRecord.burstArrows(skeleton).size() == 1) {
				skeleton.setTarget(null);
				mark[0] = helper.getTick();
				phase[0] = 1;
			} else if (phase[0] == 1 && helper.getTick() >= mark[0] + QUIET_TICKS) {
				int loosed = VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size();
				helper.assertTrue(loosed == 1, "the skeleton loosed " + loosed + " arrows in all, so the burst went on without a target");
				skeleton.setTarget(zombie);
				phase[0] = 2;
			} else if (phase[0] == 2 && VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size() > 1) {
				helper.assertTrue(
					VolleyRecord.burstArrows(skeleton).size() == 1 && VolleyRecord.shots(skeleton).size() == 1,
					"the cut burst came back: " + VolleyRecord.burstArrows(skeleton).size() + " burst arrows, " + VolleyRecord.shots(skeleton).size() + " singles"
				);
				result("burstLostTarget", "arrowsBeforeLoss=1 arrowsWhileLost=0 nextRelease=single");
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 250)
	public void aBurstStopsWhenItsTargetDies(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		Zombie zombie = zombieTarget(helper);
		skeleton.setTarget(zombie);
		boolean[] killed = new boolean[1];
		long[] mark = new long[1];

		helper.onEachTick(() -> {
			if (!killed[0] && VolleyRecord.burstArrows(skeleton).size() == 1) {
				zombie.kill(helper.getLevel());
				killed[0] = true;
				mark[0] = helper.getTick();
			} else if (killed[0] && helper.getTick() >= mark[0] + QUIET_TICKS) {
				int loosed = VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size();
				helper.assertTrue(!zombie.isAlive(), "the target is still alive, so the case proves nothing");
				helper.assertTrue(loosed == 1, "the skeleton loosed " + loosed + " arrows in all, so the burst went on at a dead target");
				result("burstDeadTarget", "arrowsBeforeDeath=1 arrowsAfter=0");
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 160)
	public void aSwordSkeletonNeverBursts(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.IRON_SWORD));
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		helper.assertTrue(BossAbilities.copiesOf(skeleton, SkeletonVolleyGoal.class) == 1, "the burst goal is not installed, so the case proves nothing");

		helper.runAfterDelay(120L, () -> {
			helper.assertTrue(skeleton.getTarget() == golem, "the skeleton lost its target, so the case proves nothing");
			int loosed = VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size();
			helper.assertTrue(loosed == 0, "a sword skeleton loosed " + loosed + " arrows");
			result("burstSwordNever", "arrows=0");
			helper.succeed();
		});
	}

	@GameTest
	public void everySkeletonSeesAsFarAsItShoots(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		double follow = skeleton.getAttributeValue(Attributes.FOLLOW_RANGE);
		result("followRange", "follow=" + follow + " range=" + SPEC_RANGE);
		helper.assertTrue(follow >= SPEC_FOLLOW_RANGE, "the follow range is " + follow);
		helper.assertTrue(follow >= SPEC_RANGE, "the follow range " + follow + " is shorter than the bow's " + SPEC_RANGE);
		helper.succeed();
	}

	@GameTest
	public void aBossSkeletonCarriesOneFollowRangeBonus(final GameTestHelper helper) {
		Skeleton boss = archer(helper, new ItemStack(Items.BOW));
		// Three installs reach the goal: spawn, boss
		// bless, and a second boss install.
		Bosses.blessAsBoss(boss, helper.getLevel(), 1.0);
		BossAbilities.install(boss);
		double follow = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
		int goals = BossAbilities.copiesOf(boss, SkeletonVolleyGoal.class);
		result("followRangeBoss", "follow=" + follow + " goals=" + goals);
		helper.assertTrue(goals == 1, "the boss carries " + goals + " burst goals");
		helper.assertTrue(follow == SPEC_FOLLOW_RANGE, "repeated installs left the follow range at " + follow);
		helper.succeed();
	}

	/**
	 * The rate a player meets the burst: the share of naturally rolled skeletons that end up with a bow.
	 * A skeleton the weapon roll skips keeps its vanilla bow, so the rate can never fall below that share.
	 */
	@GameTest(maxTicks = 100)
	public void burstReachRateIsMeasured(final GameTestHelper helper) {
		GearConfig shipped = GearConfigStore.defaults();
		Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(EntityTypes.SKELETON);
		double rollChance = shipped.rollFor(type).rollChance();
		double bossChance = shipped.rollFor(type).bossChance();
		GearConfig previous = GearConfigStore.get();
		RandomSource random = RandomSource.create(REACH_SEED);
		int bows = 0;
		try {
			GearConfigStore.set(shipped);
			for (int i = 0; i < REACH_SAMPLES; i++) {
				Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				GearRoller.roll(skeleton, shipped.rollFor(type), random, helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
				if (skeleton.getMainHandItem().is(Items.BOW)) {
					bows++;
				}
				skeleton.discard();
			}
		} finally {
			GearConfigStore.set(previous);
		}
		double bowRate = (double) bows / REACH_SAMPLES;
		double reachRate = bowRate * (1.0 - bossChance);
		result("burstReachRate", "samples=" + REACH_SAMPLES + " bowRate=" + bowRate + " bossChance=" + bossChance + " reachRate=" + reachRate + " floor=" + (1.0 - rollChance));
		helper.assertTrue(bowRate >= 1.0 - rollChance && bowRate < 1.0, "the bow rate " + bowRate + " is outside [" + (1.0 - rollChance) + ", 1)");
		helper.succeed();
	}

	/** Checks the first burst of a fight. */
	private static void firstBurst(final GameTestHelper helper, final Skeleton skeleton, final int arrows, final String name) {
		List<VolleyRecord.Release> singles = VolleyRecord.shots(skeleton);
		helper.assertTrue(!singles.isEmpty(), "no single shot has followed the burst yet, so the burst is not over");
		List<List<VolleyRecord.Release>> bursts = VolleyRecord.bursts(skeleton);
		helper.assertTrue(!bursts.isEmpty(), "no burst yet");
		List<VolleyRecord.Release> burst = bursts.get(0);
		helper.assertTrue(burst.size() == arrows, "the burst loosed " + burst.size() + " arrows, not " + arrows);

		List<Integer> gaps = new ArrayList<>();
		for (int i = 1; i < burst.size(); i++) {
			gaps.add(burst.get(i).serverTick() - burst.get(i - 1).serverTick());
		}

		helper.assertTrue(
			gaps.stream().allMatch(gap -> gap == SPEC_SHOT_GAP_TICKS),
			"burst arrows came " + gaps + " ticks apart, not " + SPEC_SHOT_GAP_TICKS
		);
		helper.assertTrue(burst.get(0).serverTick() < singles.get(0).serverTick(), "the first release of the fight was a single shot");

		List<Double> yaws = yawOffsets(burst);
		double widest = SPEC_SPREAD_DEGREES + ANGLE_TOLERANCE_DEGREES;
		helper.assertTrue(yaws.stream().allMatch(yaw -> Math.abs(yaw) <= widest), "burst arrows strayed " + yaws + " degrees in yaw, beyond " + SPEC_SPREAD_DEGREES);
		List<Double> pitches = burst.stream().map(arrow -> pitchDegrees(arrow.velocity())).toList();
		double pitchSpan = pitches.stream().mapToDouble(Double::doubleValue).max().orElse(0.0) - pitches.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
		helper.assertTrue(pitchSpan <= 2.0 * widest, "burst arrows spanned " + pitchSpan + " degrees in pitch, beyond " + 2.0 * SPEC_SPREAD_DEGREES);
		result(name, "arrows=" + burst.size() + " gaps=" + gaps + " yawOffsets=" + yaws + " pitchSpan=" + pitchSpan);
	}

	/** What a fight left, sampled each tick. */
	private static final class Watch {
		private final GameTestHelper helper;
		private final Set<UUID> burning = ConcurrentHashMap.newKeySet();
		private volatile boolean targetAlight;
		private volatile boolean hurt;
		private volatile int clouds;

		private Watch(final GameTestHelper helper) {
			this.helper = helper;
		}

		void assertNothingBurnedAfterAHit(final GameTestHelper test, final Skeleton skeleton, final String name) {
			test.assertTrue(!VolleyRecord.shots(skeleton).isEmpty(), "no single shot yet, so the burst is not over");
			test.assertTrue(this.hurt, "no arrow hurt the target, so the case proves nothing");
			test.assertTrue(this.burning.isEmpty(), this.burning.size() + " skeleton arrows burned");
			test.assertTrue(!this.targetAlight, "the target was set alight");
			result(name, "arrows=" + (VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size()) + " burning=0 targetAlight=false");
		}

		void assertNoCloudAfterAHit(final GameTestHelper test, final Skeleton skeleton, final String name) {
			test.assertTrue(!VolleyRecord.shots(skeleton).isEmpty(), "no single shot yet, so the burst is not over");
			test.assertTrue(this.hurt, "no arrow hurt the target, so the case proves nothing");
			test.assertTrue(this.clouds == 0, "a skeleton arrow left " + this.clouds + " clouds");
			result(name, "arrows=" + (VolleyRecord.burstArrows(skeleton).size() + VolleyRecord.shots(skeleton).size()) + " clouds=0");
		}

		private void sample(final Skeleton skeleton, final LivingEntity target) {
			Stream.concat(VolleyRecord.burstArrows(skeleton).stream(), VolleyRecord.shots(skeleton).stream()).forEach(release -> {
				Entity arrow = this.helper.getLevel().getEntity(release.arrow());
				if (arrow != null && arrow.isOnFire()) {
					this.burning.add(release.arrow());
				}
			});
			this.targetAlight |= target.isOnFire();
			this.hurt |= target.getHealth() < target.getMaxHealth();
			// Only this arena: a neighbour is five
			// blocks past the wall.
			int here = this.helper.getLevel().getEntitiesOfClass(AreaEffectCloud.class, this.helper.getBounds().inflate(1.0)).size();
			this.clouds = Math.max(this.clouds, here);
		}
	}

	private static Watch watch(final GameTestHelper helper, final Skeleton skeleton, final LivingEntity target) {
		Watch watch = new Watch(helper);
		helper.onEachTick(() -> watch.sample(skeleton, target));
		return watch;
	}

	/** Each arrow's yaw against the bearing to the target as that arrow left the bow. */
	private static List<Double> yawOffsets(final List<VolleyRecord.Release> arrows) {
		return arrows.stream().map(arrow -> Math.IEEEremainder(yawDegrees(arrow.velocity()) - arrow.lineYaw(), 360.0)).toList();
	}

	private static double yawDegrees(final Vec3 velocity) {
		return Math.toDegrees(Math.atan2(velocity.z, velocity.x));
	}

	private static double pitchDegrees(final Vec3 velocity) {
		return Math.toDegrees(Math.atan2(velocity.y, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)));
	}

	private static VolleyRecord.Release last(final List<VolleyRecord.Release> burst) {
		return burst.get(burst.size() - 1);
	}

	private static Skeleton archer(final GameTestHelper helper, final ItemStack weapon) {
		prepare(helper);
		Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.0, 1.5));
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		// Tagged out of the spawn rolls, so the weapon in its hand is exactly the one the test gives it.
		skeleton.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		skeleton.setPersistenceRequired();
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	private static IronGolem target(final GameTestHelper helper) {
		IronGolem golem = helper.spawnMob(EntityTypes.IRON_GOLEM, 3, 3, 7).withNoFreeWill().spawn();
		golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		golem.setHealth(1000.0F);
		golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		golem.setPersistenceRequired();
		((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) golem).removeAttached(com.stella.mobs.behavior.EliteState.TYPE);
		return golem;
	}

	/** A target the skeleton's goals never pick. */
	private static Zombie zombieTarget(final GameTestHelper helper) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new Vec3(3.5, 1.0, 7.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setPersistenceRequired();
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=skeletonVolley.{} RESULT {}", test, detail);
	}
}
