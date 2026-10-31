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
	 * Collision boxes for one element, relative to the anchor's minimum corner.
	 * <p>
	 * If the element ends up axis-aligned (all rotations multiples of 90°) its
	 * exact box is returned. Otherwise the rotated box is approximated by a
	 * "staircase" of axis-aligned boxes: the enclosing box is cut into a grid of
	 * {@code step}-sized cells, every cell whose centre lies inside the rotated
	 * box is kept, and neighbouring cells are greedily merged into larger boxes.
	 * If that needs more than {@code maxBoxes} boxes the grid is made coarser;
	 * as a last resort the enclosing box is used.
	 */
	public static List<AABB> collisionBoxes(ModelElement e, int quarterTurns, double step, int maxBoxes) {
		AABB bounds = rotatedBounds(e, quarterTurns);
		if (isAxisAligned(e)) {
			return List.of(bounds);
		}
		// Never coarser than half the thinnest side, or thin rotated plates would vanish.
		Vector3fc size = e.size();
		double minSide = Math.min(size.x(), Math.min(size.y(), size.z()));
		double s = Math.max(1.0 / 64.0, Math.min(step, minSide / 2.0));
		Quaternionf inverse = totalRotation(e, quarterTurns).conjugate();
		Vector3f center = rotatedCenter(e, quarterTurns);
		Vector3f half = new Vector3f(size).mul(0.5f);

		for (int attempt = 0; attempt < 4; attempt++, s *= 2) {
			int nx = (int) Math.ceil(bounds.getXsize() / s - 1e-6);
			int ny = (int) Math.ceil(bounds.getYsize() / s - 1e-6);
			int nz = (int) Math.ceil(bounds.getZsize() / s - 1e-6);
			if ((long) nx * ny * nz > 262_144) {
				continue; // grid too fine for this element; try coarser
			}
			boolean[][][] filled = new boolean[nx][ny][nz];
			boolean any = false;
			Vector3f p = new Vector3f();
			for (int x = 0; x < nx; x++) {
				for (int y = 0; y < ny; y++) {
					for (int z = 0; z < nz; z++) {
						p.set((float) (bounds.minX + (x + 0.5) * s - center.x),
								(float) (bounds.minY + (y + 0.5) * s - center.y),
								(float) (bounds.minZ + (z + 0.5) * s - center.z));
						inverse.transform(p);
						if (Math.abs(p.x) <= half.x + 1e-4 && Math.abs(p.y) <= half.y + 1e-4 && Math.abs(p.z) <= half.z + 1e-4) {
							filled[x][y][z] = true;
							any = true;
						}
					}
				}
			}
			if (!any) {
				break;
			}
			List<AABB> boxes = greedyMerge(filled, nx, ny, nz, bounds, s, bounds.maxX, bounds.maxY, bounds.maxZ);
			if (boxes.size() <= maxBoxes) {
				return boxes;
			}
		}
		return List.of(bounds);
	}

	/** True if the element's own rotation is a multiple of 90° on every axis. */
	public static boolean isAxisAligned(ModelElement e) {
		Vector3fc r = e.rotation();
		return isRightAngle(r.x()) && isRightAngle(r.y()) && isRightAngle(r.z());
	}

	private static boolean isRightAngle(float degrees) {
		double m = Math.abs(degrees % 90.0);
		return m < 1e-3 || 90.0 - m < 1e-3;
	}

	/**
	 * Classic 3D greedy meshing: grow a box along X, then Z, then Y while every
	 * cell is filled and unused. Cell edges at the far end are clamped to the
	 * real bounds so the result never sticks out of the enclosing box.
	 */
	private static List<AABB> greedyMerge(boolean[][][] filled, int nx, int ny, int nz, AABB origin, double s,
			double maxX, double maxY, double maxZ) {
		boolean[][][] used = new boolean[nx][ny][nz];
		List<AABB> out = new ArrayList<>();
		for (int y = 0; y < ny; y++) {
			for (int z = 0; z < nz; z++) {
				for (int x = 0; x < nx; x++) {
					if (!filled[x][y][z] || used[x][y][z]) {
						continue;
					}
					int x2 = x;
					while (x2 + 1 < nx && filled[x2 + 1][y][z] && !used[x2 + 1][y][z]) {
						x2++;
					}
					int z2 = z;
					grow:
					while (z2 + 1 < nz) {
						for (int i = x; i <= x2; i++) {
							if (!filled[i][y][z2 + 1] || used[i][y][z2 + 1]) {
								break grow;
							}
						}
						z2++;
					}
					int y2 = y;
					growY:
					while (y2 + 1 < ny) {
						for (int i = x; i <= x2; i++) {
							for (int k = z; k <= z2; k++) {
								if (!filled[i][y2 + 1][k] || used[i][y2 + 1][k]) {
									break growY;
								}
							}
						}
						y2++;
					}
					for (int i = x; i <= x2; i++) {
						for (int j = y; j <= y2; j++) {
							for (int k = z; k <= z2; k++) {
								used[i][j][k] = true;
							}
						}
					}
					out.add(new AABB(
							origin.minX + x * s, origin.minY + y * s, origin.minZ + z * s,
							Math.min(maxX, origin.minX + (x2 + 1) * s),
							Math.min(maxY, origin.minY + (y2 + 1) * s),
							Math.min(maxZ, origin.minZ + (z2 + 1) * s)));
				}
			}
		}
		return out;
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
