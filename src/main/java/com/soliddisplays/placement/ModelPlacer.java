package com.soliddisplays.placement;

import com.mojang.math.Transformation;
import com.soliddisplays.SolidDisplays;
import com.soliddisplays.config.SDConfig;
import com.soliddisplays.entity.ModelColliderEntity;
import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.ModelGeometry;
import com.soliddisplays.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side creation and removal of placed models.
 * <p>
 * A model becomes one vanilla display entity per element (what you see) plus
 * one or more {@link ModelColliderEntity} pieces per element (what you hit).
 * All of them carry the model's id so they can be found and removed together.
 */
public final class ModelPlacer {
	/** Tag on every vanilla display entity created by this mod. */
	public static final String PART_TAG = "solid_displays.part";
	/** Prefix of the tag that stores the model id on display entities. */
	public static final String MODEL_TAG_PREFIX = "solid_displays.model.";

	/** How long the "sneak-click again to confirm" window stays open, in ticks. */
	private static final long CONFIRM_TICKS = 60;

	/** player -> (model id, game time of first click) */
	private static final Map<UUID, PendingDismantle> PENDING = new HashMap<>();

	private record PendingDismantle(UUID modelId, long time) {
	}

	private ModelPlacer() {
	}

	// ---- Placing -------------------------------------------------------------

	/** Result of {@link #place}: either the placed model or a reason it failed. */
	public record PlaceResult(Optional<PlacedModel> model, Component error) {
		static PlaceResult ok(PlacedModel model) {
			return new PlaceResult(Optional.of(model), Component.empty());
		}

		static PlaceResult fail(Component error) {
			return new PlaceResult(Optional.empty(), error);
		}
	}

	/** One collider piece. */
	public record Piece(AABB box, boolean solid) {
	}

	/**
	 * Every collider piece of a model, relative to the anchor's minimum corner.
	 * Solid parts follow their (possibly rotated) shape; non-solid parts only
	 * need one clickable box, so they use the enclosing box.
	 */
	public static List<Piece> colliderPieces(ModelData model, int quarterTurns) {
		SDConfig config = SDConfig.get();
		List<Piece> pieces = new ArrayList<>();
		for (ModelElement element : model.elements()) {
			List<AABB> shape = element.collision()
					? ModelGeometry.collisionBoxes(element, quarterTurns, config.rotatedCollisionStep, config.maxCollidersPerRotatedPart)
					: List.of(ModelGeometry.rotatedBounds(element, quarterTurns));
			for (AABB box : shape) {
				for (AABB piece : ModelGeometry.split(box, ModelColliderEntity.MAX_PIECE_SIZE)) {
					pieces.add(new Piece(piece, element.collision()));
				}
			}
		}
		return pieces;
	}

	/**
	 * Places a model paid for with the player's materials. Creative players
	 * place for free (and get nothing back). Materials are only taken once the
	 * model has actually been placed.
	 */
	public static PlaceResult placeWithMaterials(ServerLevel level, ServerPlayer player, ModelData model,
			BlockPos anchor, int quarterTurns) {
		boolean free = player.hasInfiniteMaterials() || !SDConfig.get().requireMaterials;
		List<ItemStack> cost = free ? List.of() : MaterialCalculator.cost(model);
		List<ItemStack> missing = Materials.missing(player.getInventory(), cost);
		if (!missing.isEmpty()) {
			return PlaceResult.fail(Component.translatable("message.solid_displays.place.missing", describe(missing)));
		}
		PlaceResult result = place(level, player, model, anchor, quarterTurns, Materials.toStacks(cost));
		if (result.model().isPresent()) {
			Materials.take(player.getInventory(), cost);
		}
		return result;
	}

