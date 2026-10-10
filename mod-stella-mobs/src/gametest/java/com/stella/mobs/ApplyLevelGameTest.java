package com.stella.mobs;

import com.stella.mobs.scaling.ArmorNormalizer;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The {@code mod-stella-mobs:apply-level} share. A mob that another mod owns (it carries the no-roll tag) gets the level
 * scaling of a natural spawn at the level it is given, with no elite, boss or variation roll.
 */
public class ApplyLevelGameTest {
	private static final String KEY = "mod-stella-mobs:apply-level";

	@SuppressWarnings("unchecked")
	private static BiConsumer<Mob, Integer> share() {
		Object shared = FabricLoader.getInstance().getObjectShare().get(KEY);
		return shared instanceof BiConsumer<?, ?> consumer ? (BiConsumer<Mob, Integer>) consumer : null;
	}

	/** An owned mob, as the ruins mod builds one: the no-roll and self-scaled tags, not yet scaled. */
	private static Mob owned(final GameTestHelper helper) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.addTag(StellaMobs.SELF_SCALED_TAG);
		return mob;
	}

	/** A natural spawn at a level: no tag, no variation, scaled by the mod's own routine. */
	private static Mob natural(final GameTestHelper helper, final int level) {
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(level, 0, 0, 0));
		SpawnScaling.apply(mob);
		return mob;
	}

	/** The health a fresh victim loses to one real melee hit of the attacker. */
	private static float meleeLoss(final GameTestHelper helper, final Mob attacker) {
		ServerLevel level = helper.getLevel();
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.COW, 2, 2, 2);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		victim.damageCooldownTime = 0;
		attacker.doHurtTarget(level, victim);
		float lost = before - victim.getHealth();
		victim.discard();
		return lost;
	}

	private static void check(final GameTestHelper helper, final int level) {
		BiConsumer<Mob, Integer> apply = share();
		helper.assertTrue(apply != null, "the share " + KEY + " is not published");
		Mob given = owned(helper);
		apply.accept(given, level);
		Mob reference = natural(helper, level);
		double healthGiven = given.getMaxHealth();
		double healthReference = reference.getMaxHealth();
		float hitGiven = meleeLoss(helper, given);
		float hitReference = meleeLoss(helper, reference);
		double armorGiven = ArmorNormalizer.scale(helper.getLevel().damageSources().mobAttack(given));
		double armorReference = ArmorNormalizer.scale(helper.getLevel().damageSources().mobAttack(reference));
		StellaMobs.LOGGER.info("[BEHAVIOR] test=applyLevel RESULT level={} given={} reference={} health={}/{} hit={}/{} armorScale={}/{}",
			level, ThreatState.level(given), ThreatState.level(reference), healthGiven, healthReference, hitGiven, hitReference, armorGiven, armorReference);
		helper.assertTrue(ThreatState.level(given) == level, "the level is " + ThreatState.level(given) + ", not " + level);
		helper.assertTrue(ThreatState.get(given).variation() == 0, "the share left a variation of " + ThreatState.get(given).variation());
		helper.assertTrue(Math.abs(healthGiven - healthReference) < 0.01, "max health " + healthGiven + ", a natural spawn has " + healthReference);
		helper.assertTrue(level == 0 || healthGiven > 20.0, "the health did not rise with the level: " + healthGiven);
		helper.assertTrue(Math.abs(hitGiven - hitReference) < 0.01F * Math.max(1.0F, hitReference), "a hit takes " + hitGiven + ", a natural spawn's takes " + hitReference);
		helper.assertTrue(Math.abs(armorGiven - armorReference) < 0.001, "armor scale " + armorGiven + ", a natural spawn has " + armorReference);
		helper.assertTrue(given.getHealth() == given.getMaxHealth(), "the mob starts hurt");
		helper.assertTrue(!com.stella.mobs.behavior.EliteState.isElite(given) && !com.stella.mobs.behavior.EliteState.isBoss(given), "the share rolled an elite or a boss");
	}

	@GameTest
	public void aMobGivenALevelMatchesANaturalSpawnOfThatLevel(final GameTestHelper helper) {
		check(helper, 60);
		helper.succeed();
	}

	@GameTest
	public void aHighLevelFollowsTheNaturalCurvePastItsTail(final GameTestHelper helper) {
		check(helper, 400);
		helper.succeed();
	}

	@GameTest
	public void aSecondCallABadValueAndAMobTheNaturalRollOwnsChangeNothing(final GameTestHelper helper) {
		BiConsumer<Mob, Integer> apply = share();
		helper.assertTrue(apply != null, "the share " + KEY + " is not published");
		Mob mob = owned(helper);
		apply.accept(mob, 40);
		double health = mob.getMaxHealth();
		int level = ThreatState.level(mob);
		helper.assertTrue(level == 40 && health > 20.0, "the first call did not scale: level " + level + " health " + health);
		apply.accept(mob, 90);
		helper.assertTrue(ThreatState.level(mob) == 40 && mob.getMaxHealth() == health, "a second call changed the mob: level " + ThreatState.level(mob) + " health " + mob.getMaxHealth());

		Mob negative = owned(helper);
		Mob huge = owned(helper);
		double base = negative.getMaxHealth();
		apply.accept(negative, -5);
		apply.accept(huge, Integer.MAX_VALUE);
		helper.assertTrue(negative.getMaxHealth() == base && ThreatState.get(negative) == null, "a negative level scaled the mob");
		helper.assertTrue(huge.getMaxHealth() == base && ThreatState.get(huge) == null, "an absurd level scaled the mob");

		Mob plain = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		double plainBase = plain.getMaxHealth();
		apply.accept(plain, 70);
		helper.assertTrue(plain.getMaxHealth() == plainBase && ThreatState.get(plain) == null, "a mob without the no-roll tag was scaled");

		boolean threw = false;
		try {
			apply.accept(null, 10);
			apply.accept(owned(helper), null);
		} catch (RuntimeException e) {
			threw = true;
		}
		helper.assertTrue(!threw, "a null argument threw");
		helper.succeed();
	}
}
