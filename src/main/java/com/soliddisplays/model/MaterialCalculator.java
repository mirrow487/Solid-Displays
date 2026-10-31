package com.soliddisplays.model;

import com.soliddisplays.config.SDConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Works out which items a model costs.
 * <p>
 * Formula (all numbers from {@link SDConfig}):
 * <pre>
 *   amount(item) = max(minPerBlockType, ceil(totalVolume(item) × volumeMultiplier))
 * </pre>
 * Volumes of all parts made of the same block are added up <i>before</i>
 * rounding, so eight 1/2-block parts cost 1 block and not 8. One full block
 * of volume costs one item with the default multiplier of 1.0.
 * <p>
 * Used on the server (to charge and refund) and on the client (tooltip).
 */
public final class MaterialCalculator {
	private MaterialCalculator() {
	}

	/** The item a part consumes, or {@link Items#AIR} if it has none (e.g. a block without an item form). */
	public static Item itemFor(ModelElement element) {
		if (element.kind() == ElementKind.BLOCK) {
			return BuiltInRegistries.BLOCK.getValue(element.id()).asItem();
		}
		return BuiltInRegistries.ITEM.getValue(element.id());
	}

	/** Cost of a model as a list of stacks (one per item type, possibly above 64). */
	public static List<ItemStack> cost(ModelData model) {
		SDConfig config = SDConfig.get();
		Map<Item, Double> volumes = new LinkedHashMap<>();
		for (ModelElement element : model.elements()) {
			Item item = itemFor(element);
			if (item != Items.AIR) {
				volumes.merge(item, (double) element.volume(), Double::sum);
			}
		}
		List<ItemStack> out = new ArrayList<>();
		volumes.forEach((item, volume) -> {
			// Tiny epsilon so 0.1+0.2+... float sums that should be 3.0 don't become 4.
			int amount = (int) Math.ceil(volume * config.volumeMultiplier - 1e-6);
			amount = Math.max(config.minPerBlockType, amount);
			if (amount > 0) {
				out.add(new ItemStack(item, amount));
			}
		});
		return out;
	}
}
