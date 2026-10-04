package com.stella.equips;

import com.stella.equips.unique.UniqueDrops;
import com.stella.equips.unique.UniqueRank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ObjectShare;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** The drop chance: its formula, the rank of a mob, the share bonuses, and where Looting is read. */
public class UniqueDropRateGameTest {
	private static final double EPSILON = 1.0e-12;
	private static final Map<UUID, Double> BONUS = new ConcurrentHashMap<>();
	private static final AtomicBoolean SOURCE_ADDED = new AtomicBoolean();

	@GameTest
	public void chanceFollowsTheFormula(final GameTestHelper helper) {
		double base = UniqueDrops.BASE_CHANCE;
		double plain = UniqueDrops.chance(base, 1, 0, 0.0);
		double boss = UniqueDrops.chance(base, 10, 0, 0.0);
		double looted = UniqueDrops.chance(base, 1, 3, 0.0);
		double shared = UniqueDrops.chance(base, 1, 0, 0.25);
		double all = UniqueDrops.chance(base, 5, 2, 0.5);
		double capped = UniqueDrops.chance(0.5, 10, 0, 0.0);
		double summoned = UniqueDrops.chance(base, 0, 3, 1.0);
		UniqueTestKit.result("dropChance", "plain=" + plain + " boss=" + boss + " looted=" + looted + " shared=" + shared + " all=" + all
			+ " capped=" + capped + " summoned=" + summoned);
		helper.assertTrue(Math.abs(UniqueDrops.BASE_CHANCE - 0.001) < EPSILON, "the base chance is " + UniqueDrops.BASE_CHANCE);
		helper.assertTrue(Math.abs(UniqueDrops.LOOTING_STEP - 0.5) < EPSILON, "the looting step is " + UniqueDrops.LOOTING_STEP);
		helper.assertTrue(Math.abs(plain - base) < EPSILON, "plain chance " + plain);
		helper.assertTrue(Math.abs(boss - base * 10) < EPSILON, "boss chance " + boss);
		helper.assertTrue(Math.abs(looted - base * (1 + UniqueDrops.LOOTING_STEP * 3)) < EPSILON, "looted chance " + looted);
		helper.assertTrue(Math.abs(shared - base * 1.25) < EPSILON, "shared chance " + shared);
		helper.assertTrue(Math.abs(all - base * 5 * (1 + UniqueDrops.LOOTING_STEP * 2 + 0.5)) < EPSILON, "combined chance " + all);
		helper.assertTrue(capped == 1.0, "the chance is capped at 1.0, got " + capped);
		helper.assertTrue(summoned == 0.0, "rank 0 gives " + summoned);
		helper.succeed();
	}

