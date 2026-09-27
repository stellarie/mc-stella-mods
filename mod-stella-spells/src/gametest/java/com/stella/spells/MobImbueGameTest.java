package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mob that wields an imbued weapon casts its spell too, at the imbued strength and on the same cooldown,
 * when the spell strikes a single foe. A spell that acts on its caster or on a spot stays silent in a
 * mob's hand. A village head's netherite blade is the mob this was made for.
 */
public class MobImbueGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.mobimbue");
	private static final float HIT = 4.0F;
	private static final Identifier EMBER_BRAND = Identifier.parse("mod-stella-spells:ember_brand");
	private static final Identifier QUICKSILVER = Identifier.parse("mod-stella-spells:quicksilver");
	private static final Map<UUID, List<Identifier>> CASTS = new ConcurrentHashMap<>();

	static {
		SpellImbue.setMobRecorder((wielder, spell, victim) -> CASTS.computeIfAbsent(wielder.getUUID(), key -> new CopyOnWriteArrayList<>()).add(spell));
	}

	@GameTest
	public void theMobSpellsAreEverySpellThatStrikesOneFoe(final GameTestHelper helper) {
		Set<Identifier> hostile = SpellbookCatalog.DEFINITIONS.stream()
			.filter(definition -> definition.targetMode() == TargetMode.HOSTILE)
			.map(SpellDefinition::id)
			.collect(Collectors.toSet());
		LOG.info("[SPELLTEST] test=theMobSpellsAreEverySpellThatStrikesOneFoe RESULT hostile={} mob={}", hostile.size(), SpellImbue.MOB_SPELLS.size());
		helper.assertTrue(hostile.equals(SpellImbue.MOB_SPELLS), "the mob spells " + SpellImbue.MOB_SPELLS + " are not the hostile spells " + hostile);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void everyMobSpellCastsFromAMobsImbuedHit(final GameTestHelper helper) {
		floor(helper);
		List<Identifier> missing = new ArrayList<>();
		int x = 0;
		for (Identifier spell : SpellImbue.MOB_SPELLS.stream().sorted().toList()) {
			Mob wielder = wielder(helper, spell, x % 8, 1);
			Mob victim = victim(helper, x % 8, 3 + (x / 8) * 3);
			hit(helper, wielder, victim);
			if (!CASTS.getOrDefault(wielder.getUUID(), List.of()).contains(spell)) {
				missing.add(spell);
			}
			wielder.discard();
			victim.discard();
			x++;
		}
		LOG.info("[SPELLTEST] test=everyMobSpellCastsFromAMobsImbuedHit RESULT spells={} missing={}", SpellImbue.MOB_SPELLS.size(), missing);
		helper.assertTrue(missing.isEmpty(), "a mob's imbued hit cast nothing for " + missing);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aMobsImbuedEmberBrandBurnsAndHurtsAtReducedStrength(final GameTestHelper helper) {
		floor(helper);
		Mob wielder = wielder(helper, EMBER_BRAND, 2, 1);
		Mob victim = victim(helper, 2, 3);
		hit(helper, wielder, victim);
		float taken = victim.getMaxHealth() - victim.getHealth();
		float expected = HIT + MoreSpells.EMBER_DAMAGE * (float) SpellImbue.IMBUE_POWER;
		LOG.info("[SPELLTEST] test=aMobsImbuedEmberBrandBurnsAndHurtsAtReducedStrength RESULT taken={} expected={} burning={}", taken, expected, victim.isOnFire());
		helper.assertTrue(Math.abs(taken - expected) < 0.001F, "the mob's imbued hit took " + taken + ", not " + expected);
		helper.assertTrue(victim.isOnFire(), "the mob's imbued hit set no fire");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aMobsImbuedSpellWaitsOutItsCooldown(final GameTestHelper helper) {
		floor(helper);
		Mob wielder = wielder(helper, EMBER_BRAND, 2, 1);
		Mob victim = victim(helper, 2, 3);
		hit(helper, wielder, victim);
		hit(helper, wielder, victim);
		int casts = CASTS.getOrDefault(wielder.getUUID(), List.of()).size();
		LOG.info("[SPELLTEST] test=aMobsImbuedSpellWaitsOutItsCooldown RESULT hits=2 casts={}", casts);
		helper.assertTrue(casts == 1, "two quick hits cast " + casts + " times");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aMobsImbuedSelfSpellStaysSilent(final GameTestHelper helper) {
		floor(helper);
		Mob wielder = wielder(helper, QUICKSILVER, 2, 1);
		Mob victim = victim(helper, 2, 3);
		hit(helper, wielder, victim);
		int casts = CASTS.getOrDefault(wielder.getUUID(), List.of()).size();
		LOG.info("[SPELLTEST] test=aMobsImbuedSelfSpellStaysSilent RESULT casts={} speed={}", casts, wielder.hasEffect(net.minecraft.world.effect.MobEffects.SPEED));
		helper.assertTrue(casts == 0 && !wielder.hasEffect(net.minecraft.world.effect.MobEffects.SPEED), "a mob cast a self spell from its imbued weapon");
		helper.succeed();
	}

	// The mob's own melee source, as its attack would deal it.
	private static void hit(final GameTestHelper helper, final Mob wielder, final Mob victim) {
		victim.damageCooldownTime = 0;
		victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(wielder), HIT);
	}

	private static Mob wielder(final GameTestHelper helper, final Identifier spell, final int x, final int z) {
		Mob wielder = helper.spawnMob(EntityTypes.VINDICATOR, x, 1, z).withNoFreeWill().spawn();
		ItemStack blade = new ItemStack(Items.IRON_AXE);
		blade.set(SpellImbue.IMBUED, new SpellImbue.ImbuedSpell(spell));
		wielder.setItemInHand(InteractionHand.MAIN_HAND, blade);
		return wielder;
	}

	// A cow: not undead, so every effect takes, and without armour, so a hit reads as its damage.
	private static Mob victim(final GameTestHelper helper, final int x, final int z) {
		Mob mob = helper.spawnMob((EntityType<? extends Mob>) EntityTypes.COW, x, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
