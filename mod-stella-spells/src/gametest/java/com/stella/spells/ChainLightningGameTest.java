package com.stella.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Chainstorm jumps from mob to mob: each next strike is the unstruck target nearest the last, joined by an arc. */
public class ChainLightningGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("mod-stella-spells-test.chain");
	private record Arc(UUID from, UUID to, int particles) {
	}

	private static final Map<UUID, List<UUID>> STRUCK = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Arc>> ARCS = new ConcurrentHashMap<>();

	static {
		SpellStorms.addRecorder(new SpellStorms.Recorder() {
			@Override
			public void struck(final LivingEntity caster, final UUID target, final int serverTick) {
				STRUCK.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(target);
			}

			@Override
			public void arced(final LivingEntity caster, final UUID from, final UUID to, final int particles, final int serverTick) {
				ARCS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(new Arc(from, to, particles));
			}
		});
	}

	// Offsets from the cast spot along one line: the distance order would zigzag, the chain runs +1, +3, -2, -4.
	@GameTest(maxTicks = 120)
	public void chainOrderAndArcs(final GameTestHelper helper) {
		SpellTestLog.track(helper, "chainOrderAndArcs");
		helper.setTime(18_000L);
		for (int z = 0; z <= 14; z++) {
			helper.setBlock(2, 0, z, Blocks.STONE);
		}
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 1.0, 1.5));
		caster.setPos(at.x, at.y, at.z);
		Mob nearest = zombie(helper, 9);
		Mob second = zombie(helper, 6);
		Mob third = zombie(helper, 11);
		Mob fourth = zombie(helper, 4);
		List<Mob> chain = List.of(nearest, third, second, fourth);
		Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1.0, 8.5));
		Findable.then(helper, List.of(nearest, second, third, fourth), () -> {
			helper.assertTrue(SpellStorms.start(caster, spot, SpellModifiers.resolve(net.minecraft.world.item.ItemStack.EMPTY,
				net.minecraft.world.item.ItemStack.EMPTY, net.minecraft.world.item.ItemStack.EMPTY, 20)), "Chainstorm did not start");
		});
		helper.runAfterDelay(4L * SpellStorms.STRIKE_INTERVAL_TICKS + 10L, () -> {
			List<UUID> struck = STRUCK.getOrDefault(caster.getUUID(), List.of());
			List<UUID> want = chain.stream().map(Mob::getUUID).toList();
			List<Arc> arcs = ARCS.getOrDefault(caster.getUUID(), List.of());
			LOG.info("[SPELLTEST] class=ChainLightningGameTest test=chainOrderAndArcs RESULT struck={} arcs={}", struck, arcs);
			helper.assertTrue(struck.equals(want), "strike order " + struck + " is not the chain " + want);
			helper.assertTrue(arcs.size() == 3, "arcs recorded: " + arcs.size());
			for (int hop = 0; hop < arcs.size(); hop++) {
				helper.assertTrue(arcs.get(hop).from().equals(want.get(hop)) && arcs.get(hop).to().equals(want.get(hop + 1)),
					"arc " + hop + " does not join the previous target to the next");
				helper.assertTrue(arcs.get(hop).particles() > 0, "arc " + hop + " sent no particles");
			}
			helper.getLevel().getServer().getPlayerList().remove(caster);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void chainOrderBreaksTiesOnLowerId(final GameTestHelper helper) {
		helper.setTime(18_000L);
		Mob a = zombie(helper, 5);
		Mob b = zombie(helper, 5);
		Vec3 point = helper.absoluteVec(new Vec3(2.5, 1.0, 5.5));
		List<LivingEntity> ordered = SpellStorms.chainOrder(new ArrayList<>(List.of(b, a)), point);
		LivingEntity lower = a.getId() < b.getId() ? a : b;
		helper.assertTrue(ordered.get(0) == lower, "a tie did not go to the lower entity id");
		helper.succeed();
	}

	private static Mob zombie(final GameTestHelper helper, final int z) {
		Mob mob = helper.spawnMob(EntityTypes.ZOMBIE, 2, 1, z).withNoFreeWill().spawn();
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		mob.setHealth(1000.0F);
		mob.setNoGravity(true);
		return mob;
	}
}
