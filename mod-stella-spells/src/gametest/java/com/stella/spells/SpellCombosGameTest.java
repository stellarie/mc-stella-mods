package com.stella.spells;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Surprise, spells: two spells cast in the right order on the same target, inside the window, land a
 * combo. The wrong order, another target, or a late second spell land nothing.
 */
public class SpellCombosGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.combos");
	private static final Identifier FROSTBIND = Identifier.parse("mod-stella-spells:frostbind");
	private static final Identifier SPARK_SHOT = Identifier.parse("mod-stella-spells:spark_shot");
	private static final String THERMAL_SHOCK = "Thermal Shock";
	private static final Map<UUID, List<String>> LANDED = new ConcurrentHashMap<>();

	static {
		SpellCombos.setRecorder((caster, combo, target) ->
			LANDED.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(combo.name() + "@" + target.getUUID())
		);
	}

	@GameTest
	public void everyComboJoinsTwoRealSpellsAndHasItsOwnName(final GameTestHelper helper) {
		Set<String> names = ConcurrentHashMap.newKeySet();
		for (SpellCombos.Combo combo : SpellCombos.combos()) {
			helper.assertTrue(isBook(combo.first()) && isBook(combo.second()) && !combo.first().equals(combo.second()), combo.name() + " does not join two different spellbooks");
			helper.assertTrue(names.add(combo.name()), "two combos share the name " + combo.name());
		}
		helper.assertTrue(names.size() >= 4, "only " + names.size() + " combos exist");
		LOG.info("[SPELLTEST] test=everyComboJoinsTwoRealSpellsAndHasItsOwnName RESULT combos={}", names);
		helper.succeed();
	}

	@GameTest
	public void frostbindThenSparkShotLandsAThermalShock(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper, 2.5);
		Mob comboTarget = target(helper, 2);
		cast(helper, caster, FROSTBIND);
		float before = comboTarget.getHealth();
		cast(helper, caster, SPARK_SHOT);
		float comboDrop = before - comboTarget.getHealth();

		ServerPlayer control = caster(helper, 5.5);
		Mob controlTarget = target(helper, 5);
		float controlBefore = controlTarget.getHealth();
		cast(helper, control, SPARK_SHOT);
		float plainDrop = controlBefore - controlTarget.getHealth();

		helper.assertTrue(LANDED.getOrDefault(caster.getUUID(), List.of()).contains(THERMAL_SHOCK + "@" + comboTarget.getUUID()), "no Thermal Shock landed: " + LANDED.get(caster.getUUID()));
		helper.assertTrue(Math.abs(comboDrop - plainDrop - SpellCombos.THERMAL_SHOCK_DAMAGE) < 1.0e-4, "the combo hit took " + comboDrop + " against a plain " + plainDrop);
		LOG.info("[SPELLTEST] test=frostbindThenSparkShotLandsAThermalShock RESULT comboDrop={} plainDrop={}", comboDrop, plainDrop);
		helper.succeed();
	}

	@GameTest
	public void theWrongOrderLandsNoCombo(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper, 2.5);
		target(helper, 2);
		cast(helper, caster, SPARK_SHOT);
		cast(helper, caster, FROSTBIND);
		helper.assertTrue(LANDED.getOrDefault(caster.getUUID(), List.of()).isEmpty(), "Spark Shot then Frostbind landed " + LANDED.get(caster.getUUID()));
		LOG.info("[SPELLTEST] test=theWrongOrderLandsNoCombo RESULT combos=0");
		helper.succeed();
	}

	@GameTest
	public void aComboNeedsTheSameTarget(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper, 2.5);
		Mob first = target(helper, 2);
		cast(helper, caster, FROSTBIND);
		first.discard();
		target(helper, 2);
		cast(helper, caster, SPARK_SHOT);
		helper.assertTrue(LANDED.getOrDefault(caster.getUUID(), List.of()).isEmpty(), "a combo landed across two targets: " + LANDED.get(caster.getUUID()));
		LOG.info("[SPELLTEST] test=aComboNeedsTheSameTarget RESULT combos=0");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void theComboWindowCloses(final GameTestHelper helper) {
		helper.setTime(18_000L);
		ServerPlayer caster = caster(helper, 2.5);
		target(helper, 2);
		cast(helper, caster, FROSTBIND);
		helper.runAfterDelay(SpellCombos.WINDOW_TICKS + 5L, () -> {
			cast(helper, caster, SPARK_SHOT);
			helper.assertTrue(LANDED.getOrDefault(caster.getUUID(), List.of()).isEmpty(), "a late Spark Shot still landed " + LANDED.get(caster.getUUID()));
			LOG.info("[SPELLTEST] test=theComboWindowCloses RESULT combos=0 window={}", SpellCombos.WINDOW_TICKS);
			helper.succeed();
		});
	}

	private static boolean isBook(final Identifier id) {
		return BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR) instanceof SpellbookItem;
	}

	private static void cast(final GameTestHelper helper, final ServerPlayer caster, final Identifier id) {
		Item item = BuiltInRegistries.ITEM.get(id).map(Holder.Reference::value).orElse(Items.AIR);
		helper.assertTrue(item instanceof SpellbookItem, id + " is not a spellbook");
		SpellbookItem book = (SpellbookItem) item;
		caster.setItemInHand(InteractionHand.MAIN_HAND, book.getDefaultInstance());
		InteractionResult result = book.use(helper.getLevel(), caster, InteractionHand.MAIN_HAND);
		helper.assertTrue(result == InteractionResult.SUCCESS, id + " did not cast: " + result + " caster=" + caster.position() + " look=" + caster.getLookAngle()
			+ " targets=" + helper.getLevel().getEntitiesOfClass(Mob.class, caster.getBoundingBox().inflate(12.0), Mob::isAlive).stream().map(mob -> mob.getType().toShortString() + "@" + mob.position()).toList());
	}

	// A durable zombie in the caster's lane, six blocks ahead, standing on a floor so it cannot drop out of the aim line.
	private static Mob target(final GameTestHelper helper, final int lane) {
		for (int z = 0; z <= 9; z++) {
			helper.setBlock(lane, 0, z, Blocks.STONE);
		}
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, lane, 1, 7).withNoFreeWill().spawn();
		mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
		mob.setHealth(100.0F);
		return mob;
	}

	private static ServerPlayer caster(final GameTestHelper helper, final double lane) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 position = helper.absoluteVec(new Vec3(lane, 1.0, 1.5));
		player.setPos(position.x, position.y, position.z);
		player.setYRot(0.0f);
		player.setXRot(0.0f);
		return player;
	}
}
