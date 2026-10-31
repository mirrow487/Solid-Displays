package com.soliddisplays.client.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import com.soliddisplays.SolidDisplays;
import com.soliddisplays.item.BlueprintItem;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.network.RotateBlueprintPayload;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.placement.Materials;
import com.soliddisplays.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client side of using a blueprint: the rotate key (R) / Shift+scroll, and
 * the material list with have/need colours in the tooltip.
 */
public final class BlueprintClient {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SolidDisplays.id("main"));
	public static final KeyMapping ROTATE_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.solid_displays.rotate", InputConstants.KEY_R, CATEGORY));

	private BlueprintClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (ROTATE_KEY.consumeClick()) {
				rotateHeld(mc, 1);
			}
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addMaterialLines(stack, lines));
	}

	/**
	 * Turns the held blueprint. The change is applied locally right away (so the
	 * ghost reacts instantly) and sent to the server, which does the same.
	 *
	 * @return true if the player was holding a filled blueprint
	 */
	public static boolean rotateHeld(Minecraft mc, int steps) {
		Player player = mc.player;
		if (player == null || !BlueprintPlacement.isFilledBlueprint(player.getMainHandItem())) {
			return false;
		}
		BlueprintPlacement.rotate(player.getMainHandItem(), steps);
		ClientPlayNetworking.send(new RotateBlueprintPayload(steps));
		return true;
	}

	/** Lines like "✔ 8× Stone Bricks (12)" in green, or "✘ 1× Diamond (0)" in red. */
	private static void addMaterialLines(ItemStack stack, List<Component> lines) {
		if (!stack.is(ModItems.BLUEPRINT)) {
			return;
		}
		ModelData model = BlueprintItem.getModel(stack);
		Player player = Minecraft.getInstance().player;
		if (model.isEmpty() || player == null) {
			return;
		}
		if (player.hasInfiniteMaterials()) {
			lines.add(Component.translatable("tooltip.solid_displays.blueprint.free").withStyle(ChatFormatting.AQUA));
		} else {
			lines.add(Component.translatable("tooltip.solid_displays.blueprint.materials").withStyle(ChatFormatting.GRAY));
			for (ItemStack need : MaterialCalculator.cost(model)) {
				int have = Materials.count(player.getInventory(), need.getItem());
				boolean ok = have >= need.getCount();
				lines.add(Component.literal(ok ? " ✔ " : " ✘ ")
						.append(need.getCount() + "× ").append(need.getHoverName())
						.append(Component.literal(" (" + have + ")"))
						.withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		}
		lines.add(Component.translatable("tooltip.solid_displays.blueprint.usage", ROTATE_KEY.getTranslatedKeyMessage())
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
