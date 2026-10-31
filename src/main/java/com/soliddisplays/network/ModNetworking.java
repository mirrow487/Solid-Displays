package com.soliddisplays.network;

import com.soliddisplays.block.DesignTableBlockEntity;
import com.soliddisplays.menu.DesignTableMenu;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelValidator;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.registry.ModComponents;
import com.soliddisplays.registry.ModItems;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Packet registration and the server-side handlers. Handlers run on the
 * server thread and re-check everything: the player must have this very
 * table's screen open, and the design must pass {@link ModelValidator}.
 */
public final class ModNetworking {
	/** Upper bound for a design packet; the element count limit in the codec keeps real ones far below. */
	private static final int MAX_DESIGN_PACKET = 1 << 20;

	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(SaveBlueprintPayload.TYPE, SaveBlueprintPayload.CODEC, MAX_DESIGN_PACKET);
		PayloadTypeRegistry.serverboundPlay().registerLarge(UpdateDraftPayload.TYPE, UpdateDraftPayload.CODEC, MAX_DESIGN_PACKET);
		PayloadTypeRegistry.serverboundPlay().register(RotateBlueprintPayload.TYPE, RotateBlueprintPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveBlueprintPayload.TYPE,
				(payload, context) -> saveBlueprint(context.player(), payload.pos(), payload.model()));
		ServerPlayNetworking.registerGlobalReceiver(UpdateDraftPayload.TYPE,
				(payload, context) -> updateDraft(context.player(), payload.pos(), payload.model()));
		ServerPlayNetworking.registerGlobalReceiver(RotateBlueprintPayload.TYPE, (payload, context) -> {
			ItemStack held = context.player().getMainHandItem();
			// Only ever ±1 per key press / scroll notch; clamp anything else.
			if (BlueprintPlacement.isFilledBlueprint(held)) {
				BlueprintPlacement.rotate(held, Integer.signum(payload.steps()));
			}
		});
	}

	/** The table the player is currently using, if it is the one at {@code pos}. */
	private static Optional<DesignTableBlockEntity> openTable(ServerPlayer player, BlockPos pos) {
		if (!(player.containerMenu instanceof DesignTableMenu menu) || !menu.getTablePos().equals(pos)
				|| !menu.stillValid(player)) {
			return Optional.empty();
		}
		if (player.level().getBlockEntity(pos) instanceof DesignTableBlockEntity table) {
			return Optional.of(table);
		}
		return Optional.empty();
	}

	private static void saveBlueprint(ServerPlayer player, BlockPos pos, ModelData model) {
		Optional<DesignTableBlockEntity> table = openTable(player, pos);
		if (table.isEmpty()) {
			return;
		}
		ModelData clean = model.withName(model.name().strip());
		Optional<Component> error = ModelValidator.validate(clean);
		if (error.isPresent()) {
			player.sendOverlayMessage(error.get().copy().withStyle(ChatFormatting.RED));
			return;
		}
		if (clean.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.solid_displays.save.empty").withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack current = table.get().getBlueprintSlot().getItem(0);
		if (!current.is(ModItems.EMPTY_BLUEPRINT) && !current.is(ModItems.BLUEPRINT)) {
			player.sendOverlayMessage(Component.translatable("message.solid_displays.save.no_blueprint").withStyle(ChatFormatting.RED));
			return;
		}
		// One blueprint is written; any extra empty blueprints in the slot are impossible (slot max 1).
		ItemStack filled = new ItemStack(ModItems.BLUEPRINT);
		filled.set(ModComponents.MODEL_DATA, clean);
		table.get().getBlueprintSlot().setItem(0, filled);
		table.get().setDraft(clean);
		player.containerMenu.broadcastChanges();
		player.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
		player.sendOverlayMessage(Component.translatable("message.solid_displays.save.done", clean.name()).withStyle(ChatFormatting.GREEN));
	}

	private static void updateDraft(ServerPlayer player, BlockPos pos, ModelData model) {
		Optional<DesignTableBlockEntity> table = openTable(player, pos);
		// A draft outside the limits is simply not kept.
		if (table.isPresent() && ModelValidator.validate(model).isEmpty()) {
			table.get().setDraft(model);
		}
	}
}
