package com.stella.learn.gym;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The TCP link to the Python trainer, on 127.0.0.1, little-endian. A frame is a u32 payload length, then the payload,
 * whose first byte is the frame type. `training/stella_train/wire.py` mirrors the codec below.
 */
public final class Bridge implements AutoCloseable {
	public static final int PROTOCOL_VERSION = 2;
	public static final int HELLO = 1;
	public static final int STEP = 2;
	public static final int ACT = 3;
	public static final int RESET = 4;
	public static final int BYE = 5;
	public static final int FLAG_START = 1;
	public static final int FLAG_TERMINAL = 2;
	public static final int FLAG_TRUNCATED = 4;
	public static final int FLAG_WIN = 8;
	public static final int FLAG_ERROR = 16;
	public static final int ALL_ARENAS = -1;
	public static final int HASH_SIZE = 32;
	public static final int READ_TIMEOUT_MILLIS = 30_000;
	/** Largest payload a peer may announce; a bigger one is a corrupt stream, not a frame. */
	private static final int MAX_PAYLOAD = 16 * 1024 * 1024;

	private final ServerSocket server;
	private final BlockingQueue<Socket> pending = new LinkedBlockingQueue<>();
	private final Thread acceptor;
	private volatile boolean closed;

	public Bridge(final int port) throws IOException {
		this.server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
		this.acceptor = new Thread(this::acceptLoop, "stella-gym-accept");
		this.acceptor.setDaemon(true);
		this.acceptor.start();
	}

	public int port() {
		return server.getLocalPort();
	}

	private void acceptLoop() {
		while (!closed) {
			try {
				pending.add(server.accept());
			} catch (IOException e) {
				if (!closed) {
					GymLog.info(GymLog.BRIDGE, "ACCEPT_FAILED", "reason=" + GymLog.CLIENT_GONE + " error=" + e.getMessage());
				}
			}
		}
	}

	/** The next client that connected, or null. Runs on the server thread, which then owns the session. */
	public Session pollSession() throws IOException {
		Socket socket = pending.poll();
		return socket == null ? null : new Session(socket);
	}

	@Override
	public void close() {
		closed = true;
		try {
			server.close();
		} catch (IOException ignored) {
			// The server is stopping; a failed close leaves nothing to clean.
		}
	}

	/** One client link. Reads block for at most the read timeout. */
	public static final class Session implements AutoCloseable {
		private final Socket socket;
		private final DataInputStream in;
		private final OutputStream out;

