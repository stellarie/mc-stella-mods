package com.stella.spells;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * A spellbook counts its casts that hit something and awakens at {@link #CASTS_TO_AWAKEN}. The awakened state is
 * a flag on the stack; every other component stays as it was.
 */
public final class Awakening {
	public static final int CASTS_TO_AWAKEN = 120;
	public static final DataComponentType<Integer> COUNT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "awakening"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build()
	);
	public static final DataComponentType<Unit> AWAKENED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "awakened"),
		DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build()
	);
	/** The one spell on a weapon that casts awakened. */
	public static final DataComponentType<Identifier> AWAKENED_IMBUE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "awakened_imbue"),
		DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build()
	);
	private static final int BURST_TOTEM_PARTICLES = 40;
	private static final int BURST_ROD_PARTICLES = 60;

	/** Test seam: the awakened flag each cast carried to its spell effect. */
	public interface AwakenedProbe {
		void seen(Identifier spell, boolean awakened);
	}

	private static final AwakenedProbe SILENT = (spell, awakened) -> {
	};
	private static volatile AwakenedProbe probe = SILENT;

	private Awakening() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, selection) -> registerCommand(dispatcher));
		CastHits.register();
	}

	public static void setProbe(final AwakenedProbe newProbe) {
		probe = newProbe == null ? SILENT : newProbe;
	}

	static void report(final Identifier spell, final boolean awakened) {
		probe.seen(spell, awakened);
	}

	public static boolean isAwakened(final ItemStack stack) {
		return stack.has(AWAKENED);
	}

	public static int count(final ItemStack stack) {
		return stack.getOrDefault(COUNT, 0);
	}

	/** Casts a plain book still needs; zero once it is awakened. */
	public static int castsLeft(final ItemStack stack) {
		return isAwakened(stack) ? 0 : Math.max(0, CASTS_TO_AWAKEN - count(stack));
	}

	/** Adds one landed cast to the book and awakens it at the threshold; true on the flip. */
	static boolean recordCast(final ServerPlayer caster, final ItemStack book) {
		if (!(book.getItem() instanceof SpellbookItem) || isAwakened(book)) {
			return false;
		}
		int next = count(book) + 1;
		if (next >= CASTS_TO_AWAKEN) {
			awaken(caster, book);
			return true;
		}
		book.set(COUNT, next);
		return false;
	}

	/** Sets the count to the threshold and awakens the very stack given; it is never copied. */
	static void awaken(final ServerPlayer owner, final ItemStack book) {
		book.set(COUNT, CASTS_TO_AWAKEN);
		book.set(AWAKENED, Unit.INSTANCE);
		book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		ServerLevel level = owner.level();
		level.playSound(null, owner.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getY(1.0), owner.getZ(), BURST_TOTEM_PARTICLES, 0.5, 0.8, 0.5, 0.4);
		level.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY(1.0), owner.getZ(), BURST_ROD_PARTICLES, 0.4, 0.8, 0.4, 0.2);
		owner.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".awakened", spellName(book)));
		Spellbooks.LOGGER.info("[SPELL] event=AWAKENED player={} book={}", owner.getUUID(), BuiltInRegistries.ITEM.getKey(book.getItem()));
	}

	private static Component spellName(final ItemStack book) {
		Identifier id = ((SpellbookItem) book.getItem()).definition().id();
		return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
	}

	/** Credits the first plain book of the spell in the caster's pack; used when a later pulse lands a cast's hit. */
	static void creditPending(final MinecraftServer server, final UUID caster, final Identifier spell) {
		ServerPlayer player = server.getPlayerList().getPlayer(caster);
		if (player == null) {
			return;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.getItem() instanceof SpellbookItem book && book.definition().id().equals(spell) && !isAwakened(stack)) {
				recordCast(player, stack);
				return;
			}
		}
	}

	/** Removes the awakening state; loot stacks never carry it. */
	public static ItemStack strip(final ItemStack stack) {
		stack.remove(COUNT);
		stack.remove(AWAKENED);
		stack.remove(AWAKENED_IMBUE);
		return stack;
	}

	private static void registerCommand(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("spellbook")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("awaken").executes(context -> awakenHeld(context.getSource()))));
	}

	private static int awakenHeld(final CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".awaken.no_player"));
			return 0;
		}
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!(held.getItem() instanceof SpellbookItem)) {
			source.sendFailure(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".awaken.no_book"));
			return 0;
		}
		if (isAwakened(held)) {
			source.sendFailure(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".awaken.already"));
			return 0;
		}
		awaken(player, held);
		return 1;
	}
}
