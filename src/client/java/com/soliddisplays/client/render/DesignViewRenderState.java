package com.soliddisplays.client.render;

import com.soliddisplays.model.ModelElement;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Snapshot of everything the 3D viewport needs for one frame. Built by the
 * screen during extraction, drawn by {@link DesignViewRenderer}.
 *
 * @param elements  parts to draw
 * @param selected  index of the selected part or -1
 * @param view      camera matrix (see {@link ViewCamera#view()})
 * @param gizmo     gizmo lines/handles to draw on top, in view space
 * @param gridRadius half-size of the floor grid, in blocks
 */
public record DesignViewRenderState(
		List<ModelElement> elements, int selected, Matrix4f view, List<Overlay.Shape> gizmo, int gridRadius,
		int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
		implements PictureInPictureRenderState {

	public DesignViewRenderState(List<ModelElement> elements, int selected, Matrix4f view, List<Overlay.Shape> gizmo,
			int gridRadius, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
		this(elements, selected, view, gizmo, gridRadius, x0, y0, x1, y1, scissorArea,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}

	@Override
	public float scale() {
		return 1f;
	}
}
