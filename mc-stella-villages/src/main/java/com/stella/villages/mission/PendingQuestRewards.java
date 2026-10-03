package com.stella.villages.mission;

import com.mojang.serialization.Codec;
import com.stella.villages.McStellaVillages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Kill and guard rewards that wait for an owner who was offline at the last kill. Each entry keeps the mob type,
 * elite status and threat level, so the login roll pays the tier the quest earned.
 */
public final class PendingQuestRewards extends SavedData {
	/** One reward owed: the quest mob that fell, and whether the quest was a guard quest. */
	public record Entry(Identifier type, boolean elite, int threat, boolean guard) {
	}

	private static final String OWNERS_TAG = "owners";
	public static final Codec<PendingQuestRewards> CODEC = CompoundTag.CODEC.xmap(PendingQuestRewards::load, PendingQuestRewards::save);
	private static final SavedDataType<PendingQuestRewards> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "pending_quest_rewards"),
		PendingQuestRewards::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, List<Entry>> owed = new LinkedHashMap<>();

	public static PendingQuestRewards get(final ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public void add(final UUID owner, final Entry entry) {
		this.owed.computeIfAbsent(owner, key -> new ArrayList<>()).add(entry);
		setDirty();
	}

	/** Takes every reward waiting for the owner. */
	public List<Entry> take(final UUID owner) {
		List<Entry> entries = this.owed.remove(owner);
		if (entries == null) {
			return List.of();
		}
		setDirty();
		return entries;
	}

	public int count(final UUID owner) {
		return this.owed.getOrDefault(owner, List.of()).size();
	}

	private CompoundTag save() {
		CompoundTag root = new CompoundTag();
		ListTag owners = new ListTag();
		this.owed.forEach((owner, entries) -> {
			CompoundTag ownerTag = new CompoundTag();
			ownerTag.putString("owner", owner.toString());
			ListTag list = new ListTag();
			for (Entry entry : entries) {
				CompoundTag tag = new CompoundTag();
				tag.putString("type", entry.type().toString());
				tag.putBoolean("elite", entry.elite());
				tag.putInt("threat", entry.threat());
				tag.putBoolean("guard", entry.guard());
				list.add(tag);
			}
			ownerTag.put("entries", list);
			owners.add(ownerTag);
		});
		root.put(OWNERS_TAG, owners);
		return root;
	}

	private static PendingQuestRewards load(final CompoundTag root) {
		PendingQuestRewards data = new PendingQuestRewards();
		for (Tag value : root.getListOrEmpty(OWNERS_TAG)) {
			if (!(value instanceof CompoundTag ownerTag)) {
				continue;
			}
			try {
				UUID owner = UUID.fromString(ownerTag.getStringOr("owner", ""));
				for (Tag item : ownerTag.getListOrEmpty("entries")) {
					if (item instanceof CompoundTag tag) {
						data.owed.computeIfAbsent(owner, key -> new ArrayList<>()).add(new Entry(
							Identifier.parse(tag.getStringOr("type", "minecraft:zombie")), tag.getBooleanOr("elite", false),
							tag.getIntOr("threat", 0), tag.getBooleanOr("guard", false)
						));
					}
				}
			} catch (IllegalArgumentException error) {
				McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=saved_reward_skipped detail={}", McStellaVillages.MOD_ID, error.toString());
			}
		}
		return data;
	}
}
