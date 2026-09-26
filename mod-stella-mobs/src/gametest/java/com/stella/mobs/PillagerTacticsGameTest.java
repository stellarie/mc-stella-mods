package com.stella.mobs;

import com.stella.mobs.behavior.CrossbowCadenceGoal;
import com.stella.mobs.behavior.PillagerMeleeGoal;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.config.RollSpec;
import com.stella.mobs.gear.GearRoller;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** M4 and M5: every Pillager can use what it holds, a sword closes to melee, and a crossbow closes to its range. */
public class PillagerTacticsGameTest {
	private static final String NO_ROLLS = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";
	private static final int MAX_TICKS = 200;
	private static final int ROLLS = 2000;
	private static final float TARGET_HEALTH = 1000.0F;
	private static final double MELEE_REACH = 2.5;
	private static final long WATCH_TICKS = 120L;
	private static final long SETTLE_TICK = 20L;
	private static final double HOLD_DRIFT = 1.0;
	private static final int WALL_X = 3;
	private static final int WALL_END_Z = 5;
	private static final int WALL_HEIGHT = 3;

	@GameTest(maxTicks = MAX_TICKS)
	public void swordPillagerClosesAndHitsItsTarget(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.IRON_SWORD));
		Cow target = target(helper);
		helper.onEachTick(() -> pillager.setTarget(target));

		helper.succeedWhen(() -> {
			float lost = TARGET_HEALTH - target.getHealth();
			helper.assertTrue(lost > 0.0F, "the sword Pillager never struck its target; distance=" + pillager.distanceTo(target));
			result("swordPillagerClosesAndHitsItsTarget", "lost=" + lost + " tick=" + helper.getTick());
			discard(pillager, target);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void crossbowPillagerNeverClosesToMelee(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.CROSSBOW));
		Cow target = target(helper);
		double[] closest = { Double.MAX_VALUE };

		// The same setup as the sword case with only the weapon changed, so the approach must come from the weapon.
		helper.onEachTick(() -> {
			pillager.setTarget(target);
			closest[0] = Math.min(closest[0], pillager.distanceTo(target));
		});
		helper.runAfterDelay(WATCH_TICKS, () -> {
			result("crossbowPillagerNeverClosesToMelee", "closest=" + closest[0]);
			discard(pillager, target);
			helper.assertTrue(closest[0] > MELEE_REACH, "a crossbow Pillager walked into melee reach: " + closest[0]);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void crossbowPillagerClosesToItsFiringRange(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.CROSSBOW), 0, 0);
		Cow target = target(helper, 7, 7);
		helper.onEachTick(() -> pillager.setTarget(target));

		helper.succeedWhen(() -> {
			double distance = pillager.distanceTo(target);
			helper.assertTrue(distance <= CrossbowCadenceGoal.CHASE_RADIUS, "the crossbow Pillager stayed " + distance + " away");
			result("crossbowPillagerClosesToItsFiringRange", "distance=" + distance + " tick=" + helper.getTick());
			discard(pillager, target);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void crossbowPillagerInsideItsRangeHoldsItsGround(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.CROSSBOW), 1, 1);
		Cow target = target(helper, 5, 1);
		Vec3[] settled = { null };
		double[] drift = { 0.0 };

		// The same Pillager a few blocks closer, in sight: it must shoot from where it stands.
		helper.onEachTick(() -> {
			pillager.setTarget(target);
			if (helper.getTick() == SETTLE_TICK) {
				settled[0] = pillager.position();
			} else if (settled[0] != null) {
				drift[0] = Math.max(drift[0], Math.hypot(pillager.getX() - settled[0].x, pillager.getZ() - settled[0].z));
			}
		});
		helper.runAfterDelay(WATCH_TICKS, () -> {
			result("crossbowPillagerInsideItsRangeHoldsItsGround", "drift=" + drift[0] + " distance=" + pillager.distanceTo(target));
			discard(pillager, target);
			helper.assertTrue(drift[0] < HOLD_DRIFT, "a Pillager in range and in sight walked " + drift[0]);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void crossbowPillagerWalksAroundAWallToSeeItsTarget(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.CROSSBOW), 1, 1);
		Cow target = target(helper, 6, 1);

		for (int z = 0; z <= WALL_END_Z; z++) {
			for (int y = 1; y <= WALL_HEIGHT; y++) {
				helper.setBlock(WALL_X, y, z, Blocks.STONE);
			}
		}

		helper.onEachTick(() -> pillager.setTarget(target));
		helper.succeedWhen(() -> {
			helper.assertTrue(pillager.getSensing().hasLineOfSight(target), "the Pillager never found a line of sight around the wall");
			result("crossbowPillagerWalksAroundAWallToSeeItsTarget", "position=" + pillager.position() + " tick=" + helper.getTick());
			discard(pillager, target);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void everyRolledPillagerHoldsAWeaponItCanUse(final GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RollSpec spec = new RollSpec(1.0, 1.0);
		Pillager pillager = EntityTypes.PILLAGER.create(helper.getLevel(), EntitySpawnReason.EVENT);
		int idle = 0;
		int swords = 0;

		// A roll that leaves a Pillager holding something no goal can use is a Pillager that never fights.
		for (int i = 0; i < ROLLS; i++) {
			pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			GearRoller.roll(pillager, spec, RandomSource.create(i), registry);
			boolean crossbow = CrossbowCadenceGoal.holdsCrossbow(pillager);
			boolean melee = PillagerMeleeGoal.canWield(pillager);
			swords += melee ? 1 : 0;
			idle += crossbow || melee ? 0 : 1;
		}

		result("everyRolledPillagerHoldsAWeaponItCanUse", "rolls=" + ROLLS + " melee=" + swords + " meleeShare=" + (double) swords / ROLLS + " idle=" + idle);
		helper.assertValueEqual(Integer.valueOf(idle), Integer.valueOf(0), "rolled Pillagers holding nothing they can fight with");
		helper.succeed();
	}

	private static Pillager pillager(final GameTestHelper helper, final ItemStack weapon) {
		return pillager(helper, weapon, 1, 1);
	}

	private static Pillager pillager(final GameTestHelper helper, final ItemStack weapon, final int x, final int z) {
		GearConfig previous = GearConfigStore.get();
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);

		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLLS));
			// The weapon is in hand before the load hook runs, as it is for a Pillager that spawns in play.
			Pillager pillager = EntityTypes.PILLAGER.create(helper.getLevel(), EntitySpawnReason.NATURAL);
			Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 3, z + 0.5));
			pillager.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			pillager.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			pillager.setPersistenceRequired();
			helper.getLevel().addFreshEntity(pillager);
			return pillager;
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static Cow target(final GameTestHelper helper) {
		return target(helper, 6, 6);
	}

	private static Cow target(final GameTestHelper helper, final int x, final int z) {
		Cow cow = helper.spawnMob(EntityTypes.COW, x, 1, z).withNoFreeWill().spawn();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		cow.setHealth(TARGET_HEALTH);
		cow.setPersistenceRequired();
		return cow;
	}

	/** Removes the subjects at the end, so an armed Pillager never outlives its arena and shoots a neighbour's. */
	private static void discard(final Pillager pillager, final Cow target) {
		pillager.discard();
		target.discard();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
