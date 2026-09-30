package com.stella.mobs;

import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import com.stella.mobs.scaling.ThreatState;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/**
 * The rpg mod reads these two facts by reflection, so the test calls them the same way.
 */
public class RpgFactsGameTest {
	private static final String ROLES = """
		{
			"version": 1,
			"entities": {},
			"roles": { "minecraft:skeleton": "ranged", "minecraft:evoker": "magic" },
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0 }
		}""";

	@GameTest
	public void factsMatchThreatAndConfiguredRole(final GameTestHelper helper) throws Throwable {
		var previous = GearConfigStore.get();
		GearConfigStore.set(GearConfigParser.parse(ROLES));
		try {
			check(helper);
		} finally {
			GearConfigStore.set(previous);
		}
		helper.succeed();
	}

	private static void check(final GameTestHelper helper) throws Throwable {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		MethodHandle threat = lookup.findStatic(RpgFacts.class, "threat", MethodType.methodType(int.class, Mob.class));
		MethodHandle role = lookup.findStatic(RpgFacts.class, "role", MethodType.methodType(String.class, Mob.class));
		Mob zombie = mob(helper, EntityTypes.ZOMBIE, 7);
		Mob skeleton = mob(helper, EntityTypes.SKELETON, 40);
		Mob evoker = mob(helper, EntityTypes.EVOKER, 200);

		helper.assertValueEqual((int) threat.invoke(zombie), ThreatState.level(zombie), "zombie threat");
		helper.assertValueEqual((int) threat.invoke(skeleton), ThreatState.level(skeleton), "skeleton threat");
		helper.assertValueEqual((int) threat.invoke(evoker), ThreatState.level(evoker), "evoker threat");
		helper.assertTrue(ThreatState.level(evoker) > ThreatState.level(zombie), "the facts do not follow the mob's own level");
		helper.assertValueEqual((String) role.invoke(zombie), "melee", "unconfigured role");
		helper.assertValueEqual((String) role.invoke(skeleton), "ranged", "skeleton role");
		helper.assertValueEqual((String) role.invoke(evoker), "magic", "evoker role");
	}

	private static Mob mob(final GameTestHelper helper, final net.minecraft.world.entity.EntityType<? extends Mob> type, final int area) {
		Mob mob = type.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		mob.addTag(StellaMobs.NO_SPAWN_ROLL_TAG);
		((AttachmentTarget) mob).setAttached(ThreatState.TYPE, new ThreatState(area, 0));
		return mob;
	}
}
