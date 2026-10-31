package com.soliddisplays.client.render;

import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Screen-space overlay shapes for the viewport (grid, outlines, gizmo).
 * Coordinates are in view space (GUI pixels, see {@link ViewCamera}); widths
 * are in GUI pixels, so lines keep the same thickness at any zoom.
 */
public final class Overlay {
	private Overlay() {
	}

	/** Something drawn as flat quads. */
	public sealed interface Shape permits Line, Square {
	}

	/** A segment from a to b, {@code width} pixels thick. */
	public record Line(Vector3fc a, Vector3fc b, float width, int argb) implements Shape {
	}

	/** A filled square centred on p, {@code size} pixels wide. */
	public record Square(Vector3fc p, float size, int argb) implements Shape {
	}

	/** Twelve edges of a box given by its 8 corners (index bits: x=1, y=2, z=4). */
	public static void boxEdges(Vector3f[] corners, float width, int argb, List<Shape> out) {
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			out.add(new Line(corners[e[0]], corners[e[1]], width, argb));
		}
	}
}
