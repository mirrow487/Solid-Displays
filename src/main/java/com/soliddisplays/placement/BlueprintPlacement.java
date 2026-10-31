package com.soliddisplays.placement;

import com.soliddisplays.model.ModelData;
import com.soliddisplays.registry.ModComponents;
import com.soliddisplays.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Rules shared by the client ghost and the server placement, so what the
 * player sees is exactly what gets placed.
 */
public final class BlueprintPlacement {
	private BlueprintPlacement() {
	}

	/**
	 * The anchor block for a click: the clicked block itself if it can be
	 * replaced (grass, snow layer…), otherwise the block in front of the
	 * clicked face, like normal block placement.
	 */
	public static BlockPos anchorFor(Level level, BlockPos clicked, Direction face) {
		return level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
	}

	public static boolean isFilledBlueprint(ItemStack stack) {
		return stack.is(ModItems.BLUEPRINT) && !stack.getOrDefault(ModComponents.MODEL_DATA, ModelData.EMPTY).isEmpty();
	}

	public static int rotation(ItemStack stack) {
		return Math.floorMod(stack.getOrDefault(ModComponents.ROTATION, 0), 4);
	}

	/** Turns the blueprint by {@code steps} quarter turns (negative = the other way). */
	public static void rotate(ItemStack stack, int steps) {
		stack.set(ModComponents.ROTATION, Math.floorMod(rotation(stack) + steps, 4));
	}
}
