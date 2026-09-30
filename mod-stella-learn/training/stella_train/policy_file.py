"""The weights file (`SLMP`, format 1) that `com.stella.learn.model.PolicyFile` reads, and a numpy forward pass.

Layout, little-endian: magic `SLMP`, u16 format, 32 byte contract hash, u16 length and UTF-8 archetype, u32 observation
size, f32 mean and f32 std per input, u16 layer count, then per layer u32 rows, u32 columns, f32 weights row-major and
f32 bias. The last layer holds the raw outputs: continuous means, then jump logits, then bow logits.
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from stella_train.contract import CLAMP, Contract

MAGIC = b"SLMP"
FORMAT_VERSION = 1


class PolicyFormatError(ValueError):
    """A weights file that does not follow the format."""


@dataclass(frozen=True)
class Layer:
    weights: np.ndarray
    bias: np.ndarray


@dataclass(frozen=True)
class Policy:
    contract_hash: bytes
    archetype: str
    mean: np.ndarray
    std: np.ndarray
    layers: tuple[Layer, ...]

    @property
    def obs_size(self) -> int:
        return int(self.mean.shape[0])

    def forward(self, obs: np.ndarray) -> np.ndarray:
        """Raw outputs for one observation or a batch, in float64 over the stored float32 values."""
        x = (np.asarray(obs, dtype=np.float64) - self.mean.astype(np.float64)) / self.std.astype(np.float64)
        last = len(self.layers) - 1
        for index, layer in enumerate(self.layers):
            x = x @ layer.weights.astype(np.float64).T + layer.bias.astype(np.float64)
            if index < last:
                x = np.tanh(x)
        return x


def scale_observation(raw: np.ndarray, contract: Contract) -> np.ndarray:
    """What the Java side feeds the network: raw / scale, clamped to [-4, 4]."""
    return np.clip(np.asarray(raw, dtype=np.float64) / np.asarray(contract.scales), -CLAMP, CLAMP)


def make_policy(contract: Contract, layers: list[Layer], mean: np.ndarray | None = None, std: np.ndarray | None = None) -> Policy:
    mean = np.zeros(contract.obs_size, dtype=np.float32) if mean is None else np.asarray(mean, dtype=np.float32)
    std = np.ones(contract.obs_size, dtype=np.float32) if std is None else np.asarray(std, dtype=np.float32)
    return Policy(contract.hash, contract.archetype, mean, std, tuple(layers))


def encode(policy: Policy) -> bytes:
    name = policy.archetype.encode("utf-8")
    out = bytearray()
    out += MAGIC
    out += struct.pack("<H", FORMAT_VERSION)
    if len(policy.contract_hash) != 32:
        raise PolicyFormatError("contract hash must be 32 bytes")
    out += policy.contract_hash
    out += struct.pack("<H", len(name)) + name
    out += struct.pack("<I", policy.obs_size)
    out += policy.mean.astype("<f4").tobytes()
    out += policy.std.astype("<f4").tobytes()
    out += struct.pack("<H", len(policy.layers))
    for layer in policy.layers:
        rows, cols = layer.weights.shape
        out += struct.pack("<II", rows, cols)
        out += layer.weights.astype("<f4").tobytes()
        out += layer.bias.astype("<f4").tobytes()
    return bytes(out)


def write(policy: Policy, path: Path | str) -> None:
    Path(path).write_bytes(encode(policy))


def decode(data: bytes) -> Policy:
    view = memoryview(data)
    at = 0

    def take(size: int) -> memoryview:
        nonlocal at
        if size < 0 or at + size > len(view):
            raise PolicyFormatError("file ends early")
        chunk = view[at : at + size]
        at += size
        return chunk

    if bytes(take(4)) != MAGIC:
        raise PolicyFormatError("bad magic")
    (version,) = struct.unpack("<H", take(2))
    if version != FORMAT_VERSION:
        raise PolicyFormatError(f"unsupported format {version}")
    contract_hash = bytes(take(32))
    (name_length,) = struct.unpack("<H", take(2))
    archetype = bytes(take(name_length)).decode("utf-8")
    (obs_size,) = struct.unpack("<I", take(4))
    mean = np.frombuffer(bytes(take(4 * obs_size)), dtype="<f4").astype(np.float32)
    std = np.frombuffer(bytes(take(4 * obs_size)), dtype="<f4").astype(np.float32)
    (layer_count,) = struct.unpack("<H", take(2))
    layers = []
    for _ in range(layer_count):
        rows, cols = struct.unpack("<II", take(8))
        weights = np.frombuffer(bytes(take(4 * rows * cols)), dtype="<f4").astype(np.float32).reshape(rows, cols)
        bias = np.frombuffer(bytes(take(4 * rows)), dtype="<f4").astype(np.float32)
        layers.append(Layer(weights, bias))
    if at != len(view):
        raise PolicyFormatError("trailing bytes")
    return Policy(contract_hash, archetype, mean, std, tuple(layers))


def read(path: Path | str) -> Policy:
    return decode(Path(path).read_bytes())
