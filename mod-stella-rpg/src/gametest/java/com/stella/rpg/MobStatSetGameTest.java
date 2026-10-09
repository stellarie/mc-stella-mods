package com.stella.rpg;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Another mod fixes chosen stats of one mob through the object share {@link MobStatOverride#SHARE_KEY}. A listed stat
 * reads the given value, an unlisted stat keeps the role formula, growth still adds, and the values are saved.
 */
public final class MobStatSetGameTest {
	@GameTest
	public void shareSetsListedStatsOnly(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Zombie control = zombie(helper);
		set(zombie, Map.of("vit", 200, "str", 60));
		int vit = StatBonuses.effective(zombie, Stat.VIT);
		int str = MobStats.base(zombie, Stat.STR);
		int agi = MobStats.base(zombie, Stat.AGI);
		int controlAgi = MobStats.base(control, Stat.AGI);
		RpgTestSupport.result("mobStatSet", "vit=" + vit + " str=" + str + " agi=" + agi + " controlAgi=" + controlAgi);
		helper.assertTrue(vit == 200 && str == 60, "listed stats read VIT " + vit + " STR " + str);
		helper.assertTrue(agi == controlAgi, "an unlisted stat changed from " + controlAgi + " to " + agi);
		helper.succeed();
	}

	@GameTest
	public void valuesClampToTheStatCeiling(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		set(zombie, Map.of("vit", 5000, "dex", -7));
		int vit = MobStats.base(zombie, Stat.VIT);
		int dex = MobStats.base(zombie, Stat.DEX);
		RpgTestSupport.result("mobStatSetClamp", "vit=" + vit + " dex=" + dex);
		helper.assertTrue(vit == StatSheet.CEILING, "5000 read as " + vit);
		helper.assertTrue(dex == MobStats.base(zombie(helper), Stat.DEX), "a negative value was applied: " + dex);
		helper.succeed();
	}

	@GameTest
	public void badInputIsSkipped(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		Zombie control = zombie(helper);
		Map<Object, Object> junk = new HashMap<>();
		junk.put("nope", 10);
		junk.put("luk", "many");
		junk.put(3, 5);
		junk.put("int", null);
		set(zombie, junk);
		set(zombie, null);
		boolean untouched = true;
		for (Stat stat : Stat.values()) {
			untouched &= MobStats.base(zombie, stat) == MobStats.base(control, stat);
		}
		RpgTestSupport.result("mobStatSetJunk", "untouched=" + untouched);
		helper.assertTrue(untouched, "junk input changed a stat");
		helper.succeed();
	}

	@GameTest
	public void growthStillAdds(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		set(zombie, Map.of("vit", 200));
		MobGrowth.grow(zombie, Stat.VIT, 3.0);
		int vit = MobStats.base(zombie, Stat.VIT);
		RpgTestSupport.result("mobStatSetGrowth", "vit=" + vit);
		helper.assertTrue(vit == 203, "200 plus 3 growth read " + vit);
		helper.succeed();
	}

	@GameTest
	public void aSecondCallReplacesTheFirst(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		set(zombie, Map.of("vit", 200, "str", 60));
		set(zombie, Map.of("str", 70));
		int str = MobStats.base(zombie, Stat.STR);
		int vit = MobStats.base(zombie, Stat.VIT);
		RpgTestSupport.result("mobStatSetReplace", "str=" + str + " vit=" + vit);
		helper.assertTrue(str == 70, "STR read " + str);
		helper.assertTrue(vit == MobStats.base(zombie(helper), Stat.VIT), "the old VIT stayed: " + vit);
		helper.succeed();
	}

	@GameTest
	public void valuesAreSaved(final GameTestHelper helper) {
		Zombie zombie = zombie(helper);
		set(zombie, Map.of("vit", 200, "luk", 77));
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		zombie.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		Zombie loaded = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.LOAD);
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
		int vit = MobStats.base(loaded, Stat.VIT);
		int luk = MobStats.base(loaded, Stat.LUK);
		RpgTestSupport.result("mobStatSetSaved", "vit=" + vit + " luk=" + luk);
		helper.assertTrue(vit == 200 && luk == 77, "a saved VIT 200 LUK 77 loaded as " + vit + " " + luk);
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	private static void set(final Mob mob, final Map<?, ?> values) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(MobStatOverride.SHARE_KEY);
		if (!(shared instanceof BiConsumer)) {
			throw new AssertionError("no setter at " + MobStatOverride.SHARE_KEY + ": " + shared);
		}
		((BiConsumer<Mob, Map<?, ?>>) shared).accept(mob, values);
	}

	private static Zombie zombie(final GameTestHelper helper) {
		return helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(1.5, 2.0, 1.5));
	}
}
