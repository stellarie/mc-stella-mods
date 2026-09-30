package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mission.MissionService;
import com.stella.villages.patrol.PatrolLabels;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.villages.patrol.VillageWitchKit;
import com.stella.villages.patrol.VillageWitchSplash;
import com.stella.villages.patrol.PatrolWitchPotionGoal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * With the spells mod, every village gets a witch by its bell: labelled, in enchanted diamond, one of the
 * village's own, so no golem, patrol or villager takes it for a foe. Without that mod the witch still comes, but casts no spells. Each
 * test runs in a batch of its own, because the switch is shared by the whole server.
 */
public class VillageWitchGameTest {
	private record PotionLaunch(boolean hostile, int tick, UUID target, String kind) { }
	private static final Map<UUID, List<List<UUID>>> HOSTILE_SPLASHES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<PotionLaunch>> POTION_LAUNCHES = new ConcurrentHashMap<>();

	static {
		VillageWitchSplash.setRecorder((witch, victims) -> HOSTILE_SPLASHES
			.computeIfAbsent(witch, ignored -> new CopyOnWriteArrayList<>()).add(victims));
		PatrolWitchPotionGoal.setRecorder((witch, target, kind, hostile, tick) -> POTION_LAUNCHES
			.computeIfAbsent(witch.getUUID(), ignored -> new CopyOnWriteArrayList<>())
			.add(new PotionLaunch(hostile, tick, target, kind)));
	}
	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillageGetsAWitchByItsBellWhenSpellsAreThere(final GameTestHelper helper) {
		String test = "aVillageGetsAWitchByItsBellWhenSpellsAreThere";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
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

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void ringingTheBellRalliesTheVillageWitch(final GameTestHelper helper) {
		String test = "ringingTheBellRalliesTheVillageWitch";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		BlockPos bell = PatrolTestSupport.anchor(helper);
		ServerPlayer ringer = helper.makeMockServerPlayerInLevel();
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(
			ringer, helper.getLevel(), net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(bell), net.minecraft.core.Direction.NORTH, bell, false)
		);
		var speed = witch.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
		var strength = witch.getEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
		int members = PatrolTestSupport.patrols(helper).size();
		PatrolTestSupport.result(test, "members=" + members + " witchNear="
			+ (witch.distanceToSqr(Vec3.atCenterOf(bell)) <= 64.0) + " speed=" + speed + " strength=" + strength);
		helper.assertTrue(members == PatrolService.PATROL_MEMBER_COUNT && speed != null && strength != null
			&& speed.getDuration() >= 6000 && strength.getDuration() >= 6000
			&& witch.distanceToSqr(Vec3.atCenterOf(bell)) <= 64.0,
			"the village witch did not join the four-member bell rally");
		helper.succeed();
	}

