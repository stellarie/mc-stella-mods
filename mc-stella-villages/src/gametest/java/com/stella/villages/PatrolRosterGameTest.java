package com.stella.villages;

import com.stella.mobs.behavior.ShieldUseGoal;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mission.GuardQuests;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A village patrol holds seven members: the duke, two melee guards, two ranged guards, the mage and the trainer. A village saved
 * with only the older four gains its ranged guards the next time its patrol is checked, and keeps its melee guards.
 */
public class PatrolRosterGameTest {
	/** The roster version before ranged guards. */
	private static final int ROSTER_VERSION_WITHOUT_RANGED_GUARDS = 3;

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void fourGuards(final GameTestHelper helper) {
		String test = "fourGuards";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Map<PatrolState.Role, List<Mob>> roster = roster(helper);
		PatrolTestSupport.result(test, "newVillage=" + describe(roster));
		int expected = 2 + PatrolService.PILLAGER_COUNT + PatrolService.RANGED_GUARD_COUNT + PatrolService.TRAINER_COUNT;
		helper.assertValueEqual(Integer.valueOf(PatrolService.PATROL_MEMBER_COUNT), Integer.valueOf(expected), "the shipped roster size");
		helper.assertValueEqual(Integer.valueOf(size(roster)), Integer.valueOf(expected), "members of a new village");
		expect(helper, roster, PatrolState.Role.HEAD, 1);
		expect(helper, roster, PatrolState.Role.PILLAGER, PatrolService.PILLAGER_COUNT);
		expect(helper, roster, PatrolState.Role.RANGED_GUARD, PatrolService.RANGED_GUARD_COUNT);
		expect(helper, roster, PatrolState.Role.VILLAGE_WITCH, 1);
		expect(helper, roster, PatrolState.Role.TRAINER, PatrolService.TRAINER_COUNT);
		checkGuardGear(helper, roster);

		// The old village has no ranged guards
		// and a reopened roster.
		Set<UUID> meleeIds = ids(roster.getOrDefault(PatrolState.Role.PILLAGER, List.of()));
		VillageWorldData data = VillageWorldData.get(helper.getLevel());
		for (Mob ranged : List.copyOf(roster.getOrDefault(PatrolState.Role.RANGED_GUARD, List.of()))) {
			data.removeMember(ranged.getUUID());
			ranged.discard();
		}
		Map<PatrolState.Role, List<Mob>> old = roster(helper);
		helper.assertValueEqual(Integer.valueOf(size(old)), Integer.valueOf(expected - PatrolService.RANGED_GUARD_COUNT), "members of the old village");
		if (!PatrolTestSupport.setRosterStarted(helper, test, false) || !PatrolTestSupport.scanChunk(helper, test)) {
			return;
		}

		Map<PatrolState.Role, List<Mob>> grown = roster(helper);
		PatrolTestSupport.result(test, "oldVillageAfterCheck=" + describe(grown) + " rosterStarted=" + PatrolTestSupport.rosterStarted(helper, test));
		helper.assertValueEqual(Integer.valueOf(size(grown)), Integer.valueOf(expected), "members of the old village after its patrol check");
		expect(helper, grown, PatrolState.Role.RANGED_GUARD, PatrolService.RANGED_GUARD_COUNT);
		helper.assertValueEqual(ids(grown.getOrDefault(PatrolState.Role.PILLAGER, List.of())), meleeIds, "the melee guards of the old village");
		helper.assertTrue(PatrolTestSupport.rosterStarted(helper, test), "the roster stayed open after its patrol check");
		checkGuardGear(helper, grown);
		helper.succeed();
	}

