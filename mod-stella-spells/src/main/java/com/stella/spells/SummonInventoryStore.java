package com.stella.spells;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;

/** One saved gear, pack and growth snapshot per owner, held on the Overworld across deaths and dimensions. */
final class SummonInventoryStore {
	private record Kit(UUID owner, List<ItemStack> gear, List<ItemStack> pack, String name, SummonSkeleton.Growth growth) {
		private static final Codec<Kit> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Kit::owner),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("gear").forGetter(Kit::gear),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("pack").forGetter(Kit::pack),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Kit::name),
			SummonSkeleton.Growth.CODEC.optionalFieldOf("growth", SummonSkeleton.FRESH).forGetter(Kit::growth)
		).apply(instance, Kit::new));
	}

	static final AttachmentType<List<Kit>> TYPE = AttachmentRegistry.createPersistent(
		Identifier.fromNamespaceAndPath(Spellbooks.MOD_ID, "summon_inventory"), Kit.CODEC.listOf()
	);

	private SummonInventoryStore() {
	}

	static void register() {
		Spellbooks.LOGGER.debug("Registered summon inventory attachment {}", TYPE.identifier());
	}

	static void save(final Skeleton skeleton) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		UUID owner = SummonSkeleton.ownerOf(skeleton).orElse(null);
		if (owner == null) {
			return;
		}
		List<ItemStack> gear = new ArrayList<>();
		for (EquipmentSlot slot : SkeletonMenu.GEAR) {
			gear.add(skeleton.getItemBySlot(slot).copy());
		}
		List<ItemStack> pack = SummonSkeleton.pack(skeleton).stream().map(ItemStack::copy).toList();
		String name = skeleton.getCustomName() == null ? "" : skeleton.getCustomName().getString();
		MinecraftServer server = level.getServer();
		AttachmentTarget world = (AttachmentTarget) server.overworld();
		List<Kit> previous = world.getAttached(TYPE);
		List<Kit> updated = new ArrayList<>(previous == null ? List.of() : previous);
		updated.removeIf(kit -> kit.owner().equals(owner));
		updated.add(new Kit(owner, List.copyOf(gear), List.copyOf(pack), name, SummonSkeleton.growth(skeleton)));
		world.setAttached(TYPE, List.copyOf(updated));
	}

	static boolean restore(final Skeleton skeleton) {
		if (!(skeleton.level() instanceof ServerLevel level)) {
			return false;
		}
		UUID owner = SummonSkeleton.ownerOf(skeleton).orElse(null);
		if (owner == null) {
			return false;
		}
		List<Kit> saved = ((AttachmentTarget) level.getServer().overworld()).getAttached(TYPE);
		Kit kit = saved == null ? null : saved.stream().filter(entry -> entry.owner().equals(owner)).findFirst().orElse(null);
		if (kit == null) {
			return false;
		}
		for (int i = 0; i < SkeletonMenu.GEAR.length; i++) {
			EquipmentSlot slot = SkeletonMenu.GEAR[i];
			skeleton.setItemSlot(slot, i < kit.gear().size() ? kit.gear().get(i).copy() : ItemStack.EMPTY);
			skeleton.setDropChance(slot, 0.0F);
		}
		skeleton.setAttached(SummonSkeleton.PACK, kit.pack().stream().map(ItemStack::copy).toList());
		if (!kit.name().isEmpty()) {
			skeleton.setCustomName(Component.literal(kit.name()));
		}
		SummonSkeleton.Growth growth = kit.growth();
		SummonSkeleton.restoreGrowth(skeleton, growth.level(), growth.xp(), growth.stats());
		return true;
	}
}