	/**
	 * One test holds every rank case, because the mobs share is one global key: the absent cases and the present
	 * cases must not run side by side.
	 */
	@GameTest
	public void rankFollowsTheMobAndTheShare(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ObjectShare share = FabricLoader.getInstance().getObjectShare();
		Object previousRank = share.remove(UniqueRank.MOB_RANK_SHARE);
		Object previousSovereign = share.remove(UniqueRank.SOVEREIGN_SHARE);
		try {
			Zombie zombie = create(level, EntityTypes.ZOMBIE);
			Zombie nemesis = create(level, EntityTypes.ZOMBIE);
			nemesis.addTag(UniqueRank.NEMESIS_TAG);
			Zombie summonedTag = create(level, EntityTypes.ZOMBIE);
			summonedTag.addTag(UniqueRank.SUMMONED_TAG);
			summonedTag.addTag(UniqueRank.NEMESIS_TAG);
			int absentNormal = UniqueRank.of(zombie);
			int absentNemesis = UniqueRank.of(nemesis);
			int absentSummoned = UniqueRank.of(summonedTag);
			int wither = UniqueRank.of(create(level, EntityTypes.WITHER));
			int dragon = UniqueRank.of(create(level, EntityTypes.ENDER_DRAGON));
			int warden = UniqueRank.of(create(level, EntityTypes.WARDEN));
			int elder = UniqueRank.of(create(level, EntityTypes.ELDER_GUARDIAN));
			int plainBlaze = UniqueRank.of(create(level, EntityTypes.BLAZE));
			UniqueTestKit.result("rankAbsentShare", "normal=" + absentNormal + " nemesis=" + absentNemesis + " summonedTag=" + absentSummoned
				+ " wither=" + wither + " dragon=" + dragon + " warden=" + warden + " elder=" + elder + " blaze=" + plainBlaze);
			helper.assertTrue(absentNormal == 1, "a plain zombie with no share has rank " + absentNormal);
			helper.assertTrue(absentNemesis == 5, "a nemesis has rank " + absentNemesis);
			helper.assertTrue(absentSummoned == 0, "a summoned nemesis has rank " + absentSummoned);
			helper.assertTrue(wither == 10 && dragon == 10 && warden == 10 && elder == 10, "fixed boss ranks " + wither + dragon + warden + elder);
			helper.assertTrue(plainBlaze == 1, "a plain blaze has rank " + plainBlaze);

			Map<UUID, String> ranks = new ConcurrentHashMap<>();
			Function<Entity, String> rankShare = entity -> ranks.get(entity.getUUID());
			share.put(UniqueRank.MOB_RANK_SHARE, rankShare);
			Zombie elite = create(level, EntityTypes.ZOMBIE);
			Zombie eliteNemesis = create(level, EntityTypes.ZOMBIE);
			eliteNemesis.addTag(UniqueRank.NEMESIS_TAG);
			Zombie boss = create(level, EntityTypes.ZOMBIE);
			Zombie summoned = create(level, EntityTypes.ZOMBIE);
			Zombie summonedBoss = create(level, EntityTypes.ZOMBIE);
			summonedBoss.addTag(UniqueRank.NEMESIS_TAG);
			Zombie strange = create(level, EntityTypes.ZOMBIE);
			Zombie normal = create(level, EntityTypes.ZOMBIE);
			ranks.put(elite.getUUID(), "elite");
			ranks.put(eliteNemesis.getUUID(), "elite");
			ranks.put(boss.getUUID(), "boss");
			ranks.put(summoned.getUUID(), "summoned");
			ranks.put(summonedBoss.getUUID(), "summoned");
			ranks.put(strange.getUUID(), "strange");
			ranks.put(normal.getUUID(), "normal");
			int shareElite = UniqueRank.of(elite);
			int shareEliteNemesis = UniqueRank.of(eliteNemesis);
			int shareBoss = UniqueRank.of(boss);
			int shareSummoned = UniqueRank.of(summoned);
			int shareSummonedBoss = UniqueRank.of(summonedBoss);
			int shareStrange = UniqueRank.of(strange);
			int shareNormal = UniqueRank.of(normal);
			int shareWither = UniqueRank.of(create(level, EntityTypes.WITHER));
			UniqueTestKit.result("rankShare", "elite=" + shareElite + " eliteNemesis=" + shareEliteNemesis + " boss=" + shareBoss
				+ " summoned=" + shareSummoned + " summonedBoss=" + shareSummonedBoss + " strange=" + shareStrange + " normal=" + shareNormal);
			helper.assertTrue(shareElite == 3, "an elite has rank " + shareElite);
			helper.assertTrue(shareEliteNemesis == 5, "the larger of elite and nemesis applies, got " + shareEliteNemesis);
			helper.assertTrue(shareBoss == 10, "a boss has rank " + shareBoss);
			helper.assertTrue(shareSummoned == 0 && shareSummonedBoss == 0, "summoned overrides: " + shareSummoned + " " + shareSummonedBoss);
			helper.assertTrue(shareStrange == 1 && shareNormal == 1, "an unknown or normal value gives " + shareStrange + " " + shareNormal);
			helper.assertTrue(shareWither == 10, "a fixed boss keeps rank 10 with the share present, got " + shareWither);

			share.put(UniqueRank.MOB_RANK_SHARE, (Function<Entity, String>) entity -> {
				throw new IllegalStateException("a broken share");
			});
			int broken = UniqueRank.of(zombie);
			share.put(UniqueRank.MOB_RANK_SHARE, "not a function");
			int wrongType = UniqueRank.of(nemesis);
			helper.assertTrue(broken == 1, "a throwing share must read as absent, got " + broken);
			helper.assertTrue(wrongType == 5, "a share of the wrong type must read as absent, got " + wrongType);
			share.remove(UniqueRank.MOB_RANK_SHARE);

			Blaze sovereign = create(level, EntityTypes.BLAZE);
			Blaze other = create(level, EntityTypes.BLAZE);
			Predicate<Entity> sovereignShare = entity -> entity.getUUID().equals(sovereign.getUUID());
			share.put(UniqueRank.SOVEREIGN_SHARE, sovereignShare);
			int sovereignRank = UniqueRank.of(sovereign);
			int otherRank = UniqueRank.of(other);
			UniqueTestKit.result("rankSovereign", "sovereign=" + sovereignRank + " other=" + otherRank);
			helper.assertTrue(sovereignRank == 10, "the Sovereign has rank " + sovereignRank);
			helper.assertTrue(otherRank == 1, "another blaze has rank " + otherRank);
			share.remove(UniqueRank.SOVEREIGN_SHARE);
			helper.assertTrue(UniqueRank.of(sovereign) == 1, "the Sovereign share is gone, so the blaze is normal");
			helper.succeed();
		} finally {
			share.remove(UniqueRank.MOB_RANK_SHARE);
			share.remove(UniqueRank.SOVEREIGN_SHARE);
			if (previousRank != null) {
				share.put(UniqueRank.MOB_RANK_SHARE, previousRank);
			}
			if (previousSovereign != null) {
				share.put(UniqueRank.SOVEREIGN_SHARE, previousSovereign);
			}
		}
	}

