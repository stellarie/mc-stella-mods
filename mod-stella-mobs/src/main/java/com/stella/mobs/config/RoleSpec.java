package com.stella.mobs.config;

public record RoleSpec(CombatRole role, double nearDistance, double farDistance) {
	public static final double RANGED_NEAR = 8.0;
	public static final double RANGED_FAR = 15.0;
	public static final double MAGIC_NEAR = 8.0;
	public static final double MAGIC_FAR = 12.0;
	public static final RoleSpec DEFAULT = new RoleSpec(CombatRole.MELEE, 0.0, 0.0);

	public static RoleSpec of(final CombatRole role) {
		return switch (role) {
			case RANGED -> new RoleSpec(role, RANGED_NEAR, RANGED_FAR);
			case MAGIC -> new RoleSpec(role, MAGIC_NEAR, MAGIC_FAR);
			case MELEE -> DEFAULT;
		};
	}

	public RoleSpec withBand(final double near, final double far) {
		return new RoleSpec(this.role, near, far);
	}

	public RoleSpec withRole(final CombatRole newRole) {
		return new RoleSpec(newRole, this.nearDistance, this.farDistance);
	}
}
