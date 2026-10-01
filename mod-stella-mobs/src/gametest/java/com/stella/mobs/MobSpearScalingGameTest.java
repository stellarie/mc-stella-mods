package com.stella.mobs;

import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Regression guard: a mob's spear jab already reads its scaled attack, so it is melee and takes no second multiplier. */
public class MobSpearScalingGameTest {
	private static final float HIT = 4.0F;

	@GameTest
	public void spearCountsAsMelee(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
		((AttachmentTarget) attacker).setAttached(ThreatState.TYPE, new ThreatState(60, 0, 0, 0));
		Zombie meleeVictim = victim(helper, 3.5);
		Zombie spearVictim = victim(helper, 5.5);
		float meleeBefore = meleeVictim.getHealth();
		float spearBefore = spearVictim.getHealth();
		meleeVictim.hurtServer(level, level.damageSources().mobAttack(attacker), HIT);
		DamageSource spear = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SPEAR), attacker);
		spearVictim.hurtServer(level, spear, HIT);
		float melee = meleeBefore - meleeVictim.getHealth();
		float spearTaken = spearBefore - spearVictim.getHealth();
		StellaMobs.LOGGER.info("[BEHAVIOR] test=mobSpearScaling RESULT melee={} spear={}", melee, spearTaken);
		helper.assertTrue(melee > 0.0F && Math.abs(melee - spearTaken) < 0.01F, "a melee hit took " + melee + " and a spear hit " + spearTaken);
		helper.succeed();
	}

	private static Zombie victim(final GameTestHelper helper, final double x) {
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, 1.5));
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		victim.setHealth(1000.0F);
		victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return victim;
	}
}
