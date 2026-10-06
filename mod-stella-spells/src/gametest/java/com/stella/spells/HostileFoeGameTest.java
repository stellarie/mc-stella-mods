package com.stella.spells;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mob another mod marks hostile is a spell's foe even though it is not an Enemy: the star golem of the ruins.
 * A friend tag still wins, and nothing here compiles against the mod that writes the tag.
 */
public class HostileFoeGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.hostilefoe");
	private static final double FLOOR = 6.0;

	@GameTest
	public void anUntaggedGolemIsNotAFoe(final GameTestHelper helper) {
		arena(helper);
		IronGolem golem = golem(helper);
		LOG.info("[SPELLTEST] test=anUntaggedGolemIsNotAFoe RESULT tags={} foe={} friend={}", golem.entityTags(), Friends.isFoe(golem), Friends.isFriend(golem));
		helper.assertTrue(!Friends.isFoe(golem), "an untagged iron golem is already a foe, so the tag is not what makes one");
		helper.succeed();
	}

	@GameTest
	public void aTaggedGolemIsAFoe(final GameTestHelper helper) {
		arena(helper);
		IronGolem golem = golem(helper);
		Friends.FOE_TAGS.forEach(golem::addTag);
		LOG.info("[SPELLTEST] test=aTaggedGolemIsAFoe RESULT tags={} foe={} friend={} hostile={}",
			golem.entityTags(), Friends.isFoe(golem), Friends.isFriend(golem), MoreSpells.hostiles(helper.getLevel(), golem.position(), 6.0).size());
		helper.assertFalse(Friends.FOE_TAGS.isEmpty(), "the foe tag set is empty, so no mob can be a foe by tag");
		helper.assertTrue(Friends.isFoe(golem), "a golem tagged " + Friends.FOE_TAGS + " is not a foe");
		helper.assertTrue(!Friends.isFriend(golem), "a golem tagged " + Friends.FOE_TAGS + " counts as a friend");
		helper.assertTrue(MoreSpells.hostiles(helper.getLevel(), golem.position(), 6.0).contains(golem),
			"the area spell list left out a tagged foe, so a healing spell still treats it as a heal target");
		helper.succeed();
	}

	@GameTest
	public void aFriendTagBeatsTheFoeTag(final GameTestHelper helper) {
		arena(helper);
		IronGolem golem = golem(helper);
		Friends.FOE_TAGS.forEach(golem::addTag);
		golem.addTag(SummonSkeleton.SUMMON_TAG);
		LOG.info("[SPELLTEST] test=aFriendTagBeatsTheFoeTag RESULT tags={} foe={} friend={}", golem.entityTags(), Friends.isFoe(golem), Friends.isFriend(golem));
		helper.assertTrue(Friends.isFriend(golem), "the friend tag " + SummonSkeleton.SUMMON_TAG + " did not register as a friend");
		helper.assertTrue(!Friends.isFoe(golem), "a friend of the village became a foe");
		helper.succeed();
	}

	@GameTest
	public void everyFoeTagMakesAFoeAlone(final GameTestHelper helper) {
		arena(helper);
		List<String> tags = List.copyOf(Friends.FOE_TAGS);
		helper.assertFalse(tags.isEmpty(), "the foe tag set is empty, so no mob can be a foe by tag");
		for (String tag : tags) {
			IronGolem golem = golem(helper);
			golem.addTag(tag);
			helper.assertTrue(Friends.isFoe(golem), "the foe tag " + tag + " did not make a foe");
		}
		LOG.info("[SPELLTEST] test=everyFoeTagMakesAFoeAlone RESULT tags={} foes={}", tags, tags.size());
		helper.succeed();
	}

	private static IronGolem golem(final GameTestHelper helper) {
		return helper.spawnMob(EntityTypes.IRON_GOLEM, 3, 1, 3).withNoFreeWill().spawn();
	}

	private static void arena(final GameTestHelper helper) {
		helper.setTime(18_000L);
		for (int x = 0; x <= FLOOR; x++) {
			for (int z = 0; z <= FLOOR; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}
}
