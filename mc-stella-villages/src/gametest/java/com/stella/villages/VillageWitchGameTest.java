package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mission.MissionService;
import com.stella.villages.patrol.PatrolLabels;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.VillageWitchKit;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * With the spells mod, every village gets a witch by its bell: labelled, in enchanted diamond, one of the
 * village's own, so no golem, patrol or villager takes it for a foe. Without that mod, no witch comes. Each
 * test runs in a batch of its own, because the switch is shared by the whole server.
 */
public class VillageWitchGameTest {
	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillageGetsAWitchByItsBellWhenSpellsAreThere(final GameTestHelper helper) {
		String test = "aVillageGetsAWitchByItsBellWhenSpellsAreThere";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(null);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch among its members");
		var chest = witch.getItemBySlot(EquipmentSlot.CHEST);
		int protection = EnchantmentHelper.getItemEnchantmentLevel(
			helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), chest);
		double fromBell = Math.sqrt(witch.blockPosition().distSqr(PatrolTestSupport.anchor(helper)));
		IronGolem golem = helper.spawnMob(EntityTypes.IRON_GOLEM, 5, 1, 5).withNoFreeWill().spawn();
		boolean golemSpares = !golem.canAttack(witch);
		String label = PatrolLabels.label(PatrolState.Role.VILLAGE_WITCH, false).getString();
		PatrolTestSupport.result(test, "witch=" + witch.getUUID() + " chest=" + chest.getItem() + " protection=" + protection
			+ " fromBell=" + fromBell + " marked=" + PatrolState.isMarked(witch) + " golemSpares=" + golemSpares + " label=" + label
			+ " tagged=" + VillageWitchKit.isVillageWitch(witch));
		golem.discard();
		helper.assertTrue(chest.is(Items.DIAMOND_CHESTPLATE) && protection == 4, "the witch wears " + chest);
		helper.assertTrue(VillageWitchKit.isVillageWitch(witch) && witch.entityTags().contains(VillageWitchKit.NO_BEHAVIOR_TAG), "the witch lacks its tags");
		helper.assertTrue(PatrolState.isMarked(witch) && golemSpares, "the witch is not one of the village's own");
		helper.assertTrue(fromBell <= VillageWitchKit.BELL_RADIUS, "the witch stands " + fromBell + " from the bell");
		helper.assertTrue(label.equals(VillageWitchKit.LABEL), "the witch is labelled " + label);
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void noWitchComesWithoutSpells(final GameTestHelper helper) {
		String test = "noWitchComesWithoutSpells";
		boolean loaded;
		try {
			VillageWitchKit.force(false);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(null);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		int members = VillageWorldData.get(helper.getLevel()).village(PatrolTestSupport.villageId(helper)).map(village -> village.members().size()).orElse(0);
		PatrolTestSupport.result(test, "witch=" + (witch != null) + " members=" + members);
		helper.assertTrue(members > 0, "the village has no patrol, so the case proves nothing");
		helper.assertTrue(witch == null, "a witch came without the spells mod");
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillageWithNoBellLoadedLooksAgainLater(final GameTestHelper helper) {
		String test = "aVillageWithNoBellLoadedLooksAgainLater";
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 1));
		var village = VillageWorldData.get(helper.getLevel()).findOrCreate(helper.getLevel().dimension(), anchor);
		BlockPos before = PatrolService.bellOf(helper.getLevel(), village);
		helper.setBlock(4, 1, 4, Blocks.BELL);
		BlockPos after = PatrolService.bellOf(helper.getLevel(), village);
		PatrolTestSupport.result(test, "before=" + before + " after=" + after + " bell=" + helper.absolutePos(new BlockPos(4, 1, 4)));
		helper.assertTrue(before.equals(village.anchor()), "a village with no bell did not fall back to its anchor, so the case proves nothing");
		helper.assertTrue(after.equals(helper.absolutePos(new BlockPos(4, 1, 4))), "the bell placed later was never found: " + after);
		helper.succeed();
	}

	@GameTest
	public void noNemesisIsAWaterMobOrASnowGolem(final GameTestHelper helper) {
		List<Identifier> pool = MissionService.defaultOfferPool();
		List<String> water = List.of("cod", "salmon", "squid", "glow_squid", "dolphin", "axolotl", "tadpole", "tropical_fish", "pufferfish",
			"guardian", "elder_guardian", "snow_golem");
		List<String> offered = water.stream().filter(id -> pool.contains(Identifier.withDefaultNamespace(id))).toList();
		PatrolTestSupport.result("noNemesisIsAWaterMobOrASnowGolem", "pool=" + pool.size() + " offered=" + offered);
		helper.assertTrue(pool.contains(Identifier.withDefaultNamespace("zombie")), "the pool lost the zombie, so the case proves nothing");
		helper.assertTrue(offered.isEmpty(), "the pool still offers " + offered);
		helper.succeed();
	}

	@GameTest
	public void onlyAPlayersKillPaysTheNemesisReward(final GameTestHelper helper) {
		Mob byPlayer = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, 2).withNoFreeWill().spawn();
		Mob bySun = helper.spawnMob(EntityTypes.ZOMBIE, 4, 1, 4).withNoFreeWill().spawn();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		byPlayer.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1.0F);
		bySun.hurtServer(helper.getLevel(), helper.getLevel().damageSources().onFire(), 1.0F);
		boolean player1 = MissionService.paysFor(byPlayer);
		boolean sun = MissionService.paysFor(bySun);
		PatrolTestSupport.result("onlyAPlayersKillPaysTheNemesisReward", "player=" + player1 + " sun=" + sun);
		helper.assertTrue(player1 && !sun, "the reward rule read player=" + player1 + " sun=" + sun);
		helper.succeed();
	}

	private static Mob witchOf(final GameTestHelper helper) {
		var village = VillageWorldData.get(helper.getLevel()).village(PatrolTestSupport.villageId(helper)).orElse(null);
		if (village == null) {
			return null;
		}
		for (var member : village.members()) {
			if (member.role().equals(PatrolState.Role.VILLAGE_WITCH.name()) && helper.getLevel().getEntity(member.id()) instanceof Mob mob) {
				return mob;
			}
		}
		return null;
	}
}
