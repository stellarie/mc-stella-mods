package com.stella.end;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;

/** The mod stands alone: it needs only Fabric API, and each bridge to a sibling does nothing when the sibling is gone. */
public class AbsentSiblingGameTest {
	/** Package prefixes of every other mod in the pack, as they appear inside compiled classes. */
	static final List<String> SIBLING_PACKAGES = List.of(
		"com/stella/mobs/", "com/stella/equips/", "com/stella/spells/", "com/stella/rpg/", "com/stella/nether/",
		"com/stella/tower/", "com/stella/learn/", "com/stella/villages/", "com/stella/food/", "com/stella/ruins/"
	);

	@GameTest(environment = "mod-stella-end-test:absent_depends", maxTicks = 20)
	public void theModDeclaresOnlyFabricAndMinecraftAsRequiredMods(final GameTestHelper helper) {
		ModContainer container = FabricLoader.getInstance().getModContainer(StellaEnd.MOD_ID).orElseThrow();
		Set<String> required = container.getMetadata().getDependencies().stream()
			.filter(dependency -> dependency.getKind() == ModDependency.Kind.DEPENDS)
			.map(ModDependency::getModId)
			.collect(Collectors.toSet());
		EndTestSupport.result("absentDepends", "required=" + required);
		helper.assertTrue(Set.of("fabricloader", "minecraft", "java", "fabric-api").containsAll(required), "required mods: " + required);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:absent_classes", maxTicks = 20)
	public void noClassOfTheModNamesAClassOfASiblingMod(final GameTestHelper helper) throws IOException {
		ModContainer container = FabricLoader.getInstance().getModContainer(StellaEnd.MOD_ID).orElseThrow();
		StringBuilder bad = new StringBuilder();
		int classes = 0;
		for (Path root : container.getRootPaths()) {
			try (Stream<Path> files = Files.walk(root)) {
				for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
					classes++;
					String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
					for (String sibling : SIBLING_PACKAGES) {
						if (text.contains(sibling)) {
							bad.append(' ').append(file.getFileName()).append(" names ").append(sibling);
						}
					}
				}
			}
		}
		EndTestSupport.result("absentClasses", "classes=" + classes + " bad=" + bad.length());
		helper.assertTrue(classes > 10, "only " + classes + " classes were scanned");
		helper.assertTrue(bad.isEmpty(), "sibling classes in the bytecode:" + bad);
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:absent_level", maxTicks = 100)
	public void theLevelBridgeDoesNothingWhenTheMobsModIsAbsentAndCastleMobsStillSpawn(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		var share = FabricLoader.getInstance().getObjectShare();
		Object apply = share.remove(LevelBridge.APPLY_KEY);
		Object read = share.remove(LevelBridge.READ_KEY);
		try {
			WitherSkeleton plain = EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.SPAWNER);
			boolean answered = LevelBridge.apply(plain, 130);
			int level = LevelBridge.read(plain);
			int[] cell = EndCastles.layout(end).floor(3).spawnCells().get(0);
			// A claimed skeleton nobody levelled: other mods may scale every mob, so this is the yardstick, not the vanilla base.
			WitherSkeleton baseline = EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.SPAWNER);
			LevelBridge.claim(baseline);
			baseline.addTag(EndMobs.FLOOR_TAG + "0");
			BlockPos at = EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(3), cell[1]);
			baseline.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
			end.addFreshEntity(baseline);
			Mob spawned = EndMobs.spawn(end, 3, EndMobs.Kind.WITHER_SKELETON, EndTestSupport.world(EndCastle.min(), cell[0], EndCastle.standY(3), cell[1]));
			EndTestSupport.result("absentLevel", "answered=" + answered + " read=" + level + " spawned=" + (spawned != null)
				+ " health=" + (spawned == null ? -1 : spawned.getMaxHealth()) + " baseline=" + baseline.getMaxHealth());
			helper.assertTrue(!answered, "the level bridge claims an answer from an absent mod");
			helper.assertTrue(level == -1, "the level bridge read " + level + " from an absent mod");
			helper.assertTrue(spawned != null, "a castle mob could not spawn without the mobs mod");
			helper.assertTrue(Math.abs(spawned.getMaxHealth() - baseline.getMaxHealth()) < 1.0E-6,
				"the mob has " + spawned.getMaxHealth() + " health, not the unlevelled " + baseline.getMaxHealth());
		} finally {
			if (apply != null) {
				share.put(LevelBridge.APPLY_KEY, apply);
			}
			if (read != null) {
				share.put(LevelBridge.READ_KEY, read);
			}
			EndTestSupport.fresh(helper);
		}
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:absent_xp", maxTicks = 20)
	public void theXpBridgeDoesNothingWhenTheRpgModIsAbsent(final GameTestHelper helper) {
		var share = FabricLoader.getInstance().getObjectShare();
		Object curve = share.remove(XpBridge.CURVE_KEY);
		Object scale = share.remove(HealthBridge.SCALE_KEY);
		boolean empty;
		double flat;
		try {
			empty = XpBridge.needFor(120).isEmpty();
			flat = HealthBridge.scale();
		} finally {
			if (curve != null) {
				share.put(XpBridge.CURVE_KEY, curve);
			}
			if (scale != null) {
				share.put(HealthBridge.SCALE_KEY, scale);
			}
		}
		EndTestSupport.result("absentXp", "empty=" + empty + " healthScale=" + flat);
		helper.assertTrue(empty, "the xp bridge answered without the rpg mod");
		helper.assertTrue(flat == 1.0, "the health scale bridge answered " + flat + " without the rpg mod");
		helper.succeed();
	}

	@GameTest(environment = "mod-stella-end-test:present_bridges", maxTicks = 100)
	public void withTheSiblingsPresentTheLevelBridgeLevelsAClaimedMobAndTheXpBridgeReadsTheCurve(final GameTestHelper helper) {
		ServerLevel end = EndTestSupport.fresh(helper);
		WitherSkeleton plain = EntityTypes.WITHER_SKELETON.create(end, EntitySpawnReason.SPAWNER);
		double vanilla = plain.getMaxHealth();
		LevelBridge.claim(plain);
		boolean answered = LevelBridge.apply(plain, 130);
		int level = LevelBridge.read(plain);
		Object curve = FabricLoader.getInstance().getObjectShare().get(XpBridge.CURVE_KEY);
		helper.assertTrue(curve instanceof IntUnaryOperator, "the rpg mod publishes no curve: " + curve);
		IntUnaryOperator published = (IntUnaryOperator) curve;
		boolean same = XpBridge.needFor(1).orElse(-1) == published.applyAsInt(1) && XpBridge.needFor(120).orElse(-1) == published.applyAsInt(120);
		EndTestSupport.result("presentBridges", "answered=" + answered + " level=" + level + " health=" + plain.getMaxHealth() + " vanilla=" + vanilla
			+ " need120=" + XpBridge.needFor(120));
		helper.assertTrue(answered, "the level bridge found no mobs mod");
		helper.assertTrue(level == 130, "the claimed mob reads level " + level);
		helper.assertTrue(plain.getMaxHealth() > vanilla, "the level added no health: " + plain.getMaxHealth());
		helper.assertTrue(same, "the xp bridge disagrees with the curve it reads");
		helper.assertTrue(HealthBridge.scale() > 1.0, "the health scale bridge reads " + HealthBridge.scale() + " with the rpg mod present");
		helper.succeed();
	}
}
