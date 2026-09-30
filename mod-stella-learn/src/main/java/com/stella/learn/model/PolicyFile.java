package com.stella.learn.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A weights file (`SLMP`, format 1) checked against a contract. `stella_train/policy_file.py` writes the same layout.
 * A file that fails a check throws a {@link Rejected} whose reason is the `status` of the `learnModel LOAD` line.
 */
public final class PolicyFile {
	public static final String STATUS_CONTRACT = "contract";
	public static final String STATUS_FORMAT = "format";
	private static final byte[] MAGIC = {'S', 'L', 'M', 'P'};
	private static final int FORMAT_VERSION = 1;
	private static final int HASH_SIZE = 32;

	/** One dense layer: `weights` is row-major with `rows` outputs and `cols` inputs. */
	public record Layer(int rows, int cols, float[] weights, float[] bias) {
	}

	/** Why a file was refused. `reason` is `contract` or `format`. */
	public static final class Rejected extends Exception {
		private final String reason;

		public Rejected(final String reason, final String detail) {
			super(detail);
			this.reason = reason;
		}

		public String reason() {
			return this.reason;
		}
	}

	private final int size;
	private final float[] mean;
	private final float[] std;
	private final List<Layer> layers;

	private PolicyFile(final int size, final float[] mean, final float[] std, final List<Layer> layers) {
		this.size = size;
		this.mean = mean;
		this.std = std;
		this.layers = layers;
	}

	/** Size in bytes of the file this was read from. */
	public int size() {
		return this.size;
	}

	public float[] mean() {
		return this.mean;
	}

	public float[] std() {
		return this.std;
	}

	public List<Layer> layers() {
		return this.layers;
	}

	public static PolicyFile loadResource(final String resource, final Contract contract) throws IOException, Rejected {
		try (InputStream stream = PolicyFile.class.getClassLoader().getResourceAsStream(resource)) {
			if (stream == null) {
				throw new IOException("missing");
			}

			return parse(stream.readAllBytes(), contract);
		}
	}

	public static PolicyFile parse(final byte[] bytes, final Contract contract) throws Rejected {
		ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		try {
			byte[] magic = new byte[MAGIC.length];
			in.get(magic);
			if (!java.util.Arrays.equals(magic, MAGIC)) {
				throw new Rejected(STATUS_FORMAT, "bad magic");
			}

			int version = Short.toUnsignedInt(in.getShort());
			if (version != FORMAT_VERSION) {
				throw new Rejected(STATUS_FORMAT, "unsupported format " + version);
			}

			byte[] hash = new byte[HASH_SIZE];
			in.get(hash);
			if (!contract.hashEquals(hash)) {
				throw new Rejected(STATUS_CONTRACT, "contract hash differs from " + contract.hashHex().substring(0, 8));
			}

			byte[] name = new byte[Short.toUnsignedInt(in.getShort())];
			in.get(name);
			String archetype = new String(name, StandardCharsets.UTF_8);
			if (!archetype.equals(contract.archetype())) {
				throw new Rejected(STATUS_CONTRACT, "archetype " + archetype + " is not " + contract.archetype());
			}

			long observationSize = Integer.toUnsignedLong(in.getInt());
			if (observationSize != contract.observationSize()) {
				throw new Rejected(STATUS_FORMAT, "observation size " + observationSize + " is not " + contract.observationSize());
			}

			int size = contract.observationSize();
			float[] mean = floats(in, size);
			float[] std = floats(in, size);
			for (int i = 0; i < size; i++) {
				if (!Float.isFinite(mean[i]) || !Float.isFinite(std[i]) || std[i] <= 0.0F) {
					throw new Rejected(STATUS_FORMAT, "input " + i + " has mean " + mean[i] + " and std " + std[i]);
				}
			}

			int[] hidden = contract.hidden();
			int count = Short.toUnsignedInt(in.getShort());
			if (count != hidden.length + 1) {
				throw new Rejected(STATUS_FORMAT, "layer count " + count + " is not " + (hidden.length + 1));
			}

			List<Layer> layers = new ArrayList<>(count);
			int inputs = size;
			for (int i = 0; i < count; i++) {
				long rows = Integer.toUnsignedLong(in.getInt());
				long cols = Integer.toUnsignedLong(in.getInt());
				int expectedRows = i < hidden.length ? hidden[i] : contract.outputSize();
				if (rows != expectedRows || cols != inputs) {
					throw new Rejected(STATUS_FORMAT, "layer " + i + " is " + rows + "x" + cols + ", expected " + expectedRows + "x" + inputs);
				}

				float[] weights = floats(in, (int) (rows * cols));
				float[] bias = floats(in, (int) rows);
				layers.add(new Layer((int) rows, (int) cols, weights, bias));
				inputs = (int) rows;
			}

			if (in.hasRemaining()) {
				throw new Rejected(STATUS_FORMAT, in.remaining() + " trailing bytes");
			}

			return new PolicyFile(bytes.length, mean, std, List.copyOf(layers));
		} catch (BufferUnderflowException exception) {
			throw new Rejected(STATUS_FORMAT, "file ends early");
		}
	}

	private static float[] floats(final ByteBuffer in, final int count) throws Rejected {
		if ((long) count * Float.BYTES > in.remaining()) {
			throw new Rejected(STATUS_FORMAT, "file ends early");
		}

		float[] values = new float[count];
		in.asFloatBuffer().get(values);
		in.position(in.position() + count * Float.BYTES);
		return values;
	}
}
