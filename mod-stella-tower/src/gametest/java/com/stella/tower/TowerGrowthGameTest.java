package com.stella.tower;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Growth: a rune's and ascension's share of flat numbers, resonance with a rune's element, and Lightning charges. */
public class TowerGrowthGameTest {
	private static ItemStack runed(final net.minecraft.world.item.Item item, final TowerElement element, final int floor) {
		return TowerRunes.apply(new ItemStack(item), new RuneData(element, floor));
	}

	private static ItemStack ascended(final int level) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(TowerItems.ASCENDED, new TowerGrowth.Ascension(level * TowerGrowth.KILLS_PER_LEVEL, level));
		return sword;
	}

	/** A rune multiplies flat numbers by (1 + 99 S(r))^0.7: the design's x2.8, x15.6 and x25.1 at floors 100, 500 and 1000. */
	@GameTest
	public void aRunesShareOfFlatNumbersFollowsTheCurve(final GameTestHelper helper) {
		int[] floors = {100, 500, 1000};
		double[] table = {2.8, 15.6, 25.1};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < floors.length; i++) {
			double got = TowerGrowth.runePart(runed(Items.DIAMOND_SWORD, TowerElement.FIRE, floors[i]));
			double want = Math.pow(1.0 + (TowerCurve.HEALTH_MAX - 1.0) * TowerCurve.s(floors[i]), TowerGrowth.RUNE_EXPONENT);
			out.append("f").append(floors[i]).append("=").append(got).append("/").append(want).append(" ");
			helper.assertTrue(Math.abs(got - want) < 1.0E-6, "a floor " + floors[i] + " rune gave " + got + ", not " + want);
			helper.assertTrue(Math.abs(got - table[i]) < 0.1, "a floor " + floors[i] + " rune gave " + got + " where the design says " + table[i]);
		}
		helper.assertTrue(TowerGrowth.runePart(new ItemStack(Items.DIAMOND_SWORD)) == 1.0, "an unruned sword grew");
		TowerTestSupport.result("runePart", out.toString().trim());
		helper.succeed();
	}

	/** Ascension multiplies by (1 + 99 S(a))^0.3, about x4 at 1000, and the two exponents make one. */
	@GameTest
	public void ascensionsShareOfFlatNumbersFollowsTheCurve(final GameTestHelper helper) {
		double at1000 = TowerGrowth.ascensionPart(ascended(1000));
		double at100 = TowerGrowth.ascensionPart(ascended(100));
		double want100 = Math.pow(1.0 + (TowerCurve.HEALTH_MAX - 1.0) * TowerCurve.s(100), TowerGrowth.ASCENSION_EXPONENT);
		TowerTestSupport.result("ascensionPart", "a100=" + at100 + "/" + want100 + " a1000=" + at1000);
		helper.assertTrue(Math.abs(TowerGrowth.RUNE_EXPONENT + TowerGrowth.ASCENSION_EXPONENT - 1.0) < 1.0E-9, "the exponents do not add up to one");
		helper.assertTrue(Math.abs(at100 - want100) < 1.0E-6, "ascension 100 gave " + at100 + ", not " + want100);
		helper.assertTrue(Math.abs(at100 - 1.5) < 0.1 && Math.abs(at1000 - 4.0) < 0.1, "ascension gave " + at100 + " and " + at1000 + " where the design says 1.5 and 4.0");
		helper.assertTrue(TowerGrowth.ascensionPart(new ItemStack(Items.DIAMOND_SWORD)) == 1.0, "an unascended sword grew");
		helper.succeed();
	}

	@GameTest
	public void aFullyGrownItemKeepsPaceWithTheTower(final GameTestHelper helper) {
		ItemStack sword = runed(Items.DIAMOND_SWORD, TowerElement.FIRE, 1000);
		sword.set(TowerItems.ASCENDED, new TowerGrowth.Ascension(10_000, 1000));
		double flat = TowerGrowth.flat(sword);
		TowerTestSupport.result("flat", "both=" + flat + " health=" + TowerCurve.HEALTH_MAX);
		helper.assertTrue(Math.abs(flat - TowerCurve.HEALTH_MAX) < 1.0, "a floor 1000 rune on a fully ascended item gave x" + flat + ", the tower gives x" + TowerCurve.HEALTH_MAX);
		helper.succeed();
	}

	/** Ten kills raise ascension by one, and never past the floor the kill happened on. */
	@GameTest
	public void tenKillsRaiseAscensionByOneButNotPastTheFloor(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		for (int i = 0; i < TowerGrowth.KILLS_PER_LEVEL - 1; i++) {
			TowerGrowth.credit(player, 50);
		}
		TowerGrowth.Ascension nine = TowerGrowth.ascension(player.getMainHandItem());
		TowerGrowth.credit(player, 50);
		TowerGrowth.Ascension ten = TowerGrowth.ascension(player.getMainHandItem());
		// Twenty more on floor 1: kills count, ascension is held at the floor's height.
		for (int i = 0; i < 2 * TowerGrowth.KILLS_PER_LEVEL; i++) {
			TowerGrowth.credit(player, 1);
		}
		TowerGrowth.Ascension shallow = TowerGrowth.ascension(player.getMainHandItem());
		TowerTestSupport.result("ascension", "nine=" + nine + " ten=" + ten + " shallow=" + shallow);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(nine.level() == 0 && nine.kills() == TowerGrowth.KILLS_PER_LEVEL - 1, "nine kills gave " + nine);
		helper.assertTrue(ten.level() == 1 && ten.kills() == TowerGrowth.KILLS_PER_LEVEL, "ten kills gave " + ten);
		helper.assertTrue(shallow.level() == 1 && shallow.kills() == 3 * TowerGrowth.KILLS_PER_LEVEL, "kills on floor 1 gave " + shallow + ", not level 1 after 30 kills");
		helper.succeed();
	}

	/** The kill of a tower mob is counted on what the player holds and wears, and on nothing else. */
	@GameTest
	public void aTowerKillCountsOnWhatIsHeldAndWorn(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		TowerTestSupport.vulnerable(player);
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOOK));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		ItemStack spare = new ItemStack(Items.DIAMOND_SWORD);
		player.getInventory().setItem(20, spare);
		Zombie towerMob = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		TowerMobs.makeTowerMob(towerMob, 50, TowerElement.WIND);
		towerMob.snapTo(helper.absoluteVec(new Vec3(2.5, 2.0, 3.5)));
		towerMob.setNoAi(true);
		level.addFreshEntity(towerMob);
		towerMob.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
		int sword = TowerGrowth.ascension(player.getMainHandItem()).kills();
		int book = TowerGrowth.ascension(player.getOffhandItem()).kills();
		int chest = TowerGrowth.ascension(player.getItemBySlot(EquipmentSlot.CHEST)).kills();
		int inPack = TowerGrowth.ascension(spare).kills();
		// A vanilla mob's death counts for nothing.
		Zombie plain = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		plain.snapTo(helper.absoluteVec(new Vec3(3.5, 2.0, 3.5)));
		plain.setNoAi(true);
		level.addFreshEntity(plain);
		plain.hurtServer(level, player.damageSources().playerAttack(player), 1.0E30F);
		int afterPlain = TowerGrowth.ascension(player.getMainHandItem()).kills();
		TowerTestSupport.result("killCredit", "sword=" + sword + " book=" + book + " chest=" + chest + " inPack=" + inPack + " afterPlain=" + afterPlain);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(sword == 1 && book == 1 && chest == 1, "the kill counted " + sword + "/" + book + "/" + chest + " on the sword, book and chestplate");
		helper.assertTrue(inPack == 0, "a sword in the pack counted a kill");
		helper.assertTrue(afterPlain == 1, "a vanilla mob's death counted");
		helper.succeed();
	}

	/** A rune strengthens the enchantments and spells of its element by 10% + P/10 %, and nothing else. */
	@GameTest
	public void aRuneResonatesWithItsElementsEnchantmentsAndSpells(final GameTestHelper helper) {
		ItemStack fire = runed(Items.DIAMOND_SWORD, TowerElement.FIRE, 400);
		double power = TowerRunes.power(400);
		double want = 1.0 + TowerGrowth.RESONANCE_BASE + power / 1000.0;
		double pyre = TowerGrowth.resonance(fire, "pyre");
		double flameRing = TowerGrowth.resonance(fire, "flame_ring");
		double chill = TowerGrowth.resonance(fire, "chill");
		double ashfallIce = TowerGrowth.resonance(runed(Items.DIAMOND_SWORD, TowerElement.ICE, 400), "ashfall");
		double bare = TowerGrowth.resonance(new ItemStack(Items.DIAMOND_SWORD), "pyre");
		double unknown = TowerGrowth.resonance(fire, "no_such_enchantment");
		TowerTestSupport.result("resonance", "pyre=" + pyre + " flameRing=" + flameRing + " want=" + want + " chill=" + chill + " ashfallIce=" + ashfallIce + " bare=" + bare);
		helper.assertTrue(Math.abs(pyre - want) < 1.0E-9 && Math.abs(flameRing - want) < 1.0E-9, "fire resonance gave " + pyre + " and " + flameRing + ", not " + want);
		helper.assertTrue(chill == 1.0, "a fire rune strengthened Chill");
		helper.assertTrue(Math.abs(ashfallIce - want) < 1.0E-9, "an ice rune gave Ashfall " + ashfallIce + ": Ashfall is fire and ice");
		helper.assertTrue(bare == 1.0 && unknown == 1.0, "an unruned sword or an unknown id resonated");
		helper.assertTrue(TowerGrowth.elementsOf("ashfall").size() == 2, "Ashfall is listed under " + TowerGrowth.elementsOf("ashfall"));
		helper.succeed();
	}

	@GameTest
	public void everyListedEnchantmentAndSpellBelongsToAnElement(final GameTestHelper helper) {
		String[] listed = {"pyre", "ashfall", "ember_guard", "blaze_armor", "bomb_arrows", "meteor", "flame_ring", "ember_brand", "phoenix_mantle", "dragonfall", "spark_shot",
			"chill", "frostbite", "rime_guard", "mist_armor", "ice_lance", "frost_nova", "frostbind",
			"tidecaller", "undertow", "tidal_push", "tide_lungs", "aegis_of_the_deep", "stormcaller", "static_lash", "chainstorm",
			"shockwave", "waveform", "feathered_edge", "gale_step", "tailwind",
			"void_brand", "ender_ward", "riftwalk", "void_well", "gravity", "time_lock",
			"second_wind", "guarding_ward", "sanctuary", "mending_word", "life_bloom", "purify", "bulwark_hymn", "battle_hymn",
			"venom", "dread_aura", "wounding", "wither_seal", "plague_cloud", "sapping_hex", "soul_siphon", "shadow_veil"};
		StringBuilder missing = new StringBuilder();
		for (String id : listed) {
			if (TowerGrowth.elementsOf(id).isEmpty()) {
				missing.append(id).append(' ');
			}
		}
		TowerTestSupport.result("resonanceTable", "listed=" + listed.length + " missing=[" + missing.toString().trim() + "]");
		helper.assertTrue(missing.length() == 0, "no element for: " + missing);
		helper.succeed();
	}

	/** Each stored Lightning charge takes 4% off a spell's cooldown, five at most, and is spent by it. */
	@GameTest
	public void lightningChargesCutASpellsCooldownAndAreSpent(final GameTestHelper helper) {
		ServerPlayer player = TowerTestSupport.join(helper, 1.5, 1.5);
		int none = TowerGrowth.cooldown(player, 100);
		for (int i = 0; i < 3; i++) {
			RuneEffects.addCharge(player);
		}
		int three = TowerGrowth.cooldown(player, 100);
		int spent = RuneEffects.chargesOf(player);
		for (int i = 0; i < 9; i++) {
			RuneEffects.addCharge(player);
		}
		int five = TowerGrowth.cooldown(player, 100);
		TowerTestSupport.result("lightningCooldown", "none=" + none + " three=" + three + " five=" + five + " leftAfterThree=" + spent);
		TowerTestSupport.leave(helper, player);
		helper.assertTrue(none == 100, "no charges changed the cooldown to " + none);
		helper.assertTrue(three == Math.round(100 * (1.0 - 3 * TowerGrowth.CHARGE_CUT)) && spent == 0, "three charges gave " + three + " with " + spent + " left");
		helper.assertTrue(five == Math.round(100 * (1.0 - RuneEffects.MAX_CHARGES * TowerGrowth.CHARGE_CUT)), "a full store gave " + five);
		helper.succeed();
	}

	/** Spellbooks take runes at the anvil too, as their own kind of slot. */
	@GameTest
	public void aSpellbookTakesARune(final GameTestHelper helper) {
		RuneData rune = new RuneData(TowerElement.HOLY, 300);
		ItemStack book = TowerRunes.apply(new ItemStack(Items.BOOK), rune);
		ItemStack stick = TowerRunes.apply(new ItemStack(Items.STICK), rune);
		TowerTestSupport.result("bookRune", "book=" + !book.isEmpty() + " slot=" + TowerRunes.slotOf(new ItemStack(Items.BOOK)) + " stick=" + !stick.isEmpty());
		helper.assertTrue(!book.isEmpty() && TowerRunes.runeOf(book) != null, "a spellbook took no rune");
		helper.assertTrue(TowerRunes.slotOf(book) == TowerRunes.Slot.BOOK, "a spellbook's slot was " + TowerRunes.slotOf(book));
		helper.assertTrue(stick.isEmpty(), "a stick took a rune");
		helper.succeed();
	}
}
