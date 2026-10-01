package com.stella.mobs;

import com.stella.mobs.behavior.ArcherKit;
import com.stella.mobs.behavior.CreeperEscapeController;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.behavior.SkeletonVolley;
import com.stella.mobs.behavior.StrayKit;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.mixin.ArrowPotionInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The archer kit looses the arrows the mobs mod already looses, for another mod to call. A skeleton's single shot
 * and burst arrow match its goal, a stray's and a bogged's arrow carry what vanilla gives it, the arrow flies where
 * it is told, and a mob tagged as model driven is left alone by the creeper escape.
 */
public class ArcherKitGameTest {
	private static final float POWER = 1.6F;
	private static final double DIRECTION_TOLERANCE_DEGREES = 1.0;
	private static final double ANGLE_TOLERANCE_DEGREES = 0.01;
	private static final int SPREAD_SAMPLES = 100;
	private static final int SPEC_BURST_ARROWS = 3;
	private static final double SPEC_SPREAD_DEGREES = 3.0;
	private static final Vec3 AIM = new Vec3(1.0, 0.2, 2.0);

	@GameTest
	public void skeletonSingleMatchesTheGoal(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		List<AbstractArrow> arrows = arrows(helper, ArcherKit.release(skeleton, AIM, POWER, 0.0F, false));
		boolean slow = arrows.size() == 1 && arrows.get(0).entityTags().contains(SkeletonVolley.SLOW_TAG);
		long burning = arrows.stream().filter(Entity::isOnFire).count();
		result("skeletonSingle", "arrows=" + arrows.size() + " slowTag=" + slow + " burning=" + burning);
		helper.assertTrue(slow, "the single shot loosed " + arrows.size() + " arrows, slow tag " + slow);
		helper.assertTrue(burning == 0, "the single shot was alight");
		helper.succeed();
	}

	@GameTest
	public void skeletonBurstArrowMatchesTheGoal(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		List<AbstractArrow> arrows = arrows(helper, ArcherKit.release(skeleton, AIM, POWER, 0.0F, true));
		boolean slow = arrows.stream().anyMatch(arrow -> arrow.entityTags().contains(SkeletonVolley.SLOW_TAG));
		long burning = arrows.stream().filter(Entity::isOnFire).count();
		result("skeletonBurstArrow", "arrows=" + arrows.size() + " slowTag=" + slow + " burning=" + burning);
		helper.assertTrue(arrows.size() == 1, "a burst release loosed " + arrows.size() + " arrows, not one");
		helper.assertTrue(!slow, "a burst arrow carried the single shot's slow");
		helper.assertTrue(burning == 0, "a burst arrow was alight");
		helper.succeed();
	}

	@GameTest
	public void burstArrowsStrayWithinTheSpreadAndNotAllTheSame(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		List<Double> yaws = new ArrayList<>();
		List<Double> pitches = new ArrayList<>();
		for (int i = 0; i < SPREAD_SAMPLES; i++) {
			AbstractArrow arrow = arrows(helper, ArcherKit.release(skeleton, AIM, POWER, 0.0F, true)).get(0);
			Vec3 velocity = arrow.getDeltaMovement();
			yaws.add(Math.IEEEremainder(yawDegrees(velocity) - yawDegrees(AIM), 360.0));
			pitches.add(pitchDegrees(velocity) - pitchDegrees(AIM));
			arrow.discard();
		}

		double spread = SPEC_SPREAD_DEGREES;
		double widestYaw = yaws.stream().mapToDouble(Math::abs).max().orElse(0.0);
		double widestPitch = pitches.stream().mapToDouble(Math::abs).max().orElse(0.0);
		result("burstSpread", "samples=" + SPREAD_SAMPLES + " widestYaw=" + widestYaw + " widestPitch=" + widestPitch + " spread=" + spread);
		helper.assertTrue(widestYaw <= spread + ANGLE_TOLERANCE_DEGREES, "an arrow strayed " + widestYaw + " degrees in yaw");
		helper.assertTrue(widestPitch <= spread + ANGLE_TOLERANCE_DEGREES, "an arrow strayed " + widestPitch + " degrees in pitch");
		// Half the spread, on both sides:
		// a fixed or one sided offset fails.
		helper.assertTrue(
			widestYaw >= spread / 2.0 && yaws.stream().anyMatch(yaw -> yaw > 0.0) && yaws.stream().anyMatch(yaw -> yaw < 0.0),
			"the yaw never strayed: " + widestYaw
		);
		helper.assertTrue(
			widestPitch >= spread / 2.0 && pitches.stream().anyMatch(pitch -> pitch > 0.0) && pitches.stream().anyMatch(pitch -> pitch < 0.0),
			"the pitch never strayed: " + widestPitch
		);
		helper.succeed();
	}

