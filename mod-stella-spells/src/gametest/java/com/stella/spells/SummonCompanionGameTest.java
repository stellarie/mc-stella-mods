package com.stella.spells;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The summoned skeleton as a companion: its thirty second book, the recall that moves it, its gear and
 * growth across a death, its follow, its two target modes, the friends a village spares, a heal that
 * leaves it whole, and the experience it earns and shares.
 */
public class SummonCompanionGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.summoncompanion");
	private static final String PATROL_TAG = "mc-stella-villages.patrol";
	private static final String WITCH_TAG = "mc-stella-villages.village_witch";
	private static final String FRIENDLY_PATROL_TAG = "mod-stella-mobs.friendly_patrol";
	private static final String HARMLESS_TAG = "mod-stella-mobs.harmless";
	private static final float MAGIC_HIT = 4.0F;
	private static final float PREY_HEALTH = 8.0F;
	private static final int KILL_TICKS = 200;

	@GameTest(maxTicks = 60)
	public void theSummonBookRestsThirtySeconds(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theSummonBookRestsThirtySeconds");
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		ItemStack held = new ItemStack(book);
		master.setItemInHand(InteractionHand.MAIN_HAND, held);
		int seconds = SummonSkeleton.COOLDOWN_TICKS / 20;
		SpellCasting.CastResult first = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		boolean resting = master.getCooldowns().isOnCooldown(held);
		Skeleton served = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(served != null, "the first cast raised no servant, so the case proves nothing");
		Vec3 far = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		master.teleportTo(far.x, far.y, far.z);
		SpellCasting.CastResult recall = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		boolean back = served.distanceTo(master) <= SummonSkeleton.FOLLOW_DISTANCE;
		served.discard();
		SpellCasting.CastResult blocked = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		boolean raised = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID()) != null;
		LOG.info("[BEHAVIOR] test=theSummonBookRestsThirtySeconds RESULT seconds={} first={} resting={} recall={} back={} blocked={} raised={}",
			seconds, first, resting, recall, back, blocked, raised);
		helper.assertTrue(seconds == 30, "the book's cooldown is " + seconds + " seconds, not 30");
		helper.assertTrue(first == SpellCasting.CastResult.CAST && resting, "the first cast was " + first + " with resting=" + resting);
		helper.assertTrue(!raised && blocked == SpellCasting.CastResult.COOLDOWN,
			"a cast inside the rest answered " + blocked + " and raised=" + raised);
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aRecastMovesTheLivingServantRatherThanRaisingAnother(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aRecastMovesTheLivingServantRatherThanRaisingAnother");
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton first = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(first != null, "the first cast raised no servant, so the case proves nothing");
		UUID raised = first.getUUID();
		Vec3 far = helper.absoluteVec(new Vec3(6.5, 1.0, 6.5));
		master.teleportTo(far.x, far.y, far.z);
		SpellCasting.CastResult second = SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton after = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		// The master's bond is the single servant it holds: the recast must leave it on the same one.
		UUID bonded = master.getAttached(SummonSkeleton.BOND);
		double distance = after == null ? -1.0 : after.distanceTo(master);
		LOG.info("[BEHAVIOR] test=aRecastMovesTheLivingServantRatherThanRaisingAnother RESULT result={} same={} bondKept={} distance={}",
			second, after == first, raised.equals(bonded), distance);
		helper.assertTrue(second == SpellCasting.CastResult.CAST && after == first,
			"the recast answered " + second + " and same=" + (after == first));
		helper.assertTrue(raised.equals(bonded), "the recast moved the master's bond to " + bonded);
		helper.assertTrue(distance <= SummonSkeleton.FOLLOW_DISTANCE, "the servant stands " + distance + " from its master");
		if (after != null) {
			after.discard();
		}
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aDeadServantKeepsItsGearAndWearsItAgain(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aDeadServantKeepsItsGearAndWearsItAgain");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton first = servant(helper, master, 3, 3);
		SummonSkeleton.equip(first, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		Vec3 grave = first.position();
		// The drop chance is what keeps the gear on a servant a player strikes down.
		helper.assertTrue(first.getDropChances().byEquipment(EquipmentSlot.HEAD) == 0.0F,
			"the servant's gear can drop: chance=" + first.getDropChances().byEquipment(EquipmentSlot.HEAD));
		first.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		helper.assertTrue(!first.isAlive(), "the servant survived a lethal blow, so the case proves nothing");
		Skeleton wild = (Skeleton) mob(helper, master, EntityTypes.SKELETON, 5.0, 5.0);
		wild.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		wild.setGuaranteedDrop(EquipmentSlot.HEAD);
		Vec3 wildGrave = wild.position();
		wild.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000.0F);
		clear(helper, master);
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton second = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(second != null && second != first, "the second cast raised no new servant");
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
		second.teleportTo(at.x, at.y, at.z);
		helper.runAfterDelay(10L, () -> {
			long dropped = dropped(helper, grave);
			long wildDropped = dropped(helper, wildGrave);
			LOG.info("[BEHAVIOR] test=aDeadServantKeepsItsGearAndWearsItAgain RESULT helmet={} dropped={} wildDropped={}",
				second.getItemBySlot(EquipmentSlot.HEAD), dropped, wildDropped);
			helper.assertTrue(wildDropped > 0, "the wild skeleton never dropped its helmet, so the case proves nothing");
			helper.assertTrue(dropped == 0, "the dead servant dropped " + dropped + " of its gear");
			helper.assertTrue(second.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
				"the next servant wears " + second.getItemBySlot(EquipmentSlot.HEAD));
			second.discard();
			leave(helper, master);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void theServantWalksToStayNearItsMaster(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theServantWalksToStayNearItsMaster");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 7, 7);
		helper.assertTrue(servant != null, "the summon raised no servant, so the case proves nothing");
		double start = servant.distanceTo(master);
		helper.assertTrue(start > SummonSkeleton.FOLLOW_DISTANCE,
			"the servant started " + start + " from its master, so the case proves nothing");
		final int[] plans = {0};
		final boolean[] everMoving = {false};
		helper.onEachTick(() -> {
			if (servant.getNavigation().isInProgress()) {
				everMoving[0] = true;
			}
		});
		helper.runAfterDelay(5L, () -> {
			plans[0] = servant.getNavigation().moveTo(master, 1.1) ? 1 : 0;
		});
		helper.runAfterDelay(190L, () -> {
			boolean planned = servant.getNavigation().moveTo(master, 1.1);
			LOG.info("[BEHAVIOR] test=theServantWalksToStayNearItsMaster RESULT start={} now={} ticks={} everMoving={} earlyPlan={} latePlan={} masterGround={} servantGround={}",
				start, servant.distanceTo(master), servant.tickCount, everMoving[0], plans[0], planned,
				master.onGround(), servant.onGround());
			helper.assertTrue(everMoving[0], "the servant never moved: earlyPlan=" + plans[0] + " latePlan=" + planned
				+ " servant=" + servant.position() + " master=" + master.position());
			helper.assertTrue(servant.distanceTo(master) < start,
				"the servant ended " + servant.distanceTo(master) + " from its master, no closer than " + start);
			servant.discard();
			leave(helper, master);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void theModePicksBetweenSearchingAndMirroring(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theModePicksBetweenSearchingAndMirroring");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		Skeleton near = (Skeleton) mob(helper, master, EntityTypes.SKELETON, 4.5, 0.5);
		Skeleton aimed = (Skeleton) mob(helper, master, EntityTypes.SKELETON, 5.5, 4.5);
		helper.assertTrue(near.distanceTo(master) < aimed.distanceTo(master),
			"the two foes are not ordered by distance, so the case proves nothing");
		master.setLastHurtMob(aimed);
		SummonSkeleton.setTargetMode(master, SummonSkeleton.Mode.SEARCH);
		Mob searched = SummonSkeleton.targetFor(servant, SummonSkeleton.targetMode(master));
		SummonSkeleton.setTargetMode(master, SummonSkeleton.Mode.MIRROR);
		Mob mirrored = SummonSkeleton.targetFor(servant, SummonSkeleton.targetMode(master));
		Mob lastHurt = master.getLastHurtMob() instanceof Mob hurt ? hurt : null;
		LOG.info("[BEHAVIOR] test=theModePicksBetweenSearchingAndMirroring RESULT nearMaster={} nearServant={} aimedMaster={} radius={} searchIsNear={} mirrored={} lastHurtIsAimed={}",
			near.distanceTo(master), near.distanceTo(servant), aimed.distanceTo(master), SummonSkeleton.GUARD_RADIUS,
			searched == near, mirrored == aimed ? "aimed" : mirrored == near ? "near" : String.valueOf(mirrored),
			lastHurt == aimed);
		helper.assertTrue(searched == near, "search mode picked "
			+ (searched == null ? "none" : searched == aimed ? "the master's aim" : searched.getUUID()));
		helper.assertTrue(mirrored == aimed, "mirror mode picked "
			+ (mirrored == null ? "none" : mirrored == near ? "the nearest foe" : mirrored.getUUID()));
		servant.discard();
		near.discard();
		aimed.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void villageGuardsAndWitchesSpareTheServant(final GameTestHelper helper) {
		SpellTestLog.track(helper, "villageGuardsAndWitchesSpareTheServant");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		Skeleton wild = wild(helper, 5, 5);
		Pillager duke = (Pillager) mob(helper, master, EntityTypes.PILLAGER, 3.0, -2.0);
		duke.addTag(PATROL_TAG);
		Witch witch = (Witch) mob(helper, master, EntityTypes.WITCH, 2.0, -2.0);
		witch.addTag(WITCH_TAG);
		// The villages mod excludes a target that wears either mark the servant now carries.
		boolean marksWild = wild.entityTags().contains(PATROL_TAG) || wild.entityTags().contains(FRIENDLY_PATROL_TAG);
		int friendMarks = 0;
		for (String tag : SummonSkeleton.FRIEND_TAGS) {
			if (servant.entityTags().contains(tag)) {
				friendMarks++;
			}
		}
		int marks = friendMarks;
		boolean servantSparesDuke = !MobSpells.isFoeOf(servant, duke);
		boolean servantSparesWitch = !MobSpells.isFoeOf(servant, witch);
		boolean servantServes = SummonSkeleton.isServant(servant, master);
		boolean wildIsFoe = MobSpells.isFoeOf(witch, wild);
		LOG.info("[BEHAVIOR] test=villageGuardsAndWitchesSpareTheServant RESULT marks={}/{} markedWild={} dukeAttacks={} witchAttacks={} servantServes={} servantSparesDuke={} servantSparesWitch={} servantTarget={} wildIsFoe={}",
			marks, SummonSkeleton.FRIEND_TAGS.length, marksWild, duke.canAttack(servant), witch.canAttack(servant),
			servantServes, servantSparesDuke, servantSparesWitch,
			servant.getTarget() == witch ? "witch" : servant.getTarget() == duke ? "duke" : String.valueOf(servant.getTarget()),
			wildIsFoe);
		helper.assertTrue(marks == SummonSkeleton.FRIEND_TAGS.length && servantServes,
			"the servant wears " + marks + " of the " + SummonSkeleton.FRIEND_TAGS.length + " friend marks a patrol reads");
		helper.assertTrue(!marksWild, "the control skeleton wears a friend mark, so the case proves nothing");
		helper.assertTrue(wildIsFoe, "a village witch takes no wild skeleton either, so the case proves nothing");
		helper.assertTrue(servantSparesDuke && servantSparesWitch,
			"the servant counts a village guard or witch as its foe");
		servant.discard();
		wild.discard();
		duke.discard();
		witch.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aHealingSpellLeavesTheServantWhole(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aHealingSpellLeavesTheServantWhole");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 2, 2);
		Skeleton wild = wild(helper, 1, 2);
		servant.setHealth(servant.getMaxHealth());
		master.setHealth(2.0F);
		SpellbookItem bloom = (SpellbookItem) BuiltInRegistries.ITEM.getValue(
			Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "life_bloom"));
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(bloom));
		float before = servant.getHealth();
		float masterBefore = master.getHealth();
		SpellCasting.CastResult cast = SpellCasting.cast(master, bloom, InteractionHand.MAIN_HAND);
		float after = servant.getHealth();
		float masterAfter = master.getHealth();
		float wildBefore = wild.getHealth();
		wild.hurtServer(helper.getLevel(), master.damageSources().indirectMagic(master, master), MAGIC_HIT);
		float wildAfter = wild.getHealth();
		boolean wildHurt = wildBefore - wildAfter > 0.0F;
		LOG.info("[BEHAVIOR] test=aHealingSpellLeavesTheServantWhole RESULT cast={} servantBefore={} servantAfter={} masterBefore={} masterAfter={} wildHurt={}",
			cast, before, after, masterBefore, masterAfter, wildHurt);
		helper.assertTrue(cast == SpellCasting.CastResult.CAST && masterAfter > masterBefore,
			"the heal did not land on the caster: " + cast + " " + masterBefore + " -> " + masterAfter);
		helper.assertTrue(wildHurt, "a magic blow leaves a skeleton whole, so the case proves nothing");
		helper.assertTrue(after == before, "the heal took the servant from " + before + " to " + after);
		servant.discard();
		wild.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void theServantLevelsOnHalfAPlayersCurveAndRollsAStat(final GameTestHelper helper) {
		SpellTestLog.track(helper, "theServantLevelsOnHalfAPlayersCurveAndRollsAStat");
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		int needed = SummonSkeleton.xpNeeded(servant);
		int firstStep = SummonSkeleton.VANILLA_LEVEL_XP / 2;
		int before = sum(SummonSkeleton.stats(servant));
		SummonSkeleton.gain(servant, master, needed - 1);
		int nearly = SummonSkeleton.level(servant);
		int mods = statModifiers(servant);
		SummonSkeleton.gain(servant, master, 1);
		int after = sum(SummonSkeleton.stats(servant));
		int secondStep = SummonSkeleton.xpNeeded(servant);
		LOG.info("[BEHAVIOR] test=theServantLevelsOnHalfAPlayersCurveAndRollsAStat RESULT needed={} vanillaHalf={} nearly={} level={} before={} after={} mods={} secondStep={}",
			needed, firstStep, nearly, SummonSkeleton.level(servant), before, after, mods, secondStep);
		helper.assertTrue(needed == firstStep, "the servant's first step is " + needed + ", not half a level's " + firstStep);
		helper.assertTrue(nearly == 0 && mods == 0, "the servant rose or rolled a stat one point early");
		helper.assertTrue(SummonSkeleton.level(servant) == 1, "the servant stayed at level " + SummonSkeleton.level(servant));
		helper.assertTrue(after == before + 1, "one level rolled " + (after - before) + " stat points");
		helper.assertTrue(statModifiers(servant) > 0, "the rolled stat never reached the servant's attributes");
		helper.assertTrue(secondStep > needed, "the second step " + secondStep + " is no longer than the first " + needed);
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void aServantGainPaysTheMasterHalf(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aServantGainPaysTheMasterHalf");
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		master.experienceLevel = 0;
		master.totalExperience = 0;
		int served = 40;
		int expected = (int) Math.floor(served * SummonSkeleton.XP_SHARE);
		SummonSkeleton.gain(servant, master, served);
		int after = master.totalExperience;
		LOG.info("[BEHAVIOR] test=aServantGainPaysTheMasterHalf RESULT served={} expected={} masterBefore=0 masterAfter={}",
			served, expected, after);
		helper.assertTrue(after == expected, "the master gained " + after + " from a servant gain of " + served);
		servant.discard();
		leave(helper, master);
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void aKillFeedsTheServantAndItsMaster(final GameTestHelper helper) {
		SpellTestLog.track(helper, "aKillFeedsTheServantAndItsMaster");
		helper.setTime(18_000L);
		floor(helper);
		ServerPlayer master = master(helper, 1.5, 1.5);
		Skeleton servant = servant(helper, master, 3, 3);
		// The servant fights with the test's own blow, so the kill and its reward are both known.
		servant.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		master.experienceLevel = 0;
		master.totalExperience = 0;
		Mob prey = helper.spawnMob(EntityTypes.ZOMBIE, 5, 1, 5).withNoFreeWill().spawn();
		prey.setNoAi(true);
		prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PREY_HEALTH);
		prey.setHealth(PREY_HEALTH);
		final boolean[] struck = {false};
		helper.onEachTick(() -> {
			if (prey.isAlive() && !struck[0]) {
				struck[0] = true;
				prey.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(servant), (float) PREY_HEALTH + 1.0F);
			}
		});
		int earnedBefore = SummonSkeleton.xp(servant);
		int masterBefore = master.totalExperience;
		helper.runAfterDelay(KILL_TICKS, () -> {
			Skeleton alive = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
			boolean dead = !prey.isAlive();
			int earned = alive == null ? 0 : SummonSkeleton.xp(alive);
			int shared = Math.max(0, earned - earnedBefore);
			int paid = master.totalExperience - masterBefore;
			LOG.info("[BEHAVIOR] test=aKillFeedsTheServantAndItsMaster RESULT struck={} preyDead={} servantXp={} level={} shared={} masterPaid={}",
				struck[0], dead, earned, alive == null ? -1 : SummonSkeleton.level(alive), shared, paid);
			helper.assertTrue(dead, "the servant never struck the foe down");
			helper.assertTrue(shared > 0, "the servant earned no experience from the kill");
			helper.assertTrue(paid == (int) Math.floor(shared * SummonSkeleton.XP_SHARE),
				"a servant gain of " + shared + " paid its master " + paid);
			if (alive != null) {
				alive.discard();
			}
			leave(helper, master);
			helper.succeed();
		});
	}

	/** The village patrol's own search, with the predicate the villages mod installs over it. */
	private static final class PatrolQuarry extends NearestAttackableTargetGoal<Mob> {
		PatrolQuarry(final Mob guard) {
			super(guard, Mob.class, 1, true, false, (target, level) -> target instanceof Enemy
				&& !target.entityTags().contains(PATROL_TAG)
				&& !target.entityTags().contains(FRIENDLY_PATROL_TAG)
				&& !target.entityTags().contains(HARMLESS_TAG));
		}

		private @org.jspecify.annotations.Nullable LivingEntity quarry() {
			this.targetMob = null;
			this.findTarget();
			return this.targetMob;
		}

		private boolean sees(final LivingEntity candidate) {
			return this.getTargetSearchArea(this.getFollowDistance()).contains(candidate.position());
		}
	}

	private static long summons(final GameTestHelper helper, final Skeleton servant) {
		return helper.getLevel().getEntitiesOfClass(Skeleton.class, servant.getBoundingBox().inflate(16.0),
			other -> other.entityTags().contains(SummonSkeleton.SUMMON_TAG)).size();
	}

	private static long dropped(final GameTestHelper helper, final Vec3 at) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
			new net.minecraft.world.phys.AABB(at, at).inflate(2.0),
			item -> item.isAlive() && item.getItem().is(Items.DIAMOND_HELMET)).size();
	}

	private static int sum(final int[] values) {
		int total = 0;
		for (int value : values) {
			total += value;
		}
		return total;
	}

	private static int statModifiers(final Skeleton servant) {
		int total = 0;
		for (var attribute : List.of(Attributes.MAX_HEALTH, Attributes.MOVEMENT_SPEED, Attributes.KNOCKBACK_RESISTANCE)) {
			AttributeInstance instance = servant.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier modifier : instance.getModifiers()) {
				if (modifier.id().getNamespace().equals(Spellbooks.MOD_ID) && modifier.id().getPath().startsWith("summon_stat")) {
					total += 1;
				}
			}
		}
		return total;
	}

	private static void clear(final GameTestHelper helper, final ServerPlayer master) {
		master.getCooldowns().removeCooldown(master.getCooldowns().getCooldownGroup(master.getMainHandItem()));
	}

	private static Skeleton servant(final GameTestHelper helper, final ServerPlayer master, final int x, final int z) {
		SpellbookItem book = book();
		master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(book));
		SpellCasting.cast(master, book, InteractionHand.MAIN_HAND);
		Skeleton servant = SummonSkeleton.find(helper.getLevel().getServer(), master.getUUID());
		helper.assertTrue(servant != null, "the summon raised no servant, so the case proves nothing");
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		servant.teleportTo(at.x, at.y, at.z);
		servant.setNoAi(false);
		return servant;
	}

	private static Skeleton wild(final GameTestHelper helper, final int x, final int z) {
		Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5));
		Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(skeleton != null, "the level refused a wild skeleton");
		skeleton.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		skeleton.setNoAi(true);
		helper.getLevel().addFreshEntity(skeleton);
		return skeleton;
	}

	// Placed by absolute position, as the test level's own structure is far larger than the arena floor.
	private static Mob mob(final GameTestHelper helper, final ServerPlayer master, final double dx, final double dz) {
		return mob(helper, master, EntityTypes.ZOMBIE, dx, dz);
	}

	private static Mob mob(
		final GameTestHelper helper, final ServerPlayer master,
		final net.minecraft.world.entity.EntityType<? extends Mob> type, final double dx, final double dz
	) {
		Vec3 at = master.position().add(dx, 0.0, dz);
		Mob mob = type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
		helper.assertTrue(mob != null, "the level refused a foe");
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		mob.setNoAi(true);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static SpellbookItem book() {
		return (SpellbookItem) BuiltInRegistries.ITEM.getValue(SummonSkeleton.ID);
	}

	private static ServerPlayer master(final GameTestHelper helper, final double x, final double z) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "summoner"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		return player;
	}

	private static void leave(final GameTestHelper helper, final ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static void floor(final GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
