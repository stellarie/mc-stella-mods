package com.stella.learn;

import com.stella.mobs.StellaMobs;
import com.stella.mobs.config.GearConfig;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Mobs that stand near each other form a formation, take their places against a player, share sight, and never turn on each other. */
public class FormationGameTest {
	private static final int LONG = 600;
	private static final double HURT_SHARE = 0.15;
	private static final double FALL_BACK_BLOCKS = 6.0;
	private static final double CLOSING_DISTANCE = 9.0;
	private static final String ARCHER_CONFIG = """
		{
			"version": 1,
			"entities": { "minecraft:skeleton": { "slots": { "mainhand": { "item": "minecraft:bow" } } } },
			"roles": { "minecraft:skeleton": { "role": "ranged", "near": 5.0, "far": 15.0 } },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	private static void archerConfig(final GameTestHelper helper) {
		GearConfig previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(ARCHER_CONFIG));
		helper.runBeforeTestEnd(() -> GearConfigStore.set(previous));
	}

	/** A mob out of level scaling, since a far arena sets its level in the thousands and one arrow would then kill. */
	private static Mob zombie(final GameTestHelper helper, final int x, final int z) {
		return StanceFixtures.unscaled(helper, EntityTypes.ZOMBIE, x, z);
	}

	/** The bow goes on before the skeleton joins the level, because the mobs mod reads the weapon then. */
	private static Mob skeleton(final GameTestHelper helper, final int x, final int z) {
		Mob mob = EntityTypes.SKELETON.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		mob.setPersistenceRequired();
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(x, LearnArena.SPAWN_Y, z)));
		mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.getLevel().addFreshEntity(mob);
		return mob;
	}

	private static boolean sameFormation(final Mob first, final Mob second) {
		Formation formation = Formations.of(first);
		return formation != null && formation == Formations.of(second);
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void fourMobsFormOneFormationAndAFifthThatWalksInJoins(final GameTestHelper helper) {
		LearnArena.floor(helper);
		archerConfig(helper);
		Mob shield = zombie(helper, 10, 10);
		Mob sword = zombie(helper, 14, 10);
		Mob firstArcher = skeleton(helper, 10, 14);
		Mob secondArcher = skeleton(helper, 14, 14);
		Mob fifth = zombie(helper, 46, 46);
		shield.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		List<Mob> four = List.of(shield, sword, firstArcher, secondArcher);
		LearnArena.whenReady(helper, List.of(shield, sword, firstArcher, secondArcher, fifth), () -> {
			Formations.scan(helper.getLevel());
			boolean formed = four.stream().allMatch(mob -> sameFormation(shield, mob));
			boolean outside = Formations.of(fifth) == null || !Formations.of(fifth).contains(shield);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=fourMobsFormOneFormationAndAFifthThatWalksInJoins RESULT formed={} fifthOutside={}", formed, outside);
			helper.assertTrue(formed, "the four mobs did not share one formation");
			helper.assertTrue(outside, "the fifth mob, 45 blocks off, already belonged");
			Vec3 at = helper.absoluteVec(new Vec3(24, LearnArena.SPAWN_Y, 24));
			fifth.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
			Formations.scan(helper.getLevel());
			boolean joined = sameFormation(shield, fifth);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=fourMobsFormOneFormationAndAFifthThatWalksInJoins RESULT joined={}", joined);
			helper.assertTrue(joined, "the fifth mob did not join once it was in the area");
			helper.assertTrue(four.stream().allMatch(mob -> sameFormation(shield, mob)), "the four lost their formation when the fifth joined");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theShieldBearerStandsNearerThePlayerThanTheArchers(final GameTestHelper helper) {
		LearnArena.floor(helper);
		archerConfig(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 4.5);
		Mob shield = zombie(helper, 24, 32);
		Mob sword = zombie(helper, 24, 35);
		Mob firstArcher = skeleton(helper, 22, 11);
		Mob secondArcher = skeleton(helper, 27, 11);
		shield.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		List<Mob> all = List.of(shield, sword, firstArcher, secondArcher);
		all.forEach(mob -> LearnArena.keepTargeting(helper, mob, player));
		LearnArena.whenReady(helper, List.of(player, shield, sword, firstArcher, secondArcher), () -> {
			// The archers start nearer the player than the bearer. Left alone they hold near 6 blocks while the bearer comes in.
			// Sampling when the bearer has closed to 9 blocks finds them in front of it unless the formation sent them back.
			LearnArena.pollUntil(helper, () -> Formations.flatDistance(shield.position(), player.position()) <= CLOSING_DISTANCE, LONG - 40, () -> {
				double front = Formations.flatDistance(shield.position(), player.position());
				double first = Formations.flatDistance(firstArcher.position(), player.position());
				double second = Formations.flatDistance(secondArcher.position(), player.position());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=theShieldBearerStandsNearerThePlayerThanTheArchers RESULT shield={} archers={},{}", front, first, second);
				helper.assertTrue(front < first && front < second, "an archer stood nearer the player than the shield bearer: " + front + " against " + first + " and " + second);
				helper.succeed();
			}, () -> "the shield bearer never closed in");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobAtTwentyPercentHealthWalksTowardAHealthierAlly(final GameTestHelper helper) {
		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 44.5);
		Mob hurt = zombie(helper, 24, 24);
		Mob firstAlly = zombie(helper, 20, 6);
		Mob secondAlly = zombie(helper, 28, 6);
		// Allies that stand still: the player is on the far side of the hurt mob, so only a fall back moves it toward them.
		firstAlly.setNoAi(true);
		secondAlly.setNoAi(true);
		LearnArena.keepTargeting(helper, hurt, player);
		LearnArena.whenReady(helper, List.of(player, hurt, firstAlly, secondAlly), () -> {
			hurt.setHealth((float) (HURT_SHARE * hurt.getMaxHealth()));
			double startZ = hurt.getZ();
			double startToPlayer = hurt.distanceTo(player);
			LearnArena.pollUntil(helper, () -> startZ - hurt.getZ() >= FALL_BACK_BLOCKS, LONG - 40, () -> {
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=aMobAtTwentyPercentHealthWalksTowardAHealthierAlly RESULT moved={} playerDistanceBefore={} playerDistanceAfter={}",
					startZ - hurt.getZ(), startToPlayer, hurt.distanceTo(player)
				);
				helper.assertTrue(hurt.distanceTo(player) > startToPlayer, "the hurt mob went toward the player");
				helper.succeed();
			}, () -> "the hurt mob never walked toward its ally, z moved " + (startZ - hurt.getZ()));
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobHitByAnAlliesArrowDoesNotTargetTheArcher(final GameTestHelper helper) {
		LearnArena.floor(helper);
		archerConfig(helper);
		Mob victim = zombie(helper, 20, 20);
		Mob bystander = zombie(helper, 24, 20);
		Mob archer = skeleton(helper, 22, 26);
		boolean[] armed = {false};
		boolean[] turned = {false};
		// Any tick on which either zombie holds the archer as a target counts, however short.
		helper.onEachTick(() -> turned[0] |= armed[0] && (victim.getTarget() == archer || bystander.getTarget() == archer));
		LearnArena.whenReady(helper, List.of(victim, bystander, archer), () -> {
			Arrow arrow = EntityTypes.ARROW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
			arrow.setOwner(archer);
			float before = victim.getHealth();
			armed[0] = true;
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, archer), 4.0F);
			helper.assertTrue(victim.getHealth() < before, "the arrow did no damage, so friendly fire was not accepted");
			// The retaliation goal and the ally defence read the hurt mob on their next pass.
			helper.runAfterDelay(30L, () -> {
				StellaLearn.LOGGER.info(
					"[BEHAVIOR] test=aMobHitByAnAlliesArrowDoesNotTargetTheArcher RESULT everTargeted={} victimTarget={} bystanderTarget={} lastHurtBy={}",
					turned[0], victim.getTarget(), bystander.getTarget(), victim.getLastHurtByMob()
				);
				helper.assertTrue(victim.getTarget() != archer, "the hurt mob targets the archer");
				helper.assertTrue(bystander.getTarget() != archer, "the mob beside the hurt one targets the archer");
				helper.assertTrue(victim.getLastHurtByMob() != archer, "the hurt mob remembers the archer as its attacker");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobThatCannotSeeThePlayerLearnsOfItFromMobsThatSeeEachOther(final GameTestHelper helper) {
		LearnArena.floor(helper);
		// One wall hides the player from the second and third mob. A second wall hides the first mob from the third.
		for (int y = 1; y <= 6; y++) {
			for (int z = 22; z <= 26; z++) {
				helper.setBlock(16, y, z, Blocks.STONE);
			}

			for (int z = 26; z <= 34; z++) {
				helper.setBlock(28, y, z, Blocks.STONE);
			}
		}

		ServerPlayer player = LearnArena.player(helper, 10.5, 24.5);
		Mob seer = zombie(helper, 16, 34);
		Mob second = zombie(helper, 24, 24);
		Mob third = zombie(helper, 34, 24);
		// Without AI the seer picks no target and stays where the walls expect it.
		seer.setNoAi(true);
		LearnArena.whenReady(helper, List.of(player, seer, second, third), () -> {
			helper.assertTrue(seer.hasLineOfSight(player), "the first mob does not see the player");
			helper.assertTrue(!second.hasLineOfSight(player) && !third.hasLineOfSight(player), "a mob behind the wall sees the player");
			helper.assertTrue(second.hasLineOfSight(seer) && third.hasLineOfSight(second) && !third.hasLineOfSight(seer), "the chain of sight is not as built");
			Formations.scan(helper.getLevel());
			StellaLearn.LOGGER.info(
				"[BEHAVIOR] test=aMobThatCannotSeeThePlayerLearnsOfItFromMobsThatSeeEachOther RESULT secondTarget={} thirdTarget={} thirdSeen={}",
				second.getTarget(), third.getTarget(), SharedSight.seenSpot(third)
			);
			helper.assertTrue(second.getTarget() == player, "the mob that sees the seer did not get the player as target");
			helper.assertTrue(third.getTarget() == player, "the mob at the end of the chain did not get the player as target");
			helper.assertTrue(SharedSight.seenSpot(third) != null, "the end of the chain kept no spot for the player");
			helper.succeed();
		});
	}
}
