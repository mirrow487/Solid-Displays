package com.soliddisplays.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Pure math shared by placement (server) and previews (client).
 * <p>
 * Coordinate system: model space has its origin at the minimum corner of the
 * <i>anchor block</i> (the block a model is placed into). A placed model is
 * rotated in 90° steps around the vertical axis through the anchor block's
 * centre, {@link #PIVOT}.
 */
public final class ModelGeometry {
	/** Rotation pivot in model space: centre of the anchor block's floor. */
	public static final Vector3fc PIVOT = new Vector3f(0.5f, 0f, 0.5f);

	private ModelGeometry() {
	}

	/** Rotation of the whole model, {@code quarterTurns} × 90° around +Y (counter-clockwise from above). */
	public static Quaternionf modelRotation(int quarterTurns) {
		return new Quaternionf().rotateY((float) (Math.floorMod(quarterTurns, 4) * Math.PI / 2.0));
	}

	/** Element's own rotation (Euler XYZ in degrees) around its centre. */
	public static Quaternionf elementRotation(ModelElement e) {
		Vector3fc r = e.rotation();
		return new Quaternionf().rotateXYZ(
				(float) Math.toRadians(r.x()), (float) Math.toRadians(r.y()), (float) Math.toRadians(r.z()));
	}

	/** Combined rotation: element rotation first, then model rotation. */
	public static Quaternionf totalRotation(ModelElement e, int quarterTurns) {
		return modelRotation(quarterTurns).mul(elementRotation(e));
	}

	/** Centre of the element's box in model space, before the model rotation. */
	public static Vector3f localCenter(ModelElement e) {
		return new Vector3f(e.size()).mul(0.5f).add(e.position());
	}

	/**
	 * Centre of the element relative to the anchor block's minimum corner,
	 * after applying the model rotation.
	 */
	public static Vector3f rotatedCenter(ModelElement e, int quarterTurns) {
		Vector3f c = localCenter(e).sub(PIVOT);
		modelRotation(quarterTurns).transform(c);
		return c.add(PIVOT);
	}

	/**
	 * Transformation for a display entity that sits at the element's rotated
	 * centre. Block displays draw their model in the unit cube [0,1]³, so the
	 * cube is first shifted to be centred on the origin; item displays already
	 * draw centred.
	 */
	public static Matrix4f displayMatrix(ModelElement e, int quarterTurns) {
		Matrix4f m = new Matrix4f().rotate(totalRotation(e, quarterTurns));
		m.scale(e.size().x(), e.size().y(), e.size().z());
		if (e.kind() == ElementKind.BLOCK) {
			m.translate(-0.5f, -0.5f, -0.5f);
		}
		return m;
	}

	/**
	 * Axis-aligned box that encloses the (possibly rotated) element, relative
	 * to the anchor block's minimum corner. For rotations that are not
	 * multiples of 90° this is the smallest enclosing axis-aligned box.
	 */
	public static AABB rotatedBounds(ModelElement e, int quarterTurns) {
		Quaternionf rot = totalRotation(e, quarterTurns);
		Vector3f center = rotatedCenter(e, quarterTurns);
		Vector3fc half = new Vector3f(e.size()).mul(0.5f);
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		Vector3f corner = new Vector3f();
		for (int i = 0; i < 8; i++) {
			corner.set((i & 1) == 0 ? -half.x() : half.x(),
					(i & 2) == 0 ? -half.y() : half.y(),
					(i & 4) == 0 ? -half.z() : half.z());
			rot.transform(corner).add(center);
			minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
			minY = Math.min(minY, corner.y); maxY = Math.max(maxY, corner.y);
			minZ = Math.min(minZ, corner.z); maxZ = Math.max(maxZ, corner.z);
		}
		// Round away float noise (e.g. 0.99999994) so flat surfaces line up exactly.
		return new AABB(snap(minX), snap(minY), snap(minZ), snap(maxX), snap(maxY), snap(maxZ));
	}

	/**
	 * Splits a box into equal pieces no larger than {@code maxPiece} on any
	 * axis. Pieces share faces exactly, so walking across seams is smooth.
	 */
	public static List<AABB> split(AABB box, double maxPiece) {
		int nx = Math.max(1, (int) Math.ceil(box.getXsize() / maxPiece - 1e-6));
		int ny = Math.max(1, (int) Math.ceil(box.getYsize() / maxPiece - 1e-6));
		int nz = Math.max(1, (int) Math.ceil(box.getZsize() / maxPiece - 1e-6));
		List<AABB> out = new ArrayList<>(nx * ny * nz);
		for (int ix = 0; ix < nx; ix++) {
			for (int iy = 0; iy < ny; iy++) {
				for (int iz = 0; iz < nz; iz++) {
					out.add(new AABB(
							lerp(box.minX, box.maxX, ix, nx), lerp(box.minY, box.maxY, iy, ny), lerp(box.minZ, box.maxZ, iz, nz),
							lerp(box.minX, box.maxX, ix + 1, nx), lerp(box.minY, box.maxY, iy + 1, ny), lerp(box.minZ, box.maxZ, iz + 1, nz)));
				}
			}
		}
		return out;
	}

	public static Vec3 toVec3(Vector3fc v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	private static double lerp(double a, double b, int i, int n) {
		return i == n ? b : a + (b - a) * i / n;
	}

	/** Snaps to the nearest 1/4096 block; far below any visible or snap-grid size. */
	private static double snap(float v) {
		return Math.round(v * 4096.0) / 4096.0;
	}
}
