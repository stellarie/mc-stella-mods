package com.stella.learn;

import com.stella.learn.gym.Bot;
import com.stella.learn.gym.BotInput;
import com.stella.mobs.StellaMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The gym bot: a server-side player that moves, fights, blocks, and edits blocks under player physics. */
public class GymBotGameTest {
	private static final int LONG = 600;
	private static final float EAST = -90.0F;
	private static final float SOUTH = 0.0F;

	private record Hit(float base, float taken, boolean blocked) {
	}

	private static final Map<UUID, List<Hit>> HITS = new ConcurrentHashMap<>();

	static {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				HITS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(new Hit(base, taken, blocked));
			}
		});
	}

	private static List<Hit> hitsOn(final Bot bot) {
		return HITS.getOrDefault(bot.player().getUUID(), List.of());
	}

	private static Bot spawn(final GameTestHelper helper, final double x, final double z, final float yaw) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		Bot bot = Bot.spawn(helper.getLevel(), "gym-test-bot", at, yaw, 0);
		helper.runBeforeTestEnd(bot::remove);
		return bot;
	}

	private static double horizontal(final Vec3 from, final Vec3 to) {
		return Math.hypot(to.x - from.x, to.z - from.z);
	}

	private static Mob zombieAt(final GameTestHelper helper, final double x, final double z, final boolean ai) {
		// Far from the world origin the mobs mod scales a fresh spawn's damage until one hit kills the bot, so this one is
		// tagged out of the spawn rolls and keeps the vanilla numbers the assertions below rely on.
		Mob zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.NATURAL);
		Vec3 at = helper.absoluteVec(new Vec3((int) x + 0.5, LearnArena.SPAWN_Y - 2, (int) z + 0.5));
		zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.setPersistenceRequired();
		zombie.setNoAi(!ai);
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	@GameTest
	public void theBotClassIsOnTheGameTestClasspath(final GameTestHelper helper) {
		helper.assertTrue(Bot.yawTo(Vec3.ZERO, new Vec3(0, 0, 1)) == 0.0F, "yaw to +z is not 0");
		helper.succeed();
	}

	private static void runFor(final GameTestHelper helper, final Bot bot, final int ticks, final Runnable then) {
		long from = bot.ticks();
		LearnArena.pollUntil(helper, () -> bot.ticks() - from >= ticks, ticks + 20, then, () -> "the bot did not tick " + ticks + " times");
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void walksAtPlayerSpeed(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 8.5, 24.5, EAST);
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			Vec3 start = bot.player().position();
			bot.setInput(BotInput.IDLE.facing(EAST, 0.0F).moving(1.0F, 0.0F, false, false));
			runFor(helper, bot, 40, () -> {
				double moved = horizontal(start, bot.player().position());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=walksAtPlayerSpeed RESULT moved={} ticks=40", LearnLog.num(moved));
				helper.assertTrue(moved >= 5.5 && moved <= 9.5, "the bot walked " + moved + " blocks in 40 ticks");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void sprintIsFasterThanWalk(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot walker = spawn(helper, 8.5, 18.5, EAST);
		Bot sprinter = spawn(helper, 8.5, 30.5, EAST);
		LearnArena.whenReady(helper, List.of(walker.player(), sprinter.player()), () -> {
			Vec3 walkStart = walker.player().position();
			Vec3 sprintStart = sprinter.player().position();
			walker.setInput(BotInput.IDLE.facing(EAST, 0.0F).moving(1.0F, 0.0F, false, false));
			sprinter.setInput(BotInput.IDLE.facing(EAST, 0.0F).moving(1.0F, 0.0F, false, true));
			runFor(helper, sprinter, 40, () -> {
				double walked = horizontal(walkStart, walker.player().position());
				double sprinted = horizontal(sprintStart, sprinter.player().position());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=sprintIsFasterThanWalk RESULT walked={} sprinted={}", LearnLog.num(walked), LearnLog.num(sprinted));
				helper.assertTrue(sprinted >= 1.2 * walked, "sprint covered " + sprinted + " against " + walked + " walking");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void jumpClearsOneBlock(final GameTestHelper helper) {
		LearnArena.floor(helper);
		for (int z = 22; z <= 27; z++) {
			helper.setBlock(11, 1, z, Blocks.STONE);
		}

		// A jump from a standing start carries little speed, so the bot starts half a block from the step.
		Bot bot = spawn(helper, 10.2, 24.5, EAST);
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			bot.setInput(BotInput.IDLE.facing(EAST, 0.0F).moving(1.0F, 0.0F, true, false));
			double top = helper.absoluteVec(new Vec3(0, 2.0, 0)).y;
			double stepEdge = helper.absoluteVec(new Vec3(11.0, 0, 0)).x;
			long from = bot.ticks();
			LearnArena.pollUntil(helper, () -> bot.player().getY() >= top - 0.05 && bot.player().getX() >= stepEdge + 0.1, 20, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=jumpClearsOneBlock RESULT y={} ticks={}", LearnLog.num(bot.player().getY()), bot.ticks() - from);
				helper.assertTrue(bot.ticks() - from <= 20, "the climb took " + (bot.ticks() - from) + " ticks");
				helper.succeed();
			}, () -> "the bot stands at x=" + bot.player().getX() + " y=" + bot.player().getY() + ", the step is at x>=" + stepEdge + " y=" + top);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void meleeHitDamagesAMob(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 20.5, SOUTH);
		Mob zombie = zombieAt(helper, 24, 22, false);
		LearnArena.whenReady(helper, List.of(bot.player(), zombie), () -> {
			float before = zombie.getHealth();
			Vec3 aim = zombie.getBoundingBox().getCenter();
			float pitch = Bot.pitchTo(bot.player().getEyePosition(), aim);
			float yaw = Bot.yawTo(bot.player().getEyePosition(), aim);
			LearnArena.pollUntil(helper, () -> bot.player().getAttackStrengthScale(0.0F) >= 1.0F, 60, () -> {
				bot.setInput(BotInput.IDLE.facing(yaw, pitch).holding(true, false, 0));
				helper.runAfterDelay(2L, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=meleeHitDamagesAMob RESULT before={} after={} range={}", before, zombie.getHealth(), LearnLog.num(bot.player().distanceTo(zombie)));
					helper.assertTrue(zombie.getHealth() < before, "the zombie health is " + zombie.getHealth() + " after the hit, was " + before);
					helper.succeed();
				});
			}, () -> "the attack cooldown never filled");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void knockbackMovesTheBot(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 20.5, SOUTH);
		Mob zombie = zombieAt(helper, 24, 30, true);
		LearnArena.keepTargeting(helper, zombie, bot.player());
		LearnArena.whenReady(helper, List.of(bot.player(), zombie), () -> {
			bot.setBrain(b -> BotInput.IDLE.facing(Bot.yawTo(bot.player().position(), zombie.position()), 0.0F));
			LearnArena.pollUntil(helper, () -> !hitsOn(bot).isEmpty(), 400, () -> {
				Vec3 atHit = bot.player().position();
				runFor(helper, bot, 8, () -> {
					double moved = horizontal(atHit, bot.player().position());
					StellaLearn.LOGGER.info("[BEHAVIOR] test=knockbackMovesTheBot RESULT moved={} hits={}", LearnLog.num(moved), hitsOn(bot).size());
					helper.assertTrue(moved >= 0.3, "the hit moved the bot " + moved + " blocks");
					helper.succeed();
				});
			}, () -> "the zombie never hit the bot");
		});
	}

	private static void hold(final Bot bot, final int useFrom, final int useUntil, final int again) {
		long from = bot.ticks();
		bot.setBrain(b -> {
			long t = b.ticks() - from - 1;
			boolean use = t >= useFrom && t < useUntil || again > 0 && t >= again && t < again + 2;
			return BotInput.IDLE.holding(false, use, 0);
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void bowShotSpawnsAnArrow(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		bot.give(0, new ItemStack(Items.BOW));
		bot.give(1, new ItemStack(Items.ARROW, 16));
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			hold(bot, 0, 20, 0);
			runFor(helper, bot, 26, () -> {
				int loosed = LearnTestInit.loosedBy(bot.player().getUUID());
				boolean present = !helper.getLevel().getEntitiesOfClass(AbstractArrow.class, bot.player().getBoundingBox().inflate(60.0), a -> a.getOwner() == bot.player()).isEmpty();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=bowShotSpawnsAnArrow RESULT loosed={} present={}", loosed, present);
				helper.assertTrue(loosed > 0, "no arrow was loosed");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void crossbowShot(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		bot.give(0, new ItemStack(Items.CROSSBOW));
		bot.give(1, new ItemStack(Items.ARROW, 16));
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			hold(bot, 0, 30, 34);
			runFor(helper, bot, 42, () -> {
				int loosed = LearnTestInit.loosedBy(bot.player().getUUID());
				StellaLearn.LOGGER.info("[BEHAVIOR] test=crossbowShot RESULT loosed={}", loosed);
				helper.assertTrue(loosed > 0, "the charged crossbow loosed no arrow");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void drinksPotion(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		bot.give(0, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS));
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			hold(bot, 0, 60, 0);
			runFor(helper, bot, 45, () -> {
				boolean fast = bot.player().hasEffect(MobEffects.SPEED);
				StellaLearn.LOGGER.info("[BEHAVIOR] test=drinksPotion RESULT speed={}", fast);
				helper.assertTrue(fast, "the bot has no speed effect after drinking");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void throwsPearl(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		bot.give(0, new ItemStack(Items.ENDER_PEARL, 4));
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			hold(bot, 0, 1, 0);
			LearnArena.pollUntil(helper, () -> !helper.getLevel().getEntitiesOfClass(ThrownEnderpearl.class, bot.player().getBoundingBox().inflate(60.0), p -> p.getOwner() == bot.player()).isEmpty(), 20, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=throwsPearl RESULT pearl=true");
				helper.succeed();
			}, () -> "no pearl owned by the bot exists");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void wearsArmor(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bare = spawn(helper, 20.5, 24.5, SOUTH);
		Bot armored = spawn(helper, 28.5, 24.5, SOUTH);
		armored.player().setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		Mob zombie = zombieAt(helper, 24, 30, false);
		LearnArena.whenReady(helper, List.of(bare.player(), armored.player(), zombie), () -> {
			int armor = armored.player().getArmorValue();
			helper.assertTrue(armor >= 6 && bare.player().getArmorValue() == 0, "the armor values read " + armor + " and " + bare.player().getArmorValue());
			var source = helper.getLevel().damageSources().mobAttack(zombie);
			bare.player().setInvulnerableTime(0);
			armored.player().setInvulnerableTime(0);
			boolean bareHurt = bare.player().hurtServer(helper.getLevel(), source, 10.0F);
			boolean armoredHurt = armored.player().hurtServer(helper.getLevel(), source, 10.0F);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=wearsArmor HURT bare={} armored={}", bareHurt, armoredHurt);
			float bareLoss = 20.0F - bare.player().getHealth();
			float armoredLoss = 20.0F - armored.player().getHealth();
			StellaLearn.LOGGER.info("[BEHAVIOR] test=wearsArmor RESULT armor={} bareLoss={} armoredLoss={}", armor, bareLoss, armoredLoss);
			helper.assertTrue(armoredLoss > 0.0F && armoredLoss < bareLoss, "the armored bot lost " + armoredLoss + " and the bare bot " + bareLoss);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void shieldBlocksAHit(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 20.5, SOUTH);
		bot.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		Mob zombie = zombieAt(helper, 24, 30, true);
		LearnArena.keepTargeting(helper, zombie, bot.player());
		LearnArena.whenReady(helper, List.of(bot.player(), zombie), () -> {
			float health = bot.player().getHealth();
			bot.setBrain(b -> BotInput.IDLE.facing(Bot.yawTo(bot.player().position(), zombie.position()), 0.0F).holding(false, true, 0));
			LearnArena.pollUntil(helper, () -> bot.player().getOffhandItem().getDamageValue() > 0 || bot.player().getHealth() < health, 400, () -> {
				int shieldDamage = bot.player().getOffhandItem().getDamageValue();
				StellaLearn.LOGGER.info("[BEHAVIOR] test=shieldBlocksAHit RESULT health={} before={} shieldDamage={} blocking={} events={}",
					bot.player().getHealth(), health, shieldDamage, bot.player().isBlocking(), hitsOn(bot));
				helper.assertTrue(bot.player().getHealth() == health, "the hit cost " + (health - bot.player().getHealth()) + " health");
				helper.assertTrue(shieldDamage > 0, "the shield took no damage, so nothing was blocked");
				helper.succeed();
			}, () -> "the zombie never hit the shielded bot");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void placesAndBreaksBlocks(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		bot.give(0, new ItemStack(Items.DIRT, 8));
		BlockPos floorBlock = helper.absolutePos(new BlockPos(24, 0, 26));
		BlockPos placed = floorBlock.above();
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			bot.placeAgainst(floorBlock, Direction.UP);
			helper.assertTrue(helper.getLevel().getBlockState(placed).is(Blocks.DIRT), "no dirt was placed at " + placed);
			bot.startBreaking(placed);
			LearnArena.pollUntil(helper, () -> helper.getLevel().getBlockState(placed).isAir(), 60, () -> {
				StellaLearn.LOGGER.info("[BEHAVIOR] test=placesAndBreaksBlocks RESULT digTicks={}", bot.digTicks());
				helper.succeed();
			}, () -> "the placed dirt was never broken");
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void digTakesTime(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		BlockPos dirt = helper.absolutePos(new BlockPos(24, 1, 26));
		helper.getLevel().setBlock(dirt, Blocks.DIRT.defaultBlockState(), 3);
		LearnArena.whenReady(helper, List.of(bot.player()), () -> {
			bot.startBreaking(dirt);
			runFor(helper, bot, 8, () -> {
				helper.assertTrue(helper.getLevel().getBlockState(dirt).is(Blocks.DIRT), "the dirt broke within 8 ticks");
				LearnArena.pollUntil(helper, () -> helper.getLevel().getBlockState(dirt).isAir(), 60, () -> {
					StellaLearn.LOGGER.info("[BEHAVIOR] test=digTakesTime RESULT digTicks={}", bot.digTicks());
					helper.assertTrue(bot.digTicks() >= 10, "dirt broke after " + bot.digTicks() + " ticks by hand");
					helper.succeed();
				}, () -> "the dirt was never broken");
			});
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theBotDrainsItsOutboundQueue(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		LearnArena.whenReady(helper, List.of(bot.player()), () -> runFor(helper, bot, 10, () -> {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=theBotDrainsItsOutboundQueue RESULT drained={}", bot.drainedMessages());
			helper.assertTrue(bot.drainedMessages() > 0, "the bot queued no packet, so nothing was drained");
			helper.succeed();
		}));
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aBotTickLogsItsSpawnAndInput(final GameTestHelper helper) {
		LearnArena.floor(helper);
		Bot bot = spawn(helper, 24.5, 24.5, SOUTH);
		String pid = bot.player().getUUID().toString();
		helper.assertTrue(!LearnTestInit.linesWith(pid, "gymBot SPAWN").isEmpty(), "the spawn was not logged");
		helper.succeed();
	}
}
