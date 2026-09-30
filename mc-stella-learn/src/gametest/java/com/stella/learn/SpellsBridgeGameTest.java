package com.stella.learn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** With the spells mod loaded, mobs that carry its spells answer callouts with them, and without it nothing changes. */
public class SpellsBridgeGameTest {
	private static final int LONG = 700;
	private static final String CAST_TAG = "mod-stella-spells.cast.";

	static {
		CalloutKits.register();
	}

	/** In a run without the spells mod these cases have nothing to prove, and say so in the log instead of passing silently. */
	private static boolean skipWithoutSpells(final GameTestHelper helper, final String test) {
		if (SpellsBridge.present()) {
			return false;
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test={} RESULT skipped=noSpellsMod", test);
		helper.succeed();
		return true;
	}

	private static Mob zombie(final GameTestHelper helper, final int x, final int z, final String spell) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, x, LearnArena.SPAWN_Y, z).spawn();
		mob.setNoAi(true);
		if (spell != null) {
			mob.addTag(CAST_TAG + spell);
		}

		return mob;
	}

	private static Callout call(final Identifier type, final Mob caller, final ServerPlayer player, final Identifier detail) {
		return new Callout(type, caller.getUUID(), player.getUUID(), 1, 200, null, detail);
	}

	private static CalloutBoard.Answer answerOf(final List<CalloutBoard.Answer> answers, final Mob mob) {
		return answers.stream().filter(a -> a.receiver().equals(mob.getUUID())).findFirst().orElseThrow();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobCarryingASupportSpellHealsANearbyCaller(final GameTestHelper helper) {
		if (skipWithoutSpells(helper, "aMobCarryingASupportSpellHealsANearbyCaller")) {
			return;
		}

		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20, null);
		Mob healer = zombie(helper, 22, 20, "mending_word");
		LearnArena.whenReady(helper, List.of(player, caller, healer), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			float before = caller.getHealth();
			CalloutBoard.Answer answer = answerOf(
				CalloutBoard.send(caller, player, call(CalloutKits.HEAL, caller, player, null), LearnConfig.defaults()), healer
			);
			float gained = caller.getHealth() - before;
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobCarryingASupportSpellHealsANearbyCaller RESULT accepted={} gained={}", answer.accepted(), gained);
			helper.assertTrue(answer.accepted(), "the spell carrier answered " + answer);
			helper.assertTrue(gained > 0.0F, "the caller gained " + gained);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aSupportCasterBeyondTheSpellsReachDeclines(final GameTestHelper helper) {
		if (skipWithoutSpells(helper, "aSupportCasterBeyondTheSpellsReachDeclines")) {
			return;
		}

		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 16, 20, null);
		Mob healer = zombie(helper, 30, 20, "mending_word");
		LearnArena.whenReady(helper, List.of(player, caller, healer), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			float before = caller.getHealth();
			CalloutBoard.Answer answer = answerOf(
				CalloutBoard.send(caller, player, call(CalloutKits.HEAL, caller, player, null), LearnConfig.defaults()), healer
			);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aSupportCasterBeyondTheSpellsReachDeclines RESULT reason={}", answer.reason());
			helper.assertTrue(!answer.accepted() && answer.reason().equals("cannot"), "the far caster answered " + answer);
			helper.assertTrue(caller.getHealth() == before, "the caller was healed from too far");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void aMobCarryingAStrikeSpellDebuffsThePlayer(final GameTestHelper helper) {
		if (skipWithoutSpells(helper, "aMobCarryingAStrikeSpellDebuffsThePlayer")) {
			return;
		}

		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20, null);
		Mob striker = zombie(helper, 30, 24, "frostbind");
		LearnArena.whenReady(helper, List.of(player, caller, striker), () -> {
			striker.setTarget(player);
			helper.assertTrue(!player.hasEffect(MobEffects.SLOWNESS), "the player was slow already, so the case proves nothing");
			CalloutBoard.send(caller, player, call(CalloutKits.DEBUFF, caller, player, Identifier.withDefaultNamespace("slowness")), LearnConfig.defaults());
			StellaLearn.LOGGER.info("[BEHAVIOR] test=aMobCarryingAStrikeSpellDebuffsThePlayer RESULT slow={}", player.hasEffect(MobEffects.SLOWNESS));
			helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "the strike spell never landed");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void theSpellsModIsSuggestedNotRequired(final GameTestHelper helper) {
		var metadata = FabricLoader.getInstance().getModContainer(StellaLearn.MOD_ID).orElseThrow().getMetadata();
		boolean required = metadata.getDependencies().stream()
			.anyMatch(d -> d.getKind() == ModDependency.Kind.DEPENDS && d.getModId().equals(SpellsBridge.SPELLS_MOD));
		boolean suggested = metadata.getDependencies().stream()
			.anyMatch(d -> d.getKind() == ModDependency.Kind.SUGGESTS && d.getModId().equals(SpellsBridge.SPELLS_MOD));
		StellaLearn.LOGGER.info("[BEHAVIOR] test=theSpellsModIsSuggestedNotRequired RESULT required={} suggested={}", required, suggested);
		helper.assertTrue(!required && suggested, "required=" + required + " suggested=" + suggested);
		helper.succeed();
	}

	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void onlyTheBridgesInnerClassTouchesTheSpellsMod(final GameTestHelper helper) throws IOException {
		int leaks = 0;
		StringBuilder where = new StringBuilder();
		for (String name : List.of("SpellsBridge", "CalloutKits", "CalloutBoard", "LearnGoals", "StellaLearn")) {
			try (InputStream in = SpellsBridge.class.getResourceAsStream("/com/stella/learn/" + name + ".class")) {
				String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
				if (text.contains("com/stella/spells")) {
					leaks++;
					where.append(name).append(' ');
				}
			}
		}

		StellaLearn.LOGGER.info("[BEHAVIOR] test=onlyTheBridgesInnerClassTouchesTheSpellsMod RESULT leaks={} in={}", leaks, where);
		helper.assertTrue(leaks == 0, "classes that load without the spells mod name its classes: " + where);
		helper.succeed();
	}

	/** Only meaningful in the run without the spells mod: a mob tagged with a spell is then declined like any other. */
	@GameTest(maxTicks = LONG, padding = LearnArena.PADDING)
	public void whenTheSpellsModIsAbsentTheBridgeIsInert(final GameTestHelper helper) {
		if (SpellsBridge.present()) {
			StellaLearn.LOGGER.info("[BEHAVIOR] test=whenTheSpellsModIsAbsentTheBridgeIsInert RESULT skipped=spellsModPresent");
			helper.succeed();
			return;
		}

		LearnArena.floor(helper);
		ServerPlayer player = LearnArena.player(helper, 24.5, 24.5);
		Mob caller = zombie(helper, 20, 20, null);
		Mob healer = zombie(helper, 22, 20, "mending_word");
		LearnArena.whenReady(helper, List.of(player, caller, healer), () -> {
			caller.setHealth(caller.getMaxHealth() / 2.0F);
			CalloutBoard.Answer answer = answerOf(
				CalloutBoard.send(caller, player, call(CalloutKits.HEAL, caller, player, null), LearnConfig.defaults()), healer
			);
			StellaLearn.LOGGER.info("[BEHAVIOR] test=whenTheSpellsModIsAbsentTheBridgeIsInert RESULT reason={}", answer.reason());
			helper.assertTrue(!answer.accepted() && answer.reason().equals("cannot"), "the tagged mob answered " + answer);
			helper.succeed();
		});
	}
}
