package com.stella.ruins.site.ruinedkeep;

import com.stella.ruins.world.RuinMobKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Acceptance items 8 and 11: the four mob kinds, the warden's charge, the Castellan's summon and cleave, and the drop rates. */
public class KeepMobGameTest {
	static final int ROLLS = 20_000;

	private static Mob spawn(final GameTestHelper helper, final RuinMobKind kind, final double x, final double z) {
		Mob mob = kind.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, z)));
		mob.setNoAi(true);
		return mob;
	}

	private static Mob plain(final GameTestHelper helper, final EntityType<? extends Mob> type) {
		return type.create(helper.getLevel(), EntitySpawnReason.EVENT);
	}

	@GameTest
	public void everyKindBuildsWithItsScaleHealthGearAndEffect(final GameTestHelper helper) {
		Mob sentinel = spawn(helper, KeepMobs.sentinel, 2.5, 2.5);
		Mob knight = spawn(helper, KeepMobs.knight, 3.5, 2.5);
		Mob warden = spawn(helper, KeepMobs.warden, 4.5, 2.5);
		Mob castellan = spawn(helper, KeepMobs.castellan, 5.5, 2.5);
		try {
			helper.assertTrue(sentinel.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && sentinel.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "the sentinel lacks iron armor or a shield");
			helper.assertTrue(sentinel.getType() == EntityTypes.ZOMBIE, "the sentinel is not a zombie");
			helper.assertTrue(knight.getType() == EntityTypes.SKELETON && knight.getItemBySlot(EquipmentSlot.CHEST).is(Items.CHAINMAIL_CHESTPLATE), "the knight is not a chain-armored skeleton");
			helper.assertTrue(knight.hasEffect(MobEffects.GLOWING), "the knight does not glow");
			helper.assertTrue(Math.abs(knight.getAttributeValue(Attributes.SCALE) - 1.1) < 1.0E-6, "the knight scale is " + knight.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(warden.getType() == EntityTypes.VINDICATOR, "the warden is not a vindicator");
			helper.assertTrue(Math.abs(warden.getAttributeValue(Attributes.SCALE) - 1.3) < 1.0E-6, "the warden scale is " + warden.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(warden.getMaxHealth() - plain(helper, EntityTypes.VINDICATOR).getMaxHealth() * 4.0F) < 1.0E-3, "the warden health is " + warden.getMaxHealth());
			helper.assertTrue(castellan.getType() == EntityTypes.WITHER_SKELETON && castellan.getMainHandItem().is(Items.NETHERITE_SWORD), "the Castellan is not a wither skeleton with a netherite sword");
			helper.assertTrue(Math.abs(castellan.getAttributeValue(Attributes.SCALE) - 2.0) < 1.0E-6, "the Castellan scale is " + castellan.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(Math.abs(castellan.getMaxHealth() - plain(helper, EntityTypes.WITHER_SKELETON).getMaxHealth() * 8.0F) < 1.0E-3, "the Castellan health is " + castellan.getMaxHealth());
			for (Mob mob : List.of(sentinel, knight, warden, castellan)) {
				helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB) && mob.entityTags().contains(RuinMobKind.NO_SPAWN_ROLL_TAG), "a keep mob lacks its ruin tags");
			}
			KeepTestSupport.result("kinds", "sentinelHp=" + sentinel.getMaxHealth() + " knightScale=1.1 wardenHp=" + warden.getMaxHealth() + " castellanHp=" + castellan.getMaxHealth());
		} finally {
			List.of(sentinel, knight, warden, castellan).forEach(Mob::discard);
		}
		helper.succeed();
	}

	@GameTest
	public void aSpectralKnightsHitGivesWeaknessOneForSixtyTicks(final GameTestHelper helper) {
		Mob knight = spawn(helper, KeepMobs.knight, 2.5, 2.5);
		Cow cow = KeepTestSupport.cow(helper, 4.5, 2.5, 1000.0F, 1000.0F);
		try {
			helper.assertTrue(!cow.hasEffect(MobEffects.WEAKNESS), "the target was weak before the hit");
			// With the mobs mod a ruin mob hits at its level, which kills any victim, and a death fires no hit event.
			// There the test calls the hook the hit event would call.
			boolean sibling = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mod-stella-mobs");
			if (sibling) {
				KeepMobs.knight.ability().onHurtTarget(knight, cow);
			} else {
				cow.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(knight), 1.0F);
			}
			MobEffectInstance weakness = cow.getEffect(MobEffects.WEAKNESS);
			KeepTestSupport.result("knightWeakness", "path=" + (sibling ? "hook" : "damage") + " effect=" + (weakness == null ? "none" : "amp" + weakness.getAmplifier() + " ticks" + weakness.getDuration()));
			helper.assertTrue(weakness != null, "the knight's hit gave no weakness");
			helper.assertTrue(weakness.getAmplifier() == 0, "the weakness amplifier is " + weakness.getAmplifier());
			helper.assertTrue(weakness.getDuration() > 0 && weakness.getDuration() <= KeepMobs.WEAKNESS_TICKS, "the weakness lasts " + weakness.getDuration() + " ticks");
		} finally {
			knight.discard();
			cow.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void aWardenChargesTowardItsTargetOnceEveryOneHundredTwentyTicks(final GameTestHelper helper) {
		Mob warden = spawn(helper, KeepMobs.warden, 2.5, 2.5);
		Cow cow = KeepTestSupport.cow(helper, 7.5, 2.5, 20.0F, 20.0F);
		try {
			warden.setTarget(cow);
			int charges = 0;
			List<Integer> at = new ArrayList<>();
			for (int tick = 0; tick <= 245; tick += 5) {
				warden.tickCount = tick;
				warden.setDeltaMovement(Vec3.ZERO);
				KeepMobs.warden.ability().tick(warden, helper.getLevel());
				if (warden.getDeltaMovement().horizontalDistance() > 0.5) {
					charges++;
					at.add(tick);
					helper.assertTrue(warden.getDeltaMovement().x > 1.0 && Math.abs(warden.getDeltaMovement().z) < 0.5, "the charge at tick " + tick + " heads " + warden.getDeltaMovement() + ", not toward the target");
				}
			}
			KeepTestSupport.result("wardenCharge", "chargesIn245Ticks=" + charges + " at=" + at);
			helper.assertTrue(charges == 2 && at.equals(List.of(KeepMobs.CHARGE_TICKS, KeepMobs.CHARGE_TICKS * 2)), "the warden charged at " + at);
			warden.setTarget(null);
			warden.tickCount = KeepMobs.CHARGE_TICKS;
			warden.setDeltaMovement(Vec3.ZERO);
			KeepMobs.warden.ability().tick(warden, helper.getLevel());
			helper.assertTrue(warden.getDeltaMovement().horizontalDistance() < 0.1, "the warden charged with no target");
		} finally {
			warden.discard();
			cow.discard();
		}
		helper.succeed();
	}

	private static int summoned(final ServerLevel level, final Mob castellan) {
		return level.getEntitiesOfClass(Mob.class, castellan.getBoundingBox().inflate(40.0), m -> m.isAlive() && m.entityTags().contains(KeepMobs.SUMMON_TAG)).size();
	}

	@GameTest
	public void theCastellanSummonsTwoSentinelsEveryThreeHundredTicksWhileAboveHalfHealth(final GameTestHelper helper) {
		Mob castellan = spawn(helper, KeepMobs.castellan, 4.5, 4.5);
		ServerLevel level = helper.getLevel();
		try {
			castellan.tickCount = 295;
			KeepMobs.castellan.ability().tick(castellan, level);
			helper.assertTrue(summoned(level, castellan) == 0, "a summon came before tick 300");
			castellan.tickCount = KeepMobs.SUMMON_TICKS;
			KeepMobs.castellan.ability().tick(castellan, level);
			int first = summoned(level, castellan);
			castellan.tickCount = KeepMobs.SUMMON_TICKS + 5;
			KeepMobs.castellan.ability().tick(castellan, level);
			int between = summoned(level, castellan);
			castellan.tickCount = KeepMobs.SUMMON_TICKS * 2;
			KeepMobs.castellan.ability().tick(castellan, level);
			int second = summoned(level, castellan);
			helper.assertTrue(first == 2 && between == 2 && second == 4, "summons were " + first + ", " + between + ", " + second + ", not 2, 2, 4");
			List<Mob> kids = level.getEntitiesOfClass(Mob.class, castellan.getBoundingBox().inflate(40.0), m -> m.entityTags().contains(KeepMobs.SUMMON_TAG));
			for (Mob kid : kids) {
				helper.assertTrue(kid.entityTags().contains(RuinMobKind.TAG_KIND_PREFIX + "keep_sentinel"), "a summon is not a keep sentinel");
				helper.assertTrue(kid.entityTags().contains(com.stella.ruins.world.RuinMobs.TAG_SUMMONED), "a summon does not carry the summoned tag");
			}
			// At half health or less, the summons stop.
			castellan.setHealth(castellan.getMaxHealth() * 0.5F);
			castellan.tickCount = KeepMobs.SUMMON_TICKS * 3;
			KeepMobs.castellan.ability().tick(castellan, level);
			int weak = summoned(level, castellan);
			helper.assertTrue(weak == 4, "the Castellan at half health summoned, now " + weak);
			// The cap holds.
			castellan.setHealth(castellan.getMaxHealth());
			for (int i = 4; i <= 8; i++) {
				castellan.tickCount = KeepMobs.SUMMON_TICKS * i;
				KeepMobs.castellan.ability().tick(castellan, level);
			}
			helper.assertTrue(summoned(level, castellan) == KeepMobs.SUMMON_CAP, "summons stand at " + summoned(level, castellan) + ", not the cap " + KeepMobs.SUMMON_CAP);
			KeepTestSupport.result("castellanSummon", "first=" + first + " between=" + between + " second=" + second + " atHalf=" + weak + " cap=" + KeepMobs.SUMMON_CAP);
			kids.forEach(Mob::discard);
			level.getEntitiesOfClass(Mob.class, castellan.getBoundingBox().inflate(40.0), m -> m.entityTags().contains(KeepMobs.SUMMON_TAG)).forEach(Mob::discard);
		} finally {
			castellan.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void theCastellanCleavesPlayersWithinThreeBlocksAndNoOneFarther(final GameTestHelper helper) {
		Mob castellan = spawn(helper, KeepMobs.castellan, 4.5, 4.5);
		ServerPlayer near = KeepTestSupport.joinPlayer(helper, 4.5, 6.5);
		ServerPlayer far = KeepTestSupport.joinPlayer(helper, 4.5, 11.5);
		KeepTestSupport.vulnerable(near);
		KeepTestSupport.vulnerable(far);
		try {
			ServerLevel level = helper.getLevel();
			float nearBefore = near.getHealth();
			float farBefore = far.getHealth();
			castellan.tickCount = 45;
			KeepMobs.castellan.ability().tick(castellan, level);
			helper.assertTrue(near.getHealth() == nearBefore, "a cleave landed off the cycle");
			castellan.tickCount = KeepMobs.CLEAVE_TICKS;
			near.damageCooldownTime = 0;
			KeepMobs.castellan.ability().tick(castellan, level);
			float lost = nearBefore - near.getHealth();
			KeepTestSupport.result("castellanCleave", "nearLost=" + lost + " farLost=" + (farBefore - far.getHealth()) + " distNear=" + near.distanceTo(castellan) + " distFar=" + far.distanceTo(castellan));
			helper.assertTrue(lost > 0.0F, "the cleave left the near player unhurt");
			helper.assertTrue(far.getHealth() == farBefore, "the cleave reached a player " + far.distanceTo(castellan) + " blocks away");
		} finally {
			castellan.discard();
			KeepTestSupport.leave(helper, near);
			KeepTestSupport.leave(helper, far);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 6000)
	public void aSummonJoinsTheRosterSoTheFightCountsIt(final GameTestHelper helper) {
		KeepTestSupport.inArena(helper, arena -> {
			ServerPlayer player = arena.wake();
			net.minecraft.world.item.ItemStack fragments = new ItemStack(KeepRewards.keyFragment, 3);
			for (int i = 0; i < 3; i++) {
				KeepObjectiveGameTest.useCore(arena, player, fragments);
			}
			arena.freeze();
			Mob castellan = arena.roster(KeepMobs.castellan).get(0);
			int before = arena.entry().roster().size();
			castellan.tickCount = KeepMobs.SUMMON_TICKS;
			KeepMobs.castellan.ability().tick(castellan, arena.level());
			int gained = arena.entry().roster().size() - before;
			helper.assertTrue(gained == KeepMobs.SUMMON_COUNT, "the roster gained " + gained + " from a summon, not " + KeepMobs.SUMMON_COUNT);
			KeepTestSupport.result("summonInRoster", "gained=" + gained);
		});
	}

	private static double rate(final GameTestHelper helper, final ResourceKey<LootTable> key, final net.minecraft.world.item.Item item, final EntityType<? extends Mob> type, final int[] counts) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Mob subject = plain(helper, type);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.THIS_ENTITY, subject)
			.withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(LootContextParamSets.ENTITY);
		net.minecraft.util.RandomSource seeds = net.minecraft.util.RandomSource.create(777_000L);
		int hits = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (int i = 0; i < ROLLS; i++) {
			int count = 0;
			for (ItemStack stack : table.getRandomItems(params, seeds.nextLong())) {
				if (stack.is(item)) {
					count += stack.getCount();
				}
			}
			hits += count > 0 ? 1 : 0;
			min = Math.min(min, count);
			max = Math.max(max, count);
		}
		counts[0] = min;
		counts[1] = max;
		return (double) hits / ROLLS;
	}

	@GameTest(maxTicks = 400)
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		int[] c = new int[2];
		double crest = rate(helper, KeepMobs.SENTINEL_DROPS, KeepRewards.tarnishedCrest, EntityTypes.ZOMBIE, c);
		double thread = rate(helper, KeepMobs.KNIGHT_DROPS, KeepRewards.spectralThread, EntityTypes.SKELETON, c);
		double fragment = rate(helper, KeepMobs.WARDEN_DROPS, KeepRewards.keyFragment, EntityTypes.VINDICATOR, c);
		int fragMin = c[0];
		int fragMax = c[1];
		double bossCrest = rate(helper, KeepMobs.CASTELLAN_DROPS, KeepRewards.tarnishedCrest, EntityTypes.WITHER_SKELETON, c);
		KeepTestSupport.result("dropRates", "rolls=" + ROLLS + " sentinelCrest=" + crest + " knightThread=" + thread + " wardenFragment=" + fragment + " fragCount=" + fragMin + ".." + fragMax
			+ " castellanCrest=" + bossCrest + " crestCount=" + c[0] + ".." + c[1]);
		helper.assertTrue(Math.abs(crest - 0.4) < 0.02, "the sentinel dropped a crest at " + crest + ", not 40%");
		helper.assertTrue(Math.abs(thread - 0.4) < 0.02, "the knight dropped thread at " + thread + ", not 40%");
		helper.assertTrue(fragment == 1.0 && fragMin == 1 && fragMax == 1, "the warden dropped a fragment at " + fragment + " in counts " + fragMin + ".." + fragMax + ", not 1 at 100%");
		helper.assertTrue(bossCrest == 1.0 && c[0] == 3 && c[1] == 5, "the Castellan's crests are " + c[0] + ".." + c[1] + " at " + bossCrest + ", not 3 to 5 always");
		helper.succeed();
	}
}
