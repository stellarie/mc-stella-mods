package com.stella.rpg;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RpgMobStatsGameTest {
	private static final float HIT = 4.0F;
	private static final float TOLERANCE = 0.001F;
	private static final int[][] MELEE_TABLE = {
		{0, 5, 5, 5, 5, 5, 5},
		{4, 5, 5, 5, 5, 5, 5},
		{40, 40, 20, 40, 10, 10, 20},
		{200, 200, 100, 200, 50, 50, 100}
	};

	private static final class Stub implements MobsBridge.Facts {
		private final Map<Mob, Integer> threats = new IdentityHashMap<>();
		private final Map<Mob, String> roles = new IdentityHashMap<>();

		Stub set(final Mob mob, final int threat, final String role) {
			this.threats.put(mob, threat);
			this.roles.put(mob, role);
			return this;
		}

		@Override
		public int threat(final Mob mob) {
			return this.threats.getOrDefault(mob, 0);
		}

		@Override
		public String role(final Mob mob) {
			return this.roles.getOrDefault(mob, MobStats.MELEE);
		}
	}

	@GameTest
	public void table(final GameTestHelper helper) {
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			Zombie zombie = zombie(helper);
			for (int[] row : MELEE_TABLE) {
				stub.set(zombie, row[0], "melee");
				for (Stat stat : Stat.values()) {
					int actual = MobStats.base(zombie, stat);
					RpgTestSupport.result("table", "threat=" + row[0] + " " + stat + "=" + actual);
					helper.assertTrue(actual == row[1 + stat.ordinal()],
						"melee threat " + row[0] + " " + stat + " was " + actual + " instead of " + row[1 + stat.ordinal()]);
				}
			}
			stub.set(zombie, 200, "ranged");
			assertStats(helper, zombie, "ranged", 50, 200, 100, 200, 50, 100);
			stub.set(zombie, 200, "magic");
			assertStats(helper, zombie, "magic", 50, 100, 100, 50, 200, 100);
			stub.set(zombie, 200, "unknown");
			assertStats(helper, zombie, "unknown role", 200, 100, 200, 50, 50, 100);
			stub.set(zombie, -50, "melee");
			assertStats(helper, zombie, "negative threat", 5, 5, 5, 5, 5, 5);
			stub.set(zombie, 5000, "melee");
			assertStats(helper, zombie, "clamped", 1024, 1024, 1024, 1024, 1024, 1024);
			stub.set(zombie, 40, "melee");
			StatBonuses.Provider bonus = (entity, stat) -> stat == Stat.STR ? 3 : 0;
			StatBonuses.register(bonus);
			try {
				helper.assertTrue(StatBonuses.effective(zombie, Stat.STR) == 43, "a bonus did not add to the mob base");
			} finally {
				StatBonuses.unregister(bonus);
			}
		} finally {
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void absentMobsMod(final GameTestHelper helper) {
		MobsBridge.setFacts(null);
		Zombie zombie = zombie(helper);
		for (Stat stat : Stat.values()) {
			int actual = MobStats.base(zombie, stat);
			helper.assertTrue(actual == 5, stat + " was " + actual + " without the mobs mod");
		}
		RpgTestSupport.result("absentMobsMod", "all stats 5");
		helper.succeed();
	}

	@GameTest
	public void weightConfigOverrides(final GameTestHelper helper) {
		List<String> errors = new ArrayList<>();
		RpgConfig config = RpgConfigStore.parse(
			"{\"mob_weights\":{\"melee\":{\"str\":2.0,\"agi\":-1,\"vit\":\"x\"},\"ranged\":7}}", errors::add);
		helper.assertTrue(errors.size() == 3, "errors=" + errors);
		helper.assertTrue(config.mobWeights().get("melee").get(Stat.STR) == 2.0, "the valid weight was lost");
		helper.assertTrue(config.mobWeights().get("melee").get(Stat.AGI) == 0.5, "a negative weight did not fall back");
		helper.assertTrue(config.mobWeights().get("melee").get(Stat.VIT) == 1.0, "a text weight did not fall back");
		helper.assertTrue(config.mobWeights().get("ranged").equals(RpgConfig.DEFAULT_MOB_WEIGHTS.get("ranged")),
			"a bad role row did not fall back");
		RpgConfigStore.set(config);
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			Zombie zombie = zombie(helper);
			stub.set(zombie, 40, "melee");
			helper.assertTrue(MobStats.base(zombie, Stat.STR) == 80, "the configured weight did not scale the stat");
		} finally {
			RpgConfigStore.set(null);
			MobsBridge.setFacts(null);
		}
		helper.succeed();
	}

	@GameTest
	public void mobMeleeAddsDice(final GameTestHelper helper) {
		float loss40;
		float loss200;
		Difficulty before = helper.getLevel().getDifficulty();
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			forceMax(false);
			Zombie attacker = zombie(helper);
			stub.set(attacker, 40, "melee");
			loss40 = meleeLoss(helper, attacker);
			stub.set(attacker, 200, "melee");
			loss200 = meleeLoss(helper, attacker);
		} finally {
			resetAll(helper, before);
		}
		assertLoss(helper, "mobMeleeAddsDice 40", loss40, HIT + RpgDice.of(40).max());
		assertLoss(helper, "mobMeleeAddsDice 200", loss200, HIT + RpgDice.of(200).max());
		helper.succeed();
	}

	@GameTest
	public void mobCritMultipliesTotal(final GameTestHelper helper) {
		float loss;
		Difficulty before = helper.getLevel().getDifficulty();
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			forceMax(true);
			Zombie attacker = zombie(helper);
			stub.set(attacker, 40, "melee");
			loss = meleeLoss(helper, attacker);
		} finally {
			resetAll(helper, before);
		}
		assertLoss(helper, "mobCritMultipliesTotal", loss, (HIT + RpgDice.of(40).max()) * 1.5F);
		helper.succeed();
	}

	@GameTest
	public void mobVitalityReduces(final GameTestHelper helper) {
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		DefenseBonuses.Provider flat = entity -> 1.0F;
		float plain;
		float withFlat;
		float clamped;
		try {
			Zombie victim = zombie(helper);
			stub.set(victim, 40, "melee");
			plain = magicLoss(helper, victim, 6.0F);
			DefenseBonuses.register(flat);
			withFlat = magicLoss(helper, zombie(helper, stub, 40), 6.0F);
			clamped = magicLoss(helper, zombie(helper, stub, 40), 0.5F);
		} finally {
			DefenseBonuses.unregister(flat);
			MobsBridge.setFacts(null);
		}
		double multiplier = RpgCurve.damageTakenMultiplier(40);
		assertLoss(helper, "mobVitalityReduces plain", plain, (float) (6.0 * multiplier));
		assertLoss(helper, "mobVitalityReduces flat", withFlat, (float) (6.0 * multiplier - 1.0));
		assertLoss(helper, "mobVitalityReduces clamped", clamped, 0.0F);
		helper.succeed();
	}

	@GameTest
	public void mobArrowUsesDex(final GameTestHelper helper) {
		float loss40;
		float loss200;
		List<RpgDice.Dice> rolled = new ArrayList<>();
		Difficulty before = helper.getLevel().getDifficulty();
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			RpgCombat.setRoller((attacker, random, chance) -> false);
			RpgCombat.setDiceRoller((attacker, random, dice) -> {
				rolled.add(dice);
				return dice.max();
			});
			Skeleton skeleton = helper.spawnMob(EntityTypes.SKELETON, 1, 1, 2).withNoFreeWill().spawn();
			stub.set(skeleton, 40, "ranged");
			loss40 = arrowLoss(helper, skeleton);
			stub.set(skeleton, 200, "ranged");
			loss200 = arrowLoss(helper, skeleton);
		} finally {
			resetAll(helper, before);
		}
		assertLoss(helper, "mobArrowUsesDex 40", loss40, HIT + RpgDice.of(40).max());
		assertLoss(helper, "mobArrowUsesDex 200", loss200, HIT + RpgDice.of(200).max());
		helper.assertTrue(rolled.size() == 2 && rolled.get(0).equals(RpgDice.of(40)) && rolled.get(1).equals(RpgDice.of(200)),
			"arrow dice were " + rolled);
		helper.succeed();
	}

	@GameTest
	public void mobSpellUsesInt(final GameTestHelper helper) {
		float loss40;
		float loss200;
		Difficulty before = helper.getLevel().getDifficulty();
		Stub stub = new Stub();
		MobsBridge.setFacts(stub);
		try {
			helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			forceMax(true);
			Zombie caster = zombie(helper);
			stub.set(caster, 40, "magic");
			loss40 = spellLoss(helper, caster);
			stub.set(caster, 200, "magic");
			loss200 = spellLoss(helper, caster);
		} finally {
			resetAll(helper, before);
		}
		assertLoss(helper, "mobSpellUsesInt 40", loss40, (float) (HIT * RpgCurve.spellPowerMultiplier(40)));
		assertLoss(helper, "mobSpellUsesInt 200", loss200, (float) (HIT * RpgCurve.spellPowerMultiplier(200)));
		helper.succeed();
	}

	private static void assertStats(
		final GameTestHelper helper, final Mob mob, final String name, final int... expected
	) {
		for (Stat stat : Stat.values()) {
			int actual = MobStats.base(mob, stat);
			helper.assertTrue(actual == expected[stat.ordinal()],
				name + " " + stat + " was " + actual + " instead of " + expected[stat.ordinal()]);
		}
	}

	private static float meleeLoss(final GameTestHelper helper, final Mob attacker) {
		ServerPlayer victim = victim(helper);
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), HIT);
		return before - victim.getHealth();
	}

	private static float spellLoss(final GameTestHelper helper, final Mob caster) {
		ServerPlayer victim = victim(helper);
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), HIT);
		return before - victim.getHealth();
	}

	private static float arrowLoss(final GameTestHelper helper, final Mob shooter) {
		ServerLevel level = helper.getLevel();
		Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
		level.addFreshEntity(arrow);
		ServerPlayer victim = victim(helper);
		float before = victim.getHealth();
		DamageSource source = level.damageSources().arrow(arrow, shooter);
		victim.hurtServer(level, source, HIT);
		arrow.discard();
		return before - victim.getHealth();
	}

	private static float magicLoss(final GameTestHelper helper, final Zombie victim, final float damage) {
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), damage);
		return victim.getMaxHealth() - victim.getHealth();
	}

	private static ServerPlayer victim(final GameTestHelper helper) {
		ServerPlayer player = RpgTestSupport.realPlayer(helper);
		RpgTestSupport.bare(player);
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		player.setHealth(200.0F);
		return player;
	}

	private static Zombie zombie(final GameTestHelper helper, final Stub stub, final int threat) {
		Zombie zombie = zombie(helper);
		stub.set(zombie, threat, "melee");
		return zombie;
	}

	private static Zombie zombie(final GameTestHelper helper) {
		EntityType<Zombie> type = EntityTypes.ZOMBIE;
		Zombie zombie = helper.spawnMob(type, 1, 1, 2).withNoFreeWill().spawn();
		RpgTestSupport.bare(zombie);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return zombie;
	}

	private static void forceMax(final boolean crit) {
		RpgCombat.setRoller((attacker, random, chance) -> crit);
		RpgCombat.setDiceRoller((attacker, random, dice) -> dice.max());
	}

	private static void resetAll(final GameTestHelper helper, final Difficulty difficulty) {
		RpgCombat.setRoller(null);
		RpgCombat.setDiceRoller(null);
		MobsBridge.setFacts(null);
		helper.getLevel().getServer().setDifficulty(difficulty, true);
	}

	private static void assertLoss(final GameTestHelper helper, final String name, final float loss, final float expected) {
		RpgTestSupport.result(name, "loss=" + loss + " expected=" + expected);
		helper.assertTrue(Math.abs(loss - expected) < TOLERANCE, name + " loss=" + loss + " expected " + expected);
	}
}
