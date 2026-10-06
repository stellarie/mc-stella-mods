package com.stella.ruins.site.meteorcrater;

import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** The two themed mobs: their kit, the staggered falling-star strike, their targets, and their drop tables. */
public class CraterMobsGameTest {
	static final long SEED = 0xBEEFL;
	static final int TICKS = 6000;
	static final int ROLLS = 4000;
	static final boolean MOBS_PRESENT = FabricLoader.getInstance().isModLoaded("mod-stella-mobs");

	private static Vec3 spot(final BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	private static Mob golem(final CraterTestSupport.Arena arena, final int slot, final Vec3 at) {
		Mob mob = CraterMobs.STAR_GOLEM.spawn(arena.level(), at);
		mob.setNoAi(true);
		mob.addTag(CraterMobs.SLOT_TAG_PREFIX + slot);
		return mob;
	}

	@GameTest(maxTicks = TICKS)
	public void aStarGolemIsAGlowingIronGolemOfScaleOnePointThreeWithTheGolemDrops(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			Mob mob = golem(arena, 0, spot(arena.markers(CraterMarkers.GOLEM).get(0)));
			helper.assertTrue(mob.getType() == EntityTypes.IRON_GOLEM, "the golem is a " + mob.getType());
			double scale = mob.getAttributeValue(Attributes.SCALE);
			helper.assertTrue(Math.abs(scale - 1.3) < 1.0E-6, "scale " + scale);
			helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), "the golem does not glow");
			helper.assertTrue(RuinMobs.kindOf(mob) == CraterMobs.STAR_GOLEM && CraterMobs.STAR_GOLEM.drops().equals(CraterMobs.GOLEM_DROPS), "kind or drop table");
			helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB) && mob.isPersistenceRequired(), "not tagged or not persistent");
			CraterTestSupport.result("golemKit", "scale=" + mob.getAttributeValue(Attributes.SCALE));
			mob.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void anEmberSlimeIsAMagmaCubeOfSizeTwoWithTheEmberDrops(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			Mob mob = CraterMobs.EMBER_SLIME.spawn(arena.level(), spot(arena.markers(CraterMarkers.SLIME).get(0)));
			mob.setNoAi(true);
			helper.assertTrue(mob.getType() == EntityTypes.MAGMA_CUBE, "the slime is a " + mob.getType());
			CraterMobs.EMBER_SLIME.ability().tick(mob, arena.level());
			AbstractCubeMob cube = (AbstractCubeMob) mob;
			helper.assertTrue(cube.getSize() == CraterMobs.SLIME_SIZE, "size " + cube.getSize());
			helper.assertTrue(RuinMobs.kindOf(mob) == CraterMobs.EMBER_SLIME && CraterMobs.EMBER_SLIME.drops().equals(CraterMobs.SLIME_DROPS), "kind or drop table");
			mob.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void theThreeGolemsStrikeFiftyTicksApartAndNeverTogether(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			Vec3 standing = arena.standAt();
			arena.join(standing);
			Mob[] golems = new Mob[3];
			for (int slot = 0; slot < 3; slot++) {
				golems[slot] = golem(arena, slot, standing.add(4.0 + slot, 0.0, 4.0));
			}
			List<String> log = new ArrayList<>();
			for (int tick = 0; tick <= 330; tick += RuinMobs.ABILITY_TICKS) {
				for (int slot = 0; slot < 3; slot++) {
					golems[slot].tickCount = tick;
					CraterMobs.STAR_GOLEM.ability().tick(golems[slot], level);
				}
				for (int slot = 0; slot < 3; slot++) {
					if (CraterMobs.StarStrike.pending(golems[slot]) != null && !log.contains("slot" + slot)) {
						log.add("slot" + slot);
						helper.assertTrue(tick == CraterMobs.STRIKE_TICKS + slot * CraterMobs.STRIKE_STAGGER, "golem " + slot + " marked at tick " + tick);
						helper.assertTrue(CraterMobs.StarStrike.offset(slot) == slot * CraterMobs.STRIKE_STAGGER, "golem " + slot + " offset " + CraterMobs.StarStrike.offset(slot));
					}
				}
			}
			CraterTestSupport.result("stagger", "marks=" + log + " offsets=0," + CraterMobs.StarStrike.offset(1) + "," + CraterMobs.StarStrike.offset(2));
			helper.assertTrue(CraterMobs.STRIKE_STAGGER == 50 && CraterMobs.STRIKE_TICKS == 160, "the stagger is " + CraterMobs.STRIKE_STAGGER + " of " + CraterMobs.STRIKE_TICKS);
			helper.assertTrue(log.size() == 3, "only " + log + " marked a strike");
			for (Mob golem : golems) {
				golem.discard();
			}
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aStrikeWarnsForTwentyTicksThenDealsFiveDamageAtTheMarkedSpot(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt());
			CraterTestSupport.vulnerable(player);
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
			player.setHealth(1000.0F);
			Mob mob = golem(arena, 0, arena.standAt().add(5.0, 0.0, 5.0));
			mob.tickCount = CraterMobs.STRIKE_TICKS;
			CraterMobs.STAR_GOLEM.ability().tick(mob, level);
			Vec3 marked = CraterMobs.StarStrike.pending(mob);
			helper.assertTrue(marked != null && marked.distanceTo(player.position()) < 1.0E-6, "the strike is not marked at the player: " + marked);
			float before = player.getHealth();
			mob.tickCount = CraterMobs.STRIKE_TICKS + CraterMobs.WARNING_TICKS - 5;
			CraterMobs.STAR_GOLEM.ability().tick(mob, level);
			helper.assertTrue(player.getHealth() == before && CraterMobs.StarStrike.pending(mob) != null, "the strike landed before its warning ended");
			mob.tickCount = CraterMobs.STRIKE_TICKS + CraterMobs.WARNING_TICKS;
			CraterMobs.STAR_GOLEM.ability().tick(mob, level);
			float lost = before - player.getHealth();
			CraterTestSupport.result("strike", "marked=" + marked + " lost=" + lost + " level=" + com.stella.ruins.world.RuinLevels.of(mob));
			helper.assertTrue(CraterMobs.StarStrike.pending(mob) == null, "the strike is still pending");
			// A ruin mob's hit follows its level with the mobs mod, so the control is the same golem hitting a cow
			// for 5 points. A player can only lose the health it has.
			net.minecraft.world.entity.animal.cow.Cow cow = CraterTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
			cow.damageCooldownTime = 0;
			float cowBefore = cow.getHealth();
			cow.hurtServer(level, level.damageSources().indirectMagic(mob, mob), CraterMobs.STRIKE_DAMAGE);
			float cowLost = cowBefore - cow.getHealth();
			cow.discard();
			float expected = Math.min(1000.0F, cowLost);
			CraterTestSupport.result("strikeControl", "strike=" + lost + " cowControl=" + cowLost + " expected=" + expected);
			helper.assertTrue(Math.abs(lost - expected) < 1.0E-3F, "the strike did " + lost + " but the same golem's 5 point hit did " + cowLost);
			helper.assertTrue(MOBS_PRESENT ? lost > CraterMobs.STRIKE_DAMAGE : Math.abs(lost - CraterMobs.STRIKE_DAMAGE) < 1.0E-4F, "the strike did " + lost);
			mob.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aPlayerWhoLeavesTheMarkedSpotTakesNoDamage(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt());
			CraterTestSupport.vulnerable(player);
			Mob mob = golem(arena, 0, arena.standAt().add(5.0, 0.0, 5.0));
			mob.tickCount = CraterMobs.STRIKE_TICKS;
			CraterMobs.STAR_GOLEM.ability().tick(mob, level);
			float before = player.getHealth();
			Vec3 at = player.position();
			player.snapTo(at.x + CraterMobs.STRIKE_RADIUS + 1.0, at.y, at.z);
			mob.tickCount = CraterMobs.STRIKE_TICKS + CraterMobs.WARNING_TICKS;
			CraterMobs.STAR_GOLEM.ability().tick(mob, level);
			helper.assertTrue(player.getHealth() == before, "the player took " + (before - player.getHealth()) + " after stepping out");
			mob.discard();
		});
	}

	@GameTest(maxTicks = TICKS)
	public void aGolemHuntsPlayersOnlyAndForgetsAMobTarget(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt());
			Mob golem = golem(arena, 0, arena.standAt().add(6.0, 0.0, 0.0));
			Mob slime = CraterMobs.EMBER_SLIME.spawn(level, arena.standAt().add(8.0, 0.0, 0.0));
			slime.setNoAi(true);
			golem.setTarget(slime);
			slime.setTarget(golem);
			CraterMobs.STAR_GOLEM.ability().tick(golem, level);
			CraterMobs.EMBER_SLIME.ability().tick(slime, level);
			helper.assertTrue(golem.getTarget() == player, "the golem targets " + golem.getTarget());
			helper.assertTrue(slime.getTarget() == null, "the slime still targets " + slime.getTarget());
			CraterTestSupport.result("targets", "golem=" + golem.getTarget().getName().getString() + " slime=null");
			golem.discard();
			slime.discard();
		});
	}

	/**
	 * Item 1. A star golem carries the published foe mark, and the mark follows the kind's own flag, so a kind that
	 * does not opt in carries nothing.
	 */
	@GameTest(maxTicks = TICKS)
	public void aStarGolemCarriesThePublishedHostileMarkAndTheSlimeDoesNot(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			Mob golem = CraterMobs.STAR_GOLEM.spawn(arena.level(), spot(arena.markers(CraterMarkers.GOLEM).get(0)));
			golem.setNoAi(true);
			Mob slime = CraterMobs.EMBER_SLIME.spawn(arena.level(), spot(arena.markers(CraterMarkers.SLIME).get(0)));
			slime.setNoAi(true);
			helper.assertTrue(CraterMobs.STAR_GOLEM.hostile(), "the star golem kind is not marked hostile");
			helper.assertTrue(golem.entityTags().contains(RuinMobKind.TAG_HOSTILE), "the golem tags are " + golem.entityTags());
			helper.assertTrue("mod-stella-ruins.hostile".equals(RuinMobKind.TAG_HOSTILE), "the published mark is " + RuinMobKind.TAG_HOSTILE);
			helper.assertTrue(!slime.entityTags().contains(RuinMobKind.TAG_HOSTILE), "a kind that did not opt in carries the mark: " + slime.entityTags());
			helper.assertTrue(!CraterMobs.EMBER_SLIME.hostile(), "the ember slime kind is marked hostile");
			CraterTestSupport.result("hostileMark", "golemTags=" + golem.entityTags().size() + " mark=" + RuinMobKind.TAG_HOSTILE + " slimeMarked=" + slime.entityTags().contains(RuinMobKind.TAG_HOSTILE));
			golem.discard();
			slime.discard();
		});
	}

	/**
	 * Item 1, the fight. A golem beside a player turns on that player, and its own blow takes health. The mark alone
	 * is not hostile: the golem must land a hit. The strike window is driven through the golem's own tick count,
	 * because an entity in a test arena does not advance it.
	 */
	@GameTest(maxTicks = TICKS)
	public void aGolemBesideAPlayerTurnsOnItAndItsBlowTakesHealth(final GameTestHelper helper) {
		CraterTestSupport.inArena(helper, SEED, arena -> {
			ServerLevel level = arena.level();
			ServerPlayer player = arena.join(arena.standAt());
			CraterTestSupport.vulnerable(player);
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024.0);
			player.setHealth(1024.0F);
			Mob golem = CraterMobs.STAR_GOLEM.spawn(level, arena.standAt().add(2.0, 0.0, 0.0));
			boolean targets = false;
			boolean marked = false;
			for (int tick = 0; tick <= CraterMobs.STRIKE_TICKS && !marked; tick++) {
				golem.tickCount = tick;
				CraterMobs.STAR_GOLEM.ability().tick(golem, level);
				targets |= golem.getTarget() == player;
				marked = CraterMobs.StarStrike.pending(golem) != null;
			}
			float before = player.getHealth();
			player.damageCooldownTime = 0;
			boolean landed = golem.doHurtTarget(level, player);
			float lost = before - player.getHealth();
			CraterTestSupport.result("hostileFight", "targeted=" + targets + " marked=" + marked + " landed=" + landed + " healthLost=" + lost
				+ " health=" + player.getHealth() + "/" + player.getMaxHealth());
			helper.assertTrue(targets, "the golem beside the player targets " + golem.getTarget());
			helper.assertTrue(marked, "the golem never marked its strike at the player");
			helper.assertTrue(landed && lost > 0.0F, "the golem's blow landed=" + landed + " and took " + lost + " health");
			helper.assertTrue(player.isAlive(), "the subject player died, so the health reading proves nothing");
			golem.discard();
		});
	}

	private static List<ItemStack> roll(final ServerLevel level, final ResourceKey<LootTable> key, final long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		Mob mob = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
			.withParameter(LootContextParams.THIS_ENTITY, mob).withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
			.create(LootContextParamSets.ENTITY);
		return table.getRandomItems(params, seed);
	}

	@GameTest
	public void eachMobKindDropsItsExclusiveItemAtItsTableRate(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RandomSource master = RandomSource.create(20261006L);
		int golemRolls = 0;
		int fragments = 0;
		boolean one = false;
		boolean two = false;
		int slimeRolls = 0;
		for (int i = 0; i < ROLLS; i++) {
			for (ItemStack stack : roll(level, CraterMobs.GOLEM_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(CraterItems.STAR_FRAGMENT), "the golem dropped " + stack);
				helper.assertTrue(stack.getCount() >= 1 && stack.getCount() <= 2, "fragment count " + stack.getCount());
				golemRolls++;
				fragments += stack.getCount();
				one |= stack.getCount() == 1;
				two |= stack.getCount() == 2;
			}
			for (ItemStack stack : roll(level, CraterMobs.SLIME_DROPS, master.nextLong())) {
				helper.assertTrue(stack.is(CraterItems.EMBER_CORE) && stack.getCount() == 1, "the slime dropped " + stack);
				slimeRolls++;
			}
		}
		double golemRate = golemRolls / (double) ROLLS;
		double slimeRate = slimeRolls / (double) ROLLS;
		CraterTestSupport.result("mobDrops", "rolls=" + ROLLS + " fragmentRate=" + golemRate + " coreRate=" + slimeRate + " fragments=" + fragments);
		helper.assertTrue(Math.abs(golemRate - 0.70) <= 0.70 * 0.05, "fragment rate " + golemRate);
		helper.assertTrue(Math.abs(slimeRate - 0.40) <= 0.40 * 0.05, "ember core rate " + slimeRate);
		helper.assertTrue(one && two, "fragment counts did not cover 1 and 2");
		helper.succeed();
	}
}
