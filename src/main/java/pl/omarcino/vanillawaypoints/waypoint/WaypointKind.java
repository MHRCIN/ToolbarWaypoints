package pl.omarcino.vanillawaypoints.waypoint;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum WaypointKind {
	CUSTOM,
	DEATH;

	public static final Codec<WaypointKind> CODEC = Codec.STRING.xmap(
			WaypointKind::fromSerializedName,
			WaypointKind::serializedName
	);

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	private static WaypointKind fromSerializedName(String name) {
		return "death".equalsIgnoreCase(name) ? DEATH : CUSTOM;
	}
}
