package com.soliddisplays.model;

import com.soliddisplays.config.SDConfig;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3fc;

/**
 * Server-side checks for designs sent by clients. Never trust the client:
 * every limit here comes from {@link SDConfig}, and every part must be made
 * of something a survival player can actually pay for.
 */
public final class ModelValidator {
	/** Technical blocks/items that may never be used in a design. */
	private static final Set<Identifier> DENIED = Set.of(
			Identifier.withDefaultNamespace("air"), Identifier.withDefaultNamespace("cave_air"),
			Identifier.withDefaultNamespace("void_air"), Identifier.withDefaultNamespace("barrier"),
			Identifier.withDefaultNamespace("light"), Identifier.withDefaultNamespace("structure_void"),
			Identifier.withDefaultNamespace("structure_block"), Identifier.withDefaultNamespace("jigsaw"),
			Identifier.withDefaultNamespace("command_block"), Identifier.withDefaultNamespace("chain_command_block"),
			Identifier.withDefaultNamespace("repeating_command_block"), Identifier.withDefaultNamespace("command_block_minecart"),
			Identifier.withDefaultNamespace("debug_stick"), Identifier.withDefaultNamespace("knowledge_book"),
			Identifier.withDefaultNamespace("moving_piston"), Identifier.withDefaultNamespace("test_block"),
			Identifier.withDefaultNamespace("test_instance_block"));

	private ModelValidator() {
	}

	/** True if a part may be made of this block/item. Shared with the client picker. */
	public static boolean isAllowed(ElementKind kind, Identifier id) {
		if (DENIED.contains(id)) {
			return false;
		}
		if (kind == ElementKind.BLOCK) {
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			// Must exist and have an item form, otherwise it could not be paid for.
			return block != Blocks.AIR && block.asItem() != Items.AIR;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item != Items.AIR;
	}

	/** Empty if the model is acceptable, otherwise a message for the player. */
	public static Optional<Component> validate(ModelData model) {
		SDConfig config = SDConfig.get();
		if (model.name().length() > ModelData.MAX_NAME_LENGTH) {
			return fail("name_too_long");
		}
		if (model.elements().size() > config.maxElements) {
			return Optional.of(Component.translatable("message.solid_displays.invalid.too_many", config.maxElements));
		}
		for (ModelElement e : model.elements()) {
			if (!isAllowed(e.kind(), e.id())) {
				return Optional.of(Component.translatable("message.solid_displays.invalid.material", e.id().toString()));
			}
			if (!finite(e.position()) || !finite(e.size()) || !finite(e.rotation())) {
				return fail("number");
			}
			Vector3fc s = e.size();
			float min = config.minElementSize - 1e-4f;
			float max = config.maxElementSize + 1e-4f;
			if (s.x() < min || s.y() < min || s.z() < min || s.x() > max || s.y() > max || s.z() > max) {
				return Optional.of(Component.translatable("message.solid_displays.invalid.size",
						config.minElementSize, config.maxElementSize));
			}
			Vector3fc p = e.position();
			float r = config.maxModelRadius + 1e-4f;
			if (Math.abs(p.x()) > r || Math.abs(p.y()) > r || Math.abs(p.z()) > r
					|| Math.abs(p.x() + s.x()) > r || Math.abs(p.y() + s.y()) > r || Math.abs(p.z() + s.z()) > r) {
				return Optional.of(Component.translatable("message.solid_displays.invalid.radius", config.maxModelRadius));
			}
			Vector3fc rot = e.rotation();
			if (Math.abs(rot.x()) > 360.001f || Math.abs(rot.y()) > 360.001f || Math.abs(rot.z()) > 360.001f) {
				return fail("number");
			}
		}
		return Optional.empty();
	}

	private static Optional<Component> fail(String key) {
		return Optional.of(Component.translatable("message.solid_displays.invalid." + key));
	}

	private static boolean finite(Vector3fc v) {
		return Float.isFinite(v.x()) && Float.isFinite(v.y()) && Float.isFinite(v.z());
	}
}
