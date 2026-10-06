package com.stella.spells;

public enum TargetMode {
	SELF,
	HOSTILE,
	BLOCK,
	SAFE_TELEPORT,
	/** An ally the caster looks at, else the caster itself; read through {@link CastContext#ally()}. */
	ALLY
}
