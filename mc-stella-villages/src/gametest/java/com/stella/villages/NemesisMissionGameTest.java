package com.stella.villages;

import com.stella.villages.mission.MissionBoardMenu;
import com.stella.villages.mission.MissionService;
import com.stella.villages.mission.NemesisHead;
import com.stella.villages.mission.NemesisState;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.gear.GearTier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.SpawnEggItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class NemesisMissionGameTest {
	@GameTest(dimension = "minecraft:the_nether")
	public void excludedKindsNeverBecomeNemeses(final GameTestHelper helper) {
		List<String> excluded = List.of("creeper", "ender_dragon", "wither", "endermite", "silverfish", "blaze", "phantom", "ghast");
		List<Identifier> pool = MissionService.defaultOfferPool();
		List<String> problems = new ArrayList<>();
		for (String name : excluded) {
			Identifier id = Identifier.parse("minecraft:" + name);
			Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(helper.getLevel(), EntitySpawnReason.COMMAND);
			boolean allowed = entity instanceof Mob mob && MissionService.canBeNemesis(mob);
			if (pool.contains(id) || allowed || !MissionService.NEVER_OFFERED.contains(id)) {
				problems.add(name + " pool=" + pool.contains(id) + " canBeNemesis=" + allowed);
			}
			if (entity != null) {
				entity.discard();
			}
		}
		PatrolTestSupport.result("excludedKindsNeverBecomeNemeses", "problems=" + problems);
		helper.assertTrue(problems.isEmpty(), "an excluded kind can still be a Nemesis: " + problems);
		helper.assertTrue(pool.contains(Identifier.parse("minecraft:zombie")) && pool.contains(Identifier.parse("minecraft:spider")),
			"the pool lost an ordinary hostile");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void rewardPaysOneBookTwoEmeraldStacksAndTwoPotions(final GameTestHelper helper) {
		Mob subject = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(subject != null, "could not create the reward subject");
		var random = net.minecraft.util.RandomSource.create(20_260_929L);
		MissionService.setSpellBookPool(List.of());
		try {
			for (int roll = 0; roll < 300; roll++) {
				List<ItemStack> reward = MissionService.rollReward(helper.getLevel(), subject, random);
				List<ItemStack> emeralds = reward.stream().filter(stack -> stack.is(Items.EMERALD)).toList();
				List<ItemStack> potions = reward.stream().filter(stack -> stack.is(Items.POTION)).toList();
				helper.assertTrue(reward.stream().filter(stack -> stack.is(Items.ENCHANTED_BOOK)).count() == 1,
					"the reward holds no single enchanted book: " + reward);
				helper.assertTrue(emeralds.size() == MissionService.REWARD_EMERALD_STACKS
					&& emeralds.stream().allMatch(stack -> stack.getCount() == Items.EMERALD.getDefaultMaxStackSize()),
					"the reward emeralds are not two full stacks: " + reward);
				helper.assertTrue(potions.size() == MissionService.REWARD_POTIONS && potions.stream().allMatch(stack -> stack
					.getOrDefault(DataComponents.POTION_CONTENTS, net.minecraft.world.item.alchemy.PotionContents.EMPTY)
					.getAllEffects().iterator().hasNext()), "a reward potion has no effect: " + reward);
				helper.assertTrue(reward.stream().noneMatch(stack -> stack.is(Items.PLAYER_HEAD) || stack.is(Items.ZOMBIE_HEAD)
					|| stack.is(Items.WITHER_SKELETON_SKULL) || stack.is(Items.CREEPER_HEAD)), "the reward holds a head");
			}
		} finally {
			MissionService.setSpellBookPool(null);
		}
		PatrolTestSupport.result("rewardPaysOneBookTwoEmeraldStacksAndTwoPotions", "rolls=300");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void rewardBookIsASpellBookWhenTheTagHasItemsAndFallsBackOtherwise(final GameTestHelper helper) {
		var random = net.minecraft.util.RandomSource.create(20_260_930L);
		List<Item> books = List.of(Items.PAPER, Items.STICK);
		int spell = 0;
		MissionService.setSpellBookPool(books);
		try {
			for (int roll = 0; roll < 2_000; roll++) {
				ItemStack book = MissionService.rewardBook(helper.getLevel(), random, true);
				helper.assertTrue(books.contains(book.getItem()), "a spell book roll gave " + book);
				ItemStack plain = MissionService.rewardBook(helper.getLevel(), random, false);
				helper.assertTrue(plain.is(Items.ENCHANTED_BOOK), "an enchanted book roll gave " + plain);
			}
			Mob subject = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			for (int roll = 0; roll < 2_000; roll++) {
				ItemStack book = MissionService.rollReward(helper.getLevel(), subject, random).get(0);
				spell += books.contains(book.getItem()) ? 1 : 0;
				helper.assertTrue(books.contains(book.getItem()) || book.is(Items.ENCHANTED_BOOK),
					"the reward book is neither kind: " + book);
			}
			MissionService.setSpellBookPool(List.of());
			ItemStack fallback = MissionService.rewardBook(helper.getLevel(), random, true);
			helper.assertTrue(fallback.is(Items.ENCHANTED_BOOK), "an empty tag gave " + fallback);
		} finally {
			MissionService.setSpellBookPool(null);
		}
		var tag = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
			.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, MissionService.SPELL_BOOK_TAG));
		boolean tagHasItems = tag.isPresent() && tag.get().size() > 0;
		ItemStack real = MissionService.rewardBook(helper.getLevel(), random, true);
		helper.assertTrue(tagHasItems ? !real.is(Items.ENCHANTED_BOOK) : real.is(Items.ENCHANTED_BOOK),
			"the real tag (items=" + tagHasItems + ") gave " + real);
		helper.assertTrue(withinFiveSigma(spell, 2_000, 0.5), "the spell book share is " + spell + " of 2000");
		PatrolTestSupport.result("rewardBookIsASpellBookWhenTheTagHasItemsAndFallsBackOtherwise",
			"spell=" + spell + " realTagItems=" + tagHasItems);
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void rareRewardRollsAreIndependentAndAFailedRollPaysNothing(final GameTestHelper helper) {
		Mob subject = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(subject != null, "could not create the reward subject");
		subject.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		var random = net.minecraft.util.RandomSource.create(20_260_931L);
		int rolls = 200_000;
		int diamonds = 0;
		int debris = 0;
		int gear = 0;
		int empty = 0;
		int diamondsAndGear = 0;
		int debrisWithOther = 0;
		boolean shapes = true;
		for (int roll = 0; roll < rolls; roll++) {
			List<ItemStack> rare = MissionService.rollRareRewards(subject, random);
			boolean hasDiamonds = rare.stream().anyMatch(stack -> stack.is(Items.DIAMOND));
			boolean hasDebris = rare.stream().anyMatch(stack -> stack.is(Items.ANCIENT_DEBRIS));
			boolean hasGear = rare.stream().anyMatch(stack -> stack.is(Items.DIAMOND_HELMET) || stack.is(Items.DIAMOND_SWORD));
			diamonds += hasDiamonds ? 1 : 0;
			debris += hasDebris ? 1 : 0;
			gear += hasGear ? 1 : 0;
			empty += rare.isEmpty() ? 1 : 0;
			diamondsAndGear += hasDiamonds && hasGear ? 1 : 0;
			debrisWithOther += hasDebris && rare.size() > 1 ? 1 : 0;
			shapes &= rare.stream().allMatch(stack -> stack.is(Items.DIAMOND) ? stack.getCount() == 32 : stack.getCount() == 1)
				&& rare.stream().noneMatch(stack -> stack.is(Items.PLAYER_HEAD) || stack.is(Items.ZOMBIE_HEAD));
		}
		PatrolTestSupport.result("rareRewardRollsAreIndependentAndAFailedRollPaysNothing", "rolls=" + rolls
			+ " diamonds=" + diamonds + " debris=" + debris + " gear=" + gear + " empty=" + empty
			+ " diamondsAndGear=" + diamondsAndGear + " debrisWithOther=" + debrisWithOther);
		helper.assertTrue(shapes, "a rare reward has the wrong stack shape");
		double bonus = MissionService.REWARD_BONUS_CHANCE;
		double rareDebris = MissionService.REWARD_DEBRIS_CHANCE;
		helper.assertTrue(withinFiveSigma(diamonds, rolls, bonus), "diamonds paid " + diamonds + " of " + rolls);
		helper.assertTrue(withinFiveSigma(gear, rolls, bonus), "gear paid " + gear + " of " + rolls);
		helper.assertTrue(withinFiveSigma(debris, rolls, rareDebris), "ancient debris paid " + debris + " of " + rolls);
		helper.assertTrue(withinFiveSigma(diamondsAndGear, rolls, bonus * bonus),
			"diamonds and gear paid together " + diamondsAndGear + " times, so the rolls are not independent");
		helper.assertTrue(debrisWithOther > 0, "the debris roll never paid beside another roll");
		helper.assertTrue(withinFiveSigma(empty, rolls, (1 - bonus) * (1 - bonus) * (1 - rareDebris)),
			"failed rolls paid something: empty=" + empty);
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void offersOnlyHostileMobsAndIronGolems(final GameTestHelper helper) {
		List<Identifier> pool = MissionService.defaultOfferPool();
		boolean valid = pool.contains(Identifier.parse("minecraft:zombie"))
			&& pool.contains(Identifier.parse("minecraft:iron_golem"))
			&& !pool.contains(Identifier.parse("minecraft:wandering_trader"))
			&& !pool.contains(Identifier.parse("minecraft:creaking"))
			&& !pool.contains(Identifier.parse("minecraft:cow"));
		PatrolTestSupport.result("offersOnlyHostileMobsAndIronGolems", "valid=" + valid);
		helper.assertTrue(valid, "Nemesis offers include a neutral mob or exclude the iron golem");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", padding = PatrolTestSupport.VILLAGE_PADDING)
	public void villageHeadTradesVanillaAndNamedMobHeads(final GameTestHelper helper) {
		String test = "villageHeadTradesVanillaAndNamedMobHeads";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		boolean mapping = NemesisHead.itemFor(Identifier.parse("minecraft:zombie")) == Items.ZOMBIE_HEAD
			&& NemesisHead.itemFor(Identifier.parse("minecraft:skeleton")) == Items.SKELETON_SKULL
			&& NemesisHead.itemFor(Identifier.parse("minecraft:spider")) == Items.PLAYER_HEAD;
		fixture.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ZOMBIE_HEAD));
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		int vanillaBooks = enchantedBookCount(fixture.owner().getInventory());
		boolean vanillaConsumed = !fixture.owner().getItemInHand(InteractionHand.MAIN_HAND).is(Items.ZOMBIE_HEAD);
		fixture.owner().setItemInHand(InteractionHand.OFF_HAND,
			NemesisHead.create(Identifier.parse("minecraft:spider")));
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.OFF_HAND, fixture.head(), null
		);
		int totalBooks = enchantedBookCount(fixture.owner().getInventory());
		boolean namedConsumed = fixture.owner().getItemInHand(InteractionHand.OFF_HAND).isEmpty();
		boolean valid = mapping && vanillaConsumed && namedConsumed && vanillaBooks == 3 && totalBooks == 6;
		PatrolTestSupport.result(test, "mapping=" + mapping + " vanillaBooks=" + vanillaBooks
			+ " totalBooks=" + totalBooks + " valid=" + valid);
		helper.assertTrue(valid, "village head did not trade both vanilla and named mob heads");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200)
	public void ironGolemNemesisTargetsItsMissionPlayer(final GameTestHelper helper) {
		String test = "ironGolemNemesisTargetsItsMissionPlayer";
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = survivalPlayer(helper);
		BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
		movePlayer(owner, Vec3.atBottomCenterOf(position.offset(8, 0, 0)));
		Mob golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
		if (golem == null) {
			helper.fail("could not create iron golem Nemesis");
			return;
		}
		UUID missionId = UUID.randomUUID();
		com.stella.villages.data.VillageWorldData.get(level).addMission(
			new com.stella.villages.data.VillageWorldData.MissionRecord(
				missionId, owner.getUUID(), Identifier.parse("minecraft:iron_golem"), position,
				Optional.of(golem.getUUID()), com.stella.villages.data.VillageWorldData.MissionStatus.ACTIVE,
				Optional.empty(), level.dimension()
			)
		);
		NemesisState.attach(golem, missionId);
		golem.snapTo(Vec3.atBottomCenterOf(position), 0.0F, 0.0F);
		level.addFreshEntity(golem);
		helper.runAfterDelay(40, () -> {
			boolean targeted = golem.getTarget() == owner;
			PatrolTestSupport.result(test, "elite=" + Elite.isElite(golem) + " targeted=" + targeted
				+ " golemTicks=" + golem.tickCount + " ownerDistance=" + Math.sqrt(golem.distanceToSqr(owner))
				+ " missionPresent=" + com.stella.villages.data.VillageWorldData.get(level).mission(missionId).isPresent()
				+ " ownerCreative=" + owner.isCreative() + " ownerSpectator=" + owner.isSpectator()
				+ " currentTarget=" + golem.getTarget());
			golem.discard();
			com.stella.villages.data.VillageWorldData.get(level).removeMission(missionId);
			helper.assertTrue(targeted, "iron golem Nemesis ignored its mission player");
			helper.succeed();
		});
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void equipmentRewardRollsAtFivePercentAndReturnsOnePiece(final GameTestHelper helper) {
		Mob subject = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (subject == null) {
			helper.fail("could not create equipment reward subject");
			return;
		}
		subject.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		subject.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		int rewards = 0;
		boolean singlePiece = true;
		var random = net.minecraft.util.RandomSource.create(20_260_928L);
		for (int roll = 0; roll < 10_000; roll++) {
			ItemStack item = MissionService.rollEquipmentReward(subject, random);
			if (!item.isEmpty()) {
				rewards++;
				singlePiece &= item.getCount() == 1 && (item.is(Items.DIAMOND_HELMET)
					|| item.is(Items.DIAMOND_SWORD));
			}
		}
		boolean valid = singlePiece && withinFiveSigma(rewards, 10_000, MissionService.REWARD_BONUS_CHANCE);
		PatrolTestSupport.result("equipmentRewardRollsAtFivePercentAndReturnsOnePiece",
			"rolls=10000 rewards=" + rewards + " singlePiece=" + singlePiece + " valid=" + valid);
		helper.assertTrue(valid, "equipment reward rate or single-piece rule changed");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void savedPlainNemesisBecomesEliteOnLoad(final GameTestHelper helper) {
		Mob saved = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (saved == null) {
			helper.fail("could not create saved Nemesis fixture");
			return;
		}
		double baseline = saved.getMaxHealth();
		saved.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseline + MissionService.NEMESIS_BONUS_HEALTH);
		saved.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		NemesisState.attach(saved, UUID.randomUUID());
		saved.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		saved.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))), 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(saved);
		boolean upgraded = Elite.isElite(saved) && hasNemesisKit(saved)
			&& Math.abs(saved.getMaxHealth() - (baseline * Elite.HEALTH_MULTIPLIER
				+ MissionService.NEMESIS_BONUS_HEALTH)) < 0.001
			&& saved.getActiveEffects().stream().anyMatch(effect -> effect.getDuration() == -1);
		PatrolTestSupport.result("savedPlainNemesisBecomesEliteOnLoad", "elite=" + Elite.isElite(saved)
			+ " health=" + saved.getMaxHealth() + " upgraded=" + upgraded);
		saved.discard();
		helper.assertTrue(upgraded, "an existing plain Nemesis did not receive the elite profile");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void savedEliteNemesisGetsThreeEnchantmentsOnLoad(final GameTestHelper helper) {
		Mob saved = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		if (saved == null) {
			helper.fail("could not create saved elite fixture");
			return;
		}
		double baseline = saved.getMaxHealth();
		saved.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		// The build order of a real Nemesis: elite health first, then the bonus.
		Elite.mark(saved, GearTier.DIAMOND, Elite.HEALTH_MULTIPLIER);
		saved.getAttribute(Attributes.MAX_HEALTH).setBaseValue(saved.getAttribute(Attributes.MAX_HEALTH).getBaseValue()
			+ MissionService.NEMESIS_BONUS_HEALTH);
		saved.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
		NemesisState.attach(saved, UUID.randomUUID());
		saved.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))), 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(saved);
		boolean upgraded = Elite.isElite(saved) && hasNemesisKit(saved)
			&& Math.abs(saved.getMaxHealth() - (baseline * Elite.HEALTH_MULTIPLIER
				+ MissionService.NEMESIS_BONUS_HEALTH)) < 0.001;
		PatrolTestSupport.result("savedEliteNemesisGetsThreeEnchantmentsOnLoad",
			"health=" + saved.getMaxHealth() + " upgraded=" + upgraded);
		saved.discard();
		helper.assertTrue(upgraded, "saved elite Nemesis kept old equipment or duplicate bonus health");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void missingEliteCompanionIsReplaced(final GameTestHelper helper) {
		String test = "missingEliteCompanionIsReplaced";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		movePlayer(fixture.owner(), Vec3.atBottomCenterOf(safeSite));
		Mob[] nemesis = {null};
		UUID[] originalCompanionId = {null};
		helper.startSequence()
			.thenWaitUntil(() -> {
				var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
				nemesis[0] = mission == null ? null
					: loadNemesis(test, helper.getLevel(), mission.id(), mission.targetPosition());
				NemesisState state = nemesis[0] == null ? null : NemesisState.get(nemesis[0]);
				helper.assertTrue(state != null && state.companionId().isPresent(),
					"Nemesis and original companion did not spawn");
			})
			.thenExecute(() -> {
				originalCompanionId[0] = NemesisState.get(nemesis[0]).companionId().orElseThrow();
				Entity companion = helper.getLevel().getEntity(originalCompanionId[0]);
				helper.assertTrue(companion instanceof Mob, "original companion was not loaded");
				companion.discard();
			})
			.thenWaitUntil(() -> {
				NemesisState state = NemesisState.get(nemesis[0]);
				UUID replacementId = state == null ? null : state.companionId().orElse(null);
				Entity replacement = replacementId == null ? null : helper.getLevel().getEntity(replacementId);
				helper.assertTrue(replacement instanceof Mob guard
					&& !replacementId.equals(originalCompanionId[0])
					&& guard.getType() == nemesis[0].getType() && Elite.isElite(guard)
					&& nemesis[0].getActiveEffects().stream().anyMatch(effect -> effect.getDuration() == -1
						&& guard.hasEffect(effect.getEffect())), "elite companion was not replaced with the same buff");
			})
			.thenExecute(() -> {
				PatrolTestSupport.result(test, "accepted=" + accepted + " replacement="
					+ NemesisState.get(nemesis[0]).companionId().orElseThrow() + " elite=true sharedBuff=true");
				movePlayer(fixture.owner(), Vec3.atBottomCenterOf(fixture.head().blockPosition()));
				releaseSafeTargetSite(helper, safeSite);
			})
			.thenSucceed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void killedEliteCompanionNeverRespawns(final GameTestHelper helper) {
		String test = "killedEliteCompanionNeverRespawns";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		helper.assertTrue(offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId)), "the mission was not accepted");
		movePlayer(fixture.owner(), Vec3.atBottomCenterOf(safeSite));
		Mob[] nemesis = {null};
		UUID[] companionId = {null};
		helper.startSequence()
			.thenWaitUntil(() -> {
				var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
				nemesis[0] = mission == null ? null
					: loadNemesis(test, helper.getLevel(), mission.id(), mission.targetPosition());
				NemesisState state = nemesis[0] == null ? null : NemesisState.get(nemesis[0]);
				helper.assertTrue(state != null && state.companionId().isPresent(), "Nemesis and companion did not spawn");
			})
			.thenExecute(() -> {
				companionId[0] = NemesisState.get(nemesis[0]).companionId().orElseThrow();
				Entity companion = helper.getLevel().getEntity(companionId[0]);
				helper.assertTrue(companion instanceof Mob, "the companion was not loaded");
				companion.kill(helper.getLevel());
				movePlayer(fixture.owner(), Vec3.atBottomCenterOf(fixture.head().blockPosition()));
			})
			.thenIdle(160)
			.thenExecute(() -> {
				NemesisState state = NemesisState.get(nemesis[0]);
				UUID current = state == null ? null : state.companionId().orElse(null);
				PatrolTestSupport.result(test, "original=" + companionId[0] + " current=" + current
					+ " originalAlive=" + (helper.getLevel().getEntity(companionId[0]) != null));
				helper.assertTrue(companionId[0].equals(current) && helper.getLevel().getEntity(current) == null,
					"a killed elite companion respawned");
				releaseSafeTargetSite(helper, safeSite);
			})
			.thenSucceed();
	}

	@GameTest(dimension = "minecraft:the_nether")
	public void everyNemesisKitPieceHasThreeEnchantments(final GameTestHelper helper) {
		int checked = 0;
		try {
			var method = Class.forName("com.stella.villages.mission.NemesisKit")
				.getDeclaredMethod("armNemesis", Mob.class, GearTier.class);
			method.setAccessible(true);
			for (int index = 0; index < 100; index++) {
				Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
				GearTier tier = index % 2 == 0 ? GearTier.DIAMOND : GearTier.NETHERITE;
				method.invoke(null, mob, tier);
				if (!hasNemesisKit(mob)) {
					PatrolTestSupport.result("everyNemesisKitPieceHasThreeEnchantments", "checked=" + checked
						+ " failedTier=" + tier);
					helper.fail("a Nemesis kit piece has fewer than three enchantments");
					return;
				}
				checked++;
			}
		} catch (ReflectiveOperationException error) {
			helper.fail("could not arm Nemesis kit: " + error);
			return;
		}
		PatrolTestSupport.result("everyNemesisKitPieceHasThreeEnchantments", "checked=" + checked + " valid=true");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void meleeNemesisTeleportsNearPlayer(final GameTestHelper helper) {
		testTeleport(helper, "meleeNemesisTeleportsNearPlayer", EntityTypes.ZOMBIE, Items.DIAMOND_SWORD, 5.0);
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rangedNemesisTeleportsToSafeDistance(final GameTestHelper helper) {
		testTeleport(helper, "rangedNemesisTeleportsToSafeDistance", EntityTypes.SKELETON, Items.BOW, 12.0);
	}

	private static void testTeleport(
		final GameTestHelper helper, final String name, final EntityType<? extends Mob> type,
		final Item weapon, final double maximumDistance
	) {
		BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
		ServerLevel level = helper.getLevel();
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 13; dz++) {
				BlockPos floor = center.offset(dx, -1, dz);
				level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int dy = 1; dy <= 3; dy++) {
					level.setBlockAndUpdate(floor.above(dy), Blocks.AIR.defaultBlockState());
				}
			}
		}
		level.setBlockAndUpdate(center.offset(20, -1, 0), Blocks.STONE.defaultBlockState());
		for (int dy = 0; dy < 3; dy++) {
			level.setBlockAndUpdate(center.offset(20, dy, 0), Blocks.AIR.defaultBlockState());
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		movePlayer(player, Vec3.atBottomCenterOf(center));
		Mob nemesis = type.create(level, EntitySpawnReason.COMMAND);
		if (nemesis == null) {
			helper.fail("could not create teleport subject");
			return;
		}
		nemesis.snapTo(Vec3.atBottomCenterOf(center.offset(20, 0, 0)), 0.0F, 0.0F);
		nemesis.setNoAi(true);
		nemesis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
		level.addFreshEntity(nemesis);
		helper.runAfterDelay(10, () -> {
			boolean teleported = MissionService.tryTeleportTowards(level, nemesis, player);
			double distance = Math.sqrt(nemesis.distanceToSqr(player));
			boolean safe = level.getBlockState(nemesis.blockPosition().below()).is(Blocks.STONE);
			boolean valid = teleported && safe && distance >= 2.0 && distance <= maximumDistance;
			PatrolTestSupport.result(name, "teleported=" + teleported + " safe=" + safe
				+ " distance=" + distance + " valid=" + valid);
			nemesis.discard();
			helper.assertTrue(valid, "Nemesis did not teleport to the correct safe position");
			helper.succeed();
		});
	}

	/** Each board choice as "offer:outcome", keyed by the player, from the board's own record. */
	private static final Map<UUID, List<String>> CHOICES = new ConcurrentHashMap<>();

	static {
		// These fixtures use three hostile kinds; a board otherwise draws from every eligible kind.
		com.stella.villages.mission.MissionService.setOfferPool(List.of(
			Identifier.parse("minecraft:zombie"), Identifier.parse("minecraft:skeleton"), Identifier.parse("minecraft:spider")
		));
		MissionBoardMenu.setRecorder(new MissionBoardMenu.Recorder() {
			@Override
			public void chose(final ServerPlayer player, final UUID offerId, final boolean accepted, final String reason) {
				CHOICES.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(offerId + ":" + reason);
			}
		});
	}

	private static final String MISSION_SERVICE = "com.stella.villages.mission.MissionService";
	private static final String NEMESIS_STATE = "com.stella.villages.mission.NemesisState";
	private static final String NEMESIS_MAP_ITEM = "com.stella.villages.mission.NemesisMapItem";
	private static final int TEST_MAX_TICKS = 3200;
	// Waits on chunk and entity IO end at the test limit; a fixed short bound measured machine speed.
	private static final int DIAGNOSTIC_BOUND_TICKS = TEST_MAX_TICKS - 20;

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void headOffersThreeTargets(final GameTestHelper helper) {
		String test = "headOffersThreeTargets";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}

		InteractionResult interaction = UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		List<?> offers = offersFor(fixture.owner().getUUID());
		List<Identifier> types = targetTypes(offers);
		// The board holds nine offers: the first three are the Nemesis contracts this case was written for.
		List<Identifier> nemesisTypes = types.size() == 9 ? types.subList(0, 3) : List.of();
		boolean threeDistinct = nemesisTypes.size() == 3 && new HashSet<>(nemesisTypes).size() == 3;
		boolean nonBossHostiles = types.size() == 9 && types.stream().allMatch(this::isNonBossHostile);
		boolean boardOpened = interaction.consumesAction() && offers != null;

		PatrolTestSupport.result(test, "boardOpened=" + boardOpened + " offerCount=" + (offers == null ? -1 : offers.size())
			+ " distinctTypes=" + threeDistinct + " nonBossHostiles=" + nonBossHostiles);
		helper.assertTrue(boardOpened && threeDistinct && nonBossHostiles,
			"right-clicking a registered head did not open three distinct hostile non-boss offers");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void serverRejectsStaleForeignAndOutOfRangeOffers(final GameTestHelper helper) {
		String test = "serverRejectsStaleForeignAndOutOfRangeOffers";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		List<?> offers = offersFor(fixture.owner().getUUID());
		UUID offerId = firstOfferId(offers);
		if (offerId == null) {
			PatrolTestSupport.result(test, "offers=MISSING");
			helper.assertTrue(false, "mission offer IDs are missing");
			return;
		}

		int before = missionsFor(helper.getLevel(), fixture.owner().getUUID()).size();
		boolean staleAccepted = Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), UUID.randomUUID()
		));
		boolean foreignHeadAccepted = Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), UUID.randomUUID(), offerId
		));
		movePlayer(fixture.owner(), fixture.head().position().add(32.0, 0.0, 0.0));
		boolean outOfRangeAccepted = Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		int after = missionsFor(helper.getLevel(), fixture.owner().getUUID()).size();
		boolean offerRetained = offersFor(fixture.owner().getUUID()) != null;
		boolean stateMatches = !staleAccepted && !foreignHeadAccepted && !outOfRangeAccepted
			&& before == after && offerRetained;

		PatrolTestSupport.result(test, "staleAccepted=" + staleAccepted + " foreignHeadAccepted=" + foreignHeadAccepted
			+ " outOfRangeAccepted=" + outOfRangeAccepted + " missionCount=" + after + " offerRetained=" + offerRetained
			+ " stateMatches=" + stateMatches);
		helper.assertTrue(stateMatches, "server accepted a stale, foreign-head, or out-of-range offer");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void mapMatchesPersistentTarget(final GameTestHelper helper) {
		String test = "mapMatchesPersistentTarget";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		LevelChunk safeChunk = helper.getLevel().getChunkSource().getChunkNow(safeSite.getX() >> 4, safeSite.getZ() >> 4);
		int worldSurfaceY = safeChunk == null ? helper.getLevel().getMinY()
			: safeChunk.getHeight(Heightmap.Types.WORLD_SURFACE, safeSite.getX(), safeSite.getZ());
		boolean siteMatchesHeightmap = safeChunk != null && worldSurfaceY + 1 == safeSite.getY();
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		releaseSafeTargetSite(helper, safeSite);
		var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
		Item mapItem = nemesisMapItem();
		ItemStack map = mapItem == null ? ItemStack.EMPTY : findMap(fixture.owner().getInventory(), mapItem);
		BlockPos mapTarget = map.isEmpty() ? null : targetPosition(map);
		MapId mapId = map.isEmpty() ? null : map.get(DataComponents.MAP_ID);
		MapItemSavedData mapData = map.isEmpty() ? null : MapItem.getSavedData(map, helper.getLevel());
		boolean mapLocked = mapData != null && mapData.locked;
		MapDecorations decorations = map.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
		MapDecorations.Entry marker = decorations.decorations().get("nemesis");
		boolean mapMarkerMatches = mission != null && marker != null
			&& marker.type().equals(MapDecorationTypes.TARGET_POINT)
			&& marker.x() == mission.targetPosition().getX() && marker.z() == mission.targetPosition().getZ();
		Optional<?> persistedMapId = mission == null ? null : missionMapId(mission);
		boolean mapIdMatches = persistedMapId != null && Optional.ofNullable(mapId).equals(persistedMapId);
		boolean stateMatches = accepted && siteMatchesHeightmap && mission != null && !map.isEmpty() && mapLocked
			&& mapMarkerMatches && mission.targetPosition().equals(mapTarget) && mapIdMatches
			&& mission.targetPosition().distToCenterSqr(Vec3.atCenterOf(fixture.head().blockPosition()))
				>= (double) missionMinDistance() * missionMinDistance()
			&& mission.targetPosition().distToCenterSqr(Vec3.atCenterOf(fixture.head().blockPosition()))
				<= (double) missionMaxDistance() * missionMaxDistance();

		PatrolTestSupport.result(test, "accepted=" + accepted + " siteMatchesHeightmap=" + siteMatchesHeightmap
			+ " siteSupportY=" + worldSurfaceY + " missionFound=" + (mission != null)
			+ " mapFound=" + !map.isEmpty() + " missionTarget=" + (mission == null ? "missing" : mission.targetPosition())
			+ " mapTarget=" + mapTarget + " mapId=" + mapId + " mapIdMatches=" + mapIdMatches
			+ " mapLocked=" + mapLocked + " mapMarkerMatches=" + mapMarkerMatches + " stateMatches=" + stateMatches);
		helper.assertTrue(stateMatches, "Nemesis map marker did not match its persistent mission target");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_end", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void targetSearchRejectsUnsafeLocationAndSpiderNarrowClearance(final GameTestHelper helper) {
		String test = "targetSearchRejectsUnsafeLocationAndSpiderNarrowClearance";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		List<BlockPos> candidates = targetCandidates(fixture.head().blockPosition());
		if (candidates == null || candidates.isEmpty() || !prepareUnsafeTargetSites(helper, candidates)) {
			PatrolTestSupport.result(test, "unsafeSitesPrepared=false");
			helper.fail("could not prepare unsafe target sites");
			return;
		}
		ServerLevel level = helper.getLevel();
		Set<CandidateChunk> candidateChunks = new HashSet<>();
		for (BlockPos candidate : candidates) {
			candidateChunks.add(new CandidateChunk(candidate.getX() >> 4, candidate.getZ() >> 4));
		}
		Set<CandidateChunk> loadedBefore = loadedCandidateChunks(level, candidateChunks);
		Set<Long> forcedBefore = new HashSet<>(level.getForceLoadedChunks());
		Identifier spiderType = Identifier.parse("minecraft:spider");
		Optional<?> unsafeSearch = findSafeTarget(level, fixture.head().blockPosition(), spiderType);
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), level, InteractionHand.MAIN_HAND, fixture.head(), null
		);
		List<?> offers = offersFor(fixture.owner().getUUID());
		UUID spiderOfferId = offerIdForTargetType(offers, spiderType);
		int missionsBefore = missionsFor(level, fixture.owner().getUUID()).size();
		boolean unsafeAccepted = spiderOfferId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), spiderOfferId
		));
		int missionsAfterUnsafe = missionsFor(level, fixture.owner().getUUID()).size();
		boolean unsafeOfferRetained = spiderOfferId != null
			&& hasOfferId(offersFor(fixture.owner().getUUID()), spiderOfferId);
		if (!prepareSpiderNarrowClearanceSites(helper, candidates)) {
			PatrolTestSupport.result(test, "spiderSitesPrepared=false unsafeAccepted=" + unsafeAccepted);
			helper.fail("could not prepare Spider-clearance sites");
			return;
		}
		BlockPos candidate = candidates.getFirst();
		LevelChunk chunk = level.getChunkSource().getChunkNow(candidate.getX() >> 4, candidate.getZ() >> 4);
		if (chunk == null) {
			PatrolTestSupport.result(test, "firstCandidateChunk=missing unsafeAccepted=" + unsafeAccepted);
			helper.fail("first Spider candidate chunk was not loaded");
			return;
		}
		int supportY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
		BlockPos site = new BlockPos(candidate.getX(), supportY + 1, candidate.getZ());
		Mob spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.COMMAND);
		Mob vindicator = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.COMMAND);
		if (spider == null || vindicator == null) {
			PatrolTestSupport.result(test, "probeMissing=true unsafeAccepted=" + unsafeAccepted);
			helper.fail("could not create target-size probes");
			return;
		}
		spider.snapTo(Vec3.atBottomCenterOf(site), 0.0F, 0.0F);
		vindicator.snapTo(Vec3.atBottomCenterOf(site), 0.0F, 0.0F);
		boolean spiderFits = level.noCollision(spider, spider.getBoundingBox());
		boolean vindicatorFits = level.noCollision(vindicator, vindicator.getBoundingBox());
		boolean spiderAccepted = spiderOfferId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), spiderOfferId
		));
		int missionsAfterSpider = missionsFor(level, fixture.owner().getUUID()).size();
		boolean spiderOfferRetained = spiderOfferId != null
			&& hasOfferId(offersFor(fixture.owner().getUUID()), spiderOfferId);
		Set<CandidateChunk> loadedAfter = loadedCandidateChunks(level, candidateChunks);
		Set<Long> forcedAfter = new HashSet<>(level.getForceLoadedChunks());
		boolean forceTicketsUnchanged = forcedBefore.equals(forcedAfter);
		boolean stateMatches = unsafeSearch != null && unsafeSearch.isEmpty() && !unsafeAccepted
			&& missionsBefore == missionsAfterUnsafe && unsafeOfferRetained
			&& !spiderFits && vindicatorFits && !spiderAccepted
			&& missionsAfterUnsafe == missionsAfterSpider && spiderOfferRetained && forceTicketsUnchanged;

		PatrolTestSupport.result(test, "unsafeSiteFound=" + (unsafeSearch != null && unsafeSearch.isPresent())
			+ " unsafeAccepted=" + unsafeAccepted + " unsafeOfferRetained=" + unsafeOfferRetained
			+ " spiderFits=" + spiderFits + " vindicatorFits=" + vindicatorFits
			+ " spiderAccepted=" + spiderAccepted + " missionsBefore=" + missionsBefore
			+ " missionsAfterUnsafe=" + missionsAfterUnsafe + " missionsAfterSpider=" + missionsAfterSpider
			+ " spiderOfferRetained=" + spiderOfferRetained
			+ " candidateChunksLoadedBefore=" + (loadedBefore == null ? "missing" : loadedBefore.size())
			+ " candidateChunksLoadedAfter=" + (loadedAfter == null ? "missing" : loadedAfter.size())
			+ " forceTicketsUnchanged=" + forceTicketsUnchanged + " stateMatches=" + stateMatches);
		helper.assertTrue(stateMatches,
			"mission search accepted unsafe support or a Spider target that cannot fit its collision box");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void targetNotForcedLoaded(final GameTestHelper helper) {
		String test = "targetNotForcedLoaded";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		releaseSafeTargetSite(helper, safeSite);
		var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
		boolean forced = mission != null && helper.getLevel().getForceLoadedChunks()
			.contains(ChunkPos.pack(mission.targetPosition().getX() >> 4, mission.targetPosition().getZ() >> 4));
		boolean stateMatches = accepted && mission != null && !forced;

		PatrolTestSupport.result(test, "accepted=" + accepted + " missionTarget="
			+ (mission == null ? "missing" : mission.targetPosition()) + " chunkForced=" + forced
			+ " stateMatches=" + stateMatches);
		helper.assertTrue(stateMatches, "mission target acceptance forced its chunk to stay loaded");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void fullInventoryDropsMap(final GameTestHelper helper) {
		String test = "fullInventoryDropsMap";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		fixture.owner().getAbilities().instabuild = false;
		Inventory inventory = fixture.owner().getInventory();
		for (int index = 0; index < inventory.getNonEquipmentItems().size(); index++) {
			inventory.setItem(index, new ItemStack(Items.DIRT, 64));
		}
		boolean noCapacity = !inventory.add(new ItemStack(Items.GOLD_INGOT));
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		releaseSafeTargetSite(helper, safeSite);
		Item mapItem = nemesisMapItem();
		boolean[] mapLogged = {false};
		helper.startSequence()
			.thenWaitUntil(() -> {
				boolean mapInInventory = mapItem != null && !findMap(inventory, mapItem).isEmpty();
				int nearbyMapDrops = mapItem == null ? 0 : helper.getLevel().getEntitiesOfClass(
					ItemEntity.class, AABB.ofSize(fixture.owner().position(), 8.0, 8.0, 8.0),
						item -> item.isAlive() && item.getItem().is(mapItem)
					).size();
				helper.assertTrue(mapInInventory || nearbyMapDrops > 0, "mission map entity is not visible yet");
			})
			.thenExecute(() -> {
				boolean mapInInventory = mapItem != null && !findMap(inventory, mapItem).isEmpty();
				List<ItemEntity> nearbyItems = helper.getLevel().getEntitiesOfClass(
					ItemEntity.class, AABB.ofSize(fixture.owner().position(), 8.0, 8.0, 8.0), ItemEntity::isAlive
				);
				int nearbyMapDrops = mapItem == null ? 0
					: (int) nearbyItems.stream().filter(item -> item.getItem().is(mapItem)).count();
				boolean ownerEntityTicking = helper.getLevel().isPositionEntityTicking(fixture.owner().blockPosition());
				boolean mapDropped = nearbyMapDrops == 1;
				boolean stateMatches = accepted && noCapacity && !mapInInventory && mapDropped;

				mapLogged[0] = true;
				PatrolTestSupport.result(test, "accepted=" + accepted + " noCapacity=" + noCapacity
					+ " ownerEntityTicking=" + ownerEntityTicking + " mapInInventory=" + mapInInventory
					+ " nearbyMapDrops=" + nearbyMapDrops + " otherItems=" + nearbyItems.size()
					+ " mapDropped=" + mapDropped + " stateMatches=" + stateMatches);
				helper.assertTrue(stateMatches, "full inventory rejected a mission map instead of dropping it");
			})
			.thenSucceed();
		helper.runAfterDelay(DIAGNOSTIC_BOUND_TICKS, () -> {
			if (!mapLogged[0]) {
				boolean mapInInventory = mapItem != null && !findMap(inventory, mapItem).isEmpty();
				int nearbyMapDrops = mapItem == null ? 0 : helper.getLevel().getEntitiesOfClass(
					ItemEntity.class, AABB.ofSize(fixture.owner().position(), 8.0, 8.0, 8.0),
						item -> item.isAlive() && item.getItem().is(mapItem)
					).size();
				PatrolTestSupport.result(test, "accepted=" + accepted + " noCapacity=" + noCapacity
					+ " ownerEntityTicking=" + helper.getLevel().isPositionEntityTicking(fixture.owner().blockPosition())
					+ " mapInInventory=" + mapInInventory + " nearbyMapDrops=" + nearbyMapDrops
					+ " stateMatches=false");
				helper.fail("mission map entity did not become visible before its test bound");
			}
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nemesisUsesProfileAndEnragesOnce(final GameTestHelper helper) {
		String test = "nemesisUsesProfileAndEnragesOnce";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		movePlayer(fixture.owner(), Vec3.atBottomCenterOf(safeSite));
		Mob[] target = {null};
		boolean[] targetEntityTicking = {false};
		boolean[] profileLogged = {false};
		int[] strengthDuration = {-1};
		int[] speedDuration = {-1};
		helper.startSequence()
			.thenWaitUntil(() -> {
				var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
				targetEntityTicking[0] = helper.getLevel().isPositionEntityTicking(safeSite);
				target[0] = mission == null ? null
					: loadNemesis(test, helper.getLevel(), mission.id(), mission.targetPosition());
				helper.assertTrue(targetEntityTicking[0] && target[0] != null,
					"Nemesis did not enter the entity-ticking target chunk");
			})
			.thenExecute(() -> {
				var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
				Mob nemesis = target[0];
				boolean prefix = nemesis.getCustomName() != null
					&& nemesis.getCustomName().getString().startsWith("[Quest] <Nemesis>");
				boolean tagged = nemesis.entityTags().contains(MissionService.NEMESIS_TAG);
				double baselineHealth = mission == null ? 0.0 : baseHealth(helper.getLevel(), mission.targetType());
				double expectedHealth = baselineHealth * Elite.HEALTH_MULTIPLIER
					+ MissionService.NEMESIS_BONUS_HEALTH;
				boolean equipped = hasNemesisKit(nemesis);
				NemesisState state = NemesisState.get(nemesis);
				Mob companion = state == null ? null : state.companionId()
					.map(id -> helper.getLevel().getEntity(id)).filter(entity -> entity instanceof Mob)
					.map(entity -> (Mob) entity).orElse(null);
				helper.assertTrue(tagged && (companion == null || !companion.entityTags().contains(MissionService.NEMESIS_TAG)),
					"Nemesis tag missing, or the companion carries it");
				boolean sharedBuff = companion != null && nemesis.getActiveEffects().stream()
					.anyMatch(effect -> effect.getDuration() == -1 && companion.hasEffect(effect.getEffect()));
				boolean elitePair = Elite.isElite(nemesis) && companion != null
					&& Elite.isElite(companion) && companion.getType() == nemesis.getType() && sharedBuff;
				boolean initiallyUnenraged = !isEnraged(nemesis);
				boolean stateMatches = accepted && prefix && Math.abs(nemesis.getMaxHealth() - expectedHealth) < 0.001
					&& equipped && elitePair && initiallyUnenraged;

				profileLogged[0] = true;
				PatrolTestSupport.detail(test, "accepted=" + accepted + " targetEntityTicking=" + targetEntityTicking[0]
					+ " nemesis=true" + " namePrefix=" + prefix + " health=" + nemesis.getMaxHealth()
					+ " equipped=" + equipped + " elitePair=" + elitePair
					+ " initiallyUnenraged=" + initiallyUnenraged + " stateMatches=" + stateMatches);
				helper.assertTrue(stateMatches, "Nemesis did not use its profile before enrage");
				nemesis.setHealth(nemesis.getMaxHealth() * 0.5F + 2.0F);
				nemesis.hurt(helper.getLevel().damageSources().generic(), 10.0F);
				var strengthEffect = nemesis.getEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
				var speedEffect = nemesis.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
				strengthDuration[0] = strengthEffect == null ? -1 : strengthEffect.getDuration();
				speedDuration[0] = speedEffect == null ? -1 : speedEffect.getDuration();
				PatrolTestSupport.detail(test, "strengthDuration=" + strengthDuration[0]
					+ " speedDuration=" + speedDuration[0] + " expectedDuration="
					+ com.stella.villages.mission.MissionService.NEMESIS_ENRAGE_DURATION);
			})
			.thenWaitUntil(() -> helper.assertTrue(
				target[0] != null && isEnraged(target[0]), "Nemesis did not enrage at half health"
			))
			.thenExecute(() -> {
				Mob nemesis = target[0];
				boolean strength = nemesis.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
				boolean speed = nemesis.hasEffect(net.minecraft.world.effect.MobEffects.SPEED);
				boolean durationMatches = strengthDuration[0] == com.stella.villages.mission.MissionService.NEMESIS_ENRAGE_DURATION
					&& speedDuration[0] == com.stella.villages.mission.MissionService.NEMESIS_ENRAGE_DURATION;
				helper.assertTrue(strength && speed && durationMatches,
					"Nemesis enrage did not grant Strength and Speed for the required duration");
				nemesis.removeEffect(net.minecraft.world.effect.MobEffects.STRENGTH);
				nemesis.removeEffect(net.minecraft.world.effect.MobEffects.SPEED);
				nemesis.setHealth(nemesis.getMaxHealth() * 0.4F);
				nemesis.hurt(helper.getLevel().damageSources().generic(), 1.0F);
				helper.assertFalse(nemesis.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH)
					|| nemesis.hasEffect(net.minecraft.world.effect.MobEffects.SPEED),
					"Nemesis enrage effects were applied more than once");
				PatrolTestSupport.result(test, "enraged=" + isEnraged(nemesis) + " strength=" + strength
					+ " speed=" + speed + " strengthDuration=" + strengthDuration[0]
					+ " speedDuration=" + speedDuration[0] + " durationMatches=" + durationMatches);
				movePlayer(fixture.owner(), Vec3.atBottomCenterOf(fixture.head().blockPosition()));
				releaseSafeTargetSite(helper, safeSite);
			})
			.thenSucceed();
		helper.runAfterDelay(DIAGNOSTIC_BOUND_TICKS, () -> {
			if (!profileLogged[0]) {
				PatrolTestSupport.result(test, "accepted=" + accepted + " nemesis=false entityLookupWaitTicks="
					+ DIAGNOSTIC_BOUND_TICKS + " stateMatches=false");
				helper.fail("Nemesis did not enter the target chunk before the test bound");
			}
		});
	}

	@GameTest(dimension = "minecraft:the_end", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nemesisPersistsAfterTargetChunkReload(final GameTestHelper helper) {
		String test = "nemesisPersistsAfterTargetChunkReload";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		boolean[] initialEntityLoaded = {false};
		boolean[] lifecycleLogged = {false};
		int[] lifecycleStage = {0};
		int[] targetChunkX = {0};
		int[] targetChunkZ = {0};
		UUID[] nemesisId = {null};
		BlockPos[] targetPosition = {null};
		helper.runAfterDelay(1, () -> {
			BlockPos safeSite = prepareSafeTargetSiteAwayFromPlayers(
				helper, test, fixture.head().blockPosition(), fixture.owner()
			);
			if (safeSite == null) {
				return;
			}
			UseEntityCallback.EVENT.invoker().interact(
				fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
			);
			UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
			boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
				fixture.owner(), fixture.head().getUUID(), offerId
			));
			var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
			UUID entityId = mission == null ? null : mission.entityId().orElse(null);
			if (!accepted || mission == null || entityId == null) {
				PatrolTestSupport.result(test, "accepted=" + accepted + " mission=" + (mission != null)
					+ " entityId=" + entityId + " stateMatches=false");
				helper.fail("mission target record did not include its Nemesis entity");
				return;
			}
			UUID missionId = mission.id();
			BlockPos missionTarget = mission.targetPosition();
			nemesisId[0] = entityId;
			targetPosition[0] = missionTarget;
			targetChunkX[0] = missionTarget.getX() >> 4;
			targetChunkZ[0] = missionTarget.getZ() >> 4;
			movePlayer(fixture.owner(), Vec3.atBottomCenterOf(missionTarget));
			helper.startSequence()
				.thenWaitUntil(() -> {
					boolean entityTicking = helper.getLevel().isPositionEntityTicking(missionTarget);
					Mob nemesis = loadNemesis(test, helper.getLevel(), missionId, missionTarget);
					initialEntityLoaded[0] = entityTicking && nemesis != null && entityId.equals(nemesis.getUUID());
					helper.assertTrue(initialEntityLoaded[0], "Nemesis entity did not enter the entity-ticking target chunk");
					lifecycleStage[0] = 1;
					PatrolTestSupport.detail(test, "lifecycleStage=initial_entity_loaded");
				})
				.thenExecute(() -> {
					movePlayer(fixture.owner(), Vec3.atBottomCenterOf(fixture.head().blockPosition()));
					releaseSafeTargetSite(helper, safeSite);
					lifecycleStage[0] = 2;
					PatrolTestSupport.detail(test, "lifecycleStage=waiting_for_unload");
				})
				.thenWaitUntil(() -> helper.assertFalse(
					helper.getLevel().getChunkSource().hasChunk(targetChunkX[0], targetChunkZ[0]),
					"Nemesis target chunk did not unload"
				))
				.thenExecute(() -> {
					helper.assertTrue(helper.getLevel().getEntityInAnyDimension(entityId) == null,
						"Nemesis remained loaded after its chunk unload");
					lifecycleStage[0] = 3;
					PatrolTestSupport.detail(test, "lifecycleStage=target_chunk_unloaded");
					movePlayer(fixture.owner(), Vec3.atBottomCenterOf(missionTarget));
				})
				.thenWaitUntil(() -> helper.assertTrue(
					helper.getLevel().isPositionEntityTicking(missionTarget)
						&& loadNemesis(test, helper.getLevel(), missionId, missionTarget) != null,
					"Nemesis did not return after its target chunk reloaded naturally"
				))
				.thenExecute(() -> {
					Mob reloaded = loadNemesis(test, helper.getLevel(), missionId, missionTarget);
					boolean sameEntity = reloaded != null && entityId.equals(reloaded.getUUID());
					boolean sameMission = reloaded != null && missionId.equals(nemesisMissionId(reloaded));
					movePlayer(fixture.owner(), Vec3.atBottomCenterOf(fixture.head().blockPosition()));
					lifecycleStage[0] = 4;
					lifecycleLogged[0] = true;
					PatrolTestSupport.result(test, "chunkUnloaded=true initialEntityLoaded=" + initialEntityLoaded[0]
						+ " sameEntity=" + sameEntity + " sameMission=" + sameMission);
					helper.assertTrue(sameEntity && sameMission, "Nemesis identity or mission state changed after reload");
				})
				.thenSucceed();
		});
		helper.runAfterDelay(DIAGNOSTIC_BOUND_TICKS, () -> {
			if (!lifecycleLogged[0]) {
				boolean targetKnown = targetPosition[0] != null;
				boolean targetChunkLoaded = targetKnown
					&& helper.getLevel().getChunkSource().hasChunk(targetChunkX[0], targetChunkZ[0]);
				boolean targetChunkForced = targetKnown && helper.getLevel().getForceLoadedChunks()
					.contains(ChunkPos.pack(targetChunkX[0], targetChunkZ[0]));
				boolean entityLoaded = nemesisId[0] != null
					&& helper.getLevel().getEntityInAnyDimension(nemesisId[0]) != null;
				String playerPositions = helper.getLevel().players().stream()
					.map(player -> player.getUUID() + "@" + player.blockPosition()).toList().toString();
				PatrolTestSupport.result(test, "targetKnown=" + targetKnown
					+ " lifecycleStage=" + lifecycleStage[0] + " targetChunkLoaded=" + targetChunkLoaded
					+ " targetChunkForced=" + targetChunkForced + " entityLoaded=" + entityLoaded
					+ " observerPosition=" + fixture.owner().blockPosition() + " players=" + playerPositions
					+ " lifecycleCompleted=false stateMatches=false");
				helper.fail("Nemesis persistence lifecycle did not finish before the test bound");
			}
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nonOwnerKillPaysOnce(final GameTestHelper helper) {
		String test = "nonOwnerKillPaysOnce";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}
		UseEntityCallback.EVENT.invoker().interact(
			fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null
		);
		UUID offerId = firstOfferId(offersFor(fixture.owner().getUUID()));
		boolean accepted = offerId != null && Boolean.TRUE.equals(acceptOffer(
			fixture.owner(), fixture.head().getUUID(), offerId
		));
		var mission = missionsFor(helper.getLevel(), fixture.owner().getUUID()).stream().findFirst().orElse(null);
		if (!accepted || mission == null || mission.entityId().isEmpty()) {
			releaseSafeTargetSite(helper, safeSite);
			PatrolTestSupport.result(test, "accepted=" + accepted + " mission=" + (mission != null)
				+ " entityId=" + (mission == null ? "missing" : mission.entityId()) + " stateMatches=false");
			helper.assertTrue(false, "accepted mission did not persist its Nemesis entity ID");
			return;
		}
		UUID missionId = mission.id();
		BlockPos targetPosition = mission.targetPosition();
		Item expectedHead = NemesisHead.itemFor(mission.targetType());
		ServerPlayer killer = helper.makeMockServerPlayerInLevel();
		movePlayer(killer, Vec3.atBottomCenterOf(targetPosition));
		boolean nonOwner = !killer.getUUID().equals(fixture.owner().getUUID());
		Mob[] nemesis = {null};
		ItemStack[][] assignedGear = {null};
		int[] emeraldBefore = {0};
		int paidEmeralds = MissionService.REWARD_EMERALD_STACKS * Items.EMERALD.getDefaultMaxStackSize();
		boolean[] rewardsLogged = {false};
		helper.startSequence()
			.thenWaitUntil(() -> {
				boolean targetEntityTicking = helper.getLevel().isPositionEntityTicking(targetPosition);
				nemesis[0] = loadNemesis(test, helper.getLevel(), missionId, targetPosition);
				helper.assertTrue(targetEntityTicking && nemesis[0] != null,
					"Nemesis did not enter the entity-ticking target chunk for the non-owner kill");
			})
			.thenExecute(() -> {
				assignedGear[0] = assignedGear(nemesis[0]);
				emeraldBefore[0] = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.EMERALD);
				var source = helper.getLevel().damageSources().playerAttack(killer);
				nemesis[0].hurt(source, nemesis[0].getMaxHealth() * 4.0F);
				ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(nemesis[0], source);
				movePlayer(killer, Vec3.atBottomCenterOf(fixture.head().blockPosition()));
			})
			.thenWaitUntil(() -> {
				int emeralds = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.EMERALD);
				boolean complete = emeralds == paidEmeralds && emeralds >= emeraldBefore[0] && missionRemoved(helper.getLevel(), missionId);
				helper.assertTrue(complete, "non-owner Nemesis reward drops are not ready");
			})
			.thenExecute(() -> {
				int emeralds = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.EMERALD);
				int diamonds = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.DIAMOND);
				int debris = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.ANCIENT_DEBRIS);
				int heads = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), expectedHead);
				int potions = droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.POTION);
				int books = droppedBookCount(helper.getLevel(), nemesis[0].blockPosition());
				int gearDrops = assignedGearDropCount(helper.getLevel(), nemesis[0].blockPosition(), assignedGear[0]);
				boolean missionRemoved = missionRemoved(helper.getLevel(), missionId);
				boolean stateMatches = accepted && nonOwner && missionRemoved && emeralds == paidEmeralds
					&& emeralds >= emeraldBefore[0] && potions == MissionService.REWARD_POTIONS && books == 1 && heads == 0
					&& (diamonds == 0 || diamonds == MissionService.REWARD_BONUS_DIAMONDS) && debris <= 1 && gearDrops <= 1;
				rewardsLogged[0] = true;
				releaseSafeTargetSite(helper, safeSite);
				PatrolTestSupport.result(test, "accepted=" + accepted + " nonOwner=" + nonOwner
					+ " emeralds=" + emeralds + " potions=" + potions + " books=" + books + " heads=" + heads
					+ " diamonds=" + diamonds + " debris=" + debris + " gearDrops=" + gearDrops
					+ " missionRemoved=" + missionRemoved + " stateMatches=" + stateMatches);
				helper.assertTrue(stateMatches,
					"non-owner Nemesis kill did not complete and reward its mission exactly once");
			})
			.thenSucceed();
		helper.runAfterDelay(DIAGNOSTIC_BOUND_TICKS, () -> {
			if (!rewardsLogged[0]) {
				boolean nemesisLoaded = nemesis[0] != null;
				int emeralds = nemesisLoaded ? droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.EMERALD) : 0;
				int potions = nemesisLoaded ? droppedCount(helper.getLevel(), nemesis[0].blockPosition(), Items.POTION) : 0;
				int heads = nemesisLoaded ? droppedCount(helper.getLevel(), nemesis[0].blockPosition(), expectedHead) : 0;
				int gearDrops = nemesisLoaded && assignedGear[0] != null
					? assignedGearDropCount(helper.getLevel(), nemesis[0].blockPosition(), assignedGear[0]) : 0;
				PatrolTestSupport.result(test, "accepted=" + accepted + " nonOwner=" + nonOwner
					+ " nemesisLoaded=" + nemesisLoaded + " emeralds=" + emeralds + " potions=" + potions
					+ " heads=" + heads + " gearDrops=" + gearDrops
					+ " rewardsReady=false stateMatches=false");
				helper.fail("non-owner Nemesis rewards were incomplete");
			}
		});
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void unlimitedActiveMissions(final GameTestHelper helper) {
		String test = "unlimitedActiveMissions";
		UUID ownerId = UUID.randomUUID();
		var data = com.stella.villages.data.VillageWorldData.get(helper.getLevel());
		Identifier targetType = Identifier.parse("minecraft:zombie");
		for (int index = 0; index < 1000; index++) {
			data.addMission(new com.stella.villages.data.VillageWorldData.MissionRecord(
				UUID.randomUUID(), ownerId, targetType,
				helper.absolutePos(new BlockPos(index, 1, 2)), Optional.of(UUID.randomUUID()),
				com.stella.villages.data.VillageWorldData.MissionStatus.ACTIVE, Optional.empty(), helper.getLevel().dimension()
			));
		}
		long activeForOwner = data.missions().stream().filter(mission -> mission.ownerId().equals(ownerId)).count();
		boolean stateMatches = activeForOwner == 1000L;

		PatrolTestSupport.result(test, "owner=" + ownerId + " activeMissions=" + activeForOwner
			+ " limit=none stateMatches=" + stateMatches);
		helper.assertTrue(stateMatches, "mission ledger imposed a lifetime mission cap");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rightClickOpensTheMissionBoardMenu(final GameTestHelper helper) {
		String test = "rightClickOpensTheMissionBoardMenu";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}

		UseEntityCallback.EVENT.invoker().interact(fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null);
		helper.assertTrue(fixture.owner().containerMenu instanceof MissionBoardMenu, "right-clicking the head opened " + fixture.owner().containerMenu);
		MissionBoardMenu menu = (MissionBoardMenu) fixture.owner().containerMenu;
		List<MissionService.MissionOffer> offers = MissionService.offersFor(fixture.owner().getUUID());
		helper.assertTrue(menu.offers().equals(offers) && menu.headId().equals(fixture.head().getUUID()), "the menu shows a different board than the server holds");
		for (int slot = 0; slot < menu.getContainer().getContainerSize(); slot++) {
			int index = MissionBoardMenu.offerIndex(slot);
			ItemStack icon = menu.getContainer().getItem(slot);
			if (index < 0 && slot == com.stella.villages.mission.SlayerContract.SLOT) {
				helper.assertTrue(icon.is(Items.SLIME_BALL), "the slayer slot holds " + icon);
				continue;
			}
			if (index < 0 && com.stella.villages.mission.SlimeTrade.tradeIndex(slot) >= 0) {
				ItemStack expected = com.stella.villages.mission.SlimeTrade.icon(com.stella.villages.mission.SlimeTrade.tradeIndex(slot))
					.orElse(new ItemStack(Items.STAINED_GLASS_PANE.gray()));
				helper.assertTrue(icon.is(expected.getItem()), "trade slot " + slot + " holds " + icon);
				continue;
			} else if (index < 0) {
				helper.assertTrue(icon.is(Items.STAINED_GLASS_PANE.gray()), "slot " + slot + " holds " + icon + " instead of glass");
				continue;
			}
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(offers.get(index).targetType());
			helper.assertTrue(SpawnEggItem.spawnsEntity(icon, type), "slot " + slot + " shows " + icon + " for a " + offers.get(index).targetType() + " offer");
		}
		PatrolTestSupport.result(test, "menu=open offers=" + offers.size());
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void choosingAnOfferOnTheBoardAcceptsItThroughTheServer(final GameTestHelper helper) {
		String test = "choosingAnOfferOnTheBoardAcceptsItThroughTheServer";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}
		BlockPos safeSite = prepareSafeTargetSite(helper, test, fixture.head().blockPosition());
		if (safeSite == null) {
			return;
		}

		UseEntityCallback.EVENT.invoker().interact(fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null);
		helper.assertTrue(fixture.owner().containerMenu instanceof MissionBoardMenu, "the board did not open");
		MissionBoardMenu menu = (MissionBoardMenu) fixture.owner().containerMenu;
		MissionService.MissionOffer chosen = menu.offers().get(0);
		menu.clicked(MissionBoardMenu.OFFER_SLOTS[0], 0, ContainerInput.PICKUP, fixture.owner());
		releaseSafeTargetSite(helper, safeSite);
		var missions = missionsFor(helper.getLevel(), fixture.owner().getUUID());
		boolean recorded = CHOICES.getOrDefault(fixture.owner().getUUID(), List.of()).contains(chosen.id() + ":accepted");
		helper.assertTrue(recorded, "the board did not accept the chosen offer: " + CHOICES.get(fixture.owner().getUUID()));
		helper.assertTrue(missions != null && missions.size() == 1 && missions.get(0).targetType().equals(chosen.targetType()), "the accepted mission does not match the chosen offer");
		helper.assertTrue(fixture.owner().containerMenu == fixture.owner().inventoryMenu, "the board stayed open after the choice");
		PatrolTestSupport.result(test, "accepted=" + chosen.targetType() + " closed=true");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aChoiceFromTooFarAwayIsRefusedAndSaysWhy(final GameTestHelper helper) {
		String test = "aChoiceFromTooFarAwayIsRefusedAndSaysWhy";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}

		UseEntityCallback.EVENT.invoker().interact(fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null);
		helper.assertTrue(fixture.owner().containerMenu instanceof MissionBoardMenu, "the board did not open");
		MissionBoardMenu menu = (MissionBoardMenu) fixture.owner().containerMenu;
		MissionService.MissionOffer chosen = menu.offers().get(1);
		movePlayer(fixture.owner(), fixture.head().position().add(MissionService.MISSION_MENU_RANGE + 4.0, 0.0, 0.0));
		menu.clicked(MissionBoardMenu.OFFER_SLOTS[1], 0, ContainerInput.PICKUP, fixture.owner());
		var missions = missionsFor(helper.getLevel(), fixture.owner().getUUID());
		List<String> choices = CHOICES.getOrDefault(fixture.owner().getUUID(), List.of());
		helper.assertTrue(choices.contains(chosen.id() + ":head_missing_or_out_of_range"), "the refusal did not name the range: " + choices);
		helper.assertTrue(missions == null || missions.isEmpty(), "a choice from too far away made a mission");
		PatrolTestSupport.result(test, "refused=head_missing_or_out_of_range");
		helper.succeed();
	}

	@GameTest(dimension = "minecraft:the_nether", maxTicks = TEST_MAX_TICKS, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theBoardNeverGivesUpAnItem(final GameTestHelper helper) {
		String test = "theBoardNeverGivesUpAnItem";
		MissionFixture fixture = fixture(helper, test);
		if (fixture == null) {
			return;
		}

		UseEntityCallback.EVENT.invoker().interact(fixture.owner(), helper.getLevel(), InteractionHand.MAIN_HAND, fixture.head(), null);
		helper.assertTrue(fixture.owner().containerMenu instanceof MissionBoardMenu, "the board did not open");
		MissionBoardMenu menu = (MissionBoardMenu) fixture.owner().containerMenu;
		ItemStack before = menu.getContainer().getItem(0).copy();
		menu.clicked(0, 0, ContainerInput.PICKUP, fixture.owner());
		menu.clicked(0, 0, ContainerInput.QUICK_MOVE, fixture.owner());
		menu.clicked(MissionBoardMenu.OFFER_SLOTS[2], 1, ContainerInput.THROW, fixture.owner());
		menu.clicked(MissionBoardMenu.OFFER_SLOTS[2], 0, ContainerInput.SWAP, fixture.owner());
		boolean carried = !menu.getCarried().isEmpty();
		boolean leaked = fixture.owner().getInventory().contains(stack -> stack.is(Items.STAINED_GLASS_PANE.gray()) || stack.getItem() instanceof SpawnEggItem);
		helper.assertTrue(!carried && !leaked, "the board gave up an item: carried=" + carried + " inventory=" + leaked);
		helper.assertTrue(ItemStack.matches(before, menu.getContainer().getItem(0)) && CHOICES.getOrDefault(fixture.owner().getUUID(), List.of()).isEmpty(), "a glass or throw click changed the board or chose an offer");
		PatrolTestSupport.result(test, "carried=false leaked=false choices=0");
		helper.succeed();
	}

	private static void movePlayer(final ServerPlayer player, final Vec3 position) {
		player.connection.teleport(position.x, position.y, position.z, 0.0F, 0.0F);
		if (player.level() instanceof ServerLevel level) {
			level.getChunkSource().move(player);
		}
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void nemesisMapUsesTheFilledMapModel(final GameTestHelper helper) {
		com.google.gson.JsonObject definition;
		try (var in = MissionService.class.getResourceAsStream("/assets/mc-stella-villages/items/nemesis_map.json")) {
			helper.assertTrue(in != null, "the Nemesis map ships no item model, so it renders as the missing texture");
			definition = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException error) {
			throw new IllegalStateException(error);
		}
		var model = definition.getAsJsonObject("model");
		helper.assertTrue(model != null && "minecraft:model".equals(model.get("type").getAsString())
			&& "minecraft:item/filled_map".equals(model.get("model").getAsString()), "the Nemesis map does not use the filled map model: " + definition);
		String name = new ItemStack(nemesisMapItem()).getHoverName().getString();
		helper.assertTrue(!name.startsWith("item."), "the Nemesis map shows a raw key for its name: " + name);
		PatrolTestSupport.result("nemesisMapUsesTheFilledMapModel", "model=" + model.get("model").getAsString() + " name=" + name);
		helper.succeed();
	}

	private static @Nullable MissionFixture fixture(final GameTestHelper helper, final String test) {
		ServerLevel level = helper.getLevel();
		BlockPos anchor = PatrolTestSupport.findDistantVillageAnchor(helper, test);
		if (anchor == null) {
			return null;
		}
		int villageChunkX = anchor.getX() >> 4;
		int villageChunkZ = anchor.getZ() >> 4;
		level.setChunkForced(villageChunkX, villageChunkZ, true);
		level.getChunkAt(anchor);
		helper.setBlock(4, 0, 4, Blocks.STONE);
		Mob head = helper.spawn(EntityTypes.VINDICATOR, 4, 1, 4);
		head.setNoAi(true);
		head.setPersistenceRequired();
		BlockPos headPosition = anchor;
		head.snapTo(Vec3.atBottomCenterOf(headPosition), 0.0F, 0.0F);
		var data = com.stella.villages.data.VillageWorldData.get(level);
		var village = data.findOrCreate(level.dimension(), anchor);
		// The search is random now. These cases build their ground for the old fixed order, so it is steered to it.
		MissionService.setTargetCandidateSource(village.anchor(), (bell, attempt, random) -> ringCandidate(bell, attempt));
		com.stella.villages.patrol.PatrolState.attach(
			head, village.id(), com.stella.villages.patrol.PatrolState.Role.HEAD
		);
		data.addMember(village, head.getUUID(), com.stella.villages.patrol.PatrolState.Role.HEAD.name(), headPosition);
		ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(head, level);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		movePlayer(owner, Vec3.atBottomCenterOf(headPosition.offset(1, 0, 0)));
		return new MissionFixture(owner, head);
	}

	private static @Nullable BlockPos prepareSafeTargetSite(
		final GameTestHelper helper,
		final String test,
		final BlockPos origin
	) {
		int minimum = missionMinDistance();
		if (minimum < 0) {
			PatrolTestSupport.result(test, "minimumDistance=MISSING");
			helper.fail("mission minimum distance constant is missing");
			return null;
		}
		BlockPos target = origin.offset(minimum, 0, 0);
		int chunkX = target.getX() >> 4;
		int chunkZ = target.getZ() >> 4;
		helper.getLevel().setChunkForced(chunkX, chunkZ, true);
		helper.getLevel().getChunkAt(target);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos floor = target.offset(dx, -1, dz);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int dy = 0; dy < 3; dy++) {
					helper.getLevel().setBlockAndUpdate(floor.above(dy + 1), Blocks.AIR.defaultBlockState());
				}
			}
		}
		return target;
	}

	private static @Nullable BlockPos prepareSafeTargetSiteAwayFromPlayers(
		final GameTestHelper helper,
		final String test,
		final BlockPos origin,
		final ServerPlayer owner
	) {
		List<BlockPos> candidates = targetCandidates(origin);
		if (candidates == null || candidates.isEmpty()) {
			PatrolTestSupport.result(test, "isolatedCandidates=MISSING");
			helper.fail("mission target candidates are missing");
			return null;
		}
		ServerLevel level = helper.getLevel();
		int viewDistance = level.getServer().getPlayerList().getViewDistance();
		int exclusionChunks = viewDistance + 2;
		List<ServerPlayer> observers = level.players().stream()
			.filter(player -> !player.getUUID().equals(owner.getUUID())).toList();
		Set<Long> forcedBefore = new HashSet<>(level.getForceLoadedChunks());
		for (int attempt = 0; attempt < candidates.size(); attempt++) {
			BlockPos candidate = candidates.get(attempt);
			int chunkX = candidate.getX() >> 4;
			int chunkZ = candidate.getZ() >> 4;
			long chunkKey = ChunkPos.pack(chunkX, chunkZ);
			if (forcedBefore.contains(chunkKey)) {
				PatrolTestSupport.result(test, "candidateChunkAlreadyForced=" + chunkKey + " attempt=" + attempt);
				helper.fail("an active GameTest already owns a mission candidate chunk");
				return null;
			}
			boolean isolated = observers.stream().allMatch(player -> {
				BlockPos observer = player.blockPosition();
				int observerChunkX = observer.getX() >> 4;
				int observerChunkZ = observer.getZ() >> 4;
				return Math.abs(chunkX - observerChunkX) > exclusionChunks
					|| Math.abs(chunkZ - observerChunkZ) > exclusionChunks;
			});
			level.setChunkForced(chunkX, chunkZ, true);
			try {
				LevelChunk chunk = level.getChunkAt(candidate);
				int supportY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
				if (supportY < level.getMinY() || supportY + 3 >= level.getMaxY()) {
					continue;
				}
				BlockPos floor = new BlockPos(candidate.getX(), supportY, candidate.getZ());
				if (!isolated) {
					level.setBlockAndUpdate(floor, Blocks.POWDER_SNOW.defaultBlockState());
					continue;
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						BlockPos platform = floor.offset(dx, 0, dz);
						level.setBlockAndUpdate(platform, Blocks.STONE.defaultBlockState());
						for (int dy = 1; dy <= 3; dy++) {
							level.setBlockAndUpdate(platform.above(dy), Blocks.AIR.defaultBlockState());
						}
					}
				}
				BlockPos safeSite = floor.above();
				PatrolTestSupport.detail(test, "isolatedTarget=" + safeSite + " attempt=" + attempt
					+ " viewDistanceChunks=" + viewDistance + " otherPlayers=" + observers.size());
				return safeSite;
			} finally {
				level.setChunkForced(chunkX, chunkZ, false);
			}
		}
		PatrolTestSupport.result(test, "isolatedTarget=MISSING viewDistanceChunks=" + viewDistance
			+ " otherPlayers=" + observers.size());
		helper.fail("mission search found no target site outside other player view ranges");
		return null;
	}

	private static void releaseSafeTargetSite(final GameTestHelper helper, final BlockPos target) {
		helper.getLevel().setChunkForced(target.getX() >> 4, target.getZ() >> 4, false);
	}

	private static @Nullable List<?> offersFor(final UUID playerId) {
		try {
			return (List<?>) Class.forName(MISSION_SERVICE).getMethod("offersFor", UUID.class).invoke(null, playerId);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static @Nullable Boolean acceptOffer(
		final ServerPlayer player,
		final UUID headId,
		final UUID offerId
	) {
		try {
			return (Boolean) Class.forName(MISSION_SERVICE)
				.getMethod("acceptOffer", ServerPlayer.class, UUID.class, UUID.class)
				.invoke(null, player, headId, offerId);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static @Nullable Optional<?> findSafeTarget(
		final ServerLevel level,
		final BlockPos origin,
		final Identifier targetType
	) {
		try {
			return (Optional<?>) Class.forName(MISSION_SERVICE)
				.getMethod("findSafeTarget", ServerLevel.class, BlockPos.class, Identifier.class)
				.invoke(null, level, origin, targetType);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static @Nullable UUID firstOfferId(final @Nullable List<?> offers) {
		if (offers == null || offers.isEmpty()) {
			return null;
		}
		try {
			return (UUID) offers.getFirst().getClass().getMethod("id").invoke(offers.getFirst());
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static @Nullable UUID offerIdForTargetType(
		final @Nullable List<?> offers,
		final Identifier targetType
	) {
		if (offers == null) {
			return null;
		}
		for (Object offer : offers) {
			try {
				Identifier actualType = (Identifier) offer.getClass().getMethod("targetType").invoke(offer);
				UUID id = (UUID) offer.getClass().getMethod("id").invoke(offer);
				if (targetType.equals(actualType)) {
					return id;
				}
			} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
				return null;
			}
		}
		return null;
	}

	private static boolean hasOfferId(final @Nullable List<?> offers, final UUID offerId) {
		if (offers == null) {
			return false;
		}
		for (Object offer : offers) {
			try {
				if (offerId.equals(offer.getClass().getMethod("id").invoke(offer))) {
					return true;
				}
			} catch (ReflectiveOperationException | LinkageError error) {
				return false;
			}
		}
		return false;
	}

	private static List<Identifier> targetTypes(final @Nullable List<?> offers) {
		if (offers == null) {
			return List.of();
		}
		List<Identifier> result = new ArrayList<>();
		for (Object offer : offers) {
			try {
				Object value = offer.getClass().getMethod("targetType").invoke(offer);
				if (value instanceof Identifier id) {
					result.add(id);
				}
			} catch (ReflectiveOperationException | LinkageError error) {
				return List.of();
			}
		}
		return List.copyOf(result);
	}

	private boolean isNonBossHostile(final Identifier id) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id).map(holder -> holder.value()).orElse(null);
		if (type == null || type.getCategory() != MobCategory.MONSTER
			|| id.equals(Identifier.parse("minecraft:ender_dragon"))
			|| id.equals(Identifier.parse("minecraft:wither"))) {
			return false;
		}
		return true;
	}

	private static @Nullable List<com.stella.villages.data.VillageWorldData.MissionRecord> missionsFor(
		final ServerLevel level,
		final UUID ownerId
	) {
		return com.stella.villages.data.VillageWorldData.get(level).missions().stream()
			.filter(mission -> mission.ownerId().equals(ownerId)).toList();
	}

	private static @Nullable Optional<?> missionMapId(final Object mission) {
		try {
			return (Optional<?>) mission.getClass().getMethod("mapId").invoke(mission);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static int missionMinDistance() {
		try {
			return Class.forName(MISSION_SERVICE).getField("MISSION_MIN_DISTANCE").getInt(null);
		} catch (ReflectiveOperationException | LinkageError error) {
			return -1;
		}
	}

	private static int missionMaxDistance() {
		try {
			return Class.forName(MISSION_SERVICE).getField("MISSION_MAX_DISTANCE").getInt(null);
		} catch (ReflectiveOperationException | LinkageError error) {
			return -1;
		}
	}

	private static @Nullable Item nemesisMapItem() {
		try {
			return (Item) Class.forName(NEMESIS_MAP_ITEM).getField("ITEM").get(null);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static ItemStack findMap(final Inventory inventory, final Item mapItem) {
		return inventory.getNonEquipmentItems().stream().filter(stack -> stack.is(mapItem)).findFirst()
			.orElse(ItemStack.EMPTY);
	}

	private static @Nullable List<BlockPos> targetCandidates(final BlockPos origin) {
		try {
			int limit = Class.forName(MISSION_SERVICE).getField("MISSION_TARGET_SEARCH_LIMIT").getInt(null);
			List<BlockPos> candidates = new ArrayList<>(limit);
			for (int attempt = 0; attempt < limit; attempt++) {
				candidates.add(ringCandidate(origin, attempt));
			}
			return List.copyOf(candidates);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	/** The fixed search order the target search had before it went random: eight bearings on eight rings. */
	private static BlockPos ringCandidate(final BlockPos origin, final int attempt) {
		int direction = attempt % 8;
		int ring = attempt / 8;
		int distance = missionMinDistance() + ring * (missionMaxDistance() - missionMinDistance()) / 7;
		int[] dx = {1, 1, 0, -1, -1, -1, 0, 1};
		int[] dz = {0, 1, 1, 1, 0, -1, -1, -1};
		double normalizer = direction % 2 == 0 ? 1.0 : Math.sqrt(2.0);
		return new BlockPos(origin.getX() + net.minecraft.util.Mth.floor(dx[direction] * distance / normalizer), origin.getY(),
			origin.getZ() + net.minecraft.util.Mth.floor(dz[direction] * distance / normalizer));
	}

	private static boolean prepareUnsafeTargetSites(
		final GameTestHelper helper,
		final List<BlockPos> candidates
	) {
		ServerLevel level = helper.getLevel();
		Set<Long> forcedBefore = new HashSet<>(level.getForceLoadedChunks());
		for (BlockPos candidate : candidates) {
			int chunkX = candidate.getX() >> 4;
			int chunkZ = candidate.getZ() >> 4;
			long chunkKey = ChunkPos.pack(chunkX, chunkZ);
			boolean wasForced = forcedBefore.contains(chunkKey);
			if (!wasForced) {
				level.setChunkForced(chunkX, chunkZ, true);
			}
			try {
				LevelChunk chunk = level.getChunkAt(candidate);
				int supportY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
				if (supportY >= level.getMinY() && supportY + 3 < level.getMaxY()) {
					level.setBlockAndUpdate(
						new BlockPos(candidate.getX(), supportY, candidate.getZ()),
						Blocks.POWDER_SNOW.defaultBlockState()
					);
				}
			} finally {
				if (!wasForced) {
					level.setChunkForced(chunkX, chunkZ, false);
				}
			}
		}
		return true;
	}

	private static boolean prepareSpiderNarrowClearanceSites(
		final GameTestHelper helper,
		final List<BlockPos> candidates
	) {
		ServerLevel level = helper.getLevel();
		Set<Long> forcedBefore = new HashSet<>(level.getForceLoadedChunks());
		for (BlockPos candidate : candidates) {
			int chunkX = candidate.getX() >> 4;
			int chunkZ = candidate.getZ() >> 4;
			long chunkKey = ChunkPos.pack(chunkX, chunkZ);
			boolean wasForced = forcedBefore.contains(chunkKey);
			if (!wasForced) {
				level.setChunkForced(chunkX, chunkZ, true);
			}
			try {
				LevelChunk chunk = level.getChunkAt(candidate);
				int supportY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
				if (supportY < level.getMinY() || supportY + 3 >= level.getMaxY()) {
					return false;
				}
				BlockPos floor = new BlockPos(candidate.getX(), supportY, candidate.getZ());
				BlockPos feet = floor.above();
				level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int dy = 0; dy < 3; dy++) {
					level.setBlockAndUpdate(feet.above(dy), Blocks.AIR.defaultBlockState());
					level.setBlockAndUpdate(feet.offset(-1, dy, 0), Blocks.STONE.defaultBlockState());
					level.setBlockAndUpdate(feet.offset(1, dy, 0), Blocks.STONE.defaultBlockState());
				}
			} finally {
				if (!wasForced) {
					level.setChunkForced(chunkX, chunkZ, false);
				}
			}
		}
		return true;
	}

	private static Set<CandidateChunk> loadedCandidateChunks(
		final ServerLevel level,
		final Set<CandidateChunk> candidates
	) {
		Set<CandidateChunk> loaded = new HashSet<>();
		for (CandidateChunk candidate : candidates) {
			if (level.getChunkSource().hasChunk(candidate.x(), candidate.z())) {
				loaded.add(candidate);
			}
		}
		return loaded;
	}

	private static @Nullable BlockPos targetPosition(final ItemStack map) {
		try {
			return (BlockPos) Class.forName(NEMESIS_MAP_ITEM)
				.getMethod("targetPosition", ItemStack.class).invoke(null, map);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static @Nullable Mob loadNemesis(
		final String test,
		final ServerLevel level,
		final UUID missionId,
		final BlockPos targetPosition
	) {
		var mission = com.stella.villages.data.VillageWorldData.get(level).mission(missionId).orElse(null);
		UUID entityId = mission == null ? null : mission.entityId().orElse(null);
		if (entityId == null) {
			PatrolTestSupport.detail(test, "nemesisLookup mission=" + missionId + " savedEntityId=missing");
			return null;
		}
		Entity entity = level.getEntityInAnyDimension(entityId);
		if (entity instanceof Mob mob && mob.isAlive()) {
			PatrolTestSupport.detail(test, "nemesisLookup mission=" + missionId + " entity=" + entityId
				+ " target=" + targetPosition + " loaded=true");
			return mob;
		}
		return null;
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
		var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
			net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false));
		return player;
	}

	private static double baseHealth(final ServerLevel level, final Identifier typeId) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId).map(holder -> holder.value()).orElse(null);
		Entity entity = type.create(level, EntitySpawnReason.COMMAND);
		return entity instanceof Mob mob && mob.getAttribute(Attributes.MAX_HEALTH) != null
			? mob.getMaxHealth()
			: -1.0;
	}

	private static boolean hasNemesisKit(final Mob nemesis) {
		return java.util.Arrays.stream(new EquipmentSlot[] {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND
		}).allMatch(slot -> {
			ItemStack stack = nemesis.getItemBySlot(slot);
			var enchantments = stack.get(DataComponents.ENCHANTMENTS);
			return !stack.isEmpty() && enchantments != null && enchantments.size() == 3;
		});
	}

	private static boolean isEnraged(final Mob nemesis) {
		try {
			Object state = Class.forName(NEMESIS_STATE).getMethod("get", Mob.class).invoke(null, nemesis);
			return state != null && (Boolean) state.getClass().getMethod("enraged").invoke(state);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return false;
		}
	}

	private static @Nullable UUID nemesisMissionId(final Mob nemesis) {
		try {
			Object state = Class.forName(NEMESIS_STATE).getMethod("get", Mob.class).invoke(null, nemesis);
			return state == null ? null : (UUID) state.getClass().getMethod("missionId").invoke(state);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
			return null;
		}
	}

	private static ItemStack[] assignedGear(final Mob nemesis) {
		return new ItemStack[] {
			nemesis.getItemBySlot(EquipmentSlot.HEAD).copy(),
			nemesis.getItemBySlot(EquipmentSlot.CHEST).copy(),
			nemesis.getItemBySlot(EquipmentSlot.LEGS).copy(),
			nemesis.getItemBySlot(EquipmentSlot.FEET).copy(),
			nemesis.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
			nemesis.getItemBySlot(EquipmentSlot.OFFHAND).copy()
		};
	}

	private static int enchantedBookCount(final Inventory inventory) {
		int books = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(Items.ENCHANTED_BOOK) && stack.get(DataComponents.STORED_ENCHANTMENTS) != null
				&& !stack.get(DataComponents.STORED_ENCHANTMENTS).isEmpty()) {
				books += stack.getCount();
			}
		}
		return books;
	}

	private static int droppedCount(final ServerLevel level, final BlockPos position, final Item item) {
		AABB bounds = AABB.ofSize(Vec3.atCenterOf(position), 16.0, 16.0, 16.0);
		return level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.getItem().is(item))
			.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
	}

	/** Whether a count sits within five standard deviations of a binomial mean, so a fixed seed never flakes. */
	private static boolean withinFiveSigma(final int count, final int trials, final double chance) {
		double mean = trials * chance;
		return Math.abs(count - mean) <= 5.0 * Math.sqrt(trials * chance * (1.0 - chance)) + 1.0;
	}

	/** Enchanted books, and spell books when the spells mod is loaded: the one book a Nemesis pays. */
	private static int droppedBookCount(final ServerLevel level, final BlockPos position) {
		AABB bounds = AABB.ofSize(Vec3.atCenterOf(position), 16.0, 16.0, 16.0);
		return level.getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.getItem().is(Items.ENCHANTED_BOOK)
			|| BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).getNamespace().equals("mod-stella-spells"))
			.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
	}

	private static int assignedGearDropCount(
		final ServerLevel level,
		final BlockPos position,
		final ItemStack[] gear
	) {
		return java.util.Arrays.stream(gear).filter(stack -> !stack.isEmpty())
			.mapToInt(stack -> droppedCount(level, position, stack.getItem())).sum();
	}

	private static boolean missionRemoved(final ServerLevel level, final UUID missionId) {
		return com.stella.villages.data.VillageWorldData.get(level).mission(missionId).isEmpty();
	}

	private record CandidateChunk(int x, int z) {
	}

	private record MissionFixture(ServerPlayer owner, Mob head) {
	}
}
