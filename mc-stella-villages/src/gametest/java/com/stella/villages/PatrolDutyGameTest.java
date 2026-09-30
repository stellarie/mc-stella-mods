package com.stella.villages;

import com.stella.villages.data.VillageWorldData;
import com.stella.villages.mission.MissionService;
import com.stella.villages.patrol.HeadKit;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * A village's patrol on duty: the head keeps by the bell in enchanted netherite, the rest spread across the
 * village, villagers do not fear them, a player may sleep beside them, golems leave them alone, and they
 * take on any mob that has a player or a villager in its sights. A Nemesis is hostile or an iron golem.
 */
public class PatrolDutyGameTest {
	private static final long WATCH_TICKS = 100L;

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aHeadWearsEnchantedNetheriteThatNeverDrops(final GameTestHelper helper) {
		String test = "aHeadWearsEnchantedNetheriteThatNeverDrops";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		helper.assertTrue(head != null, "the village has no head");
		List<String> wrong = new ArrayList<>();
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND)) {
			ItemStack piece = head.getItemBySlot(slot);
			String id = BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath();
			if (!id.startsWith("netherite_") || !piece.isEnchanted() || head.getDropChances().byEquipment(slot) > 0.0F) {
				wrong.add(slot + "=" + piece + " enchanted=" + piece.isEnchanted() + " drop=" + head.getDropChances().byEquipment(slot));
			}
		}
		PatrolTestSupport.result(test, "wrong=" + wrong);
		helper.assertTrue(wrong.isEmpty(), "the head's kit is off: " + wrong);
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aHeadsAxeCarriesAnOffensiveSpellWhenSpellsIsLoaded(final GameTestHelper helper) {
		String test = "aHeadsAxeCarriesAnOffensiveSpellWhenSpellsIsLoaded";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		helper.assertTrue(head != null, "the village has no head");
		boolean spells = FabricLoader.getInstance().isModLoaded("mod-stella-spells");
		ItemStack axe = head.getItemBySlot(EquipmentSlot.MAINHAND);
		var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(HeadKit.IMBUED_COMPONENT);
		Object imbued = type == null ? null : axe.get(type);
		PatrolTestSupport.result(test, "spellsLoaded=" + spells + " imbued=" + imbued);
		if (spells) {
			helper.assertTrue(imbued != null && HeadKit.OFFENSIVE_SPELLS.stream().anyMatch(spell -> imbued.toString().contains(spell.toString())),
				"with spells loaded the head's axe carries " + imbued);
			helper.assertTrue(HeadKit.OFFENSIVE_SPELLS.stream().allMatch(BuiltInRegistries.ITEM::containsKey), "a listed spell is not a spellbook the spells mod ships");
		} else {
			helper.assertTrue(imbued == null, "without the spells mod the axe carries " + imbued);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void theHeadStartsByTheBellAndTheRestSpreadOut(final GameTestHelper helper) {
		String test = "theHeadStartsByTheBellAndTheRestSpreadOut";
		spreadGround(helper);
		forceScatterChunks(helper);
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		BlockPos bell = PatrolTestSupport.anchor(helper);
		Mob head = PatrolTestSupport.role(helper, test, "HEAD");
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		double headDistance = head.position().distanceTo(Vec3.atBottomCenterOf(bell));
		long spread = patrol.stream().filter(member -> member != head
			&& member.position().distanceTo(Vec3.atBottomCenterOf(bell)) > PatrolService.HEAD_BELL_RADIUS).count();
		PatrolTestSupport.result(test, "headDistance=" + headDistance + " members=" + patrol.size() + " spread=" + spread);
		helper.assertTrue(headDistance <= PatrolService.HEAD_BELL_RADIUS, "the head starts " + headDistance + " blocks from the bell");
		helper.assertTrue(spread >= (patrol.size() - 1) / 2, "only " + spread + " of " + (patrol.size() - 1) + " members start beyond the bell's circle");
		helper.succeed();
	}

	@GameTest(maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void everyMemberCarriesThePatrolTag(final GameTestHelper helper) {
		String test = "everyMemberCarriesThePatrolTag";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		long tagged = patrol.stream().filter(member -> member.entityTags().contains(PatrolService.PATROL_TAG)).count();
		PatrolTestSupport.result(test, "members=" + patrol.size() + " tagged=" + tagged);
		helper.assertTrue(!patrol.isEmpty() && tagged == patrol.size(), tagged + " of " + patrol.size() + " members carry the patrol tag");
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aVillagerDoesNotFearThePatrol(final GameTestHelper helper) {
		String test = "aVillagerDoesNotFearThePatrol";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		patrol.forEach(member -> member.setNoAi(true));
		Villager villager = (Villager) PatrolTestSupport.villager(helper);
		patrol.get(0).snapTo(villager.position().add(3.0, 0.0, 0.0), 0.0F, 0.0F);
		int[] afraid = {0};
		helper.onEachTick(() -> afraid[0] += villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
			.filter(hostile -> hostile instanceof Mob mob && PatrolState.isMarked(mob)).isPresent() ? 1 : 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			PatrolTestSupport.result(test, "afraidTicks=" + afraid[0] + " watched=" + WATCH_TICKS);
			helper.assertTrue(afraid[0] == 0, "the villager counted a patrol member as hostile for " + afraid[0] + " ticks");
			helper.succeed();
		});
	}

	// Its own batch at night: a bed refuses by day, and the level clock is shared with the batch's other tests.
	@GameTest(environment = "mc-stella-villages-test:night", maxTicks = 100, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void aPlayerSleepsBesideThePatrol(final GameTestHelper helper) {
		String test = "aPlayerSleepsBesideThePatrol";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		BlockPos foot = helper.absolutePos(new BlockPos(6, 1, 6));
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		helper.getLevel().setBlockAndUpdate(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		helper.getLevel().setBlockAndUpdate(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD));
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		for (Mob member : patrol) {
			member.setNoAi(true);
			member.snapTo(Vec3.atBottomCenterOf(foot.offset(-3, 0, 0)), 0.0F, 0.0F);
		}
		ServerPlayer player = survivalPlayer(helper);
		player.setPos(foot.getX() + 1.5, foot.getY(), foot.getZ() + 0.5);
		var result = player.startSleepInBed((BedBlock) Blocks.BED.red(), helper.getLevel().getBlockState(foot),
			helper.getLevel().environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE, foot), foot);
		boolean slept = player.isSleeping();
		String problem = result.left().map(Object::toString).orElse("none");
		player.stopSleeping();
		helper.getLevel().getServer().getPlayerList().remove(player);
		PatrolTestSupport.result(test, "slept=" + slept + " problem=" + problem + " members=" + patrol.size());
		helper.assertTrue(slept, "the player could not sleep beside the patrol: " + problem);
		helper.succeed();
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void anIronGolemLeavesThePatrolAlone(final GameTestHelper helper) {
		String test = "anIronGolemLeavesThePatrolAlone";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		List<Mob> patrol = PatrolTestSupport.patrols(helper);
		IronGolem golem = EntityTypes.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.EVENT);
		golem.snapTo(patrol.get(0).position().add(3.0, 0.0, 0.0), 0.0F, 0.0F);
		golem.setPersistenceRequired();
		helper.getLevel().addFreshEntity(golem);
		int[] targeted = {0};
		helper.onEachTick(() -> targeted[0] += golem.getTarget() instanceof Mob mob && PatrolState.isMarked(mob) ? 1 : 0);
		helper.runAfterDelay(WATCH_TICKS, () -> {
			golem.discard();
			PatrolTestSupport.result(test, "targetedTicks=" + targeted[0] + " watched=" + WATCH_TICKS);
			helper.assertTrue(targeted[0] == 0, "the golem targeted a patrol member for " + targeted[0] + " ticks");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = PatrolTestSupport.VILLAGE_PADDING)
	public void thePatrolTakesOnASlimeThatHuntsAVillager(final GameTestHelper helper) {
		String test = "thePatrolTakesOnASlimeThatHuntsAVillager";
		if (!PatrolTestSupport.loadVillage(helper, test, false)) {
			return;
		}
		Mob villager = PatrolTestSupport.villager(helper);
		Slime slime = EntityTypes.SLIME.create(helper.getLevel(), EntitySpawnReason.EVENT);
		slime.setSize(2, true);
		slime.snapTo(villager.position().add(2.0, 0.0, 0.0), 0.0F, 0.0F);
		slime.setNoAi(true);
		// A far-off test player would otherwise despawn a slime that is not held persistent.
		slime.setPersistenceRequired();
		slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
		slime.setHealth(1000.0F);
		helper.getLevel().addFreshEntity(slime);
		helper.onEachTick(() -> slime.setTarget(villager));
		helper.succeedWhen(() -> {
			long defenders = PatrolTestSupport.patrols(helper).stream().filter(member -> member.getTarget() == slime).count();
			helper.assertTrue(defenders > 0, "no member has taken the slime hunting the villager as its target");
			PatrolTestSupport.result(test, "defenders=" + defenders);
			slime.discard();
		});
	}

	@GameTest
	public void aNemesisCanBeHostileOrAnIronGolem(final GameTestHelper helper) {
		List<Identifier> pool = MissionService.defaultOfferPool();
		List<String> notMobs = new ArrayList<>();
		for (Identifier id : pool) {
			Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(helper.getLevel(), EntitySpawnReason.COMMAND);
			if (!(entity instanceof Mob)) {
				notMobs.add(id.toString());
			}
			if (entity != null) {
				entity.discard();
			}
		}
		Set<Identifier> offered = new HashSet<>();
		RandomSource random = RandomSource.create(20_260_927L);
		boolean distinct = true;
		for (int board = 0; board < 2_000; board++) {
			List<Identifier> offers = MissionService.rollOffers(pool, random);
			distinct &= offers.size() == MissionService.OFFERS_PER_BOARD && new HashSet<>(offers).size() == offers.size();
			offered.addAll(offers);
		}
		PatrolTestSupport.result("aNemesisCanBeHostileOrAnIronGolem", "pool=" + pool.size() + " offered=" + offered.size() + " notMobs=" + notMobs);
		helper.assertTrue(pool.contains(Identifier.parse("minecraft:iron_golem")) && pool.contains(Identifier.parse("minecraft:husk"))
			&& pool.contains(Identifier.parse("minecraft:warden")) && pool.contains(Identifier.parse("minecraft:zombie")), "the pool misses a common kind: " + pool);
		helper.assertFalse(pool.contains(Identifier.parse("minecraft:ender_dragon"))
			|| pool.contains(Identifier.parse("minecraft:cow"))
			|| pool.contains(Identifier.parse("minecraft:wandering_trader"))
			|| pool.contains(Identifier.parse("minecraft:creaking")), "a forbidden mob is offered");
		helper.assertTrue(notMobs.isEmpty(), "these offers are not mobs: " + notMobs);
		helper.assertTrue(distinct && offered.size() == pool.size(), "boards offered " + offered.size() + " of " + pool.size() + " kinds, distinct=" + distinct);
		helper.succeed();
	}

	// Members are set down only in entity-ticking chunks, so the chunks of the scatter square are forced first.
	static void forceScatterChunks(final GameTestHelper helper) {
		BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
		int radius = PatrolService.MEMBER_SCATTER_RADIUS;
		for (int x = anchor.getX() - radius; x <= anchor.getX() + radius; x += 16) {
			for (int z = anchor.getZ() - radius; z <= anchor.getZ() + radius; z += 16) {
				helper.getLevel().setChunkForced(x >> 4, z >> 4, true);
			}
		}
	}

	// Stone around the village's own chunk, so members have somewhere to spread to.
	static void spreadGround(final GameTestHelper helper) {
		BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
		for (int x = -PatrolService.MEMBER_SCATTER_RADIUS; x <= PatrolService.MEMBER_SCATTER_RADIUS; x++) {
			for (int z = -PatrolService.MEMBER_SCATTER_RADIUS; z <= PatrolService.MEMBER_SCATTER_RADIUS; z++) {
				BlockPos floor = anchor.offset(x, -1, z);
				helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				for (int y = 0; y <= 3; y++) {
					helper.getLevel().setBlockAndUpdate(floor.above(y + 1), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static ServerPlayer survivalPlayer(final GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
		return player;
	}
}
