package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spell Writer copies the off-hand book into scrolls; a hostile mob's melee weapon sometimes spawns imbued. */
public class SpellWriterGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.spellwriter");
	private static final int ZOMBIES = 300;

	@GameTest
	public void spellWriterWritesThirtyTwoScrollsOfTheOffHandBookAndNothingElse(final GameTestHelper helper) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		Item quicksilver = BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:quicksilver"));
		Item scroll = BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:quicksilver_scroll"));

		caster.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		boolean bare = write(caster);
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAPER));
		boolean notABook = write(caster);
		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mod-stella-spells:summon_skeleton"))));
		boolean noScroll = write(caster);
		int none = count(caster, scroll);
		helper.assertTrue(!bare && !notABook && !noScroll && none == 0 && !hasAnyScroll(caster),
			"Spell Writer wrote with no usable book: " + bare + " " + notABook + " " + noScroll + " scrolls=" + none);

		caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(quicksilver));
		boolean written = write(caster);
		int scrolls = count(caster, scroll);
		LOG.info("[SPELLTEST] test=spellWriterWritesThirtyTwoScrollsOfTheOffHandBookAndNothingElse RESULT written={} scrolls={}", written, scrolls);
		helper.assertTrue(written && scrolls == 32, "Spell Writer wrote " + scrolls + " scrolls, cast=" + written);
		helper.assertTrue(caster.getOffhandItem().is(quicksilver), "the off-hand book was used up");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void aSmallShareOfHostileMobWeaponsSpawnImbuedAndTheSpellCasts(final GameTestHelper helper) {
		helper.setTime(18_000L);
		List<Mob> zombies = new ArrayList<>();
		for (int i = 0; i < ZOMBIES; i++) {
			Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 1 + i % 6, 1, 1 + (i / 6) % 6).withNoFreeWill().spawn();
			zombie.setNoGravity(true);
			zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			zombies.add(zombie);
		}
		helper.runAfterDelay(5L, () -> {
			List<Mob> imbued = zombies.stream().filter(zombie -> SpellImbue.spellsOf(zombie.getMainHandItem()).size() > 0).toList();
			boolean oneSpellEach = imbued.stream().allMatch(zombie -> SpellImbue.spellsOf(zombie.getMainHandItem()).size() == 1
				&& SpellImbue.MOB_SPELLS.contains(SpellImbue.spellsOf(zombie.getMainHandItem()).get(0)));
			LOG.info("[SPELLTEST] test=aSmallShareOfHostileMobWeaponsSpawnImbuedAndTheSpellCasts RESULT imbued={} of={}", imbued.size(), ZOMBIES);
			helper.assertTrue(!imbued.isEmpty() && imbued.size() <= ZOMBIES / 7, imbued.size() + " of " + ZOMBIES + " weapons spawned imbued, not about 5 percent");
			helper.assertTrue(oneSpellEach, "an imbued mob weapon holds more than one spell, or one a mob cannot cast");

			Mob wielder = imbued.get(0);
			Mob victim = helper.spawnMob(EntityTypes.COW, 0, 1, 0).withNoFreeWill().spawn();
			victim.damageCooldownTime = 0;
			victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(wielder), 4.0F);
			helper.assertTrue(MobImbue.tracks(wielder.getUUID()), "the imbued mob weapon cast nothing on a hit");
			zombies.forEach(Mob::discard);
			victim.discard();
			helper.succeed();
		});
	}

	private static boolean write(final ServerPlayer caster) {
		SpellModifiers.CastModifiers modifiers = SpellModifiers.resolve(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		return SpellbookCatalog.get(Identifier.parse("mod-stella-spells:spell_writer")).effect().apply(caster, null, caster.position(), modifiers);
	}

	private static int count(final ServerPlayer caster, final Item item) {
		int total = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean hasAnyScroll(final ServerPlayer caster) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.getItem() instanceof SpellScrollItem) {
				return true;
			}
		}
		return false;
	}
}
