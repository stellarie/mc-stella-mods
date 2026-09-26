package com.stella.mobs;

import com.mojang.authlib.GameProfile;
import com.stella.mobs.behavior.IllusionerKit;
import com.stella.mobs.behavior.IllusionerRaidHook;
import com.stella.mobs.behavior.IllusionerRaids;
import com.stella.mobs.config.GearConfigParser;
import com.stella.mobs.config.GearConfigStore;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * M9: every Illusioner freezes, burns and blinds the players around it, splits off decoys whose death
 * slows the killer, and can join a raid wave, so a player meets one in play.
 */
public class IllusionerGameTest {
	private static final int ILLUSIONER_PADDING = 16;
	private static final int REACH_TRIALS = 100_000;
	private static final long REACH_SEED = 20_260_926L;
	private static final double RATE_TOLERANCE = 0.01;
	private static final int PROTECTION_TICKS = 100_000;
	private static final String QUIET = """
		{
			"entities": {},
			"rolls": { "roll_chance": 0.0, "elite_chance": 0.0, "boss_chance": 0.0 }
		}""";
	private static final Map<UUID, Set<UUID>> AURA = new ConcurrentHashMap<>();
	/** Each pulse's element and what it left on the player at that moment, keyed by the player. */
	private static final Map<UUID, List<String>> PULSES = new ConcurrentHashMap<>();
	private static final Map<UUID, List<List<UUID>>> DECOYS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<UUID>> SLOWED = new ConcurrentHashMap<>();

	static {
		IllusionerKit.setRecorder(new IllusionerKit.Recorder() {
			@Override
			public void aura(final Mob illusioner, final Player player, final String element) {
				AURA.computeIfAbsent(illusioner.getUUID(), key -> ConcurrentHashMap.newKeySet()).add(player.getUUID());
				boolean landed = IllusionerKit.FROST.equals(element) ? player.isFullyFrozen() : player.getRemainingFireTicks() > 0;
				PULSES.computeIfAbsent(player.getUUID(), key -> new CopyOnWriteArrayList<>())
					.add(element + (landed && player.hasEffect(MobEffects.BLINDNESS) ? ":landed" : ":missed"));
			}

			@Override
			public void decoys(final Mob caster, final List<UUID> decoys) {
				DECOYS.computeIfAbsent(caster.getUUID(), key -> new CopyOnWriteArrayList<>()).add(List.copyOf(decoys));
			}

			@Override
			public void slowed(final LivingEntity killer, final UUID decoy) {
				SLOWED.computeIfAbsent(killer.getUUID(), key -> new CopyOnWriteArrayList<>()).add(decoy);
			}
		});
	}

	@GameTest(maxTicks = 200, padding = ILLUSIONER_PADDING)
	public void illusionerAuraFreezesBurnsAndBlinds(final GameTestHelper helper) {
		Illusioner illusioner = illusioner(helper);
		ServerPlayer player = survivalPlayer(helper, 3.5, 3.0, 3.5 + IllusionerKit.AURA_RADIUS - 2.0);

		helper.succeedWhen(() -> {
			helper.assertTrue(AURA.getOrDefault(illusioner.getUUID(), Set.of()).contains(player.getUUID()), "the aura has not reached the player");
			List<String> pulses = PULSES.getOrDefault(player.getUUID(), List.of());
			helper.assertTrue(pulses.contains(IllusionerKit.FROST + ":landed"), "no frost pulse froze and blinded the player: " + pulses);
			helper.assertTrue(pulses.contains(IllusionerKit.FIRE + ":landed"), "no fire pulse burned and blinded the player: " + pulses);
			result("illusionerAura", "pulses=" + pulses);
		});
	}

