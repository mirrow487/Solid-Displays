package com.soliddisplays.entity;

import com.soliddisplays.placement.ModelPlacer;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Invisible, motionless hitbox for one piece of a placed model.
 * <p>
 * <b>Why an entity:</b> vanilla already lets entities be solid to others via
 * {@link #canBeCollidedWith} (boats, shulkers). Those shapes take part in the
 * normal movement code on <i>both</i> sides, so the client predicts exactly the
 * same collision as the server: no rubber-banding, no jitter, and it works in
 * multiplayer without extra packets.
 * <p>
 * <b>Arbitrary size:</b> vanilla entity dimensions only allow a square base, so
 * the full X/Y/Z size is a synced data value and {@link #makeBoundingBox(Vec3)}
 * builds the box from it. The position is the bottom centre of the box (like
 * every vanilla entity).
 * <p>
 * <b>Size limit per piece:</b> entity lookups only search 2 blocks sideways and
 * 4 blocks below the queried box (see {@code EntitySectionStorage}). A box much
 * wider than that could be missed near its edges, so {@link ModelPlacer} splits
 * large parts into pieces of at most {@link #MAX_PIECE_SIZE} on each axis.
 * <p>
 * A non-{@linkplain #isSolid() solid} collider is still pickable, so decorative
 * parts (grass, flowers…) can be clicked to dismantle the model, but nothing
 * collides with them.
 */
public class ModelColliderEntity extends Entity {
	/** Largest edge of a single collider piece, in blocks. Must stay below the lookup margins. */
	public static final float MAX_PIECE_SIZE = 3.0f;

	private static final EntityDataAccessor<Vector3fc> DATA_SIZE =
			SynchedEntityData.defineId(ModelColliderEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Boolean> DATA_SOLID =
			SynchedEntityData.defineId(ModelColliderEntity.class, EntityDataSerializers.BOOLEAN);

	/** Which placed model this piece belongs to (server side only). */
	private UUID modelId = new UUID(0, 0);

	public ModelColliderEntity(EntityType<? extends ModelColliderEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.blocksBuilding = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SIZE, new Vector3f(1, 1, 1));
		builder.define(DATA_SOLID, true);
	}

	// ---- Size / shape ------------------------------------------------------

	public void setSize(Vector3fc size) {
		entityData.set(DATA_SIZE, new Vector3f(size));
		setBoundingBox(makeBoundingBox(position()));
	}

	public Vector3fc getSize() {
		return entityData.get(DATA_SIZE);
	}

	public boolean isSolid() {
		return entityData.get(DATA_SOLID);
	}

	public void setSolid(boolean solid) {
		entityData.set(DATA_SOLID, solid);
		this.blocksBuilding = solid;
	}

	public UUID getModelId() {
		return modelId;
	}

	public void setModelId(UUID modelId) {
		this.modelId = modelId;
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		Vector3fc size = entityData.get(DATA_SIZE);
		double hx = size.x() / 2.0;
		double hz = size.z() / 2.0;
		return new AABB(pos.x - hx, pos.y, pos.z - hz, pos.x + hx, pos.y + size.y(), pos.z + hz);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		// On the client the size arrives after the entity is created: rebuild the box.
		if (DATA_SIZE.equals(accessor)) {
			setBoundingBox(makeBoundingBox(position()));
		} else if (DATA_SOLID.equals(accessor)) {
			this.blocksBuilding = isSolid();
		}
	}

	// ---- Collision ---------------------------------------------------------

	/** This is what makes players (and every other entity) stand on and bump into the piece. */
	@Override
	public boolean canBeCollidedWith(Entity other) {
		return isSolid();
	}

	/** Needed so the crosshair can target the piece (for dismantling). */
	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	// ---- Immovable & indestructible -----------------------------------------

	@Override
	public void tick() {
		// Static: no gravity, no fluids, no fire, no portals. Skipping Entity#tick on
		// purpose; nothing about this entity ever changes on its own.
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		// Never moved by pistons, shulker boxes or anything else.
	}

	@Override
	public void push(Entity entity) {
	}

	@Override
	public void push(double x, double y, double z) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	/** Punching the piece does nothing (and does not damage it). */
	@Override
	public boolean skipAttackInteraction(Entity attacker) {
		return true;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return isSolid();
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	// ---- Interaction -------------------------------------------------------

	/** Sneak + right click starts (and confirms) dismantling the whole model. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer && level() instanceof ServerLevel serverLevel) {
			ModelPlacer.requestDismantle(serverLevel, serverPlayer, modelId);
		}
		return InteractionResult.SUCCESS;
	}

	// ---- Persistence -------------------------------------------------------

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("model_id", UUIDUtil.CODEC).ifPresent(id -> modelId = id);
		Optional<Vector3fc> size = input.read("size", ExtraCodecs.VECTOR3F);
		size.ifPresent(this::setSize);
		setSolid(input.getBooleanOr("solid", true));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.store("model_id", UUIDUtil.CODEC, modelId);
		output.store("size", ExtraCodecs.VECTOR3F, getSize());
		output.putBoolean("solid", isSolid());
	}
}
