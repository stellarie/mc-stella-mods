package com.stella.mobs;

import com.stella.mobs.config.ScalingConfigStore;
import com.stella.mobs.scaling.ArmorNormalizer;
import com.stella.mobs.scaling.MobPiercing;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A mob with both the no-roll tag and the self-scaled tag keeps the health its owner set, but its hits follow its
 * threat level. A mob with only the no-roll tag keeps raw hits.
 */
public class SelfScaledGameTest {
	private static final int THREAT = 60;
	private static final float HIT = 4.0F;

	@GameTest
	public void selfScaledHitsTakeTheLevelPath(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double expected = HIT * SpawnScaling.damageMultiplier(THREAT) + SpawnScaling.tailFlat(THREAT);
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, true);
		float melee = loss(helper, level.damageSources().mobAttack(zombie));
		Mob skeleton = mob(helper, EntityTypes.SKELETON, true);
		float arrow = loss(helper, level.damageSources().arrow(new Arrow(level, skeleton, new ItemStack(Items.ARROW), null), skeleton));
		result("selfScaledHitsTakeTheLevelPath", "melee=" + melee + " arrow=" + arrow + " expected=" + expected);
		helper.assertTrue(Math.abs(melee - expected) < 0.05, "a self-scaled melee hit lost " + melee + ", expected " + expected);
		helper.assertTrue(Math.abs(arrow - expected) < 0.05, "a self-scaled arrow lost " + arrow + ", expected " + expected);
		helper.succeed();
	}

	@GameTest
	public void noSpawnRollAloneStaysRaw(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, false);
		float melee = loss(helper, level.damageSources().mobAttack(zombie));
		result("noSpawnRollAloneStaysRaw", "melee=" + melee);
		helper.assertTrue(Math.abs(melee - HIT) < 0.001F, "a no-roll melee hit was scaled: " + melee);
		helper.succeed();
	}

	@GameTest
	public void armorAndPiercingFollowTheLevel(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DamageSource owned = level.damageSources().mobAttack(mob(helper, EntityTypes.ZOMBIE, true));
		DamageSource raw = level.damageSources().mobAttack(mob(helper, EntityTypes.ZOMBIE, false));
		double scale = ArmorNormalizer.scale(owned);
		float pierce = MobPiercing.share(owned);
		float expectedPierce = MobPiercing.share(THREAT, ScalingConfigStore.get());
		result("armorAndPiercingFollowTheLevel", "scale=" + scale + " pierce=" + pierce + " rawScale=" + ArmorNormalizer.scale(raw)
			+ " rawPierce=" + MobPiercing.share(raw));
		helper.assertTrue(Math.abs(scale - SpawnScaling.damageMultiplier(THREAT)) < 0.001, "armor scale " + scale);
		helper.assertTrue(Math.abs(pierce - expectedPierce) < 0.0001F, "piercing " + pierce + ", expected " + expectedPierce);
		helper.assertTrue(ArmorNormalizer.scale(raw) == 1.0 && MobPiercing.share(raw) == 0.0F, "a no-roll mob got the level path");
		helper.succeed();
	}

	@GameTest
	public void spawnScalingKeepsTheOwnersHealth(final GameTestHelper helper) {
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, true);
		zombie.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2.0, 2.5)));
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0);
		helper.getLevel().addFreshEntity(zombie);
		zombie.setHealth(50.0F);
		SpawnScaling.apply(zombie);
		result("spawnScalingKeepsTheOwnersHealth", "max=" + zombie.getMaxHealth());
		helper.assertTrue(Math.abs(zombie.getMaxHealth() - 50.0F) < 0.001F, "spawn scaling changed the owner's health: " + zombie.getMaxHealth());
		helper.succeed();
	}

	private static <T extends Mob> Mob mob(final GameTestHelper helper, final EntityType<T> type, final boolean selfScaled) {
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		if (selfScaled) {
			mob.addTag(StellaMobs.SELF_SCALED_TAG);
		}
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(0, ThreatState.MIN_VARIATION, 0, THREAT));
		return mob;
	}

	private static float loss(final GameTestHelper helper, final DamageSource source) {
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 2, 2, 2);
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			victim.setItemSlot(slot, ItemStack.EMPTY);
		}
		victim.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		victim.setHealth(victim.getMaxHealth());
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), source, HIT);
		return before - victim.getHealth();
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, detail);
	}
}
