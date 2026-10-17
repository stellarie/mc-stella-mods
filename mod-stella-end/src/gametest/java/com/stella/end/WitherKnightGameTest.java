package com.stella.end;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/** The two Wither Knights: how they rise, what they are, what they wear, and how they close on a player. */
public class WitherKnightGameTest {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final net.minecraft.world.item.Item[] NETHERITE = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};

	static Registry<Enchantment> registry(final ServerLevel end) {
		return end.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	static ItemEnchantments enchantsOf(final ItemStack stack) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	static List<WitherSkeleton> raiseAlone(final GameTestHelper helper, final ServerLevel end) {
		List<WitherSkeleton> knights = WitherKnights.raise(end);
		helper.assertTrue(knights.size() == 2, "raised " + knights.size() + " knights, not two");
		return knights;
	}

	@GameTest(environment = "mod-stella-end-test:knight_rise", maxTicks = 60)
	public void twoKnightsRiseWithTheSovereignBesideItAndASecondRisingAddsNone(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		Blaze sovereign = UndeadSovereign.spawn(end);
		helper.assertTrue(sovereign != null, "the Sovereign was not raised");
		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				List<WitherKnights.Raise> raised = WitherKnights.raised();
				List<UUID> ids = raised.isEmpty() ? List.of() : raised.get(0).knights();
				Set<WitherKnights.Kind> kinds = EnumSet.noneOf(WitherKnights.Kind.class);
				List<String> bad = new ArrayList<>();
				for (UUID id : ids) {
					var entity = end.getEntity(id);
					if (!(entity instanceof WitherSkeleton knight) || !knight.isAlive() || !WitherKnights.is(knight)) {
						bad.add(id + " is " + entity);
						continue;
					}
					kinds.add(WitherKnights.kindOf(knight));
					double apart = knight.position().distanceTo(sovereign.position());
					if (apart < 2.0 || apart > 10.0) {
						bad.add(id + " stands " + apart + " blocks from the Sovereign");
					}
					if (!CastleTeleport.safe(end, knight, knight.position())) {
						bad.add(id + " stands on no safe ground at " + knight.position());
					}
				}
				List<WitherSkeleton> again = WitherKnights.raise(end);
				int standing = WitherKnights.alive(end).size();
				int bits = EndCastles.state(end).knights();
				EndTestSupport.result("knightRise", "raisings=" + raised.size() + " ids=" + ids.size() + " kinds=" + kinds + " bad=" + bad.size() + " again=" + again.size()
					+ " standing=" + standing + " bits=" + bits);
				EndTestSupport.fresh(helper);
				helper.assertTrue(raised.size() == 1 && ids.size() == 2, "the Sovereign raised " + ids.size() + " knights in " + raised.size() + " raisings");
				helper.assertTrue(kinds.size() == 2, "the knights are of kinds " + kinds);
				helper.assertTrue(bad.isEmpty(), "knight defects: " + bad);
				helper.assertTrue(again.isEmpty() && standing == 2, "a second raising added " + again.size() + " and " + standing + " knights stand");
				helper.assertTrue(bits == 3, "the saved state holds knight bits " + bits);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_stats", maxTicks = 60)
	public void eachKnightHasOneHundredTwentyFiveThousandHealthAScaleOfOnePointSixAndTheSovereignsLevel(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<WitherSkeleton> knights = raiseAlone(helper, end);
		double ceiling = ((net.minecraft.world.entity.ai.attributes.RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue();
		WitherSkeleton plain = net.minecraft.world.entity.EntityTypes.WITHER_SKELETON.create(end, net.minecraft.world.entity.EntitySpawnReason.SPAWNER);
		helper.startSequence()
			// A tick later every load listener has run, the health scale of the rpg mod included.
			.thenIdle(4)
			.thenExecute(() -> {
				List<String> bad = new ArrayList<>();
				StringBuilder seen = new StringBuilder();
				for (WitherSkeleton knight : knights) {
					float max = knight.getMaxHealth();
					double scale = knight.getAttributeValue(Attributes.SCALE);
					int level = LevelBridge.read(knight);
					seen.append(" max=").append(max).append(" health=").append(knight.getHealth()).append(" scale=").append(scale).append(" level=").append(level)
						.append(" height=").append(knight.getBbHeight());
					if (Math.abs(max - WitherKnights.MAX_HEALTH) > 1.0F || Math.abs(knight.getHealth() - max) > 1.0F) {
						bad.add("health " + knight.getHealth() + " of " + max);
					}
					if (Math.abs(scale - WitherKnights.SCALE) > 1.0E-9 || knight.getBbHeight() < plain.getBbHeight() * 1.5F) {
						bad.add("scale " + scale + " gives height " + knight.getBbHeight() + " against " + plain.getBbHeight());
					}
					if (level != -1 && level != EndLevels.KNIGHT) {
						bad.add("level " + level);
					}
					if (!knight.isPersistenceRequired() || !knight.hasCustomName()) {
						bad.add("persistent " + knight.isPersistenceRequired() + " named " + knight.hasCustomName());
					}
				}
				EndTestSupport.result("knightStats", "ceiling=" + ceiling + " constant=" + WitherKnights.MAX_HEALTH + seen);
				EndTestSupport.fresh(helper);
				helper.assertTrue(WitherKnights.MAX_HEALTH == 125000.0F && WitherKnights.SCALE == 1.6 && WitherKnights.SCALE > 1.0, "a knight constant departs from the spec");
				helper.assertTrue(ceiling >= WitherKnights.MAX_HEALTH, "the health attribute stops at " + ceiling + ", so this run cannot hold the fixed health");
				helper.assertTrue(bad.isEmpty(), "knight stats off the spec: " + bad);
			})
			.thenSucceed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_gear", maxTicks = 40)
	public void eachKnightWearsFullNetheriteWithSixEnchantmentsOnEveryPieceAndTheChillOrWaveformSwordAtLevelFour(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		List<WitherSkeleton> knights = raiseAlone(helper, end);
		Registry<Enchantment> registry = registry(end);
		boolean equips = MobGear.find(registry, WitherKnights.Kind.CHILL.special()) != null;
		List<String> bad = new ArrayList<>();
		Set<WitherKnights.Kind> kinds = EnumSet.noneOf(WitherKnights.Kind.class);
		for (WitherSkeleton knight : knights) {
			WitherKnights.Kind kind = WitherKnights.kindOf(knight);
			kinds.add(kind);
			for (int i = 0; i < ARMOR.length; i++) {
				ItemStack worn = knight.getItemBySlot(ARMOR[i]);
				if (!worn.is(NETHERITE[i]) || enchantsOf(worn).size() != WitherKnights.ENCHANTS) {
					bad.add(kind + " " + ARMOR[i] + " holds " + worn + " with " + enchantsOf(worn).size() + " enchantments");
				}
			}
			ItemStack sword = knight.getMainHandItem();
			ItemEnchantments held = enchantsOf(sword);
			int expected = WitherKnights.ENCHANTS + (equips ? 1 : 0);
			if (!sword.is(WitheringArms.sword()) || held.size() != expected) {
				bad.add(kind + " sword " + sword + " holds " + held.size() + " enchantments, wanted " + expected);
			}
			for (WitherKnights.Kind other : WitherKnights.Kind.values()) {
				Holder<Enchantment> holder = MobGear.find(registry, other.special());
				int level = holder == null ? 0 : held.getLevel(holder);
				int wanted = equips && other == kind ? MobGear.SPECIAL_LEVEL : 0;
				if (level != wanted) {
					bad.add(kind + " sword holds " + other.special() + " at " + level + ", wanted " + wanted);
				}
			}
		}
		EndTestSupport.result("knightGear", "knights=" + knights.size() + " kinds=" + kinds + " equips=" + equips + " bad=" + bad.size());
		EndTestSupport.fresh(helper);
		helper.assertTrue(kinds.size() == 2, "the knights are of kinds " + kinds);
		helper.assertTrue(WitherKnights.ENCHANTS == 6, "the shipped enchantment count is " + WitherKnights.ENCHANTS);
		helper.assertTrue(bad.isEmpty(), "knight gear off the spec: " + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_sword_roll", maxTicks = 200)
	public void aKnightSwordRollHoldsExactlySixClashFreeRandomEnchantmentsAndSkipsAnAbsentSpecialWithoutFailing(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.end(helper);
		Registry<Enchantment> registry = registry(end);
		Identifier gone = Identifier.parse("mod-stella-equips-enchants:weapon/not_in_this_pack");
		List<String> bad = new ArrayList<>();
		int rolls = 0;
		for (long seed = 0; seed < 300; seed++) {
			for (Identifier special : List.of(WitherKnights.Kind.CHILL.special(), WitherKnights.Kind.WAVEFORM.special(), gone)) {
				ItemStack sword = WitherKnights.sword(RandomSource.create(seed), registry, special);
				ItemEnchantments held = enchantsOf(sword);
				Holder<Enchantment> holder = MobGear.find(registry, special);
				rolls++;
				int wantedSize = WitherKnights.ENCHANTS + (holder == null ? 0 : 1);
				if (!sword.is(WitheringArms.sword()) || held.size() != wantedSize) {
					bad.add("seed " + seed + " " + special + " holds " + held.size() + ", wanted " + wantedSize);
				}
				if (holder != null && held.getLevel(holder) != MobGear.SPECIAL_LEVEL) {
					bad.add("seed " + seed + " " + special + " at level " + held.getLevel(holder));
				}
				List<Holder<Enchantment>> keys = new ArrayList<>(held.keySet());
				for (int i = 0; i < keys.size(); i++) {
					if (keys.get(i).is(WitherDrain.KEY)) {
						bad.add("seed " + seed + " the random pool handed out Wither Drain");
					}
					for (int j = i + 1; j < keys.size(); j++) {
						if (!Enchantment.areCompatible(keys.get(i), keys.get(j))) {
							bad.add("seed " + seed + " holds a clash");
						}
					}
				}
			}
		}
		EndTestSupport.result("knightSwordRoll", "rolls=" + rolls + " bad=" + bad.size());
		helper.assertTrue(bad.isEmpty(), "sword rolls off the spec: " + bad.stream().limit(5).toList());
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:knight_chase", maxTicks = 320)
	public void aKnightMoreThanEightBlocksFromAPlayerBlinksNextToItOnSafeGroundAndBlinksAgainOnlyAfterItsCooldown(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		ServerPlayer player = SovereignGameTest.inArena(helper, end, -9.0, 0.0);
		List<WitherSkeleton> knights = raiseAlone(helper, end);
		WitherSkeleton knight = knights.get(0);
		TeleportChaseGoal chase = CastleTeleportGameTest.goal(knight, TeleportChaseGoal.class);
		int y = EndCastle.standY(EndCastle.FLOORS);
		Vec3 far = new Vec3(9.5, y, 0.5);
		knight.teleportTo(far.x, far.y, far.z);
		helper.onEachTick(() -> knight.setTarget(player));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(!chase.hops().isEmpty(), "no blink yet; distance " + knight.distanceTo(player)))
			.thenExecute(() -> {
				TeleportChaseGoal.Hop hop = chase.hops().get(0);
				double from = hop.from().distanceTo(player.position());
				double reach = Math.hypot(hop.to().x - player.getX(), hop.to().z - player.getZ());
				boolean safe = CastleTeleportGameTest.standable(end, knight, hop.to());
				EndTestSupport.result("knightChase", "from=" + from + " landed=" + reach + " safe=" + safe + " floor=" + EndCastle.floorAt(hop.to().y)
					+ " minDistance=" + WitherKnights.CHASE_DISTANCE + " cooldown=" + WitherKnights.CHASE_COOLDOWN);
				helper.assertTrue(from > WitherKnights.CHASE_DISTANCE, "the knight blinked from only " + from + " blocks away");
				helper.assertTrue(reach >= CastleTeleport.NEAR_MIN - 1.0 && reach <= CastleTeleport.NEAR_MAX + 1.0, "the knight landed " + reach + " blocks from its target");
				helper.assertTrue(safe, "the landing spot " + hop.to() + " is not a safe stand for a knight");
				helper.assertTrue(EndCastle.floorAt(hop.to().y) == EndCastle.FLOORS, "the knight left floor 6");
				knight.teleportTo(far.x, far.y, far.z);
			})
			.thenIdle(40)
			.thenExecute(() -> {
				helper.assertTrue(chase.hops().size() == 1, "the knight blinked again " + chase.hops().size() + " hops in, inside its cooldown");
				EndClock.advance(end, WitherKnights.CHASE_COOLDOWN);
			})
			.thenWaitUntil(() -> helper.assertTrue(chase.hops().size() >= 2, "no second blink after the cooldown; distance " + knight.distanceTo(player)))
			.thenExecute(() -> {
				long gap = chase.hops().get(1).clock() - chase.hops().get(0).clock();
				EndTestSupport.result("knightChaseCooldown", "hops=" + chase.hops().size() + " gap=" + gap);
				EndTestSupport.fresh(helper);
				helper.assertTrue(gap >= WitherKnights.CHASE_COOLDOWN, "the second blink came " + gap + " ticks after the first");
			})
			.thenSucceed();
	}
}
