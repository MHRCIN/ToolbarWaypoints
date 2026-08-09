package pl.omarcino.vanillawaypoints.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import pl.omarcino.vanillawaypoints.client.WaypointClientState;
import pl.omarcino.vanillawaypoints.client.VanillaWaypointsClient;
import pl.omarcino.vanillawaypoints.network.WaypointPayloads;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WaypointScreen extends Screen {
	private static WaypointScreen activeScreen;
	private static final int[] COLORS = {
			0xFFFFFF, 0xFF5555, 0xFFAA00, 0xFFFF55,
			0x55FF55, 0x00AAAA, 0x55FFFF, 0x5555FF,
			0xAA00AA, 0xFF55FF, 0xAA0000, 0x00AA00,
			0x0000AA, 0xAAAAAA, 0x555555, 0x000000
	};
	private static final String[] COLOR_NAMES = {
			"white", "red", "orange", "yellow",
			"lime", "cyan", "light_blue", "blue",
			"purple", "pink", "dark_red", "green",
			"dark_blue", "light_gray", "gray", "black"
	};
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 120;
	private static final int MAX_VISIBLE_ROWS = 3;
	private static final int ROW_HEIGHT = 24;

	private final Set<UUID> expandedRows = new HashSet<>();
	private final List<RowArea> rowAreas = new ArrayList<>();

	private List<WaypointPayloads.Entry> entries = List.of();
	private int knownRevision = -1;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int listTop;
	private int listBottom;
	private int scrollOffset;
	private int colorIndex = ThreadLocalRandom.current().nextInt(COLORS.length);
	private boolean customCoordinates;
	private boolean editMode;
	private String nameValue = "";
	private String xValue = "0";
	private String yValue = "0";
	private String zValue = "0";
	private Component status = Component.empty();
	private int statusColor = 0xFF5555;
	private Button addButton;

	public WaypointScreen() {
		super(Component.translatable("gui.vanilla-waypoints.title"));
	}

	@Override
	protected void init() {
		entries = WaypointClientState.entries();
		knownRevision = WaypointClientState.revision();
		panelWidth = Math.min(PANEL_WIDTH, width - 16);
		panelHeight = Math.min(PANEL_HEIGHT + (customCoordinates ? 20 : 0), height - 16);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		rowAreas.clear();

		int innerLeft = panelX + 12;
		int innerRight = panelX + panelWidth - 12;
		int formY = panelY + 12;

		int nameWidth = Math.max(72, innerRight - innerLeft - 150);
		EditBox nameBox = new EditBox(font, innerLeft, formY, nameWidth, 18,
				Component.translatable("gui.vanilla-waypoints.name"));
		nameBox.setMaxLength(32);
		nameBox.setHint(Component.translatable("gui.vanilla-waypoints.name"));
		nameBox.setValue(nameValue);
		nameBox.setResponder(value -> {
			nameValue = value;
			updateAddButton();
		});
		addRenderableWidget(nameBox);

		int colorX = innerLeft + nameWidth + 4;
		Button colorButton = Button.builder(coloredSquare(COLORS[colorIndex]), button -> {
			colorIndex = (colorIndex + 1) % COLORS.length;
			button.setMessage(coloredSquare(COLORS[colorIndex]));
			button.setTooltip(colorTooltip(colorIndex));
		}).bounds(colorX, formY, 24, 18).build();
		colorButton.setTooltip(colorTooltip(colorIndex));
		addRenderableWidget(colorButton);

		Button coordinatesButton = Button.builder(
				Component.literal(customCoordinates ? "▲" : "▼"),
				button -> {
					customCoordinates = !customCoordinates;
					if (customCoordinates && minecraft.player != null && coordinatesAreInitial()) {
						BlockPos position = minecraft.player.blockPosition();
						xValue = Integer.toString(position.getX());
						yValue = Integer.toString(position.getY());
						zValue = Integer.toString(position.getZ());
					}
					rebuildWidgets();
				}
		).bounds(colorX + 28, formY, 20, 18).build();
		coordinatesButton.setTooltip(Tooltip.create(Component.translatable("gui.vanilla-waypoints.coordinates.toggle")));
		addRenderableWidget(coordinatesButton);

		int addX = colorX + 52;
		addButton = addRenderableWidget(Button.builder(Component.translatable("gui.vanilla-waypoints.add"), button -> addWaypoint())
				.bounds(addX, formY, 44, 18)
				.build());
		updateAddButton();

		addRenderableWidget(Button.builder(
				Component.translatable(editMode ? "gui.vanilla-waypoints.done" : "gui.vanilla-waypoints.edit"),
				button -> {
					editMode = !editMode;
					expandedRows.clear();
					rebuildWidgets();
				}
		).bounds(addX + 48, formY, innerRight - (addX + 48), 18).build());

		if (customCoordinates) {
			int coordinateY = formY + 20;
			int fieldWidth = Math.max(50, (innerRight - innerLeft - 8) / 3);
			addCoordinateBox(innerLeft, coordinateY, fieldWidth, "X", xValue, value -> xValue = value);
			addCoordinateBox(innerLeft + fieldWidth + 4, coordinateY, fieldWidth, "Y", yValue, value -> yValue = value);
			addCoordinateBox(innerLeft + (fieldWidth + 4) * 2, coordinateY, fieldWidth, "Z", zValue, value -> zValue = value);
		}

		listTop = formY + (customCoordinates ? 43 : 23);
		listBottom = listTop + MAX_VISIBLE_ROWS * ROW_HEIGHT;
		buildRows(innerLeft, innerRight);
	}

	private void addCoordinateBox(int x, int y, int fieldWidth, String axis, String value,
	                              java.util.function.Consumer<String> responder) {
		EditBox box = new EditBox(font, x, y, fieldWidth, 18, Component.literal(axis));
		box.setMaxLength(11);
		box.setHint(Component.literal(axis));
		box.setValue(value);
		box.setResponder(responder);
		addRenderableWidget(box);
	}

	private void buildRows(int innerLeft, int innerRight) {
		int visibleRows = visibleRowCount();
		int maxOffset = Math.max(0, entries.size() - visibleRows);
		scrollOffset = Math.min(scrollOffset, maxOffset);
		int end = Math.min(entries.size(), scrollOffset + visibleRows);

		for (int index = scrollOffset; index < end; index++) {
			WaypointPayloads.Entry entry = entries.get(index);
			int rowY = listTop + (index - scrollOffset) * ROW_HEIGHT;
			rowAreas.add(new RowArea(entry, innerLeft, rowY, innerRight - innerLeft, ROW_HEIGHT - 1));
			boolean expanded = !editMode && expandedRows.contains(entry.id());

			Button color = Button.builder(
					entry.death() ? Component.literal("☠").withStyle(Style.EMPTY.withColor(0xFFFFFF)) : coloredSquare(entry.color()),
					button -> cycleEntryColor(entry)
			).bounds(innerLeft + 2, rowY + 1, 18, 17).build();
			color.active = !entry.death();
			color.setTooltip(Tooltip.create(entry.death()
					? Component.translatable("gui.vanilla-waypoints.death")
					: Component.translatable("gui.vanilla-waypoints.color.change")));
			addRenderableWidget(color);

			int nameRightPadding = editMode ? 54 : 42;
			int labelWidth = Math.max(30, innerRight - (innerLeft + 23) - nameRightPadding);
			Component baseLabel = entry.death()
					? Component.translatable("gui.vanilla-waypoints.death.numbered", deathDisplayNumber(entry))
					: Component.literal(entry.name());
			Component label = (expanded
					? Component.translatable("gui.vanilla-waypoints.name_with_dimension", baseLabel, dimensionName(entry.dimension()))
					: baseLabel)
					.copy().withStyle(Style.EMPTY.withColor(entry.enabled() ? 0xFFFFFF : 0xA0A0A0));
			StringWidget nameLabel = new StringWidget(
					innerLeft + 23, rowY + (expanded ? 0 : 5), labelWidth, 9, label, font
			).setMaxWidth(labelWidth);
			addRenderableWidget(nameLabel);

			if (expanded) {
				Component coordinates = Component.translatable(
						"gui.vanilla-waypoints.coordinates.values",
						entry.position().getX(), entry.position().getY(), entry.position().getZ()
				).withStyle(Style.EMPTY.withColor(0xB8B8B8));
				StringWidget coordinateLabel = new StringWidget(
						innerLeft + 23, rowY + 9, labelWidth, 9, coordinates, font
				).setMaxWidth(labelWidth);
				addRenderableWidget(coordinateLabel);
			}

			if (editMode) {
				Button worldRendering = Button.builder(
						Component.literal(entry.renderInWorld() ? "3D+" : "3D−")
								.withStyle(Style.EMPTY.withColor(entry.renderInWorld() ? 0xFFFFFF : 0x888888)),
						button -> sendAction(entry.id(), WaypointPayloads.Action.TOGGLE_WORLD_RENDERING, 0)
				).bounds(innerRight - 50, rowY + 1, 30, 17).build();
				worldRendering.setTooltip(Tooltip.create(Component.translatable(entry.renderInWorld()
						? "gui.vanilla-waypoints.world_rendering.disable"
						: "gui.vanilla-waypoints.world_rendering.enable")));
				addRenderableWidget(worldRendering);

				Button delete = Button.builder(Component.literal("×"), button -> sendAction(entry.id(), WaypointPayloads.Action.DELETE, 0))
						.bounds(innerRight - 18, rowY + 1, 16, 17).build();
				delete.setTooltip(Tooltip.create(Component.translatable("gui.vanilla-waypoints.delete")));
				addRenderableWidget(delete);
			} else {
				Button visible = Button.builder(
						Component.literal(entry.enabled() ? "◆" : "◇"),
						button -> sendAction(entry.id(), WaypointPayloads.Action.TOGGLE_VISIBILITY, 0)
				).bounds(innerRight - 36, rowY + 1, 16, 17).build();
				visible.setTooltip(Tooltip.create(Component.translatable(entry.enabled()
						? "gui.vanilla-waypoints.visibility.hide"
						: "gui.vanilla-waypoints.visibility.show")));
				addRenderableWidget(visible);

				Button expand = Button.builder(Component.literal(expanded ? "▲" : "▼"), button -> {
					if (!expandedRows.add(entry.id())) {
						expandedRows.remove(entry.id());
					}
					rebuildWidgets();
				}).bounds(innerRight - 18, rowY + 1, 16, 17).build();
				expand.setTooltip(Tooltip.create(Component.translatable("gui.vanilla-waypoints.coordinates.toggle")));
				addRenderableWidget(expand);
			}
		}
	}

	private void addWaypoint() {
		if (!nameValue.matches("[A-Za-z0-9_-]{1,32}")) {
			status = Component.translatable("gui.vanilla-waypoints.invalid_name");
			statusColor = 0xFF5555;
			return;
		}

		BlockPos position = BlockPos.ZERO;
		if (customCoordinates) {
			try {
				position = new BlockPos(Integer.parseInt(xValue), Integer.parseInt(yValue), Integer.parseInt(zValue));
			} catch (NumberFormatException exception) {
				status = Component.translatable("gui.vanilla-waypoints.invalid_coordinates");
				statusColor = 0xFF5555;
				return;
			}
		}

		ClientPlayNetworking.send(new WaypointPayloads.Add(nameValue, COLORS[colorIndex], customCoordinates, position));
		status = Component.translatable("gui.vanilla-waypoints.added", nameValue);
		statusColor = 0x55FF55;
		nameValue = "";
		rebuildWidgets();
	}

	private void cycleEntryColor(WaypointPayloads.Entry entry) {
		int current = 0;
		for (int index = 0; index < COLORS.length; index++) {
			if (COLORS[index] == entry.color()) {
				current = index;
				break;
			}
		}
		sendAction(entry.id(), WaypointPayloads.Action.SET_COLOR, COLORS[(current + 1) % COLORS.length]);
	}

	private void sendAction(UUID id, int action, int value) {
		ClientPlayNetworking.send(new WaypointPayloads.Action(id, action, value));
	}

	private int deathDisplayNumber(WaypointPayloads.Entry selected) {
		int number = 0;
		for (WaypointPayloads.Entry entry : entries) {
			if (entry.death()) {
				number++;
				if (entry.id().equals(selected.id())) {
					return number;
				}
			}
		}
		return number;
	}

	private void updateAddButton() {
		if (addButton != null) {
			addButton.active = nameValue.matches("[A-Za-z0-9_-]{1,32}");
		}
	}

	private boolean coordinatesAreInitial() {
		return xValue.equals("0") && yValue.equals("0") && zValue.equals("0");
	}

	private static Component coloredSquare(int color) {
		return Component.literal("■").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
	}

	private static Tooltip colorTooltip(int index) {
		return Tooltip.create(Component.translatable(
				"gui.vanilla-waypoints.color.current",
				Component.translatable("gui.vanilla-waypoints.color." + COLOR_NAMES[index])
		));
	}

	@Override
	public void tick() {
		super.tick();
		int revision = WaypointClientState.revision();
		if (revision != knownRevision) {
			entries = WaypointClientState.entries();
			knownRevision = revision;
			rebuildWidgets();
		}
	}

	public static boolean closeIfOpen() {
		if (activeScreen == null) {
			return false;
		}
		if (!(activeScreen.getFocused() instanceof EditBox)) {
			activeScreen.onClose();
		}
		return true;
	}

	@Override
	public void added() {
		super.added();
		activeScreen = this;
	}

	@Override
	public void removed() {
		if (activeScreen == this) {
			activeScreen = null;
		}
		super.removed();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (VanillaWaypointsClient.matchesOpenMenu(event) && !(getFocused() instanceof EditBox)) {
			VanillaWaypointsClient.clearPendingOpenMenuClicks();
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= listTop && mouseY < listBottom) {
			int visibleRows = visibleRowCount();
			int maxOffset = Math.max(0, entries.size() - visibleRows);
			int previous = scrollOffset;
			if (scrollY < 0) {
				scrollOffset = Math.min(maxOffset, scrollOffset + 1);
			} else if (scrollY > 0) {
				scrollOffset = Math.max(0, scrollOffset - 1);
			}
			if (scrollOffset != previous) {
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xA0000000);
		fillContainerPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		fillRecessedArea(graphics, panelX + 8, listTop - 3, panelWidth - 16, listBottom - listTop + 3);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.text(font, title, panelX + 12, panelY + 10, 0x404040);

		if (entries.isEmpty()) {
			graphics.centeredText(font, Component.translatable("gui.vanilla-waypoints.empty"),
					panelX + panelWidth / 2, listTop + 12, 0xE0E0E0);
		}

		if (!status.getString().isEmpty()) {
			graphics.text(font, status, panelX + 12, panelY + panelHeight - 15, statusColor);
		}

		int visibleRows = visibleRowCount();
		if (entries.size() > visibleRows) {
			graphics.text(font, Component.literal((scrollOffset + 1) + "–" + Math.min(entries.size(), scrollOffset + visibleRows)
					+ "/" + entries.size()), panelX + panelWidth - 54, panelY + panelHeight - 15, 0x555555);
			drawScrollbar(graphics, visibleRows);
		}
	}

	private int visibleRowCount() {
		return MAX_VISIBLE_ROWS;
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int visibleRows) {
		int trackX = panelX + panelWidth - 7;
		int trackHeight = listBottom - listTop - 4;
		int thumbHeight = Math.max(8, trackHeight * visibleRows / entries.size());
		int maxOffset = entries.size() - visibleRows;
		int travel = trackHeight - thumbHeight;
		int thumbY = listTop + 2 + (maxOffset == 0 ? 0 : travel * scrollOffset / maxOffset);
		graphics.fill(trackX, listTop + 2, trackX + 2, listBottom - 2, 0xFF555555);
		graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFD8D8D8);
	}

	private static Component dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> Component.translatable("gui.vanilla-waypoints.dimension.overworld");
			case "minecraft:the_nether" -> Component.translatable("gui.vanilla-waypoints.dimension.nether");
			case "minecraft:the_end" -> Component.translatable("gui.vanilla-waypoints.dimension.end");
			default -> {
				int separator = dimension.indexOf(':');
				yield Component.literal(separator >= 0 ? dimension.substring(separator + 1) : dimension);
			}
		};
	}

	private static void fillContainerPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		int right = x + width;
		int bottom = y + height;

		graphics.fill(x + 2, y, right - 2, bottom, 0xFF202020);
		graphics.fill(x, y + 2, right, bottom - 2, 0xFF202020);
		graphics.fill(x + 1, y + 2, right - 1, bottom - 3, 0xFFC6C6C6);
		graphics.fill(x + 2, y + 1, right - 2, bottom - 1, 0xFFC6C6C6);

		graphics.fill(x + 2, y + 1, right - 2, y + 2, 0xFFFFFFFF);
		graphics.fill(x + 1, y + 2, x + 2, bottom - 2, 0xFFFFFFFF);
		graphics.fill(x + 2, bottom - 2, right - 2, bottom - 1, 0xFF555555);
		graphics.fill(right - 2, y + 2, right - 1, bottom - 2, 0xFF555555);
	}

	private static void fillRecessedArea(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x + 2, y, right - 2, bottom, 0xFF373737);
		graphics.fill(x, y + 2, right, bottom - 2, 0xFF373737);
		graphics.fill(x + 2, y + 2, right - 2, bottom - 2, 0xFF8B8B8B);
		graphics.fill(x + 1, y + 2, x + 2, bottom - 2, 0xFF555555);
		graphics.fill(x + 2, y + 1, right - 2, y + 2, 0xFF555555);
		graphics.fill(right - 2, y + 2, right - 1, bottom - 2, 0xFFFFFFFF);
		graphics.fill(x + 2, bottom - 2, right - 2, bottom - 1, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record RowArea(WaypointPayloads.Entry entry, int x, int y, int width, int height) {
	}

}
