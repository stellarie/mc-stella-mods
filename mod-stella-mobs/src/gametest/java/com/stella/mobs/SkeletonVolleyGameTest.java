package com.stella.mobs;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.behavior.BossAbilities;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.SkeletonVolley;
import com.stella.mobs.behavior.SkeletonVolleyGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.gear.GearRoller;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * A skeleton archer's single shot slows what it hits for two seconds. Every six seconds it looses a volley
 * instead: a narrow fan of flaming arrows, six from a plain skeleton, twelve from an elite and eighteen from
 * a boss, each leaving a cloud where it lands. A sword skeleton never volleys.
 */
public class SkeletonVolleyGameTest {
	private static final double YAW_TOLERANCE_DEGREES = 0.5;
	private static final int CADENCE_SAMPLE_VOLLEYS = 3;
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
	public void aVolleyLoosesSixFlamingArrowsInANarrowFan(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			List<VolleyRecord.Volley> volleys = VolleyRecord.volleys(skeleton);
			helper.assertTrue(!volleys.isEmpty(), "no volley yet");
			VolleyRecord.Volley volley = volleys.get(0);
			int count = SkeletonVolleyGoal.ARROWS_PER_VOLLEY;
			helper.assertTrue(volley.arrows().size() == count, "the volley loosed " + volley.arrows().size() + " arrows");
			helper.assertTrue(volley.burning() == count, "only " + volley.burning() + " of the volley's arrows were alight");
			List<Double> offsets = offsets(volley.velocities());
			double spacing = SkeletonVolleyGoal.spacingDegrees(count);
			for (int i = 0; i < count; i++) {
				double expected = (i - count / 2) * spacing;
				helper.assertTrue(Math.abs(offsets.get(i) - expected) <= YAW_TOLERANCE_DEGREES, "arrow yaws sat " + offsets + " from the middle arrow, " + spacing + " degrees apart expected");
			}
			result("volleySpread", "arrows=" + volley.arrows().size() + " burning=" + volley.burning() + " offsets=" + offsets);
		});
	}

	@GameTest(maxTicks = 360)
	public void volleysComeEverySixSeconds(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			List<VolleyRecord.Volley> volleys = VolleyRecord.volleys(skeleton);
			helper.assertTrue(volleys.size() >= CADENCE_SAMPLE_VOLLEYS, "only " + volleys.size() + " volleys so far");
			List<Integer> gaps = new ArrayList<>();
			for (int i = 1; i < volleys.size(); i++) {
				gaps.add(volleys.get(i).serverTick() - volleys.get(i - 1).serverTick());
			}
			boolean steady = gaps.stream().allMatch(gap -> Math.abs(gap - SkeletonVolleyGoal.VOLLEY_COOLDOWN_TICKS) <= 1);
			helper.assertTrue(steady, "volleys came " + gaps + " ticks apart, not " + SkeletonVolleyGoal.VOLLEY_COOLDOWN_TICKS);
			helper.assertTrue(VolleyRecord.shots(skeleton) >= 2, "no single shots came between the volleys");
			result("volleyCadence", "gaps=" + gaps + " cooldown=" + SkeletonVolleyGoal.VOLLEY_COOLDOWN_TICKS + " singles=" + VolleyRecord.shots(skeleton));
		});
	}

	@GameTest(maxTicks = 200)
	public void aSingleShotSlowsForTwoSeconds(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		// Poison-tipped, so the volley's clouds carry poison and the only slowness the target can carry is the shot's.
		ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 64);
		tipped.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, tipped);
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
	public void anEliteVolleysTwelveAndSlowsHarder(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		Elite.mark(skeleton, GearTier.IRON, 1.0);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			List<VolleyRecord.Volley> volleys = VolleyRecord.volleys(skeleton);
			helper.assertTrue(!volleys.isEmpty() && !VolleyRecord.slows(skeleton).isEmpty(), "no volley and single shot yet");
			helper.assertTrue(volleys.get(0).arrows().size() == SkeletonVolleyGoal.ELITE_ARROWS_PER_VOLLEY, "the elite's volley held " + volleys.get(0).arrows().size());
			int amplifier = VolleyRecord.slows(skeleton).get(0).getAmplifier();
			helper.assertTrue(amplifier == SkeletonVolley.ELITE_SLOW_AMPLIFIER, "the elite's shot slowed at amplifier " + amplifier);
			result("eliteVolley", "arrows=" + volleys.get(0).arrows().size() + " slowAmplifier=" + amplifier);
		});
	}

	@GameTest(maxTicks = 200)
	public void volleyArrowLeavesASlowingCloudWhereItLands(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			VolleyRecord.Cloud cloud = ownCloud(helper, skeleton);
			helper.assertTrue(has(cloud, MobEffects.SLOWNESS), "a plain arrow's cloud carried " + names(cloud.effects()));
			result("volleyCloudPlain", "at=" + cloud.at() + " effects=" + names(cloud.effects()));
		});
	}

	@GameTest(maxTicks = 200)
	public void aTippedVolleyLeavesItsOwnPotionCloud(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.BOW));
		ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 64);
		tipped.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, tipped);
		skeleton.setTarget(target(helper));

		helper.succeedWhen(() -> {
			VolleyRecord.Cloud cloud = ownCloud(helper, skeleton);
			helper.assertTrue(has(cloud, MobEffects.POISON) && !has(cloud, MobEffects.SLOWNESS), "a poison arrow's cloud carried " + names(cloud.effects()));
			result("volleyCloudTipped", "at=" + cloud.at() + " effects=" + names(cloud.effects()));
		});
	}

	@GameTest(maxTicks = 100)
	public void aBossSkeletonVolleysEighteen(final GameTestHelper helper) {
		prepare(helper);
		Skeleton boss = helper.spawnMob(EntityTypes.SKELETON, 3, 3, 3).spawnReason(EntitySpawnReason.NATURAL).spawn();
		boss.setPersistenceRequired();
		Bosses.blessAsBoss(boss, helper.getLevel(), 1.0);
		int volleys = BossAbilities.copiesOf(boss, SkeletonVolleyGoal.class);
		int arrows = SkeletonVolleyGoal.arrowsFor(boss);
		helper.assertTrue(volleys == 1 && arrows == SkeletonVolleyGoal.BOSS_ARROWS_PER_VOLLEY, "a boss skeleton carries " + volleys + " volley goals of " + arrows + " arrows");
		result("volleyBoss", "volleyGoals=" + volleys + " arrows=" + arrows);
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void swordSkeletonNeverVolleys(final GameTestHelper helper) {
		Skeleton skeleton = archer(helper, new ItemStack(Items.IRON_SWORD));
		IronGolem golem = target(helper);
		skeleton.setTarget(golem);
		helper.assertTrue(BossAbilities.copiesOf(skeleton, SkeletonVolleyGoal.class) == 1, "the volley goal is not installed, so the case proves nothing");

		helper.runAfterDelay(120L, () -> {
			helper.assertTrue(skeleton.getTarget() == golem, "the skeleton lost its target, so the case proves nothing");
			helper.assertTrue(VolleyRecord.volleys(skeleton).isEmpty(), "a sword skeleton loosed " + VolleyRecord.volleys(skeleton).size() + " volleys");
			result("volleySwordNever", "volleys=0");
			helper.succeed();
		});
	}

	/**
	 * The rate a player meets the volley: the share of naturally rolled skeletons that end up with a bow.
	 * A skeleton the weapon roll skips keeps its vanilla bow, so the rate can never fall below that share.
	 */
	@GameTest(maxTicks = 100)
	public void volleyReachRateIsMeasured(final GameTestHelper helper) {
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
		result("volleyReachRate", "samples=" + REACH_SAMPLES + " bowRate=" + bowRate + " bossChance=" + bossChance + " reachRate=" + reachRate + " floor=" + (1.0 - rollChance));
		helper.assertTrue(bowRate >= 1.0 - rollChance && bowRate < 1.0, "the bow rate " + bowRate + " is outside [" + (1.0 - rollChance) + ", 1)");
		helper.succeed();
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
		return golem;
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
	}

	/** The first cloud this skeleton made, checked against its own volley record and the live entity. */
	private static VolleyRecord.Cloud ownCloud(final GameTestHelper helper, final Skeleton skeleton) {
		List<VolleyRecord.Cloud> clouds = VolleyRecord.clouds(skeleton);
		helper.assertTrue(!clouds.isEmpty(), "no cloud yet; volleys " + VolleyRecord.volleys(skeleton).size());
		Set<UUID> loosed = VolleyRecord.volleys(skeleton).stream().flatMap(volley -> volley.arrows().stream()).collect(Collectors.toSet());
		VolleyRecord.Cloud cloud = clouds.get(0);
		helper.assertTrue(loosed.contains(cloud.arrow()), "the cloud came from an arrow the volley never loosed");
		Entity entity = helper.getLevel().getEntity(cloud.cloud());
		helper.assertTrue(entity instanceof AreaEffectCloud, "the recorded cloud is not in the level");
		AreaEffectCloud live = (AreaEffectCloud) entity;
		helper.assertTrue(live.position().distanceTo(cloud.at()) < 1.0e-6 && live.getRadius() == SkeletonVolley.CLOUD_RADIUS, "the cloud sits at " + live.position() + " with radius " + live.getRadius());
		return cloud;
	}

	private static boolean has(final VolleyRecord.Cloud cloud, final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
		return cloud.effects().stream().anyMatch(instance -> instance.getEffect().equals(effect));
	}

	private static List<String> names(final List<MobEffectInstance> effects) {
		return effects.stream().map(instance -> instance.getEffect().getRegisteredName()).toList();
	}

	// Each arrow's horizontal yaw against the middle arrow's, in degrees, in loosing order.
	private static List<Double> offsets(final List<Vec3> velocities) {
		double centre = Math.toDegrees(Math.atan2(velocities.get(velocities.size() / 2).z, velocities.get(velocities.size() / 2).x));
		List<Double> offsets = new ArrayList<>();
		for (Vec3 velocity : velocities) {
			double yaw = Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - centre;
			offsets.add(Math.IEEEremainder(yaw, 360.0));
		}
		return offsets;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=skeletonVolley.{} RESULT {}", test, detail);
	}
}
