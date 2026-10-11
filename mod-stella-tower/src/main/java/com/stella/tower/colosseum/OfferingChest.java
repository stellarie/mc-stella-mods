package com.stella.tower.colosseum;

import com.stella.tower.StellaTower;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * The offering chest beside the portal. It cannot be mined or blown up, and it holds a book that tells what it is for.
 * Two stacks of bones put into it raise the Tower Guardians again, once both are dead; the bones are taken.
 */
public final class OfferingChest {
	/** How often the chest is looked at, in ticks. */
	public static final int CHECK_TICKS = 20;
	private static final double HEARD_FROM = 64.0;

	private OfferingChest() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OfferingChest::tick);
	}

	/** The chest's book. */
	public static ItemStack book() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough("The Offering"), "The Colosseum", 0,
			List.of(
				page("Two Tower Guardians keep this colosseum. One fights with a sword. One fights with a bow. Both ride skeleton horses."),
				page("Slay both, and the sand is quiet. Then put two stacks of bones, 128 bones, in this chest. The bones sink away, and the Guardians rise again."),
				page("The sword Guardian drops a Bone Shield. The bow Guardian drops a Skeleton Crown. Their horses can be tamed.")
			),
			true
		));
		return book;
	}

	private static Filterable<Component> page(final String text) {
		return Filterable.passThrough(Component.literal(text));
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		Colosseum.Layout layout = Colosseum.layoutOf(overworld);
		if (layout == null || !overworld.isLoaded(layout.chest())) {
			return;
		}
		try {
			offer(overworld, layout);
		} catch (RuntimeException failure) {
			StellaTower.LOGGER.warn("[COLOSSEUM] the offering chest failed: {}", failure.toString());
		}
	}

	/** Takes the bones and raises the guardians if the chest holds enough and none lives; true when it did. */
	public static boolean offer(final ServerLevel overworld, final Colosseum.Layout layout) {
		Optional<ChestBlockEntity> found = chestAt(overworld, layout.chest());
		if (found.isEmpty() || count(found.get()) < Colosseum.OFFERING_BONES) {
			return false;
		}
		ColosseumData data = ColosseumData.get(overworld);
		if (anyAlive(overworld, data)) {
			return false;
		}
		take(found.get(), Colosseum.OFFERING_BONES);
		found.get().setChanged();
		Guardians.raise(overworld, layout);
		BlockPos at = layout.chest();
		overworld.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.8F);
		for (ServerPlayer player : overworld.players()) {
			if (player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) <= HEARD_FROM * HEARD_FROM) {
				player.sendSystemMessage(Component.translatable("message.mod-stella-tower.guardians_rise"));
			}
		}
		return true;
	}

	private static Optional<ChestBlockEntity> chestAt(final ServerLevel level, final BlockPos at) {
		BlockEntity entity = level.getBlockEntity(at);
		return entity instanceof ChestBlockEntity chest ? Optional.of(chest) : Optional.empty();
	}

	static int count(final ChestBlockEntity chest) {
		int bones = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.is(Items.BONE)) {
				bones += stack.getCount();
			}
		}
		return bones;
	}

	private static void take(final ChestBlockEntity chest, final int amount) {
		int left = amount;
		for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.is(Items.BONE)) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
			}
		}
	}

	/**
	 * Whether any guardian still lives. A record that names a guardian the loaded world no longer holds is struck out,
	 * but only while the whole arena is loaded, so a guardian in an unloaded chunk still counts.
	 */
	static boolean anyAlive(final ServerLevel overworld, final ColosseumData data) {
		Set<UUID> recorded = data.guardians();
		for (UUID id : recorded) {
			Entity entity = overworld.getEntity(id);
			if (entity != null && entity.isAlive()) {
				return true;
			}
			if (entity == null && !arenaLoaded(overworld)) {
				return true;
			}
			data.fallen(id);
		}
		return false;
	}

	private static boolean arenaLoaded(final ServerLevel overworld) {
		Colosseum.Layout layout = Colosseum.layoutOf(overworld);
		if (layout == null) {
			return false;
		}
		BlockPos floor = layout.floor();
		for (int x = floor.getX() - Colosseum.RADIUS; x <= floor.getX() + Colosseum.RADIUS; x += 16) {
			for (int z = floor.getZ() - Colosseum.RADIUS; z <= floor.getZ() + Colosseum.RADIUS; z += 16) {
				if (!overworld.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(new BlockPos(x, floor.getY(), z)))) {
					return false;
				}
			}
		}
		return true;
	}
}
