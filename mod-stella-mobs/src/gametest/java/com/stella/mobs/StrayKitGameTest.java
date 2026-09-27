package com.stella.mobs;

import com.stella.mobs.behavior.BossAbilities;
import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EscortSquad;
import com.stella.mobs.behavior.StrayKit;
import com.stella.mobs.gear.GearTier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The stray by what it holds: an ordinary archer's arrow leaves a block of powder snow; an elite archer is a
 * solitary sniper that shoots once every ten seconds, leaves a wide patch of powder snow, and slips away
 * when struck; a melee stray freezes the players it strikes, and an elite one ambushes a far target.
 */
public class StrayKitGameTest {
	private static final String TEST = "strayKit";
	private static final Map<UUID, List<List<BlockPos>>> POWDER = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> BLINKS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<AbstractArrow>> SHOTS = new ConcurrentHashMap<>();

	static {
		StrayKit.setRecorder(new StrayKit.Recorder() {
			@Override
			public void powder(final Mob stray, final List<BlockPos> placed) {
				POWDER.computeIfAbsent(stray.getUUID(), key -> new CopyOnWriteArrayList<>()).add(List.copyOf(placed));
			}

			@Override
			public void blink(final Mob stray, final String reason) {
				BLINKS.computeIfAbsent(stray.getUUID(), key -> new CopyOnWriteArrayList<>()).add(reason);
			}

			@Override
			public void sniped(final Mob stray, final AbstractArrow arrow) {
				SHOTS.computeIfAbsent(stray.getUUID(), key -> new CopyOnWriteArrayList<>()).add(arrow);
			}
		});
	}

	@GameTest
	public void anOrdinaryStrayArrowLeavesOneBlockOfPowderSnow(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray stray = stray(helper, Items.BOW, 1, 1);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		StrayKit.onArrowHit(arrow(helper, stray), at);
		List<List<BlockPos>> placed = POWDER.getOrDefault(stray.getUUID(), List.of());
		result("ordinaryPowder", "patches=" + placed + " block=" + helper.getLevel().getBlockState(at));
		helper.assertTrue(placed.size() == 1 && placed.get(0).equals(List.of(at)) && helper.getLevel().getBlockState(at).is(Blocks.POWDER_SNOW),
			"an ordinary stray arrow left " + placed);
		helper.succeed();
	}

