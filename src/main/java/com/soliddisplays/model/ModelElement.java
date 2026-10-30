package com.soliddisplays.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * One part of a model: a single block or item display.
 * <p>
 * All values are in blocks and relative to the model origin (the block the
 * model is placed on). {@code position} is the minimum corner of the
 * un-rotated box, {@code size} its extent, and {@code rotation} are Euler
 * angles in degrees (applied X, then Y, then Z) around the box centre.
 * <p>
 * Instances are immutable; the editor creates modified copies, which keeps
 * undo/redo trivial.
 *
 * @param kind      block display or item display
 * @param id        block id (for {@link ElementKind#BLOCK}) or item id
 * @param position  minimum corner, in blocks
 * @param size      extent on each axis, in blocks
 * @param rotation  Euler angles in degrees
 * @param collision whether this part gets a solid collider when placed
 */
public record ModelElement(ElementKind kind, Identifier id, Vector3fc position, Vector3fc size,
		Vector3fc rotation, boolean collision) {

	public static final Codec<ModelElement> CODEC = RecordCodecBuilder.create(i -> i.group(
			ElementKind.CODEC.fieldOf("kind").forGetter(ModelElement::kind),
			Identifier.CODEC.fieldOf("id").forGetter(ModelElement::id),
			ExtraCodecs.VECTOR3F.fieldOf("position").forGetter(ModelElement::position),
			ExtraCodecs.VECTOR3F.fieldOf("size").forGetter(ModelElement::size),
			ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(ModelElement::rotation),
			Codec.BOOL.optionalFieldOf("collision", true).forGetter(ModelElement::collision)
	).apply(i, ModelElement::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ModelElement> STREAM_CODEC = StreamCodec.composite(
			ElementKind.STREAM_CODEC, ModelElement::kind,
			Identifier.STREAM_CODEC, ModelElement::id,
			ByteBufCodecs.VECTOR3F, ModelElement::position,
			ByteBufCodecs.VECTOR3F, ModelElement::size,
			ByteBufCodecs.VECTOR3F, ModelElement::rotation,
			ByteBufCodecs.BOOL, ModelElement::collision,
			ModelElement::new);

	public ModelElement {
		// Defensive copies: Vector3fc may be backed by a mutable Vector3f.
		position = new Vector3f(position);
		size = new Vector3f(size);
		rotation = new Vector3f(rotation);
	}

	/** A full-block-sized element of the given block at the origin. */
	public static ModelElement block(Identifier blockId) {
		return new ModelElement(ElementKind.BLOCK, blockId, new Vector3f(), new Vector3f(1, 1, 1), new Vector3f(), true);
	}

	/** Volume of the (un-rotated) box in cubic blocks. */
	public float volume() {
		return size.x() * size.y() * size.z();
	}

	public ModelElement withPosition(Vector3fc p) {
		return new ModelElement(kind, id, p, size, rotation, collision);
	}

	public ModelElement withSize(Vector3fc s) {
		return new ModelElement(kind, id, position, s, rotation, collision);
	}

	public ModelElement withRotation(Vector3fc r) {
		return new ModelElement(kind, id, position, size, r, collision);
	}

	public ModelElement withCollision(boolean c) {
		return new ModelElement(kind, id, position, size, rotation, c);
	}

	public ModelElement withId(ElementKind k, Identifier newId) {
		return new ModelElement(k, newId, position, size, rotation, collision);
	}
}
