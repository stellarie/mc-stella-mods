package com.stella.equips;

import com.stella.equips.numbers.DamageNumbers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A hit shows a small red number over the entity and a heal a green one. The number rises and fades on
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

	@GameTest(maxTicks = 100)
	public void aHitShowsARedNumberThatRisesFadesAndGoes(final GameTestHelper helper) {
		ServerPlayer viewer = viewer(helper);
		PacketRecorder packets = PacketRecorder.of(viewer);
		Mob zombie = zombie(helper);
		zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 4.0F);
		List<Shown> shown = SHOWN.getOrDefault(zombie.getUUID(), List.of());
		helper.assertTrue(shown.size() == 1, "one hit showed " + shown.size() + " numbers");
		Shown number = shown.get(0);
		int id = number.display().getId();
		helper.assertTrue(!number.heal() && number.amount() == 4.0F && number.display().getText().getString().equals("4"), "the number reads " + number.display().getText().getString());
		helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(number.display().getText().getStyle().getColor()), "a hit's number is not red");
		helper.assertTrue(packets.addPackets(id) == 1 && number.display().level() == helper.getLevel() && helper.getLevel().getEntity(id) == null, "the number was not sent as a ghost display");
		helper.runAfterDelay(2, () -> {
			// The synced translation, read from the values the client receives.
			boolean risen = number.display().getEntityData().getNonDefaultValues().stream()
				.anyMatch(value -> value.value() instanceof org.joml.Vector3fc vector && vector.x() == 0.0F && vector.z() == 0.0F && vector.y() == DamageNumbers.RISE_BLOCKS);
			helper.assertTrue(risen, "the number does not rise to " + DamageNumbers.RISE_BLOCKS);
			helper.assertTrue(number.display().getTransformationInterpolationDuration() > 0, "the rise is not interpolated");
			helper.assertTrue(number.display().getTextOpacity() == DamageNumbers.END_OPACITY, "the number does not fade: " + number.display().getTextOpacity());
			helper.assertTrue(packets.dataPackets(id) >= 2, "the rise never reached the viewer");
		});
		helper.runAfterDelay(DamageNumbers.LIFETIME_TICKS + 2L, () -> {
			helper.assertTrue(packets.removePackets(id) == 1, "the number was never removed");
			LOG.info("[BEHAVIOR] test=aHitShowsARedNumberThatRisesFadesAndGoes RESULT text={} add={} data={} remove={}", number.display().getText().getString(), packets.addPackets(id), packets.dataPackets(id), packets.removePackets(id));
			helper.succeed();
		});
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
		helper.assertTrue(display.getText().getString().equals("+2.5") && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(display.getText().getStyle().getColor()), "the heal reads " + display.getText());
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
