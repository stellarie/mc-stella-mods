package com.stella.rpg;

import com.mojang.serialization.Codec;
import com.stella.rpg.job.Job;
import com.stella.rpg.job.Jobs;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The optional bridge to the spells mod's archer starter. No harness in this pack loads both mods, so the absent
 * path is the one that runs here and the present path is a manual item.
 */
public final class ArcherGrantGameTest {
	private static final String SPELLS_MOD = "mod-stella-spells";
	/** The item namespace the sibling's books live in. */
	private static final String SPELLS_NAMESPACE = "mod-stella-spells";
	/** The path of the archer's job item, which the kit hands over whatever else happens. */
	private static final String BOW = "mechanical_bow";

	@GameTest
	public void anArcherWithoutTheSiblingGetsTheKitAlone(final GameTestHelper helper) {
		boolean loaded = FabricLoader.getInstance().isModLoaded(SPELLS_MOD);
		helper.assertTrue(!loaded, "the spells mod is loaded here, so this is not the absent path");
		ServerPlayer archer = RpgTestSupport.player(helper);
		archer.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(archer, Job.ARCHER), "the archer job was refused");
		boolean kit;
		SpellsBridge.setGrant(null);
		try {
			kit = Jobs.grantItems(archer);
		} finally {
			SpellsBridge.setGrant(null);
		}
		boolean flagged = archer.getAttachedOrElse(SpellsBridge.ARCHER_STARTER_GIVEN, false);
		RpgTestSupport.result("anArcherWithoutTheSiblingGetsTheKitAlone", "kit=" + kit + " bow=" + held(archer, BOW)
			+ " books=" + books(archer) + " flagged=" + flagged + " spellsLoaded=" + loaded);
		helper.assertTrue(kit, "the archer's kit did not land");
		helper.assertTrue(held(archer, BOW) >= 1, "the archer holds " + held(archer, BOW) + " mechanical bows");
		helper.assertTrue(books(archer) == 0, "a book appeared with no sibling: " + books(archer));
		// Nothing was answered, so nothing was recorded.
		helper.assertTrue(!flagged, "the starter was marked as given with no sibling");
		helper.succeed();
	}

	@GameTest
	public void aSecondCallGivesNoSecondSet(final GameTestHelper helper) {
		ServerPlayer archer = RpgTestSupport.player(helper);
		archer.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(archer, Job.ARCHER), "the archer job was refused");
		AtomicBoolean asked = new AtomicBoolean(false);
		boolean first;
		boolean again;
		boolean flagged;
		// A willing sibling, so the marker refuses.
		SpellsBridge.setGrant((player, random, onGranted) -> {
			asked.set(true);
			return 1;
		});
		try {
			first = SpellsBridge.grantArcherStarter(archer);
			flagged = archer.getAttachedOrElse(SpellsBridge.ARCHER_STARTER_GIVEN, false);
			again = SpellsBridge.grantArcherStarter(archer);
		} finally {
			SpellsBridge.setGrant(null);
		}
		RpgTestSupport.result("aSecondCallGivesNoSecondSet", "first=" + first + " flagged=" + flagged + " again=" + again
			+ " offered=" + asked.get());
		helper.assertTrue(first, "the first grant was refused");
		helper.assertTrue(flagged, "a gift was not recorded");
		helper.assertTrue(!again, "a second call handed over a second set");
		helper.succeed();
	}

	@GameTest
	public void aNonArcherNeverReceivesTheStarter(final GameTestHelper helper) {
		ServerPlayer fighter = RpgTestSupport.player(helper);
		ServerPlayer mage = RpgTestSupport.player(helper);
		fighter.experienceLevel = Jobs.MIN_LEVEL;
		mage.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(fighter, Job.FIGHTER), "the fighter job was refused");
		helper.assertTrue(Jobs.choose(mage, Job.MAGE), "the mage job was refused");
		AtomicBoolean asked = new AtomicBoolean(false);
		boolean fighterKit;
		boolean mageKit;
		// A willing sibling, so only the guard refuses.
		SpellsBridge.setGrant((player, random, onGranted) -> {
			asked.set(true);
			return 1;
		});
		try {
			fighterKit = Jobs.grantItems(fighter);
			mageKit = Jobs.grantItems(mage);
		} finally {
			SpellsBridge.setGrant(null);
		}
		RpgTestSupport.result("aNonArcherNeverReceivesTheStarter", "fighterKit=" + fighterKit + " mageKit=" + mageKit
			+ " offered=" + asked.get() + " fighterBooks=" + books(fighter) + " mageBooks=" + books(mage));
		helper.assertTrue(fighterKit && mageKit, "a job kit did not land: " + fighterKit + "/" + mageKit);
		// Another job is never even asked.
		helper.assertTrue(!asked.get(), "the starter was offered to a non-archer");
		helper.assertTrue(books(fighter) == 0 && books(mage) == 0, "a non-archer holds "
			+ books(fighter) + "/" + books(mage) + " books");
		helper.succeed();
	}

	@GameTest
	public void theStarterFlagSurvivesASaveAndLoad(final GameTestHelper helper) {
		ServerPlayer archer = RpgTestSupport.player(helper);
		archer.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(archer, Job.ARCHER), "the archer job was refused");
		archer.setAttached(SpellsBridge.ARCHER_STARTER_GIVEN, true);
		Codec<Boolean> codec = SpellsBridge.ARCHER_STARTER_GIVEN.persistenceCodec();
		Tag saved = codec.encodeStart(NbtOps.INSTANCE, true).getOrThrow();
		ServerPlayer other = RpgTestSupport.player(helper);
		boolean before = other.getAttachedOrElse(SpellsBridge.ARCHER_STARTER_GIVEN, false);
		Boolean decoded = codec.parse(NbtOps.INSTANCE, saved).getOrThrow();
		other.setAttached(SpellsBridge.ARCHER_STARTER_GIVEN, decoded);
		other.experienceLevel = Jobs.MIN_LEVEL;
		helper.assertTrue(Jobs.choose(other, Job.ARCHER), "the reloaded player could not take the archer job");
		boolean after = other.getAttachedOrElse(SpellsBridge.ARCHER_STARTER_GIVEN, false);
		AtomicBoolean asked = new AtomicBoolean(false);
		boolean handed;
		SpellsBridge.setGrant((player, random, onGranted) -> {
			asked.set(true);
			return 1;
		});
		try {
			handed = Jobs.grantItems(other);
		} finally {
			SpellsBridge.setGrant(null);
		}
		RpgTestSupport.result("theStarterFlagSurvivesASaveAndLoad", "codec=" + codec + " tag=" + saved + " before=" + before
			+ " decoded=" + decoded + " after=" + after + " offeredAgain=" + asked.get());
		helper.assertTrue(codec != null, "the starter flag has no persistent codec");
		helper.assertTrue(!before && after, "the flag did not survive: " + before + " then " + after);
		// A surviving marker stops a second set.
		helper.assertTrue(!asked.get(), "the starter was offered again after the flag came back");
		helper.assertTrue(handed, "the kit did not land on the reloaded archer");
		helper.succeed();
	}

	/** How many of the mod's own item the player holds, found by path. */
	private static int held(final ServerPlayer player, final String path) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(path)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Every spellbook the sibling's mod ships, counted by namespace so the exact book does not matter. */
	private static int books(final ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && SPELLS_NAMESPACE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * The pack has no harness that loads both mods, so the present-sibling grant cannot run here. The absent path
	 * above is what this harness covers, and this case holds the manual item for the two-mod pack.
	 */
	@GameTest(required = false)
	public void theGrantWithTheSiblingPresentNeedsATwoModRunRequiresManualTest(final GameTestHelper helper) {
		helper.assertTrue(false, "by hand: in a pack with mod-stella-spells, choose ARCHER at the trainer and confirm"
			+ " arrow rain plus one other archer book land on top of the mechanical bow, once only");
	}
}
