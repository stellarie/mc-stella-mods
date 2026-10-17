package com.stella.end;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Blaze Soul: the aura of the holder strikes one random hostile mob in six blocks every forty ticks, and nothing else. */
public class BlazeSoulGameTest {
	private static final int FLOOR_Y = EndCastle.standY(EndCastle.FLOORS);
	private static final int FROM_X = -7;
	private static final int TO_X = 3;
	private static final int GRACE = 22;

	/** A weapon made at the anvil, so the test holds what a player holds. */
	static ItemStack soulSword(final GameTestHelper helper) {
		ItemStack made = CoreImbue.result(helper.getLevel().registryAccess(), new ItemStack(Items.IRON_SWORD), new ItemStack(EndItems.darkBlazeCore()));
		helper.assertTrue(!made.isEmpty() && BlazeSoul.has(made), "the anvil made no Blaze Soul sword");
		return made;
	}

	private static Pig pig(final ServerLevel end, final Vec3 at) {
		Pig pig = EntityTypes.PIG.create(end, EntitySpawnReason.SPAWNER);
		LevelBridge.claim(pig);
		pig.addTag(EndTestSupport.DUMMY_TAG);
		pig.setNoAi(true);
		pig.setPersistenceRequired();
		pig.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		end.addFreshEntity(pig);
		return pig;
	}

