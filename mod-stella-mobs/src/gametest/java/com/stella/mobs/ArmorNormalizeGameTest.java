package com.stella.mobs;

import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.ThreatState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Armor judges a mob's hit at the mob's unscaled damage: an armored target keeps the same taken share against a level 0
 * and a level 60 zombie, and armor wears as if from the base hit. A hit from a mob without level scaling is unchanged.
 */
public class ArmorNormalizeGameTest {
	private static final String NO_ROLLS = "{ \"entities\": {}, \"rolls\": { \"roll_chance\": 0.0, \"elite_chance\": 0.0 } }";

	@GameTest(maxTicks = 40)
	public void shareIndependentOfLevel(final GameTestHelper helper) {
		var previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(NO_ROLLS));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
		ServerLevel level = helper.getLevel();
		Zombie low = attacker(helper, 0, 1.5);
		Zombie high = attacker(helper, 60, 3.5);
		float lowShare = takenShare(helper, low, 5.5);
		float highShare = takenShare(helper, high, 7.5);
		// Only a player's armor wears in vanilla, so the wear check uses a survival player.
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		net.minecraft.server.level.ServerPlayer wearer = Arena.addSurvivalPlayer(helper);
		wearer.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		wearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000.0);
		wearer.setHealth(100000.0F);
		wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		wearer.hurtServer(level, level.damageSources().mobAttack(high), (float) high.getAttributeValue(Attributes.ATTACK_DAMAGE));
		int wear = wearer.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
		// Piercing still strips its share of armor; the hit itself is judged at the base damage.
		float pierced = 20.0F * (1.0F - com.stella.mobs.scaling.MobPiercing.share(level.damageSources().mobAttack(high)));
		float base = (float) (high.getAttributeValue(Attributes.ATTACK_DAMAGE) / com.stella.mobs.scaling.SpawnScaling.damageMultiplier(60));
		float expected = 1.0F - net.minecraft.util.Mth.clamp(pierced - base / 2.0F, pierced * 0.2F, 20.0F) / 25.0F;
		StellaMobs.LOGGER.info("[BEHAVIOR] test=armorNormalize RESULT low={} high={} expectedHigh={} piercedArmor={} base={} wear={}",
			lowShare, highShare, expected, pierced, base, wear);
		helper.assertTrue(lowShare > 0.0F && lowShare < 0.5F, "armor 20 let " + lowShare + " of a base hit through");
		helper.assertTrue(Math.abs(highShare - expected) < 0.01F, "a level 60 hit kept " + highShare + ", expected " + expected);
		helper.assertTrue(wear >= 1 && wear <= 2, "a level 60 hit wore the chestplate by " + wear);
		helper.succeed();
	}

	/** The taken share of one hit on a victim with 20 armor and nothing else that reduces damage. */
	private static float takenShare(final GameTestHelper helper, final Zombie attacker, final double x) {
		Zombie victim = victim(helper, x);
		victim.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
		float hit = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float before = victim.getHealth();
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), hit);
		return (before - victim.getHealth()) / hit;
	}

	private static Zombie attacker(final GameTestHelper helper, final int threat, final double x) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		((AttachmentTarget) zombie).setAttached(ThreatState.TYPE, new ThreatState(threat, 0, 0, 0));
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 1.0, 1.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static Zombie victim(final GameTestHelper helper, final double x) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1.0, 4.5));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000.0);
		zombie.setHealth(100000.0F);
		return zombie;
	}
}