	@GameTest
	public void strayArrowCarriesSlowness(final GameTestHelper helper) {
		helper.assertTrue(effectOf(helper, EntityTypes.STRAY, MobEffects.SLOWNESS), "the stray's arrow carries no slowness");
		helper.succeed();
	}

	@GameTest
	public void boggedArrowCarriesPoison(final GameTestHelper helper) {
		helper.assertTrue(effectOf(helper, EntityTypes.BOGGED, MobEffects.POISON), "the bogged's arrow carries no poison");
		helper.succeed();
	}

	@GameTest
	public void arrowFliesAlongTheGivenDirection(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		List<AbstractArrow> arrows = arrows(helper, ArcherKit.release(skeleton, AIM, POWER, 0.0F, false));
		helper.assertTrue(arrows.size() == 1, "the release loosed " + arrows.size() + " arrows");
		Vec3 velocity = arrows.get(0).getDeltaMovement();
		double degrees = Math.toDegrees(Math.acos(velocity.normalize().dot(AIM.normalize())));
		result("directionError", "degrees=" + degrees + " velocity=" + velocity);
		helper.assertTrue(degrees <= DIRECTION_TOLERANCE_DEGREES, "the arrow flew " + degrees + " degrees off the given direction");
		helper.succeed();
	}

	@GameTest
	public void eligibility(final GameTestHelper helper) {
		boolean skeleton = ArcherKit.isKitArcher(archer(helper, EntityTypes.SKELETON));
		boolean stray = ArcherKit.isKitArcher(archer(helper, EntityTypes.STRAY));
		boolean bogged = ArcherKit.isKitArcher(archer(helper, EntityTypes.BOGGED));
		Mob sword = archer(helper, EntityTypes.SKELETON);
		sword.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Mob shielded = archer(helper, EntityTypes.SKELETON);
		shielded.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		Mob sniper = archer(helper, EntityTypes.STRAY);
		Elite.mark(sniper, GearTier.IRON, 1.0);
		StrayKit.install(sniper);
		boolean swordEligible = ArcherKit.isKitArcher(sword);
		boolean shieldedEligible = ArcherKit.isKitArcher(shielded);
		boolean sniperEligible = ArcherKit.isKitArcher(sniper);
		String seen = "skeleton=" + skeleton + " stray=" + stray + " bogged=" + bogged + " sword=" + swordEligible
			+ " shielded=" + shieldedEligible + " sniper=" + sniperEligible;
		result("eligibility", seen);
		helper.assertTrue(skeleton && stray && bogged, "a bow archer was refused: " + seen);
		helper.assertTrue(!swordEligible && !shieldedEligible && !sniperEligible, "an ineligible mob was accepted: " + seen);
		helper.succeed();
	}

	@GameTest
	public void modelDrivenTagGatesCreeperEscape(final GameTestHelper helper) {
		Mob tagged = archer(helper, EntityTypes.SKELETON);
		tagged.addTag(StellaMobs.MODEL_DRIVEN_TAG);
		Mob plain = archer(helper, EntityTypes.SKELETON);
		primedCreeper(helper);
		CreeperEscapeController.EscapeDecision decision = CreeperEscapeController.apply(tagged);
		String gate = ReactiveState.get(tagged).getR02SkipReason();
		CreeperEscapeController.apply(plain);
		String plainGate = ReactiveState.get(plain).getR02SkipReason();
		result("modelDrivenGate", "decision=" + decision + " gate=" + gate + " untaggedGate=" + plainGate);
		helper.assertTrue(decision == CreeperEscapeController.EscapeDecision.STATE_GATED && "modelDriven".equals(gate), "the tagged skeleton got " + decision + " with gate " + gate);
		helper.assertTrue(!"modelDriven".equals(plainGate), "an untagged skeleton was gated as model driven");
		helper.succeed();
	}