	@GameTest(maxTicks = 160, padding = ILLUSIONER_PADDING)
	public void playerOutsideAuraIsUntouched(final GameTestHelper helper) {
		Illusioner illusioner = illusioner(helper);
		ServerPlayer player = survivalPlayer(helper, 3.5, 3.0, 3.5 + IllusionerKit.AURA_RADIUS + 3.0);
		helper.runAfterDelay(IllusionerKit.AURA_INTERVAL_TICKS * 3L, () -> {
			helper.assertTrue(player.distanceTo(illusioner) > IllusionerKit.AURA_RADIUS, "the player drifted into the aura, so the case proves nothing");
			helper.assertFalse(AURA.getOrDefault(illusioner.getUUID(), Set.of()).contains(player.getUUID()), "the aura reached a player outside its radius");
			helper.assertFalse(player.isFullyFrozen(), "a player outside the aura froze");
			result("illusionerAuraOutside", "distance=" + player.distanceTo(illusioner) + " frozen=" + player.getTicksFrozen());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = ILLUSIONER_PADDING)
	public void decoyDeathSlowsItsKiller(final GameTestHelper helper) {
		Illusioner illusioner = illusioner(helper);
		ServerPlayer player = survivalPlayer(helper, 3.5, 3.0, 7.5);
		illusioner.setTarget(player);

		helper.succeedWhen(() -> {
			List<UUID> decoys = firstDecoys(helper, illusioner);
			Entity decoy = helper.getLevel().getEntity(decoys.get(0));
			if (decoy instanceof Mob mob && mob.isAlive()) {
				mob.setInvulnerableTime(0);
				mob.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100.0F);
			}
			helper.assertTrue(SLOWED.getOrDefault(player.getUUID(), List.of()).contains(decoys.get(0)), "killing a decoy did not slow its killer");
			MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow != null && slow.getAmplifier() == IllusionerKit.KILLER_SLOW_AMPLIFIER, "the killer carries " + slow);
			result("illusionerDecoySlows", "decoys=" + decoys.size() + " slow=" + slow);
		});
	}

