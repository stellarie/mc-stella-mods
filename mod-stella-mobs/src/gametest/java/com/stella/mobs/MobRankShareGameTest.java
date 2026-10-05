package com.stella.mobs;

import com.stella.mobs.api.MobRankProvider;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.gear.GearTier;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;

public class MobRankShareGameTest {
	@GameTest
	@SuppressWarnings("unchecked")
	public void shareReturnsRank(final GameTestHelper helper) {
		Object shared = FabricLoader.getInstance().getObjectShare().get(MobRankProvider.OBJECT_SHARE_KEY);
		helper.assertTrue(shared instanceof Function<?, ?>, "the rank share is missing: " + shared);
		Function<Entity, String> share = (Function<Entity, String>) shared;
		Mob plain = zombie(helper);
		Mob elite = zombie(helper);
		EliteState.markElite(elite, GearTier.LEATHER);
		Mob boss = zombie(helper);
		EliteState.markElite(boss, GearTier.LEATHER);
		EliteState.setBoss(boss, true);
		Mob summoned = zombie(helper);
		EliteState.markElite(summoned, GearTier.LEATHER);
		EliteState.setBoss(summoned, true);
		EliteState.setSummoned(summoned, true);
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		String detail = "plain=" + share.apply(plain) + " elite=" + share.apply(elite) + " boss=" + share.apply(boss)
			+ " summoned=" + share.apply(summoned) + " stand=" + share.apply(stand);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=shareReturnsRank RESULT {}", detail);
		helper.assertTrue(MobRankProvider.NORMAL.equals(share.apply(plain))
			&& MobRankProvider.ELITE.equals(share.apply(elite))
			&& MobRankProvider.BOSS.equals(share.apply(boss))
			&& MobRankProvider.SUMMONED.equals(share.apply(summoned))
			&& MobRankProvider.NORMAL.equals(share.apply(stand)), detail);
		helper.succeed();
	}

	@GameTest
	public void rankReadCreatesNoState(final GameTestHelper helper) {
		Mob plain = zombie(helper);
		MobRankProvider.rank(plain);
		boolean attached = plain.hasAttached(EliteState.TYPE);
		StellaMobs.LOGGER.info("[BEHAVIOR] test=rankReadCreatesNoState RESULT attached={}", attached);
		helper.assertTrue(!attached, "reading the rank wrote an elite state");
		helper.succeed();
	}

	private static Mob zombie(final GameTestHelper helper) {
		return EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
	}
}
