package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A staff's normal attack: MATK damage with no crit, and imbued books that proc at 20 percent and 75 percent strength. */
public class StaffNormalAttackGameTest {
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final double MATK_HIT = 7.0;
	private static final double CRIT_BAIT = 99.0;

	@GameTest(maxTicks = 100)
	public void aStaffAttackDealsMatkAndNeverCrits(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));
		Mob zombie = target(helper);
		RpgBridge.Stats stub = new RpgBridge.Stats() {
			@Override
			public double power(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double cooldown(final net.minecraft.world.entity.player.Player player) {
				return 1.0;
			}

			@Override
			public double matkHit(final LivingEntity who, final double skillPercent) {
				return MATK_HIT;
			}

			@Override
			public double skillDamage(final LivingEntity who, final String statKey, final double skillPercent) {
				return CRIT_BAIT;
			}

			@Override
			public double spellDamage(final LivingEntity who, final double skillPercent) {
				return CRIT_BAIT;
			}
		};
		{
			helper.runAfterDelay(5L, () -> {
				RpgBridge.setStats(stub);
				for (int i = 0; i < 20; i++) {
					caster.doTick();
				}
				float before = zombie.getHealth();
				InteractionResult melee = AttackEntityCallback.EVENT.invoker().interact(caster, helper.getLevel(), InteractionHand.MAIN_HAND, zombie, null);
				float dealt = before - zombie.getHealth();
				caster.resetAttackStrengthTicker();
				zombie.damageCooldownTime = 0;
				float mid = zombie.getHealth();
				AttackEntityCallback.EVENT.invoker().interact(caster, helper.getLevel(), InteractionHand.MAIN_HAND, zombie, null);
				float spent = mid - zombie.getHealth();
				RpgBridge.setStats(null);
				helper.assertTrue(spent > 0.0F && Math.abs(spent - 0.2 * MATK_HIT) < 1.0E-3, "an empty charge dealt " + spent + ", not 20 percent of " + MATK_HIT);
				helper.assertTrue(melee == InteractionResult.FAIL, "the staff melee blow was not cancelled: " + melee);
				helper.assertTrue(Math.abs(dealt - MATK_HIT) < 1.0E-3, "the bolt dealt " + dealt + ", not the MATK hit " + MATK_HIT);
				helper.getLevel().getServer().getPlayerList().remove(caster);
				helper.succeed();
			});
		}
	}

	@GameTest(maxTicks = 100)
	public void aStaffProcsAtTwentyPercentAndThreeQuarterStrength(final GameTestHelper helper) {
		ServerPlayer caster = caster(helper);
		ItemStack staff = new ItemStack(Items.BLAZE_ROD);
		staff.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(SPARK_SHOT)));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(List.of(SPARK_SHOT)));
		Mob zombie = target(helper);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		int[] casts = {0};
		SpellImbue.addRecorder((who, spell, victim) -> {
			if (who == caster) {
				casts[0]++;
			}
		});
		helper.runAfterDelay(5L, () -> {
			double staffPower;
			int belowTen;
			int aboveTwenty;
			double swordPower;
			int doubled;
			SpellCasting.DoublecastRoll priorRoll = SpellCasting.doublecastRoll();
			try {
				SpellImbue.setLukSource((holder, key) -> 0.0);
				caster.setItemInHand(InteractionHand.MAIN_HAND, staff);
				// 0.15 is above the plain 10 percent and below the staff 20 percent.
				SpellImbue.setProcRoll(() -> 0.15);
				StaffBolt.fire(caster);
				belowTen = casts[0];
				staffPower = SpellImbue.lastCastPower;
				SpellImbue.setProcRoll(() -> 0.25);
				SpellImbue.clearCooldowns(caster.getUUID());
				zombie.damageCooldownTime = 0;
				StaffBolt.fire(caster);
				aboveTwenty = casts[0] - belowTen;
				caster.setItemInHand(InteractionHand.MAIN_HAND, sword);
				SpellImbue.setProcRoll(() -> 0.05);
				zombie.damageCooldownTime = 0;
				zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(caster), 4.0F);
				swordPower = SpellImbue.lastCastPower;
				// A staff that doublecasts at 100 percent lands one proc twice, and the second landing procs nothing.
				net.minecraft.nbt.CompoundTag profile = new net.minecraft.nbt.CompoundTag();
				profile.putString("effect", "doublecast");
				profile.putInt("base", 100);
				net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
				root.put(StaffPower.PROFILE_KEY, profile);
				staff.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
				caster.setItemInHand(InteractionHand.MAIN_HAND, staff);
				SpellImbue.setProcRoll(() -> 0.05);
				SpellImbue.clearCooldowns(caster.getUUID());
				zombie.damageCooldownTime = 0;
				zombie.setHealth(zombie.getMaxHealth());
				SpellCasting.setDoublecastRoll(who -> true);
				int castsBefore = casts[0];
				int landedBefore = SpellCasting.doublecasts;
				StaffBolt.fire(caster);
				doubled = SpellCasting.doublecasts - landedBefore;
				helper.assertTrue(casts[0] - castsBefore == 1, "the doublecast proc recorded " + (casts[0] - castsBefore) + " casts; first two procs " + belowTen + "/" + aboveTwenty + " staff " + staffPower + " sword " + swordPower + " hp " + zombie.getHealth());
			} finally {
				SpellImbue.setProcRoll(() -> 0.0);
				SpellImbue.setLukSource(null);
				SpellCasting.setDoublecastRoll(priorRoll);
			}
			helper.assertTrue(belowTen == 1, "a roll of 0.15 cast " + belowTen + " times, the staff chance is 20 percent");
			helper.assertTrue(aboveTwenty == 0, "a roll of 0.25 still cast " + aboveTwenty + " times");
			helper.assertTrue(Math.abs(staffPower - StaffPower.powerMultiplier(staff) * SpellImbue.STAFF_STRENGTH) < 1.0E-6
				&& Math.abs(swordPower - SpellImbue.IMBUE_POWER) < 1.0E-6,
				"the staff proc power " + staffPower + " is not 75 percent of " + StaffPower.powerMultiplier(staff) + "; the sword proc is " + swordPower);
			helper.assertTrue(doubled == 1, "a proc with doublecast at 100 percent landed twice " + doubled + " times extra, not once");
			helper.getLevel().getServer().getPlayerList().remove(caster);
			helper.succeed();
		});
	}

	private static ServerPlayer caster(final GameTestHelper helper) {
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static Mob target(final GameTestHelper helper) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 6).withNoFreeWill().spawn();
		zombie.setNoGravity(true);
		return zombie;
	}
}
