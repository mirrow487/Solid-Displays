package com.soliddisplays.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Orbit camera of the designer viewport, plus the maths to go from model
 * space to viewport pixels and back (used for picking and gizmo dragging).
 * <p>
 * The viewport uses an orthographic projection: a model-space point p lands on
 * screen at {@code centre + (view · p).xy}, measured in GUI pixels. Because the
 * picture-in-picture pose flips Z, the viewer looks along +Z of view space:
 * a smaller view Z is closer to the eye.
 */
public class ViewCamera {
	public float yaw = 35f;
	public float pitch = 30f;
	/** GUI pixels per block. */
	public float zoom = 32f;
	public final Vector3f target = new Vector3f(0.5f, 0.5f, 0.5f);

	/** Model space → view space (GUI pixels, origin at the viewport centre, Y down). */
	public Matrix4f view() {
		return new Matrix4f()
				.rotateZ((float) Math.PI) // model Y up -> screen Y down (same trick vanilla uses for GUI entities)
				.rotateX((float) Math.toRadians(-pitch))
				.rotateY((float) Math.toRadians(-yaw))
				.scale(zoom)
				.translate(-target.x, -target.y, -target.z);
	}

	/** Model space → GUI offset from the viewport centre (x right, y down) plus depth in z. */
	public Vector3f project(Vector3fc p) {
		return view().transformPosition(new Vector3f(p));
	}

	/** Model-space direction the camera looks along (towards the scene). */
	public Vector3f forward() {
		return new Matrix4f(view()).invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
	}

	/** Model-space point under a GUI offset (relative to the viewport centre) at view depth z. */
	public Vector3f unproject(float sx, float sy, float z) {
		return new Matrix4f(view()).invert().transformPosition(new Vector3f(sx, sy, z));
	}

	public void orbit(float dx, float dy) {
		yaw = (yaw - dx * 0.6f) % 360f;
		pitch = Math.clamp(pitch + dy * 0.6f, -89f, 89f);
	}

	/** Moves the orbit target so the scene follows the mouse. */
	public void pan(float dx, float dy) {
		Matrix4f inv = new Matrix4f(view()).invert();
		Vector3f d = inv.transformDirection(new Vector3f(dx, dy, 0));
		target.sub(d);
	}

	public void zoom(double scroll) {
		zoom = Math.clamp(zoom * (float) Math.pow(1.15, scroll), 4f, 256f);
	}

	public void reset() {
		yaw = 35f;
		pitch = 30f;
		zoom = 32f;
		target.set(0.5f, 0.5f, 0.5f);
	}
}