	@GameTest
	public void anEliteSniperArrowLeavesAThreeByThreeAndABossAFiveByFive(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray elite = stray(helper, Items.BOW, 0, 0);
		Elite.mark(elite, GearTier.IRON, 1.0);
		AbstractArrow eliteArrow = arrow(helper, elite);
		eliteArrow.addTag(StrayKit.SNIPER_TAG);
		// Two patches that do not overlap, on the floor.
		StrayKit.onArrowHit(eliteArrow, helper.absolutePos(new BlockPos(1, 1, 6)));
		Stray boss = stray(helper, Items.BOW, 0, 3);
		Elite.mark(boss, GearTier.IRON, 1.0);
		AbstractArrow bossArrow = arrow(helper, boss);
		bossArrow.addTag(StrayKit.SNIPER_TAG);
		bossArrow.addTag(StrayKit.BOSS_SNIPER_TAG);
		StrayKit.onArrowHit(bossArrow, helper.absolutePos(new BlockPos(5, 1, 2)));
		int eliteCount = POWDER.getOrDefault(elite.getUUID(), List.of()).stream().mapToInt(List::size).sum();
		int bossCount = POWDER.getOrDefault(boss.getUUID(), List.of()).stream().mapToInt(List::size).sum();
		int eliteSide = 2 * StrayKit.ELITE_POWDER_RADIUS + 1;
		int bossSide = 2 * StrayKit.BOSS_POWDER_RADIUS + 1;
		result("sniperPowder", "elite=" + eliteCount + " boss=" + bossCount);
		helper.assertTrue(eliteCount == eliteSide * eliteSide && bossCount == bossSide * bossSide, "the patches held " + eliteCount + " and " + bossCount + " blocks");
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void powderSnowClearsItself(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray stray = stray(helper, Items.BOW, 1, 1);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		StrayKit.onArrowHit(arrow(helper, stray), at);
		helper.runAfterDelay(StrayKit.POWDER_LIFETIME_TICKS + 5L, () -> {
			boolean cleared = helper.getLevel().getBlockState(at).isAir();
			result("powderClears", "cleared=" + cleared);
			helper.assertTrue(cleared, "the powder snow is still there: " + helper.getLevel().getBlockState(at));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void anEliteSniperShootsOnceEveryTenSecondsStraightAndFast(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Stray stray = stray(helper, Items.BOW, 1, 1);
		Elite.mark(stray, GearTier.IRON, 1.0);
		StrayKit.install(stray);
		Zombie target = target(helper, 6, 6);
		helper.onEachTick(() -> stray.setTarget(target));
		helper.runAfterDelay(150L, () -> {
			List<AbstractArrow> shots = SHOTS.getOrDefault(stray.getUUID(), List.of());
			boolean straight = !shots.isEmpty() && shots.get(0).isNoGravity();
			result("sniperCadence", "shots=" + shots.size() + " straight=" + straight + " goals=" + BossAbilities.copiesOf(stray, StrayKit.SniperGoal.class));
			helper.assertTrue(shots.size() == 1, "the sniper shot " + shots.size() + " times in 150 ticks, not once");
			helper.assertTrue(straight, "the sniper's arrow falls");
			helper.succeed();
		});
	}

	@GameTest
	public void aStareIsReadOnlyWhenThePlayerLooksAtTheStray(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray stray = stray(helper, Items.BOW, 6, 3);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 3.5));
		player.snapTo(at.x, at.y, at.z, -90.0F, 0.0F);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, stray.getEyePosition());
		boolean looking = StrayKit.isLookedAtBy(player, stray);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, stray.getEyePosition().add(0.0, 0.0, 12.0));
		boolean away = StrayKit.isLookedAtBy(player, stray);
		result("stare", "looking=" + looking + " away=" + away);
		helper.assertTrue(looking && !away, "the stare read " + looking + " looking at it and " + away + " looking away");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aStruckEliteArcherBlindsItsAttackerAndSlipsAwayOnce(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray stray = stray(helper, Items.BOW, 3, 3);
		Elite.mark(stray, GearTier.IRON, 1.0);
		stray.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		stray.setHealth(500.0F);
		Zombie attacker = target(helper, 4, 3);
		Vec3 before = stray.position();
		stray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
		double moved = stray.position().distanceTo(before);
		stray.damageCooldownTime = 0;
		stray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
		List<String> blinks = BLINKS.getOrDefault(stray.getUUID(), List.of());
		result("escape", "blinks=" + blinks + " moved=" + moved + " blinded=" + attacker.hasEffect(MobEffects.BLINDNESS));
		helper.assertTrue(attacker.hasEffect(MobEffects.BLINDNESS), "the attacker was not blinded");
		helper.assertTrue(blinks.equals(List.of("escape")) && moved >= StrayKit.ESCAPE_MIN - 1.0, "the stray escaped " + blinks + " and moved " + moved);
		helper.succeed();
	}

	@GameTest(maxTicks = 140)
	public void aMeleeStrayFreezesThePlayerItStrikes(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		Stray stray = stray(helper, Items.IRON_SWORD, 2, 2);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 1.0, 2.5));
		player.setPos(at.x, at.y, at.z);
		// A player who has just joined cannot be hurt for three seconds.
		helper.runAfterDelay(80L, () -> {
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(stray), 1.0F);
			result("meleeFreeze", "frozen=" + player.getTicksFrozen() + " required=" + player.getTicksRequiredToFreeze() + " fast=" + stray.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(StrayKit.FURY_ID));
			helper.assertTrue(player.isFullyFrozen(), "the struck player is not frozen: " + player.getTicksFrozen());
			helper.assertTrue(stray.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(StrayKit.FURY_ID) != null, "the melee stray is not fast");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100, padding = 24)
	public void anEliteMeleeStrayAmbushesAFarTarget(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Stray stray = stray(helper, Items.IRON_SWORD, 0, 0);
		Elite.mark(stray, GearTier.IRON, 1.0);
		stray.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		Zombie target = target(helper, 7, 7);
		target.teleportTo(target.getX() + 8.0, target.getY(), target.getZ() + 8.0);
		for (int x = 5; x < 20; x++) {
			for (int z = 5; z < 20; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		helper.onEachTick(() -> stray.setTarget(target));
		helper.succeedWhen(() -> {
			List<String> blinks = BLINKS.getOrDefault(stray.getUUID(), List.of());
			helper.assertTrue(blinks.contains("ambush"), "no ambush yet: " + blinks);
			double distance = stray.distanceTo(target);
			result("ambush", "blinks=" + blinks + " distance=" + distance + " blinded=" + target.hasEffect(MobEffects.BLINDNESS));
			helper.assertTrue(distance <= 4.0 && target.hasEffect(MobEffects.BLINDNESS), "the ambush left the stray " + distance + " away");
		});
	}

	@GameTest(maxTicks = 40)
	public void anEliteArcherComesAloneWhileAnEliteSwordsmanBringsEscorts(final GameTestHelper helper) {
		Arena.buildFloor(helper);
		helper.setTime(Arena.NIGHT_TIME);
		Stray archer = stray(helper, Items.BOW, 2, 2);
		Stray swordsman = stray(helper, Items.IRON_SWORD, 5, 5);
		Elite.rollAtSpawn(archer, helper.getLevel(), 1.0, false);
		Elite.rollAtSpawn(swordsman, helper.getLevel(), 1.0, false);
		helper.runAfterDelay(10L, () -> {
			int archerEscorts = EscortSquad.escortsOf(helper.getLevel(), archer).size();
			int swordEscorts = EscortSquad.escortsOf(helper.getLevel(), swordsman).size();
			result("solitary", "archerEscorts=" + archerEscorts + " swordEscorts=" + swordEscorts);
			helper.assertTrue(swordEscorts > 0, "the elite swordsman brought no escort either, so the case proves nothing");
			helper.assertTrue(archerEscorts == 0, "the elite archer brought " + archerEscorts + " escorts");
			EscortSquad.escortsOf(helper.getLevel(), swordsman).forEach(Mob::discard);
			helper.succeed();
		});
	}

	private static Stray stray(final GameTestHelper helper, final net.minecraft.world.item.Item weapon, final int x, final int z) {
		Stray stray = EntityTypes.STRAY.create(helper.getLevel(), EntitySpawnReason.EVENT);
		stray.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		stray.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		stray.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
		stray.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		stray.setPersistenceRequired();
		helper.getLevel().addFreshEntity(stray);
		return stray;
	}

	private static Zombie target(final GameTestHelper helper, final int x, final int z) {
		Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.EVENT);
		zombie.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		zombie.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, z + 0.5)), 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		zombie.setHealth(1000.0F);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setPersistenceRequired();
		helper.getLevel().addFreshEntity(zombie);
		return zombie;
	}

	private static AbstractArrow arrow(final GameTestHelper helper, final Stray owner) {
		return new Arrow(helper.getLevel(), owner, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
	}

	private static void result(final String test, final String outcome) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test={} RESULT test={} {}", TEST, test, outcome);
	}
}
