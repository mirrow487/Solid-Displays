package com.soliddisplays.client.screen;

import com.soliddisplays.client.editor.EditorState;
import com.soliddisplays.client.render.DesignViewRenderState;
import com.soliddisplays.client.render.Overlay;
import com.soliddisplays.client.render.ViewCamera;
import com.soliddisplays.config.SDConfig;
import com.soliddisplays.item.BlueprintItem;
import com.soliddisplays.menu.DesignTableMenu;
import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.ModelGeometry;
import com.soliddisplays.model.ModelValidator;
import com.soliddisplays.network.SaveBlueprintPayload;
import com.soliddisplays.network.UpdateDraftPayload;
import com.soliddisplays.registry.ModItems;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The Design Table editor (Axiom-style).
 * <pre>
 * +-----------+--------------------------------+-------------+
 * | search    | [snap] [move] [scale] [reset]  | [Part][Save]|
 * | block grid|                                |  fields /   |
 * |-----------|          3D viewport           |  inventory  |
 * | parts     |                                |             |
 * +-----------+--------------------------------+-------------+
 * </pre>
 * Mouse in the viewport: left click selects a part or grabs a gizmo handle,
 * left/right drag on empty space orbits, middle drag (or shift + drag) pans,
 * wheel zooms. Keys: Ctrl+Z / Ctrl+Y undo/redo, Ctrl+D duplicate, Delete.
 * <p>
 * The container part (blueprint slot + inventory) is only shown on the Save
 * tab; its position is set through {@code leftPos/topPos}.
 */
public class DesignTableScreen extends AbstractContainerScreen<DesignTableMenu> {
	// ---- layout constants (GUI pixels) ----
	private static final int TOOLBAR_H = 22;
	/** Size of the vanilla-style inventory panel (blueprint slot + player inventory). */
	private static final int INV_W = 176;
	private static final int INV_H = 132;
	private static final int CELL = 18;
	private static final int ROW_H = 18;
	/** Y of the position fields; size and rotation rows follow every ROW_STEP pixels. */
	private static final int POS_Y = 46;
	private static final int ROW_STEP = 28;

	// ---- colours: light "blueprint" theme (pale blues + yellow, few dark tones) ----
	private static final int BG = 0xFFDDE7F3;
	private static final int PANEL = 0xFFEEF3FA;
	private static final int PANEL_LIGHT = 0xFFD3E2F3;
	private static final int TOOLBAR = 0xFFFFEDB3;
	private static final int WELL = 0xFFFAFCFF;
	private static final int WELL_BORDER = 0xFFB5C8E0;
	private static final int SLOT_BG = 0xFFA9BDD6;
	/** The 3D view is blueprint blue; its grid lines are white. */
	private static final int VIEW_BG = 0xFF3D73B8;
	private static final int VIEW_TEXT = 0xFFEAF2FC;
	private static final int TEXT = 0xFF1E3552;
	private static final int TEXT_DIM = 0xFF5B7598;
	/** Yellow for things drawn on the blue view (selection outline). */
	private static final int ACCENT = 0xFFFFD24A;
	/** Darker amber for headings on the light panels (readable). */
	private static final int ACCENT_TEXT = 0xFFB07600;
	private static final int OK = 0xFF2E8B45;
	private static final int ERROR = 0xFFD33A3F;
	/** Gizmo colours: bright so they stand out on the blue view (X red, Y green, Z cyan). */
	private static final int[] AXIS_COLORS = {0xFFFF5A5F, 0xFF5BE07A, 0xFF7FE3FF};
	/** The same axes as darker tones for the X/Y/Z labels on the light panel. */
	private static final int[] AXIS_LABEL_COLORS = {0xFFC62828, 0xFF2E7D32, 0xFF1565C0};
	private static final int HOVER = 0xFFFFFFFF;

	private enum Tab { PART, SAVE }

	private enum GizmoMode { MOVE, SCALE }

	private enum Drag { NONE, ORBIT, PAN, HANDLE }

	/** A gizmo handle: which axis (0..2) and, for scale handles, which side (±1). */
	private record Handle(int axis, int sign) {
	}

	private final EditorState state;
	private final ViewCamera camera = new ViewCamera();
	private Tab tab = Tab.PART;
	private GizmoMode gizmoMode = GizmoMode.MOVE;

	// picker
	private final List<PickerEntry> allEntries = new ArrayList<>();
	private final List<PickerEntry> filtered = new ArrayList<>();
	private int pickerScroll;
	private int listScroll;

	// widgets
	private EditBox searchBox;
	private EditBox nameBox;
	private final EditBox[] posFields = new EditBox[3];
	private final EditBox[] sizeFields = new EditBox[3];
	private final EditBox[] rotFields = new EditBox[3];
	private final List<net.minecraft.client.gui.components.AbstractWidget> partTabWidgets = new ArrayList<>();
	private final List<net.minecraft.client.gui.components.AbstractWidget> saveTabWidgets = new ArrayList<>();
	private Button snapButton;
	private Button moveButton;
	private Button scaleButton;
	private Button collisionButton;
	private Button partTabButton;
	private Button saveTabButton;
	private Button undoButton;
	private Button redoButton;

	/** True while fields are being filled from the state (their responders must not write back). */
	private boolean syncingFields;
	/** True once a checkpoint was made for the current burst of typing. */
	private boolean typingCheckpoint;
	/** The part the fields currently show; when the selection changes they are refilled. */
	private ModelElement shownInFields;

	// dragging
	private Drag drag = Drag.NONE;
	private Handle dragHandle;
	private ModelElement dragStartElement;
	private double dragStartX;
	private double dragStartY;
	private Handle hoveredHandle;

	/** Status line under the save button. */
	private Component status = Component.empty();
	private int statusColor = TEXT_DIM;
	private boolean awaitingSave;

