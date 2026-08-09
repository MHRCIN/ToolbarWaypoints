package pl.omarcino.vanillawaypoints.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import pl.omarcino.vanillawaypoints.waypoint.CustomWaypoint;
import pl.omarcino.vanillawaypoints.waypoint.DeathWaypointManager;
import pl.omarcino.vanillawaypoints.waypoint.WaypointData;
import pl.omarcino.vanillawaypoints.waypoint.WaypointKind;
import pl.omarcino.vanillawaypoints.waypoint.WaypointSync;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class WaypointCommands {
	private static final int MAX_WAYPOINTS_PER_PLAYER = 128;
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	private static final Pattern HEX_COLOR = Pattern.compile("[0-9A-Fa-f]{6}");
	private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
			Map.entry("black", 0x000000),
			Map.entry("dark_blue", 0x0000AA),
			Map.entry("dark_green", 0x00AA00),
			Map.entry("dark_aqua", 0x00AAAA),
			Map.entry("dark_red", 0xAA0000),
			Map.entry("dark_purple", 0xAA00AA),
			Map.entry("gold", 0xFFAA00),
			Map.entry("gray", 0xAAAAAA),
			Map.entry("dark_gray", 0x555555),
			Map.entry("blue", 0x5555FF),
			Map.entry("green", 0x55FF55),
			Map.entry("aqua", 0x55FFFF),
			Map.entry("red", 0xFF5555),
			Map.entry("light_purple", 0xFF55FF),
			Map.entry("yellow", 0xFFFF55),
			Map.entry("white", 0xFFFFFF)
	);

	private WaypointCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("point")
						.requires(source -> source.getPlayer() != null)
						.executes(WaypointCommands::showHelp)
						.then(Commands.literal("add")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(context -> add(context, context.getSource().getPlayerOrException().blockPosition(), CustomWaypoint.DEFAULT_COLOR))
										.then(colorArgument().executes(context -> addWithParsedColor(
												context,
												context.getSource().getPlayerOrException().blockPosition()
										)))
										.then(Commands.argument("position", BlockPosArgument.blockPos())
												.executes(context -> add(context, BlockPosArgument.getBlockPos(context, "position"), CustomWaypoint.DEFAULT_COLOR))
												.then(colorArgument().executes(context -> addWithParsedColor(
														context,
														BlockPosArgument.getBlockPos(context, "position")
												))))))
						.then(Commands.literal("remove").then(waypointNameArgument().executes(WaypointCommands::remove)))
						.then(Commands.literal("list").executes(WaypointCommands::list))
						.then(Commands.literal("info").then(waypointNameArgument().executes(WaypointCommands::info)))
						.then(Commands.literal("color")
								.then(waypointNameArgument()
										.then(colorArgument()
												.executes(WaypointCommands::color))))
						.then(Commands.literal("enable").then(waypointNameArgument()
								.executes(context -> updateFlag(context, waypoint -> waypoint.withEnabled(true), "enabled"))))
						.then(Commands.literal("disable").then(waypointNameArgument()
								.executes(context -> updateFlag(context, waypoint -> waypoint.withEnabled(false), "disabled"))))
						.then(Commands.literal("share").then(waypointNameArgument()
								.executes(context -> updateFlag(context, waypoint -> waypoint.withShared(true), "shared"))))
						.then(Commands.literal("unshare").then(waypointNameArgument()
								.executes(context -> updateFlag(context, waypoint -> waypoint.withShared(false), "private"))))
						.then(Commands.literal("death")
								.executes(WaypointCommands::deathInfo)
								.then(Commands.literal("clear").executes(WaypointCommands::clearDeath)))
		));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> colorArgument() {
		return Commands.argument("color", StringArgumentType.word())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(NAMED_COLORS.keySet(), builder));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> waypointNameArgument() {
		return Commands.argument("name", StringArgumentType.word())
				.suggests((context, builder) -> {
					ServerPlayer player = context.getSource().getPlayer();
					if (player == null) {
						return builder.buildFuture();
					}
					return SharedSuggestionProvider.suggest(
							WaypointData.get(context.getSource().getServer()).namesOwnedBy(player.getUUID()),
							builder
					);
				});
	}

	private static int addWithParsedColor(
			CommandContext<CommandSourceStack> context,
			BlockPos position
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		String colorText = StringArgumentType.getString(context, "color");
		OptionalInt parsedColor = parseColor(colorText);
		if (parsedColor.isEmpty()) {
			context.getSource().sendFailure(Component.translatable("command.vanilla-waypoints.invalid_color", colorText));
			return 0;
		}
		return add(context, position, parsedColor.getAsInt());
	}

	private static int add(
			CommandContext<CommandSourceStack> context,
			BlockPos position,
			int color
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		if (!VALID_NAME.matcher(name).matches()) {
			source.sendFailure(Component.translatable("command.vanilla-waypoints.invalid_name"));
			return 0;
		}

		WaypointData data = WaypointData.get(source.getServer());
		if (data.ownedBy(player.getUUID()).size() >= MAX_WAYPOINTS_PER_PLAYER) {
			source.sendFailure(Component.translatable("command.vanilla-waypoints.limit", MAX_WAYPOINTS_PER_PLAYER));
			return 0;
		}

		CustomWaypoint waypoint = new CustomWaypoint(
				UUID.randomUUID(),
				player.getUUID(),
				name,
				player.level().dimension(),
				position.immutable(),
				color,
				true,
				false,
				true,
				System.currentTimeMillis(),
				WaypointKind.CUSTOM
		);

		if (!data.add(waypoint)) {
			source.sendFailure(Component.translatable("command.vanilla-waypoints.already_exists", name));
			return 0;
		}

		WaypointSync.refreshAll(source.getServer());
		source.sendSuccess(() -> Component.translatable(
				"command.vanilla-waypoints.added", name, position.getX(), position.getY(), position.getZ()
		), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		Optional<CustomWaypoint> removed = WaypointData.get(source.getServer()).remove(player.getUUID(), name);
		if (removed.isEmpty()) {
			return notFound(source, name);
		}

		WaypointSync.refreshAll(source.getServer());
		source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.removed", removed.get().name()), false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		WaypointData data = WaypointData.get(source.getServer());
		var waypoints = data.ownedBy(player.getUUID());
		var deaths = data.deathWaypoints(player.getUUID());
		int total = waypoints.size() + deaths.size();
		if (total == 0) {
			source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.list.empty"), false);
			return 1;
		}

		source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.list.header", total), false);
		for (CustomWaypoint waypoint : waypoints) {
			BlockPos pos = waypoint.position();
			source.sendSuccess(() -> Component.translatable(
					"command.vanilla-waypoints.list.entry",
					Component.literal(waypoint.name()).withColor(waypoint.color()),
					pos.getX(), pos.getY(), pos.getZ(),
					waypoint.dimension().identifier().toString(),
					statusKey(waypoint)
			), false);
		}
		for (int index = 0; index < deaths.size(); index++) {
			CustomWaypoint waypoint = deaths.get(index);
			BlockPos pos = waypoint.position();
			int displayNumber = index + 1;
			source.sendSuccess(() -> Component.translatable(
					"command.vanilla-waypoints.list.death_entry",
					displayNumber,
					pos.getX(), pos.getY(), pos.getZ(),
					waypoint.dimension().identifier().toString()
			), false);
		}
		return total;
	}

	private static int info(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		Optional<CustomWaypoint> found = WaypointData.get(source.getServer()).findByName(player.getUUID(), name);
		if (found.isEmpty()) {
			return notFound(source, name);
		}

		CustomWaypoint waypoint = found.get();
		BlockPos pos = waypoint.position();
		source.sendSuccess(() -> Component.translatable(
				"command.vanilla-waypoints.info",
				Component.literal(waypoint.name()).withColor(waypoint.color()),
				pos.getX(), pos.getY(), pos.getZ(),
				waypoint.dimension().identifier().toString(),
				String.format(Locale.ROOT, "%06X", waypoint.color()),
				statusKey(waypoint)
		), false);
		return 1;
	}

	private static int color(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		String colorText = StringArgumentType.getString(context, "color");
		OptionalInt parsedColor = parseColor(colorText);
		if (parsedColor.isEmpty()) {
			source.sendFailure(Component.translatable("command.vanilla-waypoints.invalid_color", colorText));
			return 0;
		}

		Optional<CustomWaypoint> updated = WaypointData.get(source.getServer()).update(
				player.getUUID(), name, waypoint -> waypoint.withColor(parsedColor.getAsInt())
		);
		if (updated.isEmpty()) {
			return notFound(source, name);
		}

		WaypointSync.refreshAll(source.getServer());
		source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.color_changed", updated.get().name(), colorText), false);
		return 1;
	}

	private static int updateFlag(
			CommandContext<CommandSourceStack> context,
			UnaryOperator<CustomWaypoint> updater,
			String result
	) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		String name = StringArgumentType.getString(context, "name");
		Optional<CustomWaypoint> updated = WaypointData.get(source.getServer()).update(player.getUUID(), name, updater);
		if (updated.isEmpty()) {
			return notFound(source, name);
		}

		WaypointSync.refreshAll(source.getServer());
		source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints." + result, updated.get().name()), false);
		return 1;
	}

	private static int showHelp(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.translatable("command.vanilla-waypoints.help"), false);
		return 1;
	}

	private static int deathInfo(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		var deaths = WaypointData.get(source.getServer()).deathWaypoints(player.getUUID());
		if (deaths.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.death.none"), false);
			return 1;
		}

		source.sendSuccess(() -> Component.translatable("command.vanilla-waypoints.death.header", deaths.size()), false);
		for (int index = 0; index < deaths.size(); index++) {
			CustomWaypoint waypoint = deaths.get(index);
			BlockPos pos = waypoint.position();
			int displayNumber = index + 1;
			source.sendSuccess(() -> Component.translatable(
					"command.vanilla-waypoints.death.info",
					displayNumber,
					pos.getX(), pos.getY(), pos.getZ(), waypoint.dimension().identifier().toString()
			), false);
		}
		return deaths.size();
	}

	private static int clearDeath(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		if (DeathWaypointManager.clearAllDeathWaypoints(player) == 0) {
			source.sendFailure(Component.translatable("command.vanilla-waypoints.death.none"));
			return 0;
		}
		return 1;
	}

	private static int notFound(CommandSourceStack source, String name) {
		source.sendFailure(Component.translatable("command.vanilla-waypoints.not_found", name));
		return 0;
	}

	private static Component statusKey(CustomWaypoint waypoint) {
		if (!waypoint.enabled()) {
			return Component.translatable("command.vanilla-waypoints.status.disabled");
		}
		return Component.translatable(waypoint.shared()
				? "command.vanilla-waypoints.status.shared"
				: "command.vanilla-waypoints.status.private");
	}

	private static OptionalInt parseColor(String input) {
		Integer named = NAMED_COLORS.get(input.toLowerCase(Locale.ROOT));
		if (named != null) {
			return OptionalInt.of(named);
		}
		if (!HEX_COLOR.matcher(input).matches()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(Integer.parseInt(input, 16));
	}
}
