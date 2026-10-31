package com.soliddisplays.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.ModelGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Draws the designer's 3D viewport into an off-screen texture.
 * <p>
 * Blocks and items are submitted exactly the way vanilla block/item display
 * entities submit them, so the preview matches the placed model. Grid,
 * outlines and gizmo are flat quads computed in view space.
 */
public class DesignViewRenderer extends PictureInPictureRenderer<DesignViewRenderState> {
	/** Full-bright light so the preview does not depend on the world. */
	private static final int LIGHT = 0xF000F0;
	/**
	 * Depth compression: the picture-in-picture projection only keeps depths in
	 * ±1000 texture pixels; scaling Z keeps large, zoomed-in models from being
	 * clipped without changing the (orthographic) image.
	 */
	private static final float DEPTH_SCALE = 0.02f;

	private final BlockModelResolver blockResolver;
	private final BlockDisplayContext blockContext = BlockDisplayContext.create();
	private final BlockModelRenderState blockState = new BlockModelRenderState();
	private final ItemStackRenderState itemState = new ItemStackRenderState();

	public DesignViewRenderer(Minecraft minecraft) {
		this.blockResolver = new BlockModelResolver(minecraft.getModelManager());
	}

	@Override
	public Class<DesignViewRenderState> getRenderStateClass() {
		return DesignViewRenderState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "solid_displays design view";
	}

	/** Put the origin in the middle of the viewport instead of at its bottom. */
	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected void renderToTexture(DesignViewRenderState state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		pose.pushPose();
		pose.scale(1f, 1f, DEPTH_SCALE);

		// ---- floor grid and anchor block (depth-tested, drawn first) -------------
		List<Overlay.Shape> floor = new ArrayList<>();
		addGrid(state, floor);
		submitShapes(pose, collector, floor, 0f);

		// ---- parts ---------------------------------------------------------------
		pose.pushPose();
		pose.mulPose(state.view());
		for (ModelElement e : state.elements()) {
			pose.pushPose();
			Vector3fc c = ModelGeometry.localCenter(e);
			pose.translate(c.x(), c.y(), c.z());
			pose.mulPose(ModelGeometry.displayMatrix(e, 0));
			if (e.kind() == ElementKind.BLOCK) {
				BlockState bs = BuiltInRegistries.BLOCK.getValue(e.id()).defaultBlockState();
				blockResolver.update(blockState, bs, blockContext);
				blockState.submit(pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
				blockState.clear();
			} else {
				ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(e.id()));
				mc.getItemModelResolver().updateForTopItem(itemState, stack, ItemDisplayContext.NONE, mc.level, null, 0);
				pose.rotate(Axis.YP, (float) Math.PI); // same as vanilla item displays
				itemState.submit(pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
				itemState.clear();
			}
			pose.popPose();
		}
		pose.popPose();

		// ---- selection outline and gizmo, always on top --------------------------
		float guiScale = (float) mc.getWindow().getGuiScale();
		float topZ = -850f / (guiScale * DEPTH_SCALE);
		submitShapes(pose, collector, state.gizmo(), topZ);
		pose.popPose();
	}

	/** 1-block floor grid on y = 0 plus a highlighted outline of the anchor block. */
	private static void addGrid(DesignViewRenderState state, List<Overlay.Shape> out) {
		Matrix4f v = state.view();
		int r = state.gridRadius();
		for (int i = -r; i <= r + 1; i++) {
			int color = i == 0 || i == 1 ? 0x80FFFFFF : 0x40FFFFFF;
			out.add(new Overlay.Line(v.transformPosition(new Vector3f(i, 0, -r)), v.transformPosition(new Vector3f(i, 0, r + 1)), 1f, color));
			out.add(new Overlay.Line(v.transformPosition(new Vector3f(-r, 0, i)), v.transformPosition(new Vector3f(r + 1, 0, i)), 1f, color));
		}
		Vector3f[] anchor = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			anchor[i] = v.transformPosition(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
		}
		Overlay.boxEdges(anchor, 1f, 0x60FFD24A, out);
	}

	/**
	 * Draws overlay shapes as camera-facing quads. With {@code forcedZ} != 0
	 * every vertex is pushed to that depth (keeping a little of its own depth
	 * so overlapping handles still sort), which puts the shapes on top.
	 */
	private static void submitShapes(PoseStack pose, SubmitNodeCollector collector, List<Overlay.Shape> shapes, float forcedZ) {
		if (shapes.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> {
			for (Overlay.Shape shape : shapes) {
				switch (shape) {
					case Overlay.Line l -> line(p, vc, l, forcedZ);
					case Overlay.Square s -> square(p, vc, s, forcedZ);
				}
			}
		});
	}

	private static float z(float z, float forcedZ) {
		return forcedZ == 0f ? z : forcedZ + z * 0.001f;
	}

	private static void line(PoseStack.Pose p, VertexConsumer vc, Overlay.Line l, float forcedZ) {
		Vector2f d = new Vector2f(l.b().x() - l.a().x(), l.b().y() - l.a().y());
		if (d.lengthSquared() < 1e-6f) {
			return;
		}
		d.normalize().perpendicular().mul(l.width() / 2f);
		float az = z(l.a().z(), forcedZ);
		float bz = z(l.b().z(), forcedZ);
		vc.addVertex(p, l.a().x() + d.x, l.a().y() + d.y, az).setColor(l.argb());
		vc.addVertex(p, l.a().x() - d.x, l.a().y() - d.y, az).setColor(l.argb());
		vc.addVertex(p, l.b().x() - d.x, l.b().y() - d.y, bz).setColor(l.argb());
		vc.addVertex(p, l.b().x() + d.x, l.b().y() + d.y, bz).setColor(l.argb());
	}

	private static void square(PoseStack.Pose p, VertexConsumer vc, Overlay.Square s, float forcedZ) {
		float h = s.size() / 2f;
		float z = z(s.p().z(), forcedZ);
		float x = s.p().x();
		float y = s.p().y();
		vc.addVertex(p, x - h, y - h, z).setColor(s.argb());
		vc.addVertex(p, x - h, y + h, z).setColor(s.argb());
		vc.addVertex(p, x + h, y + h, z).setColor(s.argb());
		vc.addVertex(p, x + h, y - h, z).setColor(s.argb());
	}
}
