package com.stella.tower;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Every rune's weapon and armor effect against a plain overworld mob, for a fixed stat value. Each case
 * pins the stats and the chance rolls inside one tick, so cases that run side by side never see them.
 */
public class RuneEffectsGameTest {
	private static final int FLOOR = 1;
	private static final double EPS = 1.0E-3;

	private static void withStats(final int value, final Runnable body) {
		RuneStats.pin((entity, stat) -> value);
		try {
			body.run();
		} finally {
			RuneStats.pin(null);
			RuneEffects.pinRoll(null);
		}
	}

	private static ServerPlayer wielder(final GameTestHelper helper, final TowerElement element) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TowerTestSupport.vulnerable(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(Items.DIAMOND_SWORD), new RuneData(element, FLOOR)));
		return player;
	}

	private static ServerPlayer wearer(final GameTestHelper helper, final TowerElement element, final int pieces) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TowerTestSupport.vulnerable(player);
		ItemStack[] gear = {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < pieces; i++) {
			player.setItemSlot(slots[i], TowerRunes.apply(gear[i], new RuneData(element, FLOOR)));
		}
		return player;
	}

	private static Zombie zombie(final GameTestHelper helper, final double x, final double z) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = TowerTestSupport.unrolled(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND));
		zombie.snapTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		zombie.setNoAi(true);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		zombie.setHealth(500.0F);
		level.addFreshEntity(zombie);
		return zombie;
	}

	/** Strikes with the weapon in hand and returns the health the target lost. */
	private static double strike(final GameTestHelper helper, final ServerPlayer player, final LivingEntity target, final float amount) {
		float before = target.getHealth();
		target.damageCooldownTime = 0;
		target.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), amount);
		return before - target.getHealth();
	}

	/** Lets the mob strike the player and returns the health the player lost. */
	private static double bite(final GameTestHelper helper, final ServerPlayer player, final Mob mob, final float amount) {
		float before = player.getHealth();
		player.damageCooldownTime = 0;
		player.hurtServer(helper.getLevel(), mob.damageSources().mobAttack(mob), amount);
		return before - player.getHealth();
	}

	private static float scaled(final LivingEntity victim, final DamageSource source) {
		return TowerMobs.scaleDamage(victim, source, 10.0F);
	}

	private static void near(final GameTestHelper helper, final double got, final double want, final String what) {
		helper.assertTrue(Math.abs(got - want) <= Math.max(EPS, Math.abs(want) * 0.03), what + ": got " + got + ", want " + want);
	}

	/** Fire: the fifth hit combusts for STR x 2 percent of the hit; armor burns attackers and returns VIT / 10 percent. */
	@GameTest
	public void fire(final GameTestHelper helper) {
		withStats(50, () -> {
			ServerPlayer player = wielder(helper, TowerElement.FIRE);
			Zombie target = zombie(helper, 3.5, 3.5);
			double[] losses = new double[RuneEffects.HEAT_STACKS];
			for (int i = 0; i < losses.length; i++) {
				losses[i] = strike(helper, player, target, 10.0F);
			}
			int heat = RuneEffects.heatOf(target);
			TowerTestSupport.result("fireWeapon", "losses=" + java.util.Arrays.toString(losses) + " heatAfter=" + heat);
			near(helper, losses[4], losses[0] * 2.0, "the fifth hit with its combustion");
			helper.assertTrue(losses[3] == losses[0] && heat == 0, "the fourth hit added " + (losses[3] - losses[0]) + "; heat after " + heat);
			target.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(100, () -> {
			for (int pieces : new int[] {4, 1}) {
				ServerPlayer player = wearer(helper, TowerElement.FIRE, pieces);
				Zombie attacker = zombie(helper, 4.5, 4.5);
				double taken = bite(helper, player, attacker, 10.0F);
				double back = 500.0 - attacker.getHealth();
				double want = taken * 0.10 * pieces / 4.0;
				TowerTestSupport.result("fireArmor", "pieces=" + pieces + " taken=" + taken + " back=" + back + " want=" + want + " burning=" + attacker.isOnFire());
				helper.assertTrue(attacker.isOnFire(), "the attacker did not burn");
				near(helper, back, want, pieces + " fire pieces returned");
				attacker.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
		helper.succeed();
	}

	/** Ice: hits chill, slowed targets take INT / 4 percent more; armor freezes attackers at AGI / 10 percent. */
	@GameTest
	public void ice(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = wielder(helper, TowerElement.ICE);
			Zombie plain = zombie(helper, 3.5, 3.5);
			Zombie slowed = zombie(helper, 5.5, 3.5);
			slowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
			double ratio = scaled(slowed, player.damageSources().playerAttack(player)) / (double) scaled(plain, player.damageSources().playerAttack(player));
			strike(helper, player, plain, 1.0F);
			boolean chilled = plain.hasEffect(MobEffects.SLOWNESS);
			TowerTestSupport.result("iceWeapon", "ratio=" + ratio + " chilled=" + chilled);
			near(helper, ratio, 1.25, "ice against the slowed");
			helper.assertTrue(chilled, "an ice hit did not chill");
			plain.discard();
			slowed.discard();
			TowerTestSupport.leave(helper, player);
			ServerPlayer wearer = wearer(helper, TowerElement.ICE, 4);
			Zombie attacker = zombie(helper, 4.5, 4.5);
			RuneEffects.pinRoll(0.05);
			bite(helper, wearer, attacker, 10.0F);
			MobEffectInstance held = attacker.getEffect(MobEffects.SLOWNESS);
			boolean frozen = held != null && held.getAmplifier() == RuneGuards.FREEZE_SLOWNESS && attacker.getTicksFrozen() > 0;
			Zombie unlucky = zombie(helper, 5.5, 4.5);
			RuneEffects.pinRoll(0.5);
			bite(helper, wearer, unlucky, 10.0F);
			boolean spared = !unlucky.hasEffect(MobEffects.SLOWNESS);
			TowerTestSupport.result("iceArmor", "frozen=" + frozen + " spared=" + spared);
			helper.assertTrue(frozen, "a roll under AGI / 10 percent did not freeze");
			helper.assertTrue(spared, "a roll over AGI / 10 percent froze");
			attacker.discard();
			unlucky.discard();
			TowerTestSupport.leave(helper, wearer);
		});
		helper.succeed();
	}

	/** Water: a hit soaks for LUK / 3 percent more damage from any source; armor regenerates VIT / 20 percent. */
	@GameTest
	public void water(final GameTestHelper helper) {
		withStats(30, () -> {
			ServerPlayer player = wielder(helper, TowerElement.WATER);
			Zombie dry = zombie(helper, 3.5, 3.5);
			Zombie soaked = zombie(helper, 5.5, 3.5);
			strike(helper, player, soaked, 0.01F);
			DamageSource bolt = helper.getLevel().damageSources().lightningBolt();
			double ratio = scaled(soaked, bolt) / (double) scaled(dry, bolt);
			TowerTestSupport.result("waterWeapon", "wet=" + RuneEffects.isWet(soaked) + " ratio=" + ratio);
			helper.assertTrue(RuneEffects.isWet(soaked) && !RuneEffects.isWet(dry), "a water hit left the wrong target wet");
			near(helper, ratio, 1.10, "damage on the soaked");
			dry.discard();
			soaked.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(100, () -> {
			for (int pieces : new int[] {4, 1}) {
				ServerPlayer player = wearer(helper, TowerElement.WATER, pieces);
				player.setHealth(10.0F);
				RuneGuards.regen(player);
				double gained = player.getHealth() - 10.0;
				double want = player.getMaxHealth() * 0.05 * RuneGuards.REGEN_PER_INTERVAL * pieces / 4.0;
				TowerTestSupport.result("waterArmor", "pieces=" + pieces + " gained=" + gained + " want=" + want);
				near(helper, gained, want, pieces + " water pieces regenerated");
				TowerTestSupport.leave(helper, player);
			}
		});
		helper.succeed();
	}

	/** Lightning: every fourth hit chains to two mobs in range for DEX / 2 percent; armor stores charges for the next attack. */
	@GameTest
	public void lightning(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = wielder(helper, TowerElement.LIGHTNING);
			Zombie target = zombie(helper, 3.5, 3.5);
			Zombie one = zombie(helper, 5.0, 3.5);
			Zombie two = zombie(helper, 3.5, 5.5);
			Zombie far = zombie(helper, 3.5, 9.5);
			Zombie extra = zombie(helper, 5.5, 5.5);
			double hit = 0.0;
			double chainedEarly = 0.0;
			for (int i = 0; i < 3; i++) {
				hit = strike(helper, player, target, 10.0F);
				chainedEarly += 500.0 - one.getHealth() + 500.0 - two.getHealth();
			}
			strike(helper, player, target, 10.0F);
			double lostOne = 500.0 - one.getHealth();
			double lostTwo = 500.0 - two.getHealth();
			double lostFar = 500.0 - far.getHealth();
			double lostExtra = 500.0 - extra.getHealth();
			TowerTestSupport.result("lightningWeapon", "hit=" + hit + " early=" + chainedEarly + " one=" + lostOne + " two=" + lostTwo + " far=" + lostFar + " extra=" + lostExtra);
			helper.assertTrue(chainedEarly == 0.0, "a chain came before the fourth hit");
			near(helper, lostOne, hit * 0.5, "the nearest chain");
			near(helper, lostTwo, hit * 0.5, "the second chain");
			helper.assertTrue(lostFar == 0.0 && lostExtra == 0.0, "a chain reached past two mobs or four blocks: far=" + lostFar + " extra=" + lostExtra);
			target.discard();
			one.discard();
			two.discard();
			far.discard();
			extra.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(100, () -> {
			ServerPlayer player = wearer(helper, TowerElement.LIGHTNING, 4);
			Zombie biter = zombie(helper, 4.5, 4.5);
			bite(helper, player, biter, 1.0F);
			int one = RuneEffects.chargesOf(player);
			for (int i = 0; i < 8; i++) {
				bite(helper, player, biter, 0.1F);
			}
			int most = RuneEffects.chargesOf(player);
			double charged = scaled(biter, player.damageSources().playerAttack(player));
			double want = 10.0 * (1.0 + most * 0.20);
			strike(helper, player, biter, 1.0F);
			int after = RuneEffects.chargesOf(player);
			TowerTestSupport.result("lightningArmor", "one=" + one + " most=" + most + " charged=" + charged + " after=" + after);
			helper.assertTrue(one == 1 && most == RuneEffects.MAX_CHARGES, "armor stored " + one + " and at most " + most);
			near(helper, charged, want, "five charges");
			helper.assertTrue(after == 0, "the attack left " + after + " charges");
			biter.discard();
			TowerTestSupport.leave(helper, player);
		});
		helper.succeed();
	}

	/** Wind: AGI / 3 percent more against the airborne with extra knockback; armor dodges at AGI / 20 percent. */
	@GameTest
	public void wind(final GameTestHelper helper) {
		withStats(90, () -> {
			ServerPlayer player = wielder(helper, TowerElement.WIND);
			Zombie grounded = zombie(helper, 3.5, 3.5);
			Zombie flying = zombie(helper, 5.5, 3.5);
			grounded.setOnGround(true);
			flying.setOnGround(false);
			double ratio = scaled(flying, player.damageSources().playerAttack(player)) / (double) scaled(grounded, player.damageSources().playerAttack(player));
			strike(helper, player, grounded, 1.0F);
			Zombie bare = zombie(helper, 3.5, 5.5);
			ServerPlayer other = TowerTestSupport.join(helper, 1.5, 5.5);
			other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			strike(helper, other, bare, 1.0F);
			double pushed = grounded.getDeltaMovement().x;
			double plain = bare.getDeltaMovement().x;
			TowerTestSupport.result("windWeapon", "ratio=" + ratio + " pushed=" + pushed + " plain=" + plain);
			near(helper, ratio, 1.30, "wind against the airborne");
			helper.assertTrue(pushed > plain, "a wind hit pushed " + pushed + ", no more than a plain " + plain);
			grounded.discard();
			flying.discard();
			bare.discard();
			TowerTestSupport.leave(helper, player);
			TowerTestSupport.leave(helper, other);
		});
		withStats(200, () -> {
			ServerPlayer player = wearer(helper, TowerElement.WIND, 4);
			Zombie biter = zombie(helper, 4.5, 4.5);
			RuneEffects.pinRoll(0.05);
			double dodged = bite(helper, player, biter, 5.0F);
			RuneEffects.pinRoll(0.5);
			double hit = bite(helper, player, biter, 5.0F);
			TowerTestSupport.result("windArmor", "dodged=" + dodged + " hit=" + hit);
			helper.assertTrue(dodged == 0.0, "a roll under AGI / 20 percent still cost " + dodged);
			helper.assertTrue(hit > 0.0, "a roll over AGI / 20 percent was dodged");
			biter.discard();
			TowerTestSupport.leave(helper, player);
		});
		helper.succeed();
	}

	/** Void: hits shred 2 percent of armor each up to INT / 5 percent; armor resists magic at INT / 20 percent. */
	@GameTest
	public void voidRune(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = wielder(helper, TowerElement.VOID);
			Zombie target = zombie(helper, 3.5, 3.5);
			target.getAttribute(Attributes.ARMOR).setBaseValue(25.0);
			double armor = target.getAttribute(Attributes.ARMOR).getValue();
			strike(helper, player, target, 0.01F);
			double once = target.getAttribute(Attributes.ARMOR).getValue() / armor;
			for (int i = 0; i < 30; i++) {
				strike(helper, player, target, 0.01F);
			}
			double most = target.getAttribute(Attributes.ARMOR).getValue() / armor;
			TowerTestSupport.result("voidWeapon", "once=" + once + " most=" + most);
			near(helper, once, 0.98, "armor after one hit");
			near(helper, most, 0.80, "armor after many hits");
			target.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(400, () -> {
			ServerPlayer player = wearer(helper, TowerElement.VOID, 4);
			ServerLevel level = helper.getLevel();
			float magic = scaled(player, level.damageSources().magic());
			float indirect = scaled(player, level.damageSources().indirectMagic(player, player));
			float other = scaled(player, level.damageSources().generic());
			TowerTestSupport.result("voidArmor", "magic=" + magic + " indirect=" + indirect + " other=" + other);
			near(helper, magic, 8.0, "magic through void armor");
			near(helper, indirect, 8.0, "indirect magic through void armor");
			near(helper, other, 10.0, "a plain blow through void armor");
			TowerTestSupport.leave(helper, player);
		});
		helper.succeed();
	}

	/** Holy: heals VIT / 10 percent of damage dealt, double against undead; armor cuts undead blows by VIT / 5 percent. */
	@GameTest
	public void holy(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = wielder(helper, TowerElement.HOLY);
			ServerLevel level = helper.getLevel();
			net.minecraft.world.entity.animal.pig.Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			pig.snapTo(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
			pig.setNoAi(true);
			pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
			pig.setHealth(500.0F);
			level.addFreshEntity(pig);
			Zombie undead = zombie(helper, 5.5, 3.5);
			double[] healed = new double[2];
			LivingEntity[] targets = {pig, undead};
			for (int i = 0; i < 2; i++) {
				player.setHealth(10.0F);
				double dealt = strike(helper, player, targets[i], 10.0F);
				healed[i] = (player.getHealth() - 10.0) / dealt;
			}
			TowerTestSupport.result("holyWeapon", "living=" + healed[0] + " undead=" + healed[1]);
			near(helper, healed[0], 0.10, "healing from a living target");
			near(helper, healed[1], 0.20, "healing from an undead target");
			pig.discard();
			undead.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(100, () -> {
			ServerPlayer player = wearer(helper, TowerElement.HOLY, 4);
			Zombie undead = zombie(helper, 4.5, 4.5);
			net.minecraft.world.entity.monster.spider.Spider living = EntityTypes.SPIDER.create(helper.getLevel(), EntitySpawnReason.COMMAND);
			float fromUndead = scaled(player, undead.damageSources().mobAttack(undead));
			float fromLiving = scaled(player, living.damageSources().mobAttack(living));
			TowerTestSupport.result("holyArmor", "undead=" + fromUndead + " living=" + fromLiving);
			near(helper, fromUndead, 8.0, "an undead blow through holy armor");
			near(helper, fromLiving, 10.0, "a living blow through holy armor");
			undead.discard();
			living.discard();
			TowerTestSupport.leave(helper, player);
		});
		helper.succeed();
	}

	/** Holy on a staff heals on spell hits; a held staff stands in for the weapon a magic source lacks. */
	@GameTest
	public void holyStaffMagic(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
			TowerTestSupport.vulnerable(player);
			player.setItemInHand(InteractionHand.MAIN_HAND, TowerRunes.apply(new ItemStack(Items.BLAZE_ROD), new RuneData(TowerElement.HOLY, FLOOR)));
			ServerLevel level = helper.getLevel();
			Zombie target = zombie(helper, 3.5, 3.5);
			DamageSource[] sources = {level.damageSources().indirectMagic(player, player), level.damageSources().playerAttack(player)};
			for (DamageSource source : sources) {
				player.setHealth(10.0F);
				target.damageCooldownTime = 0;
				float before = target.getHealth();
				target.hurtServer(level, source, 10.0F);
				double healed = player.getHealth() - 10.0;
				TowerTestSupport.result("holyStaff", source.type().msgId() + " dealt=" + (before - target.getHealth()) + " healed=" + healed);
				helper.assertTrue(healed > 0.0, "a holy staff heals on " + source.type().msgId() + ", healed " + healed);
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK));
			player.getInventory().setItem(35, TowerRunes.apply(new ItemStack(Items.BLAZE_ROD), new RuneData(TowerElement.HOLY, FLOOR)));
			player.setHealth(10.0F);
			target.damageCooldownTime = 0;
			target.hurtServer(level, sources[0], 10.0F);
			double slotHealed = player.getHealth() - 10.0;
			TowerTestSupport.result("holyStaffSlot", "healed=" + slotHealed);
			helper.assertTrue(slotHealed > 0.0, "a holy staff in the weapon slot heals on a spell, healed " + slotHealed);
			target.discard();
			TowerTestSupport.leave(helper, player);
		});
		helper.succeed();
	}

	/** Dark: STR / 5 percent more against targets below 30 percent health; armor weakens attackers for LUK / 20 seconds. */
	@GameTest
	public void dark(final GameTestHelper helper) {
		withStats(100, () -> {
			ServerPlayer player = wielder(helper, TowerElement.DARK);
			Zombie healthy = zombie(helper, 3.5, 3.5);
			Zombie weak = zombie(helper, 5.5, 3.5);
			weak.setHealth(100.0F);
			double ratio = scaled(weak, player.damageSources().playerAttack(player)) / (double) scaled(healthy, player.damageSources().playerAttack(player));
			TowerTestSupport.result("darkWeapon", "ratio=" + ratio);
			near(helper, ratio, 1.20, "dark against the weak");
			healthy.discard();
			weak.discard();
			TowerTestSupport.leave(helper, player);
		});
		withStats(100, () -> {
			for (int pieces : new int[] {4, 1}) {
				ServerPlayer player = wearer(helper, TowerElement.DARK, pieces);
				Zombie attacker = zombie(helper, 4.5, 4.5);
				bite(helper, player, attacker, 1.0F);
				MobEffectInstance weakness = attacker.getEffect(MobEffects.WEAKNESS);
				int ticks = weakness == null ? 0 : weakness.getDuration();
				int want = (int) Math.round(5.0 * pieces / 4.0 * 20.0);
				TowerTestSupport.result("darkArmor", "pieces=" + pieces + " ticks=" + ticks + " want=" + want);
				helper.assertTrue(weakness != null && weakness.getAmplifier() == 0 && Math.abs(ticks - want) <= 1, pieces + " dark pieces gave weakness for " + ticks + " ticks, not " + want);
				attacker.discard();
				TowerTestSupport.leave(helper, player);
			}
		});
		helper.succeed();
	}

	/** The caps hold, a quarter comes per piece, and without the rpg mod every effect stays at its floor. */
	@GameTest
	public void capsPiecesAndTheFloor(final GameTestHelper helper) {
		helper.assertTrue(RuneValues.iceFreeze(1000, 1.0) == 40.0 && RuneValues.windDodge(1000, 1.0) == 30.0, "a freeze or dodge cap did not hold");
		helper.assertTrue(RuneValues.voidResist(1000, 1.0) == 30.0 && RuneValues.holyUndead(1000, 1.0) == 60.0, "a resistance cap did not hold");
		helper.assertTrue(RuneValues.windDodge(1000, 0.5) == 25.0 && RuneValues.windDodge(2000, 0.5) == 30.0 && RuneValues.windDodge(200, 0.5) == 5.0, "a half set did not halve the dodge before the cap");
		withStats(0, () -> {
			ServerPlayer player = wielder(helper, TowerElement.ICE);
			Zombie slowed = zombie(helper, 3.5, 3.5);
			slowed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 0));
			Zombie plain = zombie(helper, 5.5, 3.5);
			double ratio = scaled(slowed, player.damageSources().playerAttack(player)) / (double) scaled(plain, player.damageSources().playerAttack(player));
			near(helper, ratio, 1.0, "ice at zero INT");
			slowed.discard();
			plain.discard();
			TowerTestSupport.leave(helper, player);
		});
		List<Integer> read = new ArrayList<>();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		for (RuneStats.Stat stat : RuneStats.Stat.values()) {
			read.add(RuneStats.of(player, stat));
		}
		TowerTestSupport.result("runeStatsFloor", "read=" + read);
		helper.assertTrue(read.stream().allMatch(value -> value == 0), "stats without the rpg mod read " + read);
		TowerTestSupport.leave(helper, player);
		helper.succeed();
	}

	/** The numbers a rune's tooltip lines carry, by line key. */
	private static java.util.Map<String, String> numbers(final RuneData rune, final int stat) {
		java.util.Map<String, String> seen = new java.util.HashMap<>();
		TowerRunes.describe(rune, ignored -> stat, line -> {
			if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable && translatable.getArgs().length == 1) {
				seen.put(translatable.getKey(), String.valueOf(translatable.getArgs()[0]));
			}
		});
		return seen;
	}

	/** The tooltip shows each effect with the number for the player's stats. */
	@GameTest
	public void theTooltipShowsTheNumbers(final GameTestHelper helper) {
		java.util.Map<String, String> fire = numbers(new RuneData(TowerElement.FIRE, FLOOR), 50);
		java.util.Map<String, String> ice = numbers(new RuneData(TowerElement.ICE, FLOOR), 100);
		java.util.Map<String, String> none = numbers(new RuneData(TowerElement.ICE, FLOOR), 0);
		TowerTestSupport.result("runeTooltip", "fire=" + fire + " ice=" + ice + " none=" + none);
		String prefix = "item.mod-stella-tower.rune.";
		helper.assertTrue("100".equals(fire.get(prefix + "weapon.fire")) && "5".equals(fire.get(prefix + "armor.fire")), "the fire lines show " + fire);
		helper.assertTrue("25".equals(ice.get(prefix + "weapon.ice")) && "10".equals(ice.get(prefix + "armor.ice")), "the ice lines show " + ice);
		helper.assertTrue("0".equals(none.get(prefix + "weapon.ice")) && "0".equals(none.get(prefix + "armor.ice")), "the ice lines at zero stats show " + none);
		helper.succeed();
	}
}