	@GameTest(maxTicks = 100, padding = ILLUSIONER_PADDING)
	public void realIllusionerDeathDoesNotSlow(final GameTestHelper helper) {
		Illusioner illusioner = illusioner(helper);
		ServerPlayer player = survivalPlayer(helper, 3.5, 3.0, 3.5 + IllusionerKit.AURA_RADIUS + 3.0);
		illusioner.setInvulnerableTime(0);
		illusioner.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		helper.runAfterDelay(5L, () -> {
			helper.assertFalse(illusioner.isAlive(), "the illusioner survived, so the case proves nothing");
			helper.assertTrue(SLOWED.getOrDefault(player.getUUID(), List.of()).isEmpty() && !player.hasEffect(MobEffects.SLOWNESS), "killing the real illusioner slowed the killer");
			result("illusionerRealNoSlow", "slowed=false");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = ILLUSIONER_PADDING)
	public void decoysVanishWithTheirCaster(final GameTestHelper helper) {
		Illusioner illusioner = illusioner(helper);
		ServerPlayer player = survivalPlayer(helper, 3.5, 3.0, 7.5);
		illusioner.setTarget(player);
		boolean[] killed = {false};

		helper.succeedWhen(() -> {
			List<UUID> decoys = firstDecoys(helper, illusioner);
			if (!killed[0]) {
				killed[0] = true;
				illusioner.discard();
			}
			long alive = decoys.stream().map(id -> helper.getLevel().getEntity(id)).filter(entity -> entity != null && entity.isAlive()).count();
			helper.assertTrue(alive == 0, alive + " decoys outlived their caster");
			result("illusionerDecoysVanish", "decoys=" + decoys.size() + " alive=0");
		});
	}

	@GameTest(maxTicks = 100, padding = ILLUSIONER_PADDING)
	public void anIllusionerJoinsARaidWave(final GameTestHelper helper) {
		prepare(helper);
		BlockPos centre = helper.absolutePos(new BlockPos(3, 1, 3));
		Raid raid = new Raid(centre, Difficulty.NORMAL);
		helper.assertTrue((Object) raid instanceof IllusionerRaidHook, "the raid wave hook is not applied");
		Illusioner joined = IllusionerRaids.join(raid, helper.getLevel(), IllusionerRaids.FIRST_WAVE, centre);
		helper.assertTrue(joined != null && joined.isAlive() && helper.getLevel().getEntity(joined.getUUID()) == joined, "no illusioner joined the wave");
		helper.assertTrue(joined.getCurrentRaid() == raid && joined.getWave() == IllusionerRaids.FIRST_WAVE, "the illusioner is not in the raid's wave");
		result("illusionerRaidJoin", "wave=" + joined.getWave() + " inRaid=true");
		joined.discard();
		helper.succeed();
	}

	/** The rate a player meets an Illusioner: the share of raids that bring at least one, per difficulty. */
	@GameTest(maxTicks = 100)
	public void illusionerReachRateIsMeasured(final GameTestHelper helper) {
		StringBuilder rates = new StringBuilder();
		for (Difficulty difficulty : List.of(Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD)) {
			int waves = new Raid(BlockPos.ZERO, difficulty).getNumGroups(difficulty);
			RandomSource random = RandomSource.create(REACH_SEED);
			int met = 0;
			for (int trial = 0; trial < REACH_TRIALS; trial++) {
				boolean any = false;
				for (int wave = 1; wave <= waves; wave++) {
					any |= IllusionerRaids.shouldJoin(wave, random);
				}
				met += any ? 1 : 0;
			}
			double rate = (double) met / REACH_TRIALS;
			int eligible = Math.max(0, waves - IllusionerRaids.FIRST_WAVE + 1);
			double expected = 1.0 - Math.pow(1.0 - IllusionerRaids.JOIN_CHANCE, eligible);
			rates.append(difficulty.getSerializedName()).append('=').append(rate).append(" (waves ").append(waves).append(") ");
			helper.assertTrue(rate > 0.0, "no " + difficulty.getSerializedName() + " raid ever brought an illusioner");
			helper.assertTrue(Math.abs(rate - expected) <= RATE_TOLERANCE, difficulty.getSerializedName() + " rate " + rate + " is not the spec's " + expected);
		}
		result("illusionerReachRate", "trials=" + REACH_TRIALS + " " + rates.toString().trim());
		helper.succeed();
	}

	private static List<UUID> firstDecoys(final GameTestHelper helper, final Illusioner illusioner) {
		List<List<UUID>> casts = DECOYS.getOrDefault(illusioner.getUUID(), List.of());
		helper.assertTrue(!casts.isEmpty(), "no decoys yet");
		List<UUID> decoys = casts.get(0);
		helper.assertTrue(decoys.size() == IllusionerKit.DECOY_COUNT, "the illusioner split into " + decoys.size() + " decoys");
		return decoys;
	}

	private static Illusioner illusioner(final GameTestHelper helper) {
		prepare(helper);
		Illusioner illusioner = helper.spawnMob(EntityTypes.ILLUSIONER, 3, 3, 3).spawnReason(EntitySpawnReason.NATURAL).spawn();
		illusioner.setPersistenceRequired();
		illusioner.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		return illusioner;
	}

	private static void prepare(final GameTestHelper helper) {
		GearConfigStore.set(GearConfigParser.parse(QUIET));
		helper.setTime(Arena.NIGHT_TIME);
		Arena.buildFloor(helper);
	}

	// The in-level mock player is always creative, which the aura skips, so this one is survival.
	private static ServerPlayer survivalPlayer(final GameTestHelper helper, final double x, final double y, final double z) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "illusioner-player"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		// Resistance V takes every hit to zero; invulnerable abilities would also block freezing and targeting.
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PROTECTION_TICKS, 4));
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		player.teleportTo(at.x, at.y, at.z);
		player.setNoGravity(true);
		return player;
	}

	private static void result(final String test, final String detail) {
		StellaMobs.LOGGER.info("[BEHAVIOR] test=illusionerKit.{} RESULT {}", test, detail);
	}
}
