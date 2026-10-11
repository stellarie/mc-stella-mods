package com.stella.tower;

import com.stella.mobs.behavior.TrueInvisibility;
import com.stella.equips.refine.RefineStation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the ghost merchant sells, used outside the tower, where no merchant stands. Each good does its work anywhere; only the
 * sale is held to the tower (see {@link TowerMerchantGameTest}).
 */
public class MerchantGoodsGameTest {
	private static final long NIGHT = 18000L;

	private static MerchantGoods.Sale saleTo(final GameTestHelper helper, final ServerPlayer player, final long seed) {
		return new MerchantGoods.Sale(helper.getLevel(), player, RandomSource.create(seed));
	}

	/**
	 * A skeleton, which the mobs mod's invisibility leaves seeing an invisible player, takes a player in plain sight; then not
	 * when the player has drunk the potion, for the whole of a minute's first seconds; then again once the player strikes.
	 */
	@GameTest(maxTicks = 500)
	public void thePerfectInvisibilityPotionHidesAPlayerFromASkeletonUntilTheyStrike(final GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.setTime(NIGHT);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		TowerTestSupport.vulnerable(player);
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		TowerTestSupport.unrolled(skeleton).addTag(com.stella.mobs.StellaMobs.NO_BEHAVIOR_TAG);
		skeleton.setPersistenceRequired();
		skeleton.snapTo(helper.absoluteVec(new Vec3(5.5, 1.0, 2.5)));
		level.addFreshEntity(skeleton);
		Zombie victim = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerTestSupport.unrolled(victim).setNoAi(true);
		victim.setPersistenceRequired();
		victim.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 6.5)));
		victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100_000.0);
		victim.setHealth(100_000.0F);
		level.addFreshEntity(victim);
		int[] held = {0, 0, 0};
		int[] phase = {0};
		helper.onEachTick(() -> {
			player.setInvulnerableTime(0);
			held[phase[0]] += skeleton.getTarget() == player ? 1 : 0;
		});
		int[] minutes = {-1};
		helper.runAfterDelay(100L, () -> {
			skeleton.setTarget(null);
			ItemStack potion = PerfectVeil.potion();
			potion.finishUsingItem(level, player);
			MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
			minutes[0] = invisibility == null ? -1 : invisibility.getDuration();
			phase[0] = 1;
		});
		helper.runAfterDelay(300L, () -> {
			victim.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
			phase[0] = 2;
		});
		helper.runAfterDelay(450L, () -> {
			TowerTestSupport.result("perfectInvisibility", "heldBefore=" + held[0] + " heldHidden=" + held[1] + " heldAfterStrike=" + held[2]
				+ " duration=" + minutes[0] + " revealed=" + TrueInvisibility.isRevealed(player));
			skeleton.discard();
			victim.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(held[0] > 0, "the skeleton never took the visible player, so the case proves nothing");
			helper.assertTrue(minutes[0] >= PerfectVeil.TICKS - 2 && minutes[0] <= PerfectVeil.TICKS, "the potion gave " + minutes[0] + " ticks of invisibility");
			helper.assertTrue(held[1] == 0, "the skeleton held the hidden player " + held[1] + " ticks");
			helper.assertTrue(held[2] > 0, "the skeleton never saw the player again after a blow");
			helper.succeed();
		});
	}

	/** Catches a tonic that heals less than in full or gives the wrong regeneration. */
	@GameTest(maxTicks = 40)
	public void theSoulTonicHealsInFullAndGivesRegenerationThreeAnywhere(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		TowerTestSupport.vulnerable(player);
		player.setHealth(1.0F);
		ItemStack tonic = MerchantGoods.SOUL_TONIC.make().apply(saleTo(helper, player, 1L));
		tonic.finishUsingItem(helper.getLevel(), player);
		MobEffectInstance regeneration = player.getEffect(MobEffects.REGENERATION);
		TowerTestSupport.result("soulTonic", "health=" + player.getHealth() + "/" + player.getMaxHealth() + " regeneration="
			+ (regeneration == null ? "none" : regeneration.getAmplifier() + ":" + regeneration.getDuration()));
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the tonic healed to " + player.getHealth());
		helper.assertTrue(regeneration != null && regeneration.getAmplifier() == SoulTonicItem.REGENERATION_AMPLIFIER
			&& regeneration.getDuration() >= SoulTonicItem.REGENERATION_TICKS - 2, "the tonic gave the wrong regeneration");
		helper.succeed();
	}

	/**
	 * Thirty-two phantom arrows, a bow takes them, and one shot through a line of four zombies hurts the first three and not the
	 * fourth: spectral, and through two bodies after the first.
	 */
	@GameTest(maxTicks = 120)
	public void phantomArrowsAreSpectralAndPierceTwoFromAnyBow(final GameTestHelper helper) {
		helper.setTime(NIGHT);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		ServerPlayer player = TowerTestSupport.join(helper, 0.5, 3.5);
		TowerTestSupport.vulnerable(player);
		ItemStack arrows = MerchantGoods.PHANTOM_ARROWS_GOOD.make().apply(saleTo(helper, player, 2L));
		boolean bowTakesThem = ((ProjectileWeaponItem) Items.BOW).getAllSupportedProjectiles().test(arrows);
		AbstractArrow arrow = ((ArrowItem) arrows.getItem()).createArrow(level, arrows, player, new ItemStack(Items.BOW));
		List<Zombie> line = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			TowerTestSupport.unrolled(zombie).setNoAi(true);
			zombie.setPersistenceRequired();
			zombie.setNoGravity(true);
			zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
			zombie.setHealth(1000.0F);
			zombie.snapTo(helper.absoluteVec(new Vec3(2.5 + i * 1.5, 1.0, 3.5)));
			level.addFreshEntity(zombie);
			line.add(zombie);
		}
		arrow.setPos(player.getX(), player.getY() + 1.2, player.getZ());
		arrow.shoot(1.0, 0.0, 0.0, 3.0F, 0.0F);
		level.addFreshEntity(arrow);
		helper.runAfterDelay(30L, () -> {
			List<Boolean> hurt = line.stream().map(zombie -> zombie.getHealth() < zombie.getMaxHealth()).toList();
			TowerTestSupport.result("phantomArrows", "count=" + arrows.getCount() + " spectral=" + (arrow instanceof SpectralArrow) + " pierce=" + arrow.getPierceLevel()
				+ " bow=" + bowTakesThem + " hurt=" + hurt);
			line.forEach(Zombie::discard);
			arrow.discard();
			TowerTestSupport.leave(helper, player);
			helper.assertTrue(arrows.getCount() == MerchantGoods.PHANTOM_ARROWS, "the good is " + arrows.getCount() + " arrows");
			helper.assertTrue(bowTakesThem, "a bow does not take a phantom arrow");
			helper.assertTrue(arrow instanceof SpectralArrow && arrow.getPierceLevel() == PhantomArrowItem.PIERCE, "the arrow is not a spectral arrow of pierce 2");
			helper.assertTrue(hurt.equals(List.of(true, true, true, false)), "the arrow hurt " + hurt);
			helper.succeed();
		});
	}

	/**
	 * The lantern gives full light, schedules its scan when it is set down, and its scan makes an invisible mob within sixteen
	 * blocks glow; one beyond, and a visible one, do not. The scan is run by hand: the game test level's clock barely moves, so a
	 * scheduled tick would not come in time (see the repository notes).
	 */
	@GameTest(maxTicks = 300)
	public void aGhostLanternLightsAndRevealsInvisibleMobsWithinSixteenBlocks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A standing lantern needs a block under it, or the next update takes it.
		helper.setBlock(4, 1, 4, net.minecraft.world.level.block.Blocks.STONE);
		BlockPos spot = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockState lantern = MerchantItems.GHOST_LANTERN_BLOCK.defaultBlockState();
		level.setBlock(spot, lantern, 3);
		boolean scheduled = level.getBlockTicks().hasScheduledTick(spot, MerchantItems.GHOST_LANTERN_BLOCK);
		Vec3 centre = Vec3.atCenterOf(spot);
		// The far mob stands outside the test structure, in chunks that nothing else here holds loaded.
		TowerTestSupport.holdRoom(level, centre);
		TowerTestSupport.holdRoom(level, centre.add(GhostLanternBlock.RANGE + 6.0, 0.0, 0.0));
		Zombie near = hiddenZombie(level, centre.add(3.0, 0.0, 0.0), true);
		Zombie far = hiddenZombie(level, centre.add(GhostLanternBlock.RANGE + 6.0, 0.0, 0.0), true);
		Zombie seen = hiddenZombie(level, centre.add(0.0, 0.0, 3.0), false);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(TowerTestSupport.roomLoaded(level, far.position()) && TowerTestSupport.roomLoaded(level, near.position()), "the mobs' chunks are not loaded yet");
			if (!done[0]) {
				done[0] = true;
				lantern.tick(level, spot, level.getRandom());
				boolean nearGlows = near.hasEffect(MobEffects.GLOWING);
				boolean farGlows = far.hasEffect(MobEffects.GLOWING);
				boolean seenGlows = seen.hasEffect(MobEffects.GLOWING);
				int light = lantern.getLightEmission();
				TowerTestSupport.result("ghostLantern", "tick=" + helper.getTick() + " light=" + light + " scheduled=" + scheduled + " near=" + nearGlows + " far=" + farGlows + " visible=" + seenGlows);
				near.discard();
				far.discard();
				seen.discard();
				level.setBlock(spot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				helper.assertTrue(light == 15, "the lantern gives light " + light);
				helper.assertTrue(scheduled, "the lantern did not schedule its scan when it was set down");
				helper.assertTrue(nearGlows, "an invisible mob three blocks off did not glow");
				helper.assertFalse(farGlows, "an invisible mob beyond sixteen blocks glowed");
				helper.assertFalse(seenGlows, "a visible mob glowed");
			}
		});
	}

	private static Zombie hiddenZombie(final ServerLevel level, final Vec3 at, final boolean invisible) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerTestSupport.unrolled(zombie).setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.setNoGravity(true);
		zombie.snapTo(at);
		if (invisible) {
			zombie.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1000, 0, false, false));
		}
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** The charm saves its bearer from one killing blow at half health, then cannot until its ten minutes are out. */
	@GameTest(maxTicks = 40)
	public void anEctoplasmCharmCheatsDeathOnceAndThenNeedsTenMinutes(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		TowerTestSupport.vulnerable(player);
		ItemStack charm = MerchantGoods.ECTOPLASM_CHARM.make().apply(saleTo(helper, player, 3L));
		player.getInventory().add(charm);
		player.hurtServer(level, level.damageSources().generic(), 1_000_000.0F);
		boolean savedFirst = player.isAlive() && player.getHealth() > 0.0F;
		float healthAfter = player.getHealth();
		boolean cooling = player.getCooldowns().isOnCooldown(new ItemStack(MerchantItems.ECTOPLASM_CHARM));
		player.setInvulnerableTime(0);
		// The player's hurt cooldown still holds the first blow, so the second must be the larger to count.
		boolean landedSecond = player.hurtServer(level, level.damageSources().generic(), 3_000_000.0F);
		boolean diedSecond = !player.isAlive() || player.getHealth() <= 0.0F;
		TowerTestSupport.result("ectoplasmCharm", "savedFirst=" + savedFirst + " health=" + healthAfter + "/" + player.getMaxHealth() + " cooling=" + cooling + " diedSecond=" + diedSecond + " healthAfterSecond=" + player.getHealth() + " landedSecond=" + landedSecond + " invulnerable=" + player.isInvulnerable() + " effects=" + player.getActiveEffects().size());
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(savedFirst && Math.abs(healthAfter - player.getMaxHealth() * EctoplasmCharm.HEALTH_SHARE) < 0.01F, "the first killing blow was not cheated: health " + healthAfter);
		helper.assertTrue(cooling, "the charm is not cooling down");
		helper.assertTrue(diedSecond, "the second killing blow was cheated as well");
		helper.succeed();
	}

	/** A key to a floor one to five above the player's best, a rune of that best floor, both from the shop's own sale. */
	@GameTest(maxTicks = 40)
	public void aFloorKeyOpensUpToFiveFloorsAboveTheBestAndARuneIsOfThatFloor(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		player.setAttached(TowerMerchants.BEST_FLOOR, 12);
		Set<Integer> keys = new HashSet<>();
		boolean runesOfBest = true;
		boolean runesAreRunes = true;
		for (long seed = 0; seed < 100; seed++) {
			ItemStack key = MerchantGoods.FLOOR_KEY.make().apply(saleTo(helper, player, seed));
			keys.add(FloorKeyItem.floorOf(key));
			ItemStack rune = MerchantGoods.TOWER_RUNE.make().apply(saleTo(helper, player, seed));
			RuneData data = rune.get(TowerItems.RUNE_DATA);
			runesAreRunes &= rune.is(TowerItems.RUNE) && data != null;
			runesOfBest &= data != null && data.floor() == 12;
		}
		TowerTestSupport.result("keyAndRune", "keys=" + keys + " runesOfBest=" + runesOfBest);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(keys.equals(Set.of(13, 14, 15, 16, 17)), "the keys opened floors " + keys);
		helper.assertTrue(runesAreRunes && runesOfBest, "a rune is not a rune of the player's best floor");
		helper.succeed();
	}

	/** The shield generator is sold by its id where the mobs mod is present, and a good by an absent id is never in stock. */
	@GameTest(maxTicks = 40)
	public void theShieldGeneratorIsSoldByIdAndAnAbsentGoodIsNotInStock(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		ItemStack generator = MerchantGoods.SHIELD_GENERATOR.make().apply(saleTo(helper, player, 4L));
		MerchantGoods.Good absent = MerchantGoods.ofItem(StellaTower.id("absent"), Identifier.fromNamespaceAndPath("no-such-mod", "nothing"), 1);
		TowerTestSupport.result("shieldGenerator", "stocked=" + MerchantGoods.SHIELD_GENERATOR.stocked().getAsBoolean() + " id=" + BuiltInRegistries.ITEM.getKey(generator.getItem())
			+ " absentStocked=" + absent.stocked().getAsBoolean());
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(MerchantGoods.SHIELD_GENERATOR.stocked().getAsBoolean(), "the shield generator is not in stock beside the mobs mod");
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(generator.getItem()).equals(MerchantGoods.SHIELD_GENERATOR_ID), "the good is " + generator);
		helper.assertTrue(MerchantGoods.SHIELD_GENERATOR.price() == 192, "the shield generator costs " + MerchantGoods.SHIELD_GENERATOR.price());
		helper.assertFalse(absent.stocked().getAsBoolean() || !absent.make().apply(saleTo(helper, player, 5L)).isEmpty(), "a good by an absent id is in stock");
		helper.succeed();
	}

	/**
	 * The whetstone against a stand-in of the equips mod's refine station, which this harness has no real one of: a refine that
	 * would fail succeeds and spends one whetstone, and the next one fails again. Without the equips mod the ward does nothing.
	 */
	@GameTest(maxTicks = 40)
	public void aWhetstoneTurnsAFailedRefineIntoASuccessOnce(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 2.5, 2.5);
		boolean installed = RefineWard.install();
		boolean withoutStone = RefineStation.refine(player, 0);
		player.getInventory().add(MerchantGoods.TOWER_WHETSTONE.make().apply(saleTo(helper, player, 6L)));
		boolean withStone = RefineStation.refine(player, 0);
		boolean leftover = RefineStation.refine(player, 0);
		int stones = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			stones += player.getInventory().getItem(slot).is(MerchantItems.TOWER_WHETSTONE) ? 1 : 0;
		}
		player.getInventory().add(new ItemStack(MerchantItems.TOWER_WHETSTONE));
		@SuppressWarnings("unchecked")
		Predicate<Player> share = (Predicate<Player>) FabricLoader.getInstance().getObjectShare().get(RefineWard.SHARE);
		boolean shared = share != null && share.test(player) && !share.test(player);
		RefineStation.setRoller(null);
		TowerTestSupport.result("whetstone", "installed=" + installed + " withoutStone=" + withoutStone + " withStone=" + withStone + " after=" + leftover + " left=" + stones + " shared=" + shared);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(installed, "the ward did not install over the refine station");
		helper.assertFalse(withoutStone, "a refine that should fail succeeded with no whetstone");
		helper.assertTrue(withStone && stones == 0, "the whetstone did not turn the failure into a success: " + withStone + " left " + stones);
		helper.assertFalse(leftover, "the whetstone worked twice");
		helper.assertTrue(shared, "the object share does not spend exactly one whetstone");
		helper.succeed();
	}

	/** The real refine at an anvil is the equips mod's; this harness has none. */
	@GameTest(required = false)
	public void theWhetstoneGuardsARealRefineAtAnAnvilRequiresManualTest(final GameTestHelper helper) {
		helper.fail("with the equips mod installed: put a +9 piece in an anvil's Refine tab with a whetstone in the inventory, refine until a roll would fail, and the piece rises and the whetstone is spent");
	}
}