		Session(final Socket socket) throws IOException {
			this.socket = socket;
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(READ_TIMEOUT_MILLIS);
			this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 1 << 16));
			this.out = new BufferedOutputStream(socket.getOutputStream(), 1 << 16);
		}

		public String remote() {
			return String.valueOf(socket.getRemoteSocketAddress());
		}

		public void send(final byte[] frame) throws IOException {
			out.write(frame);
			out.flush();
		}

		/** Blocks for the next frame. Throws SocketTimeoutException when the peer is silent past the read timeout. */
		public Frame read() throws IOException {
			byte[] header = new byte[4];
			in.readFully(header);
			int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
			if (length < 1 || length > MAX_PAYLOAD) {
				throw new IllegalArgumentException("bad frame length " + length);
			}

			byte[] payload = new byte[length];
			in.readFully(payload);
			ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
			return new Frame(buffer.get() & 0xFF, buffer);
		}

		@Override
		public void close() {
			try {
				socket.close();
			} catch (IOException ignored) {
				// The peer is gone already.
			}
		}
	}

	/** A frame read off the wire; `body` is positioned after the type byte. */
	public record Frame(int type, ByteBuffer body) {
	}

	/** `brain` and `stage` are negative when the trainer leaves the choice to the server; `ticks` 0 means the server's episode length. */
	public record Reset(int arena, int seed, int brain, int stage, int ticks, Body.Range body) {
	}

	public record Act(int stepId, int[] agents, float[][] actions) {
	}

	private static ByteBuffer frame(final int type, final int bodySize) {
		ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + bodySize).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(1 + bodySize);
		buffer.put((byte) type);
		return buffer;
	}

	public static byte[] helloFrame(final int observationSize, final int actionSize, final int arenas, final byte[] contractHash) {
		if (contractHash.length != HASH_SIZE) {
			throw new IllegalArgumentException("contract hash must be " + HASH_SIZE + " bytes");
		}

		ByteBuffer buffer = frame(HELLO, 2 + 2 + 2 + 4 + HASH_SIZE);
		buffer.putShort((short) PROTOCOL_VERSION);
		buffer.putShort((short) observationSize);
		buffer.putShort((short) actionSize);
		buffer.putInt(arenas);
		buffer.put(contractHash);
		return buffer.array();
	}

	/** Per agent: ticks, engaged ticks, chase ticks, shots. Filled in an episode's last step; zero before. */
	public static final int METRICS = 4;

	public static byte[] stepFrame(
		final int stepId, final int[] agents, final int[] flags, final float[] rewards, final float[] shaping, final float[][] metrics,
		final float[][] observations
	) {
		int observationSize = agents.length == 0 ? 0 : observations[0].length;
		ByteBuffer buffer = frame(STEP, 4 + 4 + agents.length * (4 + 1 + 4 + 4 + 4 * METRICS + 4 * observationSize));
		buffer.putInt(stepId);
		buffer.putInt(agents.length);
		for (int i = 0; i < agents.length; i++) {
			buffer.putInt(agents[i]);
			buffer.put((byte) flags[i]);
			buffer.putFloat(rewards[i]);
			buffer.putFloat(shaping[i]);
			for (float value : metrics[i]) {
				buffer.putFloat(value);
			}

			for (float value : observations[i]) {
				buffer.putFloat(value);
			}
		}

		return buffer.array();
	}

	public static byte[] actFrame(final int stepId, final int[] agents, final float[][] actions) {
		int actionSize = agents.length == 0 ? 0 : actions[0].length;
		ByteBuffer buffer = frame(ACT, 4 + 4 + agents.length * (4 + 4 * actionSize));
		buffer.putInt(stepId);
		buffer.putInt(agents.length);
		for (int i = 0; i < agents.length; i++) {
			buffer.putInt(agents[i]);
			for (float value : actions[i]) {
				buffer.putFloat(value);
			}
		}

		return buffer.array();
	}

	public static byte[] resetFrame(final int arena, final int seed, final int brain, final int stage, final int ticks, final Body.Range body) {
		ByteBuffer buffer = frame(RESET, 44);
		buffer.putInt(arena);
		buffer.putInt(seed);
		buffer.putInt(brain);
		buffer.putInt(stage);
		buffer.putInt(ticks);
		buffer.putInt(body.healthMin());
		buffer.putInt(body.healthMax());
		buffer.putInt(body.armorMax());
		buffer.putInt(body.toughnessMax());
		buffer.putInt(body.protectionMax());
		buffer.putInt(body.specialPermille());
		return buffer.array();
	}

	public static byte[] byeFrame() {
		return frame(BYE, 0).array();
	}

	public static Act decodeAct(final ByteBuffer body, final int actionSize) {
		int stepId = body.getInt();
		int count = body.getInt();
		if (count < 0 || body.remaining() != count * (4 + 4 * actionSize)) {
			throw new IllegalArgumentException("act frame holds " + body.remaining() + " bytes for " + count + " agents");
		}

		int[] agents = new int[count];
		float[][] actions = new float[count][actionSize];
		for (int i = 0; i < count; i++) {
			agents[i] = body.getInt();
			for (int j = 0; j < actionSize; j++) {
				actions[i][j] = body.getFloat();
			}
		}

		return new Act(stepId, agents, actions);
	}

	public static Reset decodeReset(final ByteBuffer body) {
		if (body.remaining() != 44) {
			throw new IllegalArgumentException("reset frame holds " + body.remaining() + " bytes");
		}

		return new Reset(body.getInt(), body.getInt(), body.getInt(), body.getInt(), body.getInt(),
			new Body.Range(body.getInt(), body.getInt(), body.getInt(), body.getInt(), body.getInt(), body.getInt()));
	}
}
