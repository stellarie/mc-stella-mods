package com.stella.food;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The small stat bonus an infused dish carries. It is rolled once, when the dish is infused, and stored on the
 * stack. The RPG mod reads the matching {@code seasoned_<stat>} effect: one stat point per effect level.
 */
public record Seasoning(String stat, int amount) {
	public static final List<String> STATS = List.of("str", "agi", "vit", "dex", "int", "luk");
	public static final int MIN_AMOUNT = 5;
	public static final int MAX_AMOUNT = 10;
	public static final Codec<Seasoning> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.validate(Seasoning::knownStat).fieldOf("stat").forGetter(Seasoning::stat),
		Codec.intRange(1, 255).fieldOf("amount").forGetter(Seasoning::amount)
	).apply(instance, Seasoning::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, Seasoning> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, Seasoning::stat,
		ByteBufCodecs.VAR_INT, Seasoning::amount,
		Seasoning::new
	);

	public static Seasoning roll(final RandomSource random) {
		return new Seasoning(
			STATS.get(random.nextInt(STATS.size())),
			MIN_AMOUNT + random.nextInt(MAX_AMOUNT - MIN_AMOUNT + 1)
		);
	}

	/** The stored bonus of a stack; null for a plain dish, ordinary food, or a dish infused before this existed. */
	public static @Nullable Seasoning read(final ItemStack stack) {
		return stack.get(StellaFood.SEASONING);
	}

	public Holder<MobEffect> effect() {
		return FoodEffects.SEASONED.get(this.stat);
	}

	public int amplifier() {
		return this.amount - 1;
	}

	public String label() {
		return this.stat.toUpperCase(Locale.ROOT);
	}

	private static DataResult<String> knownStat(final String stat) {
		return STATS.contains(stat) ? DataResult.success(stat) : DataResult.error(() -> "Unknown stat " + stat);
	}
}
