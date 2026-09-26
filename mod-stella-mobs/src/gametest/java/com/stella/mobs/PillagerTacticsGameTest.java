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

	@GameTest(maxTicks = MAX_TICKS)
	public void swordPillagerClosesAndHitsItsTarget(final GameTestHelper helper) {
		Pillager pillager = pillager(helper, new ItemStack(Items.IRON_SWORD));
		Cow target = target(helper);
		helper.onEachTick(() -> pillager.setTarget(target));

		helper.succeedWhen(() -> {
			float lost = TARGET_HEALTH - target.getHealth();
			helper.assertTrue(lost > 0.0F, "the sword Pillager never struck its target; distance=" + pillager.distanceTo(target));
			result("swordPillagerClosesAndHitsItsTarget", "lost=" + lost + " tick=" + helper.getTick());
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
			helper.assertTrue(closest[0] > MELEE_REACH, "a crossbow Pillager walked into melee reach: " + closest[0]);
			helper.succeed();
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
		GearConfig previous = GearConfigStore.get();
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);

		try {
			GearConfigStore.set(GearConfigParser.parse(NO_ROLLS));
			Pillager pillager = helper.spawnMob(EntityTypes.PILLAGER, 1, 3, 1).spawn();
			pillager.setPersistenceRequired();
			pillager.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			return pillager;
		} finally {
			GearConfigStore.set(previous);
		}
	}

	private static Cow target(final GameTestHelper helper) {
		Cow cow = helper.spawnMob(EntityTypes.COW, 6, 1, 6).withNoFreeWill().spawn();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
		cow.setHealth(TARGET_HEALTH);
		cow.setPersistenceRequired();
		return cow;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
