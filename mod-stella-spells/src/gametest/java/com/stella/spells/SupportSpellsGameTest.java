package com.stella.spells;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Three support spells, each laid on the caster and every ally around: players, villagers, golems and
 * tamed pets. A hostile in the same circle gets nothing, and neither does a player beyond it.
 */
public class SupportSpellsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.support");

	@GameTest
	public void bulwarkHymnShieldsAlliesAroundTheCaster(final GameTestHelper helper) {
		assertSupport(helper, "bulwarkHymnShieldsAlliesAroundTheCaster", "bulwark_hymn", MobEffects.ABSORPTION, MoreSpells.BULWARK_AMPLIFIER, MoreSpells.BULWARK_TICKS);
	}

	@GameTest
	public void tailwindHastensAlliesAroundTheCaster(final GameTestHelper helper) {
		assertSupport(helper, "tailwindHastensAlliesAroundTheCaster", "tailwind", MobEffects.SPEED, MoreSpells.TAILWIND_AMPLIFIER, MoreSpells.TAILWIND_TICKS);
	}

	@GameTest
	public void battleHymnSharpensAlliesAroundTheCaster(final GameTestHelper helper) {
		assertSupport(helper, "battleHymnSharpensAlliesAroundTheCaster", "battle_hymn", MobEffects.STRENGTH, MoreSpells.BATTLE_HYMN_AMPLIFIER, MoreSpells.BATTLE_HYMN_TICKS);
	}

	private static void assertSupport(final GameTestHelper helper, final String test, final String spell, final Holder<MobEffect> effect, final int amplifier, final int ticks) {
		floor(helper);
		ServerPlayer caster = player(helper, new Vec3(2.5, 1.0, 1.5));
		ServerPlayer ally = player(helper, new Vec3(4.5, 1.0, 3.5));
		ServerPlayer far = player(helper, new Vec3(2.5, 1.0, 1.5 + MoreSpells.SUPPORT_RADIUS + 3.0));
		Mob villager = helper.spawnMob(EntityTypes.VILLAGER, 1, 1, 4).withNoFreeWill().spawn();
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 5).withNoFreeWill().spawn();
		SpellbookItem book = (SpellbookItem) BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, spell));
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		LOG.info("[SPELLTEST] test={} RESULT result={} caster={} ally={} villager={} zombie={} far={}", test, result,
			caster.getEffect(effect), ally.getEffect(effect), villager.getEffect(effect), zombie.hasEffect(effect), far.hasEffect(effect));
		helper.assertTrue(result == InteractionResult.SUCCESS, spell + " did not cast: " + result);
		for (var blessed : java.util.List.of(caster, ally, villager)) {
			MobEffectInstance applied = blessed.getEffect(effect);
			helper.assertTrue(applied != null && applied.getAmplifier() == amplifier && applied.getDuration() == ticks, spell + " left " + applied + " on the " + blessed.getType());
		}
		helper.assertFalse(zombie.hasEffect(effect), spell + " blessed a hostile");
		helper.assertFalse(far.hasEffect(effect), spell + " reached past its radius");
		helper.succeed();
	}

	private static ServerPlayer player(final GameTestHelper helper, final Vec3 local) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(local);
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static void floor(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}
}
