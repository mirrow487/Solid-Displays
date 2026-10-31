package com.soliddisplays.placement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Counting and taking materials from a player's inventory (server side). */
public final class Materials {
	private Materials() {
	}

	/** How many of {@code item} the inventory holds (undamaged stacks only). */
	public static int count(Inventory inventory, Item item) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item) && !stack.isDamaged()) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** The part of {@code cost} the inventory does not cover (empty list = affordable). */
	public static List<ItemStack> missing(Inventory inventory, List<ItemStack> cost) {
		List<ItemStack> missing = new ArrayList<>();
		for (ItemStack needed : cost) {
			int have = count(inventory, needed.getItem());
			if (have < needed.getCount()) {
				missing.add(new ItemStack(needed.getItem(), needed.getCount() - have));
			}
		}
		return missing;
	}

	/** Removes {@code cost} from the inventory. Call only after {@link #missing} returned empty. */
	public static void take(Inventory inventory, List<ItemStack> cost) {
		for (ItemStack needed : cost) {
			int left = needed.getCount();
			for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.is(needed.getItem()) && !stack.isDamaged()) {
					int n = Math.min(left, stack.getCount());
					stack.shrink(n);
					left -= n;
				}
			}
		}
		inventory.setChanged();
	}

	/** Splits large counts into normal max-size stacks (for dropping in the world). */
	public static List<ItemStack> toStacks(List<ItemStack> items) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : items) {
			int left = s.getCount();
			int max = s.getMaxStackSize();
			while (left > 0) {
				int n = Math.min(left, max);
				out.add(s.copyWithCount(n));
				left -= n;
			}
		}
		return out;
	}
}