	@GameTest
	public void releaseUsesOneAmmoItemPerArrow(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		ItemStack ammo = new ItemStack(Items.ARROW, 64);
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, ammo);
		int loosed = 0;
		for (int i = 0; i < SPEC_BURST_ARROWS; i++) {
			loosed += ArcherKit.release(skeleton, AIM, POWER, 0.0F, true).size();
		}

		loosed += ArcherKit.release(skeleton, AIM, POWER, 0.0F, false).size();
		result("ammo", "loosed=" + loosed + " left=" + ammo.getCount());
		helper.assertTrue(
			loosed == SPEC_BURST_ARROWS + 1 && ammo.getCount() == 64 - loosed,
			"a burst and a single shot loosed " + loosed + " arrows and left " + ammo.getCount() + " of 64"
		);
		helper.succeed();
	}

	@GameTest
	public void releaseLogsALine(final GameTestHelper helper) {
		Mob skeleton = archer(helper, EntityTypes.SKELETON);
		List<String> lines = new CopyOnWriteArrayList<>();
		ArcherKit.setLineTap(lines::add);
		try {
			ArcherKit.release(skeleton, AIM, POWER, 0.0F, true);
		} finally {
			ArcherKit.setLineTap(null);
		}

		String mine = skeleton.getUUID().toString();
		List<String> releases = lines.stream().filter(line -> line.contains("test=archerKit RELEASE") && line.contains(mine)).toList();
		result("releaseLine", "lines=" + releases);
		helper.assertTrue(releases.size() == 1, "the release wrote " + releases.size() + " RELEASE lines");
		String line = releases.get(0);
		helper.assertTrue(line.contains("type=minecraft:skeleton") && line.contains("arrows=1") && line.contains("burst=true") && line.contains("power=1.60") && line.contains("uncertainty=0.00"), "the line lacks its values: " + line);
		helper.succeed();
	}

	@GameTest
	public void aNonArcherReleaseLogsItsReason(final GameTestHelper helper) {
		Mob zombie = archer(helper, EntityTypes.ZOMBIE);
		List<String> lines = new CopyOnWriteArrayList<>();
		ArcherKit.setLineTap(lines::add);
		List<UUID> arrows;
		try {
			arrows = ArcherKit.release(zombie, AIM, POWER, 0.0F, false);
		} finally {
			ArcherKit.setLineTap(null);
		}

		result("unsupportedRelease", "arrows=" + arrows.size() + " lines=" + lines);
		helper.assertTrue(arrows.isEmpty() && lines.size() == 1 && lines.get(0).contains("SKIP") && lines.get(0).contains("reason=unsupportedType"), "the refused release wrote " + lines);
		helper.succeed();
	}

	private static boolean effectOf(final GameTestHelper helper, final EntityType<? extends Mob> type, final Holder<MobEffect> effect) {
		Mob archer = archer(helper, type);
		List<AbstractArrow> arrows = arrows(helper, ArcherKit.release(archer, AIM, POWER, 0.0F, false));
		helper.assertTrue(arrows.size() == 1 && arrows.get(0) instanceof Arrow, "the release loosed " + arrows.size() + " arrows");
		boolean carried = false;
		for (MobEffectInstance instance : ((ArrowPotionInvoker) arrows.get(0)).stella$potionContents().getAllEffects()) {
			carried |= instance.getEffect().equals(effect);
		}
		result("vanillaEffect", "type=" + EntityType.getKey(type) + " effect=" + effect.getRegisteredName() + " carried=" + carried);
		return carried;
	}

	private static Mob archer(final GameTestHelper helper, final EntityType<? extends Mob> type) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 3.0, 1.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		// Tagged out of the spawn rolls, so the weapon in its hand is exactly the one the test gives it.
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static void primedCreeper(final GameTestHelper helper) {
		Creeper creeper = helper.spawnMob(EntityTypes.CREEPER, 3, 3, 3).withNoFreeWill().spawn();
		creeper.ignite();
	}

	private static List<AbstractArrow> arrows(final GameTestHelper helper, final List<UUID> ids) {
		return ids.stream().map(id -> (AbstractArrow) helper.getLevel().getEntity(id)).toList();
	}

	private static double yawDegrees(final Vec3 direction) {
		return Math.toDegrees(Math.atan2(direction.z, direction.x));
	}

	private static double pitchDegrees(final Vec3 direction) {
		return Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)));
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=archerKit.{} RESULT {}", test, detail);
	}
}
