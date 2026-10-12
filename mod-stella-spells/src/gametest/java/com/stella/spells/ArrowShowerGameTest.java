package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arrow Shower looses real arrows: 32 over 80 ticks, 64 when awakened, for 8 arrows of the caster. They land over
 * a wide disc, a roof stops them, each one is worth a quarter of an arrow from the same bow, and they spare the
 * caster and its allies.
 */
public class ArrowShowerGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.arrowshower");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final int PACK_SLOT = 20;
	/** Where the shower aims, and the middle of the 8 by 8 arena. */
	private static final double AIM_X = 4.5;
	private static final double AIM_Z = 4.5;
	private static final double ARENA_MIDDLE = 4.0;
	/** The last arrow leaves at tick 80 and a lob takes about 72 ticks to come down. */
	private static final long LANDED_TICKS = ArcherSkills.ARROW_SHOWER_TICKS + 100L;
	private static final long SEED = 4242L;
	private static final double ROOF_HEIGHT = 4.0;
	private static final int ROOF_REACH = 2;

	@GameTest(maxTicks = 120)
	public void arrowShowerLoosesThirtyTwoArrowsInEightyTicksAndSpendsEight(final GameTestHelper helper) {
		showerCase(helper, false, ArcherSkills.ARROW_SHOWER_ARROWS, "arrowShowerLoosesThirtyTwoArrowsInEightyTicksAndSpendsEight");
	}

	@GameTest(maxTicks = 120)
	public void anAwakenedArrowShowerLoosesSixtyFourArrowsInTheSameEightyTicks(final GameTestHelper helper) {
		showerCase(helper, true, ArcherSkills.AWAKENED_ARROW_SHOWER_ARROWS, "anAwakenedArrowShowerLoosesSixtyFourArrowsInTheSameEightyTicks");
	}

	@GameTest(maxTicks = 60)
	public void arrowShowerWithSevenArrowsIsRefusedAndSpendsNothing(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, ArcherSkills.ARROW_SHOWER_COST - 1));
		boolean applied = ArcherSkills.applyArrowRain(caster, null, ground(helper), SkillSchoolGameTest.modifiers(caster, false));
		helper.runAfterDelay(10L, () -> {
			int left = caster.getInventory().getItem(PACK_SLOT).getCount();
			int loosed = ArcherSkills.showerArrows(caster).size();
			LOG.info("[BEHAVIOR] test=arrowShowerWithSevenArrowsIsRefusedAndSpendsNothing RESULT applied={} left={} loosed={}", applied, left, loosed);
			helper.assertFalse(applied, "a shower started on " + (ArcherSkills.ARROW_SHOWER_COST - 1) + " arrows");
			helper.assertTrue(left == ArcherSkills.ARROW_SHOWER_COST - 1, "the refused shower spent arrows: " + left + " left");
			helper.assertTrue(loosed == 0, "the refused shower loosed " + loosed + " arrows");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void arrowShowerSpendsTheQuiverBeforeThePackAndLoosesTheKindItSpent(final GameTestHelper helper) {
		Item fire = ArrowItems.BY_KIND.get(ArrowKind.FIRE);
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, 6));
		int fromQuiver = 5;
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

	@GameTest(maxTicks = 260)
	public void withNoRoofTheShowerHitsAFoeStandingWhereAnArrowLands(final GameTestHelper helper) {
		roofCase(helper, false, "withNoRoofTheShowerHitsAFoeStandingWhereAnArrowLands");
	}

	@GameTest(maxTicks = 260)
	public void aSolidRoofOverAFoeStopsTheArrows(final GameTestHelper helper) {
		roofCase(helper, true, "aSolidRoofOverAFoeStopsTheArrows");
	}

	@GameTest(maxTicks = 60)
	public void oneShowerArrowDealsAQuarterOfAnArrowFromTheSameBow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		caster.setItemInHand(InteractionHand.MAIN_HAND, bow);
		Mob normalFoe = FighterBooksGameTest.foe(helper, 2.5, 5.5);
		Mob showerFoe = FighterBooksGameTest.foe(helper, 5.5, 5.5);
		float speed = (float) ArcherSkills.ARROW_SHOWER_SPEED;
		Vec3 normalFrom = helper.absoluteVec(new Vec3(2.5, 2.0, 3.0));
		Vec3 showerFrom = helper.absoluteVec(new Vec3(5.5, 2.0, 3.0));
		ArrowItem plain = (ArrowItem) Items.ARROW;
		AbstractArrow normal = plain.createArrow(helper.getLevel(), new ItemStack(Items.ARROW), caster, bow);
		normal.setPos(normalFrom.x, normalFrom.y, normalFrom.z);
		Projectile.spawnProjectileUsingShoot(normal, helper.getLevel(), bow, 0.0, 0.0, 1.0, speed, 0.0F);
		AbstractArrow shower = ArcherSkills.spawnShowerArrow(caster, new ItemStack(Items.ARROW), bow, showerFrom, new Vec3(0.0, 0.0, speed));
		helper.runAfterDelay(8L, () -> {
			double normalLost = 1000.0 - normalFoe.getHealth();
			double showerLost = 1000.0 - showerFoe.getHealth();
			LOG.info("[BEHAVIOR] test=oneShowerArrowDealsAQuarterOfAnArrowFromTheSameBow RESULT normal={} shower={} share={} ratio={}",
				normalLost, showerLost, ArcherSkills.ARROW_SHOWER_DAMAGE_SHARE, normalLost > 0.0 ? showerLost / normalLost : -1.0);
			helper.assertTrue(normalLost > 0.0, "the reference arrow hit nothing, so the ratio proves nothing");
			helper.assertTrue(showerLost > 0.0, "the shower arrow hit nothing");
			helper.assertTrue(Math.abs(showerLost / normalLost - ArcherSkills.ARROW_SHOWER_DAMAGE_SHARE) < 0.02,
				"the shower arrow took " + showerLost + " against " + normalLost + " for a normal one");
			helper.assertTrue(ArcherSkills.ARROW_SHOWER_DAMAGE_SHARE == 0.25F, "the share is " + ArcherSkills.ARROW_SHOWER_DAMAGE_SHARE + ", not a quarter");
			normal.discard();
			shower.discard();
			normalFoe.discard();
			showerFoe.discard();
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

	private static AbstractArrow aimed(
		final GameTestHelper helper, final ServerPlayer caster, final ItemStack bow, final Vec3 from, final Vec3 velocity, final boolean shower
	) {
		if (shower) {
			return ArcherSkills.spawnShowerArrow(caster, new ItemStack(Items.ARROW), bow, from, velocity);
		}
		AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW), caster, bow);
		arrow.setPos(from.x, from.y, from.z);
		Projectile.spawnProjectileUsingShoot(arrow, helper.getLevel(), bow, velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
		return arrow;
	}

	private static void showerCase(final GameTestHelper helper, final boolean awakened, final int want, final String test) {
		helper.assertTrue(ArcherSkills.ARROW_SHOWER_ARROWS == 32 && ArcherSkills.AWAKENED_ARROW_SHOWER_ARROWS == 64
			&& ArcherSkills.ARROW_SHOWER_TICKS == 80 && ArcherSkills.ARROW_SHOWER_COST == 8,
			"the shower numbers drifted from 32 (64 awakened) arrows in 80 ticks for 8 arrows");
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, 20));
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
			helper.assertTrue(biggestStep <= 1, "the shower loosed " + biggestStep + " arrows in one tick, so they did not leave one after another");
			finish(helper, caster);
		});
	}

	// Without the roof an arrow lands on a foe standing at its aim; with it, nothing comes through. The ceiling of
	// the arena is open for the case, so the lobs can leave it and come back down.
	private static void roofCase(final GameTestHelper helper, final boolean roofed, final String test) {
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, 20));
		Vec3 point = ground(helper);
		Vec3 middle = helper.absoluteVec(new Vec3(ARENA_MIDDLE, 1.0, ARENA_MIDDLE));
		List<Vec3> spots = ArcherSkills.showerSpots(point, ArcherSkills.ARROW_SHOWER_ARROWS, RandomSource.create(SEED));
		// The roof must sit inside the arena, so the foe stands at the aim nearest the middle of it.
		int aimed = 0;
		for (int index = 1; index < spots.size(); index++) {
			if (flatDistance(spots.get(index), middle) < flatDistance(spots.get(aimed), middle)) {
				aimed = index;
			}
		}
		Vec3 spot = spots.get(aimed);
		Mob foe = EntityTypes.RAVAGER.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(foe != null, "the level refused a foe");
		foe.snapTo(spot.x, point.y, spot.z, 0.0F, 0.0F);
		foe.setNoAi(true);
		foe.setNoGravity(true);
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		foe.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		foe.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(foe);
		List<BlockPos> ceiling = ArenaCeiling.open(helper);
		List<BlockPos> roof = new ArrayList<>();
		if (roofed) {
			BlockPos centre = BlockPos.containing(spot.x, point.y + ROOF_HEIGHT, spot.z);
			for (int dx = -ROOF_REACH; dx <= ROOF_REACH; dx++) {
				for (int dz = -ROOF_REACH; dz <= ROOF_REACH; dz++) {
					roof.add(centre.offset(dx, 0, dz));
				}
			}
			roof.forEach(at -> helper.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 3));
		}
		ArcherSkills.setShowerRandom(RandomSource.create(SEED));
		boolean applied;
		try {
			applied = ArcherSkills.applyArrowRain(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		} finally {
			ArcherSkills.setShowerRandom(null);
		}
		int aimedIndex = aimed;
		helper.runAfterDelay(LANDED_TICKS, () -> {
			double lost = 1000.0 - foe.getHealth();
			int loosed = ArcherSkills.showerArrows(caster).size();
			LOG.info("[BEHAVIOR] test={} RESULT applied={} loosed={} roofed={} aimedArrow={} spot={} lost={}", test, applied, loosed, roofed, aimedIndex, spot, lost);
			roof.forEach(at -> helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3));
			ArenaCeiling.close(helper, ceiling);
			helper.assertTrue(applied && loosed == ArcherSkills.ARROW_SHOWER_ARROWS, "the shower loosed " + loosed + " arrows");
			if (roofed) {
				helper.assertTrue(lost == 0.0, "a foe under a solid roof lost " + lost);
			} else {
				helper.assertTrue(lost > 0.0, "no arrow reached a foe standing at the aim of arrow " + aimedIndex + " at " + spot);
			}
			foe.discard();
			finish(helper, caster);
		});
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

	/** The arc, the fall and the sound read on a client only: this is the manual item. */
	@GameTest(required = false, maxTicks = 40)
	public void theShowerLooksLikeAShowerOnAClientRequiresManualReview(final GameTestHelper helper) {
		helper.fail("[MANUAL] cast Arrow Shower on open ground at 10 and 20 blocks. Arrows climb out of the sky above you, then fall over a wide disc around the aimed point, one after another for 4 seconds. This harness has no client.");
	}
}
