package com.soliddisplays.model;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Fixed models used to prove collision before the designer exists. Each part
 * exercises one case: a large slab that must be split into several colliders,
 * thin plates, thin legs, a 45° rotated block (axis-aligned enclosing box), a
 * 4-block pillar (split vertically), and decorative parts without collision.
 */
public final class TestModels {
	public static final ModelData DEMO = new ModelData("Test", List.of(
			// 5×0.5×3 platform: wider than one collider piece -> split in two on X.
			block("stone_bricks", 0, 0, 0, 5, 0.5f, 3),
			// Small table on the platform: 2×1 top, 1/8-thick, on four 1/8 legs.
			block("oak_planks", 0.5f, 1.375f, 0.5f, 2, 0.125f, 1),
			block("oak_log", 0.5f, 0.5f, 0.5f, 0.125f, 0.875f, 0.125f),
			block("oak_log", 2.375f, 0.5f, 0.5f, 0.125f, 0.875f, 0.125f),
			block("oak_log", 0.5f, 0.5f, 1.375f, 0.125f, 0.875f, 0.125f),
			block("oak_log", 2.375f, 0.5f, 1.375f, 0.125f, 0.875f, 0.125f),
			// Rotated 45° around Y: collider is the enclosing ~1.41×1×1.41 box.
			new ModelElement(ElementKind.BLOCK, Identifier.withDefaultNamespace("cobblestone"),
					new Vector3f(3.25f, 0.5f, 0.25f), new Vector3f(1, 1, 1), new Vector3f(0, 45, 0), true),
			// 4-block-tall thin pillar: split into two collider pieces vertically.
			block("quartz_pillar", 4.25f, 0.5f, 2.25f, 0.5f, 4, 0.5f),
			// Decoration only: no collision (walk through), still clickable.
			new ModelElement(ElementKind.BLOCK, Identifier.withDefaultNamespace("poppy"),
					new Vector3f(0.25f, 0.5f, 2.25f), new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(), false),
			// Item display on the table, no collision.
			new ModelElement(ElementKind.ITEM, Identifier.withDefaultNamespace("diamond"),
					new Vector3f(1.25f, 1.5f, 0.75f), new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(), false)
	));

	private TestModels() {
	}

	private static ModelElement block(String id, float x, float y, float z, float sx, float sy, float sz) {
		return new ModelElement(ElementKind.BLOCK, Identifier.withDefaultNamespace(id),
				new Vector3f(x, y, z), new Vector3f(sx, sy, sz), new Vector3f(), true);
	}
}
