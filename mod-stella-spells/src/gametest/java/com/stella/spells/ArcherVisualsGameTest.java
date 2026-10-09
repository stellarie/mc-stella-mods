package com.stella.spells;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The archer skills look and sound like archer skills: every book plays at least two kinds of particle and a
 * sound through the {@link SpellVisuals} seam, and a trail fed by a quiver arrow takes that kind colour.
 */
public class ArcherVisualsGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.archervisuals");
	private static final double CASTER_X = 1.5;
	private static final double CASTER_Z = 1.5;
	private static final int WEAPON_SLOT = 35;
	private static final int PACK_SLOT = 12;
	/** The books that aim at the ground rather than along the look. */
	private static final Set<String> GROUND_BOOKS = Set.of("arrow_rain", "ankle_snare");
	private static final double GROUND_PITCH = 40.0;
	private static final double COLOR_TOLERANCE = 0.01;

	static {
		SpellVisualLog.install();
	}

	@GameTest(maxTicks = 120)
	public void everyArcherSkillPlaysTwoParticleKindsAndASoundThroughTheSeam(final GameTestHelper helper) {
		ServerPlayer caster = archer(helper, new ItemStack(Items.ARROW, 40));
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.5);
		List<String> report = new ArrayList<>();
		List<String> thin = new ArrayList<>();
		for (String path : new java.util.TreeSet<>(SkillSchoolGameTest.ARCHER_IDS)) {
			SkillSchoolGameTest.clearCooldown(caster);
			ArcherSkills.clearFor(caster);
			caster.removeAllEffects();
			caster.getInventory().setItem(PACK_SLOT, new ItemStack(Items.BONE, 2));
			Vec3 home = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
			caster.setPos(home.x, home.y, home.z);
			caster.setXRot(GROUND_BOOKS.contains(path) ? (float) GROUND_PITCH : 0.0F);
			caster.setYRot(0.0F);
			Vec3 foeAt = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z + 3.5));
			foe.snapTo(foeAt.x, foeAt.y, foeAt.z, 180.0F, 0.0F);
			foe.setHealth(1000.0F);
			ItemStack book = new ItemStack(SkillSchoolGameTest.book(path));
			caster.setItemInHand(InteractionHand.MAIN_HAND, book);
			SpellCasting.CastResult result = SpellCasting.cast(caster, SkillSchoolGameTest.book(path), InteractionHand.MAIN_HAND);
			Identifier spell = SkillSchoolGameTest.id(path);
			Set<ParticleType<?>> kinds = new HashSet<>();
			SpellVisualLog.played(caster.getUUID(), spell).forEach(entry -> kinds.add(entry.particle().getType()));
			int sounds = SpellVisualLog.sounded(caster.getUUID(), spell).size();
			report.add(path + ":" + result + "/" + kinds.size() + "p/" + sounds + "s");
			helper.assertTrue(result == SpellCasting.CastResult.CAST, path + " read " + result);
			if (kinds.size() < 2 || sounds < 1) {
				thin.add(path + " kinds=" + kinds.size() + " sounds=" + sounds);
			}
		}
		LOG.info("[BEHAVIOR] test=everyArcherSkillPlaysTwoParticleKindsAndASoundThroughTheSeam RESULT books={} report={}",
			SkillSchoolGameTest.ARCHER_IDS.size(), report);
		helper.assertTrue(thin.isEmpty(), "these skills played too little: " + thin);
		foe.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void aQuiverFedTrailTakesTheArrowKindColour(final GameTestHelper helper) {
		Map<ArrowKind, Predicate> hues = Map.of(
			ArrowKind.FIRE, (r, g, b) -> r > 0.9F && g > 0.4F && g < 0.7F && b < 0.3F,
			ArrowKind.FROST, (r, g, b) -> r < 0.5F && g > 0.7F && b > 0.9F,
			ArrowKind.VENOM, (r, g, b) -> g > 0.7F && r < 0.5F && b < 0.5F,
			ArrowKind.BLAST, (r, g, b) -> r > 0.8F && g < 0.3F && b < 0.35F,
			ArrowKind.DIAMOND, (r, g, b) -> r > 0.95F && g > 0.95F && b > 0.95F,
			ArrowKind.KEEN, (r, g, b) -> b > 0.9F && r > 0.5F && r < 0.8F && g < 0.4F);
		ServerPlayer caster = archer(helper);
		Mob foe = FighterBooksGameTest.foe(helper, CASTER_X, CASTER_Z + 3.5);
		Identifier spell = SkillSchoolGameTest.id("rapid_fire");
		List<String> report = new ArrayList<>();
		for (Map.Entry<ArrowKind, Predicate> entry : hues.entrySet()) {
			Item arrow = ArrowItems.BY_KIND.get(entry.getKey());
			caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith(new ItemStack(arrow, 2)));
			int before = SpellVisualLog.played(caster.getUUID(), spell).size();
			ArcherSkills.applyDoubleStrafe(caster, foe, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
			List<Vector3f> colours = dustColours(caster, spell, before);
			Vector3f wanted = rgb(ArcherVisuals.colorOf(new ItemStack(arrow)));
			boolean hue = colours.stream().anyMatch(c -> entry.getValue().test(c.x, c.y, c.z));
			boolean exact = colours.stream().anyMatch(c -> near(c, wanted));
			report.add(entry.getKey().path() + ":hue=" + hue + "/shipped=" + exact + "/dust=" + colours.size());
			helper.assertTrue(!colours.isEmpty(), entry.getKey().path() + " drew no coloured trail");
			helper.assertTrue(hue, entry.getKey().path() + " trail colours " + colours + " are not the colour of its kind");
			helper.assertTrue(exact, entry.getKey().path() + " trail never used the shipped colour " + wanted);
			foe.setHealth(1000.0F);
		}
		int before = SpellVisualLog.played(caster.getUUID(), spell).size();
		caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith());
		ArcherSkills.applyDoubleStrafe(caster, foe, caster.position(), SkillSchoolGameTest.modifiers(caster, false));
		List<Vector3f> plain = dustColours(caster, spell, before);
		Vector3f plainWanted = rgb(ArcherVisuals.PLAIN_COLOR);
		LOG.info("[BEHAVIOR] test=aQuiverFedTrailTakesTheArrowKindColour RESULT report={} plain={}", report, plain.stream().distinct().toList());
		helper.assertTrue(!plain.isEmpty() && plain.stream().allMatch(c -> near(c, plainWanted)), "an unfed trail was not the plain colour: " + plain);
		foe.discard();
		helper.succeed();
	}

	private interface Predicate {
		boolean test(float r, float g, float b);
	}

	private static List<Vector3f> dustColours(final ServerPlayer caster, final Identifier spell, final int from) {
		List<SpellVisualLog.Played> played = SpellVisualLog.played(caster.getUUID(), spell);
		List<Vector3f> colours = new ArrayList<>();
		for (int index = from; index < played.size(); index++) {
			if (played.get(index).particle() instanceof DustParticleOptions dust) {
				colours.add(dust.getColor());
			}
		}
		return colours;
	}

	private static Vector3f rgb(final int color) {
		return new Vector3f(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
	}

	private static boolean near(final Vector3f a, final Vector3f b) {
		return Math.abs(a.x - b.x) < COLOR_TOLERANCE && Math.abs(a.y - b.y) < COLOR_TOLERANCE && Math.abs(a.z - b.z) < COLOR_TOLERANCE;
	}

	private static ServerPlayer archer(final GameTestHelper helper, final ItemStack... ammunition) {
		ServerPlayer caster = FighterBooksGameTest.caster(helper);
		SkillSchoolGameTest.setAutoKit(false);
		caster.getInventory().clearContent();
		caster.removeAllEffects();
		caster.getInventory().setItem(WEAPON_SLOT, new ItemStack(Items.BOW));
		caster.setItemInHand(InteractionHand.OFF_HAND, quiverWith(ammunition));
		Vec3 at = helper.absoluteVec(new Vec3(CASTER_X, 1.0, CASTER_Z));
		caster.setPos(at.x, at.y, at.z);
		caster.setYRot(0.0F);
		caster.setXRot(0.0F);
		return caster;
	}

	private static ItemStack quiverWith(final ItemStack... ammunition) {
		ItemStack quiver = new ItemStack(Quiver.ITEM);
		NonNullList<ItemStack> items = NonNullList.withSize(QuiverMenu.SIZE, ItemStack.EMPTY);
		for (int slot = 0; slot < ammunition.length; slot++) {
			items.set(slot, ammunition[slot]);
		}
		Quiver.store(quiver, items);
		return quiver;
	}
}
