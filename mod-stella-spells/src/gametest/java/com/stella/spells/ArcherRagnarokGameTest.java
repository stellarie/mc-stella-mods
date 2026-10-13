package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Ragnarok-style archer tree: the five reworked books, the two new ones, the split bow and ammunition gate,
 * and the bow imbue. Every bound reads the constant that ships.
 */
public class ArcherRagnarokGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archerragnarok");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final int QUIVER_SLOT = 20;
	private static final Map<UUID, List<Identifier>> CASTS = new ConcurrentHashMap<>();

	static {
		SpellImbue.addRecorder((player, spell, victim) ->
			CASTS.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell));
	}

	/** One recording stub: how many archer hits the dice rolled, and the critical bonus in force at each. */
	private static final class HitProbe implements RpgBridge.Stats {
		final List<Double> critical = new ArrayList<>();
		private final UUID owner;

		HitProbe(final ServerPlayer owner) {
			this.owner = owner.getUUID();
		}

		@Override
		public double power(final Player player) {
			return 1.0;
		}

		@Override
		public double cooldown(final Player player) {
			return 1.0;
		}

		@Override
		public double skillDamage(final LivingEntity caster, final String statKey, final double skillPercent) {
			// The stub is global, so a neighbour test's hit is not this case's hit.
			if (caster.getUUID().equals(this.owner)) {
				this.critical.add(ArcherSkills.criticalChanceBonus(caster));
			}
			return Double.NaN;
		}
	}

	@GameTest(maxTicks = 80)
	public void doubleStrafeHitsTheTargetTwiceWithNoArrow(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, false);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		HitProbe probe = new HitProbe(caster);
		RpgBridge.setStats(probe);
		boolean applied;
		try {
			applied = SkillSchoolGameTest.cast(caster, "rapid_fire", foe, false);
		} finally {
			RpgBridge.setStats(null);
		}
		double lost = 1000.0 - foe.getHealth();
		LOG.info("[BEHAVIOR] test=doubleStrafeHitsTheTargetTwiceWithNoArrow RESULT applied={} hits={} expected={} lost={} flat={} arrows={}",
			applied, probe.critical.size(), ArcherSkills.DOUBLE_STRAFE_HITS, lost, SpellNumbers.RAPID_FIRE.damage(),
			caster.getInventory().countItem(Items.ARROW));
		helper.assertTrue(applied, "double strafe never cast with a bow and no arrow");
		helper.assertTrue(probe.critical.size() == ArcherSkills.DOUBLE_STRAFE_HITS,
			"double strafe rolled " + probe.critical.size() + " hits, not " + ArcherSkills.DOUBLE_STRAFE_HITS);
		helper.assertTrue(Math.abs(lost - SpellNumbers.RAPID_FIRE.damage() * ArcherSkills.DOUBLE_STRAFE_HITS) < 0.05,
			"double strafe took " + lost + " from the target");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void sharpShootingHitsEveryFoeOnTheLineWithARaisedCriticalChance(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, false);
		Mob first = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		Mob second = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.5);
		HitProbe probe = new HitProbe(caster);
		RpgBridge.setStats(probe);
		boolean applied;
		try {
			applied = SkillSchoolGameTest.cast(caster, "piercing_shot", null, false);
		} finally {
			RpgBridge.setStats(null);
		}
		double outside = ArcherSkills.criticalChanceBonus(caster);
		LOG.info("[BEHAVIOR] test=sharpShootingHitsEveryFoeOnTheLineWithARaisedCriticalChance RESULT applied={} hits={} critical={} raised={} outside={} firstLost={} secondLost={}",
			applied, probe.critical.size(), probe.critical, ArcherSkills.SHARP_SHOOTING_CRIT_CHANCE, outside,
			1000.0 - first.getHealth(), 1000.0 - second.getHealth());
		helper.assertTrue(applied, "sharp shooting never cast");
		// The ray runs 32 blocks through a level other cases share, so a neighbour mob on the line adds a roll.
		// The two foes of this case are proved by their own health below.
		helper.assertTrue(probe.critical.size() >= 2, "sharp shooting rolled " + probe.critical.size() + " hits, fewer than its 2 foes");
		helper.assertTrue(probe.critical.stream().allMatch(bonus -> bonus >= ArcherSkills.SHARP_SHOOTING_CRIT_CHANCE),
			"a line hit rolled without the raised critical chance: " + probe.critical);
		helper.assertTrue(outside == 0.0, "the raised chance outlived the shot: " + outside);
		helper.assertTrue(1000.0 - first.getHealth() > 0.0 && 1000.0 - second.getHealth() > 0.0,
			"a foe on the line took nothing");
		first.discard();
		second.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void trueSightKeepsItsDamageFactorAndRaisesTheCriticalChance(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, true);
		double without = ArcherSkills.criticalChanceBonus(caster);
		boolean applied = SkillSchoolGameTest.cast(caster, "eagle_eye", null, false);
		double plain = ArcherSkills.criticalChanceBonus(caster);
		double factor = ArcherSkills.eagleEyeFactor(caster);
		caster.removeAllEffects();
		SkillSchoolGameTest.cast(caster, "eagle_eye", null, true);
		double awakened = ArcherSkills.criticalChanceBonus(caster);
		LOG.info("[BEHAVIOR] test=trueSightKeepsItsDamageFactorAndRaisesTheCriticalChance RESULT applied={} without={} plain={} awakened={} factor={}",
			applied, without, plain, awakened, factor);
		helper.assertTrue(applied, "true sight never cast");
		helper.assertTrue(without == 0.0, "a caster with no sight read a critical bonus: " + without);
		helper.assertTrue(plain == ArcherSkills.TRUE_SIGHT_CRIT_CHANCE, "true sight raised the chance by " + plain);
		helper.assertTrue(awakened == ArcherSkills.TRUE_SIGHT_AWAKENED_CRIT_CHANCE, "awakened sight raised the chance by " + awakened);
		helper.assertTrue(factor == ArcherSkills.EAGLE_EYE_PLAIN_FACTOR, "true sight changed its damage factor to " + factor);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void ankleSnareRootsTheFirstFoeThatEntersAndNoOtherAfterIt(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, true);
		Vec3 point = helper.absoluteVec(new Vec3(5.5, 1.0, CASTER_Z + 5.0));
		Mob first = FighterBooksGameTest.foe(helper, 10.5, CASTER_Z + 5.0);
		Mob second = FighterBooksGameTest.foe(helper, 10.5, CASTER_Z + 8.0);
		boolean applied = ArcherSkills.applyAnkleSnare(caster, null, point, SkillSchoolGameTest.modifiers(caster, false));
		int laid = ArcherSkills.ankleSnares(caster);
		boolean farRooted = first.hasEffect(MobEffects.SLOWNESS);
		first.setPos(point.x, point.y, point.z);
		boolean setApplied = applied;
		helper.runAfterDelay(6L, () -> {
			MobEffectInstance root = first.getEffect(MobEffects.SLOWNESS);
			int left = ArcherSkills.ankleSnares(caster);
			second.setPos(point.x, point.y, point.z);
			helper.runAfterDelay(6L, () -> {
				LOG.info("[BEHAVIOR] test=ankleSnareRootsTheFirstFoeThatEntersAndNoOtherAfterIt RESULT applied={} laid={} farRooted={} rooted={} amplifier={} left={} secondRooted={}",
					setApplied, laid, farRooted, root != null, root == null ? -1 : root.getAmplifier(), left,
					second.hasEffect(MobEffects.SLOWNESS));
				helper.assertTrue(setApplied && laid == 1, "the trap was not laid: laid=" + laid);
				helper.assertFalse(farRooted, "a foe far from the trap was rooted");
				helper.assertTrue(root != null && root.getAmplifier() == SpellNumbers.SLOWNESS_ROOT_AMPLIFIER,
					"the first foe was not rooted");
				helper.assertTrue(left == 0, "the sprung trap stayed in the world: " + left);
				helper.assertFalse(second.hasEffect(MobEffects.SLOWNESS), "a second foe was rooted by a spent trap");
				first.discard();
				second.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 120)
	public void arrowRepelKnocksTheTargetBackSeveralBlocks(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, true);
		Mob foe = movableFoe(helper, CASTER_X, CASTER_Z + 3.0);
		Vec3 start = foe.position();
		boolean applied = SkillSchoolGameTest.cast(caster, "arrow_repel", foe, false);
		helper.runAfterDelay(40L, () -> {
			double moved = horizontal(foe.position(), start);
			double away = foe.position().subtract(start).dot(start.subtract(caster.position()).multiply(1.0, 0.0, 1.0).normalize());
			LOG.info("[BEHAVIOR] test=arrowRepelKnocksTheTargetBackSeveralBlocks RESULT applied={} moved={} away={} min={} lost={}",
				applied, moved, away, ArcherSkills.ARROW_REPEL_MIN_BLOCKS, 1000.0 - foe.getHealth());
			helper.assertTrue(applied, "arrow repel never cast");
			helper.assertTrue(away >= ArcherSkills.ARROW_REPEL_MIN_BLOCKS,
				"the target was driven " + away + " blocks away, not " + ArcherSkills.ARROW_REPEL_MIN_BLOCKS);
			helper.assertTrue(1000.0 - foe.getHealth() > 0.0, "the heavy shot dealt nothing");
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void everyArcherBookRefusesWithoutABowInEitherHand(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, true);
		SkillSchoolGameTest.setAutoKit(false);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		caster.getInventory().setItem(WEAPON_SLOT, ItemStack.EMPTY);
		caster.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		caster.removeAllEffects();
		List<String> cast = new ArrayList<>();
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			// Arrow Crafting, Blitz Beat and Summon Wolf need no bow; their own cases prove that.
			if (path.equals("arrow_crafting") || path.equals("hunters_mark") || path.equals("summon_wolf")) {
				continue;
			}
			if (SkillSchoolGameTest.cast(caster, path, foe, false)) {
				cast.add(path);
			}
			ArcherSkills.clearFor(caster);
		}
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CROSSBOW));
		boolean offHandCast = SkillSchoolGameTest.cast(caster, "snare_trap", null, false);
		LOG.info("[BEHAVIOR] test=everyArcherBookRefusesWithoutABowInEitherHand RESULT books={} castWithBareHands={} offHandCrossbowCast={}",
			SkillSchoolGameTest.ARCHER_IDS.size(), cast, offHandCast);
		helper.assertTrue(SkillSchoolGameTest.ARCHER_IDS.size() == 17, "the school holds " + SkillSchoolGameTest.ARCHER_IDS.size() + " books, not 17");
		helper.assertTrue(cast.isEmpty(), "these books cast with no bow or crossbow: " + cast);
		helper.assertTrue(offHandCast, "a crossbow in the off hand did not open the gate");
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void everyArcherBookCastsWithABowAndNoArrowAnywhere(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, false);
		SkillSchoolGameTest.setAutoKit(false);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 2.5);
		caster.removeAllEffects();
		List<String> refused = new ArrayList<>();
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			// Arrow Crafting makes arrows from a material, and Arrow Shower spends four; their own cases prove them.
			if (path.equals("arrow_crafting") || path.equals("arrow_rain")) {
				continue;
			}
			// An earlier book may have moved the caster or the foe, and the set order is not fixed.
			Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
			caster.setPos(at.x, at.y, at.z);
			caster.setYRot(0.0F);
			caster.setXRot(0.0F);
			Vec3 foeAt = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z + 2.5));
			foe.snapTo(foeAt.x, foeAt.y, foeAt.z, 180.0F, 0.0F);
			if (!SkillSchoolGameTest.cast(caster, path, foe, false)) {
				refused.add(path);
			}
			ArcherSkills.clearFor(caster);
		}
		int arrows = caster.getInventory().countItem(Items.ARROW) + caster.getInventory().countItem(Items.SPECTRAL_ARROW);
		LOG.info("[BEHAVIOR] test=everyArcherBookCastsWithABowAndNoArrowAnywhere RESULT books={} refused={} arrowsCarried={} ammoBooks={}",
			SkillSchoolGameTest.ARCHER_IDS.size(), refused, arrows, ArcherSkills.AMMO_BOOKS);
		helper.assertTrue(ArcherSkills.AMMO_BOOKS.equals(java.util.Set.of("arrow_rain")), "these books need an arrow: " + ArcherSkills.AMMO_BOOKS);
		helper.assertTrue(arrows == 0, "the case carried " + arrows + " arrows, so it proves nothing");
		helper.assertTrue(refused.isEmpty(), "these books refused a bow and no arrow: " + refused);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void anAnvilImbuesTargetStrikingBooksOnABowOrCrossbowAndNotOnASword(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		List<String> bowRefused = new ArrayList<>();
		List<String> crossbowRefused = new ArrayList<>();
		List<String> swordTook = new ArrayList<>();
		for (Identifier spell : SpellImbue.BOW_IMBUABLE) {
			if (anvil(helper, player, new ItemStack(Items.BOW), spell).isEmpty()) {
				bowRefused.add(spell.getPath());
			}
			if (anvil(helper, player, new ItemStack(Items.CROSSBOW), spell).isEmpty()) {
				crossbowRefused.add(spell.getPath());
			}
			if (!anvil(helper, player, new ItemStack(Items.IRON_SWORD), spell).isEmpty()) {
				swordTook.add(spell.getPath());
			}
		}
		LOG.info("[BEHAVIOR] test=anAnvilImbuesTargetStrikingBooksOnABowOrCrossbowAndNotOnASword RESULT books={} bowRefused={} crossbowRefused={} swordTook={}",
			SpellImbue.BOW_IMBUABLE.size(), bowRefused, crossbowRefused, swordTook);
		helper.assertTrue(SpellImbue.BOW_IMBUABLE.size() == 6, "the bow takes " + SpellImbue.BOW_IMBUABLE.size() + " books, not 6");
		helper.assertTrue(bowRefused.isEmpty(), "a bow refused: " + bowRefused);
		helper.assertTrue(crossbowRefused.isEmpty(), "a crossbow refused: " + crossbowRefused);
		helper.assertTrue(swordTook.isEmpty(), "a sword took: " + swordTook);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void selfBuffAndNextShotBooksAreRefusedOnABow(final GameTestHelper helper) {
		ServerPlayer player = FighterBooksGameTest.caster(helper);
		List<String> took = new ArrayList<>();
		List<String> checked = new ArrayList<>();
		for (String path : SkillSchoolGameTest.ARCHER_IDS) {
			Identifier spell = SkillSchoolGameTest.id(path);
			if (SpellImbue.BOW_IMBUABLE.contains(spell)) {
				continue;
			}
			checked.add(path);
			if (!anvil(helper, player, new ItemStack(Items.BOW), spell).isEmpty()) {
				took.add(path);
			}
		}
		LOG.info("[BEHAVIOR] test=selfBuffAndNextShotBooksAreRefusedOnABow RESULT checked={} bowTook={}", checked, took);
		helper.assertTrue(checked.size() == 11, "the refused archer books number " + checked.size() + ", not 11");
		helper.assertTrue(took.isEmpty(), "a bow took: " + took);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void anImbuedBowArrowCastsTheBookOnTheVictimAtHalfPower(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, false);
		Identifier spell = SkillSchoolGameTest.id("rapid_fire");
		ItemStack bow = imbuedBow(caster, spell);
		Mob bookVictim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 6.0);
		Mob plainVictim = FighterBooksGameTest.foe(helper, CASTER_X + 3.0, CASTER_Z + 6.0);
		Mob imbuedVictim = FighterBooksGameTest.foe(helper, CASTER_X + 6.0, CASTER_Z + 6.0);
		SkillSchoolGameTest.cast(caster, "rapid_fire", bookVictim, false);
		double book = 1000.0 - bookVictim.getHealth();
		double plain = 1000.0 - arrowHit(helper, caster, plainVictim, ItemStack.EMPTY).getHealth();
		int castsBefore = CASTS.getOrDefault(caster.getUUID(), List.of()).size();
		double both = 1000.0 - arrowHit(helper, caster, imbuedVictim, bow).getHealth();
		int casts = CASTS.getOrDefault(caster.getUUID(), List.of()).size() - castsBefore;
		double cast = both - plain;
		LOG.info("[BEHAVIOR] test=anImbuedBowArrowCastsTheBookOnTheVictimAtHalfPower RESULT book={} plainArrow={} arrowAndCast={} cast={} ratio={} power={} casts={} arrows={}",
			book, plain, both, cast, cast / book, SpellImbue.IMBUE_POWER, casts, caster.getInventory().countItem(Items.ARROW));
		helper.assertTrue(book > 0.0 && plain > 0.0, "a hit dealt nothing, so the ratio proves nothing");
		helper.assertTrue(casts == 1, "one arrow hit recorded " + casts + " casts");
		helper.assertTrue(Math.abs(cast / book - SpellImbue.IMBUE_POWER) < ArcherSkills.SHOT_RATIO_TOLERANCE,
			"the imbued cast dealt " + (cast / book) + " of the book, not " + SpellImbue.IMBUE_POWER);
		bookVictim.discard();
		plainVictim.discard();
		imbuedVictim.discard();
		ArcherSkills.clearFor(caster);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void everyBowImbuableBookCastsFromAnImbuedArrowWithZeroArrows(final GameTestHelper helper) {
		List<String> silent = new ArrayList<>();
		for (Identifier spell : SpellImbue.BOW_IMBUABLE) {
			ServerPlayer caster = archer(helper, false);
			ItemStack bow = imbuedBow(caster, spell);
			Mob victim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 5.0);
			arrowHit(helper, caster, victim, bow);
			if (!CASTS.getOrDefault(caster.getUUID(), List.of()).contains(spell)) {
				silent.add(spell.getPath());
			}
			ArcherSkills.clearFor(caster);
			victim.discard();
		}
		LOG.info("[BEHAVIOR] test=everyBowImbuableBookCastsFromAnImbuedArrowWithZeroArrows RESULT books={} silent={}",
			SpellImbue.BOW_IMBUABLE.size(), silent);
		helper.assertTrue(silent.isEmpty(), "imbued arrows that cast nothing: " + silent);
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aMobHoldingAnImbuedBowNeverRunsThePlayerCast(final GameTestHelper helper) {
		ServerPlayer watcher = archer(helper, false);
		Identifier spell = SkillSchoolGameTest.id("rapid_fire");
		ItemStack bow = imbuedBow(watcher, spell);
		Mob skeleton = helper.spawnMob(EntityTypes.SKELETON, 8, 1, 8).withNoFreeWill().spawn();
		Mob victim = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 4.0);
		Arrow arrow = new Arrow(helper.getLevel(), skeleton, new ItemStack(Items.ARROW), bow);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, skeleton), ArcherSkills.SHOT_PROBE_DAMAGE);
		double lost = 1000.0 - victim.getHealth();
		int casts = CASTS.getOrDefault(watcher.getUUID(), List.of()).size();
		LOG.info("[BEHAVIOR] test=aMobHoldingAnImbuedBowNeverRunsThePlayerCast RESULT lost={} probe={} playerCasts={}",
			lost, ArcherSkills.SHOT_PROBE_DAMAGE, casts);
		helper.assertTrue(casts == 0, "a mob's arrow ran the player cast");
		helper.assertTrue(lost <= ArcherSkills.SHOT_PROBE_DAMAGE + 0.05, "a mob's imbued arrow dealt " + lost);
		skeleton.discard();
		victim.discard();
		helper.succeed();
	}

	/** A foe that a push can move: the shared foe has its AI off, and a mob with no AI never moves. */
	private static Mob movableFoe(final GameTestHelper helper, final double x, final double z) {
		Mob foe = helper.spawnMob(EntityTypes.ZOMBIE, (int) x, 1, (int) z).withNoFreeWill().spawn();
		Vec3 at = helper.absoluteVec(new Vec3(x, 1.0, z));
		foe.setPos(at.x, at.y, at.z);
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		foe.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		foe.setHealth(1000.0F);
		return foe;
	}

	private static double horizontal(final Vec3 a, final Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static ItemStack imbuedBow(final ServerPlayer caster, final Identifier spell) {
		ItemStack book = new ItemStack(SkillSchoolGameTest.book(spell.getPath()));
		ItemStack bow = SpellImbue.imbue(new ItemStack(Items.BOW), book);
		if (bow.isEmpty()) {
			throw new IllegalStateException("the bow refused " + spell);
		}
		caster.getInventory().setItem(WEAPON_SLOT, bow);
		return bow;
	}

	private static ItemStack anvil(final GameTestHelper helper, final ServerPlayer player, final ItemStack left, final Identifier spell) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(),
			ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))));
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(SkillSchoolGameTest.book(spell.getPath())));
		menu.createResult();
		ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
		return result.get(SpellImbue.IMBUED) == null ? ItemStack.EMPTY : result;
	}

	private static Mob arrowHit(final GameTestHelper helper, final ServerPlayer caster, final Mob victim, final ItemStack weapon) {
		Arrow arrow = new Arrow(helper.getLevel(), caster, new ItemStack(Items.ARROW), weapon.isEmpty() ? null : weapon);
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, caster), ArcherSkills.SHOT_PROBE_DAMAGE);
		return victim;
	}

	/** A bow in the weapon slot, and a full quiver only when asked: the zero-arrow cases pass false. */
	private static ServerPlayer archer(final GameTestHelper helper, final boolean arrows) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		if (arrows) {
			caster.getInventory().setItem(QUIVER_SLOT, new ItemStack(Items.ARROW, 64));
		}
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}
}
