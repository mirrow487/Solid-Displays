package com.soliddisplays.menu;

import com.soliddisplays.block.DesignTableBlockEntity;
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

	/** Client constructor (opened via {@link ModMenus#DESIGN_TABLE}). */
	public DesignTableMenu(int syncId, Inventory inventory, BlockPos pos) {
		this(syncId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL, pos);
	}

	/** Server constructor. */
	public DesignTableMenu(int syncId, Inventory inventory, DesignTableBlockEntity table) {
		this(syncId, inventory, table.getBlueprintSlot(),
				ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()), table.getBlockPos());
	}

	private DesignTableMenu(int syncId, Inventory inventory, Container blueprintContainer,
			ContainerLevelAccess access, BlockPos pos) {
		super(ModMenus.DESIGN_TABLE, syncId);
		this.blueprintContainer = blueprintContainer;
		this.access = access;
		this.tablePos = pos;

		addSlot(new Slot(blueprintContainer, 0, SLOT_X, SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return isBlueprint(stack);
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addStandardInventorySlots(inventory, 8, INVENTORY_Y);
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
