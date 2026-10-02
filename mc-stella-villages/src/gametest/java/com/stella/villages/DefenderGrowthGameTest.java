package com.stella.villages;

import com.stella.mobs.behavior.FriendlyPatrol;
import com.stella.villages.patrol.DefenderGrowth;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A village defender grows by half a point with each kill of a mob or a player. With the rpg mod's object share, the
 * share grows an rpg stat. Without it, the defender gains a permanent modifier on one vanilla attribute, and the
 * total survives a save. A fellow defender and a friend of the village never count as a kill. Each test that
 * installs the share does so inside one tick, so no parallel test sees the stub.
 */
public class DefenderGrowthGameTest {
	private static final double EPSILON = 1.0e-9;
	private static final String STUB_STAT = "stub_stat";
	private static final long SEED = 20261002L;
	private static final int KILLS = 12;
	private static final int PICKS = 100;

	private record ShareCall(Mob defender, double points) {
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killCallsTheRpgShare(final GameTestHelper helper) {
		String test = "killCallsTheRpgShare";
		Mob guard = fixture(helper, test, PatrolState.Role.PILLAGER);
		if (guard == null) {
			return;
		}

		Mob victim = victim(helper, EntityTypes.ZOMBIE);
		List<ShareCall> calls = new ArrayList<>();
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		BiFunction<Mob, Double, String> stub = (defender, points) -> {
			calls.add(new ShareCall(defender, points));
			return STUB_STAT;
		};
		Object previous = share.put(DefenderGrowth.RPG_SHARE_KEY, stub);
		try {
			kill(helper, guard, victim);
		} finally {
			if (previous == null) {
				share.remove(DefenderGrowth.RPG_SHARE_KEY);
			} else {
				share.put(DefenderGrowth.RPG_SHARE_KEY, previous);
			}
		}

		PatrolTestSupport.result(test, "calls=" + calls.size() + " defenderIsTheKiller=" + (!calls.isEmpty() && calls.getFirst().defender() == guard)
			+ " points=" + (calls.isEmpty() ? "none" : String.valueOf(calls.getFirst().points())) + " fallbackTotals=" + DefenderGrowth.totals(guard));
		helper.assertValueEqual(Integer.valueOf(calls.size()), Integer.valueOf(1), "calls of the rpg share for one kill");
		helper.assertTrue(calls.getFirst().defender() == guard, "the share grew a mob that did not make the kill");
		helper.assertValueEqual(Double.valueOf(calls.getFirst().points()), Double.valueOf(DefenderGrowth.KILL_POINTS), "points sent to the share");
		helper.assertTrue(DefenderGrowth.totals(guard).isEmpty(), "the fallback grew the defender as well as the share: " + DefenderGrowth.totals(guard));
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killGrowsAnAttribute(final GameTestHelper helper) {
		String test = "killGrowsAnAttribute";
		Mob guard = fixture(helper, test, PatrolState.Role.PILLAGER);
		if (guard == null) {
			return;
		}

		helper.assertTrue(FabricLoader.getInstance().getObjectShare().get(DefenderGrowth.RPG_SHARE_KEY) == null,
			"an rpg share is present, so the case cannot test the fallback");
		guard.getRandom().setSeed(SEED);
		Map<String, Double> before = attributeValues(guard);
		kill(helper, guard, victim(helper, EntityTypes.ZOMBIE));
		Map<String, Double> afterOne = DefenderGrowth.totals(guard);
		helper.assertValueEqual(Integer.valueOf(afterOne.size()), Integer.valueOf(1), "attributes grown by one kill");
		String grown = afterOne.keySet().iterator().next();
		helper.assertValueEqual(afterOne.get(grown), Double.valueOf(DefenderGrowth.KILL_POINTS), "points one kill adds to " + grown);
		for (Holder<Attribute> attribute : DefenderGrowth.ATTRIBUTES) {
			String key = DefenderGrowth.key(attribute);
			double gained = guard.getAttributeValue(attribute) - before.get(key);
			double wanted = key.equals(grown) ? DefenderGrowth.KILL_POINTS : 0.0;
			helper.assertTrue(Math.abs(gained - wanted) < EPSILON, key + " rose by " + gained + " after one kill, not " + wanted);
		}

		for (int kill = 1; kill < KILLS; kill++) {
			kill(helper, guard, victim(helper, EntityTypes.ZOMBIE));
		}
		Map<String, Double> totals = DefenderGrowth.totals(guard);
		double sum = totals.values().stream().mapToDouble(Double::doubleValue).sum();
		PatrolTestSupport.result(test, "firstKillGrew=" + grown + " kills=" + KILLS + " totals=" + totals + " sum=" + sum);
		helper.assertTrue(Math.abs(sum - KILLS * DefenderGrowth.KILL_POINTS) < EPSILON, "the totals add up to " + sum + " after " + KILLS + " kills");
		for (Holder<Attribute> attribute : DefenderGrowth.ATTRIBUTES) {
			String key = DefenderGrowth.key(attribute);
			AttributeInstance instance = guard.getAttribute(attribute);
			long growthModifiers = instance.getModifiers().stream().filter(modifier -> modifier.id().getNamespace().equals(McStellaVillages.MOD_ID)).count();
			AttributeModifier modifier = instance.getModifier(DefenderGrowth.modifierId(attribute));
			double amount = modifier == null ? 0.0 : modifier.amount();
			helper.assertTrue(growthModifiers <= 1, key + " carries " + growthModifiers + " growth modifiers, so growth stacks");
			helper.assertTrue(Math.abs(amount - totals.getOrDefault(key, 0.0)) < EPSILON, key + " modifier is " + amount + ", not its total " + totals.getOrDefault(key, 0.0));
		}

		ServerLevel level = helper.getLevel();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		guard.saveWithoutId(output);
		Mob restored = (Mob) guard.getType().create(level, EntitySpawnReason.LOAD);
		restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
		PatrolTestSupport.result(test, "restoredTotals=" + DefenderGrowth.totals(restored));
		helper.assertValueEqual(DefenderGrowth.totals(restored), totals, "the totals after a save and a load");
		for (Holder<Attribute> attribute : DefenderGrowth.ATTRIBUTES) {
			helper.assertTrue(Math.abs(restored.getAttributeValue(attribute) - guard.getAttributeValue(attribute)) < EPSILON,
				DefenderGrowth.key(attribute) + " changed across a save and a load");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void growthPicksEveryAttribute(final GameTestHelper helper) {
		String test = "growthPicksEveryAttribute";
		Mob guard = fixture(helper, test, PatrolState.Role.PILLAGER);
		if (guard == null) {
			return;
		}

		guard.getRandom().setSeed(SEED);
		Mob victim = victim(helper, EntityTypes.ZOMBIE);
		Map<String, Integer> picks = new HashMap<>();
		for (int pick = 0; pick < PICKS; pick++) {
			picks.merge(String.valueOf(DefenderGrowth.grow(guard, victim)), 1, Integer::sum);
		}
		PatrolTestSupport.result(test, "picks=" + picks);
		int fair = PICKS / DefenderGrowth.ATTRIBUTES.size();
		for (Holder<Attribute> attribute : DefenderGrowth.ATTRIBUTES) {
			int count = picks.getOrDefault(DefenderGrowth.key(attribute), 0);
			helper.assertTrue(count >= fair / 2 && count <= fair * 2, DefenderGrowth.key(attribute) + " was picked " + count + " times in " + PICKS);
		}
		helper.assertValueEqual(Integer.valueOf(picks.size()), Integer.valueOf(DefenderGrowth.ATTRIBUTES.size()), "distinct picks");
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void onlyFoesGrowTheDefender(final GameTestHelper helper) {
		String test = "onlyFoesGrowTheDefender";
		Mob guard = fixture(helper, test, PatrolState.Role.PILLAGER);
		if (guard == null) {
			return;
		}

		ServerLevel level = helper.getLevel();
		Mob fellow = PatrolTestSupport.patrols(helper).stream().filter(mob -> mob != guard && PatrolState.role(mob) == PatrolState.Role.PILLAGER).findFirst().orElseThrow();
		// A fellow's hit does no harm,
		// so only its death can reach the listener.
		fellow.die(level.damageSources().mobAttack(guard));
		Mob friendlyPatrol = victim(helper, EntityTypes.ZOMBIE);
		friendlyPatrol.addTag(FriendlyPatrol.TAG);
		kill(helper, guard, friendlyPatrol);
		kill(helper, guard, victim(helper, EntityTypes.VILLAGER));
		kill(helper, guard, victim(helper, EntityTypes.IRON_GOLEM));
		PatrolTestSupport.result(test, "totalsAfterFriends=" + DefenderGrowth.totals(guard));
		helper.assertTrue(DefenderGrowth.totals(guard).isEmpty(), "a defender grew from a fellow or a friend of the village: " + DefenderGrowth.totals(guard));

		kill(helper, guard, victim(helper, EntityTypes.ZOMBIE));
		helper.assertTrue(Math.abs(growth(guard) - DefenderGrowth.KILL_POINTS) < EPSILON, "a defender did not grow from a mob: " + DefenderGrowth.totals(guard));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().instabuild = false;
		player.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		kill(helper, guard, player);
		PatrolTestSupport.result(test, "totalsAfterAMobAndAPlayer=" + DefenderGrowth.totals(guard));
		helper.assertTrue(Math.abs(growth(guard) - 2 * DefenderGrowth.KILL_POINTS) < EPSILON, "a defender did not grow from a player: " + DefenderGrowth.totals(guard));
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyMemberOfThePatrolGrows(final GameTestHelper helper) {
		String test = "everyMemberOfThePatrolGrows";
		if (fixture(helper, test, PatrolState.Role.HEAD) == null) {
			return;
		}

		List<String> grown = new ArrayList<>();
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			kill(helper, member, victim(helper, EntityTypes.ZOMBIE));
			if (Math.abs(growth(member) - DefenderGrowth.KILL_POINTS) < EPSILON) {
				grown.add(PatrolState.role(member).toString());
			}
		}
		PatrolTestSupport.result(test, "grown=" + grown);
		helper.assertValueEqual(Integer.valueOf(grown.size()), Integer.valueOf(PatrolTestSupport.patrols(helper).size()), "members that grew from a kill: " + grown);
		helper.assertTrue(grown.contains("HEAD") && grown.contains("VILLAGE_WITCH"), "the duke or the mage did not grow: " + grown);
		helper.succeed();
	}

	private static @Nullable Mob fixture(final GameTestHelper helper, final String test, final PatrolState.Role role) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return null;
		}
		Mob member = PatrolTestSupport.patrols(helper).stream().filter(mob -> PatrolState.role(mob) == role).findFirst().orElse(null);
		helper.assertTrue(member != null, "the village has no " + role);
		return member;
	}

	/** A victim with no rolls and no actions. */
	private static <T extends Mob> T victim(final GameTestHelper helper, final EntityType<T> type) {
		T mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		mob.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static void kill(final GameTestHelper helper, final Mob killer, final LivingEntity victim) {
		DamageSource source = helper.getLevel().damageSources().mobAttack(killer);
		// A new player takes no damage for 3 seconds,
		// and the patrol never harms a villager or golem.
		if (victim instanceof Player || victim instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
			|| victim instanceof net.minecraft.world.entity.animal.golem.AbstractGolem) {
			victim.setHealth(0.0F);
			victim.die(source);
		} else {
			victim.hurtServer(helper.getLevel(), source, Float.MAX_VALUE);
		}
		helper.assertTrue(victim.isDeadOrDying(), PatrolTestSupport.typeId(victim) + " survived the hit, so the case proves nothing");
	}

	private static double growth(final Mob defender) {
		return DefenderGrowth.totals(defender).values().stream().mapToDouble(Double::doubleValue).sum();
	}

	private static Map<String, Double> attributeValues(final Mob mob) {
		Map<String, Double> values = new HashMap<>();
		for (Holder<Attribute> attribute : DefenderGrowth.ATTRIBUTES) {
			values.put(DefenderGrowth.key(attribute), mob.getAttributeValue(attribute));
		}
		return values;
	}
}
