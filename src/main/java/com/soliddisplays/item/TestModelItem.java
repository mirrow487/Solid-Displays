package com.soliddisplays.item;

import com.soliddisplays.model.TestModels;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.placement.ModelPlacer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Creative-tab debug tool: places {@link TestModels#DEMO} facing the way the
 * player looks. In survival it costs materials (refunded on dismantle), in
 * creative it is free. Used to verify placement before the designer exists.
 */
public class TestModelItem extends Item {
	public TestModelItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos clicked = context.getClickedPos();
		BlockPos anchor = BlueprintPlacement.anchorFor(level, clicked, context.getClickedFace());
		int quarterTurns = quarterTurnsFor(player);
		// Survival: costs (and refunds) materials exactly like a blueprint will. Creative: free.
		ModelPlacer.PlaceResult result = ModelPlacer.placeWithMaterials(level, player, TestModels.DEMO, anchor, quarterTurns);
		if (result.model().isEmpty()) {
			player.sendOverlayMessage(result.error().copy().withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Model +Z faces away from the player; 0..3 quarter turns. */
	public static int quarterTurnsFor(ServerPlayer player) {
		// Direction 2D values: south=0, west=1, north=2, east=3.
		return Math.floorMod(-player.getDirection().get2DDataValue(), 4);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.solid_displays.test_model").withStyle(ChatFormatting.GRAY));
	}
}
