package com.stella.villages;

import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.item.ApprenticeChestplate;
import com.stella.villages.mission.SlayerContract;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The slayer reward chestplate, its dark guard, and the level 20 enraged slime of the slayer contract. */
public final class ApprenticeChestplateGameTest {
	private static final float HIT = 10.0F;

	@GameTest(maxTicks = 20)
	public void stats(final GameTestHelper helper) {
		ItemStack chest = new ItemStack(ApprenticeChestplate.ITEM);
		Zombie wearer = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1.0, 2.5));
		double baseArmor = wearer.getAttributeValue(Attributes.ARMOR);
		wearer.setItemSlot(EquipmentSlot.CHEST, chest.copy());
		helper.runAfterDelay(2, () -> checkStats(helper, chest, wearer, baseArmor));
	}

	private static void checkStats(final GameTestHelper helper, final ItemStack chest, final Zombie wearer, final double baseArmor) {
		double[] fromItem = new double[2];
		chest.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).forEach(EquipmentSlot.CHEST,
			(attribute, modifier) -> {
				if (attribute.equals(Attributes.ARMOR)) {
					fromItem[0] += modifier.amount();
				} else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) {
					fromItem[1] += modifier.amount();
				}
			});
		double armor = fromItem[0];
		double toughness = fromItem[1];
		boolean worn = wearer.getAttributeValue(Attributes.ARMOR) >= baseArmor + armor;
		List<String> supporting = new ArrayList<>();
		for (Holder<Enchantment> enchantment : helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
			if (enchantment.value().canEnchant(chest)) {
				supporting.add(enchantment.getRegisteredName());
			}
		}
		boolean repairs = chest.isValidRepairItem(new ItemStack(Items.IRON_INGOT));
		PatrolTestSupport.result("apprenticeChestplateStats", "maxDamage=" + chest.getMaxDamage() + " armor=" + armor + " toughness=" + toughness
			+ " repairs=" + repairs + " enchantable=" + chest.has(DataComponents.ENCHANTABLE) + " supporting=" + supporting);
		helper.assertTrue(chest.getMaxDamage() == ApprenticeChestplate.DURABILITY && chest.getMaxStackSize() == 1, "max damage " + chest.getMaxDamage());
		helper.assertTrue(armor == 6.0 && toughness == 0.0 && worn, "chest armor " + armor + ", toughness " + toughness + ", worn " + worn);
		helper.assertTrue(repairs, "iron ingots do not repair it");
		helper.assertTrue(!chest.has(DataComponents.ENCHANTABLE) && !chest.isEnchantable() && supporting.isEmpty(),
			"enchantments that apply: " + supporting);
		helper.succeed();
	}

	/** The guard is 3% per light level below 15, read where the wearer stands, and only from the chest slot. */
	@GameTest(maxTicks = 100)
	public void darkReduction(final GameTestHelper helper) {
		float[] byLight = {ApprenticeChestplate.reduction(15), ApprenticeChestplate.reduction(10), ApprenticeChestplate.reduction(0)};
		helper.assertTrue(byLight[0] == 0.0F && Math.abs(byLight[1] - 0.15F) < 1.0E-6F && Math.abs(byLight[2] - 0.45F) < 1.0E-6F,
			"the reduction by light is " + java.util.Arrays.toString(byLight));
		ServerLevel level = helper.getLevel();
		for (int x = 0; x <= 4; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = 0; z <= 4; z++) {
					boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
					helper.setBlock(new BlockPos(x + 1, y, z + 1), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		Zombie worn = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1.0, 3.5));
		worn.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ApprenticeChestplate.ITEM));
		Zombie control = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1.0, 2.5));
		control.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		Zombie held = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1.0, 3.5));
		held.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ApprenticeChestplate.ITEM));
		helper.runAfterDelay(40, () -> {
			int light = level.getMaxLocalRawBrightness(worn.blockPosition());
			float expected = ApprenticeChestplate.reduction(light);
			float wornReduction = ApprenticeChestplate.reduction(worn);
			float heldReduction = ApprenticeChestplate.reduction(held);
			float wornBefore = worn.getHealth();
			float controlBefore = control.getHealth();
			worn.hurtServer(level, level.damageSources().magic(), HIT);
			control.hurtServer(level, level.damageSources().magic(), HIT);
			float taken = wornBefore - worn.getHealth();
			float controlTaken = controlBefore - control.getHealth();
			PatrolTestSupport.result("apprenticeChestplateDark", "light=" + light + " reduction=" + wornReduction + " held=" + heldReduction
				+ " taken=" + taken + " control=" + controlTaken);
			helper.assertTrue(light < 15 && wornReduction == expected && heldReduction == 0.0F,
				"light " + light + " gives reduction " + wornReduction + ", held " + heldReduction);
			helper.assertTrue(controlTaken > 0.0F && Math.abs(taken - controlTaken * (1.0F - expected)) < 0.01F,
				"the worn chestplate took " + taken + " where an iron chestplate took " + controlTaken);
			helper.succeed();
		});
	}

	/** In the Overworld the enraged slime is exactly level 20, with infinite Speed II and Strength II. */
	@GameTest
	public void slayerSlimeIsLevelTwenty(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MissionRecord mission = new MissionRecord(UUID.randomUUID(), UUID.randomUUID(), SlayerContract.TARGET, helper.absolutePos(new BlockPos(2, 1, 2)),
			Optional.empty(), MissionStatus.TARGET_PENDING, Optional.empty(), level.dimension(), MissionKind.SLAYER, 1, List.of());
		VillageWorldData.get(level).addMission(mission);
		Mob slime = SlayerContract.spawnTarget(level, mission);
		helper.assertTrue(slime != null, "the enraged slime did not spawn");
		MobEffectInstance speed = slime.getEffect(MobEffects.SPEED);
		MobEffectInstance strength = slime.getEffect(MobEffects.STRENGTH);
		int mobLevel = ThreatState.level(slime);
		String name = slime.getCustomName() == null ? "" : slime.getCustomName().getString();
		PatrolTestSupport.result("slayerSlimeLevel", "level=" + mobLevel + " speed=" + speed + " strength=" + strength + " name=" + name);
		helper.assertTrue(mobLevel == SlayerContract.LEVEL, "the enraged slime is level " + mobLevel);
		helper.assertTrue(speed != null && speed.isInfiniteDuration() && speed.getAmplifier() == 1
			&& strength != null && strength.isInfiniteDuration() && strength.getAmplifier() == 1, "the effects are " + speed + " and " + strength);
		helper.assertTrue(name.contains(SlayerContract.NAME), "the slime is named " + name);
		VillageWorldData.get(level).removeMission(mission.id());
		slime.discard();
		helper.succeed();
	}
}
