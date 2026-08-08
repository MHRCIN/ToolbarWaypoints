package pl.omarcino.vanillawaypoints.client;

import pl.omarcino.vanillawaypoints.network.WaypointPayloads;

import java.util.List;

public final class WaypointClientState {
	private static List<WaypointPayloads.Entry> entries = List.of();
	private static int revision;

	private WaypointClientState() {
	}

	public static synchronized void replace(List<WaypointPayloads.Entry> newEntries) {
		entries = List.copyOf(newEntries);
		revision++;
	}

	public static synchronized List<WaypointPayloads.Entry> entries() {
		return entries;
	}

	public static synchronized int revision() {
		return revision;
	}
}
