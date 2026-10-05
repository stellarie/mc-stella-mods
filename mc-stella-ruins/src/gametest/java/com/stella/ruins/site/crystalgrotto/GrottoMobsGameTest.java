package com.stella.ruins.site.crystalgrotto;

import com.stella.ruins.world.RuinMobKind;
import com.stella.ruins.world.RuinMobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;

/** The two themed mobs: their kit, the Slowness of a sentinel's hit, and the hop of a Prism Slime. */
public class GrottoMobsGameTest {
	static final long SEED = 0x6D0B5L;

	private static Vec3 spot(final BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	@GameTest(maxTicks = GrottoTestSupport.ARENA_TICKS)
	public void aShardSentinelIsAnAmethystStrayWithABowAndTheSentinelDrops(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			Mob mob = GrottoMobs.SHARD_SENTINEL.spawn(arena.level(), spot(arena.markers(GrottoMarkers.SENTINEL).get(0)));
			mob.setNoAi(true);
			helper.assertTrue(mob != null && mob.getType() == net.minecraft.world.entity.EntityTypes.STRAY, "the sentinel is not a stray");
			helper.assertTrue(mob.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW), "the sentinel holds no bow");
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				helper.assertTrue(mob.getItemBySlot(slot).getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(0)).rgb() == GrottoMobs.SENTINEL_COLOR, "slot " + slot + " is not amethyst leather");
			}
			helper.assertTrue(Math.abs(mob.getAttributeValue(Attributes.SCALE) - 1.1) < 1.0E-6, "scale " + mob.getAttributeValue(Attributes.SCALE));
			helper.assertTrue(RuinMobs.kindOf(mob) == GrottoMobs.SHARD_SENTINEL && GrottoMobs.SHARD_SENTINEL.drops().equals(GrottoMobs.SENTINEL_DROPS), "the kind or its drop table is wrong");
			helper.assertTrue(mob.entityTags().contains(RuinMobKind.TAG_MOB), "the sentinel is not tagged as a ruin mob");
			GrottoTestSupport.result("sentinelKit", "scale=" + mob.getAttributeValue(Attributes.SCALE) + " main=" + mob.getItemBySlot(EquipmentSlot.MAINHAND));
		});
	}

	@GameTest
	public void aSentinelsHitGivesSlownessOneForSixtyTicks(final GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// With the mobs mod a ruin mob hits at its level, which kills a plain cow, and no hook fires for a death.
		Cow cow = GrottoTestSupport.cow(helper, 3.5, 3.5, 1_000_000.0F);
		Mob sentinel = GrottoMobs.SHARD_SENTINEL.spawn(level, helper.absoluteVec(new Vec3(5.5, 2.0, 5.5)));
		sentinel.setNoAi(true);
		helper.assertTrue(cow.getEffect(MobEffects.SLOWNESS) == null, "the cow was slow before the hit");
		if (FabricLoader.getInstance().isModLoaded("mod-stella-mobs")) {
			// A ruin mob hits at its level there and kills any target before a hook can fire, so the hook is called as the event would.
			GrottoMobs.SHARD_SENTINEL.ability().onHurtTarget(sentinel, cow);
		} else {
			cow.hurtServer(level, level.damageSources().mobAttack(sentinel), 1.0F);
		}
		MobEffectInstance slow = cow.getEffect(MobEffects.SLOWNESS);
		GrottoTestSupport.result("sentinelSlow", "effect=" + (slow == null ? "none" : slow.getDuration() + "/" + slow.getAmplifier()));
		helper.assertTrue(slow != null && slow.getAmplifier() == 0 && slow.getDuration() > 0 && slow.getDuration() <= GrottoMobs.SLOW_TICKS, "slowness is " + slow);
		sentinel.discard();
		cow.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = GrottoTestSupport.ARENA_TICKS)
	public void aPrismSlimeGrowsToSizeTwoAndHopsWithinSixBlocksEveryHundredTicks(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			BlockPos marker = arena.markers(com.stella.ruins.world.RuinMarkers.SPAWN).get(0);
			Mob mob = GrottoMobs.PRISM_SLIME.spawn(arena.level(), spot(marker));
			mob.setNoAi(true);
			Slime slime = (Slime) mob;
			helper.assertTrue(RuinMobs.kindOf(mob) == GrottoMobs.PRISM_SLIME && mob.hasEffect(MobEffects.GLOWING), "the slime is not a glowing prism slime");
			Vec3 start = mob.position();
			mob.tickCount = 50;
			GrottoMobs.PRISM_SLIME.ability().tick(mob, arena.level());
			helper.assertTrue(slime.getSize() == GrottoMobs.SLIME_SIZE, "size " + slime.getSize());
			helper.assertTrue(mob.position().equals(start), "the slime hopped outside its window");
			mob.tickCount = GrottoMobs.TELEPORT_TICKS;
			GrottoMobs.PRISM_SLIME.ability().tick(mob, arena.level());
			Vec3 end = mob.position();
			double flat = Math.hypot(end.x - start.x, end.z - start.z);
			GrottoTestSupport.result("slimeHop", "size=" + slime.getSize() + " flat=" + flat + " dy=" + (end.y - start.y));
			helper.assertTrue(!end.equals(start), "the slime did not hop");
			helper.assertTrue(flat <= GrottoMobs.TELEPORT_RANGE + 0.01, "the hop was " + flat + " blocks");
			helper.assertTrue(arena.frame().box().isInside(BlockPos.containing(end)), "the slime left the grotto's box");
			helper.assertTrue(arena.level().noCollision(mob, mob.getBoundingBox()), "the slime landed inside a block");
			mob.discard();
		});
	}

	@GameTest(maxTicks = GrottoTestSupport.ARENA_TICKS)
	public void everyActivationRosterIsThreePrismSlimesAtTheirOwnMarkers(final GameTestHelper helper) {
		GrottoTestSupport.inArena(helper, SEED, arena -> {
			arena.wake();
			arena.freeze();
			List<UUID> roster = arena.entry().roster();
			helper.assertTrue(roster.size() == 3, "the roster holds " + roster.size());
			List<BlockPos> markers = arena.markers(com.stella.ruins.world.RuinMarkers.SPAWN);
			int[] used = new int[markers.size()];
			for (UUID id : roster) {
				Mob mob = (Mob) arena.level().getEntity(id);
				helper.assertTrue(RuinMobs.kindOf(mob) == GrottoMobs.PRISM_SLIME, "a roster mob is no prism slime");
				for (int i = 0; i < markers.size(); i++) {
					if (mob.blockPosition().distManhattan(markers.get(i)) <= 1) {
						used[i]++;
					}
				}
			}
			GrottoTestSupport.result("rosterMarkers", "used=" + java.util.Arrays.toString(used));
			for (int count : used) {
				helper.assertTrue(count == 1, "the slimes did not take one marker each: " + java.util.Arrays.toString(used));
			}
		});
	}
}
