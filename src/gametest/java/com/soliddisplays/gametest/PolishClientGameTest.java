package com.soliddisplays.gametest;

import com.soliddisplays.client.screen.DesignTableScreen;
import com.soliddisplays.model.ModelData;
import com.soliddisplays.model.TestModels;
import com.soliddisplays.placement.ModelPlacer;
import com.soliddisplays.placement.PlacedModels;
import com.soliddisplays.registry.ModBlocks;
import com.soliddisplays.registry.ModComponents;
import com.soliddisplays.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Stage 5: loading a blueprint back into the editor, owner protection
 * against another player, the reload command, and pictures of the new
 * block/item textures and the light editor theme.
 */
public class PolishClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		String only = System.getenv("SD_ONLY");
		if (only != null && !only.equals(getClass().getSimpleName())) {
			return;
		}
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("gamerule spawn_mobs false");
			BlockPos ground = server.computeOnServer(s -> s.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.ZERO));

			// --- textures: a row of design tables, seen from two sides, and the items in the hotbar
			server.runOnServer(s -> {
				for (int i = 0; i < 3; i++) {
					s.overworld().setBlockAndUpdate(ground.offset(i * 2, 0, 0), ModBlocks.DESIGN_TABLE.defaultBlockState());
				}
				ServerPlayer p = player(s);
				ItemStack filled = new ItemStack(ModItems.BLUEPRINT);
				filled.set(ModComponents.MODEL_DATA, TestModels.DEMO);
				p.getInventory().setItem(0, new ItemStack(ModItems.EMPTY_BLUEPRINT));
				p.getInventory().setItem(1, filled);
				p.getInventory().setItem(2, new ItemStack(ModItems.DISMANTLE_TOOL));
				p.getInventory().setItem(3, new ItemStack(ModItems.DESIGN_TABLE));
			});
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(4));
			server.runCommand(String.format("tp @a %d.5 %d %d.5 -50 30", ground.getX() - 2, ground.getY() + 1, ground.getZ() - 3));
			ctx.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("stage5_design_table_front");
			server.runCommand(String.format("tp @a %d.5 %d %d.5 130 30", ground.getX() + 6, ground.getY() + 1, ground.getZ() + 3));
			ctx.waitTicks(20);
			ctx.takeScreenshot("stage5_design_table_back");

			// --- load a blueprint back into the editor
			BlockPos table = ground;
			ItemStack named = new ItemStack(ModItems.BLUEPRINT);
			named.set(ModComponents.MODEL_DATA, TestModels.DEMO.withName("Bench"));
			server.runOnServer(s -> DesignerClientGameTest.table(s, table).getBlueprintSlot().setItem(0, named.copy()));
			DesignerClientGameTest.openTable(ctx, server, table);
			check(DesignerClientGameTest.state(ctx).size() == 0, "editor should start empty");
			DesignerClientGameTest.clickButtonContaining(ctx, "Blueprint", "Blueprint");
			ctx.waitTicks(2);
			DesignerClientGameTest.clickButtonContaining(ctx, "Yükle", "Load");
			ctx.waitTicks(3);
			ModelData loaded = DesignerClientGameTest.state(ctx).toModel();
			check(loaded.elements().equals(TestModels.DEMO.elements()) && loaded.name().equals("Bench"), "load from blueprint failed: " + loaded);
			System.out.println("[SolidDisplays test] OK loaded blueprint into the editor");
			ctx.takeScreenshot("stage5_editor_blueprint_tab");
			DesignerClientGameTest.clickButtonContaining(ctx, "Parça", "Part");
			ctx.runOnClient(mc -> ((DesignTableScreen) mc.gui.screen()).editorState().select(1));
			ctx.waitTicks(3);
			ctx.takeScreenshot("stage5_editor_part_tab");
			ctx.setScreen(() -> null);
			ctx.waitTicks(5);

			// --- another player cannot dismantle my model, even with two clicks
			BlockPos anchor = ground.offset(0, 0, 8);
			server.runCommand(String.format("tp @a %d %d %d", anchor.getX() - 3, anchor.getY(), anchor.getZ() - 3));
			ctx.waitTicks(5);
			UUID model = server.computeOnServer(s -> ModelPlacer.place(s.overworld(), player(s), TestModels.DEMO, anchor, 0, List.of())
					.model().orElseThrow().id());
			server.runOnServer(s -> {
				ServerPlayer stranger = FakePlayer.get(s.overworld(), new GameProfile(UUID.randomUUID(), "Stranger"));
				ModelPlacer.requestDismantle(s.overworld(), stranger, model);
				ModelPlacer.requestDismantle(s.overworld(), stranger, model);
			});
			boolean stillThere = server.computeOnServer(s -> PlacedModels.get(s.overworld()).contains(model));
			check(stillThere, "a different player dismantled my model");
			server.runOnServer(s -> {
				ModelPlacer.requestDismantle(s.overworld(), player(s), model);
				ModelPlacer.requestDismantle(s.overworld(), player(s), model);
			});
			boolean gone = server.computeOnServer(s -> !PlacedModels.get(s.overworld()).contains(model));
			check(gone, "the owner could not dismantle the model");
			System.out.println("[SolidDisplays test] OK only the owner can dismantle");

			// --- reload command works for operators
			server.runCommand("soliddisplays reload");
			System.out.println("[SolidDisplays test] OK reload command");
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
