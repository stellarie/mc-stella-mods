package com.stella.spells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arrow Shower looses real arrows: 64 over 80 ticks, 128 when awakened, for 4 arrows of the caster. They leave
 * from behind the caster on both sides and fly a low arc onto a 3 by 3 block area around the aim, a wall stops
 * them, each one deals the caster's average ranged roll and slows its foe, with the arrow's own effects and the
 * bow's enchantments, and they spare the caster and its allies.
 */
public class ArrowShowerGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.arrowshower");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	/** Where a caster stands to cast: its volley leaves up to 2 blocks behind it, so it keeps clear of the back wall. */
	private static final double CAST_X = 4.5;
	private static final double CAST_Z = 3.5;
	private static final int WEAPON_SLOT = 35;
	private static final int PACK_SLOT = 20;
	/** Where the shower aims, and the middle of the 8 by 8 arena. */
	private static final double AIM_X = 4.5;
	private static final double AIM_Z = 6.0;
	/** The area is 3 blocks wide, so an arrow lands within 1.5 blocks of the aim on each axis. */
	private static final double AREA_HALF_WIDTH = 1.5;
	/** A stuck arrow sits this far behind the point where it hit the ground. */
	private static final double ARROW_EMBED = 0.05;
	/** The last arrow leaves at tick 80 and a flat flight ends within a few ticks; the rest is margin. */
	private static final long LANDED_TICKS = ArcherSkills.ARROW_SHOWER_TICKS + 100L;
	private static final long SEED = 4242L;
	/** The speed of an arrow from a fully drawn bow, the crawl of a very slow lob, and the Power level of the case. */
	private static final double FULL_DRAW_SPEED = 3.0;
	private static final double SLOW_LOB = 1.0;
	private static final int POWER_LEVEL = 3;
	/** What a full-draw arrow deals with no Power: speed 3.0 times a base damage of 2.0. */
	private static final double FULL_DRAW_PLAIN_DAMAGE = 6.0;
	/** The ranged roll the stub RPG mod gives, in health points: the low end, the high end and the average. */
	private static final float LOW_ROLL = 5.5F;
	private static final float HIGH_ROLL = 8.5F;
	private static final float AVERAGE_ROLL = (LOW_ROLL + HIGH_ROLL) / 2.0F;
	/** The corridor of the low ceiling case: the ceiling sits 4 blocks over the floor, the foe 12 blocks from the caster. */
	private static final int CORRIDOR_CEILING = 5;
	// The test cells sit 17 blocks apart, so the corridor stays between the ring of one cell and the next.
	private static final int CORRIDOR_START = -7;
	private static final int CORRIDOR_END = 10;
	private static final double CORRIDOR_RANGE = 12.0;
	private static final double CORRIDOR_FOE_Z = 7.5;
	/** A wall across the arena, between the caster and the foe. */
	private static final int WALL_Z = 4;
	private static final int WALL_TOP = 6;

	@GameTest(maxTicks = 120)
	public void arrowShowerLoosesSixtyFourArrowsInEightyTicksAndSpendsFour(final GameTestHelper helper) {
		showerCase(helper, false, ArcherSkills.ARROW_SHOWER_ARROWS, "arrowShowerLoosesSixtyFourArrowsInEightyTicksAndSpendsFour");
	}

	@GameTest(maxTicks = 120)
	public void anAwakenedArrowShowerLoosesOneHundredTwentyEightArrowsInTheSameEightyTicks(final GameTestHelper helper) {
		showerCase(helper, true, ArcherSkills.AWAKENED_ARROW_SHOWER_ARROWS, "anAwakenedArrowShowerLoosesOneHundredTwentyEightArrowsInTheSameEightyTicks");
	}

	@GameTest(maxTicks = 60)
	public void arrowShowerWithThreeArrowsIsRefusedAndSpendsNothing(final GameTestHelper helper) {
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, ArcherSkills.ARROW_SHOWER_COST - 1));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground(helper), SkillSchoolGameTest.modifiers(caster, false));
		helper.runAfterDelay(10L, () -> {
			int left = caster.getInventory().getItem(PACK_SLOT).getCount();
			int loosed = ArcherSkills.showerArrows(caster).size();
			LOG.info("[BEHAVIOR] test=arrowShowerWithThreeArrowsIsRefusedAndSpendsNothing RESULT applied={} left={} loosed={}", applied, left, loosed);
			helper.assertFalse(applied, "a shower started on " + (ArcherSkills.ARROW_SHOWER_COST - 1) + " arrows");
			helper.assertTrue(left == ArcherSkills.ARROW_SHOWER_COST - 1, "the refused shower spent arrows: " + left + " left");
			helper.assertTrue(loosed == 0, "the refused shower loosed " + loosed + " arrows");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void arrowShowerSpendsTheQuiverBeforeThePackAndLoosesTheKindItSpent(final GameTestHelper helper) {
		Item fire = ArrowItems.BY_KIND.get(ArrowKind.FIRE);
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 6));
		int fromQuiver = 3;
		caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(fire, fromQuiver)));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground(helper), SkillSchoolGameTest.modifiers(caster, false));
		helper.runAfterDelay(ArcherSkills.ARROW_SHOWER_TICKS + 2L, () -> {
			int quiverLeft = Quiver.count(Quiver.held(caster), fire);
			int packLeft = caster.getInventory().getItem(PACK_SLOT).getCount();
			int fireArrows = 0;
			int plainArrows = 0;
			for (UUID id : ArcherSkills.showerArrows(caster)) {
				Entity arrow = helper.getLevel().getEntity(id);
				if (arrow != null && ArrowHits.kindOf(arrow) == ArrowKind.FIRE) {
					fireArrows++;
				} else if (arrow != null) {
					plainArrows++;
				}
			}
			int total = ArcherSkills.ARROW_SHOWER_ARROWS;
			int fireWant = total / ArcherSkills.ARROW_SHOWER_COST * fromQuiver;
			LOG.info("[BEHAVIOR] test=arrowShowerSpendsTheQuiverBeforeThePackAndLoosesTheKindItSpent RESULT applied={} quiverLeft={} packLeft={} fire={} plain={}",
				applied, quiverLeft, packLeft, fireArrows, plainArrows);
			helper.assertTrue(applied, "the shower never started");
			helper.assertTrue(quiverLeft == 0, "the quiver kept " + quiverLeft + " arrows");
			helper.assertTrue(packLeft == 6 - (ArcherSkills.ARROW_SHOWER_COST - fromQuiver), "the pack kept " + packLeft + " arrows");
			helper.assertTrue(fireArrows == fireWant, fireArrows + " arrows carried the spent fire kind, not " + fireWant);
			helper.assertTrue(plainArrows == total - fireWant, plainArrows + " arrows were plain, not " + (total - fireWant));
			finish(helper, caster);
		});
	}

	// Each arrow is followed on every tick, so its last position is where it came to rest even after the shower
	// takes it away.
	@GameTest(maxTicks = 260)
	public void everyShowerArrowLandsInsideTheThreeByThreeAreaAroundTheAim(final GameTestHelper helper) {
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 20));
		Vec3 point = ground(helper);
		boolean applied = ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		Map<UUID, Vec3> resting = new HashMap<>();
		helper.onEachTick(() -> {
			for (UUID id : ArcherSkills.showerArrows(caster)) {
				Entity arrow = helper.getLevel().getEntity(id);
				if (arrow != null) {
					resting.put(id, arrow.position());
				}
			}
		});
		helper.runAfterDelay(LANDED_TICKS, () -> {
			double offX = 0.0;
			double offZ = 0.0;
			double lowX = Double.MAX_VALUE;
			double highX = -Double.MAX_VALUE;
			double lowZ = Double.MAX_VALUE;
			double highZ = -Double.MAX_VALUE;
			int airborne = 0;
			for (Vec3 at : resting.values()) {
				offX = Math.max(offX, Math.abs(at.x - point.x));
				offZ = Math.max(offZ, Math.abs(at.z - point.z));
				lowX = Math.min(lowX, at.x);
				highX = Math.max(highX, at.x);
				lowZ = Math.min(lowZ, at.z);
				highZ = Math.max(highZ, at.z);
				airborne += at.y > point.y + 0.6 ? 1 : 0;
			}
			LOG.info("[BEHAVIOR] test=everyShowerArrowLandsInsideTheThreeByThreeAreaAroundTheAim RESULT applied={} seen={} offX={} offZ={} spanX={} spanZ={} airborne={}",
				applied, resting.size(), offX, offZ, highX - lowX, highZ - lowZ, airborne);
			helper.assertTrue(applied, "the shower never started");
			helper.assertTrue(resting.size() == ArcherSkills.ARROW_SHOWER_ARROWS, "only " + resting.size() + " arrows were seen");
			helper.assertTrue(airborne == 0, airborne + " arrows never came down");
			helper.assertTrue(offX <= AREA_HALF_WIDTH + ARROW_EMBED && offZ <= AREA_HALF_WIDTH + ARROW_EMBED,
				"an arrow landed " + offX + " east-west and " + offZ + " north-south from the aim, past " + AREA_HALF_WIDTH);
			helper.assertTrue(highX - lowX > 2 * AREA_HALF_WIDTH * 0.8 && highZ - lowZ > 2 * AREA_HALF_WIDTH * 0.8,
				"the arrows did not cover the area: " + (highX - lowX) + " by " + (highZ - lowZ));
			finish(helper, caster);
		});
	}

	@GameTest(maxTicks = 260)
	public void withNoWallTheShowerHitsAFoeStandingWhereAnArrowLands(final GameTestHelper helper) {
		wallCase(helper, false, "withNoWallTheShowerHitsAFoeStandingWhereAnArrowLands");
	}

	@GameTest(maxTicks = 260)
	public void aSolidWallBetweenTheCasterAndAFoeStopsTheArrows(final GameTestHelper helper) {
		wallCase(helper, true, "aSolidWallBetweenTheCasterAndAFoeStopsTheArrows");
	}

	// Every arrow leaves behind the caster, left and right in turn, so each side gets half of them.
	@GameTest(maxTicks = 120)
	public void theVolleyLeavesFromBehindTheCasterOnBothSides(final GameTestHelper helper) {
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 20));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground(helper), SkillSchoolGameTest.modifiers(caster, false));
		helper.runAfterDelay(ArcherSkills.ARROW_SHOWER_TICKS + 2L, () -> {
			List<Vec3> origins = ArcherSkills.showerOrigins(caster);
			int right = 0;
			int ahead = 0;
			int offBand = 0;
			for (Vec3 origin : origins) {
				double along = origin.z - caster.getZ();
				double across = origin.x - caster.getX();
				right += across > 0.0 ? 1 : 0;
				ahead += along >= 0.0 ? 1 : 0;
				boolean inBand = along <= -ArcherSkills.SHOWER_BEHIND_MIN + 1.0E-6 && along >= -ArcherSkills.SHOWER_BEHIND_MAX - 1.0E-6
					&& Math.abs(across) >= ArcherSkills.SHOWER_SIDE_MIN - 1.0E-6 && Math.abs(across) <= ArcherSkills.SHOWER_SIDE_MAX + 1.0E-6
					&& Math.abs(origin.y - caster.getY() - ArcherSkills.SHOWER_SHOULDER) < 1.0E-6;
				offBand += inBand ? 0 : 1;
			}
			double share = origins.isEmpty() ? 0.0 : (double) right / origins.size();
			LOG.info("[BEHAVIOR] test=theVolleyLeavesFromBehindTheCasterOnBothSides RESULT applied={} origins={} right={} ahead={} offBand={}",
				applied, origins.size(), right, ahead, offBand);
			helper.assertTrue(applied, "the shower never started");
			helper.assertTrue(origins.size() == ArcherSkills.ARROW_SHOWER_ARROWS, "only " + origins.size() + " arrows left");
			helper.assertTrue(ahead == 0, ahead + " arrows left level with or ahead of the caster");
			helper.assertTrue(offBand == 0, offBand + " arrows left outside the band behind and beside the caster");
			helper.assertTrue(share >= 0.4 && share <= 0.6, "the right side got " + share + " of the arrows");
			finish(helper, caster);
		});
	}

	// The ceiling is 4 blocks over the floor and the foe stands 12 blocks away. A high lob would hit the ceiling
	// and reach no one. The stub RPG mod gives a roll with a low end under its average, so a hit that deals the
	// minimum is not a whole number of averages. Each hit slows the foe hard for a few ticks.
	@GameTest(maxTicks = 260)
	public void aCastUnderALowCeilingHitsAFoeTwelveBlocksAwayAndSlowsIt(final GameTestHelper helper) {
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 20));
		Vec3 standAt = helper.absoluteVec(new Vec3(CAST_X, 1.0, CORRIDOR_FOE_Z - CORRIDOR_RANGE));
		caster.setPos(standAt.x, standAt.y, standAt.z);
		Vec3 point = helper.absoluteVec(new Vec3(AIM_X, 1.0, CORRIDOR_FOE_Z));
		Mob foe = foeAt(helper, point);
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> saved = buildCorridor(helper);
		double[] highest = {0.0};
		double[] slowest = {-1.0};
		int[] shortest = {Integer.MAX_VALUE};
		helper.onEachTick(() -> {
			for (UUID id : ArcherSkills.showerArrows(caster)) {
				Entity arrow = helper.getLevel().getEntity(id);
				if (arrow != null) {
					highest[0] = Math.max(highest[0], arrow.getY());
				}
			}
			var slow = foe.getEffect(MobEffects.SLOWNESS);
			if (slow != null) {
				slowest[0] = Math.max(slowest[0], slow.getAmplifier());
				shortest[0] = Math.min(shortest[0], slow.getDuration());
			}
		});
		RpgBridge.setStats(new RollRange(LOW_ROLL, HIGH_ROLL));
		boolean applied;
		try {
			applied = ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		} finally {
			RpgBridge.setStats(null);
		}
		helper.runAfterDelay(LANDED_TICKS, () -> {
			double lost = lost(foe);
			double ceilingY = helper.absolutePos(new BlockPos(0, CORRIDOR_CEILING, 0)).getY();
			LOG.info("[BEHAVIOR] test=aCastUnderALowCeilingHitsAFoeTwelveBlocksAwayAndSlowsIt RESULT applied={} lost={} highestArrow={} ceilingY={} slowAmplifier={} slowShortest={}",
				applied, lost, highest[0], ceilingY, slowest[0], shortest[0]);
			saved.forEach((at, state) -> helper.getLevel().setBlock(at, state, 3));
			helper.assertTrue(applied, "the shower never started");
			helper.assertTrue(highest[0] < ceilingY, "an arrow climbed to " + highest[0] + ", into the ceiling at " + ceilingY);
			helper.assertTrue(lost >= AVERAGE_ROLL, "no arrow reached the foe 12 blocks away: it lost " + lost);
			helper.assertTrue(lost % AVERAGE_ROLL < 1.0E-3, "the foe lost " + lost + ", not a whole number of average rolls of " + AVERAGE_ROLL);
			helper.assertTrue(slowest[0] == ArcherSkills.SHOWER_SLOW_AMPLIFIER, "the foe never had Slowness amplifier " + ArcherSkills.SHOWER_SLOW_AMPLIFIER);
			helper.assertTrue(shortest[0] <= ArcherSkills.SHOWER_SLOW_TICKS, "the slow lasted " + shortest[0] + " ticks, over " + ArcherSkills.SHOWER_SLOW_TICKS);
			foe.discard();
			finish(helper, caster);
		});
	}

	// A shower arrow deals the average ranged roll of the caster, whatever speed it lands at and whatever the bow
	// enchantments add to a drawn arrow. The case lands one at the shower's own speed and one at a crawl.
	@GameTest(maxTicks = 80)
	public void oneShowerArrowDealsTheAverageRangedRollWhateverItsSpeedAndTheBow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		bow.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), POWER_LEVEL);
		caster.setItemInHand(InteractionHand.MAIN_HAND, bow);
		Mob drawnFoe = FighterBooksGameTest.foe(helper, 1.5, 5.5);
		Mob showerFoe = FighterBooksGameTest.foe(helper, 3.5, 5.5);
		Mob slowFoe = FighterBooksGameTest.foe(helper, 5.5, 5.5);
		Mob bareFoe = FighterBooksGameTest.foe(helper, 7.5, 5.5);
		float drawn = (float) FULL_DRAW_SPEED;
		float lob = (float) ArcherSkills.ARROW_SHOWER_SPEED;
		AbstractArrow full = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW), caster, bow);
		full.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 3.0)));
		Projectile.spawnProjectileUsingShoot(full, helper.getLevel(), bow, 0.0, 0.0, 1.0, drawn, 0.0F);
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow shower = ArcherSkills.spawnShowerArrow(caster, ammo, bow, helper.absoluteVec(new Vec3(3.5, 2.0, 3.0)), new Vec3(0.0, 0.0, lob), AVERAGE_ROLL);
		AbstractArrow slow = ArcherSkills.spawnShowerArrow(caster, ammo, bow, helper.absoluteVec(new Vec3(5.5, 2.0, 3.0)), new Vec3(0.0, 0.0, SLOW_LOB), AVERAGE_ROLL);
		AbstractArrow bare = ArcherSkills.spawnShowerArrow(caster, ammo, new ItemStack(Items.BOW), helper.absoluteVec(new Vec3(7.5, 2.0, 3.0)), new Vec3(0.0, 0.0, lob), AVERAGE_ROLL);
		helper.runAfterDelay(14L, () -> {
			double fullLost = lost(drawnFoe);
			double showerLost = lost(showerFoe);
			double slowLost = lost(slowFoe);
			double bareLost = lost(bareFoe);
			LOG.info("[BEHAVIOR] test=oneShowerArrowDealsTheAverageRangedRollWhateverItsSpeedAndTheBow RESULT fullDraw={} shower={} slow={} unpowered={} roll={}",
				fullLost, showerLost, slowLost, bareLost, AVERAGE_ROLL);
			helper.assertTrue(fullLost > AVERAGE_ROLL, "the full-draw Power arrow dealt " + fullLost + ", so the roll proves nothing against it");
			helper.assertTrue(shower.entityTags().contains("mod-stella-rpg.fixed_hit"), "the shower arrow lacks the tag that keeps the RPG mod from scaling it");
			helper.assertTrue(Math.abs(showerLost - AVERAGE_ROLL) < 1.0E-4, "a shower arrow dealt " + showerLost + ", not the roll of " + AVERAGE_ROLL);
			helper.assertTrue(Math.abs(slowLost - AVERAGE_ROLL) < 1.0E-4, "a slow shower arrow dealt " + slowLost + ", not the roll of " + AVERAGE_ROLL);
			helper.assertTrue(Math.abs(bareLost - AVERAGE_ROLL) < 1.0E-4, "a shower arrow from a bare bow dealt " + bareLost + ", not the roll of " + AVERAGE_ROLL);
			Stream.of(full, shower, slow, bare).forEach(Entity::discard);
			Stream.of(drawnFoe, showerFoe, slowFoe, bareFoe).forEach(Entity::discard);
			helper.succeed();
		});
	}

	// Without the RPG mod there is no roll, so a shower arrow deals what a full-draw arrow from the same bow deals.
	@GameTest(maxTicks = 80)
	public void withoutTheRpgModAShowerArrowDealsThePlainFullDrawDamage(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		Mob drawnFoe = FighterBooksGameTest.foe(helper, 2.5, 5.5);
		Mob showerFoe = FighterBooksGameTest.foe(helper, 5.5, 5.5);
		AbstractArrow full = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW), caster, bow);
		full.setPos(helper.absoluteVec(new Vec3(2.5, 2.0, 3.0)));
		Projectile.spawnProjectileUsingShoot(full, helper.getLevel(), bow, 0.0, 0.0, 1.0, (float) FULL_DRAW_SPEED, 0.0F);
		AbstractArrow shower = ArcherSkills.spawnShowerArrow(caster, new ItemStack(Items.ARROW), bow, helper.absoluteVec(new Vec3(5.5, 2.0, 3.0)),
			new Vec3(0.0, 0.0, ArcherSkills.ARROW_SHOWER_SPEED), ArcherSkills.NO_FIXED_DAMAGE);
		helper.runAfterDelay(14L, () -> {
			double fullLost = lost(drawnFoe);
			double showerLost = lost(showerFoe);
			LOG.info("[BEHAVIOR] test=withoutTheRpgModAShowerArrowDealsThePlainFullDrawDamage RESULT fullDraw={} shower={}", fullLost, showerLost);
			helper.assertTrue(Math.abs(fullLost - FULL_DRAW_PLAIN_DAMAGE) < 1.0E-4, "the full-draw arrow dealt " + fullLost);
			helper.assertTrue(Math.abs(showerLost - fullLost) < 1.0E-4, "a shower arrow dealt " + showerLost + " against " + fullLost + " for a full draw");
			helper.assertFalse(shower.entityTags().contains("mod-stella-rpg.fixed_hit"), "a shower arrow with no roll kept the fixed-hit tag");
			Stream.of(full, shower).forEach(Entity::discard);
			Stream.of(drawnFoe, showerFoe).forEach(Entity::discard);
			helper.succeed();
		});
	}

	// The arrow keeps its own effect and the bow its enchantments: a Flame bow sets the foe on fire, a tipped
	// arrow gives its effect, and both still deal the average roll.
	@GameTest(maxTicks = 80)
	public void aFlameBowAndATippedArrowStillApplyTheirEffectsToAShowerArrow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack flameBow = new ItemStack(Items.BOW);
		flameBow.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FLAME), 1);
		Mob flameFoe = FighterBooksGameTest.foe(helper, 3.5, 5.5);
		Mob tippedFoe = FighterBooksGameTest.foe(helper, 5.5, 5.5);
		Vec3 lob = new Vec3(0.0, 0.0, ArcherSkills.ARROW_SHOWER_SPEED);
		AbstractArrow flame = ArcherSkills.spawnShowerArrow(caster, new ItemStack(Items.ARROW), flameBow, helper.absoluteVec(new Vec3(3.5, 2.0, 3.0)), lob, AVERAGE_ROLL);
		ItemStack tippedAmmo = PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.SLOWNESS);
		AbstractArrow tipped = ArcherSkills.spawnShowerArrow(caster, tippedAmmo, new ItemStack(Items.BOW), helper.absoluteVec(new Vec3(5.5, 2.0, 3.0)), lob, AVERAGE_ROLL);
		helper.runAfterDelay(14L, () -> {
			double flameLost = lost(flameFoe);
			double tippedLost = lost(tippedFoe);
			boolean burning = flameFoe.isOnFire();
			boolean slowed = tippedFoe.hasEffect(MobEffects.SLOWNESS);
			LOG.info("[BEHAVIOR] test=aFlameBowAndATippedArrowStillApplyTheirEffectsToAShowerArrow RESULT burning={} slowed={} flameLost={} tippedLost={}",
				burning, slowed, flameLost, tippedLost);
			helper.assertTrue(burning, "a shower arrow from a Flame bow never set its foe on fire");
			helper.assertTrue(slowed, "a tipped shower arrow never gave its effect");
			helper.assertTrue(flameLost >= AVERAGE_ROLL, "the Flame arrow dealt " + flameLost + ", under the roll of " + AVERAGE_ROLL);
			helper.assertTrue(Math.abs(tippedLost - AVERAGE_ROLL) < 1.0E-4, "the tipped arrow dealt " + tippedLost + ", not the roll of " + AVERAGE_ROLL);
			Stream.of(flame, tipped).forEach(Entity::discard);
			Stream.of(flameFoe, tippedFoe).forEach(Entity::discard);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void showerArrowsNeverHurtTheCasterOrAnAlly(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		Mob ally = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(ally != null, "the level refused an ally");
		Vec3 allyAt = helper.absoluteVec(new Vec3(5.5, 1.0, 4.5));
		ally.snapTo(allyAt.x, allyAt.y, allyAt.z, 0.0F, 0.0F);
		ally.setNoAi(true);
		ally.setNoGravity(true);
		ally.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		ally.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		ally.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(ally);
		// Each arrow starts at the far wall, six blocks from the caster so it has left the caster's reach, and flies at the target.
		Vec3 aimedAtCaster = helper.absoluteVec(new Vec3(CASTER_X, 2.0, 7.5));
		Vec3 aimedAtAlly = helper.absoluteVec(new Vec3(5.5, 2.0, 7.5));
		float speed = (float) ArcherSkills.ARROW_SHOWER_SPEED;
		List<AbstractArrow> arrows = new ArrayList<>();
		// A normal arrow of the owner hurts the owner and the golem; that is what a shower arrow must not do.
		arrows.add(aimed(helper, caster, bow, aimedAtCaster, new Vec3(0.0, 0.0, -speed), false));
		arrows.add(aimed(helper, caster, bow, aimedAtAlly, new Vec3(0.0, 0.0, -speed), false));
		helper.runAfterDelay(12L, () -> {
			double casterControl = 1000.0 - caster.getHealth();
			double allyControl = 1000.0 - ally.getHealth();
			caster.setHealth(1000.0F);
			ally.setHealth(1000.0F);
			caster.damageCooldownTime = 0;
			ally.damageCooldownTime = 0;
			arrows.add(aimed(helper, caster, bow, aimedAtCaster, new Vec3(0.0, 0.0, -speed), true));
			arrows.add(aimed(helper, caster, bow, aimedAtAlly, new Vec3(0.0, 0.0, -speed), true));
			helper.runAfterDelay(12L, () -> {
				double casterLost = 1000.0 - caster.getHealth();
				double allyLost = 1000.0 - ally.getHealth();
				LOG.info("[BEHAVIOR] test=showerArrowsNeverHurtTheCasterOrAnAlly RESULT casterControl={} allyControl={} casterLost={} allyLost={}",
					casterControl, allyControl, casterLost, allyLost);
				helper.assertTrue(casterControl > 0.0 && allyControl > 0.0, "a normal arrow hurt neither, so the case proves nothing");
				helper.assertTrue(casterLost == 0.0, "a shower arrow hurt its caster for " + casterLost);
				helper.assertTrue(allyLost == 0.0, "a shower arrow hurt an ally for " + allyLost);
				arrows.forEach(Entity::discard);
				ally.discard();
				helper.succeed();
			});
		});
	}

	private static double lost(final LivingEntity foe) {
		return 1000.0 - foe.getHealth();
	}

	private static AbstractArrow aimed(
		final GameTestHelper helper, final ServerPlayer caster, final ItemStack bow, final Vec3 from, final Vec3 velocity, final boolean shower
	) {
		if (shower) {
			return ArcherSkills.spawnShowerArrow(caster, new ItemStack(Items.ARROW), bow, from, velocity, AVERAGE_ROLL);
		}
		AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW), caster, bow);
		arrow.setPos(from.x, from.y, from.z);
		Projectile.spawnProjectileUsingShoot(arrow, helper.getLevel(), bow, velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
		return arrow;
	}

	private static void showerCase(final GameTestHelper helper, final boolean awakened, final int want, final String test) {
		helper.assertTrue(ArcherSkills.ARROW_SHOWER_ARROWS == 64 && ArcherSkills.AWAKENED_ARROW_SHOWER_ARROWS == 128
			&& ArcherSkills.ARROW_SHOWER_TICKS == 80 && ArcherSkills.ARROW_SHOWER_COST == 4,
			"the shower numbers drifted from 64 (128 awakened) arrows in 80 ticks for 4 arrows");
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 20));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground(helper), SkillSchoolGameTest.modifiers(caster, awakened));
		List<Integer> sizes = new ArrayList<>();
		helper.onEachTick(() -> sizes.add(ArcherSkills.showerArrows(caster).size()));
		helper.runAfterDelay(ArcherSkills.ARROW_SHOWER_TICKS + 2L, () -> {
			List<UUID> loosed = ArcherSkills.showerArrows(caster);
			long real = loosed.stream().filter(id -> helper.getLevel().getEntity(id) instanceof AbstractArrow arrow && arrow.getOwner() == caster).count();
			int spent = 20 - caster.getInventory().getItem(PACK_SLOT).getCount();
			int biggestStep = 0;
			for (int tick = 1; tick < sizes.size(); tick++) {
				biggestStep = Math.max(biggestStep, sizes.get(tick) - sizes.get(tick - 1));
			}
			int quarter = sizes.get(sizes.size() / 4);
			LOG.info("[BEHAVIOR] test={} RESULT applied={} loosed={} real={} spent={} quarterWay={} biggestStep={} want={}",
				test, applied, loosed.size(), real, spent, quarter, biggestStep, want);
			helper.assertTrue(applied, "the shower never started");
			helper.assertTrue(loosed.size() == want, "the shower loosed " + loosed.size() + " arrows in " + ArcherSkills.ARROW_SHOWER_TICKS + " ticks, not " + want);
			helper.assertTrue(real == want, "only " + real + " of the arrows are live arrow entities owned by the caster");
			helper.assertTrue(spent == ArcherSkills.ARROW_SHOWER_COST, "the shower spent " + spent + " arrows, not " + ArcherSkills.ARROW_SHOWER_COST);
			helper.assertTrue(quarter > 0 && quarter < want, "a quarter of the way in, " + quarter + " arrows were out: the shower was not spread over time");
			int perTick = (int) Math.ceil((double) want / ArcherSkills.ARROW_SHOWER_TICKS);
			helper.assertTrue(biggestStep <= perTick, "the shower loosed " + biggestStep + " arrows in one tick, over " + perTick + ", so they did not leave steadily");
			finish(helper, caster);
		});
	}

	// Without the wall an arrow lands on a foe standing at its aim; with a wall across the arena between the
	// caster and the foe, nothing comes through. The stub RPG mod gives the roll, so every hit the foe takes is a
	// whole number of average rolls.
	private static void wallCase(final GameTestHelper helper, final boolean walled, final String test) {
		ServerPlayer caster = shooter(helper, new ItemStack(Items.ARROW, 20));
		Vec3 point = ground(helper);
		List<Vec3> spots = ArcherSkills.showerSpots(point, ArcherSkills.ARROW_SHOWER_ARROWS, RandomSource.create(SEED));
		// The foe stands at the aim nearest the middle of the area, clear of the wall.
		int aimed = 0;
		for (int index = 1; index < spots.size(); index++) {
			if (flatDistance(spots.get(index), point) < flatDistance(spots.get(aimed), point)) {
				aimed = index;
			}
		}
		Vec3 spot = spots.get(aimed);
		Mob foe = foeAt(helper, new Vec3(spot.x, point.y, spot.z));
		List<BlockPos> wall = new ArrayList<>();
		if (walled) {
			for (int x = 0; x < 8; x++) {
				for (int y = 1; y <= WALL_TOP; y++) {
					wall.add(helper.absolutePos(new BlockPos(x, y, WALL_Z)));
				}
			}
			wall.forEach(at -> helper.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 3));
		}
		ArcherSkills.setShowerRandom(RandomSource.create(SEED));
		RpgBridge.setStats(new RollRange(LOW_ROLL, HIGH_ROLL));
		boolean applied;
		try {
			applied = ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		} finally {
			ArcherSkills.setShowerRandom(null);
			RpgBridge.setStats(null);
		}
		int aimedIndex = aimed;
		helper.runAfterDelay(LANDED_TICKS, () -> {
			double lost = lost(foe);
			int loosed = ArcherSkills.showerArrows(caster).size();
			LOG.info("[BEHAVIOR] test={} RESULT applied={} loosed={} walled={} aimedArrow={} spot={} lost={}", test, applied, loosed, walled, aimedIndex, spot, lost);
			wall.forEach(at -> helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3));
			helper.assertTrue(applied && loosed == ArcherSkills.ARROW_SHOWER_ARROWS, "the shower loosed " + loosed + " arrows");
			if (walled) {
				helper.assertTrue(lost == 0.0, "a foe behind a solid wall lost " + lost);
			} else {
				helper.assertTrue(lost >= AVERAGE_ROLL, "no arrow reached a foe standing at the aim of arrow " + aimedIndex + " at " + spot);
				helper.assertTrue(lost % AVERAGE_ROLL < 1.0E-3, "the foe lost " + lost + ", which is not a whole number of rolls of " + AVERAGE_ROLL);
			}
			foe.discard();
			finish(helper, caster);
		});
	}

	// A foe nothing can push, move or kill, so its health reads back what the arrows dealt.
	private static Mob foeAt(final GameTestHelper helper, final Vec3 at) {
		Mob foe = EntityTypes.RAVAGER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(foe != null, "the level refused a foe");
		foe.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		foe.setNoAi(true);
		foe.setNoGravity(true);
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		foe.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		foe.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(foe);
		return foe;
	}

	// A stone floor and a stone ceiling 4 blocks over it, running out of the arena through its walls at both ends. The
	// result holds every block as it was, so the case can put it back.
	private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> buildCorridor(final GameTestHelper helper) {
		Map<BlockPos, net.minecraft.world.level.block.state.BlockState> saved = new HashMap<>();
		for (int x = -1; x <= 8; x++) {
			for (int z = CORRIDOR_START; z <= CORRIDOR_END; z++) {
				for (int y = 0; y <= CORRIDOR_CEILING; y++) {
					boolean outside = z < 0 || z >= 8;
					boolean shell = y == CORRIDOR_CEILING || y == 0 && x >= 0 && x <= 7;
					if (!shell && !(outside && y > 0)) {
						continue;
					}
					BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
					saved.put(at, helper.getLevel().getBlockState(at));
					helper.getLevel().setBlock(at, (shell ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
				}
			}
		}
		return saved;
	}

	private static double flatDistance(final Vec3 a, final Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static void finish(final GameTestHelper helper, final ServerPlayer caster) {
		ArcherSkills.showerArrows(caster).forEach(id -> {
			Entity arrow = helper.getLevel().getEntity(id);
			if (arrow != null) {
				arrow.discard();
			}
		});
		ArcherSkills.clearFor(caster);
		helper.succeed();
	}

	/** The ground the shower aims at, on the arena floor. */
	private static Vec3 ground(final GameTestHelper helper) {
		return helper.absoluteVec(new Vec3(AIM_X, 1.0, AIM_Z));
	}

	private static ServerPlayer shooter(final GameTestHelper helper, final ItemStack... pack) {
		ServerPlayer caster = archer(helper, pack);
		Vec3 at = helper.absoluteVec(new Vec3(CAST_X, 1.0, CAST_Z));
		caster.setPos(at.x, at.y, at.z);
		return caster;
	}

	private static ServerPlayer archer(final GameTestHelper helper, final ItemStack... pack) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		for (int index = 0; index < pack.length; index++) {
			caster.getInventory().setItem(PACK_SLOT + index, pack[index]);
		}
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static ItemStack quiverWith(final ItemStack... ammunition) {
		ItemStack quiver = new ItemStack(Quiver.ITEM);
		NonNullList<ItemStack> items = NonNullList.withSize(QuiverMenu.SIZE, ItemStack.EMPTY);
		for (int slot = 0; slot < ammunition.length; slot++) {
			items.set(slot, ammunition[slot]);
		}
		Quiver.store(quiver, items);
		return quiver;
	}

	/** An RPG mod whose caster rolls this low and this high on a ranged attack, in health points. */
	private record RollRange(float low, float high) implements RpgBridge.Stats {
		@Override
		public double power(final net.minecraft.world.entity.player.Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final net.minecraft.world.entity.player.Player player) {
			return 1.0;
		}

		@Override
		public double floorAtk(final LivingEntity caster, final boolean ranged) {
			return ranged ? this.low : Double.NaN;
		}

		@Override
		public double[] atkRange(final LivingEntity caster, final boolean ranged) {
			return ranged ? new double[] {this.low, this.high} : new double[] {Double.NaN, Double.NaN};
		}
	}

	/** The arc, the fall and the sound read on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theShowerLooksLikeAShowerOnAClientRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Arrow Shower on open ground at 10 and 20 blocks, and in a corridor 4 blocks high. Arrows leave from behind you, left and right in turn, and fly a low arc past you onto a 3 by 3 block area around the aimed point, one after another for 4 seconds. This harness has no client.");
	}
}
