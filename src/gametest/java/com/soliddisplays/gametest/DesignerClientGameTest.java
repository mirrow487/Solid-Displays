package com.soliddisplays.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.soliddisplays.block.DesignTableBlockEntity;
import com.soliddisplays.client.editor.EditorState;
import com.soliddisplays.client.screen.DesignTableScreen;
import com.soliddisplays.item.BlueprintItem;
import com.soliddisplays.model.ElementKind;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.TestModels;
import com.soliddisplays.network.SaveBlueprintPayload;
import com.soliddisplays.registry.ModBlocks;
import com.soliddisplays.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Drives the Design Table screen with real mouse and keyboard input:
 * picker search, gizmo dragging, number fields, undo, saving to a blueprint,
 * draft persistence, and server-side rejection of forged packets.
 */
public class DesignerClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		// SD_ONLY=<ClassName> runs a single test class (handy while iterating).
		String only = System.getenv("SD_ONLY");
		if (only != null && !only.equals(getClass().getSimpleName())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			BlockPos table = server.computeOnServer(s -> {
				BlockPos ground = s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO).offset(2, 0, 2);
				s.overworld().setBlockAndUpdate(ground, ModBlocks.DESIGN_TABLE.defaultBlockState());
				return ground;
			});
			openTable(ctx, server, table);
			ctx.takeScreenshot("stage3_designer_empty");

			// --- search + click the first result adds a part
			click(ctx, 40, 10);
			ctx.getInput().typeChars("oak planks");
			ctx.waitTicks(2);
			Vector2f cell = screen(ctx).firstPickerCell();
			click(ctx, cell.x, cell.y);
			ctx.waitTicks(2);
			ModelElement first = state(ctx).selected();
			check(state(ctx).size() == 1 && first.id().equals(Identifier.withDefaultNamespace("oak_planks"))
					&& first.kind() == ElementKind.BLOCK, "picker did not add oak planks: " + state(ctx).elements());
			System.out.println("[SolidDisplays test] OK picker search + add");

			// --- drag the X move arrow: position.x grows by whole snap steps
			ctx.runOnClient(mc -> state(mc).setSnapIndex(0)); // 1 block
			Vector2f tip = ctx.computeOnClient(mc -> screen(mc).handleScreenPosition(0, 1, false));
			dragAlong(ctx, tip, directionOfAxis(ctx, 0), 3.2f);
			float x = state(ctx).selected().position().x();
			check(x == 3f, "move gizmo: expected x=3 after dragging 3.2 blocks with 1-block snap, got " + x);
			System.out.println("[SolidDisplays test] OK move gizmo x=" + x);

			// --- frame the camera on the model again (the part moved), then scale:
			clickButtonContaining(ctx, "Ortala", "Frame");
			ctx.waitTicks(2);
			// drag the +Y face up by 1.5 blocks with 1/2 snap -> height 2.5, bottom stays at 0
			ctx.runOnClient(mc -> state(mc).setSnapIndex(1));
			Vector2f top = ctx.computeOnClient(mc -> screen(mc).handleScreenPosition(1, 1, true));
			dragAlong(ctx, top, directionOfAxis(ctx, 1), 1.5f);
			ModelElement scaled = state(ctx).selected();
			check(scaled.size().y() == 2.5f && Math.abs(scaled.position().y()) < 1e-5,
					"scale gizmo: expected height 2.5 with bottom at 0, got " + scaled);
			System.out.println("[SolidDisplays test] OK scale gizmo size=" + scaled.size());
			ctx.takeScreenshot("stage3_designer_scaled");

			// --- type a number into the Z position field, then undo it with Ctrl+Z
			typeIntoField(ctx, "1.25");
			float z = state(ctx).selected().position().z();
			check(z == 1.25f, "number field: expected z=1.25, got " + z);
			// (Ctrl+Z works in game; the test input cannot send modifier flags under SDL, so click Undo.)
			clickButtonContaining(ctx, "Geri", "Undo");
			ctx.waitTicks(2);
			z = state(ctx).selected().position().z();
			check(z == 0f, "undo: expected z=0, got " + z);
			System.out.println("[SolidDisplays test] OK number field + undo");

			// --- load the demo model and take pictures of both gizmos
			ctx.runOnClient(mc -> screen(mc).reloadEditor(TestModels.DEMO.withName("Demo")));
			ctx.runOnClient(mc -> state(mc).select(1));
			ctx.computeOnClient(mc -> screen(mc).handleScreenPosition(0, 1, false));
			ctx.waitTicks(3);
			ctx.takeScreenshot("stage3_designer_demo_move");
			ctx.runOnClient(mc -> state(mc).select(6));
			ctx.computeOnClient(mc -> screen(mc).handleScreenPosition(0, 1, true));
			ctx.waitTicks(3);
			ctx.takeScreenshot("stage3_designer_demo_scale");

			// --- save into the blueprint slot
			server.runOnServer(s -> table(s, table).getBlueprintSlot().setItem(0, new ItemStack(ModItems.EMPTY_BLUEPRINT)));
			ctx.waitTicks(5);
			clickButtonContaining(ctx, "Blueprint", "Blueprint");
			ctx.waitTicks(3);
			ctx.takeScreenshot("stage3_designer_save_tab");
			clickButtonContaining(ctx, "Kaydet", "Save");
			ctx.waitTicks(10);
			ModelData saved = server.computeOnServer(s -> BlueprintItem.getModel(table(s, table).getBlueprintSlot().getItem(0)));
			boolean isFilled = server.computeOnServer(s -> table(s, table).getBlueprintSlot().getItem(0).is(ModItems.BLUEPRINT));
			check(isFilled && saved.name().equals("Demo") && saved.elements().equals(TestModels.DEMO.elements()),
					"blueprint not saved correctly: " + saved);
			ctx.takeScreenshot("stage3_designer_saved");
			System.out.println("[SolidDisplays test] OK saved blueprint '" + saved.name() + "' with " + saved.elements().size() + " parts");

			// --- how it looks at 1920x1080: automatic GUI scale (480x270, the tightest common case) and GUI scale 2
			ctx.getInput().resizeWindow(1920, 1080);
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
			ctx.waitTicks(5);
			clickButtonContaining(ctx, "Parça", "Part");
			ctx.runOnClient(mc -> state(mc).select(1));
			ctx.computeOnClient(mc -> screen(mc).handleScreenPosition(0, 1, false));
			clickButtonContaining(ctx, "Ortala", "Frame");
			ctx.getInput().setCursorPos(0, 0);
			ctx.waitTicks(5);
			ctx.takeScreenshot("stage3_designer_1080p_auto_scale");
			ctx.runOnClient(mc -> {
				mc.options.guiScale().set(2);
				mc.resizeGui();
			});
			ctx.waitTicks(5);
			clickButtonContaining(ctx, "Ortala", "Frame");
			ctx.getInput().setCursorPos(0, 0);
			ctx.waitTicks(5);
			ctx.takeScreenshot("stage3_designer_1080p_scale2");

			// --- forged packets are rejected by the server
			ModelData bad = new ModelData("cheat", List.of(new ModelElement(ElementKind.BLOCK, Identifier.withDefaultNamespace("barrier"),
					new Vector3f(), new Vector3f(1, 1, 1), new Vector3f(), true)));
			ModelData huge = new ModelData("huge", List.of(new ModelElement(ElementKind.BLOCK, Identifier.withDefaultNamespace("stone"),
					new Vector3f(), new Vector3f(500, 1, 1), new Vector3f(), true)));
			ctx.runOnClient(mc -> {
				ClientPlayNetworking.send(new SaveBlueprintPayload(table, bad));
				ClientPlayNetworking.send(new SaveBlueprintPayload(table, huge));
				ClientPlayNetworking.send(new SaveBlueprintPayload(table.above(5), TestModels.DEMO)); // not the open table
			});
			ctx.waitTicks(10);
			ModelData after = server.computeOnServer(s -> BlueprintItem.getModel(table(s, table).getBlueprintSlot().getItem(0)));
			check(after.equals(saved), "server accepted a forged design: " + after);
			System.out.println("[SolidDisplays test] OK forged designs rejected");

			// --- close and reopen: the draft comes back
			ctx.setScreen(() -> null);
			ctx.waitTicks(10);
			openTable(ctx, server, table);
			check(state(ctx).toModel().elements().equals(TestModels.DEMO.elements()), "draft not restored after reopening");
			System.out.println("[SolidDisplays test] OK draft restored after reopening");
			ctx.setScreen(() -> null);
		}
	}

	// ---- helpers ------------------------------------------------------------

	static void openTable(ClientGameTestContext ctx, TestServerContext server, BlockPos pos) {
		server.runOnServer(s -> player(s).openMenu(table(s, pos)));
		ctx.waitForScreen(DesignTableScreen.class);
		ctx.waitTicks(5);
	}

	static DesignTableBlockEntity table(MinecraftServer s, BlockPos pos) {
		return (DesignTableBlockEntity) s.overworld().getBlockEntity(pos);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static DesignTableScreen screen(net.minecraft.client.Minecraft mc) {
		return (DesignTableScreen) mc.gui.screen();
	}

	private static DesignTableScreen screen(ClientGameTestContext ctx) {
		return ctx.computeOnClient(DesignerClientGameTest::screen);
	}

	private static EditorState state(net.minecraft.client.Minecraft mc) {
		return screen(mc).editorState();
	}

	static EditorState state(ClientGameTestContext ctx) {
		return ctx.computeOnClient(DesignerClientGameTest::state);
	}

	private static double guiScale(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
	}

	static void click(ClientGameTestContext ctx, float guiX, float guiY) {
		double s = guiScale(ctx);
		ctx.getInput().setCursorPos(guiX * s, guiY * s);
		ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
	}

	/** On-screen movement (GUI pixels) for one block along a world axis. */
	private static Vector2f directionOfAxis(ClientGameTestContext ctx, int axis) {
		return ctx.computeOnClient(mc -> {
			Vector3f d = new Vector3f();
			d.setComponent(axis, 1f);
			Vector3f v = screen(mc).camera().view().transformDirection(d);
			return new Vector2f(v.x, v.y);
		});
	}

	/** Presses at {@code from}, moves {@code blocks} along the on-screen axis in small steps, releases. */
	private static void dragAlong(ClientGameTestContext ctx, Vector2f from, Vector2f perBlock, float blocks) {
		double s = guiScale(ctx);
		ctx.getInput().setCursorPos(from.x * s, from.y * s);
		ctx.waitTick();
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		int steps = 10;
		for (int i = 1; i <= steps; i++) {
			float t = blocks * i / steps;
			ctx.getInput().setCursorPos((from.x + perBlock.x * t) * s, (from.y + perBlock.y * t) * s);
			ctx.waitTick();
		}
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
	}

	/** Clicks the Z position field, replaces its text and presses Enter. */
	private static void typeIntoField(ClientGameTestContext ctx, String text) {
		Vector2f p = ctx.computeOnClient(mc -> screen(mc).positionZField());
		click(ctx, p.x, p.y);
		ctx.getInput().pressKey(InputConstants.KEY_END);
		for (int i = 0; i < 12; i++) {
			ctx.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		}
		ctx.getInput().typeChars(text);
		ctx.getInput().pressKey(InputConstants.KEY_RETURN);
		ctx.waitTicks(2);
	}

	static void clickButtonContaining(ClientGameTestContext ctx, String tr, String en) {
		Vector2f p = ctx.computeOnClient(mc -> {
			for (var child : mc.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.Button b && b.visible) {
					String label = b.getMessage().getString();
					if (label.equals(tr) || label.equals(en)) {
						return new Vector2f(b.getX() + b.getWidth() / 2f, b.getY() + b.getHeight() / 2f);
					}
				}
			}
			throw new AssertionError("button not found: " + en);
		});
		click(ctx, p.x, p.y);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