	/** "3× Stone Bricks, 1× Diamond" */
	public static Component describe(List<ItemStack> items) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(items.get(i).getCount() + "× ").append(items.get(i).getHoverName());
		}
		return out;
	}

	/**
	 * Spawns a model. Does no material or limit checks; callers validate first.
	 *
	 * @param anchor       block whose minimum corner is model-space (0,0,0)
	 * @param quarterTurns rotation in 90° steps around the anchor's centre
	 * @param refund       items handed back when the model is dismantled
	 */
	public static PlaceResult place(ServerLevel level, ServerPlayer owner, ModelData model, BlockPos anchor,
			int quarterTurns, List<ItemStack> refund) {
		Vec3 base = Vec3.atLowerCornerOf(anchor);

		// 1. Work out every collider piece first, so we can refuse cleanly.
		List<Piece> pieces = new ArrayList<>();
		for (Piece piece : colliderPieces(model, quarterTurns)) {
			pieces.add(new Piece(piece.box().move(base), piece.solid()));
		}

		// 2. Never trap a player or mob inside a solid part.
		for (Piece piece : pieces) {
			if (piece.solid() && !level.getEntitiesOfClass(LivingEntity.class, piece.box().deflate(1e-3),
					e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
				return PlaceResult.fail(Component.translatable("message.solid_displays.place.blocked"));
			}
		}

		// 3. Register the model before spawning, otherwise the load check would
		//    treat the new entities as orphans and delete them.
		UUID modelId = UUID.randomUUID();
		PlacedModels registry = PlacedModels.get(level);
		registry.add(new PlacedModel(modelId, owner.getUUID(), model.name(), anchor, List.of(), refund));

		List<UUID> entityIds = new ArrayList<>();
		for (ModelElement element : model.elements()) {
			Display display = createDisplay(level, element, quarterTurns);
			if (display == null) {
				continue;
			}
			Vec3 center = ModelGeometry.toVec3(ModelGeometry.rotatedCenter(element, quarterTurns)).add(base);
			display.snapTo(center.x, center.y, center.z, 0f, 0f);
			display.addTag(PART_TAG);
			display.addTag(MODEL_TAG_PREFIX + modelId);
			if (level.addFreshEntity(display)) {
				entityIds.add(display.getUUID());
			}
		}

		for (Piece piece : pieces) {
			ModelColliderEntity collider = ModEntities.MODEL_COLLIDER.create(level, EntitySpawnReason.TRIGGERED);
			if (collider == null) {
				continue;
			}
			AABB box = piece.box();
			collider.setModelId(modelId);
			collider.setSolid(piece.solid());
			collider.snapTo(box.getCenter().x, box.minY, box.getCenter().z, 0f, 0f);
			collider.setSize(new org.joml.Vector3f((float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize()));
			if (level.addFreshEntity(collider)) {
				entityIds.add(collider.getUUID());
			}
		}

		PlacedModel placed = new PlacedModel(modelId, owner.getUUID(), model.name(), anchor, entityIds, refund);
		registry.add(placed);
		SolidDisplays.LOGGER.debug("Placed model {} ({} entities) at {}", modelId, entityIds.size(), anchor);
		return PlaceResult.ok(placed);
	}

	/** Builds the vanilla display entity for one element (not yet added to the level). */
	private static Display createDisplay(ServerLevel level, ModelElement element, int quarterTurns) {
		Display display;
		if (element.kind() == ElementKind.BLOCK) {
			Block block = BuiltInRegistries.BLOCK.getValue(element.id());
			if (block == Blocks.AIR) {
				return null;
			}
			Display.BlockDisplay blockDisplay = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (blockDisplay == null) {
				return null;
			}
			blockDisplay.setBlockState(block.defaultBlockState());
			display = blockDisplay;
		} else {
			Item item = BuiltInRegistries.ITEM.getValue(element.id());
			if (item == Items.AIR) {
				return null;
			}
			Display.ItemDisplay itemDisplay = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (itemDisplay == null) {
				return null;
			}
			itemDisplay.setItemStack(new ItemStack(item));
			itemDisplay.setItemTransform(ItemDisplayContext.NONE);
			display = itemDisplay;
		}
		display.setTransformation(new Transformation(ModelGeometry.displayMatrix(element, quarterTurns)));
		// Culling box: generous enough to cover the rotated element around its centre.
		AABB bounds = ModelGeometry.rotatedBounds(element, quarterTurns);
		float extent = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
		display.setWidth(extent * 2f);
		display.setHeight(extent * 2f);
		return display;
	}

	// ---- Dismantling -----------------------------------------------------------

	/**
	 * Called when a player sneak-right-clicks a model. The first click asks for
	 * confirmation; a second click on the same model within 3 seconds removes it.
	 */
	public static void requestDismantle(ServerLevel level, ServerPlayer player, UUID modelId) {
		PlacedModels registry = PlacedModels.get(level);
		PlacedModel model = registry.get(modelId);
		if (model == null) {
			return;
		}
		if (!mayDismantle(player, model)) {
			player.sendOverlayMessage(Component.translatable("message.solid_displays.dismantle.not_owner")
					.withStyle(ChatFormatting.RED));
			return;
		}

		long now = level.getGameTime();
		PendingDismantle pending = PENDING.get(player.getUUID());
		if (pending == null || !pending.modelId().equals(modelId) || now - pending.time() > CONFIRM_TICKS) {
			PENDING.put(player.getUUID(), new PendingDismantle(modelId, now));
			player.sendOverlayMessage(Component.translatable("message.solid_displays.dismantle.confirm")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}

		PENDING.remove(player.getUUID());
		dismantle(level, modelId, player);
	}

	public static boolean mayDismantle(ServerPlayer player, PlacedModel model) {
		return !SDConfig.get().onlyOwnerCanDismantle
				|| model.owner().equals(player.getUUID())
				|| player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/**
	 * Removes every entity of a model and drops its refund at the anchor.
	 * Entities in unloaded chunks are removed later by {@link #onEntityLoad}.
	 */
	public static void dismantle(ServerLevel level, UUID modelId, ServerPlayer player) {
		PlacedModel model = PlacedModels.get(level).remove(modelId);
		if (model == null) {
			return;
		}
		for (UUID entityId : model.entities()) {
			Entity entity = level.getEntity(entityId);
			if (entity != null) {
				entity.discard();
			}
		}
		// The materials pop out where the model stood, like a broken block.
		Vec3 at = Vec3.atCenterOf(model.anchor());
		for (ItemStack stack : model.refund()) {
			net.minecraft.world.Containers.dropItemStack(level, at.x, at.y, at.z, stack.copy());
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 0.8f);
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.solid_displays.dismantle.done")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	// ---- Orphan cleanup --------------------------------------------------------

	/**
	 * Deletes entities whose model no longer exists. This covers parts that were
	 * in unloaded chunks when the model was dismantled.
	 */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		UUID modelId = null;
		if (entity instanceof ModelColliderEntity collider) {
			modelId = collider.getModelId();
		} else if (entity instanceof Display && entity.entityTags().contains(PART_TAG)) {
			for (String tag : entity.entityTags()) {
				if (tag.startsWith(MODEL_TAG_PREFIX)) {
					try {
						modelId = UUID.fromString(tag.substring(MODEL_TAG_PREFIX.length()));
					} catch (IllegalArgumentException ignored) {
						// Malformed tag: treat as orphan below.
					}
				}
			}
			if (modelId == null) {
				entity.discard();
				return;
			}
		}
		if (modelId != null && !PlacedModels.get(level).contains(modelId)) {
			entity.discard();
		}
	}

	/** Forget pending confirmations of players who left. */
	public static void onPlayerLeave(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}
}
