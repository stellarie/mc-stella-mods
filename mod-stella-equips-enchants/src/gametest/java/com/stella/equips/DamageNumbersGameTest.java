package com.stella.equips;

import com.stella.equips.numbers.DamageNumbers;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hit shows a small bold white number over the entity and a heal a green one. A critical hit, vanilla or
 * RPG, shows a larger yellow one. The number rises and fades on
 * the client, from one update the server sends, and is removed once its moment has passed. It is sent
 * to the players nearby, never added to the level, so it can never be saved into the world.
 */
public class DamageNumbersGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-equips-test.numbers");
	private static final Map<UUID, List<Shown>> SHOWN = new ConcurrentHashMap<>();

	private record Shown(Display.TextDisplay display, float amount, boolean heal) {
	}

	static {
		DamageNumbers.setRecorder((entity, display, amount, heal) ->
			SHOWN.computeIfAbsent(entity.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Shown(display, amount, heal)));
	}

	@GameTest(maxTicks = DamageNumbers.LIFETIME_TICKS + 40)
	public void aHitShowsAWhiteNumberThatRisesFadesAndGoes(final GameTestHelper helper) {
		ServerPlayer viewer = viewer(helper);
		PacketRecorder packets = PacketRecorder.of(viewer);
		Mob zombie = zombie(helper);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
		List<Shown> shown = SHOWN.getOrDefault(zombie.getUUID(), List.of());
		helper.assertTrue(shown.size() == 1, "one hit showed " + shown.size() + " numbers");
		Shown number = shown.get(0);
		int id = number.display().getId();
		helper.assertTrue(!number.heal() && number.amount() == 4.0F && number.display().getText().getString().equals("4"), "the number reads " + number.display().getText().getString());
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.WHITE).equals(number.display().getText().getStyle().getColor()), "a hit's number is not white");
		helper.assertTrue(number.display().getText().getStyle().isBold(), "a hit's number is not bold");
		helper.assertTrue(scaleOf(number.display()) == DamageNumbers.SCALE, "a hit's number has scale " + scaleOf(number.display()));
		helper.assertTrue(packets.addPackets(id) == 1 && number.display().level() == helper.getLevel() && helper.getLevel().getEntity(id) == null, "the number was not sent as a ghost display");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(number.display().getTransformationInterpolationDuration() > 0, "the first leg is not interpolated");
			helper.assertTrue(number.display().getTextOpacity() == DamageNumbers.MID_OPACITY, "the first leg does not stay above alpha 128: " + number.display().getTextOpacity());
		});
		helper.runAfterDelay(DamageNumbers.SECOND_LEG_TICK + 1L, () -> {
			// The synced translation, read from the values the client receives.
			boolean risen = number.display().getEntityData().getNonDefaultValues().stream()
				.anyMatch(value -> value.value() instanceof org.joml.Vector3fc vector && vector.x() == 0.0F && vector.z() == 0.0F && vector.y() == DamageNumbers.RISE_BLOCKS);
			helper.assertTrue(risen, "the number does not rise to " + DamageNumbers.RISE_BLOCKS);
			helper.assertTrue(number.display().getTransformationInterpolationDuration() > 0, "the second leg is not interpolated");
			helper.assertTrue(number.display().getTransformationInterpolationDelay() == 0, "the second leg does not restart the clock");
			helper.assertTrue(number.display().getTextOpacity() == DamageNumbers.END_OPACITY, "the number does not fade: " + number.display().getTextOpacity());
			helper.assertTrue(packets.dataPackets(id) >= 4, "the three legs never reached the viewer");
		});
		helper.runAfterDelay(DamageNumbers.LIFETIME_TICKS - 2L, () ->
			helper.assertTrue(DamageNumbers.LIFETIME_TICKS == 100 && packets.removePackets(id) == 0, "the number was removed before its " + DamageNumbers.LIFETIME_TICKS + " ticks"));
		helper.runAfterDelay(DamageNumbers.LIFETIME_TICKS + 2L, () -> {
			helper.assertTrue(packets.removePackets(id) == 1, "the number was never removed");
			LOG.info("[BEHAVIOR] test=aHitShowsAWhiteNumberThatRisesFadesAndGoes RESULT text={} add={} data={} remove={}", number.display().getText().getString(), packets.addPackets(id), packets.dataPackets(id), packets.removePackets(id));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = DamageNumbers.SECOND_LEG_TICK + 10)
	public void aVanillaCriticalHitShowsALargerBoldYellowNumber(final GameTestHelper helper) {
		ServerPlayer attacker = viewer(helper);
		Mob zombie = zombie(helper);
		withoutRpgShare(() -> blow(attacker, zombie, true));
		Shown number = onlyNumber(helper, zombie);
		Display.TextDisplay display = number.display();
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(display.getText().getStyle().getColor()), "a critical number reads " + display.getText().getStyle().getColor());
		helper.assertTrue(display.getText().getStyle().isBold(), "a critical number is not bold");
		helper.assertTrue(scaleOf(display) == DamageNumbers.CRIT_SCALE, "a critical number has scale " + scaleOf(display));
		helper.assertTrue(DamageNumbers.CRIT_SCALE / DamageNumbers.SCALE > 1.25F && DamageNumbers.CRIT_SCALE / DamageNumbers.SCALE < 1.35F, "the critical size is not about 1.3 times");
		helper.runAfterDelay(DamageNumbers.SECOND_LEG_TICK + 1L, () -> {
			helper.assertTrue(scaleOf(display) == DamageNumbers.CRIT_SCALE, "the rise legs lost the critical size: " + scaleOf(display));
			LOG.info("[BEHAVIOR] test=aVanillaCriticalHitShowsALargerBoldYellowNumber RESULT text={} scale={}", display.getText().getString(), scaleOf(display));
			helper.succeed();
		});
	}

	@GameTest
	public void aBlowFromTheGroundShowsAWhiteNumber(final GameTestHelper helper) {
		ServerPlayer attacker = viewer(helper);
		Mob zombie = zombie(helper);
		withoutRpgShare(() -> blow(attacker, zombie, false));
		Display.TextDisplay display = onlyNumber(helper, zombie).display();
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.WHITE).equals(display.getText().getStyle().getColor()) && scaleOf(display) == DamageNumbers.SCALE,
			"a plain blow read " + display.getText().getStyle().getColor() + " at scale " + scaleOf(display));
		LOG.info("[BEHAVIOR] test=aBlowFromTheGroundShowsAWhiteNumber RESULT text={}", display.getText().getString());
		helper.succeed();
	}

	@GameTest
	public void anRpgCriticalHitShowsALargerBoldYellowNumber(final GameTestHelper helper) {
		viewer(helper);
		Mob zombie = zombie(helper);
		Mob plain = zombie(helper);
		// The stand-in answers like the rpg share does: true only inside the critical hit of its victim.
		Function<Object[], Boolean> standIn = args -> args[0] == zombie;
		Object previous = FabricLoader.getInstance().getObjectShare().put(DamageNumbers.RPG_CRIT_SHARE_KEY, standIn);
		try {
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
			plain.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
		} finally {
			restore(previous);
		}
		Display.TextDisplay display = onlyNumber(helper, zombie).display();
		Display.TextDisplay other = onlyNumber(helper, plain).display();
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.YELLOW).equals(display.getText().getStyle().getColor()) && display.getText().getStyle().isBold()
			&& scaleOf(display) == DamageNumbers.CRIT_SCALE, "an rpg critical number read " + display.getText().getStyle().getColor() + " at scale " + scaleOf(display));
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.WHITE).equals(other.getText().getStyle().getColor()) && scaleOf(other) == DamageNumbers.SCALE,
			"a hit the share called plain read " + other.getText().getStyle().getColor() + " at scale " + scaleOf(other));
		LOG.info("[BEHAVIOR] test=anRpgCriticalHitShowsALargerBoldYellowNumber RESULT critScale={} plainScale={}", scaleOf(display), scaleOf(other));
		helper.succeed();
	}

	@GameTest
	public void aHealShowsAGreenNumber(final GameTestHelper helper) {
		viewer(helper);
		Mob zombie = zombie(helper);
		zombie.setHealth(10.0F);
		zombie.heal(2.5F);
		List<Shown> shown = SHOWN.getOrDefault(zombie.getUUID(), List.of());
		helper.assertTrue(shown.size() == 1 && shown.get(0).heal() && shown.get(0).amount() == 2.5F, "a heal showed " + shown);
		Display.TextDisplay display = shown.get(0).display();
		helper.assertTrue(display.getText().getString().equals("+3") && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(display.getText().getStyle().getColor()), "the heal reads " + display.getText());
		LOG.info("[BEHAVIOR] test=aHealShowsAGreenNumber RESULT text={}", display.getText().getString());
		helper.succeed();
	}

	@GameTest
	public void nothingShowsWhenNothingChanges(final GameTestHelper helper) {
		viewer(helper);
		Mob zombie = zombie(helper);
		zombie.heal(5.0F);
		int afterHeal = SHOWN.getOrDefault(zombie.getUUID(), List.of()).size();
		zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 200, 4));
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
		helper.assertTrue(afterHeal == 0, "a heal at full health showed a number");
		helper.assertTrue(SHOWN.getOrDefault(zombie.getUUID(), List.of()).isEmpty(), "a hit that did no harm showed " + SHOWN.getOrDefault(zombie.getUUID(), List.of()) + " with health " + zombie.getHealth() + "/" + zombie.getMaxHealth());
		LOG.info("[BEHAVIOR] test=nothingShowsWhenNothingChanges RESULT shown=0");
		helper.succeed();
	}

	@GameTest
	public void aPlayerSeesNoNumberOverTheirOwnHead(final GameTestHelper helper) {
		ServerPlayer player = viewer(helper);
		PacketRecorder packets = PacketRecorder.of(player);
		player.setHealth(10.0F);
		player.heal(2.0F);
		List<Shown> shown = SHOWN.getOrDefault(player.getUUID(), List.of());
		helper.assertTrue(shown.size() == 1, "the player's heal showed " + shown.size() + " numbers, so the case proves nothing");
		helper.assertTrue(packets.addPackets(shown.get(0).display().getId()) == 0, "a player was sent the number over their own head");
		LOG.info("[BEHAVIOR] test=aPlayerSeesNoNumberOverTheirOwnHead RESULT sentToSelf=0");
		helper.succeed();
	}

	@GameTest
	public void anInvisibleEntityShowsNoNumber(final GameTestHelper helper) {
		viewer(helper);
		Mob zombie = zombie(helper);
		zombie.setInvisible(true);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
		int shown = SHOWN.getOrDefault(zombie.getUUID(), List.of()).size();
		LOG.info("[BEHAVIOR] test=anInvisibleEntityShowsNoNumber RESULT shown={} health={}", shown, zombie.getHealth());
		helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the hit did no harm, so the case proves nothing");
		helper.assertTrue(shown == 0, "an invisible zombie showed " + shown + " numbers and gave its place away");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void rapidHitsShowOneNumberPerGap(final GameTestHelper helper) {
		viewer(helper);
		Mob zombie = zombie(helper);
		for (int hit = 0; hit < 3; hit++) {
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2.0F);
		}
		int burst = SHOWN.getOrDefault(zombie.getUUID(), List.of()).size();
		helper.runAfterDelay(DamageNumbers.MIN_GAP_TICKS, () -> {
			zombie.damageCooldownTime = 0;
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 2.0F);
			int later = SHOWN.getOrDefault(zombie.getUUID(), List.of()).size();
			LOG.info("[BEHAVIOR] test=rapidHitsShowOneNumberPerGap RESULT burst={} later={} health={}", burst, later, zombie.getHealth());
			helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth() - 8.0F, "not every hit landed, so the case proves nothing: " + zombie.getHealth());
			helper.assertTrue(burst == 1 && later == 2, "three hits in a tick showed " + burst + " numbers and a hit after the gap made " + later);
			helper.succeed();
		});
	}

	private static Shown onlyNumber(final GameTestHelper helper, final Mob zombie) {
		List<Shown> shown = SHOWN.getOrDefault(zombie.getUUID(), List.of());
		helper.assertTrue(shown.size() == 1, "one hit showed " + shown.size() + " numbers");
		return shown.get(0);
	}

	/** The scale the client receives: the synced vector whose three parts are equal and not zero. */
	private static float scaleOf(final Display.TextDisplay display) {
		return display.getEntityData().getNonDefaultValues().stream()
			.map(value -> value.value())
			.filter(value -> value instanceof org.joml.Vector3fc vector && vector.x() != 0.0F && vector.x() == vector.y() && vector.y() == vector.z())
			.map(value -> ((org.joml.Vector3fc) value).x())
			.findFirst().orElse(-1.0F);
	}

	/** A full charge attack, falling when it is meant to be a critical hit. The charge is set the way the unique tests do. */
	private static void blow(final ServerPlayer attacker, final LivingEntity target, final boolean falling) {
		try {
			Field charge = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			charge.setAccessible(true);
			charge.setInt(attacker, 100);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(failure);
		}
		attacker.setOnGround(!falling);
		attacker.fallDistance = falling ? 2.0 : 0.0;
		attacker.attack(target);
	}

	/** Runs with no rpg share published, the way a pack without that mod runs. */
	private static void withoutRpgShare(final Runnable body) {
		Object previous = FabricLoader.getInstance().getObjectShare().remove(DamageNumbers.RPG_CRIT_SHARE_KEY);
		try {
			body.run();
		} finally {
			restore(previous);
		}
	}

	private static void restore(final Object previous) {
		if (previous == null) {
			FabricLoader.getInstance().getObjectShare().remove(DamageNumbers.RPG_CRIT_SHARE_KEY);
		} else {
			FabricLoader.getInstance().getObjectShare().put(DamageNumbers.RPG_CRIT_SHARE_KEY, previous);
		}
	}

	private static ServerPlayer viewer(final GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		player.setPos(at.x, at.y, at.z);
		return player;
	}

	private static Mob zombie(final GameTestHelper helper) {
		Mob zombie = helper.spawnMob(EntityTypes.ZOMBIE, 3, 1, 3).withNoFreeWill().spawn();
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setHealth(40.0F);
		return zombie;
	}
}
