package com.soliddisplays.client.editor;

import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Everything the designer edits, independent of any widget.
 * <p>
 * The model is an immutable list of immutable {@link ModelElement}s, so an
 * undo step is simply the previous list. Callers that make a change call
 * {@link #checkpoint()} first (once per user action, not once per mouse move).
 */
public class EditorState {
	/** Snap steps offered in the toolbar, in blocks. */
	public static final float[] SNAP_STEPS = {1f, 0.5f, 0.25f, 0.125f, 0.0625f};
	public static final String[] SNAP_LABELS = {"1", "1/2", "1/4", "1/8", "1/16"};
	private static final int MAX_UNDO = 200;

	private List<ModelElement> elements;
	private String name;
	private int selected = -1;
	private int snapIndex = 2;

	private final Deque<List<ModelElement>> undo = new ArrayDeque<>();
	private final Deque<List<ModelElement>> redo = new ArrayDeque<>();

	public EditorState(ModelData initial) {
		this.elements = new ArrayList<>(initial.elements());
		this.name = initial.name();
		this.selected = elements.isEmpty() ? -1 : 0;
	}

	public ModelData toModel() {
		return new ModelData(name, elements);
	}

	// ---- Accessors ---------------------------------------------------------

	public List<ModelElement> elements() {
		return List.copyOf(elements);
	}

	public int size() {
		return elements.size();
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int selectedIndex() {
		return selected;
	}

	public ModelElement selected() {
		return selected >= 0 && selected < elements.size() ? elements.get(selected) : null;
	}

	public void select(int index) {
		selected = index >= 0 && index < elements.size() ? index : -1;
	}

	public float snap() {
		return SNAP_STEPS[snapIndex];
	}

	public String snapLabel() {
		return SNAP_LABELS[snapIndex];
	}

	public void cycleSnap() {
		snapIndex = (snapIndex + 1) % SNAP_STEPS.length;
	}

	public void setSnapIndex(int index) {
		snapIndex = Math.floorMod(index, SNAP_STEPS.length);
	}

	/** Rounds to the current snap step. */
	public float snap(float value) {
		float step = snap();
		return Math.round(value / step) * step;
	}

	// ---- Editing -----------------------------------------------------------

	/** Records the current state for undo. Call before each user action. */
	public void checkpoint() {
		undo.push(List.copyOf(elements));
		if (undo.size() > MAX_UNDO) {
			undo.removeLast();
		}
		redo.clear();
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	public void undo() {
		if (!undo.isEmpty()) {
			redo.push(List.copyOf(elements));
			elements = new ArrayList<>(undo.pop());
			clampSelection();
		}
	}

	public void redo() {
		if (!redo.isEmpty()) {
			undo.push(List.copyOf(elements));
			elements = new ArrayList<>(redo.pop());
			clampSelection();
		}
	}

	/** Replaces the whole design (undoable), e.g. when loading a blueprint. */
	public void load(ModelData model) {
		checkpoint();
		elements = new ArrayList<>(model.elements());
		name = model.name();
		selected = elements.isEmpty() ? -1 : 0;
	}

	/** Replaces the selected element (no checkpoint; the caller decides). */
	public void updateSelected(ModelElement element) {
		if (selected() != null) {
			elements.set(selected, element);
		}
	}

	/**
	 * Adds a full-block part made of {@code id}. It goes right next to the
	 * selected part (+X side), or at the origin for the first part.
	 */
	public void add(ElementKind kind, Identifier id) {
		checkpoint();
		ModelElement base = selected();
		Vector3f size = kind == ElementKind.BLOCK ? new Vector3f(1, 1, 1) : new Vector3f(0.5f, 0.5f, 0.5f);
		Vector3f pos = new Vector3f();
		if (base != null) {
			pos.set(base.position()).add(base.size().x(), 0, 0);
		}
		elements.add(new ModelElement(kind, id, pos, size, new Vector3f(), kind == ElementKind.BLOCK));
		selected = elements.size() - 1;
	}

	/** Copies the selected part, shifted one snap step up so it is visible. */
	public void duplicateSelected() {
		ModelElement e = selected();
		if (e != null) {
			checkpoint();
			Vector3fc p = e.position();
			elements.add(selected + 1, e.withPosition(new Vector3f(p).add(snap(), 0, snap())));
			selected++;
		}
	}

	public void deleteSelected() {
		if (selected() != null) {
			checkpoint();
			elements.remove(selected);
			clampSelection();
		}
	}

	/** Moves the selected element up/down in the list (draw order does not matter, only for tidiness). */
	public void moveSelected(int delta) {
		int target = selected + delta;
		if (selected() != null && target >= 0 && target < elements.size()) {
			checkpoint();
			ModelElement e = elements.remove(selected);
			elements.add(target, e);
			selected = target;
		}
	}

	private void clampSelection() {
		if (selected >= elements.size()) {
			selected = elements.size() - 1;
		}
	}
}