	// computed layout (adapts to the window: 1080p with GUI scale 4 is only 480x270)
	private int leftW;
	private int rightW;
	private int fieldW;
	private int viewX0;
	private int viewY0;
	private int viewX1;
	private int viewY1;
	private int pickerCols;
	private int pickerRows;
	private int pickerY;
	private int listY0;
	private int rightX;

	public DesignTableScreen(DesignTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 132);
		this.state = new EditorState(menu.getInitialDraft());
		buildPickerEntries();
		if (state.size() > 0) {
			frameModel();
		}
	}

	// =====================================================================
	// Layout & widgets
	// =====================================================================

	@Override
	protected void init() {
		super.init();
		partTabWidgets.clear();
		saveTabWidgets.clear();

		leftW = Math.clamp(Math.round(width * 0.26f), 104, 150);
		rightW = Math.clamp(Math.round(width * 0.32f), 140, 190);
		fieldW = (rightW - 8 - 3 * 10) / 3;
		viewX0 = leftW;
		viewY0 = TOOLBAR_H;
		viewX1 = width - rightW;
		viewY1 = height;
		rightX = width - rightW;

		// ---- left: search + picker + part list ----
		searchBox = new EditBox(font, 4, 4, leftW - 8, 14, Component.translatable("gui.solid_displays.search"));
		searchBox.setHint(Component.translatable("gui.solid_displays.search"));
		searchBox.setResponder(s -> {
			pickerScroll = 0;
			applyFilter();
		});
		addRenderableWidget(searchBox);
		pickerCols = (leftW - 8) / CELL;
		pickerY = 22;
		pickerRows = Math.max(2, (int) ((height - 26) * 0.42f) / CELL);
		int buttonsY = pickerY + pickerRows * CELL + 14;
		int bw = (leftW - 8 - 2) / 2;
		addRenderableWidget(small(4, buttonsY, bw, "gui.solid_displays.duplicate", "gui.solid_displays.duplicate.tip",
				b -> { state.duplicateSelected(); afterStructureChange(); }));
		addRenderableWidget(small(6 + bw, buttonsY, bw, "gui.solid_displays.delete", "gui.solid_displays.delete.tip",
				b -> { state.deleteSelected(); afterStructureChange(); }));
		undoButton = addRenderableWidget(small(4, buttonsY + 16, bw, "gui.solid_displays.undo", "gui.solid_displays.undo.tip",
				b -> { state.undo(); afterStructureChange(); }));
		redoButton = addRenderableWidget(small(6 + bw, buttonsY + 16, bw, "gui.solid_displays.redo", "gui.solid_displays.redo.tip",
				b -> { state.redo(); afterStructureChange(); }));
		listY0 = buttonsY + 34;

		// ---- toolbar over the viewport (buttons shrink on small screens) ----
		int tw = Math.clamp((viewX1 - viewX0 - 8 - 18 - 8) / 4, 30, 60);
		int tx = viewX0 + 4;
		snapButton = addRenderableWidget(Button.builder(snapLabel(), b -> {
			state.cycleSnap();
			b.setMessage(snapLabel());
		}).bounds(tx, 3, tw, 16).tooltip(Tooltip.create(Component.translatable("gui.solid_displays.snap.tip"))).build());
		tx += tw + 2;
		moveButton = addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.mode.move"),
				b -> gizmoMode = GizmoMode.MOVE).bounds(tx, 3, tw, 16)
				.tooltip(Tooltip.create(Component.translatable("gui.solid_displays.mode.move.tip"))).build());
		tx += tw + 2;
		scaleButton = addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.mode.scale"),
				b -> gizmoMode = GizmoMode.SCALE).bounds(tx, 3, tw, 16)
				.tooltip(Tooltip.create(Component.translatable("gui.solid_displays.mode.scale.tip"))).build());
		tx += tw + 2;
		addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.view.reset"), b -> frameModel())
				.bounds(tx, 3, tw, 16).tooltip(Tooltip.create(Component.translatable("gui.solid_displays.view.reset.tip"))).build());
		tx += tw + 2;
		addRenderableWidget(Button.builder(Component.literal("?"), b -> { })
				.bounds(tx, 3, 16, 16).tooltip(Tooltip.create(Component.translatable("gui.solid_displays.view.help"))).build());

		// ---- right: tabs ----
		partTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.tab.part"),
				b -> setTab(Tab.PART)).bounds(rightX + 4, 3, (rightW - 10) / 2, 16).build());
		saveTabButton = addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.tab.save"),
				b -> setTab(Tab.SAVE)).bounds(rightX + 6 + (rightW - 10) / 2, 3, (rightW - 10) / 2, 16).build());

		// Part tab: three rows of X/Y/Z fields
		createVectorFields(posFields, POS_Y, (e, v) -> e.withPosition(v));
		createVectorFields(sizeFields, POS_Y + ROW_STEP, (e, v) -> e.withSize(new Vector3f(
				Math.max(SDConfig.get().minElementSize, v.x), Math.max(SDConfig.get().minElementSize, v.y),
				Math.max(SDConfig.get().minElementSize, v.z))));
		createVectorFields(rotFields, POS_Y + 2 * ROW_STEP, (e, v) -> e.withRotation(v));
		int qy = POS_Y + 3 * ROW_STEP - 8;
		int qw = (rightW - 8 - 6) / 4;
		String[] quick = {"X", "Y", "Z"};
		for (int axis = 0; axis < 3; axis++) {
			int a = axis;
			partTabWidgets.add(addRenderableWidget(Button.builder(Component.literal(quick[axis] + "+90"), b -> rotateSelected(a, 90))
					.bounds(rightX + 4 + axis * (qw + 2), qy, qw, 14)
					.tooltip(Tooltip.create(Component.translatable("gui.solid_displays.rotation.quick", quick[axis]))).build()));
		}
		partTabWidgets.add(addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.rotation.reset"), b -> {
			ModelElement e = state.selected();
			if (e != null) {
				state.checkpoint();
				state.updateSelected(e.withRotation(new Vector3f()));
				syncFields();
			}
		}).bounds(rightX + 4 + 3 * (qw + 2), qy, qw, 14).build()));
		collisionButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			ModelElement e = state.selected();
			if (e != null) {
				state.checkpoint();
				state.updateSelected(e.withCollision(!e.collision()));
				syncFields();
			}
		}).bounds(rightX + 4, qy + 17, rightW - 8, 16).tooltip(Tooltip.create(Component.translatable("gui.solid_displays.collision.tip"))).build());
		partTabWidgets.add(collisionButton);

		// Save tab
		nameBox = new EditBox(font, rightX + 4, 34, rightW - 8, 16, Component.translatable("gui.solid_displays.name"));
		nameBox.setMaxLength(ModelData.MAX_NAME_LENGTH);
		nameBox.setHint(Component.translatable("gui.solid_displays.name.hint"));
		nameBox.setValue(state.name());
		nameBox.setResponder(state::setName);
		saveTabWidgets.add(addRenderableWidget(nameBox));
		int half = (rightW - 10) / 2;
		saveTabWidgets.add(addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.save"), b -> save())
				.bounds(rightX + 4, 54, half, 18)
				.tooltip(Tooltip.create(Component.translatable("gui.solid_displays.save.tip"))).build()));
		saveTabWidgets.add(addRenderableWidget(Button.builder(Component.translatable("gui.solid_displays.load"), b -> loadFromBlueprint())
				.bounds(rightX + 6 + half, 54, half, 18)
				.tooltip(Tooltip.create(Component.translatable("gui.solid_displays.load.tip"))).build()));

		// The inventory panel (Save tab) floats over the bottom-right corner of the viewport.
		leftPos = width - INV_W - 4;
		topPos = Math.max(98, height - INV_H - 4);

		applyFilter();
		setTab(tab);
		syncFields();
	}

	private Button small(int x, int y, int w, String key, String tipKey, Button.OnPress onPress) {
		return Button.builder(Component.translatable(key), onPress).bounds(x, y, w, 14)
				.tooltip(Tooltip.create(Component.translatable(tipKey))).build();
	}

	/** Creates one X/Y/Z row of number fields that writes through {@code apply}. */
	private void createVectorFields(EditBox[] fields, int y,
			BiFunction<ModelElement, Vector3f, ModelElement> apply) {
		for (int axis = 0; axis < 3; axis++) {
			EditBox box = new EditBox(font, rightX + 12 + axis * (fieldW + 10), y, fieldW, 14, Component.literal("xyz".substring(axis, axis + 1)));
			box.setMaxLength(10);
			box.setResponder(text -> {
				if (syncingFields) {
					return;
				}
				ModelElement e = state.selected();
				Optional<Vector3f> v = parseVector(fields);
				if (e == null || v.isEmpty()) {
					return;
				}
				if (!typingCheckpoint) {
					state.checkpoint();
					typingCheckpoint = true;
				}
				state.updateSelected(apply.apply(e, v.get()));
			});
			fields[axis] = box;
			partTabWidgets.add(addRenderableWidget(box));
		}
	}

	private static Optional<Vector3f> parseVector(EditBox[] fields) {
		try {
			float[] v = new float[3];
			for (int i = 0; i < 3; i++) {
				v[i] = Float.parseFloat(fields[i].getValue().trim().replace(',', '.'));
				if (!Float.isFinite(v[i])) {
					return Optional.empty();
				}
			}
			return Optional.of(new Vector3f(v[0], v[1], v[2]));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private void setTab(Tab newTab) {
		tab = newTab;
		partTabWidgets.forEach(w -> w.visible = tab == Tab.PART);
		saveTabWidgets.forEach(w -> w.visible = tab == Tab.SAVE);
		partTabButton.active = tab != Tab.PART;
		saveTabButton.active = tab != Tab.SAVE;
		menu.setSlotsVisible(tab == Tab.SAVE);
		setFocused(null);
	}

	private Component snapLabel() {
		return Component.translatable("gui.solid_displays.snap", state.snapLabel());
	}

	/** Fills the number fields from the selected part (without triggering their responders). */
	private void syncFields() {
		syncingFields = true;
		typingCheckpoint = false;
		ModelElement e = state.selected();
		shownInFields = e;
		for (int i = 0; i < 3; i++) {
			setField(posFields[i], e == null ? null : component(e.position(), i));
			setField(sizeFields[i], e == null ? null : component(e.size(), i));
			setField(rotFields[i], e == null ? null : component(e.rotation(), i));
		}
		if (collisionButton != null) {
			collisionButton.active = e != null;
			collisionButton.setMessage(Component.translatable(e == null || e.collision()
					? "gui.solid_displays.collision.on" : "gui.solid_displays.collision.off"));
		}
		syncingFields = false;
	}

	private boolean isPartField(EditBox box) {
		for (int i = 0; i < 3; i++) {
			if (box == posFields[i] || box == sizeFields[i] || box == rotFields[i]) {
				return true;
			}
		}
		return false;
	}

	private static void setField(EditBox box, Float value) {
		if (box == null) {
			return;
		}
		box.active = value != null;
		box.setEditable(value != null);
		box.setValue(value == null ? "" : format(value));
	}

	private static float component(Vector3fc v, int i) {
		return i == 0 ? v.x() : i == 1 ? v.y() : v.z();
	}

	/** "0.125", "1", "-2.5" – no trailing zeros, at most 4 decimals. */
	static String format(float v) {
		BigDecimal d = new BigDecimal(String.format(Locale.ROOT, "%.4f", v)).stripTrailingZeros();
		String s = d.toPlainString();
		return s.equals("-0") ? "0" : s;
	}

	private void afterStructureChange() {
		syncFields();
		clampListScroll();
	}

	// =====================================================================
	// Picker
	// =====================================================================

	/** One pickable block or item. */
	private record PickerEntry(ElementKind kind, Identifier id, ItemStack icon, String search) {
	}

	private void buildPickerEntries() {
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR) {
				continue;
			}
			ElementKind kind;
			Identifier id;
			if (item instanceof BlockItem blockItem) {
				kind = ElementKind.BLOCK;
				id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
			} else {
				kind = ElementKind.ITEM;
				id = BuiltInRegistries.ITEM.getKey(item);
			}
			if (!ModelValidator.isAllowed(kind, id)) {
				continue;
			}
			ItemStack icon = new ItemStack(item);
			String search = (icon.getHoverName().getString() + " " + id).toLowerCase(Locale.ROOT);
			allEntries.add(new PickerEntry(kind, id, icon, search));
		}
	}

	private void applyFilter() {
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		filtered.clear();
		for (PickerEntry e : allEntries) {
			if (q.isEmpty() || e.search().contains(q)) {
				filtered.add(e);
			}
		}
	}

	private int pickerIndexAt(double mx, double my) {
		if (mx < 4 || mx >= 4 + pickerCols * CELL || my < pickerY || my >= pickerY + pickerRows * CELL) {
			return -1;
		}
		int col = (int) ((mx - 4) / CELL);
		int row = (int) ((my - pickerY) / CELL);
		int index = (pickerScroll + row) * pickerCols + col;
		return index < filtered.size() ? index : -1;
	}

	private int maxPickerScroll() {
		int rows = (filtered.size() + pickerCols - 1) / Math.max(1, pickerCols);
		return Math.max(0, rows - pickerRows);
	}

	// =====================================================================
	// Part list
	// =====================================================================

	private int listRows() {
		return Math.max(1, (height - 4 - listY0) / ROW_H);
	}

	private void clampListScroll() {
		listScroll = Math.clamp(listScroll, 0, Math.max(0, state.size() - listRows()));
		int sel = state.selectedIndex();
		if (sel >= 0) {
			if (sel < listScroll) {
				listScroll = sel;
			} else if (sel >= listScroll + listRows()) {
				listScroll = sel - listRows() + 1;
			}
		}
	}

	private int listIndexAt(double mx, double my) {
		if (mx < 4 || mx >= leftW - 4 || my < listY0 || my >= listY0 + listRows() * ROW_H) {
			return -1;
		}
		int index = listScroll + (int) ((my - listY0) / ROW_H);
		return index < state.size() ? index : -1;
	}

	private static ItemStack iconOf(ModelElement e) {
		return new ItemStack(MaterialCalculator.itemFor(e));
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// Selection or part changed by something other than the fields (click, undo, drag…): refill them,
		// unless the player is typing into one right now.
		if (state.selected() != shownInFields && !(getFocused() instanceof EditBox box && box.isFocused() && isPartField(box))) {
			syncFields();
		}
		g.fill(0, 0, width, height, BG);
		g.fill(0, 0, leftW, height, PANEL);
		g.fill(rightX, 0, width, height, PANEL);
		g.fill(viewX0, 0, viewX1, TOOLBAR_H, TOOLBAR);
		g.fill(viewX0, viewY0, viewX1, viewY1, VIEW_BG);

		extractViewport(g);
		extractPicker(g, mouseX, mouseY);
		extractPartList(g, mouseX, mouseY);
		if (tab == Tab.PART) {
			extractPartTab(g);
		} else {
			extractSaveTab(g);
		}
		moveButton.active = gizmoMode != GizmoMode.MOVE;
		scaleButton.active = gizmoMode != GizmoMode.SCALE;
		undoButton.active = state.canUndo();
		redoButton.active = state.canRedo();
	}

	private void extractViewport(GuiGraphicsExtractor g) {
		List<Overlay.Shape> gizmo = buildGizmo();
		int radius = (int) Math.ceil(SDConfig.get().maxModelRadius);
		g.guiRenderState.addPicturesInPictureState(new DesignViewRenderState(state.elements(), state.selectedIndex(),
				camera.view(), gizmo, Math.min(radius, 16), viewX0, viewY0, viewX1, viewY1, g.scissorStack.peek()));
		if (state.size() == 0) {
			g.textWithWordWrap(font, Component.translatable("gui.solid_displays.empty_hint"),
					viewX0 + 6, viewY0 + 8, viewX1 - viewX0 - 12, VIEW_TEXT, false);
		}
	}

	private void extractPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.fill(3, pickerY - 1, 5 + pickerCols * CELL, pickerY + pickerRows * CELL + 1, WELL_BORDER);
		g.fill(4, pickerY, 4 + pickerCols * CELL, pickerY + pickerRows * CELL, WELL);
		int hovered = pickerIndexAt(mouseX, mouseY);
		for (int row = 0; row < pickerRows; row++) {
			for (int col = 0; col < pickerCols; col++) {
				int index = (pickerScroll + row) * pickerCols + col;
				if (index >= filtered.size()) {
					break;
				}
				int x = 4 + col * CELL;
				int y = pickerY + row * CELL;
				if (index == hovered) {
					g.fill(x, y, x + CELL, y + CELL, 0x503D73B8);
				}
				g.item(filtered.get(index).icon(), x + 1, y + 1);
			}
		}
		if (hovered >= 0) {
			PickerEntry entry = filtered.get(hovered);
			List<Component> lines = List.of(entry.icon().getHoverName(),
					Component.translatable(entry.kind() == ElementKind.BLOCK ? "gui.solid_displays.kind.block" : "gui.solid_displays.kind.item")
							.withColor(TEXT_DIM),
					Component.translatable("gui.solid_displays.picker.click").withColor(TEXT_DIM));
			g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
		g.text(font, Component.translatable("gui.solid_displays.parts", state.size(), SDConfig.get().maxElements),
				4, pickerY + pickerRows * CELL + 4, TEXT, false);
	}

	private void extractPartList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int rows = listRows();
		g.fill(3, listY0 - 1, leftW - 3, listY0 + rows * ROW_H + 1, WELL_BORDER);
		g.fill(4, listY0, leftW - 4, listY0 + rows * ROW_H, WELL);
		int hovered = listIndexAt(mouseX, mouseY);
		List<ModelElement> elements = state.elements();
		for (int r = 0; r < rows; r++) {
			int index = listScroll + r;
			if (index >= elements.size()) {
				break;
			}
			ModelElement e = elements.get(index);
			int y = listY0 + r * ROW_H;
			if (index == state.selectedIndex()) {
				g.fill(4, y, leftW - 4, y + ROW_H, 0x90FFD24A);
			} else if (index == hovered) {
				g.fill(4, y, leftW - 4, y + ROW_H, 0x303D73B8);
			}
			ItemStack icon = iconOf(e);
			g.item(icon, 5, y + 1);
			String label = (index + 1) + ". " + icon.getHoverName().getString();
			g.text(font, font.plainSubstrByWidth(label, leftW - 40), 24, y + 5, TEXT, false);
			// small square: filled = collision on
			int cx = leftW - 12;
			g.fill(cx, y + 6, cx + 6, y + 12, e.collision() ? OK : 0xFFB7C3D2);
		}
	}

	private void extractPartTab(GuiGraphicsExtractor g) {
		ModelElement e = state.selected();
		Component title = e == null ? Component.translatable("gui.solid_displays.no_selection")
				: iconOf(e).getHoverName();
		g.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(title, rightW - 8)), rightX + 4, 24, e == null ? TEXT_DIM : ACCENT_TEXT, false);
		String[] labels = {"gui.solid_displays.position", "gui.solid_displays.size", "gui.solid_displays.rotation"};
		for (int row = 0; row < 3; row++) {
			int fy = POS_Y + row * ROW_STEP;
			g.text(font, Component.translatable(labels[row]), rightX + 4, fy - 10, TEXT_DIM, false);
			for (int axis = 0; axis < 3; axis++) {
				g.text(font, "XYZ".substring(axis, axis + 1), rightX + 5 + axis * (fieldW + 10), fy + 3, AXIS_LABEL_COLORS[axis], false);
			}
		}

		// Total cost of the whole model, as survival would charge it.
		int y = POS_Y + 3 * ROW_STEP + 30;
		g.text(font, Component.translatable("gui.solid_displays.cost"), rightX + 4, y, TEXT_DIM, false);
		y += 12;
		List<ItemStack> cost = MaterialCalculator.cost(state.toModel());
		int perRow = (rightW - 8) / 44;
		for (int i = 0; i < cost.size(); i++) {
			int x = rightX + 4 + (i % perRow) * 44;
			int yy = y + (i / perRow) * 18;
			if (yy > height - 18) {
				g.text(font, "…", x, yy + 4, TEXT_DIM, false);
				break;
			}
			g.item(cost.get(i).copyWithCount(1), x, yy);
			g.text(font, "×" + cost.get(i).getCount(), x + 17, yy + 5, TEXT, false);
		}
	}

	private void extractSaveTab(GuiGraphicsExtractor g) {
		g.text(font, Component.translatable("gui.solid_displays.name"), rightX + 4, 24, TEXT_DIM, false);
		g.textWithWordWrap(font, status, rightX + 4, 76, rightW - 8, statusColor, false);
		// Slot backgrounds for the blueprint slot and inventory, above the 3D view.
		g.nextStratum();
		g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, WELL_BORDER);
		g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_LIGHT);
		for (Slot slot : menu.slots) {
			int x = leftPos + slot.x;
			int y = topPos + slot.y;
			g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BG);
			g.fill(x, y, x + 16, y + 16, 0xFFC9D8EA);
		}
		ItemStack bp = menu.getBlueprint();
		Component slotHint = bp.isEmpty() ? Component.translatable("gui.solid_displays.slot.empty")
				: bp.is(ModItems.EMPTY_BLUEPRINT) ? Component.translatable("gui.solid_displays.slot.blank")
				: Component.translatable("gui.solid_displays.slot.filled", BlueprintItem.getModel(bp).name());
		g.textWithWordWrap(font, slotHint, leftPos + DesignTableMenu.SLOT_X + 22, topPos + DesignTableMenu.SLOT_Y - 2,
				imageWidth - DesignTableMenu.SLOT_X - 26, TEXT, false);

		if (awaitingSave && bp.is(ModItems.BLUEPRINT) && BlueprintItem.getModel(bp).equals(saveRequest())) {
			awaitingSave = false;
			setStatus(Component.translatable("gui.solid_displays.status.saved"), OK);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (tab == Tab.SAVE) {
			g.text(font, playerInventoryTitle, 8, DesignTableMenu.INVENTORY_Y - 10, TEXT_DIM, false);
		}
	}

	// =====================================================================
	// Gizmo
	// =====================================================================

	/** Outline of the selected part plus its move or scale handles, in view space. */
	private List<Overlay.Shape> buildGizmo() {
		List<Overlay.Shape> out = new ArrayList<>();
		ModelElement e = state.selected();
		if (e == null) {
			return out;
		}
		Matrix4f view = camera.view();
		Vector3f[] corners = corners(e);
		Vector3f[] viewCorners = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			viewCorners[i] = view.transformPosition(new Vector3f(corners[i]));
		}
		Overlay.boxEdges(viewCorners, 1.5f, ACCENT, out);

		Vector3f center = view.transformPosition(ModelGeometry.localCenter(e));
		for (Handle h : handles(e)) {
			boolean hot = h.equals(hoveredHandle) || (drag == Drag.HANDLE && h.equals(dragHandle));
			int color = hot ? HOVER : AXIS_COLORS[h.axis()];
			Vector3f p = view.transformPosition(handlePosition(e, h));
			if (gizmoMode == GizmoMode.MOVE) {
				out.add(new Overlay.Line(center, p, hot ? 3.5f : 2.5f, color));
				out.add(new Overlay.Square(p, hot ? 9f : 7f, color));
			} else {
				out.add(new Overlay.Square(p, hot ? 9f : 7f, color));
			}
		}
		return out;
	}

	private List<Handle> handles(ModelElement e) {
		List<Handle> list = new ArrayList<>();
		for (int axis = 0; axis < 3; axis++) {
			list.add(new Handle(axis, 1));
			if (gizmoMode == GizmoMode.SCALE) {
				list.add(new Handle(axis, -1));
			}
		}
		return list;
	}

	/** Model-space position of a handle: arrow tips (move) or face centres (scale). */
	private Vector3f handlePosition(ModelElement e, Handle h) {
		Vector3f c = ModelGeometry.localCenter(e);
		if (gizmoMode == GizmoMode.MOVE) {
			float length = 42f / camera.zoom;
			Vector3f axis = new Vector3f();
			axis.setComponent(h.axis(), length);
			return c.add(axis);
		}
		return c.add(faceNormal(e, h).mul(component(e.size(), h.axis()) / 2f));
	}

	/** World direction of a scale handle: the part's own (rotated) axis. */
	private static Vector3f faceNormal(ModelElement e, Handle h) {
		Vector3f n = new Vector3f();
		n.setComponent(h.axis(), h.sign());
		return ModelGeometry.elementRotation(e).transform(n);
	}

	/** Model-space corners of a part's (rotated) box; index bits x=1, y=2, z=4. */
	private static Vector3f[] corners(ModelElement e) {
		Quaternionf rot = ModelGeometry.elementRotation(e);
		Vector3f c = ModelGeometry.localCenter(e);
		Vector3f half = new Vector3f(e.size()).mul(0.5f);
		Vector3f[] out = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			Vector3f p = new Vector3f((i & 1) == 0 ? -half.x : half.x, (i & 2) == 0 ? -half.y : half.y,
					(i & 4) == 0 ? -half.z : half.z);
			out[i] = rot.transform(p).add(c);
		}
		return out;
	}

	/** Viewport-centre offset of the mouse (GUI pixels). */
	private Vector2f viewOffset(double mx, double my) {
		return new Vector2f((float) (mx - (viewX0 + viewX1) / 2.0), (float) (my - (viewY0 + viewY1) / 2.0));
	}

	private boolean inViewport(double mx, double my) {
		return mx >= viewX0 && mx < viewX1 && my >= viewY0 && my < viewY1;
	}

	private Handle handleAt(double mx, double my) {
		ModelElement e = state.selected();
		if (e == null || !inViewport(mx, my)) {
			return null;
		}
		Matrix4f view = camera.view();
		Vector2f m = viewOffset(mx, my);
		Vector3f c = view.transformPosition(ModelGeometry.localCenter(e));
		Handle best = null;
		float bestDist = 7f;
		for (Handle h : handles(e)) {
			Vector3f p = view.transformPosition(handlePosition(e, h));
			float d = gizmoMode == GizmoMode.MOVE
					? distanceToSegment(m, new Vector2f(c.x, c.y), new Vector2f(p.x, p.y))
					: Math.max(Math.abs(m.x - p.x), Math.abs(m.y - p.y));
			if (d < bestDist) {
				bestDist = d;
				best = h;
			}
		}
		return best;
	}

	private static float distanceToSegment(Vector2f p, Vector2f a, Vector2f b) {
		Vector2f ab = new Vector2f(b).sub(a);
		float t = ab.lengthSquared() < 1e-6f ? 0f : Math.clamp(new Vector2f(p).sub(a).dot(ab) / ab.lengthSquared(), 0f, 1f);
		return new Vector2f(a).add(ab.mul(t)).distance(p);
	}

	/** Index of the frontmost part under the mouse, or -1. Exact ray/box test (works for rotated parts). */
	private int pickElement(double mx, double my) {
		Vector2f m = viewOffset(mx, my);
		Vector3f origin = camera.unproject(m.x, m.y, -100_000f);
		Vector3f dir = camera.forward();
		List<ModelElement> elements = state.elements();
		int best = -1;
		float bestT = Float.MAX_VALUE;
		for (int i = 0; i < elements.size(); i++) {
			ModelElement e = elements.get(i);
			// Into the part's local unit cube [-0.5, 0.5]^3.
			Matrix4f toLocal = new Matrix4f()
					.translate(ModelGeometry.localCenter(e))
					.rotate(ModelGeometry.elementRotation(e))
					.scale(e.size().x(), e.size().y(), e.size().z())
					.invert();
			Vector3f o = toLocal.transformPosition(new Vector3f(origin));
			Vector3f d = toLocal.transformDirection(new Vector3f(dir));
			float t = rayBox(o, d);
			if (t >= 0 && t < bestT) {
				bestT = t;
				best = i;
			}
		}
		return best;
	}

	/** Slab test against the centred unit cube; returns entry distance or -1. */
	private static float rayBox(Vector3f o, Vector3f d) {
		float tMin = -Float.MAX_VALUE;
		float tMax = Float.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			float oi = o.get(i);
			float di = d.get(i);
			if (Math.abs(di) < 1e-9f) {
				if (oi < -0.5f || oi > 0.5f) {
					return -1;
				}
				continue;
			}
			float t1 = (-0.5f - oi) / di;
			float t2 = (0.5f - oi) / di;
			tMin = Math.max(tMin, Math.min(t1, t2));
			tMax = Math.min(tMax, Math.max(t1, t2));
		}
		return tMax >= tMin && tMax >= 0 ? Math.max(tMin, 0) : -1;
	}

	/** Applies a handle drag (total mouse movement since the drag started). */
	private void dragHandleTo(double mx, double my) {
		ModelElement start = dragStartElement;
		Matrix4f view = camera.view();
		Vector2f mouse = new Vector2f((float) (mx - dragStartX), (float) (my - dragStartY));

		Vector3f worldDir = gizmoMode == GizmoMode.MOVE ? axisVector(dragHandle.axis()) : faceNormal(start, dragHandle);
		Vector3f screen3 = view.transformDirection(new Vector3f(worldDir));
		Vector2f screen = new Vector2f(screen3.x, screen3.y);
		if (screen.lengthSquared() < 1e-4f) {
			return; // axis points straight at the camera
		}
		float delta = mouse.dot(screen) / screen.lengthSquared();

		if (gizmoMode == GizmoMode.MOVE) {
			Vector3f p = new Vector3f(start.position());
			float value = state.snap(component(start.position(), dragHandle.axis()) + delta);
			p.setComponent(dragHandle.axis(), value);
			state.updateSelected(start.withPosition(p));
		} else {
			int a = dragHandle.axis();
			float oldSize = component(start.size(), a);
			float minSize = Math.max(SDConfig.get().minElementSize, Math.min(state.snap(), oldSize));
			float maxSize = SDConfig.get().maxElementSize;
			float newSize = Math.clamp(state.snap(oldSize + delta), minSize, maxSize);
			float grown = newSize - oldSize;
			// Keep the opposite face where it is: the centre moves half the growth.
			Vector3f center = ModelGeometry.localCenter(start).add(new Vector3f(worldDir).mul(grown / 2f));
			Vector3f size = new Vector3f(start.size());
			size.setComponent(a, newSize);
			Vector3f pos = new Vector3f(center).sub(new Vector3f(size).mul(0.5f));
			state.updateSelected(start.withSize(size).withPosition(pos));
		}
		syncFields();
	}

	private static Vector3f axisVector(int axis) {
		Vector3f v = new Vector3f();
		v.setComponent(axis, 1f);
		return v;
	}

	private void rotateSelected(int axis, float degrees) {
		ModelElement e = state.selected();
		if (e == null) {
			return;
		}
		state.checkpoint();
		Vector3f r = new Vector3f(e.rotation());
		float value = component(r, axis) + degrees;
		value = ((value + 180f) % 360f + 360f) % 360f - 180f; // keep in [-180, 180)
		r.setComponent(axis, value);
		state.updateSelected(e.withRotation(r));
		syncFields();
	}

	/** Centres the camera on the model and picks a zoom that fits it. */
	private void frameModel() {
		camera.reset();
		List<ModelElement> elements = state.elements();
		if (elements.isEmpty()) {
			return;
		}
		Vector3f min = new Vector3f(Float.MAX_VALUE);
		Vector3f max = new Vector3f(-Float.MAX_VALUE);
		for (ModelElement e : elements) {
			for (Vector3f c : corners(e)) {
				min.min(c);
				max.max(c);
			}
		}
		camera.target.set(min).add(max).mul(0.5f);
		float extent = Math.max(1f, new Vector3f(max).sub(min).length());
		float available = Math.max(60, Math.min(viewX1 - viewX0, viewY1 - viewY0) - 40);
		if (available > 60) {
			camera.zoom = Math.clamp(available / extent, 4f, 128f);
		}
	}

	// =====================================================================
	// Input
	// =====================================================================

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();

		// Widgets first (fields, buttons).
		for (var child : children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible && w.isMouseOver(mx, my)) {
				return super.mouseClicked(event, doubleClick);
			}
		}

		int picked = pickerIndexAt(mx, my);
		if (picked >= 0 && button == InputConstants.MOUSE_BUTTON_LEFT) {
			PickerEntry entry = filtered.get(picked);
			if (state.size() >= SDConfig.get().maxElements) {
				setStatus(Component.translatable("message.solid_displays.invalid.too_many", SDConfig.get().maxElements), ERROR);
				return true;
			}
			state.add(entry.kind(), entry.id());
			afterStructureChange();
			setFocused(null);
			return true;
		}

		int listed = listIndexAt(mx, my);
		if (listed >= 0) {
			state.select(listed);
			syncFields();
			setFocused(null);
			return true;
		}

		if (inViewport(mx, my)) {
			setFocused(null);
			dragStartX = mx;
			dragStartY = my;
			if (button == InputConstants.MOUSE_BUTTON_MIDDLE || (button == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown())) {
				drag = Drag.PAN;
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				Handle h = handleAt(mx, my);
				if (h != null) {
					state.checkpoint();
					drag = Drag.HANDLE;
					dragHandle = h;
					dragStartElement = state.selected();
					return true;
				}
				int hit = pickElement(mx, my);
				if (hit >= 0) {
					state.select(hit);
					syncFields();
					clampListScroll();
				}
				drag = Drag.ORBIT;
				return true;
			}
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				drag = Drag.ORBIT;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		switch (drag) {
			case ORBIT -> camera.orbit((float) dx, (float) dy);
			case PAN -> camera.pan((float) dx, (float) dy);
			case HANDLE -> dragHandleTo(event.x(), event.y());
			case NONE -> {
				return super.mouseDragged(event, dx, dy);
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (drag != Drag.NONE) {
			drag = Drag.NONE;
			dragHandle = null;
			dragStartElement = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double mx, double my) {
		hoveredHandle = drag == Drag.NONE ? handleAt(mx, my) : hoveredHandle;
		super.mouseMoved(mx, my);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		if (inViewport(mx, my)) {
			camera.zoom(scrollY);
			return true;
		}
		if (mx < leftW && my >= pickerY && my < pickerY + pickerRows * CELL) {
			pickerScroll = Math.clamp(pickerScroll - (int) Math.signum(scrollY), 0, maxPickerScroll());
			return true;
		}
		if (mx < leftW && my >= listY0) {
			listScroll = Math.clamp(listScroll - (int) Math.signum(scrollY), 0, Math.max(0, state.size() - listRows()));
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// While typing, every key goes to the text field (so "E" does not close the screen).
		if (getFocused() instanceof EditBox box && box.isFocused() && event.key() != InputConstants.KEY_ESCAPE) {
			if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_TAB) {
				typingCheckpoint = false;
				setFocused(null);
				syncFields();
				return true;
			}
			box.keyPressed(event);
			return true;
		}
		// Letter shortcuts use the layout-aware key code (works on QWERTY, AZERTY, Turkish F…).
		boolean ctrl = event.hasControlDownWithQuirk();
		int letter = Character.toLowerCase(event.shortcutKey());
		if (ctrl && letter == 'z' && !event.hasShiftDown()) {
			state.undo();
			afterStructureChange();
			return true;
		}
		if (ctrl && (letter == 'y' || (letter == 'z' && event.hasShiftDown()))) {
			state.redo();
			afterStructureChange();
			return true;
		}
		if (ctrl && letter == 'd') {
			state.duplicateSelected();
			afterStructureChange();
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE) {
			state.deleteSelected();
			afterStructureChange();
			return true;
		}
		if (!ctrl && letter == 'g') {
			gizmoMode = GizmoMode.MOVE;
			return true;
		}
		if (!ctrl && letter == 's') {
			gizmoMode = GizmoMode.SCALE;
			return true;
		}
		return super.keyPressed(event);
	}

	/** Clicking the empty viewport must not throw the carried item into the world. */
	@Override
	protected boolean hasClickedOutside(double mx, double my, int left, int top) {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// =====================================================================
	// Saving
	// =====================================================================

	private ModelData saveRequest() {
		return state.toModel().withName(state.name().strip());
	}

	private void save() {
		ModelData model = saveRequest();
		if (model.isEmpty()) {
			setStatus(Component.translatable("message.solid_displays.save.empty"), ERROR);
			return;
		}
		Optional<Component> error = ModelValidator.validate(model);
		if (error.isPresent()) {
			setStatus(error.get(), ERROR);
			return;
		}
		ItemStack bp = menu.getBlueprint();
		if (!bp.is(ModItems.EMPTY_BLUEPRINT) && !bp.is(ModItems.BLUEPRINT)) {
			setStatus(Component.translatable("message.solid_displays.save.no_blueprint"), ERROR);
			return;
		}
		ClientPlayNetworking.send(new SaveBlueprintPayload(menu.getTablePos(), model));
		awaitingSave = true;
		setStatus(Component.translatable("gui.solid_displays.status.saving"), TEXT_DIM);
	}

	/** Loads the design of the filled blueprint in the slot into the editor (undoable). */
	private void loadFromBlueprint() {
		ItemStack bp = menu.getBlueprint();
		ModelData model = BlueprintItem.getModel(bp);
		if (!bp.is(ModItems.BLUEPRINT) || model.isEmpty()) {
			setStatus(Component.translatable("gui.solid_displays.status.nothing_to_load"), ERROR);
			return;
		}
		state.load(model);
		nameBox.setValue(model.name());
		syncFields();
		clampListScroll();
		frameModel();
		setStatus(Component.translatable("gui.solid_displays.status.loaded", model.name()), OK);
	}

	private void setStatus(Component message, int color) {
		status = message;
		statusColor = color;
	}

	/** Keep the work in progress in the table. */
	@Override
	public void removed() {
		if (minecraft != null && minecraft.getConnection() != null) {
			ClientPlayNetworking.send(new UpdateDraftPayload(menu.getTablePos(), state.toModel()));
		}
		super.removed();
	}

	// ---- test hooks (used by the automated client tests) ----

	/** GUI position of a gizmo handle of the selected part (null if none). */
	public Vector2f handleScreenPosition(int axis, int sign, boolean scaleMode) {
		ModelElement e = state.selected();
		if (e == null) {
			return null;
		}
		gizmoMode = scaleMode ? GizmoMode.SCALE : GizmoMode.MOVE;
		Vector3f p = camera.view().transformPosition(handlePosition(e, new Handle(axis, sign)));
		return new Vector2f(p.x + (viewX0 + viewX1) / 2f, p.y + (viewY0 + viewY1) / 2f);
	}

	/** GUI position of the Z field of the position row. */
	public Vector2f positionZField() {
		EditBox box = posFields[2];
		return new Vector2f(box.getX() + 8, box.getY() + 5);
	}

	/** GUI position of the first picker cell. */
	public Vector2f firstPickerCell() {
		return new Vector2f(4 + CELL / 2f, pickerY + CELL / 2f);
	}

	public void reloadEditor(ModelData model) {
		state.load(model);
		nameBox.setValue(model.name());
		syncFields();
		frameModel();
	}

	public EditorState editorState() {
		return state;
	}

	public ViewCamera camera() {
		return camera;
	}
}
