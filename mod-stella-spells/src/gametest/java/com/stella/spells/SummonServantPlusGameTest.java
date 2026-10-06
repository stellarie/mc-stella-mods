package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The summoned skeleton's enhancements: it clears the ground around its master before it follows, a recast obeys
 * the cooldown and mends it, the master takes a quarter of what it earns, nothing of its own side may burn it, and
 * an awakened cast brings it a skeleton horse of its own.
 */
public class SummonServantPlusGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summonplus");
	/** The foe's distance from the master in the clear-radius case: inside CLEAR_RADIUS, well outside FOLLOW. */
	private static final double FOE_IN_THE_CLEAR = 12.0;
	/** A second foe, outside the clear radius but inside the seek reach, so the clear order can be told apart. */
	private static final double FOE_BEYOND_THE_CLEAR = 20.0;
	/** Where the servant waits in the clear-radius case: two blocks short of the near foe, on the same line. */
	private static final int SERVANT_AT = (int) FOE_IN_THE_CLEAR - 2;
	/** Where the servant waits for the clear-order case: beside the outer foe, so nearest-first would take it. */
	private static final int SERVANT_BESIDE_THE_OUTER = (int) FOE_BEYOND_THE_CLEAR - 2;

	/**
	 * The mount's mixin answers {@link SummonSkeleton#HORSE_MAX_HEALTH} for a tagged servant horse only. A wild
	 * skeleton horse carries no tag, so it must report the maximum vanilla gives it, which pins the mixin's reach.
	 */
	@GameTest(maxTicks = 60)
	public void aWildSkeletonHorseStillReportsTheVanillaMaximum(final GameTestHelper helper) {
		floor(helper);
		SkeletonHorse wild = EntityTypes.SKELETON_HORSE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(wild != null, "the level refused a wild skeleton horse");
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		wild.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(wild);
		AttributeInstance health = wild.getAttribute(Attributes.MAX_HEALTH);
		double vanillaMax = health == null ? -1.0 : health.getValue();
		LOG.info("[BEHAVIOR] test=aWildSkeletonHorseStillReportsTheVanillaMaximum RESULT tagged={} maxHealth={} vanillaMax={} servantMount={}",
			SummonSkeleton.isMount(wild), wild.getMaxHealth(), vanillaMax, SummonSkeleton.HORSE_MAX_HEALTH);
		helper.assertFalse(SummonSkeleton.isMount(wild), "a wild skeleton horse carries the servant mount tag");
		helper.assertTrue(vanillaMax > 0.0 && vanillaMax < SummonSkeleton.HORSE_MAX_HEALTH,
			"the vanilla skeleton horse maximum is " + vanillaMax + ", which does not sit below the servant mount's");
		helper.assertTrue(wild.getMaxHealth() == (float) vanillaMax,
			"a wild skeleton horse reports " + wild.getMaxHealth() + ", not the vanilla maximum " + vanillaMax);
		wild.discard();
		helper.succeed();
	}
	/** An instant health drink's worth, and the magic a hostile caster throws. */
	static final float INSTANT_HEALTH = 6.0F;
	static final float HOSTILE_MAGIC = 4.0F;

	@GameTest(maxTicks = 120)
	public void theServantClearsTheGroundAroundItsMasterBeforeItFollows(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		// Two foes on the master's line (+Z from the master). The near one is inside the clear radius, the far
		// one is outside it and inside the seek reach, so this case tells the clear order from the plain search.
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 1, SERVANT_AT, false);
		Mob near = foe(helper, master, FOE_IN_THE_CLEAR, FACING);
		Mob far = foe(helper, master, FOE_BEYOND_THE_CLEAR, FACING);
		double nearFromMaster = near.distanceTo(master);
		double farFromMaster = far.distanceTo(master);
		helper.assertTrue(near.distanceToSqr(servant) <= SummonSkeleton.FOLLOW_DISTANCE * SummonSkeleton.FOLLOW_DISTANCE,
			"the near foe stands " + near.distanceTo(servant) + " from the servant, too far for the grid's sight lines");
		helper.assertTrue(nearFromMaster > SummonSkeleton.FOLLOW_DISTANCE && nearFromMaster <= SummonSkeleton.CLEAR_RADIUS,
			"the near foe stands at " + nearFromMaster + ", outside the band this case tests");
		helper.assertTrue(farFromMaster > SummonSkeleton.CLEAR_RADIUS && farFromMaster <= SummonSkeleton.GUARD_RADIUS,
			"the far foe stands at " + farFromMaster + ", outside the seek reach this case tests");
		helper.assertTrue(servant.distanceTo(master) > SummonSkeleton.FOLLOW_DISTANCE,
			"the servant stands " + servant.distanceTo(master) + " from its master, so the follow leg proves nothing");
		Vec3 nearHeld = near.position();
		Vec3 farHeld = far.position();
		Vec3 servantHeld = servant.position();
		helper.onEachTick(() -> {
			near.setPos(nearHeld.x, nearHeld.y, nearHeld.z);
			far.setPos(farHeld.x, farHeld.y, farHeld.z);
			servant.setPos(servantHeld.x, servantHeld.y, servantHeld.z);
		});
		helper.runAfterDelay(20L, () -> {
			Mob chosen = SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH);
			boolean clearing = SummonSkeleton.clearing(servant, master);
			LOG.info("[BEHAVIOR] test=theServantClearsTheGroundAroundItsMasterBeforeItFollows RESULT nearFromMaster={} farFromMaster={} clearRadius={} guardRadius={} tookTheNear={} tookTheFar={} clearing={} follows={}",
				nearFromMaster, farFromMaster, SummonSkeleton.CLEAR_RADIUS, SummonSkeleton.GUARD_RADIUS,
				chosen == near, chosen == far, clearing, companionFollows(servant, master));
			helper.assertTrue(SummonSkeleton.CLEAR_RADIUS < SummonSkeleton.GUARD_RADIUS,
				"the clear radius " + SummonSkeleton.CLEAR_RADIUS + " is not inside the seek reach " + SummonSkeleton.GUARD_RADIUS);
			helper.assertTrue(chosen == near, "the servant took the far foe while a foe stood in the clear");
			helper.assertTrue(clearing, "the servant did not read its master's ground as occupied");
			helper.assertFalse(companionFollows(servant, master), "the servant still walks home while a foe stands in the clear");
			// The ground around the master is now clear, so the follow resumes. The wider seek reach has its own
			// guard below, because a sight line to a foe that far off is a grid question and not this case's.
			near.discard();
			helper.runAfterDelay(10L, () -> {
				boolean stillClearing = SummonSkeleton.clearing(servant, master);
				boolean follows = companionFollows(servant, master);
				LOG.info("[BEHAVIOR] test=theServantClearsTheGroundAroundItsMasterBeforeItFollows RESULT cleared={} follows={}",
					!stillClearing, follows);
				helper.assertFalse(stillClearing, "the servant still reads a cleared ground as occupied");
				helper.assertTrue(follows, "the servant did not return to following once the ground was clear");
				far.discard();
				servant.discard();
				leave(helper, master);
				helper.succeed();
			});
		});
	}

	/**
	 * The clearest reading of the clear order. Both foes stand on the master's line and both are in the servant's
	 * own sight. The foe in the clear waits at the master's clear reach and the other at the seek reach, and the
	 * servant stands beside the outer one, so a plain nearest-first search would take that one. The servant takes
	 * the foe in the clear instead, which is the whole rule, and removing it turns this case red.
	 */
	@GameTest(maxTicks = 100)
	public void theServantTakesTheFoeInTheClearOverTheNearerOneOutsideIt(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 1, SERVANT_BESIDE_THE_OUTER, false);
		Mob inTheClear = foe(helper, master, FOE_IN_THE_CLEAR, FACING);
		Mob outsideIt = foe(helper, master, FOE_BEYOND_THE_CLEAR, FACING);
		Vec3 clearingHeld = inTheClear.position();
		Vec3 outsideHeld = outsideIt.position();
		Vec3 servantHeld = servant.position();
		helper.onEachTick(() -> {
			inTheClear.setPos(clearingHeld.x, clearingHeld.y, clearingHeld.z);
			outsideIt.setPos(outsideHeld.x, outsideHeld.y, outsideHeld.z);
			// Pinned in place, and held facing the line both foes stand on, so a sight line cannot drift.
			servant.setPos(servantHeld.x, servantHeld.y, servantHeld.z);
			servant.setYRot(FACING);
			servant.setYHeadRot(FACING);
		});
		helper.runAfterDelay(20L, () -> {
			Mob chosen = SummonSkeleton.targetFor(servant, SummonSkeleton.Mode.SEARCH);
			boolean clearFoeSeen = servant.hasLineOfSight(inTheClear);
			boolean outsideFoeSeen = servant.hasLineOfSight(outsideIt);
			LOG.info("[BEHAVIOR] test=theServantTakesTheFoeInTheClearOverTheNearerOneOutsideIt RESULT clearFoeFromMaster={} outsideFoeFromMaster={} clearFoeFromServant={} outsideFoeFromServant={} sawBoth={} tookTheClearFoe={}",
				inTheClear.distanceTo(master), outsideIt.distanceTo(master), inTheClear.distanceTo(servant), outsideIt.distanceTo(servant),
				clearFoeSeen && outsideFoeSeen, chosen == inTheClear);
			helper.assertTrue(clearFoeSeen && outsideFoeSeen,
				"the grid denies a sight line to one of the two foes, so this case cannot be told apart here");
			helper.assertTrue(inTheClear.distanceToSqr(servant) > outsideIt.distanceToSqr(servant),
				"the foe in the clear is not the farther one from the servant, so the order proves nothing");
			helper.assertTrue(chosen == inTheClear, "the servant took the outside foe while a foe stood in the clear");
			inTheClear.discard();
			outsideIt.discard();
			servant.discard();
			leave(helper, master);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aRecastMendsTheServantByTheRecallFraction(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		float maximum = servant.getMaxHealth();
		float before = maximum - 10.0F;
		servant.setHealth(before);
		clearCooldown(helper, master);
		SpellCasting.CastResult second = SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		float gained = servant.getHealth() - before;
		float mend = (float) (maximum * SummonSkeleton.RECALL_HEAL_FRACTION);
		LOG.info("[BEHAVIOR] test=aRecastMendsTheServantByTheRecallFraction RESULT result={} max={} before={} after={} gained={} mend={}",
			second, maximum, before, servant.getHealth(), gained, mend);
		helper.assertTrue(second == SpellCasting.CastResult.CAST, "the recast went " + second);
		helper.assertTrue(Math.abs(gained - mend) < 1.0E-3F,
			"the recast mended " + gained + " of a maximum of " + maximum + ", not " + mend);
		// At full health the same recast takes nothing.
		servant.setHealth(maximum);
		clearCooldown(helper, master);
		SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		LOG.info("[BEHAVIOR] test=aRecastMendsTheServantByTheRecallFraction RESULT atFull={} cap={}", servant.getHealth(), maximum);
		helper.assertTrue(servant.getHealth() == maximum, "a recast at full health left " + servant.getHealth());
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aRecastInsideTheCooldownIsRefusedAndTouchesNothing(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		float maximum = servant.getMaxHealth();
		servant.setHealth(maximum - 10.0F);
		float wounded = servant.getHealth();
		Vec3 held = servant.position();
		Vec3 far = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		master.teleportTo(far.x, far.y, far.z);
		SpellCasting.CastResult refused = SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		double moved = servant.distanceTo(master);
		LOG.info("[BEHAVIOR] test=aRecastInsideTheCooldownIsRefusedAndTouchesNothing RESULT refused={} health={} wounded={} moved={} resting={}",
			refused, servant.getHealth(), wounded, moved, master.getCooldowns().isOnCooldown(master.getMainHandItem()));
		helper.assertTrue(refused == SpellCasting.CastResult.COOLDOWN, "a recast inside the rest answered " + refused);
		helper.assertTrue(servant.getHealth() == wounded, "a refused recast mended the servant to " + servant.getHealth());
		helper.assertTrue(servant.position().equals(held), "a refused recast moved the servant to " + servant.position());
		// With the rest over the same cast recalls and mends.
		clearCooldown(helper, master);
		SpellCasting.CastResult after = SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		double near = servant.distanceTo(master);
		LOG.info("[BEHAVIOR] test=aRecastInsideTheCooldownIsRefusedAndTouchesNothing RESULT afterRest={} health={} near={}",
			after, servant.getHealth(), near);
		helper.assertTrue(after == SpellCasting.CastResult.CAST && near <= 3.0,
			"the recast after the rest went " + after + " and left it " + near + " away");
		helper.assertTrue(servant.getHealth() > wounded, "the recast after the rest did not mend the servant");
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aCastWithADeadServantStillStartsTheCooldown(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton first = servant(helper, master, 3, 3, false);
		first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(!first.isAlive(), "the servant survived a lethal blow, so the case proves nothing");
		clearCooldown(helper, master);
		SpellCasting.CastResult raised = SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		boolean resting = master.getCooldowns().isOnCooldown(master.getMainHandItem());
		Skeleton second = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		SpellCasting.CastResult blocked = SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		LOG.info("[BEHAVIOR] test=aCastWithADeadServantStillStartsTheCooldown RESULT raised={} resting={} newServant={} blocked={}",
			raised, resting, second != null && second != first, blocked);
		helper.assertTrue(raised == SpellCasting.CastResult.CAST && second != null && second != first, "no new servant rose: " + raised);
		helper.assertTrue(resting, "a fresh summon did not start the book's rest");
		helper.assertTrue(blocked == SpellCasting.CastResult.COOLDOWN, "a cast inside the rest answered " + blocked);
		second.discard();
		leave(helper, master);
		helper.succeed();
	}

	/**
	 * The book's effective rest, printed for the report. Its cooldown is {@link SummonSkeleton#COOLDOWN_TICKS}
	 * shortened by Quickening and Clear Mind, and the spell is not in {@code FIXED_COOLDOWN}, so a caster with an
	 * RPG cooldown multiplier shortens it again. The guard holds the plain number to what ships.
	 */
	@GameTest(maxTicks = 40)
	public void theSummonBookReportsItsEffectiveRest(final GameTestHelper helper) {
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellModifiers.CastModifiers plainModifiers = SpellModifiers.resolve(new ItemStack(book()), ItemStack.EMPTY, ItemStack.EMPTY);
		int withRpg;
		try {
			RpgBridge.setStats(new RpgBridge.Stats() {
				@Override
				public double power(final net.minecraft.world.entity.player.Player player) {
					return 1.0;
				}

				@Override
				public double cooldown(final net.minecraft.world.entity.player.Player player) {
					return HALVED_COOLDOWN;
				}
			});
			withRpg = RpgBridge.grow(master, "summon_skeleton", plainModifiers).cooldownTicks();
		} finally {
			RpgBridge.setStats(null);
		}
		LOG.info("[BEHAVIOR] test=theSummonBookReportsItsEffectiveRest RESULT baseSeconds={} baseTicks={} plainResolved={} fixed={} fixedSet={} rpgMultiplier={} withRpgTicks={} withRpgSeconds={}",
			SummonSkeleton.COOLDOWN_TICKS / 20, SummonSkeleton.COOLDOWN_TICKS, plainModifiers.cooldownTicks(),
			SpellbookCatalog.FIXED_COOLDOWN.contains("summon_skeleton"), SpellbookCatalog.FIXED_COOLDOWN,
			HALVED_COOLDOWN, withRpg, withRpg / 20);
		helper.assertTrue(plainModifiers.cooldownTicks() == SummonSkeleton.COOLDOWN_TICKS,
			"a plain book resolves to " + plainModifiers.cooldownTicks() + " ticks, not the shipped " + SummonSkeleton.COOLDOWN_TICKS);
		helper.assertTrue(!SpellbookCatalog.FIXED_COOLDOWN.contains("summon_skeleton"),
			"the summon joined FIXED_COOLDOWN, so the enchantments no longer shorten its rest");
		helper.assertTrue(withRpg < plainModifiers.cooldownTicks(),
			"an RPG cooldown multiplier of " + HALVED_COOLDOWN + " left the rest at " + withRpg + " ticks");
		leave(helper, master);
		helper.succeed();
	}

	/** The RPG cooldown multiplier the report measures with: a caster whose Intelligence halves the rest. */
	static final double HALVED_COOLDOWN = 0.5;

	@GameTest(maxTicks = 40)
	public void theExperienceShareIsAQuarter(final GameTestHelper helper) {
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		helper.assertTrue(Math.abs(SummonSkeleton.XP_SHARE - 0.25) < 1.0E-9,
			"the experience share is " + SummonSkeleton.XP_SHARE + ", not the quarter the spec names");
		master.experienceLevel = 0;
		master.totalExperience = 0;
		int served = 40;
		int expected = (int) Math.floor(served * SummonSkeleton.XP_SHARE);
		SummonSkeleton.gain(servant, master, served);
		LOG.info("[BEHAVIOR] test=theExperienceShareIsAQuarter RESULT share={} served={} expected={} paid={} killShare={}",
			SummonSkeleton.XP_SHARE, served, expected, master.totalExperience, SummonSkeleton.KILL_XP_SHARE);
		helper.assertTrue(master.totalExperience == expected,
			"a servant gain of " + served + " paid its master " + master.totalExperience + ", not " + expected);
		helper.assertTrue(SummonSkeleton.KILL_XP_SHARE == 1.0,
			"the servant no longer takes the whole orb: " + SummonSkeleton.KILL_XP_SHARE);
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void instantHealthMendsTheServantInsteadOfBurningIt(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		float maximum = servant.getMaxHealth();
		servant.setHealth(10.0F);
		// The potion's own path: a Potion of Healing runs HealOrHarmMobEffect.applyInstantaneousEffect, which
		// heals exactly when this flag is false and burns exactly when it is true.
		servant.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 0), master);
		MobEffects.INSTANT_HEALTH.value().applyInstantaneousEffect(helper.getLevel(), servant, servant, servant, 0, INSTANT_HEALTH);
		float gained = servant.getHealth() - 10.0F;
		LOG.info("[BEHAVIOR] test=instantHealthMendsTheServantInsteadOfBurningIt RESULT inverted={} drink={} before=10.0 after={} gained={} max={}",
			servant.isInvertedHealAndHarm(), INSTANT_HEALTH, servant.getHealth(), gained, maximum);
		helper.assertFalse(servant.isInvertedHealAndHarm(), "the servant still reads as an undead body for healing");
		helper.assertTrue(gained > 0.0F, "an instant health drink took " + gained + " from the servant");
		// The control: a wild skeleton reads the other way, so the same drink burns it.
		Skeleton wild = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(wild != null, "the level refused a control skeleton");
		wild.snapTo(servant.getX() + 2.0, servant.getY(), servant.getZ());
		helper.getLevel().addFreshEntity(wild);
		wild.setHealth(10.0F);
		boolean wildInverted = wild.isInvertedHealAndHarm();
		LOG.info("[BEHAVIOR] test=instantHealthMendsTheServantInsteadOfBurningIt RESULT wildInverted={} servantInverted={}",
			wildInverted, servant.isInvertedHealAndHarm());
		helper.assertTrue(wildInverted, "a wild skeleton reads as a healing body too, so the case proves nothing");
		wild.discard();
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void sanctuaryAndRegenerationNeverHurtTheServant(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		servant.setHealth(20.0F);
		float before = servant.getHealth();
		// The zone spell, cast by the master with the servant standing inside it.
		SpellbookItem sanctuary = (SpellbookItem) BuiltInRegistries.ITEM.getValue(
			Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "sanctuary"));
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sanctuary));
		SpellCasting.CastResult cast = SpellCasting.cast(master, sanctuary, InteractionHand.MAIN_HAND);
		helper.assertTrue(cast == SpellCasting.CastResult.CAST && SpellTimers.sanctuaryZones() > 0,
			"no Sanctuary zone stood, so the case proves nothing: " + cast);
		servant.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1), master);
		helper.runAfterDelay(25L, () -> {
			float after = servant.getHealth();
			LOG.info("[BEHAVIOR] test=sanctuaryAndRegenerationNeverHurtTheServant RESULT cast={} zones={} before={} after={} regen={}",
				cast, SpellTimers.sanctuaryZones(), before, after, servant.hasEffect(MobEffects.REGENERATION));
			helper.assertTrue(after >= before, "Sanctuary or regeneration took the servant from " + before + " to " + after);
			servant.discard();
			leave(helper, master);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void aHostilesMagicStillHurtsTheServant(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		servant.setHealth(servant.getMaxHealth());
		float before = servant.getHealth();
		Mob caster = helper.spawnMob(EntityTypes.EVOKER, 6, 1, 5).withNoFreeWill().spawn();
		servant.hurtServer(helper.getLevel(), helper.getLevel().damageSources().indirectMagic(caster, caster), HOSTILE_MAGIC);
		float lost = before - servant.getHealth();
		LOG.info("[BEHAVIOR] test=aHostilesMagicStillHurtsTheServant RESULT before={} after={} lost={} magic={}",
			before, servant.getHealth(), lost, HOSTILE_MAGIC);
		helper.assertTrue(lost > 0.0F, "a hostile caster's magic took nothing from the servant");
		servant.discard();
		caster.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anAwakenedCastBringsTheServantItsOwnHorse(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, true);
		SkeletonHorse horse = SummonSkeleton.horse(servant);
		LOG.info("[BEHAVIOR] test=anAwakenedCastBringsTheServantItsOwnHorse RESULT horse={} max={} riding={} expectedMax={}",
			horse == null ? "none" : horse.getUUID(), horse == null ? 0.0 : horse.getMaxHealth(), servant.isPassenger(), SummonSkeleton.HORSE_MAX_HEALTH);
		helper.assertTrue(horse != null, "an awakened cast brought no horse");
		LOG.info("[BEHAVIOR] test=anAwakenedCastBringsTheServantItsOwnHorse RESULT horse={} maxHealth={} health={} riding={} expectedMax={}",
			horse.getUUID(), horse.getMaxHealth(), horse.getHealth(), servant.isPassenger(), SummonSkeleton.HORSE_MAX_HEALTH);
		helper.assertTrue(horse.getMaxHealth() == (float) SummonSkeleton.HORSE_MAX_HEALTH,
			"the horse reports " + horse.getMaxHealth() + " maximum health, not " + SummonSkeleton.HORSE_MAX_HEALTH);
		helper.assertTrue(horse.getHealth() == (float) SummonSkeleton.HORSE_MAX_HEALTH,
			"the horse carries " + horse.getHealth() + " health, not " + SummonSkeleton.HORSE_MAX_HEALTH);
		helper.assertTrue(servant.isPassenger() && servant.getVehicle() == horse, "the servant does not ride its own horse");
		helper.assertTrue(SkeletonMenu.horseArmor(new ItemStack(Items.DIAMOND_HORSE_ARMOR)),
			"the mount's own slot refuses diamond horse armor");
		horse.discard();
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aPlainCastBringsNoHorse(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, false);
		LOG.info("[BEHAVIOR] test=aPlainCastBringsNoHorse RESULT horse={} riding={}",
			SummonSkeleton.horse(servant) == null ? "none" : "present", servant.isPassenger());
		helper.assertTrue(SummonSkeleton.horse(servant) == null, "a plain cast brought a horse");
		helper.assertFalse(servant.isPassenger(), "a plain servant rides something");
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void theHorseTakesDiamondArmorAndKeepsItAcrossTheServantsDeath(final GameTestHelper helper) {
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3, true);
		SkeletonHorse horse = SummonSkeleton.horse(servant);
		helper.assertTrue(horse != null, "the awakened cast brought no horse, so the case proves nothing");
		// The armor rides the mount's own body slot, and the mount's pack takes the stacks with it.
		horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
		setHorseStack(horse, 0, new ItemStack(Items.DIAMOND, 3));
		setHorseStack(horse, 1, new ItemStack(Items.GOLDEN_APPLE, 2));
		SummonInventoryStore.saveHorse(horse);
		String armorBefore = horse.getItemBySlot(EquipmentSlot.BODY).toString();
		helper.assertTrue(horse.getItemBySlot(EquipmentSlot.BODY).is(Items.DIAMOND_HORSE_ARMOR),
			"the mount's body slot holds " + armorBefore);
		servant.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(!servant.isAlive(), "the servant survived a lethal blow");
		// The resummon is awakened too, so the flag rides the stack in the master's hand.
		master.setItemInHand(InteractionHand.MAIN_HAND, awakenedBook());
		clearCooldown(helper, master);
		SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		Skeleton second = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		SkeletonHorse next = second == null ? null : SummonSkeleton.horse(second);
		String armor = next == null ? "none" : next.getItemBySlot(EquipmentSlot.BODY).toString();
		int diamonds = next == null ? -1 : horseStack(next, 0).getCount();
		int apples = next == null ? -1 : horseStack(next, 1).getCount();
		LOG.info("[BEHAVIOR] test=theHorseTakesDiamondArmorAndKeepsItAcrossTheServantsDeath RESULT newServant={} newHorse={} armor={} diamonds={} apples={}",
			second != null && second != servant, next != null && next != horse, armor, diamonds, apples);
		helper.assertTrue(second != null && second != servant, "no new servant rose");
		helper.assertTrue(next != null, "the awakened resummon brought no horse");
		helper.assertTrue(next != horse, "the resummon reused the dead servant's horse");
		helper.assertTrue(next.getItemBySlot(EquipmentSlot.BODY).is(Items.DIAMOND_HORSE_ARMOR), "the new horse wears " + armor);
		helper.assertTrue(diamonds == 3 && apples == 2, "the mount's pack came back with " + diamonds + " diamonds and " + apples + " apples");
		next.discard();
		second.discard();
		leave(helper, master);
		helper.succeed();
	}

	/** Puts one stack into the mount's own pack, which the accessor mixin opens to this mod. */
	static void setHorseStack(final SkeletonHorse horse, final int slot, final ItemStack stack) {
		((AbstractHorseInventory) horse).stellaSpells$inventory().setItem(slot, stack);
	}

	static ItemStack horseStack(final SkeletonHorse horse, final int slot) {
		return ((AbstractHorseInventory) horse).stellaSpells$inventory().getItem(slot);
	}

	/** True when the follow goals would walk this servant home: its master is past the follow distance, ground clear. */
	static boolean companionFollows(final Skeleton servant, final ServerPlayer master) {
		return !SummonSkeleton.clearing(servant, master)
			&& servant.distanceToSqr(master) > SummonSkeleton.FOLLOW_DISTANCE * SummonSkeleton.FOLLOW_DISTANCE;
	}

	/** A foe this far from the master along the master's yaw, so both the master and the servant see it. */
	private static Mob foe(final GameTestHelper helper, final ServerPlayer master, final double distance, final float yaw) {
		Vec3 look = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 at = master.position().add(look.x * distance, 0.0, look.z * distance);
		Mob mob = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	/** Raises a servant, awakened or plain, and stands it where the case needs it. */
	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master, final int x, final int z, final boolean awakened) {
		// The awakened flag rides the stack in the caster's hand, which is the stack the cast reads.
		master.setItemInHand(InteractionHand.MAIN_HAND, awakened ? awakenedBook() : new ItemStack(book()));
		clearCooldown(helper, master);
		SpellCasting.cast(master, book(), InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the summon raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		servant.snapTo(at.x, at.y, at.z, servant.getYRot(), 0.0F);
		servant.setNoAi(false);
		return servant;
	}

	/** A book of this spell with the awakened mark, which is all the flag reads. */
	private static ItemStack awakenedBook() {
		ItemStack stack = new ItemStack(book());
		stack.set(Awakening.COUNT, Awakening.CASTS_TO_AWAKEN);
		stack.set(Awakening.AWAKENED, Unit.INSTANCE);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static SpellbookItem book() {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
	}

	private static void clearCooldown(final GameTestHelper helper, final ServerPlayer master) {
		master.getCooldowns().removeCooldown(master.getCooldowns().getCooldownGroup(master.getMainHandItem()));
	}

	/** A player facing along +Z, which is the line its own sight and its servant's shared sight follow. */
	private static final float FACING = 0.0F;

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		return master(helper, x, z, FACING);
	}

	/** A master facing {@code yaw}, which is the line its own sight and its servant's shared sight follow. */
	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z, final float yaw) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), yaw, 0.0F, true);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void floor(final GameTestHelper helper) {
		int span = (int) SummonSkeleton.GUARD_RADIUS + 6;
		for (int x = 0; x <= span; x++) {
			for (int z = 0; z <= 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