	@GameTest
	public void theDropRateShareAddsEachSourceOnce(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Object published = FabricLoader.getInstance().getObjectShare().get(UniqueDrops.DROP_RATE_SHARE);
		helper.assertTrue(published instanceof Consumer, "the drop-rate share holds " + published);
		@SuppressWarnings("unchecked")
		Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>> register = (Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>>) published;
		if (SOURCE_ADDED.compareAndSet(false, true)) {
			register.accept((killer, victim) -> BONUS.getOrDefault(victim.getUUID(), 0.0));
			register.accept((killer, victim) -> BONUS.containsKey(victim.getUUID()) ? 0.5 : 0.0);
		}
		Zombie bonused = create(level, EntityTypes.ZOMBIE);
		Zombie plain = create(level, EntityTypes.ZOMBIE);
		BONUS.put(bonused.getUUID(), 0.25);
		double withBonus = UniqueDrops.shareBonus(player, bonused);
		double without = UniqueDrops.shareBonus(player, plain);
		UniqueTestKit.result("dropRateShare", "withBonus=" + withBonus + " without=" + without);
		helper.assertTrue(Math.abs(withBonus - 0.75) < EPSILON, "two sources add 0.25 and 0.5, got " + withBonus);
		helper.assertTrue(without == 0.0, "a victim no source names gets " + without);
		helper.succeed();
	}

	@GameTest
	public void lootingComesFromTheWeaponOfTheKillElseTheHand(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		ItemStack hand = new ItemStack(Items.DIAMOND_SWORD);
		hand.enchant(UniqueTestKit.enchantment(level, Enchantments.LOOTING), 3);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hand);
		ItemStack bow = new ItemStack(Items.BOW);
		bow.enchant(UniqueTestKit.enchantment(level, Enchantments.LOOTING), 1);
		Arrow arrow = new Arrow(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.ARROW), bow);
		arrow.setOwner(player);
		DamageSource byArrow = level.damageSources().arrow(arrow, player);
		DamageSource byHand = level.damageSources().playerAttack(player);
		DamageSource unattributed = level.damageSources().generic();
		int fromArrow = UniqueDrops.looting(player, byArrow);
		int fromHand = UniqueDrops.looting(player, byHand);
		int fallback = UniqueDrops.looting(player, unattributed);
		UniqueTestKit.result("lootingSource", "arrow=" + fromArrow + " hand=" + fromHand + " fallback=" + fallback);
		helper.assertTrue(fromArrow == 1, "the arrow's bow has Looting I, got " + fromArrow);
		helper.assertTrue(fromHand == 3, "a melee kill reads the held sword, got " + fromHand);
		helper.assertTrue(fallback == 3, "a source with no weapon falls back to the hand, got " + fallback);
		helper.succeed();
	}

	private static <T extends net.minecraft.world.entity.Entity> T create(final ServerLevel level, final EntityType<T> type) {
		T entity = type.create(level, EntitySpawnReason.COMMAND);
		if (entity == null) {
			throw new IllegalStateException("could not create " + type);
		}
		return entity;
	}
}
