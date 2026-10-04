package com.stella.equips;

import com.stella.equips.heart.SlimeHeart;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueDrops;
import com.stella.equips.unique.UniqueRank;
import com.stella.equips.unique.Uniques;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.ToDoubleBiFunction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The roll on a kill: who is credited, where the item lands, what a failing or summoned kill does, the announcement. */
public class UniqueDropGameTest {
	private static final Map<UUID, Double> HUGE_BONUS = new ConcurrentHashMap<>();
	private static final AtomicBoolean SOURCE_ADDED = new AtomicBoolean();

	@GameTest
	public void aKillWithNoCreditedPlayerRollsNothing(final GameTestHelper helper) {
		UniqueTestKit.bonewhisper(helper);
		ServerLevel level = helper.getLevel();
		Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(2.5, 1.0, 2.5));
		Zombie attacker = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1.0, 2.5));
		UniqueTestKit.rigDrop(skeleton.getUUID(), true);
		skeleton.hurtServer(level, level.damageSources().mobAttack(attacker), Float.MAX_VALUE);
		boolean dead = !skeleton.isAlive();
		helper.runAfterDelay(2L, () -> {
			int calls = UniqueTestKit.dropCalls(skeleton.getUUID()).size();
			int items = unique(level, skeleton).size();
			UniqueTestKit.result("dropNoCredit", "dead=" + dead + " rollerCalls=" + calls + " items=" + items);
			helper.assertTrue(dead, "the skeleton survived the kill");
			helper.assertTrue(calls == 0, "a kill with no credited player rolled " + calls + " times");
			helper.assertTrue(items == 0, "a kill with no credited player dropped " + items + " uniques");
			helper.succeed();
		});
	}

	@GameTest
	public void aPassingRollDropsTheUniqueAtTheVictim(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(3.5, 1.0, 3.5));
		Vec3 at = skeleton.position();
		UniqueTestKit.rigDrop(skeleton.getUUID(), true);
		skeleton.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		List<ItemEntity> dropped = unique(level, skeleton);
		List<UniqueTestKit.DropCall> calls = UniqueTestKit.dropCalls(skeleton.getUUID());
		boolean found = dropped.stream().anyMatch(entity -> entity.getItem().is(Uniques.item(def)) && entity.position().distanceToSqr(at) < 1.0E-6);
		boolean keyed = calls.stream().anyMatch(call -> call.key().equals(def.id()) && call.killer().equals(player.getUUID()) && call.chance() > 0.0);
		UniqueTestKit.result("dropPasses", "found=" + found + " calls=" + calls.size() + " keyed=" + keyed);
		helper.assertTrue(keyed, "the roller never saw " + def.id() + " for the credited player: " + calls);
		helper.assertTrue(found, "no " + def.id() + " item at " + at + ": " + dropped);
		helper.succeed();
	}

	@GameTest
	public void aFailingRollDropsNothing(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(3.5, 1.0, 3.5));
		UniqueTestKit.rigDrop(skeleton.getUUID(), false);
		skeleton.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		int rolls = UniqueTestKit.dropCalls(skeleton.getUUID()).size();
		int items = unique(level, skeleton).size();
		UniqueTestKit.result("dropFails", "rolls=" + rolls + " items=" + items);
		helper.assertTrue(rolls >= 1, "the failing roll never ran for " + def.id());
		helper.assertTrue(items == 0, "a failing roll dropped " + items + " items");
		helper.succeed();
	}

	@GameTest
	public void aSummonedMobDropsNothingEvenWithAPassingRoll(final GameTestHelper helper) {
		UniqueTestKit.bonewhisper(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(3.5, 1.0, 3.5));
		skeleton.addTag(UniqueRank.SUMMONED_TAG);
		UniqueTestKit.rigDrop(skeleton.getUUID(), true);
		skeleton.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		int rolls = UniqueTestKit.dropCalls(skeleton.getUUID()).size();
		int items = unique(level, skeleton).size();
		UniqueTestKit.result("dropSummoned", "rolls=" + rolls + " items=" + items);
		helper.assertTrue(rolls == 0 && items == 0, "a summoned skeleton rolled " + rolls + " times and dropped " + items);
		helper.succeed();
	}

	/** The Slime Heart joins the same roller under its own key, through its source ids. */
	@GameTest
	public void theSlimeHeartDropsThroughTheRoller(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Zombie stand = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(3.5, 1.0, 3.5));
		UniqueTestKit.rigDrop(stand.getUUID(), true);
		Vec3 at = stand.position();
		DamageSource source = level.damageSources().playerAttack(player);
		List<ItemEntity> dropped = UniqueDrops.rollSource(stand, player, source, Identifier.parse("mod-stella-mobs:blue_slime"));
		boolean heart = dropped.stream().anyMatch(entity -> entity.getItem().is(SlimeHeart.ITEM) && entity.position().distanceToSqr(at) < 1.0E-6);
		List<UniqueTestKit.DropCall> calls = UniqueTestKit.dropCalls(stand.getUUID());
		UniqueDrops.Entry entry = UniqueDrops.entry("slime_heart");
		UniqueTestKit.result("slimeHeartRoll", "heart=" + heart + " calls=" + calls);
		helper.assertTrue(entry != null, "the roller has no slime_heart entry");
		helper.assertTrue(entry.baseChance() == (double) SlimeHeart.DROP_CHANCE, "the entry base chance is " + entry.baseChance());
		helper.assertTrue(calls.stream().anyMatch(call -> call.key().equals("slime_heart")), "the roller never saw the slime heart: " + calls);
		helper.assertTrue(heart, "no slime heart dropped at the victim: " + dropped);
		List<ItemEntity> red = UniqueDrops.rollSource(stand, player, source, Identifier.parse("mod-stella-mobs:red_slime"));
		List<ItemEntity> other = UniqueDrops.rollSource(stand, player, source, Identifier.parse("minecraft:cow"));
		helper.assertTrue(red.stream().anyMatch(entity -> entity.getItem().is(SlimeHeart.ITEM)), "a red slime source drops no heart");
		helper.assertTrue(other.stream().noneMatch(entity -> entity.getItem().is(SlimeHeart.ITEM)), "a cow source dropped a heart");
		helper.succeed();
	}

	@GameTest
	public void theAnnouncementNamesThePlayerAndTheItem(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		ItemStack stack = new ItemStack(Uniques.item(def));
		Component line = UniqueDrops.announcement(player, stack);
		String text = line.getString();
		UniqueTestKit.result("dropAnnouncement", "text=" + text);
		helper.assertTrue(text.equals(player.getName().getString() + " found " + def.name() + "!"), "the line reads: " + text);
		TextColor epicColor = TextColor.fromLegacyFormat(Rarity.EPIC.color());
		boolean epic = line.getSiblings().stream().anyMatch(sibling -> sibling.getString().equals(def.name()) && epicColor.equals(sibling.getStyle().getColor()));
		helper.assertTrue(epic, "the item name is not colored with the EPIC rarity: " + line);
		helper.succeed();
	}

	/** A null roller restores the fair roll. A chance of 1.0 passes a fair roll every time, and a rigged false would not. */
	@GameTest
	public void aNullRollerRestoresTheFairRoll(final GameTestHelper helper) {
		UniqueDef def = UniqueTestKit.bonewhisper(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = UniqueTestKit.survivor(helper, new Vec3(1.5, 1.0, 1.5));
		Object published = FabricLoader.getInstance().getObjectShare().get(UniqueDrops.DROP_RATE_SHARE);
		@SuppressWarnings("unchecked")
		Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>> register = (Consumer<ToDoubleBiFunction<ServerPlayer, LivingEntity>>) published;
		if (SOURCE_ADDED.compareAndSet(false, true)) {
			register.accept((killer, victim) -> HUGE_BONUS.getOrDefault(victim.getUUID(), 0.0));
		}
		Skeleton skeleton = helper.spawnWithNoFreeWill(EntityTypes.SKELETON, new Vec3(3.5, 1.0, 3.5));
		HUGE_BONUS.put(skeleton.getUUID(), 1.0E6);
		UniqueTestKit.rigDrop(skeleton.getUUID(), false);
		UniqueDrops.setRoller(null);
		try {
			skeleton.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		} finally {
			UniqueTestKit.installDropRoller();
		}
		int items = unique(level, skeleton).size();
		UniqueTestKit.result("dropFairRoll", "items=" + items);
		helper.assertTrue(items >= 1, "a fair roll at chance 1.0 dropped " + items + " uniques for " + def.id());
		helper.succeed();
	}

	private static List<ItemEntity> unique(final ServerLevel level, final LivingEntity victim) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(victim.position(), victim.position()).inflate(3.0),
			entity -> Uniques.def(entity.getItem()) != null);
	}
}
