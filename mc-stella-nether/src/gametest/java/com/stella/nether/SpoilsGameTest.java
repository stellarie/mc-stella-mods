package com.stella.nether;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** The Sovereign's spoils: the Blaze Core, and the Flame Mantle and what it does worn. */
public class SpoilsGameTest {
	@GameTest(environment = "mod-stella-nether-test:spoil_core", maxTicks = 200)
	public void aSlainSovereignLeavesABlazeCoreAndAPlainBlazeDoesNot(final GameTestHelper helper) {
		ServerPlayer player = BlazeSovereignGameTest.enter(helper);
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel raid = (ServerLevel) player.level();
		net.minecraft.world.phys.Vec3 floor = BlazeSovereignGameTest.arena(raid, server, 8);
		Blaze sovereign = BlazeSovereign.rise(raid, floor);
		Blaze plain = EntityTypes.BLAZE.create(raid, EntitySpawnReason.MOB_SUMMONED);
		plain.snapTo(floor.x + 3.0, floor.y, floor.z, 0.0F, 0.0F);
		raid.addFreshEntity(plain);
		sovereign.kill(raid);
		plain.kill(raid);
		List<ItemEntity> spoils = BlazeSovereign.spoilsOf(sovereign);
		List<ItemEntity> plainSpoils = BlazeSovereign.spoilsOf(plain);
		long cores = spoils.stream().filter(item -> item.getItem().is(NetherItems.BLAZE_CORE)).mapToInt(item -> item.getItem().getCount()).sum();
		NetherTestSupport.result("sovereignCore", "cores=" + cores + " spoils=" + spoils.size() + " plainSpoils=" + plainSpoils.size());
		spoils.forEach(ItemEntity::discard);
		NetherTestSupport.leave(helper, player);
		helper.assertTrue(cores == 1 && plainSpoils.isEmpty(), "the Sovereign left " + cores + " cores and a plain blaze " + plainSpoils.size() + " spoils");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:spoil_rate", maxTicks = 20)
	public void theMantleFallsAtItsChance(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(5L);
		int rolls = 40000;
		int mantles = 0;
		for (int i = 0; i < rolls; i++) {
			mantles += BlazeSovereign.mantleDrops(random) ? 1 : 0;
		}
		double rate = (double) mantles / rolls;
		double sigma = Math.sqrt(BlazeSovereign.MANTLE_CHANCE * (1 - BlazeSovereign.MANTLE_CHANCE) / rolls);
		NetherTestSupport.result("mantleRate", "rate=" + rate + " chance=" + BlazeSovereign.MANTLE_CHANCE);
		helper.assertTrue(Math.abs(rate - BlazeSovereign.MANTLE_CHANCE) <= 4 * sigma && BlazeSovereign.MANTLE_CHANCE > 0.0,
			"mantles fell at " + rate + " against " + BlazeSovereign.MANTLE_CHANCE);
		helper.succeed();
	}

	static ServerPlayer wearer(final GameTestHelper helper, final double x, final boolean mantle) {
		ServerPlayer player = NetherTestSupport.join(helper, x, 1.5);
		NetherTestSupport.vulnerable(player);
		if (mantle) {
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(NetherItems.FLAME_MANTLE));
		}
		return player;
	}

	@GameTest(environment = "mod-stella-nether-test:mantle_regen", maxTicks = 400)
	public void theMantleHealsItsWearerFaster(final GameTestHelper helper) {
		ServerPlayer worn = wearer(helper, 1.5, true);
		ServerPlayer bare = wearer(helper, 3.5, false);
		worn.setHealth(10.0F);
		bare.setHealth(10.0F);
		int beats = 4;
		helper.runAfterDelay(beats * FlameMantle.REGEN_EVERY_TICKS + 5, () -> {
			float wornGain = worn.getHealth() - 10.0F;
			float bareGain = bare.getHealth() - 10.0F;
			NetherTestSupport.result("mantleRegen", "worn=" + wornGain + " bare=" + bareGain);
			NetherTestSupport.leave(helper, worn);
			NetherTestSupport.leave(helper, bare);
			helper.assertTrue(wornGain >= (beats - 1) * FlameMantle.REGEN_AMOUNT && wornGain > bareGain,
				"over " + beats + " beats the wearer healed " + wornGain + " and the bare player " + bareGain);
			helper.succeed();
		});
	}

	@GameTest(environment = "mod-stella-nether-test:mantle_burn", maxTicks = 100)
	public void whoeverStrikesTheWearerCatchesFire(final GameTestHelper helper) {
		ServerPlayer worn = wearer(helper, 1.5, true);
		ServerPlayer bare = wearer(helper, 3.5, false);
		Zombie atWorn = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 3, 3));
		Zombie atBare = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 3, 3));
		atWorn.clearFire();
		atBare.clearFire();
		worn.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(atWorn), 1.0F);
		bare.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(atBare), 1.0F);
		int wornAttackerFire = atWorn.getRemainingFireTicks();
		int bareAttackerFire = atBare.getRemainingFireTicks();
		NetherTestSupport.result("mantleBurnsAttacker", "wornAttacker=" + wornAttackerFire + " bareAttacker=" + bareAttackerFire);
		NetherTestSupport.leave(helper, worn);
		NetherTestSupport.leave(helper, bare);
		helper.assertTrue(wornAttackerFire > 0 && bareAttackerFire <= 0, "attacker fire: at the wearer " + wornAttackerFire + ", at the bare player " + bareAttackerFire);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:mantle_fire", maxTicks = 100)
	public void theMantleTakesMostOfFiresHarmAway(final GameTestHelper helper) {
		ServerPlayer worn = wearer(helper, 1.5, true);
		ServerPlayer bare = wearer(helper, 3.5, false);
		float hit = 10.0F;
		worn.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), hit);
		bare.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), hit);
		float wornLost = worn.getMaxHealth() - worn.getHealth();
		float bareLost = bare.getMaxHealth() - bare.getHealth();
		NetherTestSupport.result("mantleFire", "worn=" + wornLost + " bare=" + bareLost);
		NetherTestSupport.leave(helper, worn);
		NetherTestSupport.leave(helper, bare);
		helper.assertTrue(bareLost > 0.0F && wornLost <= bareLost * (1.0F - FlameMantle.FIRE_REDUCTION) + 0.01F,
			"fire of " + hit + " took " + wornLost + " from the wearer and " + bareLost + " from the bare player");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-nether-test:mantle_enchant", maxTicks = 20)
	public void theMantleTakesArmorEnchantments(final GameTestHelper helper) {
		ItemStack mantle = new ItemStack(NetherItems.FLAME_MANTLE);
		var protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		var thorns = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.THORNS);
		boolean enchantable = mantle.isEnchantable();
		boolean takesProtection = protection.value().canEnchant(mantle);
		boolean takesThorns = thorns.value().canEnchant(mantle);
		NetherTestSupport.result("mantleEnchantable", "enchantable=" + enchantable + " protection=" + takesProtection + " thorns=" + takesThorns);
		helper.assertTrue(enchantable && takesProtection && takesThorns, "the Mantle enchantable=" + enchantable + " protection=" + takesProtection + " thorns=" + takesThorns);
		helper.succeed();
	}
}
