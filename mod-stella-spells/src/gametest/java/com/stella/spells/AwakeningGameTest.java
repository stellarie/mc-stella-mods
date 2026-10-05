package com.stella.spells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spellbook awakening: a book counts the casts that hit something and awakens at 120. Each test drives the
 * real cast path, the real anvil, or the real command, and reads the stack or the probe the cast reached.
 */
public class AwakeningGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.AwakeningGameTest");
	private static final Identifier SPARK_SHOT = id("spark_shot");
	private static final Identifier FROSTBIND = id("frostbind");
	private static final Identifier FROST_NOVA = id("frost_nova");
	private static final Identifier TIME_LOCK = id("time_lock");
	private static final Identifier BERSERK = id("berserk");
	private static final Identifier MENDING_WORD = id("mending_word");
	private static final Identifier SANCTUARY = id("sanctuary");
	private static final Identifier FLAME_RING = id("flame_ring");
	private static final Identifier PLAGUE_CLOUD = id("plague_cloud");
	private static final Identifier RIFTWALK = id("riftwalk");
	private static final float BIG_HEALTH = 1000.0F;

	private static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, path);
	}

	// ---- 1 to 5: what counts ----

	@GameTest
	public void aCastThatHitsAddsOne(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SPARK_SHOT);
		SpellCasting.CastResult result = arena.cast(book);
		LOG.info("[SPELLTEST] test=aCastThatHitsAddsOne RESULT result={} count={}", result, Awakening.count(book));
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Spark Shot did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 1, "a landed cast left the count at " + Awakening.count(book));
		arena.cast(book);
		helper.assertTrue(Awakening.count(book) == 2, "a second landed cast left the count at " + Awakening.count(book));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void anAreaCastWithNothingInRangeAddsNothing(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		arena.clearTargets();
		arena.setBlock(2, 2, 8, Blocks.STONE);
		ItemStack timeLock = book(TIME_LOCK);
		ItemStack nova = book(FROST_NOVA);
		SpellCasting.CastResult lockResult = arena.cast(timeLock);
		SpellCasting.CastResult novaResult = arena.cast(nova);
		LOG.info("[SPELLTEST] test=anAreaCastWithNothingInRangeAddsNothing RESULT lock={} nova={} lockCount={} novaCount={}",
			lockResult, novaResult, Awakening.count(timeLock), Awakening.count(nova));
		helper.assertTrue(lockResult == SpellCasting.CastResult.CAST, "Time Lock did not land: " + lockResult);
		helper.assertTrue(novaResult == SpellCasting.CastResult.CAST, "Frost Nova did not land: " + novaResult);
		helper.assertTrue(Awakening.count(timeLock) == 0, "an empty Time Lock counted: " + Awakening.count(timeLock));
		helper.assertTrue(Awakening.count(nova) == 0, "an empty Frost Nova counted: " + Awakening.count(nova));
		// The same casts with a foe in reach count, so the zero above is the rule and not a dead counter.
		arena.zombie(2, 3);
		arena.cast(timeLock);
		arena.cast(nova);
		helper.assertTrue(Awakening.count(timeLock) == 1, "Time Lock on a foe counted " + Awakening.count(timeLock));
		helper.assertTrue(Awakening.count(nova) == 1, "Frost Nova on a foe counted " + Awakening.count(nova));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void aSelfBuffAlwaysAddsOne(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(BERSERK);
		SpellCasting.CastResult result = arena.cast(book);
		LOG.info("[SPELLTEST] test=aSelfBuffAlwaysAddsOne RESULT result={} count={}", result, Awakening.count(book));
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Berserk did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 1, "a self buff left the count at " + Awakening.count(book));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void mendingWordCountsOnlyWhenItHeals(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(MENDING_WORD);
		SpellCasting.CastResult full = arena.cast(book);
		helper.assertTrue(full == SpellCasting.CastResult.NO_TARGET, "Mending Word at full health read " + full);
		helper.assertTrue(Awakening.count(book) == 0, "a heal with nothing to mend counted " + Awakening.count(book));
		arena.player.setHealth(arena.player.getMaxHealth() - 6.0F);
		SpellCasting.CastResult hurt = arena.cast(book);
		LOG.info("[SPELLTEST] test=mendingWordCountsOnlyWhenItHeals RESULT full={} hurt={} count={}", full, hurt, Awakening.count(book));
		helper.assertTrue(hurt == SpellCasting.CastResult.CAST, "Mending Word on a wounded caster read " + hurt);
		helper.assertTrue(Awakening.count(book) == 1, "a real heal left the count at " + Awakening.count(book));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void aMovingSpellCountsWithoutAFoe(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(RIFTWALK);
		arena.player.setXRot(60.0F);
		SpellCasting.CastResult result = arena.cast(book);
		LOG.info("[SPELLTEST] test=aMovingSpellCountsWithoutAFoe RESULT result={} count={}", result, Awakening.count(book));
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Riftwalk did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 1, "a landed Riftwalk left the count at " + Awakening.count(book));
		arena.leave();
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void sanctuaryCountsOnceWhenALaterPulseFirstTouches(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SANCTUARY);
		SpellCasting.CastResult result = arena.cast(book);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Sanctuary did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 0, "Sanctuary counted before any pulse: " + Awakening.count(book));
		helper.runAfterDelay(85, () -> {
			LOG.info("[SPELLTEST] test=sanctuaryCountsOnceWhenALaterPulseFirstTouches RESULT count={}", Awakening.count(book));
			helper.assertTrue(Awakening.count(book) == 1, "after several pulses Sanctuary counted " + Awakening.count(book));
			arena.leave();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void flameRingCountsOnceWhenALaterPulseFirstTouches(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		arena.zombie(2, 3);
		ItemStack book = book(FLAME_RING);
		SpellCasting.CastResult result = arena.cast(book);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Flame Ring did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 0, "Flame Ring counted before any pulse: " + Awakening.count(book));
		helper.runAfterDelay(85, () -> {
			LOG.info("[SPELLTEST] test=flameRingCountsOnceWhenALaterPulseFirstTouches RESULT count={}", Awakening.count(book));
			helper.assertTrue(Awakening.count(book) == 1, "after several pulses Flame Ring counted " + Awakening.count(book));
			arena.leave();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160)
	public void aPlagueCloudCountsOnceWhenItFirstTouches(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		// Aimed down at the floor beside the cow, so the low cloud overlaps its body.
		arena.player.setXRot(16.0F);
		// A zombie is undead and shrugs off poison, so the cloud needs a living body to touch.
		arena.clearTargets();
		Mob cow = arena.spawn(EntityTypes.COW, 2, 7);
		ItemStack book = book(PLAGUE_CLOUD);
		SpellCasting.CastResult result = arena.cast(book);
		helper.assertTrue(result == SpellCasting.CastResult.CAST, "Plague Cloud did not cast: " + result);
		helper.assertTrue(Awakening.count(book) == 0, "Plague Cloud counted before it touched: " + Awakening.count(book));
		helper.runAfterDelay(100, () -> {
			LOG.info("[SPELLTEST] test=aPlagueCloudCountsOnceWhenItFirstTouches RESULT count={} poisoned={}", Awakening.count(book), cow.hasEffect(MobEffects.POISON));
			helper.assertTrue(cow.hasEffect(MobEffects.POISON), "the cloud poisoned nothing, so the case proves nothing");
			helper.assertTrue(Awakening.count(book) == 1, "after many touches the cloud counted " + Awakening.count(book));
			arena.leave();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void aFlameRingDoublecastKeepsOneDeferredCredit(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		Mob victim = arena.zombie(2, 3);
		float beforeHealth = victim.getHealth();
		ItemStack book = book(FLAME_RING);
		book.enchant(enchantment(helper, "doublecast"), 1);
		SpellCasting.DoublecastRoll previous = SpellCasting.doublecastRoll();
		int beforeDoublecasts = SpellCasting.doublecasts;
		try {
			SpellCasting.setDoublecastRoll(caster -> true);
			helper.assertTrue(arena.cast(book) == SpellCasting.CastResult.CAST, "Flame Ring did not cast");
			helper.assertTrue(SpellCasting.doublecasts == beforeDoublecasts + 1, "Flame Ring did not doublecast");
		} finally {
			SpellCasting.setDoublecastRoll(previous);
		}
		helper.runAfterDelay(85, () -> {
			LOG.info("[SPELLTEST] test=aFlameRingDoublecastKeepsOneDeferredCredit RESULT count={} healthBefore={} healthAfter={}",
				Awakening.count(book), beforeHealth, victim.getHealth());
			try {
				helper.assertTrue(victim.getHealth() < beforeHealth, "the aura hit nothing");
				helper.assertTrue(Awakening.count(book) == 1, "Flame Ring doublecast credited " + Awakening.count(book));
			} finally {
				arena.leave();
			}
			helper.succeed();
		});
	}

	@GameTest
	public void castsThatEndInCooldownNoTargetFizzleOrAScrollAddNothing(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SPARK_SHOT);
		helper.assertTrue(arena.cast(book) == SpellCasting.CastResult.CAST, "the first cast did not land");
		helper.assertTrue(Awakening.count(book) == 1, "the control cast left the count at " + Awakening.count(book));

		arena.player.setItemInHand(InteractionHand.MAIN_HAND, book);
		SpellCasting.CastResult cooling = SpellCasting.cast(arena.player, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(cooling == SpellCasting.CastResult.COOLDOWN, "a cast on cooldown read " + cooling);

		arena.clearTargets();
		SpellCasting.CastResult none = arena.cast(book);
		helper.assertTrue(none == SpellCasting.CastResult.NO_TARGET, "a cast with no target read " + none);

		arena.zombie(2, 7);
		arena.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NAUSEA, 200, 0));
		SpellCasting.setFizzleRoll(caster -> true);
		SpellCasting.CastResult fizzle;
		try {
			fizzle = arena.cast(book);
		} finally {
			SpellCasting.setFizzleRoll(null);
			arena.player.removeEffect(MobEffects.NAUSEA);
		}
		helper.assertTrue(fizzle == SpellCasting.CastResult.FIZZLE, "a cast under Nausea read " + fizzle);

		ItemStack scrollBook = book(SPARK_SHOT);
		SpellScrollItem scroll = Spellbooks.SCROLLS.stream().filter(item -> item.definition().id().equals(SPARK_SHOT)).findFirst().orElseThrow();
		arena.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(scroll));
		arena.player.getInventory().setItem(1, scrollBook);
		SpellCasting.CastResult scrolled = SpellCasting.castScroll(arena.player, scroll, InteractionHand.MAIN_HAND);
		LOG.info("[SPELLTEST] test=castsThatEndInCooldownNoTargetFizzleOrAScrollAddNothing RESULT cooling={} none={} fizzle={} scroll={} count={} otherBook={}",
			cooling, none, fizzle, scrolled, Awakening.count(book), Awakening.count(scrollBook));
		helper.assertTrue(scrolled == SpellCasting.CastResult.CAST, "the scroll did not land: " + scrolled);
		helper.assertTrue(Awakening.count(book) == 1, "a failed cast or a scroll moved the count to " + Awakening.count(book));
		helper.assertTrue(Awakening.count(scrollBook) == 0, "a scroll cast counted on a book in the pack: " + Awakening.count(scrollBook));
		arena.leave();
		helper.succeed();
	}

	@GameTest(maxTicks = 160)
	public void abortedCloudEarnsNoCredit(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		arena.player.setXRot(16.0F);
		arena.clearTargets();
		Mob cow = arena.spawn(EntityTypes.COW, 2, 7);
		ItemStack book = book(PLAGUE_CLOUD);
		boolean aborted = false;
		MoreSpells.setRecorder((caster, cloud, effect) -> {
			if (caster == arena.player) {
				throw new IllegalStateException("cloud callback failure");
			}
		});
		try {
			arena.cast(book);
		} catch (IllegalStateException failure) {
			aborted = true;
		} finally {
			MoreSpells.setRecorder(null);
		}
		helper.assertTrue(aborted, "the cloud cast did not reach its failing callback");
		helper.runAfterDelay(100, () -> {
			LOG.info("[SPELLTEST] test=abortedCloudEarnsNoCredit RESULT poisoned={} count={}",
				cow.hasEffect(MobEffects.POISON), Awakening.count(book));
			try {
				helper.assertTrue(cow.hasEffect(MobEffects.POISON), "the surviving cloud hit nothing");
				helper.assertTrue(Awakening.count(book) == 0, "an aborted cloud earned " + Awakening.count(book));
			} finally {
				arena.leave();
			}
			helper.succeed();
		});
	}

	@GameTest
	public void aDoublecastAddsOneNotTwo(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SPARK_SHOT);
		book.enchant(enchantment(helper, "doublecast"), 1);
		SpellCasting.DoublecastRoll before = SpellCasting.doublecastRoll();
		int doublecastsBefore = SpellCasting.doublecasts;
		try {
			SpellCasting.setDoublecastRoll(caster -> true);
			SpellCasting.CastResult result = arena.cast(book);
			int landed = SpellCasting.doublecasts - doublecastsBefore;
			LOG.info("[SPELLTEST] test=aDoublecastAddsOneNotTwo RESULT result={} doublecasts={} count={}", result, landed, Awakening.count(book));
			helper.assertTrue(result == SpellCasting.CastResult.CAST, "the cast read " + result);
			helper.assertTrue(landed == 1, "the doublecast did not land, so the case proves nothing: " + landed);
			helper.assertTrue(Awakening.count(book) == 1, "a doublecast added " + Awakening.count(book));
		} finally {
			SpellCasting.setDoublecastRoll(before);
			arena.leave();
		}
		helper.succeed();
	}

	// ---- 6 and 7: the flip ----
	@GameTest
	public void aDoublecastCreditsAHitThatOnlyLandsSecond(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "static_lash"));
		book.enchant(enchantment(helper, "doublecast"), 1);
		arena.player.setItemInHand(InteractionHand.MAIN_HAND, book);
		arena.zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 100.0F);
		arena.zombie.setHealth(BIG_HEALTH);
		SpellCasting.DoublecastRoll previous = SpellCasting.doublecastRoll();
		try {
			SpellCasting.setDoublecastRoll(caster -> true);
			SpellCasting.CastResult result = SpellCasting.cast(arena.player, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);
			LOG.info("[SPELLTEST] test=aDoublecastCreditsAHitThatOnlyLandsSecond RESULT result={} count={} health={}",
				result, Awakening.count(book), arena.zombie.getHealth());
			helper.assertTrue(result == SpellCasting.CastResult.CAST && arena.zombie.getHealth() < BIG_HEALTH, "the second hit did not damage");
			helper.assertTrue(Awakening.count(book) == 1, "the second-only hit credited " + Awakening.count(book));
		} finally {
			SpellCasting.setDoublecastRoll(previous);
			arena.leave();
		}
		helper.succeed();
	}

	@GameTest
	public void rejectedSlimeboundBuffsEarnNoCredit(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "slimebound"));
		arena.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.JUMP_BOOST,
			Integer.MAX_VALUE, SpellNumbers.SLIMEBOUND.amplifier() + 1));
		arena.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SLOW_FALLING,
			Integer.MAX_VALUE, SpellNumbers.SLIMEBOUND.extraAmplifier() + 1));
		arena.cast(book);
		helper.assertTrue(Awakening.count(book) == 0, "rejected buffs credited " + Awakening.count(book));
		arena.player.removeEffect(MobEffects.JUMP_BOOST);
		arena.player.removeEffect(MobEffects.SLOW_FALLING);
		arena.cast(book);
		LOG.info("[SPELLTEST] test=rejectedSlimeboundBuffsEarnNoCredit RESULT count={}", Awakening.count(book));
		helper.assertTrue(Awakening.count(book) == 1, "successful buffs earned no credit");
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void anUnrelatedHealDoesNotTouchAnOpenCast(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		Mob unrelated = arena.spawn(EntityTypes.COW, 2, 4);
		unrelated.setHealth(BIG_HEALTH / 2);
		float before = unrelated.getHealth();
		boolean touched;
		CastHits.begin(arena.player, SPARK_SHOT, true);
		try {
			unrelated.heal(4.0F);
		} finally {
			touched = CastHits.end();
		}
		LOG.info("[SPELLTEST] test=anUnrelatedHealDoesNotTouchAnOpenCast RESULT healed={} touched={}",
			unrelated.getHealth() > before, touched);
		helper.assertTrue(unrelated.getHealth() > before && !touched, "an unrelated heal touched the open cast");
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void theHundredAndTwentiethLandedCastAwakensTheBookAndKeepsEveryComponent(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SPARK_SHOT);
		book.enchant(enchantment(helper, "quickening"), 2);
		book.enchant(enchantment(helper, "potency"), 1);
		book.set(DataComponents.CUSTOM_NAME, Component.literal("Oniichan's Spark"));
		book.set(DataComponents.REPAIR_COST, 7);
		CompoundTag tag = new CompoundTag();
		tag.putInt("refine", 3);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		book.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN - 1);
		Map<DataComponentType<?>, Object> before = components(book);
		before.remove(Awakening.COUNT);

		arena.player.setItemInHand(InteractionHand.MAIN_HAND, book);
		arena.player.getCooldowns().removeCooldown(arena.player.getCooldowns().getCooldownGroup(book));
		arena.zombie.setHealth(BIG_HEALTH);
		arena.zombie.damageCooldownTime = 0;
		SpellCasting.cast(arena.player, (SpellbookItem) book.getItem(), InteractionHand.MAIN_HAND);

		ItemStack held = arena.player.getMainHandItem();
		Map<DataComponentType<?>, Object> after = components(held);
		boolean flipped = Awakening.isAwakened(held);
		Object glint = after.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		after.remove(Awakening.AWAKENED);
		Object count = after.remove(Awakening.COUNT);
		LOG.info("[SPELLTEST] test=theHundredAndTwentiethLandedCastAwakensTheBookAndKeepsEveryComponent RESULT flipped={} count={} glint={} sameStack={}",
			flipped, count, glint, held == book);
		helper.assertTrue(flipped, "the 120th landed cast did not awaken the book");
		helper.assertTrue(held == book, "the flip replaced the stack in the hand");
		helper.assertTrue(Integer.valueOf(Awakening.CASTS_TO_AWAKEN).equals(count), "the flip left the count at " + count);
		helper.assertTrue(Boolean.TRUE.equals(glint), "the flip set no glint: " + glint);
		helper.assertTrue(before.equals(after), "the flip changed a component: before=" + before + " after=" + after);
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void anAwakenedBookKeepsItsCountAndComponentsOnLaterCasts(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = awakened(SPARK_SHOT);
		book.enchant(enchantment(helper, "potency"), 2);
		Map<DataComponentType<?>, Object> before = components(book);
		for (int i = 0; i < 3; i++) {
			SpellCasting.CastResult result = arena.cast(book);
			helper.assertTrue(result == SpellCasting.CastResult.CAST, "cast " + i + " read " + result);
		}
		Map<DataComponentType<?>, Object> after = components(book);
		LOG.info("[SPELLTEST] test=anAwakenedBookKeepsItsCountAndComponentsOnLaterCasts RESULT count={}", Awakening.count(book));
		helper.assertTrue(Awakening.count(book) == Awakening.CASTS_TO_AWAKEN, "an awakened book's count moved to " + Awakening.count(book));
		helper.assertTrue(before.equals(after), "casts changed an awakened book: before=" + before + " after=" + after);
		arena.leave();
		helper.succeed();
	}

	// ---- 8: the flag reaches the effect ----

	@GameTest
	public void anAwakenedBooksCastReachesTheEffectAwakenedAndAPlainOneDoesNot(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		List<String> seen = new CopyOnWriteArrayList<>();
		Awakening.setProbe((spell, awakened) -> seen.add(spell.getPath() + ":" + awakened));
		try {
			arena.cast(book(SPARK_SHOT));
			arena.cast(awakened(SPARK_SHOT));
		} finally {
			Awakening.setProbe(null);
		}
		LOG.info("[SPELLTEST] test=anAwakenedBooksCastReachesTheEffectAwakenedAndAPlainOneDoesNot RESULT seen={}", seen);
		helper.assertTrue(seen.equals(List.of("spark_shot:false", "spark_shot:true")), "the probe saw " + seen);
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void theFlagSurvivesTheRpgBridge(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		List<String> seen = new CopyOnWriteArrayList<>();
		Awakening.setProbe((spell, awakened) -> seen.add(spell.getPath() + ":" + awakened));
		RpgBridge.setStats(new RpgBridge.Stats() {
			@Override
			public double power(final Player player) {
				return 2.0;
			}

			@Override
			public double cooldown(final Player player) {
				return 0.5;
			}
		});
		try {
			arena.cast(awakened(SPARK_SHOT));
		} finally {
			RpgBridge.setStats(null);
			Awakening.setProbe(null);
		}
		LOG.info("[SPELLTEST] test=theFlagSurvivesTheRpgBridge RESULT seen={}", seen);
		helper.assertTrue(seen.equals(List.of("spark_shot:true")), "through the RPG bridge the probe saw " + seen);
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void theFlagSurvivesTheTowerBridge(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		List<String> seen = new CopyOnWriteArrayList<>();
		Awakening.setProbe((spell, awakened) -> seen.add(spell.getPath() + ":" + awakened));
		ItemStack book = awakened(SPARK_SHOT);
		CompoundTag tag = new CompoundTag();
		tag.putDouble("flat", 2.0);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		float before = arena.zombie.getHealth();
		try {
			arena.cast(book);
		} finally {
			Awakening.setProbe(null);
		}
		float lost = before - arena.zombie.getHealth();
		LOG.info("[SPELLTEST] test=theFlagSurvivesTheTowerBridge RESULT seen={} lost={}", seen, lost);
		helper.assertTrue(lost > SpellCasting.DAMAGE + 0.01F, "the tower stand-in did not grow the cast, so the case proves nothing: lost " + lost);
		helper.assertTrue(seen.equals(List.of("spark_shot:true")), "through the tower bridge the probe saw " + seen);
		arena.leave();
		helper.succeed();
	}

	// ---- 9: combos ----

	@GameTest
	public void combosStillFireWithAwakenedBooks(final GameTestHelper helper) {
		boolean[][] cases = {{true, false}, {false, true}, {true, true}};
		for (boolean[] awake : cases) {
			Arena arena = arena(helper, 2);
			float before = arena.zombie.getHealth();
			arena.cast(awake[0] ? awakened(FROSTBIND) : book(FROSTBIND));
			arena.cast(awake[1] ? awakened(SPARK_SHOT) : book(SPARK_SHOT));
			float comboDrop = before - arena.zombie.getHealth();
			arena.leave();

			Arena control = arena(helper, 3);
			float controlBefore = control.zombie.getHealth();
			control.cast(book(SPARK_SHOT));
			float plainDrop = controlBefore - control.zombie.getHealth();
			control.leave();
			LOG.info("[SPELLTEST] test=combosStillFireWithAwakenedBooks RESULT first={} second={} comboDrop={} plainDrop={}", awake[0], awake[1], comboDrop, plainDrop);
			helper.assertTrue(Math.abs(comboDrop - plainDrop - SpellCombos.THERMAL_SHOCK_DAMAGE) < 1.0e-4,
				"Thermal Shock with awakened=" + awake[0] + "," + awake[1] + " dealt " + comboDrop + " against a plain " + plainDrop);
			arena.zombie.discard();
			control.zombie.discard();
		}
		helper.succeed();
	}

	// ---- 10 to 12: the imbue rule ----

	@GameTest
	public void anAwakenedBookImbuesAnAwakenedSpellAndOnlyThatSpellCastsAwakened(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack first = anvil(helper, arena.player, sword, awakened(SPARK_SHOT));
		helper.assertTrue(!first.isEmpty() && SPARK_SHOT.equals(first.get(Awakening.AWAKENED_IMBUE)), "the anvil made " + first + " awakened imbue=" + first.get(Awakening.AWAKENED_IMBUE));
		ItemStack both = anvil(helper, arena.player, first, book(FROSTBIND));
		helper.assertTrue(!both.isEmpty() && SPARK_SHOT.equals(both.get(Awakening.AWAKENED_IMBUE)), "a plain book changed the awakened imbue on " + both);
		helper.assertTrue(SpellImbue.spellsOf(both).equals(List.of(SPARK_SHOT, FROSTBIND)), "the weapon holds " + SpellImbue.spellsOf(both));

		List<String> seen = new CopyOnWriteArrayList<>();
		Awakening.setProbe((spell, awakened) -> seen.add(spell.getPath() + ":" + awakened));
		try {
			arena.player.setItemInHand(InteractionHand.MAIN_HAND, both);
			arena.zombie.damageCooldownTime = 0;
			arena.zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(arena.player), 4.0F);
		} finally {
			Awakening.setProbe(null);
		}
		LOG.info("[SPELLTEST] test=anAwakenedBookImbuesAnAwakenedSpellAndOnlyThatSpellCastsAwakened RESULT imbue={} seen={}", both.get(Awakening.AWAKENED_IMBUE), seen);
		helper.assertTrue(seen.contains("spark_shot:true"), "the awakened imbue did not cast awakened: " + seen);
		helper.assertTrue(seen.contains("frostbind:false"), "the plain imbue did not cast plain: " + seen);
		helper.assertTrue(seen.size() == 2, "the imbued hit cast " + seen);
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void aSecondAwakenedBookGivesNoAnvilResultAndAPlainBookStillImbues(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		ItemStack imbued = anvil(helper, arena.player, sword, awakened(SPARK_SHOT));
		helper.assertTrue(SPARK_SHOT.equals(imbued.get(Awakening.AWAKENED_IMBUE)), "the first awakened book did not imbue");
		ItemStack second = anvil(helper, arena.player, imbued, awakened(FROSTBIND));
		ItemStack plain = anvil(helper, arena.player, imbued, book(FROSTBIND));
		LOG.info("[SPELLTEST] test=aSecondAwakenedBookGivesNoAnvilResultAndAPlainBookStillImbues RESULT second={} plain={}", second, SpellImbue.spellsOf(plain));
		helper.assertTrue(second.isEmpty(), "a second awakened book gave " + second);
		helper.assertTrue(SpellImbue.spellsOf(plain).equals(List.of(SPARK_SHOT, FROSTBIND)), "a plain book gave " + SpellImbue.spellsOf(plain));
		helper.assertTrue(SPARK_SHOT.equals(plain.get(Awakening.AWAKENED_IMBUE)), "a plain book moved the awakened imbue to " + plain.get(Awakening.AWAKENED_IMBUE));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void anAwakenedBookUpgradesASpellTheWeaponHoldsAsPlain(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack plainSword = anvil(helper, arena.player, new ItemStack(Items.IRON_SWORD), book(SPARK_SHOT));
		helper.assertTrue(SpellImbue.spellsOf(plainSword).equals(List.of(SPARK_SHOT)) && plainSword.get(Awakening.AWAKENED_IMBUE) == null, "the plain imbue is wrong: " + plainSword);
		ItemStack upgraded = anvil(helper, arena.player, plainSword, awakened(SPARK_SHOT));
		LOG.info("[SPELLTEST] test=anAwakenedBookUpgradesASpellTheWeaponHoldsAsPlain RESULT spells={} awakened={}", SpellImbue.spellsOf(upgraded), upgraded.get(Awakening.AWAKENED_IMBUE));
		helper.assertTrue(!upgraded.isEmpty(), "the anvil refused to upgrade a plain imbue");
		helper.assertTrue(SPARK_SHOT.equals(upgraded.get(Awakening.AWAKENED_IMBUE)), "the upgrade set the awakened imbue to " + upgraded.get(Awakening.AWAKENED_IMBUE));
		helper.assertTrue(SpellImbue.spellsOf(upgraded).equals(List.of(SPARK_SHOT)), "the upgrade changed the spell list to " + SpellImbue.spellsOf(upgraded));
		arena.leave();
		helper.succeed();
	}

	@GameTest
	public void arrowsNeverTakeTheAwakenedImbue(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack arrows = anvil(helper, arena.player, new ItemStack(Items.ARROW, 16), awakened(SPARK_SHOT));
		LOG.info("[SPELLTEST] test=arrowsNeverTakeTheAwakenedImbue RESULT spells={} awakened={}", SpellImbue.spellsOf(arrows), arrows.get(Awakening.AWAKENED_IMBUE));
		helper.assertTrue(SpellImbue.spellsOf(arrows).equals(List.of(SPARK_SHOT)), "an awakened book did not imbue arrows as a plain book: " + SpellImbue.spellsOf(arrows));
		helper.assertTrue(arrows.get(Awakening.AWAKENED_IMBUE) == null, "arrows took the awakened imbue");
		arena.leave();
		helper.succeed();
	}

	// ---- 13: loot ----

	@GameTest
	public void containerLootNeverCarriesAwakeningState(final GameTestHelper helper) {
		RandomSource random = RandomSource.create(20_261_005L);
		Identifier chest = Identifier.parse("minecraft:chests/simple_dungeon");
		int books = 0;
		int scrolls = 0;
		for (int i = 0; i < 10_000; i++) {
			List<ItemStack> stacks = new ArrayList<>();
			stacks.add(SpellbookTestAccess.rollChest(chest, true, random));
			stacks.add(SpellbookTestAccess.rollWitch(random));
			SpellbookTestAccess.modifyChestDrops(chest, true, random, stacks);
			for (ItemStack stack : stacks) {
				books += stack.getItem() instanceof SpellbookItem ? 1 : 0;
				scrolls += stack.getItem() instanceof SpellScrollItem ? 1 : 0;
				helper.assertTrue(!stack.has(Awakening.AWAKENED) && !stack.has(Awakening.AWAKENED_IMBUE) && Awakening.count(stack) == 0,
					"a loot stack carried awakening state: " + stack + " " + components(stack));
			}
		}
		LOG.info("[SPELLTEST] test=containerLootNeverCarriesAwakeningState RESULT books={} scrolls={}", books, scrolls);
		helper.assertTrue(books > 100 && scrolls > 100, "the rolls produced " + books + " books and " + scrolls + " scrolls, too few to prove the rule");
		helper.succeed();
	}

	// ---- 14: the command ----

	@GameTest
	public void spellbookAwakenSetsTheHeldBookToTheThresholdAndAwakensIt(final GameTestHelper helper) {
		Arena arena = arena(helper, 2);
		ItemStack book = book(SPARK_SHOT);
		book.enchant(enchantment(helper, "potency"), 1);
		arena.player.setItemInHand(InteractionHand.MAIN_HAND, book);
		var server = helper.getLevel().getServer();

		server.getCommands().performPrefixedCommand(arena.player.createCommandSourceStack().withSuppressedOutput(), "spellbook awaken");
		helper.assertTrue(!Awakening.isAwakened(book) && Awakening.count(book) == 0, "a level 0 source awakened the book");

		server.getCommands().performPrefixedCommand(
			arena.player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput(), "spellbook awaken");
		LOG.info("[SPELLTEST] test=spellbookAwakenSetsTheHeldBookToTheThresholdAndAwakensIt RESULT count={} awakened={}", Awakening.count(book), Awakening.isAwakened(book));
		helper.assertTrue(Awakening.count(book) == Awakening.CASTS_TO_AWAKEN, "the command set the count to " + Awakening.count(book));
		helper.assertTrue(Awakening.isAwakened(book), "the command did not awaken the book");
		helper.assertTrue(arena.player.getMainHandItem() == book, "the command replaced the held stack");

		arena.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		server.getCommands().performPrefixedCommand(
			arena.player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER).withSuppressedOutput(), "spellbook awaken");
		helper.assertTrue(!arena.player.getMainHandItem().has(Awakening.AWAKENED), "the command awakened a stick");
		arena.leave();
		helper.succeed();
	}

	// ---- 18 support: the numbers the tooltip shows ----

	@GameTest
	public void castsLeftReadsTheCountAndIsZeroOnceAwakened(final GameTestHelper helper) {
		ItemStack book = book(SPARK_SHOT);
		helper.assertTrue(Awakening.castsLeft(book) == Awakening.CASTS_TO_AWAKEN, "a new book has " + Awakening.castsLeft(book) + " casts left");
		book.set(Awakening.COUNT, 0);
		helper.assertTrue(Awakening.castsLeft(book) == Awakening.CASTS_TO_AWAKEN, "count 0 has " + Awakening.castsLeft(book) + " left");
		book.set(Awakening.COUNT, 87);
		helper.assertTrue(Awakening.castsLeft(book) == Awakening.CASTS_TO_AWAKEN - 87, "count 87 has " + Awakening.castsLeft(book) + " left");
		book.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN);
		helper.assertTrue(Awakening.castsLeft(book) == 0, "count 120 has " + Awakening.castsLeft(book) + " left");
		helper.succeed();
	}

	@GameTest
	public void theTooltipAndNameShowTheAwakeningState(final GameTestHelper helper) {
		ItemStack plain = book(SPARK_SHOT);
		List<String> fresh = text(SpellTooltip.awakeningLines(plain));
		plain.set(Awakening.COUNT, 87);
		List<String> partway = text(SpellTooltip.awakeningLines(plain));
		ItemStack awake = awakened(SPARK_SHOT);
		List<String> done = text(SpellTooltip.awakeningLines(awake));
		ItemStack scroll = new ItemStack(Spellbooks.SCROLLS.get(0));
		LOG.info("[SPELLTEST] test=theTooltipAndNameShowTheAwakeningState RESULT fresh={} partway={} done={}", fresh, partway, done);
		helper.assertTrue(fresh.equals(List.of("Awakening 0/120", "Awakens in 120 casts")), "a new book shows " + fresh);
		helper.assertTrue(partway.equals(List.of("Awakening 87/120", "Awakens in 33 casts")), "an 87 cast book shows " + partway);
		helper.assertTrue(done.equals(List.of("Awakened")), "an awakened book shows " + done);
		helper.assertTrue(SpellTooltip.awakeningLines(scroll).isEmpty(), "a scroll shows awakening lines");
		Component plainName = plain.getHoverName();
		Component awakeName = awake.getHoverName();
		boolean keyed = awakeName.getContents() instanceof TranslatableContents contents && contents.getKey().equals("item." + Spellbooks.MOD_ID + ".awakened");
		helper.assertTrue(keyed, "an awakened book's name is " + awakeName);
		helper.assertTrue(!(plainName.getContents() instanceof TranslatableContents plainContents && plainContents.getKey().contains("awakened")), "a plain book's name is " + plainName);
		helper.succeed();
	}

	// ---- helpers ----

	private static List<String> text(final List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static ItemStack book(final Identifier spell) {
		return BuiltInRegistries.ITEM.getValue(spell).getDefaultInstance();
	}

	private static ItemStack awakened(final Identifier spell) {
		ItemStack stack = book(spell);
		stack.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN);
		stack.set(Awakening.AWAKENED, Unit.INSTANCE);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static Map<DataComponentType<?>, Object> components(final ItemStack stack) {
		Map<DataComponentType<?>, Object> map = new HashMap<>();
		for (TypedDataComponent<?> component : stack.getComponents()) {
			map.put(component.type(), component.value());
		}
		return map;
	}

	private static Holder<Enchantment> enchantment(final GameTestHelper helper, final String path) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id(path)));
	}

	private static ItemStack anvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final ItemStack right) {
		AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		anvil.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		anvil.createResult();
		return anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
	}

	private static Arena arena(final GameTestHelper helper, final int lane) {
		return new Arena(helper, lane);
	}

	/** A lit stone lane, a caster at its start facing south, and a durable zombie six blocks ahead. */
	private static final class Arena {
		final GameTestHelper helper;
		final int lane;
		final ServerPlayer player;
		Mob zombie;
		private final List<Mob> spawned = new ArrayList<>();

		Arena(final GameTestHelper helper, final int lane) {
			this.helper = helper;
			this.lane = lane;
			helper.setTime(18_000L);
			for (int z = 0; z <= 9; z++) {
				helper.setBlock(lane, 0, z, Blocks.STONE);
			}
			this.player = helper.makeMockServerPlayerInLevel();
			this.player.setGameMode(GameType.SURVIVAL);
			Vec3 at = helper.absoluteVec(new Vec3(lane + 0.5, 1.0, 1.5));
			this.player.setPos(at.x, at.y, at.z);
			this.player.setYRot(0.0F);
			this.player.setXRot(0.0F);
			this.player.setNoGravity(true);
			this.zombie = zombie(lane, 7);
		}

		void setBlock(final int x, final int y, final int z, final net.minecraft.world.level.block.Block block) {
			this.helper.setBlock(x, y, z, block);
		}

		Mob zombie(final int x, final int z) {
			return spawn(EntityTypes.ZOMBIE, x, z);
		}

		Mob spawn(final net.minecraft.world.entity.EntityType<? extends Mob> type, final int x, final int z) {
			for (int step = 0; step <= 9; step++) {
				this.helper.setBlock(x, 0, step, Blocks.STONE);
			}
			Mob mob = this.helper.spawnMob(type, x, 1, z).withNoFreeWill().spawn();
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BIG_HEALTH);
			mob.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
			mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			mob.setHealth(BIG_HEALTH);
			this.spawned.add(mob);
			return mob;
		}

		void clearTargets() {
			this.spawned.forEach(Mob::discard);
			this.spawned.clear();
		}

		SpellCasting.CastResult cast(final ItemStack stack) {
			this.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			this.player.getCooldowns().removeCooldown(this.player.getCooldowns().getCooldownGroup(stack));
			for (Mob mob : this.spawned) {
				if (mob.isAlive()) {
					mob.setHealth(BIG_HEALTH);
					mob.damageCooldownTime = 0;
				}
			}
			return SpellCasting.cast(this.player, (SpellbookItem) stack.getItem(), InteractionHand.MAIN_HAND);
		}

		void leave() {
			this.helper.getLevel().getServer().getPlayerList().remove(this.player);
		}
	}
}