	@GameTest(environment = "mod-stella-end-test:soul_aura", maxTicks = 420)
	public void everyFortyTicksTheHoldersAuraStrikesOneRandomHostileMobInSixBlocksForTheHoldersAttackAndNoOtherCreature(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int row = KnightSlashGameTest.clearRow(end, FROM_X, TO_X, new int[] {-4, 2}, new int[] {-4, -2});
		Vec3 base = new Vec3(-3.5, FLOOR_Y, row + 0.5);
		ServerPlayer holder = EndTestSupport.join(helper, end, base);
		ServerPlayer bystander = EndTestSupport.join(helper, end, base.add(0.0, 0.0, -2.0));
		holder.setItemSlot(EquipmentSlot.MAINHAND, soulSword(helper));
		Zombie near = EndTestSupport.dummy(end, base.add(2.0, 0.0, 0.0));
		Zombie side = EndTestSupport.dummy(end, base.add(0.0, 0.0, 2.5));
		Zombie far = EndTestSupport.dummy(end, base.add(7.0, 0.0, 0.0));
		Pig pig = pig(end, base.add(-2.0, 0.0, 0.0));
		BlazeSoul.reset();
		int cycles = 16;
		var sequence = helper.startSequence().thenIdle(3);
		for (int cycle = 0; cycle < cycles; cycle++) {
			// A strike on the same mob inside its twenty ticks of grace deals nothing, so the cycles keep that apart.
			sequence = sequence.thenExecute(() -> BlazeSoul.advance(BlazeSoul.PERIOD)).thenIdle(GRACE);
		}
		sequence
			.thenExecute(() -> {
				List<BlazeSoul.Hit> hits = BlazeSoul.hits();
				Set<UUID> victims = new HashSet<>();
				List<String> bad = new ArrayList<>();
				float attack = (float) holder.getAttributeValue(Attributes.ATTACK_DAMAGE);
				for (BlazeSoul.Hit hit : hits) {
					victims.add(hit.victim());
					if (!hit.holder().equals(holder.getUUID())) {
						bad.add("held by " + hit.holder());
					}
					if (hit.victim().equals(far.getUUID()) || hit.victim().equals(pig.getUUID()) || hit.victim().equals(bystander.getUUID()) || hit.victim().equals(holder.getUUID())) {
						bad.add("struck " + hit.victim());
					}
					if (Math.abs(hit.damage() - attack) > 1.0E-4F || hit.dealt() <= 0.0F) {
						bad.add("damage " + hit.damage() + " dealt " + hit.dealt() + " against attack " + attack);
					}
				}
				boolean both = victims.contains(near.getUUID()) && victims.contains(side.getUUID());
				EndTestSupport.result("soulAura", "hits=" + hits.size() + " cycles=" + cycles + " victims=" + victims.size() + " bothTargets=" + both + " attack=" + attack + " farHealthKept="
					+ (far.getHealth() == far.getMaxHealth()) + " pigHealthKept=" + (pig.getHealth() == pig.getMaxHealth()) + " holderKept=" + (holder.getHealth() == holder.getMaxHealth())
					+ " bad=" + bad.size());
				boolean farKept = far.getHealth() == far.getMaxHealth();
				boolean pigKept = pig.getHealth() == pig.getMaxHealth();
				boolean holderKept = holder.getHealth() == holder.getMaxHealth() && bystander.getHealth() == bystander.getMaxHealth();
				EndTestSupport.fresh(helper);
				helper.assertTrue(BlazeSoul.PERIOD == 40 && BlazeSoul.RANGE == 6.0, "an aura constant departs from the spec");
				helper.assertTrue(hits.size() == cycles, "the aura struck " + hits.size() + " times in " + cycles + " periods");
				helper.assertTrue(bad.isEmpty(), "aura defects: " + bad.stream().limit(4).toList());
				helper.assertTrue(both, "sixteen strikes never reached both hostile mobs in range: " + victims.size() + " struck");
				helper.assertTrue(farKept && pigKept && holderKept, "a creature the aura must spare lost health: far " + farKept + ", pig " + pigKept + ", players " + holderKept);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:soul_gap", maxTicks = 130)
	public void theAuraStrikesEveryFortyTicksOfItsOwnClockAndNeverFasterWithoutAnySkippedTime(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int row = KnightSlashGameTest.clearRow(end, FROM_X, TO_X, new int[] {-4, 2}, new int[] {-4, -2});
		Vec3 base = new Vec3(-3.5, FLOOR_Y, row + 0.5);
		ServerPlayer holder = EndTestSupport.join(helper, end, base);
		holder.setItemSlot(EquipmentSlot.MAINHAND, soulSword(helper));
		EndTestSupport.dummy(end, base.add(2.0, 0.0, 0.0));
		BlazeSoul.reset();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(BlazeSoul.hits().size() >= 3, "only " + BlazeSoul.hits().size() + " strikes yet"))
			.thenExecute(() -> {
				List<BlazeSoul.Hit> hits = BlazeSoul.hits();
				List<Long> gaps = new ArrayList<>();
				for (int i = 1; i < hits.size(); i++) {
					gaps.add(hits.get(i).tick() - hits.get(i - 1).tick());
				}
				EndTestSupport.result("soulGap", "gaps=" + gaps);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gaps.stream().allMatch(gap -> gap == BlazeSoul.PERIOD), "gaps between strikes were " + gaps);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:soul_held", maxTicks = 100)
	public void theAuraWorksFromEitherHandAndNotFromTheBackpackOrAPlainWeapon(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		int row = KnightSlashGameTest.clearRow(end, FROM_X, TO_X, new int[] {-4, 2}, new int[] {-4, -2});
		Vec3 base = new Vec3(-3.5, FLOOR_Y, row + 0.5);
		ServerPlayer holder = EndTestSupport.join(helper, end, base);
		EndTestSupport.dummy(end, base.add(2.0, 0.0, 0.0));
		ItemStack soul = soulSword(helper);
		BlazeSoul.reset();
		int[] counts = new int[4];
		var sequence = helper.startSequence().thenIdle(3);
		// Phase 0 main hand, 1 off hand, 2 backpack, 3 a plain sword in hand. Each phase gives the aura three periods.
		for (int phase = 0; phase < 4; phase++) {
			int current = phase;
			sequence = sequence.thenExecute(() -> {
				holder.setItemSlot(EquipmentSlot.MAINHAND, current == 3 ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY);
				holder.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				holder.getInventory().setItem(20, ItemStack.EMPTY);
				switch (current) {
					case 0 -> holder.setItemSlot(EquipmentSlot.MAINHAND, soul.copy());
					case 1 -> holder.setItemSlot(EquipmentSlot.OFFHAND, soul.copy());
					case 2 -> holder.getInventory().setItem(20, soul.copy());
					default -> holder.getInventory().setItem(21, soul.copy());
				}
				counts[current] = -BlazeSoul.hits().size();
			}).thenIdle(2);
			for (int period = 0; period < 3; period++) {
				sequence = sequence.thenExecute(() -> BlazeSoul.advance(BlazeSoul.PERIOD)).thenIdle(2);
			}
			sequence = sequence.thenExecute(() -> counts[current] += BlazeSoul.hits().size());
		}
		sequence
			.thenExecute(() -> {
				EndTestSupport.result("soulHeld", "mainHand=" + counts[0] + " offHand=" + counts[1] + " backpack=" + counts[2] + " plainInHand=" + counts[3]);
				EndTestSupport.fresh(helper);
				helper.assertTrue(counts[0] == 3, "the main hand gave " + counts[0] + " strikes in three periods");
				helper.assertTrue(counts[1] == 3, "the off hand gave " + counts[1] + " strikes in three periods");
				helper.assertTrue(counts[2] == 0, "a soul sword in the backpack gave " + counts[2] + " strikes");
				helper.assertTrue(counts[3] == 0, "a plain sword in hand, with a soul sword in the backpack, gave " + counts[3] + " strikes");
			})
			.thenSucceed();
	}
}
