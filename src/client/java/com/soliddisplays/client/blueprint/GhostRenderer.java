package com.soliddisplays.client.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.soliddisplays.item.BlueprintItem;
import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.ModelGeometry;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.placement.Materials;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Semi-transparent "ghost" of the held blueprint's model at the block the
 * player looks at, using the same anchor and rotation rules as the server.
 * <p>
 * Blocks are drawn from their real baked quads with alpha (white when the
 * player can pay for the model, red when materials are missing).
 */
public final class GhostRenderer {
	private static final int LIGHT = 0xF000F0;
	private static final float ALPHA = 0.55f;
	private static final Direction[] FACES_AND_NULL = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
			Direction.WEST, Direction.EAST, null};

	private static final ItemStackRenderState ITEM_STATE = new ItemStackRenderState();
	private static final List<BlockStateModelPart> PARTS = new ArrayList<>();

	private GhostRenderer() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(GhostRenderer::render);
	}

	/** Where the ghost would go right now, or null. Shared with tests. */
	public static BlockPos currentAnchor(Minecraft mc) {
		if (mc.player == null || mc.level == null || !BlueprintPlacement.isFilledBlueprint(mc.player.getMainHandItem())) {
			return null;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		return BlueprintPlacement.anchorFor(mc.level, hit.getBlockPos(), hit.getDirection());
	}

	private static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos anchor = currentAnchor(mc);
		if (anchor == null) {
			return;
		}
		ItemStack stack = mc.player.getMainHandItem();
		ModelData model = BlueprintItem.getModel(stack);
		int turns = BlueprintPlacement.rotation(stack);
		boolean affordable = mc.player.hasInfiniteMaterials()
				|| Materials.missing(mc.player.getInventory(), MaterialCalculator.cost(model)).isEmpty();
		float r = 1f;
		float g = affordable ? 1f : 0.45f;
		float b = affordable ? 1f : 0.45f;

		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		pose.pushPose();
		pose.translate(anchor.getX() - cam.x, anchor.getY() - cam.y, anchor.getZ() - cam.z);
		for (ModelElement e : model.elements()) {
			pose.pushPose();
			Vector3fc c = ModelGeometry.rotatedCenter(e, turns);
			pose.translate(c.x(), c.y(), c.z());
			pose.mulPose(ModelGeometry.displayMatrix(e, turns));
			if (e.kind() == ElementKind.BLOCK) {
				submitBlockGhost(mc, pose, collector, BuiltInRegistries.BLOCK.getValue(e.id()).defaultBlockState(), r, g, b);
			} else {
				ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(e.id()));
				mc.getItemModelResolver().updateForTopItem(ITEM_STATE, item, ItemDisplayContext.NONE, mc.level, null, 0);
				pose.rotate(Axis.YP, (float) Math.PI); // like vanilla item displays
				ITEM_STATE.submit(pose, collector, LIGHT, OverlayTexture.NO_OVERLAY, 0);
				ITEM_STATE.clear();
			}
			pose.popPose();
		}
		pose.popPose();
	}

	/** Re-emits the block's baked quads into a translucent render type with our colour/alpha. */
	private static void submitBlockGhost(Minecraft mc, PoseStack pose, SubmitNodeCollector collector, BlockState state,
			float r, float g, float b) {
		BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
		PARTS.clear();
		model.collectParts(RandomSource.create(42L), PARTS);
		List<BakedQuad> quads = new ArrayList<>();
		for (BlockStateModelPart part : PARTS) {
			for (Direction face : FACES_AND_NULL) {
				quads.addAll(part.getQuads(face));
			}
		}
		if (quads.isEmpty()) {
			return; // blocks drawn by a block entity renderer (chests, signs…) have no quads
		}
		collector.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(), (p, vc) -> {
			for (BakedQuad quad : quads) {
				emit(p, vc, quad, r, g, b);
			}
		});
	}

	private static void emit(PoseStack.Pose p, VertexConsumer vc, BakedQuad quad, float r, float g, float b) {
		Direction d = quad.direction();
		for (int i = 0; i < 4; i++) {
			Vector3fc pos = quad.position(i);
			long uv = quad.packedUV(i);
			vc.addVertex(p, pos.x(), pos.y(), pos.z())
					.setColor(r, g, b, ALPHA)
					.setUv(UVPair.unpackU(uv), UVPair.unpackV(uv))
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LIGHT)
					.setNormal(p, d.getStepX(), d.getStepY(), d.getStepZ());
		}
	}
}
