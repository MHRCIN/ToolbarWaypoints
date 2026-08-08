package pl.omarcino.vanillawaypoints;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.omarcino.vanillawaypoints.command.WaypointCommands;
import pl.omarcino.vanillawaypoints.network.WaypointNetworking;
import pl.omarcino.vanillawaypoints.waypoint.DeathWaypointManager;
import pl.omarcino.vanillawaypoints.waypoint.WaypointSync;

public class VanillaWaypoints implements ModInitializer {
	public static final String MOD_ID = "vanilla-waypoints";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		WaypointNetworking.register();
		WaypointCommands.register();
		WaypointSync.registerEvents();
		DeathWaypointManager.registerEvents();
		LOGGER.info("Vanilla Waypoints initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
