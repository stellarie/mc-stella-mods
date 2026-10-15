package com.stella.tower;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * How gear and spellbooks grow with the tower. A rune multiplies the flat numbers of the enchantments and
 * spells of its item, kills raise an item's ascension, a rune resonates with the enchantments and spells of
 * its own element, and stored Lightning charges shorten a spell's cooldown. The equips and spells mods reach
 * this by name, so its public methods are their contract: {@link #flat}, {@link #resonance} and {@link #cooldown}.
 */
public final class TowerGrowth {
	public static final int KILLS_PER_LEVEL = 10;
	public static final double RUNE_EXPONENT = 0.7;
	public static final double ASCENSION_EXPONENT = 0.3;
	public static final double RESONANCE_BASE = 0.10;
	public static final double CHARGE_CUT = 0.04;

	/** What an item has counted in the tower: kills, and the ascension they earned. */
	public record Ascension(int kills, int level) {
		public static final com.mojang.serialization.Codec<Ascension> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
			com.mojang.serialization.Codec.INT.fieldOf("kills").forGetter(Ascension::kills),
			com.mojang.serialization.Codec.INT.fieldOf("level").forGetter(Ascension::level)
		).apply(instance, Ascension::new));
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Ascension> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
			net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Ascension::kills,
			net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Ascension::level,
			Ascension::new
		);
	}

	private static final Map<String, Set<TowerElement>> ELEMENTS = new HashMap<>();

	static {
		list(TowerElement.FIRE, "pyre", "ashfall", "ember_guard", "blaze_armor", "bomb_arrows", "meteor", "flame_ring", "ember_brand", "phoenix_mantle", "dragonfall", "spark_shot");
		list(TowerElement.ICE, "chill", "frostbite", "ashfall", "rime_guard", "mist_armor", "ice_lance", "frost_nova", "frostbind");
		list(TowerElement.WATER, "tidecaller", "undertow", "tidal_push", "tide_lungs", "aegis_of_the_deep");
		list(TowerElement.LIGHTNING, "stormcaller", "static_lash", "chainstorm");
		list(TowerElement.WIND, "shockwave", "waveform", "feathered_edge", "gale_step", "tailwind");
		list(TowerElement.VOID, "void_brand", "ender_ward", "riftwalk", "void_well", "gravity", "time_lock");
		list(TowerElement.HOLY, "second_wind", "guarding_ward", "sanctuary", "mending_word", "life_bloom", "purify", "bulwark_hymn", "battle_hymn");
		list(TowerElement.DARK, "venom", "dread_aura", "wounding", "wither_seal", "plague_cloud", "sapping_hex", "soul_siphon", "shadow_veil");
	}

	private static final EquipmentSlot[] COUNTING = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private TowerGrowth() {
	}

	private static void list(final TowerElement element, final String... ids) {
		for (String id : ids) {
			ELEMENTS.computeIfAbsent(id, key -> EnumSet.noneOf(TowerElement.class)).add(element);
		}
	}

	public static void register() {
		net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry.addLast(TowerItems.ASCENDED, ascended -> (context, lines, flag, components) ->
			DescriptionLines.add(lines, Component.translatable("item.mod-stella-tower.ascended", ascended.level(), ascended.kills()).withStyle(ChatFormatting.GOLD)));
		// A tower mob's death is a kill for what its slayer holds and wears.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			TowerMobs.State state = TowerMobs.stateOf(entity);
			if (state != null && !(entity instanceof Ghostly) && entity.getLastHurtByPlayer() instanceof ServerPlayer player) {
				credit(player, state.floor());
			}
		});
	}

	public static Ascension ascension(final ItemStack stack) {
		Ascension ascension = stack.get(TowerItems.ASCENDED);
		return ascension == null ? new Ascension(0, 0) : ascension;
	}

	/**
	 * Counts one kill on the floor on what the player holds and wears. Every {@link #KILLS_PER_LEVEL} kills earn
	 * an item one level of ascension, which rises no higher than the floor the kill happened on.
	 */
	public static void credit(final ServerPlayer player, final int floor) {
		for (EquipmentSlot slot : COUNTING) {
			ItemStack stack = player.getItemBySlot(slot);
			// Gear and spellbooks only: a torch or a stack of arrows does not carry a count.
			if (stack.isEmpty() || stack.getCount() != 1 || TowerRunes.slotOf(stack) == null) {
				continue;
			}
			Ascension now = ascension(stack);
			int kills = now.kills() + 1;
			// Kills bank: a level is 10 kills, but only as deep as this kill's floor lets it rise, and a deeper
			// kill later collects what the shallow ones earned.
			int level = Math.max(now.level(), Math.min(kills / KILLS_PER_LEVEL, floor));
			stack.set(TowerItems.ASCENDED, new Ascension(kills, level));
		}
	}

	private static double along(final int point, final double exponent) {
		return Math.pow(1.0 + (TowerCurve.HEALTH_MAX - 1.0) * TowerCurve.s(point), exponent);
	}

	/** What the item's rune multiplies its flat numbers by: (1 + 99 S(r))^0.7, or 1 with no rune. */
	public static double runePart(final ItemStack stack) {
		int floor = TowerRunes.runesOf(stack).stream().mapToInt(RuneData::floor).max().orElse(-1);
		return floor < 0 ? 1.0 : along(floor, RUNE_EXPONENT);
	}

	/** What the item's ascension multiplies its flat numbers by: (1 + 99 S(a))^0.3. */
	public static double ascensionPart(final ItemStack stack) {
		return along(ascension(stack).level(), ASCENSION_EXPONENT);
	}

	/** What an item's flat numbers (aura damage, spell damage and healing) are multiplied by. */
	public static double flat(final ItemStack stack) {
		return stack.isEmpty() ? 1.0 : runePart(stack) * ascensionPart(stack);
	}

	/** The elements an enchantment or spell, by its bare id, resonates with. */
	public static Set<TowerElement> elementsOf(final String id) {
		return ELEMENTS.getOrDefault(id, Set.of());
	}

	/** 1 plus 10% + P/10 % when the item's rune is of an element the enchantment or spell belongs to. */
	public static double resonance(final ItemStack stack, final String id) {
		RuneData best = null;
		for (RuneData rune : TowerRunes.runesOf(stack)) {
			if (elementsOf(id).contains(rune.element()) && (best == null || rune.floor() > best.floor())) {
				best = rune;
			}
		}
		if (best == null) {
			return 1.0;
		}
		return 1.0 + RESONANCE_BASE + TowerRunes.power(stack, best) / 1000.0;
	}

	/** A spell's cooldown once the player's stored Lightning charges have shortened it, which spends them. */
	public static int cooldown(final Player player, final int ticks) {
		int charges = RuneEffects.spendCharges(player);
		if (charges <= 0) {
			return ticks;
		}
		return (int) Math.round(ticks * (1.0 - Math.min(RuneEffects.MAX_CHARGES, charges) * CHARGE_CUT));
	}
}