	@GameTest(padding = PatrolTestSupport.VILLAGE_PADDING)
	public void rosterSealedBeforeTheRangedGuardsReopensOnLoad(final GameTestHelper helper) {
		VillageWorldData data = VillageWorldData.empty();
		VillageWorldData.VillageRecord village = data.findOrCreate(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 1, 3)));
		for (PatrolState.Role role : List.of(PatrolState.Role.HEAD, PatrolState.Role.PILLAGER, PatrolState.Role.PILLAGER, PatrolState.Role.VILLAGE_WITCH)) {
			data.addMember(village, UUID.randomUUID(), role.name(), helper.absolutePos(new BlockPos(4, 1, 4)));
		}
		data.markRosterStarted(village);
		CompoundTag saved = data.serializeState();
		for (Tag entry : saved.getListOrEmpty("villages")) {
			if (entry instanceof CompoundTag villageTag) {
				villageTag.putInt("roster_version", ROSTER_VERSION_WITHOUT_RANGED_GUARDS);
			}
		}
		VillageWorldData.VillageRecord loaded = VillageWorldData.loadState(saved).village(village.id()).orElseThrow();
		PatrolTestSupport.result("rosterSealedBeforeTheRangedGuardsReopensOnLoad",
			"rosterStartedAfterLoad=" + loaded.rosterStarted() + " members=" + loaded.members().size());
		helper.assertFalse(loaded.rosterStarted(), "a roster sealed before the ranged guards stayed sealed, so they can never be added");
		helper.assertValueEqual(Integer.valueOf(loaded.members().size()), Integer.valueOf(4), "members kept by the migration");
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void savedMeleeGuardGainsItsShieldWhenItLoads(final GameTestHelper helper) {
		String test = "savedMeleeGuardGainsItsShieldWhenItLoads";
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}

		Mob guard = roster(helper).getOrDefault(PatrolState.Role.PILLAGER, List.of()).getFirst();
		// A guard from before the shields: a crossbow, and nothing in the off hand.
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		guard.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		Mob loaded = PatrolTestSupport.reload(helper, guard);
		helper.succeedWhen(() -> {
			helper.assertTrue(loaded.getMainHandItem().is(Items.IRON_SWORD), "the loaded melee guard holds " + loaded.getMainHandItem());
			helper.assertTrue(loaded.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS), "the loaded melee guard holds " + loaded.getOffhandItem() + ", not a shield");
			helper.assertTrue(loaded.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof ShieldUseGoal),
				"the loaded melee guard has no shield goal");
			PatrolTestSupport.result(test, "mainHand=" + loaded.getMainHandItem().getItem() + " offHand=" + loaded.getOffhandItem().getItem());
		});
	}

	private static void checkGuardGear(final GameTestHelper helper, final Map<PatrolState.Role, List<Mob>> roster) {
		for (Mob melee : roster.getOrDefault(PatrolState.Role.PILLAGER, List.of())) {
			helper.assertTrue(melee.getMainHandItem().is(Items.IRON_SWORD), "a melee guard holds " + melee.getMainHandItem());
			helper.assertTrue(melee.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS), "a melee guard holds " + melee.getOffhandItem() + ", not a shield");
		}
		for (Mob ranged : roster.getOrDefault(PatrolState.Role.RANGED_GUARD, List.of())) {
			helper.assertTrue(ranged.getMainHandItem().is(Items.CROSSBOW), "a ranged guard holds " + ranged.getMainHandItem());
			helper.assertTrue(PatrolTestSupport.typeId(ranged).equals("minecraft:pillager"), "a ranged guard is a " + PatrolTestSupport.typeId(ranged));
			helper.assertTrue(GuardQuests.isGuard(ranged), "a ranged guard does not offer the guard quest");
		}
	}

	private static void expect(final GameTestHelper helper, final Map<PatrolState.Role, List<Mob>> roster, final PatrolState.Role role, final int count) {
		helper.assertValueEqual(Integer.valueOf(roster.getOrDefault(role, List.of()).size()), Integer.valueOf(count), role + " count");
	}

	private static Map<PatrolState.Role, List<Mob>> roster(final GameTestHelper helper) {
		return PatrolTestSupport.patrols(helper).stream().collect(Collectors.groupingBy(PatrolState::role));
	}

	private static int size(final Map<PatrolState.Role, List<Mob>> roster) {
		return roster.values().stream().mapToInt(List::size).sum();
	}

	private static Set<UUID> ids(final List<Mob> mobs) {
		return mobs.stream().map(Mob::getUUID).collect(Collectors.toCollection(HashSet::new));
	}

	private static String describe(final Map<PatrolState.Role, List<Mob>> roster) {
		return roster.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().size()).sorted().toList().toString();
	}
}
