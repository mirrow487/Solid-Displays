package com.soliddisplays.menu;

import com.soliddisplays.block.DesignTableBlockEntity;
import com.soliddisplays.model.ModelData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.soliddisplays.registry.ModBlocks;
import com.soliddisplays.registry.ModItems;
import com.soliddisplays.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container side of the Design Table: one blueprint slot plus the player
 * inventory. The design itself travels through custom packets, not slots.
 */
public class DesignTableMenu extends AbstractContainerMenu {
	public static final int BLUEPRINT_SLOT = 0;
	/** Screen-space position of the blueprint slot (used by the client screen). */
	public static final int SLOT_X = 8;
	public static final int SLOT_Y = 18;
	public static final int INVENTORY_Y = 50;

	private final Container blueprintContainer;
	private final ContainerLevelAccess access;
	private final BlockPos tablePos;

	/**
	 * Data sent to the client when the screen opens: which table, and the
	 * draft it holds so editing continues where it stopped.
	 */
	public record OpenData(BlockPos pos, ModelData draft) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, OpenData::pos,
				ModelData.STREAM_CODEC, OpenData::draft,
				OpenData::new);
	}

	private final ModelData initialDraft;
	/**
	 * Client only: the designer hides the inventory while another tab is
	 * shown. Hidden slots are neither drawn nor clickable.
	 */
	private boolean slotsVisible = true;

	/** Client constructor (opened via {@link ModMenus#DESIGN_TABLE}). */
	public DesignTableMenu(int syncId, Inventory inventory, OpenData data) {
		this(syncId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL, data.pos(), data.draft());
	}

	/** Server constructor. */
	public DesignTableMenu(int syncId, Inventory inventory, DesignTableBlockEntity table) {
		this(syncId, inventory, table.getBlueprintSlot(),
				ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()), table.getBlockPos(), table.getDraft());
	}

	private DesignTableMenu(int syncId, Inventory inventory, Container blueprintContainer,
			ContainerLevelAccess access, BlockPos pos, ModelData draft) {
		super(ModMenus.DESIGN_TABLE, syncId);
		this.blueprintContainer = blueprintContainer;
		this.access = access;
		this.tablePos = pos;
		this.initialDraft = draft;

		addSlot(new ToggleSlot(blueprintContainer, 0, SLOT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return isBlueprint(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		// Same layout as addStandardInventorySlots, but with hideable slots.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new ToggleSlot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new ToggleSlot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
		}
	}

	/** A slot that disappears while {@link #slotsVisible} is false. */
	private class ToggleSlot extends Slot {
		ToggleSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean isActive() {
			return slotsVisible;
		}
	}

	public ModelData getInitialDraft() {
		return initialDraft;
	}

	public void setSlotsVisible(boolean visible) {
		this.slotsVisible = visible;
	}

	public boolean areSlotsVisible() {
		return slotsVisible;
	}

	public static boolean isBlueprint(ItemStack stack) {
		return stack.is(ModItems.EMPTY_BLUEPRINT) || stack.is(ModItems.BLUEPRINT);
	}

	public BlockPos getTablePos() {
		return tablePos;
	}

	public ItemStack getBlueprint() {
		return blueprintContainer.getItem(0);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, ModBlocks.DESIGN_TABLE);
	}

	/** Shift-click: move blueprints into the table slot, everything else between inventory sections. */
	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int invStart = 1;
		int invEnd = slots.size();

		if (index == BLUEPRINT_SLOT) {
			if (!moveItemStackTo(stack, invStart, invEnd, true)) {
				return ItemStack.EMPTY;
			}
		} else if (isBlueprint(stack) && !slots.get(BLUEPRINT_SLOT).hasItem()) {
			// Only move one blueprint into the single slot.
			ItemStack one = stack.split(1);
			slots.get(BLUEPRINT_SLOT).setByPlayer(one);
		} else {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}
}
