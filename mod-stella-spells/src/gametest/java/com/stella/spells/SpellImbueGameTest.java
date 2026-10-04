package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A weapon and a spellbook in an anvil make an imbued weapon: the book's own enchantments stay behind,
 * and each melee hit casts the book's spell at reduced strength, then waits out the book's cooldown.
 */
public class SpellImbueGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.imbue");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final Identifier STONEGUARD = Identifier.parse("mod-stella-spells:stoneguard");
	private static final Identifier LIFE_BLOOM = Identifier.parse("mod-stella-spells:life_bloom");
	private static final Identifier DRAGONFALL = Identifier.parse("mod-stella-spells:dragonfall");
	private static final Identifier RIFTWALK = Identifier.parse("mod-stella-spells:riftwalk");
	private static final float HIT = 4.0F;
	private static final Map<UUID, List<Identifier>> CASTS = new ConcurrentHashMap<>();

	static {
		SpellImbue.setRecorder((player, spell, victim) -> CASTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell));
	}

	@GameTest
	public void anAnvilImbuesAWeaponAndLeavesTheBooksEnchantmentsBehind(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 2);
		ItemStack book = book(SPARK_SHOT);
		book.enchant(enchantment(helper, ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("mod-stella-spells:quickening"))), 1);
		AnvilMenu anvil = anvil(helper, player, sword, book);
		ItemStack result = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		SpellImbue.ImbuedSpell imbued = result.get(SpellImbue.IMBUED);
		LOG.info("[SPELLTEST] test=anAnvilImbuesAWeaponAndLeavesTheBooksEnchantmentsBehind RESULT imbued={} enchantments={} cost={}", imbued, EnchantmentHelper.getEnchantmentsForCrafting(result), anvil.getCost());
		helper.assertTrue(result.is(Items.IRON_SWORD) && imbued != null && imbued.spell().equals(SPARK_SHOT), "the anvil made " + result + " imbued with " + imbued);
		helper.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(result).equals(EnchantmentHelper.getEnchantmentsForCrafting(sword)), "the imbued sword's enchantments changed: " + EnchantmentHelper.getEnchantmentsForCrafting(result));
		helper.assertTrue(anvil.getCost() == SpellImbue.IMBUE_XP_COST, "the imbue costs " + anvil.getCost());
		helper.succeed();
	}

	@GameTest
	public void takingTheImbuedWeaponUsesUpTheBookAndItsPrice(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.setExperienceLevels(SpellImbue.IMBUE_XP_COST + 3);
		boolean emeraldAnvil = FabricLoader.getInstance().isModLoaded("mod-stella-equips-enchants");
		if (emeraldAnvil) {
			player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		}
		AnvilMenu anvil = anvil(helper, player, new ItemStack(Items.IRON_AXE), book(STONEGUARD));
		var resultSlot = anvil.getSlot(AnvilMenu.RESULT_SLOT);
		helper.assertTrue(resultSlot.mayPickup(player), "the imbued axe cannot be taken: result=" + resultSlot.getItem() + " cost=" + anvil.getCost());
		int price = anvil.getCost();
		int emeraldsBefore = emeralds(player);
		ItemStack taken = resultSlot.getItem().copy();
		resultSlot.onTake(player, taken);
		LOG.info("[SPELLTEST] test=takingTheImbuedWeaponUsesUpTheBookAndItsPrice RESULT book={} levels={} emeraldsSpent={} price={}",
			anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem(), player.experienceLevel, emeraldsBefore - emeralds(player), price);
		helper.assertTrue(taken.get(SpellImbue.IMBUED) != null, "the taken axe is not imbued");
		helper.assertTrue(anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "the spellbook was not used up");
		helper.assertTrue(player.experienceLevel == (emeraldAnvil ? SpellImbue.IMBUE_XP_COST + 3 : 3),
			"the imbue left the player at " + player.experienceLevel + " levels");
		if (emeraldAnvil) {
			helper.assertTrue(emeraldsBefore - emeralds(player) == price,
				"the imbue took " + (emeraldsBefore - emeralds(player)) + " emeralds, not " + price);
		}
		helper.succeed();
	}

	@GameTest
	public void onlyAWeaponTakesASpell(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		AnvilMenu anvil = anvil(helper, player, new ItemStack(Items.STICK), book(SPARK_SHOT));
		ItemStack result = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		LOG.info("[SPELLTEST] test=onlyAWeaponTakesASpell RESULT result={}", result);
		helper.assertTrue(result.isEmpty() || result.get(SpellImbue.IMBUED) == null, "a stick took a spell");
		helper.succeed();
	}

	@GameTest
	public void theImbuedWeaponNamesItsSpellInTheTooltip(final GameTestHelper helper) {
		ItemStack sword = imbued(Items.IRON_SWORD, SPARK_SHOT);
		List<Component> lines = sword.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL);
		List<Component> own = new ArrayList<>();
		sword.get(SpellImbue.IMBUED).addToTooltip(Item.TooltipContext.of(helper.getLevel()), own::add, TooltipFlag.NORMAL, sword);
		LOG.info("[SPELLTEST] test=theImbuedWeaponNamesItsSpellInTheTooltip RESULT own={} shown={}", own.size(), lines.containsAll(own));
		helper.assertTrue(!own.isEmpty() && lines.containsAll(own), "the tooltip does not show the imbued spell: " + lines);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedSparkShotBurnsAndHurtsAtReducedStrength(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		float taken = zombie.getMaxHealth() - zombie.getHealth();
		float expected = HIT + 3.0F * (float) SpellImbue.IMBUE_POWER;
		LOG.info("[SPELLTEST] test=anImbuedSparkShotBurnsAndHurtsAtReducedStrength RESULT taken={} expected={} burning={}", taken, expected, zombie.isOnFire());
		helper.assertTrue(Math.abs(taken - expected) < 0.001F, "the imbued hit took " + taken + ", not " + expected);
		helper.assertTrue(zombie.isOnFire(), "the imbued hit set no fire");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedSelfSpellActsOnTheWielderForAShorterTime(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, LIFE_BLOOM);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		MobEffectInstance resistance = player.getEffect(MobEffects.REGENERATION);
		int expected = (int) Math.round(300 * SpellImbue.IMBUE_DURATION);
		LOG.info("[SPELLTEST] test=anImbuedSelfSpellActsOnTheWielderForAShorterTime RESULT duration={} expected={}", resistance == null ? -1 : resistance.getDuration(), expected);
		helper.assertTrue(resistance != null && resistance.getDuration() == expected, "the wielder got " + resistance);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedAreaSpellLandsAroundTheTarget(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, DRAGONFALL);
		Mob struck = zombie(helper, 2, 4);
		Mob beside = zombie(helper, 4, 4);
		struck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		float besideTaken = beside.getMaxHealth() - beside.getHealth();
		float expected = 10.0F * (float) SpellImbue.IMBUE_POWER;
		LOG.info("[SPELLTEST] test=anImbuedAreaSpellLandsAroundTheTarget RESULT besideTaken={} expected={}", besideTaken, expected);
		helper.assertTrue(Math.abs(besideTaken - expected) < 0.001F, "a hostile beside the target took " + besideTaken + ", not " + expected);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anImbuedRiftwalkStepsBehindTheTarget(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, RIFTWALK);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		Vec3 local = helper.relativeVec(player.position());
		LOG.info("[SPELLTEST] test=anImbuedRiftwalkStepsBehindTheTarget RESULT landed={} target={}", local, helper.relativeVec(zombie.position()));
		helper.assertTrue(local.z > helper.relativeVec(zombie.position()).z && Math.abs(local.y - 1.0) < 0.01, "the wielder landed at " + local);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theImbuedSpellWaitsOutTheBooksCooldown(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		int cooldown = SpellbookCatalog.get(SPARK_SHOT).baseCooldownTicks();
		hit(helper, player, zombie);
		hit(helper, player, zombie);
		int early = CASTS.getOrDefault(player.getUUID(), List.of()).size();
		helper.runAfterDelay(cooldown + 1L, () -> {
			hit(helper, player, zombie);
			int late = CASTS.getOrDefault(player.getUUID(), List.of()).size();
			LOG.info("[SPELLTEST] test=theImbuedSpellWaitsOutTheBooksCooldown RESULT early={} late={} cooldown={}", early, late, cooldown);
			helper.assertTrue(early == 1, "two hits inside the cooldown cast " + early + " times");
			helper.assertTrue(late == 2, "a hit after the cooldown did not cast again: " + late);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void everyNewSpellCastsFromAnImbuedHit(final GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		int newSpells = 0;
		for (SpellDefinition definition : SpellbookCatalog.DEFINITIONS) {
			if (!isSecondCatalogue(definition)) {
				continue;
			}
			newSpells++;
			ServerPlayer player = wielder(helper, definition.id());
			// Purify needs something to clear, and Sanctuary a wound to close.
			player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
			player.setHealth(10.0F);
			Mob zombie = zombie(helper, 2, 4);
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
			if (!CASTS.getOrDefault(player.getUUID(), List.of()).contains(definition.id())) {
				missing.add(definition.id().getPath());
			}
			zombie.discard();
		}
		LOG.info("[SPELLTEST] test=everyNewSpellCastsFromAnImbuedHit RESULT spells={} missing={}", newSpells, missing);
		helper.assertTrue(newSpells == SpellbookCatalog.DEFINITIONS.size() - 20, "the second catalogue has " + newSpells + " spells, so the case proves the wrong set");
		helper.assertTrue(missing.isEmpty(), "imbued hits that cast nothing: " + missing);
		helper.succeed();
	}

	private static boolean isSecondCatalogue(final SpellDefinition definition) {
		return SpellbookCatalog.DEFINITIONS.indexOf(definition) >= 20;
	}

	@GameTest
	public void anArrowDoesNotCastTheSpellOfTheSwordInHand(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), null);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, player), HIT);
		int casts = CASTS.getOrDefault(player.getUUID(), List.of()).size();
		LOG.info("[SPELLTEST] test=anArrowDoesNotCastTheSpellOfTheSwordInHand RESULT casts={}", casts);
		helper.assertTrue(casts == 0 && !zombie.isOnFire(), "an arrow cast the sword's spell");
		helper.succeed();
	}

	// The cloud lands on a victim within reach, so it must never poison the wielder who stands in it.
	@GameTest(maxTicks = 100)
	public void anImbuedPlagueCloudSparesItsWielder(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, Identifier.parse("mod-stella-spells:plague_cloud"));
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		helper.runAfterDelay(30L, () -> {
			boolean cast = CASTS.getOrDefault(player.getUUID(), List.of()).contains(Identifier.parse("mod-stella-spells:plague_cloud"));
			boolean poisoned = player.hasEffect(MobEffects.POISON);
			LOG.info("[SPELLTEST] test=anImbuedPlagueCloudSparesItsWielder RESULT cast={} wielderPoisoned={} distance={}", cast, poisoned, player.distanceTo(zombie));
			helper.assertTrue(cast, "the imbued hit left no cloud, so the case proves nothing");
			helper.assertFalse(poisoned, "the wielder's own imbued Plague Cloud poisoned them");
			helper.succeed();
		});
	}

	// A spell that finds nothing to do must leave the hit's own hurt cooldown on the victim.
	@GameTest
	public void aFailedImbuedSpellKeepsTheVictimsHurtCooldown(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, Identifier.parse("mod-stella-spells:purify"));
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		int cooldown = zombie.damageCooldownTime;
		LOG.info("[SPELLTEST] test=aFailedImbuedSpellKeepsTheVictimsHurtCooldown RESULT cooldown={} casts={}", cooldown, CASTS.getOrDefault(player.getUUID(), List.of()).size());
		helper.assertTrue(cooldown > 0, "a Purify with nothing to clear wiped the victim's hurt cooldown");
		helper.succeed();
	}

	// A spell that deals no damage must leave the hit's own hurt cooldown on the victim too.
	@GameTest
	public void aHarmlessImbuedSpellKeepsTheVictimsHurtCooldown(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, Identifier.parse("mod-stella-spells:sapping_hex"));
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		int cooldown = zombie.damageCooldownTime;
		boolean hexed = zombie.hasEffect(MobEffects.WEAKNESS);
		LOG.info("[SPELLTEST] test=aHarmlessImbuedSpellKeepsTheVictimsHurtCooldown RESULT cooldown={} hexed={}", cooldown, hexed);
		helper.assertTrue(hexed, "the imbued Sapping Hex did not land, so the case proves nothing");
		helper.assertTrue(cooldown > 0, "an imbued Sapping Hex wiped the victim's hurt cooldown");
		helper.succeed();
	}

	// Its own batch, because it clears every imbue cooldown on the server.
	@GameTest(environment = "mod-stella-spells:storm_alone")
	public void stoppingTheServerForgetsImbueCooldowns(final GameTestHelper helper) {
		ServerPlayer player = wielder(helper, SPARK_SHOT);
		Mob zombie = zombie(helper, 2, 4);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
		int before = SpellImbue.readyEntries();
		SpellImbue.onServerStopping(helper.getLevel().getServer());
		int after = SpellImbue.readyEntries();
		LOG.info("[SPELLTEST] test=stoppingTheServerForgetsImbueCooldowns RESULT before={} after={}", before, after);
		helper.assertTrue(before > 0, "the hit set no cooldown, so the case proves nothing");
		helper.assertTrue(after == 0, after + " imbue cooldowns outlived the server");
		helper.succeed();
	}

	@GameTest
	public void tideLungsCannotBeImbued(final GameTestHelper helper) {
		ServerPlayer player = player(helper);
		AnvilMenu anvil = anvil(helper, player, new ItemStack(Items.IRON_SWORD), book(Identifier.parse("mod-stella-spells:tide_lungs")));
		ItemStack result = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		LOG.info("[SPELLTEST] test=tideLungsCannotBeImbued RESULT result={}", result);
		helper.assertTrue(result.isEmpty() || result.get(SpellImbue.IMBUED) == null, "Tide Lungs was imbued into " + result);
		helper.succeed();
	}

	private static void hit(final GameTestHelper helper, final ServerPlayer player, final Mob zombie) {
		zombie.damageCooldownTime = 0;
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), HIT);
	}

	private static AnvilMenu anvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final ItemStack right) {
		AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		anvil.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		anvil.createResult();
		return anvil;
	}

	private static ItemStack book(final Identifier spell) {
		return BuiltInRegistries.ITEM.getValue(spell).getDefaultInstance();
	}

	private static ItemStack imbued(final Item weapon, final Identifier spell) {
		ItemStack stack = new ItemStack(weapon);
		stack.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(spell));
		return stack;
	}

	private static Holder<Enchantment> enchantment(final GameTestHelper helper, final ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static int emeralds(final ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static ServerPlayer player(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final Identifier spell) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 5; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerPlayer player = player(helper);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		player.setYRot(0.0F);
		player.setXRot(0.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, imbued(Items.IRON_SWORD, spell));
		return player;
	}

	// A durable zombie with no armour, so each hit reads as exactly its damage.
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		mob.setHealth(100.0F);
		return mob;
	}
}