	@GameTest
	public void harmingSplashDamagesUndeadHostiles(final GameTestHelper helper) {
		helper.setTime(18_000L);
		helper.setBlock(2, 0, 2, Blocks.STONE);
		helper.setBlock(4, 0, 2, Blocks.STONE);
		Mob witch = helper.spawnMob(EntityTypes.WITCH, 2, 1, 2).withNoFreeWill().spawn();
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 4, 1, 2).withNoFreeWill().spawn();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		float before = zombie.getHealth();
		ItemStack harming = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
		ThrownSplashPotion splash = new ThrownSplashPotion(helper.getLevel(), witch, harming);
		splash.addTag(VillageWitchSplash.OFFENSIVE_TAG);
		splash.setPos(zombie.getX(), zombie.getY() + 0.5, zombie.getZ());
		splash.onHitAsPotion(helper.getLevel(), harming,
			new BlockHitResult(splash.position(), Direction.UP, splash.blockPosition(), false));
		PatrolTestSupport.result("harmingSplashDamagesUndeadHostiles",
			"before=" + before + " after=" + zombie.getHealth());
		helper.assertTrue(zombie.getHealth() < before, "Harming splash healed or spared the undead hostile");
		witch.discard();
		zombie.discard();
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void hostileWitchSplashNeverTouchesFriendlies(final GameTestHelper helper) {
		String test = "hostileWitchSplashNeverTouchesFriendlies";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		Mob ally = PatrolTestSupport.role(helper, test, "PILLAGER");
		helper.assertTrue(witch != null && ally != null, "the witch or ally is missing");
		Mob foe = helper.spawnMob(EntityTypes.PILLAGER, 5, 2, 5).withNoFreeWill().spawn();
		Villager villager = helper.spawnMob(EntityTypes.VILLAGER, 6, 2, 5).withNoFreeWill().spawn();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 center = witch.position();
		foe.setPos(center.x + 1.0, center.y, center.z);
		ally.setPos(center.x + 1.5, center.y, center.z + 0.5);
		villager.setPos(center.x + 1.0, center.y, center.z + 1.0);
		player.setPos(center.x + 1.5, center.y, center.z + 1.0);
		ItemStack wither = new ItemStack(Items.SPLASH_POTION);
		wither.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
			PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 100, 0)));
		List<ItemStack> harmful = List.of(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING),
			PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON), wither);
		boolean safe = true;
		int hits = 0;
		for (ItemStack stack : harmful) {
			for (Mob mob : List.of(foe, ally, villager)) {
				mob.setHealth(mob.getMaxHealth());
				mob.removeAllEffects();
			}
			player.setHealth(player.getMaxHealth());
			player.removeAllEffects();
			ThrownSplashPotion splash = new ThrownSplashPotion(helper.getLevel(), witch, stack);
			splash.addTag(VillageWitchSplash.OFFENSIVE_TAG);
			splash.setPos(center.x + 1.0, center.y + 0.5, center.z + 0.5);
			splash.onHitAsPotion(helper.getLevel(), stack,
				new BlockHitResult(splash.position(), Direction.UP, splash.blockPosition(), false));
			boolean affected = foe.getHealth() < foe.getMaxHealth() || foe.hasEffect(MobEffects.POISON)
				|| foe.hasEffect(MobEffects.WITHER);
			hits += affected ? 1 : 0;
			safe &= ally.getHealth() == ally.getMaxHealth() && villager.getHealth() == villager.getMaxHealth()
				&& player.getHealth() == player.getMaxHealth()
				&& !ally.hasEffect(MobEffects.POISON) && !ally.hasEffect(MobEffects.WITHER)
				&& !villager.hasEffect(MobEffects.POISON) && !villager.hasEffect(MobEffects.WITHER)
				&& !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER);
		}
		PatrolTestSupport.result(test, "harmfulTypes=" + harmful.size() + " foeHits=" + hits + " friendsSafe=" + safe);
		helper.assertTrue(hits == harmful.size() && safe, "a hostile splash missed its foe or hit a friend");
		helper.succeed();
	}

	@GameTest
	public void witchPotionTimersWaitOneHundredTicksBetweenThrows(final GameTestHelper helper) {
		PatrolTestSupport.result("witchPotionTimersWaitOneHundredTicksBetweenThrows",
			"interval=" + PatrolService.WITCH_POTION_INTERVAL_TICKS);
		helper.assertTrue(PatrolService.WITCH_POTION_INTERVAL_TICKS == 100,
			"the witch potion timers wait " + PatrolService.WITCH_POTION_INTERVAL_TICKS + " ticks, not 100");
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 260, required = false, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchPotionTimersRequireManualTestUnderLoad(final GameTestHelper helper) {
		String test = "villageWitchPotionTimersRequireManualTestUnderLoad";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != witch) {
				member.setNoAi(true);
				Vec3 far = witch.position().add(30.0, 0.0, 0.0);
				member.setPos(far.x, far.y, far.z);
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 friendly = witch.position().add(3.0, 0.0, 0.0);
		player.setPos(friendly.x, friendly.y, friendly.z);
		// A hurt player keeps needing a healing splash, so every support throw waits on the timer alone.
		helper.onEachTick(() -> player.setHealth(1.0F));
		Mob hostile = helper.spawnMob(EntityTypes.PILLAGER, 5, 2, 5).withNoFreeWill().spawn();
		hostile.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500.0);
		hostile.setHealth(500.0F);
		Vec3 enemy = witch.position().add(4.0, 0.0, 0.0);
		hostile.setPos(enemy.x, enemy.y, enemy.z);
		var chunk = hostile.chunkPosition();
		helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true);
		helper.runAfterDelay(200, () -> {
			List<PotionLaunch> launches = POTION_LAUNCHES.getOrDefault(witch.getUUID(), List.of());
			List<Integer> support = launches.stream().filter(launch -> !launch.hostile()).map(PotionLaunch::tick).toList();
			List<Integer> offense = launches.stream().filter(PotionLaunch::hostile).map(PotionLaunch::tick).toList();
			boolean separate = support.size() >= 2 && offense.size() >= 2
				&& support.get(1) - support.get(0) >= PatrolService.WITCH_POTION_INTERVAL_TICKS
				&& support.get(1) - support.get(0) <= PatrolService.WITCH_POTION_INTERVAL_TICKS + 10
				&& offense.get(1) - offense.get(0) >= PatrolService.WITCH_POTION_INTERVAL_TICKS
				&& offense.get(1) - offense.get(0) <= PatrolService.WITCH_POTION_INTERVAL_TICKS + 10
				&& Math.abs(support.get(0) - offense.get(0)) <= 10;
			PatrolTestSupport.result(test, "support=" + support + " offense=" + offense + " separate=" + separate);
			helper.assertTrue(separate, "support and hostile potions share one timer");
			helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
			helper.succeed();
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 240, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchThrowsHarmfulSplashAtHostiles(final GameTestHelper helper) {
		String test = "villageWitchThrowsHarmfulSplashAtHostiles";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != witch) {
				member.setNoAi(true);
				Vec3 far = witch.position().add(30.0, 0.0, 0.0);
				member.setPos(far.x, far.y, far.z);
			}
		}
		Mob hostile = helper.spawnMob(EntityTypes.PILLAGER, 5, 2, 5).withNoFreeWill().spawn();
		hostile.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
		hostile.setHealth(100.0F);
		Vec3 at = witch.position().add(4.0, 0.0, 0.0);
		hostile.setPos(at.x, at.y, at.z);
		var chunk = hostile.chunkPosition();
		helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true);
		PatrolTestSupport.detail(test, "hostileEligible=" + VillageWitchSplash.hostile(hostile)
			+ " sight=" + witch.hasLineOfSight(hostile) + " ticking="
			+ helper.getLevel().isPositionEntityTicking(hostile.blockPosition()));
		helper.succeedWhen(() -> {
			boolean harmed = hostile.getHealth() < 100.0F || hostile.hasEffect(MobEffects.POISON)
				|| hostile.hasEffect(MobEffects.WITHER);
			boolean splashed = HOSTILE_SPLASHES.getOrDefault(witch.getUUID(), List.of()).stream()
				.anyMatch(victims -> victims.contains(hostile.getUUID()));
			helper.assertTrue(harmed && splashed, "the witch has not landed a harmful splash on the hostile: alive="
				+ hostile.isAlive() + " distance=" + witch.distanceTo(hostile) + " sight="
				+ witch.hasLineOfSight(hostile) + " witchTarget=" + witch.getTarget()
				+ " hostileTicking=" + helper.getLevel().isPositionEntityTicking(hostile.blockPosition()));
			PatrolTestSupport.result(test, "health=" + hostile.getHealth() + " poison="
				+ hostile.hasEffect(MobEffects.POISON) + " wither=" + hostile.hasEffect(MobEffects.WITHER)
				+ " splashes=" + HOSTILE_SPLASHES.getOrDefault(witch.getUUID(), List.of()).size());
			helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 340, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchBuffsPlayersEveryFiveSeconds(final GameTestHelper helper) {
		String test = "villageWitchBuffsPlayersEveryFiveSeconds";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		for (Mob member : PatrolTestSupport.patrols(helper)) {
			if (member != witch) {
				member.setNoAi(true);
				Vec3 far = witch.position().add(30.0, 0.0, 0.0);
				member.setPos(far.x, far.y, far.z);
			}
		}
		// A villager beside the witch would take some of the buffs, so the player is the only friend in range.
		for (Villager villager : helper.getLevel().getEntitiesOfClass(Villager.class,
			witch.getBoundingBox().inflate(PatrolService.WITCH_SUPPORT_RANGE))) {
			Vec3 far = witch.position().add(30.0, 0.0, 0.0);
			villager.setNoAi(true);
			villager.setPos(far.x, far.y, far.z);
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 near = witch.position().add(3.0, 0.0, 0.0);
		player.setPos(near.x, near.y, near.z);
		long[] regen = {-1};
		long[] speed = {-1};
		long[] strength = {-1};
		helper.onEachTick(() -> {
			if (regen[0] < 0 && player.hasEffect(MobEffects.REGENERATION)) {
				regen[0] = helper.getTick();
			}
			if (speed[0] < 0 && player.hasEffect(MobEffects.SPEED)) {
				speed[0] = helper.getTick();
			}
			if (strength[0] < 0 && player.hasEffect(MobEffects.STRENGTH)) {
				strength[0] = helper.getTick();
			}
		});
		helper.runAfterDelay(300, () -> {
			boolean spaced = regen[0] >= 0 && speed[0] >= regen[0] + PatrolService.WITCH_POTION_INTERVAL_TICKS - 5
				&& strength[0] >= speed[0] + PatrolService.WITCH_POTION_INTERVAL_TICKS - 5;
			PatrolTestSupport.result(test, "regenAt=" + regen[0] + " speedAt=" + speed[0]
				+ " strengthAt=" + strength[0] + " spaced=" + spaced);
			helper.assertTrue(spaced, "the witch did not buff the player every five seconds");
			helper.succeed();
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchHealsThePlayer(final GameTestHelper helper) {
		String test = "villageWitchHealsThePlayer";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = witch.position().add(3.0, 0.0, 0.0);
		player.setPos(at.x, at.y, at.z);
		player.setHealth(5.0F);
		helper.succeedWhen(() -> {
			helper.assertTrue(player.getHealth() > 5.0F, "the village witch did not heal the player");
			PatrolTestSupport.result(test, "health=" + player.getHealth());
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchHealsADamagedVillager(final GameTestHelper helper) {
		String test = "villageWitchHealsADamagedVillager";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		helper.assertTrue(witch != null, "the village has no witch");
		Villager villager = helper.spawnMob(EntityTypes.VILLAGER, 5, 2, 5).withNoFreeWill().spawn();
		Vec3 at = witch.position().add(3.0, 0.0, 0.0);
		villager.setPos(at.x, at.y, at.z);
		villager.setHealth(5.0F);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getHealth() > 5.0F, "the village witch did not heal the villager");
			PatrolTestSupport.result(test, "health=" + villager.getHealth());
		});
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageWitchHealsADamagedPatrolMember(final GameTestHelper helper) {
		String test = "villageWitchHealsADamagedPatrolMember";
		boolean loaded;
		try {
			VillageWitchKit.force(true);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		Mob ally = PatrolTestSupport.role(helper, test, "PILLAGER");
		helper.assertTrue(witch != null && ally != null, "the witch or patrol member is missing");
		Vec3 at = witch.position().add(3.0, 0.0, 0.0);
		ally.setPos(at.x, at.y, at.z);
		ally.setHealth(5.0F);
		helper.succeedWhen(() -> {
			helper.assertTrue(ally.getHealth() > 5.0F, "the village witch did not heal the patrol member");
			PatrolTestSupport.result(test, "health=" + ally.getHealth());
		});
	}

	@GameTest
	public void harmfulWitchPotionsAreRandomSplashItems(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_260_928L);
		int[] counts = new int[3];
		int samples = 9_000;
		boolean allSplash = true;
		for (int index = 0; index < samples; index++) {
			ItemStack potion = PatrolWitchPotionGoal.randomOffensivePotion(random);
			allSplash &= potion.is(Items.SPLASH_POTION);
			switch (PatrolWitchPotionGoal.offensiveKind(potion)) {
				case "harming" -> counts[0]++;
				case "poison" -> counts[1]++;
				case "wither" -> counts[2]++;
				default -> allSplash = false;
			}
		}
		int expected = samples / counts.length;
		boolean balanced = java.util.Arrays.stream(counts)
			.allMatch(count -> Math.abs(count - expected) <= samples * 0.03);
		PatrolTestSupport.result("harmfulWitchPotionsAreRandomSplashItems", "harming=" + counts[0]
			+ " poison=" + counts[1] + " wither=" + counts[2] + " allSplash=" + allSplash);
		helper.assertTrue(allSplash && balanced, "the harmful splash potion roll lost a type or its balance");
		helper.succeed();
	}

	@GameTest(environment = "mc-stella-villages-test:witch", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theVillageWitchComesEvenWithoutSpells(final GameTestHelper helper) {
		String test = "theVillageWitchComesEvenWithoutSpells";
		boolean loaded;
		try {
			VillageWitchKit.force(false);
			loaded = PatrolTestSupport.loadVillage(helper, test, false);
		} finally {
			VillageWitchKit.force(false);
		}
		if (!loaded) {
			return;
		}
		Mob witch = witchOf(helper);
		int members = VillageWorldData.get(helper.getLevel()).village(PatrolTestSupport.villageId(helper)).map(village -> village.members().size()).orElse(0);
		PatrolTestSupport.result(test, "witch=" + (witch != null) + " members=" + members);
		helper.assertTrue(members > 0, "the village has no patrol, so the case proves nothing");
		helper.assertTrue(witch != null && VillageWitchKit.isVillageWitch(witch) && PatrolState.isMarked(witch),
			"the village has no marked village witch when the spells mod is absent");
		helper.assertTrue(members == PatrolService.PATROL_MEMBER_COUNT, "the roster holds " + members + " members");
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
