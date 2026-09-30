package com.stella.learn.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The observation and action layout of one archetype, read from its contract JSON. The JSON is the only definition of
 * the layout: Python reads the same file. The hash is the SHA-256 of the file bytes, and a weights file must carry it.
 */
public final class Contract {
	public static final String ARCHER_RESOURCE = "policies/archer-v1.contract.json";
	/** The observation is divided by its scale, then clamped to plus or minus this. */
	public static final float CLAMP = 4.0F;
	public static final List<String> CONTINUOUS = List.of("forward", "strafe", "yaw", "pitch");
	public static final int JUMP_CHOICES = 2;
	public static final int BOW_CHOICES = 4;

	private final byte[] hash;
	private final int version;
	private final String archetype;
	private final int decisionTicks;
	private final String[] names;
	private final float[] scales;
	private final int[] hidden;
	private final Map<String, Integer> indexes = new HashMap<>();

	private Contract(
		final byte[] hash, final int version, final String archetype, final int decisionTicks,
		final String[] names, final float[] scales, final int[] hidden
	) {
		this.hash = hash;
		this.version = version;
		this.archetype = archetype;
		this.decisionTicks = decisionTicks;
		this.names = names;
		this.scales = scales;
		this.hidden = hidden;
		for (int i = 0; i < names.length; i++) {
			this.indexes.put(names[i], i);
		}
	}

	private static volatile Contract archer;

	/** The shipped archer contract, read once. Throws IllegalStateException when the resource is missing or broken. */
	public static Contract archer() {
		Contract loaded = archer;
		if (loaded == null) {
			synchronized (Contract.class) {
				loaded = archer;
				if (loaded == null) {
					try {
						loaded = loadResource(ARCHER_RESOURCE);
					} catch (IOException | RuntimeException exception) {
						throw new IllegalStateException("archer contract is unusable: " + exception.getMessage(), exception);
					}

					archer = loaded;
				}
			}
		}

		return loaded;
	}

	public static Contract loadResource(final String resource) throws IOException {
		try (InputStream stream = Contract.class.getClassLoader().getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException("contract resource " + resource + " is missing");
			}

			return parse(stream.readAllBytes());
		}
	}

	/** Throws IllegalArgumentException for a file that breaks the contract rules. */
	public static Contract parse(final byte[] bytes) {
		try {
			JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray fields = root.getAsJsonArray("observation");
			String[] names = new String[fields.size()];
			float[] scales = new float[fields.size()];
			Map<String, Integer> seen = new HashMap<>();
			for (int i = 0; i < names.length; i++) {
				JsonObject field = fields.get(i).getAsJsonObject();
				names[i] = field.get("name").getAsString();
				scales[i] = field.get("scale").getAsFloat();
				if (!(scales[i] > 0.0F) || Float.isInfinite(scales[i])) {
					throw new IllegalArgumentException("scale of " + names[i] + " must be positive and finite");
				}

				if (seen.put(names[i], i) != null) {
					throw new IllegalArgumentException("observation name " + names[i] + " repeats");
				}
			}

			JsonObject action = root.getAsJsonObject("action");
			JsonArray continuous = action.getAsJsonArray("continuous");
			for (int i = 0; i < CONTINUOUS.size(); i++) {
				if (continuous.size() != CONTINUOUS.size() || !CONTINUOUS.get(i).equals(continuous.get(i).getAsString())) {
					throw new IllegalArgumentException("continuous outputs must be " + CONTINUOUS);
				}
			}

			JsonObject discrete = action.getAsJsonObject("discrete");
			if (discrete.get("jump").getAsInt() != JUMP_CHOICES || discrete.get("bow").getAsInt() != BOW_CHOICES) {
				throw new IllegalArgumentException("discrete heads must be jump " + JUMP_CHOICES + " and bow " + BOW_CHOICES);
			}

			JsonObject network = root.getAsJsonObject("network");
			if (!"tanh".equals(network.get("activation").getAsString())) {
				throw new IllegalArgumentException("only the tanh activation is supported");
			}

			JsonArray sizes = network.getAsJsonArray("hidden");
			int[] hidden = new int[sizes.size()];
			for (int i = 0; i < hidden.length; i++) {
				JsonElement size = sizes.get(i);
				hidden[i] = size.getAsInt();
				if (hidden[i] <= 0) {
					throw new IllegalArgumentException("hidden sizes must be positive");
				}
			}

			int decisionTicks = root.get("decision_ticks").getAsInt();
			if (decisionTicks <= 0 || names.length == 0) {
				throw new IllegalArgumentException("decision_ticks and the observation must be positive");
			}

			return new Contract(
				MessageDigest.getInstance("SHA-256").digest(bytes), root.get("version").getAsInt(),
				root.get("archetype").getAsString(), decisionTicks, names, scales, hidden
			);
		} catch (NoSuchAlgorithmException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException illegal) {
				throw illegal;
			}

			throw new IllegalArgumentException("contract is not readable: " + exception.getMessage(), exception);
		}
	}

	public int version() {
		return this.version;
	}

	public String archetype() {
		return this.archetype;
	}

	public int decisionTicks() {
		return this.decisionTicks;
	}

	public int observationSize() {
		return this.names.length;
	}

	public int outputSize() {
		return CONTINUOUS.size() + JUMP_CHOICES + BOW_CHOICES;
	}

	public String name(final int index) {
		return this.names[index];
	}

	public float scale(final int index) {
		return this.scales[index];
	}

	public float[] scales() {
		return this.scales.clone();
	}

	public int[] hidden() {
		return this.hidden.clone();
	}

	/** The position of a field, or -1. */
	public int indexOf(final String name) {
		return this.indexes.getOrDefault(name, -1);
	}

	public byte[] hash() {
		return this.hash.clone();
	}

	public boolean hashEquals(final byte[] other) {
		return MessageDigest.isEqual(this.hash, other);
	}

	public String hashHex() {
		return HexFormat.of().formatHex(this.hash);
	}
}
