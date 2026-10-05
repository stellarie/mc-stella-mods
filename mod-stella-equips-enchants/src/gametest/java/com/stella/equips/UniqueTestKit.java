package com.stella.equips;

import com.mojang.authlib.GameProfile;
import com.stella.equips.refine.Refine;
import com.stella.equips.unique.BonewhisperBow;
import com.stella.equips.unique.UniqueDef;
import com.stella.equips.unique.UniqueDrops;
import com.stella.equips.unique.Uniques;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared test seams for the unique tests. Game tests of one batch run side by side, so the global rollers are
 * installed once and decide by the id of the victim or the holder, never by a flag that one test sets for all.
 */
public final class UniqueTestKit {
	/** One call of the drop roller. */
	public record DropCall(String key, UUID victim, UUID killer, double chance) {
	}

	/** One call of the Bonewhisper roller. */
	public record BoneCall(UUID holder, double chance) {
	}

	private static final Map<UUID, Boolean> DROP_VERDICTS = new ConcurrentHashMap<>();
	private static final List<DropCall> DROP_CALLS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, Boolean> BONE_VERDICTS = new ConcurrentHashMap<>();
	private static final List<BoneCall> BONE_CALLS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, List<AbstractArrow>> BONE_EXTRAS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> BONE_PIERCE = new ConcurrentHashMap<>();

	private static final UniqueDrops.Roller DROP_ROLLER = (key, victim, killer, chance) -> {
		DROP_CALLS.add(new DropCall(key, victim.getUUID(), killer.getUUID(), chance));
		Boolean verdict = DROP_VERDICTS.get(victim.getUUID());
		return verdict != null ? verdict : victim.getRandom().nextDouble() < chance;
	};

	static {
		installDropRoller();
		BonewhisperBow.setRoller((holder, chance) -> {
			BONE_CALLS.add(new BoneCall(holder.getUUID(), chance));
			Boolean verdict = BONE_VERDICTS.get(holder.getUUID());
			return verdict != null ? verdict : holder.getRandom().nextDouble() < chance;
		});
		BonewhisperBow.setRecorder((holder, extra) -> {
			BONE_EXTRAS.computeIfAbsent(holder.getUUID(), key -> new CopyOnWriteArrayList<>()).add(extra);
			BONE_PIERCE.put(extra.getUUID(), (int) extra.getPierceLevel());
		});
	}

	private UniqueTestKit() {
	}

	public static void installDropRoller() {
		UniqueDrops.setRoller(DROP_ROLLER);
	}

	/** Forces the drop roll of one victim to pass or fail. Other victims roll fairly. */
	public static void rigDrop(final UUID victim, final boolean passes) {
		DROP_VERDICTS.put(victim, passes);
	}

	public static List<DropCall> dropCalls(final UUID victim) {
		return DROP_CALLS.stream().filter(call -> call.victim().equals(victim)).toList();
	}

	public static void rigBone(final UUID holder, final boolean passes) {
		BONE_VERDICTS.put(holder, passes);
	}

	public static List<BoneCall> boneCalls(final UUID holder) {
		return BONE_CALLS.stream().filter(call -> call.holder().equals(holder)).toList();
	}

	/** The pierce level an extra arrow had when it was loosed. An arrow that hits a block loses its pierce level later. */
	public static int pierceAtSpawn(final AbstractArrow extra) {
		return BONE_PIERCE.getOrDefault(extra.getUUID(), -1);
	}

	public static List<AbstractArrow> boneExtras(final UUID holder) {
		return List.copyOf(BONE_EXTRAS.getOrDefault(holder, List.of()));
	}

	/** A survival player that is not in the level, placed at a point of the test. Rolls and hooks see a real ServerPlayer. */
	public static ServerPlayer survivor(final GameTestHelper helper, final Vec3 at) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(at);
		player.setPos(origin.x, origin.y, origin.z);
		return player;
	}

	/** A player with a connection, in the level and able to take damage. Only such a player can be hurt or take an effect. */
	public static ServerPlayer connected(final GameTestHelper helper, final Vec3 at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 origin = helper.absoluteVec(at);
		player.setPos(origin.x, origin.y, origin.z);
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().invulnerable = false;
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}

	/** A spectator in the level, to prove that allies skip spectators. The mock overrides both places that read the game mode. */
	public static ServerPlayer spectator(final GameTestHelper helper) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-spectator"), false);
		ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SPECTATOR;
			}

			@Override
			public boolean isSpectator() {
				return true;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}

	public static UniqueDef bonewhisper(final GameTestHelper helper) {
		UniqueDef def = Uniques.byId(BonewhisperBow.ID);
		helper.assertTrue(def != null, "the registry holds no " + BonewhisperBow.ID);
		return def;
	}

	public static ItemStack stack(final UniqueDef def, final int refine) {
		ItemStack stack = new ItemStack(Uniques.item(def));
		if (refine > 0) {
			Refine.setLevel(stack, refine);
		}
		return stack;
	}

	public static ItemStack stack(final Item item, final int refine) {
		ItemStack stack = new ItemStack(item);
		if (refine > 0) {
			Refine.setLevel(stack, refine);
		}
		return stack;
	}

	public static Holder<Enchantment> enchantment(final ServerLevel level, final ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** The arrows of one owner near a point. A box query over the shared level also finds neighbours, so the owner filters. */
	public static List<AbstractArrow> arrowsOf(final ServerLevel level, final ServerPlayer owner, final Vec3 around) {
		return level.getEntitiesOfClass(AbstractArrow.class, new AABB(around, around).inflate(96.0), arrow -> arrow.getOwner() == owner);
	}

	public static void result(final String test, final String details) {
		StellaEquips.LOGGER.info("[BEHAVIOR] test={} RESULT {}", test, details);
	}
}
