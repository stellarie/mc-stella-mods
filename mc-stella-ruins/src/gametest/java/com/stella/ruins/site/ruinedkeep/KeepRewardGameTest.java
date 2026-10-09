package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.reward.RuinAffixes;
import com.stella.ruins.reward.RuinUniques;
import com.stella.ruins.world.Claim;
import com.stella.ruins.world.Exclusives;
import com.stella.ruins.world.RuinSite;
import com.stella.ruins.world.SiteSize;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.loot.LootTable;

/** Acceptance items 9 and 10, the registration, and what the keep's rewards are made of. */
public class KeepRewardGameTest {
	private static ItemStack smith(final ServerPlayer player, final ItemStack base) {
		SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(KeepRewards.sigil.template()));
		menu.getSlot(SmithingMenu.BASE_SLOT).set(base);
		menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(KeepRewards.tarnishedCrest));
		return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
	}

	private static double modifier(final ItemStack stack, final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, final EquipmentSlotGroup group) {
		double sum = 0.0;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(attribute) && entry.slot() == group && entry.modifier().id().getPath().startsWith("affix/keep_sigil/")) {
				sum += entry.modifier().amount();
			}
		}
		return sum;
	}

	@GameTest(maxTicks = 100)
	public void theKeepSigilGoesOnArmorOnlyAndGivesTwoArmorAndOneToughnessWhenWorn(final GameTestHelper helper) {
		ServerPlayer player = KeepTestSupport.joinPlayer(helper, 1.5, 1.5);
		ItemStack plain = new ItemStack(Items.IRON_CHESTPLATE);
		ItemStack result = smith(player, plain);
		helper.assertTrue(!result.isEmpty(), "the smithing table gave no result for a chestplate");
		helper.assertTrue(KeepRewards.SIGIL_AFFIX.equals(RuinAffixes.affixOf(result)), "the affix component is " + RuinAffixes.affixOf(result));
		helper.assertTrue(modifier(result, Attributes.ARMOR, EquipmentSlotGroup.CHEST) == 2.0, "the armor bonus is " + modifier(result, Attributes.ARMOR, EquipmentSlotGroup.CHEST));
		helper.assertTrue(modifier(result, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST) == 1.0, "the toughness bonus is " + modifier(result, Attributes.ARMOR_TOUGHNESS, EquipmentSlotGroup.CHEST));
		helper.assertTrue(smith(player, new ItemStack(Items.IRON_SWORD)).isEmpty(), "the sigil went on a sword");
		helper.assertTrue(!smith(player, new ItemStack(Items.IRON_HELMET)).isEmpty(), "the sigil refused a helmet");
		helper.assertTrue(smith(player, result).isEmpty(), "the sigil went on twice");
		player.setItemSlot(EquipmentSlot.CHEST, plain.copy());
		helper.startSequence()
			.thenExecuteAfter(3, () -> player.setItemSlot(EquipmentSlot.CHEST, result))
			.thenExecuteAfter(3, () -> {
				double armor = player.getAttributeValue(Attributes.ARMOR);
				double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
				ItemStack bare = plain.copy();
				double baseArmor = bare.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
					.filter(e -> e.attribute().equals(Attributes.ARMOR)).mapToDouble(e -> e.modifier().amount()).sum();
				double baseToughness = bare.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
					.filter(e -> e.attribute().equals(Attributes.ARMOR_TOUGHNESS)).mapToDouble(e -> e.modifier().amount()).sum();
				KeepTestSupport.result("keepSigil", "armor=" + armor + " toughness=" + toughness + " ironArmor=" + baseArmor + " ironToughness=" + baseToughness);
				helper.assertTrue(armor == baseArmor + 2.0, "the wearer has armor " + armor + ", iron gives " + baseArmor + " and the sigil adds 2");
				helper.assertTrue(toughness == baseToughness + 1.0, "the wearer has toughness " + toughness + ", iron gives " + baseToughness + " and the sigil adds 1");
				KeepTestSupport.leave(helper, player);
			})
			.thenSucceed();
	}

	@GameTest
	public void castellansOathHasNetheriteStatsAndStrikesTwiceBelowThirtyPercentHealth(final GameTestHelper helper) {
		ServerPlayer player = KeepTestSupport.joinPlayer(helper, 1.5, 1.5);
		try {
			ItemStack oath = new ItemStack(KeepRewards.castellansOath);
			ItemStack netherite = new ItemStack(Items.NETHERITE_SWORD);
			helper.assertTrue(oath.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).equals(netherite.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)),
				"the oath's attack stats differ from a netherite sword");
			helper.assertTrue(RuinUniques.effectOf(oath).equals(KeepRewards.OATH_EFFECT), "the oath carries effect " + RuinUniques.effectOf(oath));
			ServerLevel level = helper.getLevel();
			player.setItemSlot(EquipmentSlot.MAINHAND, oath);
			Cow low = KeepTestSupport.cow(helper, 3.5, 3.5, 25.0F, 100.0F);
			low.damageCooldownTime = 0;
			low.hurtServer(level, player.damageSources().playerAttack(player), 8.0F);
			float lost = 25.0F - low.getHealth();
			Cow high = KeepTestSupport.cow(helper, 5.5, 3.5, 90.0F, 100.0F);
			high.damageCooldownTime = 0;
			high.hurtServer(level, player.damageSources().playerAttack(player), 8.0F);
			float lostHigh = 90.0F - high.getHealth();
			player.setItemSlot(EquipmentSlot.MAINHAND, netherite);
			Cow control = KeepTestSupport.cow(helper, 7.5, 3.5, 25.0F, 100.0F);
			control.damageCooldownTime = 0;
			control.hurtServer(level, player.damageSources().playerAttack(player), 8.0F);
			float lostControl = 25.0F - control.getHealth();
			KeepTestSupport.result("castellansOath", "lowHealthLost=" + lost + " highHealthLost=" + lostHigh + " plainSwordLost=" + lostControl);
			helper.assertTrue(Math.abs(lost - 10.0F) < 1.0E-3F, "below 30% the hit cost " + lost + ", not 8 plus 25% (10)");
			helper.assertTrue(Math.abs(lostHigh - 8.0F) < 1.0E-3F, "above 30% the hit cost " + lostHigh + ", not 8");
			helper.assertTrue(Math.abs(lostControl - 8.0F) < 1.0E-3F, "a plain sword cost " + lostControl + ", not 8");
		} finally {
			KeepTestSupport.leave(helper, player);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void theBannerOfTheKeepRalliesHolderAndAlliesWhileAHostileIsNear(final GameTestHelper helper) {
		ServerPlayer holder = KeepTestSupport.joinPlayer(helper, 1.5, 1.5);
		ServerPlayer ally = KeepTestSupport.joinPlayer(helper, 5.5, 1.5);
		ServerPlayer distant = KeepTestSupport.joinPlayer(helper, 12.5, 1.5);
		ServerPlayer mainHand = KeepTestSupport.joinPlayer(helper, 1.5, 30.5);
		ServerPlayer lonely = KeepTestSupport.joinPlayer(helper, 60.5, 60.5);
		holder.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(KeepRewards.bannerOfTheKeep));
		mainHand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(KeepRewards.bannerOfTheKeep));
		lonely.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(KeepRewards.bannerOfTheKeep));
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		zombie.addTag(com.stella.ruins.world.RuinMobKind.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.0, 9.5)));
		zombie.setNoAi(true);
		helper.getLevel().addFreshEntity(zombie);
		Mob decoy = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		decoy.addTag(com.stella.ruins.world.RuinMobKind.NO_SPAWN_ROLL_TAG);
		decoy.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.0, 33.5)));
		decoy.setNoAi(true);
		helper.getLevel().addFreshEntity(decoy);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(holder.hasEffect(MobEffects.RESISTANCE), "the holder never gained Resistance"))
			.thenExecute(() -> {
				boolean allyHas = ally.hasEffect(MobEffects.RESISTANCE);
				boolean distantHas = distant.hasEffect(MobEffects.RESISTANCE);
				boolean mainHas = mainHand.hasEffect(MobEffects.RESISTANCE);
				boolean lonelyHas = lonely.hasEffect(MobEffects.RESISTANCE);
				int amp = holder.getEffect(MobEffects.RESISTANCE).getAmplifier();
				int ticks = holder.getEffect(MobEffects.RESISTANCE).getDuration();
				KeepTestSupport.result("banner", "holderAmp=" + amp + " ticks=" + ticks + " ally=" + allyHas + " distant=" + distantHas + " mainHandBanner=" + mainHas + " noHostile=" + lonelyHas);
				helper.assertTrue(amp == 0 && ticks <= KeepRewards.RALLY_TICKS, "the holder's Resistance is level " + amp + " for " + ticks);
				helper.assertTrue(allyHas, "an ally 4 blocks away gained no Resistance");
				helper.assertTrue(!distantHas, "a player 11 blocks away gained Resistance");
				helper.assertTrue(!mainHas, "a banner in the main hand rallied");
				helper.assertTrue(!lonelyHas, "a banner with no hostile near rallied");
				zombie.discard();
				decoy.discard();
				List.of(holder, ally, distant, mainHand, lonely).forEach(p -> KeepTestSupport.leave(helper, p));
			})
			.thenSucceed();
	}

	private static final double HIGH = 240.0;

	@GameTest(maxTicks = 300)
	public void theBannerOfTheKeepAlsoRalliesWhenOnlyARuinFoeIsNear(final GameTestHelper helper) {
		ServerPlayer holder = KeepTestSupport.joinPlayer(helper, 1.5, 1.5);
		// A rally reaches any player within 6 blocks, so this holder stands 240 blocks over the other keep tests.
		holder.teleportTo(helper.getLevel(), holder.getX(), holder.getY() + HIGH, holder.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
		holder.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(KeepRewards.bannerOfTheKeep));
		Mob golem = com.stella.ruins.world.RuinMobKind.get("deep_golem").spawn(helper.getLevel(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.0 + HIGH, 9.5)));
		golem.setNoAi(true);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(holder.hasEffect(MobEffects.RESISTANCE), "a deep golem near the holder did not count as a hostile"))
			.thenExecute(() -> {
				golem.discard();
				KeepTestSupport.leave(helper, holder);
			})
			.thenSucceed();
	}

	@GameTest
	public void everyKeepItemIsExclusiveAndNoKeepRoomChestYieldsOne(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (var item : List.of(KeepRewards.keyFragment, KeepRewards.tarnishedCrest, KeepRewards.spectralThread, KeepRewards.castellansOath, KeepRewards.bannerOfTheKeep, KeepRewards.sigil.template())) {
			helper.assertTrue(Exclusives.isExclusive(item), item + " is not exclusive");
		}
		ResourceKey<LootTable> room = KeepLayout.ROOM_LOOT;
		LootTable table = level.getServer().reloadableRegistries().getLootTable(room);
		Exclusives.Probe probe = Exclusives.probe(level, table, 400);
		KeepTestSupport.result("roomChests", "rolled=" + probe.rolled() + " exclusives=" + probe.exclusives());
		helper.assertTrue(probe.rolled() && probe.exclusives().isEmpty(), "the room chest table yields exclusive items: " + probe);
		helper.assertTrue(Exclusives.ownsTable(room.identifier()) && Exclusives.ownsTable(RuinedKeepSite.VAULT_LOOT.identifier()), "a keep table is outside the mod namespace");
		helper.succeed();
	}

	@GameTest
	public void theStructureSetSpreadsLargeSitesWithTheKeepSaltAndExcludesVillages(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var set = com.stella.ruins.world.RuinSitePlanGameTest.sites(level);
		helper.assertTrue(set != null, "the " + com.stella.ruins.world.RuinSitePlanGameTest.SITES + " structure set did not load");
		com.google.gson.JsonElement json = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC.encodeStart(
			net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, level.registryAccess()), set.placement()).getOrThrow();
		com.google.gson.JsonObject placement = json.getAsJsonObject();
		KeepTestSupport.result("structureSet", placement.toString());
		helper.assertTrue(placement.get("spacing").getAsInt() == 12 && placement.get("separation").getAsInt() == 9, "the spread is " + placement.get("spacing") + "/" + placement.get("separation"));
		helper.assertTrue(placement.get("salt").getAsInt() == 702913845, "the salt is " + placement.get("salt"));
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.excludesVillages(placement), "the village exclusion is " + placement);
		var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(ResourceKey.create(Registries.STRUCTURE, com.stella.ruins.StellaRuins.id("ruined_keep")));
		helper.assertTrue(structure.isPresent() && structure.get().value() instanceof com.stella.ruins.world.RuinStructure ruin && ruin.site().equals(RuinedKeepSite.ID), "the structure does not name the keep");
		helper.assertTrue(com.stella.ruins.world.RuinSitePlanGameTest.theSitesSetHolds(level, RuinedKeepSite.ID), "the one ruin set does not hold the keep");
		helper.succeed();
	}

	@GameTest
	public void theSiteIsRegisteredAsALargeOnceSiteWithItsRadiusBoundsAndUnique(final GameTestHelper helper) {
		RuinSite site = KeepTestSupport.site();
		helper.assertTrue(site != null, "the ruined_keep site is not registered");
		helper.assertTrue(site.size() == SiteSize.LARGE, "the size is " + site.size());
		helper.assertTrue(site.sizeX() == 48 && site.sizeY() == 48 && site.sizeZ() == 48, "the bounds are " + site.sizeX() + "x" + site.sizeY() + "x" + site.sizeZ());
		helper.assertTrue(site.claim().isOnce() && site.claim().resetTicks(site.id()) == -1L, "the claim is not once");
		helper.assertTrue(site.activationRadius() == 32, "the activation radius is " + site.activationRadius());
		helper.assertTrue(site.clueWeight() == 0, "the clue weight is " + site.clueWeight());
		helper.assertTrue(site.placement() instanceof com.stella.ruins.world.Placement.Surface surface && surface.maxSlope() == 6, "the placement is " + site.placement());
		helper.assertTrue(site.placement().foundation() == 6, "the foundation is " + site.placement().foundation());
		helper.assertTrue(site.hasUnique() && site.newUnique().is(KeepRewards.bannerOfTheKeep), "the site's unique is not the banner");
		helper.assertTrue(site.newObjective() instanceof KeepObjective, "the objective is not the keep's");
		helper.assertTrue(site.roster().isEmpty(), "the site spawns a roster at activation");
		helper.assertTrue(Claim.DAY_TICKS == 24000L, "a day is not 24000 ticks");
		helper.succeed();
	}
}
