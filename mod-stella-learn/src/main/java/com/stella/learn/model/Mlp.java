package com.stella.learn.model;

import java.util.List;

/**
 * A dense tanh network over a {@link PolicyFile}. It normalizes its input with the file's mean and std, applies tanh
 * to every hidden layer, and leaves the last layer raw. It works in double over the stored float values, so it matches
 * the numpy reference far inside the parity tolerance. A call allocates nothing.
 *
 * Each layer keeps its weights column-major: one input's weights for every output lie together. The inner loop is then a
 * plain multiply-add over two arrays, which the JIT turns into vector instructions; a dot product per output would not
 * vectorize, because floating point addition is not associative. The scratch buffers make an instance safe for one
 * thread only, which is the server thread.
 */
public final class Mlp {
	private final int inputs;
	private final int outputs;
	private final double[] mean;
	private final double[] invStd;
	private final float[][] columns;
	private final double[][] bias;
	private final int[] rows;
	private final int[] cols;
	private double[] current;
	private double[] next;

	public Mlp(final PolicyFile file) {
		List<PolicyFile.Layer> layers = file.layers();
		this.inputs = file.mean().length;
		this.outputs = layers.get(layers.size() - 1).rows();
		this.mean = toDouble(file.mean());
		this.invStd = new double[this.inputs];
		for (int i = 0; i < this.inputs; i++) {
			this.invStd[i] = 1.0 / file.std()[i];
		}

		this.columns = new float[layers.size()][];
		this.bias = new double[layers.size()][];
		this.rows = new int[layers.size()];
		this.cols = new int[layers.size()];
		int widest = this.inputs;
		for (int i = 0; i < layers.size(); i++) {
			PolicyFile.Layer layer = layers.get(i);
			float[] transposed = new float[layer.weights().length];
			for (int row = 0; row < layer.rows(); row++) {
				for (int col = 0; col < layer.cols(); col++) {
					transposed[col * layer.rows() + row] = layer.weights()[row * layer.cols() + col];
				}
			}

			this.columns[i] = transposed;
			this.bias[i] = toDouble(layer.bias());
			this.rows[i] = layer.rows();
			this.cols[i] = layer.cols();
			widest = Math.max(widest, layer.rows());
		}

		this.current = new double[widest];
		this.next = new double[widest];
	}

	public int inputSize() {
		return this.inputs;
	}

	public int outputSize() {
		return this.outputs;
	}

	/** Writes the raw outputs of the last layer. `observation` is already scaled and clamped by the contract. */
	public void forward(final float[] observation, final float[] out) {
		double[] in = this.current;
		double[] result = this.next;
		for (int i = 0; i < this.inputs; i++) {
			in[i] = (observation[i] - this.mean[i]) * this.invStd[i];
		}

		int last = this.rows.length - 1;
		for (int layer = 0; layer <= last; layer++) {
			int height = this.rows[layer];
			int width = this.cols[layer];
			System.arraycopy(this.bias[layer], 0, result, 0, height);
			multiplyAdd(this.columns[layer], in, result, width, height);
			if (layer < last) {
				for (int row = 0; row < height; row++) {
					result[row] = tanh(result[row]);
				}
			}

			double[] swap = in;
			in = result;
			result = swap;
		}

		for (int i = 0; i < this.outputs; i++) {
			out[i] = (float) in[i];
		}
	}

	/** tanh through the intrinsic exp, which is several times faster than Math.tanh and as exact as the parity test needs. */
	private static double tanh(final double x) {
		if (x > 20.0) {
			return 1.0;
		}

		if (x < -20.0) {
			return -1.0;
		}

		double e = Math.exp(x + x);
		return (e - 1.0) / (e + 1.0);
	}

	/**
	 * Adds each input times its column of weights. The weights stay float and the sums stay double: that halves the
	 * memory the loop streams, which is what limits it, and keeps the rounding of a sum out of the answer.
	 */
	private static void multiplyAdd(final float[] columns, final double[] in, final double[] result, final int width, final int height) {
		for (int col = 0; col < width; col++) {
			double value = in[col];
			int base = col * height;
			for (int row = 0; row < height; row++) {
				result[row] += columns[base + row] * value;
			}
		}
	}

	private static double[] toDouble(final float[] values) {
		double[] out = new double[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = values[i];
		}

		return out;
	}
}
