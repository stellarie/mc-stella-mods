package com.stella.mobs;

import com.stella.mobs.behavior.WitchElite;
import com.stella.mobs.gear.GearTier;
import com.stella.mobs.behavior.WitchOffensiveThrowGoal;
import com.stella.mobs.behavior.WitchPotions;
import com.stella.mobs.behavior.WitchSelfHealGoal;
import com.stella.mobs.behavior.WitchSupportHealGoal;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.SpawnScaling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The witch kit on a headless rig: invisibility on aggro, the five offensive potions, the two heal
 * thresholds, the priority order, the burst, the elite variant, and the death drop.
 */
public class WitchGameTest {
	private static final String CONFIG = """
		{
			"version": 1,
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";
	private static final int SPAWN_Y = 3;
	private static final long NIGHT_TIME = 18000L;
	private static final int MAX_TICKS = 200;
	private static final long THRESHOLD_TICKS = 60L;
	private static final int BURST_WINDOW_TICKS = WitchOffensiveThrowGoal.BURST_SPACING_TICKS * WitchOffensiveThrowGoal.BURST_SIZE + 4;
	private static final float WITCH_BASE_HEALTH = 26.0F;
	private static final double WATCH_RADIUS = 40.0;
	private static final double EPSILON = 1.0e-6;

	@GameTest(maxTicks = MAX_TICKS)
	public void witchTurnsInvisibleOnAggro(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			helper.assertTrue(
				witch.hasEffect(MobEffects.INVISIBILITY),
				"the witch never turned invisible: " + watch.summary() + " goals: " + goals(witch)
			);
			result("witchTurnsInvisibleOnAggro", "invisibleTicks=" + invisibilityTicks(witch) + " " + watch.summary());
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchThrowsHarming(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(0), "witchThrowsHarming");
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchThrowsPoison(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(1), "witchThrowsPoison");
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchThrowsBlindness(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(3), "witchThrowsBlindness");
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchThrowsSlowness(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(2), "witchThrowsSlowness");
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchThrowsLevitation(final GameTestHelper helper) {
		assertOffensiveThrow(helper, WitchPotions.offensive().get(4), "witchThrowsLevitation");
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchHealsAllyWithoutAggro(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie ally = enemy(helper, 7, 2);
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		boolean[] allyHealed = healDetector(helper, ally);
		ally.setHealth(ally.getMaxHealth() * 0.19F);

		helper.succeedWhen(() -> {
			helper.assertTrue(allyHealed[0], "the ally was never healed: " + watch.summary() + " goals: " + goals(witch));
			helper.assertTrue(ally.getLastHurtByMob() == null, "the support throw drew aggro on the ally");
			helper.assertTrue(ally.getTarget() != witch, "the ally turned on the witch");
			result(
				"witchHealsAllyWithoutAggro",
				"allyHealed=" + allyHealed[0] + " allyLastHurtBy=" + (ally.getLastHurtByMob() == null ? "none" : "set")
					+ " allyTarget=" + (ally.getTarget() == null ? "none" : "set") + " " + watch.summary()
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchSelfHealsBelowThirtyPercent(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		Holder<MobEffect> healing = WitchPotions.healing().effect();
		witch.setHealth((float) (witch.getMaxHealth() * 0.31));

		helper.runAfterDelay(THRESHOLD_TICKS, () -> {
			helper.assertFalse(watch.saw(healing), "the witch healed itself above the threshold: " + watch.summary());
			witch.setHealth((float) (witch.getMaxHealth() * 0.29));
			helper.runAfterDelay(THRESHOLD_TICKS, () -> {
				helper.assertTrue(watch.saw(healing), "the witch never healed itself below the threshold: " + watch.summary());
				result("witchSelfHealsBelowThirtyPercent", "threshold=" + WitchSelfHealGoal.HEALTH_FRACTION + " " + watch.summary());
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchHealsAllyBelowTwentyPercent(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie ally = enemy(helper, 7, 2);
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		boolean[] allyHealed = healDetector(helper, ally);
		Holder<MobEffect> healing = WitchPotions.healing().effect();
		ally.setHealth(ally.getMaxHealth() * 0.21F);

		helper.runAfterDelay(THRESHOLD_TICKS, () -> {
			helper.assertFalse(allyHealed[0], "the ally was healed above the threshold: " + watch.summary());
			allyHealed[0] = false;
			ally.setHealth(ally.getMaxHealth() * 0.19F);
			helper.runAfterDelay(THRESHOLD_TICKS, () -> {
				helper.assertTrue(allyHealed[0], "the ally was never healed below the threshold: " + watch.summary());
				result("witchHealsAllyBelowTwentyPercent", "threshold=" + WitchSupportHealGoal.ALLY_HEALTH_FRACTION + " " + watch.summary());
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchSelfHealWinsPriority(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		Zombie ally = enemy(helper, 7, 2);
		helper.onEachTick(() -> witch.setTarget(enemy));
		boostWitch(witch);
		PotionWatch watch = watch(helper, witch);
		ally.setHealth(ally.getMaxHealth() * 0.19F);
		float woundedAlly = ally.getHealth();
		witch.setHealth((float) (witch.getMaxHealth() * 0.29));

		helper.succeedWhen(() -> {
			helper.assertTrue(watch.saw(WitchPotions.healing().effect()), "the witch never healed at all: " + watch.summary());
			helper.assertTrue(
				WitchPotions.healing().effect().equals(watch.firstCombatThrow()),
				"the first action was not the self heal: " + watch.summary() + " goals: " + goals(witch)
			);
			helper.assertTrue(ally.getHealth() <= woundedAlly, "the ally heal beat the self heal");
			result(
				"witchSelfHealWinsPriority",
				"firstCombatThrow=" + watch.firstCombatThrow() + " allyStillWounded=" + (ally.getHealth() <= woundedAlly) + " " + watch.summary()
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchBurstThrowsThreePotions(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			helper.assertTrue(
				watch.offensiveCount() >= WitchOffensiveThrowGoal.BURST_SIZE,
				"the burst never emitted " + WitchOffensiveThrowGoal.BURST_SIZE + " potions: " + watch.summary() + " goals: " + goals(witch)
			);
			helper.assertTrue(
				watch.maxOffensiveInWindow(BURST_WINDOW_TICKS) >= WitchOffensiveThrowGoal.BURST_SIZE,
				"no " + BURST_WINDOW_TICKS + " tick window held " + WitchOffensiveThrowGoal.BURST_SIZE + " offensive throws: " + watch.summary()
			);
			result(
				"witchBurstThrowsThreePotions",
				"maxInWindow=" + watch.maxOffensiveInWindow(BURST_WINDOW_TICKS) + " window=" + BURST_WINDOW_TICKS + " " + watch.summary()
			);
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void eliteWitchHasBoostedHealthAndAura(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		WitchElite.mark(witch, GearTier.DIAMOND);
		float expected = (float) WitchElite.eliteHealth(WITCH_BASE_HEALTH);
		helper.succeedWhen(() -> {
			// R12 scales the maximum too, so divide it out and assert the elite multiplier alone.
			float unscaled = (float) (witch.getMaxHealth() / SpawnScaling.healthMultiplierOf(witch));
			helper.assertTrue(
				Math.abs(unscaled - expected) < EPSILON,
				"the elite witch has " + unscaled + " health before scaling, not " + expected
					+ " (maximum " + witch.getMaxHealth() + ")"
			);
			helper.assertTrue(
				!blindedPlayers(helper).equals("none"),
				"the aura never blinded the player inside " + WitchElite.AURA_RADIUS + " blocks"
			);
			result(
				"eliteWitchHasBoostedHealthAndAura",
				"maxHealth=" + witch.getMaxHealth() + " expected=" + expected + " auraRadius=" + WitchElite.AURA_RADIUS + " " + blindedPlayers(helper)
			);
		});
	}

	private static String blindedPlayers(final GameTestHelper helper) {
		StringBuilder text = new StringBuilder();

		for (ServerPlayer player : helper.getLevel().players()) {
			if (player.hasEffect(MobEffects.BLINDNESS)) {
				text.append(player.getScoreboardName()).append(' ');
			}
		}

		return text.length() == 0 ? "none" : text.toString();
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchDropsEveryCarriedPotion(final GameTestHelper helper) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		Vec3 deathSpot = witch.position();

		helper.runAfterDelay(20L, () -> {
			witch.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
			helper.runAfterDelay(5L, () -> {
				AABB box = new AABB(deathSpot, deathSpot).inflate(8.0);
				List<ItemEntity> all = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, item -> true);
				List<ItemEntity> drops = helper.getLevel()
					.getEntitiesOfClass(ItemEntity.class, box, item -> WitchPotions.isModThrow(item.getItem()));
				Set<String> dropped = new HashSet<>();

				for (ItemEntity drop : drops) {
					dropped.add(effectName(drop.getItem()));
				}

				result(
					"witchDropsEveryCarriedPotion",
					"alive=" + witch.isAlive() + " carried=" + WitchPotions.CARRIED.size() + " nearbyItems=" + all.size() + " potions=" + drops.size()
						+ " unique=" + dropped.size()
				);
				helper.assertFalse(witch.isAlive(), "the witch survived the killing blow");
				helper.assertTrue(
					drops.size() == WitchPotions.CARRIED.size(),
					"the witch dropped " + drops.size() + " potions, not " + WitchPotions.CARRIED.size() + ", nearby items " + all.size()
				);
				helper.assertTrue(
					dropped.size() == WitchPotions.CARRIED.size(),
					"the drops did not cover every carried potion: " + dropped
				);
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = MAX_TICKS)
	public void witchEliteRateIsMeasured(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20260923L);
		int rolls = 2000;
		int elites = 0;

		for (int i = 0; i < rolls; i++) {
			if (WitchElite.roll(random, WitchElite.DEFAULT_CHANCE)) {
				elites++;
			}
		}

		double rate = (double) elites / rolls;
		result("witchEliteRateIsMeasured", "rolls=" + rolls + " elites=" + elites + " rate=" + String.format(java.util.Locale.ROOT, "%.4f", rate) + " chance=" + WitchElite.DEFAULT_CHANCE);
		helper.assertTrue(
			rate > 0.08 && rate < 0.12,
			"the measured elite rate " + rate + " is not near the shipped " + WitchElite.DEFAULT_CHANCE
		);
		helper.succeed();
	}

	// --- helpers ---

	private static void assertOffensiveThrow(final GameTestHelper helper, final WitchPotions.Carried carried, final String test) {
		Witch witch = prepare(helper, 2, 2);
		Zombie enemy = enemy(helper, 7, 7);
		helper.onEachTick(() -> witch.setTarget(enemy));
		PotionWatch watch = watch(helper, witch);

		helper.succeedWhen(() -> {
			helper.assertTrue(
				watch.saw(carried.effect()),
				"no " + carried.name() + " potion was thrown: " + watch.summary() + " goals: " + goals(witch)
			);
			result(test, "potion=" + carried.name() + " modThrows=" + watch.count() + " " + watch.summary());
		});
	}

	private static Witch prepare(final GameTestHelper helper, final int x, final int z) {
		GearConfigStore.set(GearConfigParser.parse(CONFIG));
		helper.setTime(NIGHT_TIME);
		buildFloor(helper);
		addPlayer(helper, 6.5, 6.5);
		return helper.spawnMob(EntityTypes.WITCH, x, SPAWN_Y, z).spawn();
	}

	private static Zombie enemy(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = helper.spawnMob(EntityTypes.ZOMBIE, x, SPAWN_Y, z).withNoFreeWill().spawn();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return zombie;
	}

	private static PotionWatch watch(final GameTestHelper helper, final Witch witch) {
		PotionWatch watch = new PotionWatch(helper, witch);
		helper.onEachTick(watch::tick);
		return watch;
	}

	/**
	 * A big health pool for the witch, so a stray poison tick cannot walk it across its own heal
	 * threshold inside a 60 tick window.
	 */
	private static void boostWitch(final Witch witch) {
		witch.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		witch.setHealth(1000.0F);
	}

	/**
	 * True once the watched mob's health jumps, which only a heal can do. Damage from a scattered
	 * vanilla splash would otherwise hide the heal by lowering the same bar.
	 */
	private static boolean[] healDetector(final GameTestHelper helper, final Mob mob) {
		boolean[] healed = {false};
		float[] last = {mob.getHealth()};
		helper.onEachTick(() -> {
			float current = mob.getHealth();
			if (current > last[0] + 0.5F) {
				healed[0] = true;
			}
			last[0] = current;
		});
		return healed;
	}

	private static void buildFloor(final GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	@SuppressWarnings("removal")
	private static ServerPlayer addPlayer(final GameTestHelper helper, final double x, final double z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.setPos(position.x, position.y, position.z);
		return player;
	}

	private static long invisibilityTicks(final Witch witch) {
		MobEffectInstance effect = witch.getEffect(MobEffects.INVISIBILITY);
		return effect == null ? 0L : effect.getDuration();
	}

	private static String effectName(final ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents == null ? "none" : contents.customEffects().toString();
	}

	private static String goals(final Mob mob) {
		StringBuilder text = new StringBuilder();

		for (var wrapped : mob.getGoalSelector().getAvailableGoals()) {
			text.append(wrapped.getGoal().getClass().getSimpleName()).append(':').append(wrapped.getPriority()).append(' ');
		}

		return text.toString();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}

	/** Records every mod-shaped thrown potion once, with the tick it appeared and its first effect. */
	private static final class PotionWatch {
		private final GameTestHelper helper;
		private final Witch witch;
		private final Set<Integer> seen = new HashSet<>();
		private final List<Holder<MobEffect>> effects = new ArrayList<>();
		private final List<Long> ticks = new ArrayList<>();
		private final List<String> spots = new ArrayList<>();
		private final List<Integer> tracked = new ArrayList<>();
		private final List<String> landings = new ArrayList<>();

		PotionWatch(final GameTestHelper helper, final Witch witch) {
			this.helper = helper;
			this.witch = witch;
		}

		void tick() {
			AABB box = this.witch.getBoundingBox().inflate(WATCH_RADIUS);

			for (ThrownSplashPotion potion : this.helper.getLevel().getEntitiesOfClass(ThrownSplashPotion.class, box, candidate -> true)) {
				if (potion.getOwner() != this.witch) {
					continue;
				}

				String spot = String.format(java.util.Locale.ROOT, "%.1f/%.1f/%.1f", potion.getX(), potion.getY(), potion.getZ());
				int tracked = this.tracked.indexOf(potion.getId());

				if (tracked >= 0) {
					this.landings.set(tracked, spot);
				}

				if (!this.seen.add(potion.getId())) {
					continue;
				}

				ItemStack stack = potion.getItem();
				if (!WitchPotions.isModThrow(stack)) {
					continue;
				}

				PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
				if (contents == null) {
					continue;
				}

				for (MobEffectInstance effect : contents.getAllEffects()) {
					this.effects.add(effect.getEffect());
					this.ticks.add(this.helper.getTick());
					this.spots.add(spot);

					if (this.tracked.size() < 3) {
						this.tracked.add(potion.getId());
						this.landings.add(spot);
					}

					break;
				}
			}
		}

		boolean saw(final Holder<MobEffect> effect) {
			return this.effects.contains(effect);
		}

		int count() {
			return this.effects.size();
		}

		Holder<MobEffect> firstModThrow() {
			return this.effects.isEmpty() ? null : this.effects.get(0);
		}

		/** The first throw that is a heal or an offensive potion, skipping the aggro invisibility. */
		Holder<MobEffect> firstCombatThrow() {
			for (Holder<MobEffect> effect : this.effects) {
				if (isOffensive(effect) || WitchPotions.healing().effect().equals(effect)) {
					return effect;
				}
			}

			return null;
		}

		String firstOffensiveOrSupport() {
			return this.effects.isEmpty() ? "none" : String.valueOf(this.effects.get(0));
		}

		int offensiveCount() {
			int count = 0;
			for (Holder<MobEffect> effect : this.effects) {
				if (isOffensive(effect)) {
					count++;
				}
			}
			return count;
		}

		/** The most offensive throws that ever landed inside one window, which is the burst size. */
		int maxOffensiveInWindow(final long windowTicks) {
			int best = 0;

			for (int i = 0; i < this.effects.size(); i++) {
				if (!isOffensive(this.effects.get(i))) {
					continue;
				}

				int count = 0;

				for (int j = i; j < this.effects.size(); j++) {
					if (isOffensive(this.effects.get(j)) && this.ticks.get(j) - this.ticks.get(i) <= windowTicks) {
						count++;
					}
				}

				best = Math.max(best, count);
			}

			return best;
		}

		private static boolean isOffensive(final Holder<MobEffect> effect) {
			for (WitchPotions.Carried carried : WitchPotions.offensive()) {
				if (carried.effect().equals(effect)) {
					return true;
				}
			}

			return false;
		}

		String summary() {
			return "modThrows=" + this.effects.size() + " effects=" + this.effects + " spawns=" + this.spots + " landings=" + this.landings;
		}
	}
}
