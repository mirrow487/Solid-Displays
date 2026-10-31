package com.soliddisplays.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.soliddisplays.client.blueprint.GhostRenderer;
import com.soliddisplays.entity.ModelColliderEntity;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.ModelElement;
import com.soliddisplays.model.ModelGeometry;
import com.soliddisplays.model.TestModels;
import com.soliddisplays.placement.BlueprintPlacement;
import com.soliddisplays.placement.Materials;
import com.soliddisplays.placement.ModelPlacer;
import com.soliddisplays.placement.PlacedModel;
import com.soliddisplays.placement.PlacedModels;
import com.soliddisplays.registry.ModComponents;
import com.soliddisplays.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Using a filled blueprint with real input: tooltip, ghost, R / Shift+scroll
 * rotation, right-click placement with and without materials, and taking the
 * model apart with the Dismantle Tool.
 */
public class BlueprintClientGameTest implements FabricClientGameTest {
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
			// Stand 4 blocks south, within survival reach (4.5 blocks), looking at the ground.
			server.runCommand(String.format("tp @a %d.5 %d %d.5 180 0", ground.getX(), ground.getY(), ground.getZ() + 4));
			server.runCommand("clear @a");
			ItemStack blueprint = new ItemStack(ModItems.BLUEPRINT);
			blueprint.set(ModComponents.MODEL_DATA, TestModels.DEMO.withName("Demo"));
			server.runOnServer(s -> player(s).getInventory().setItem(0, blueprint.copy()));
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.waitTicks(10);
			ctx.getInput().lookAt(ground.below());
			ctx.waitTicks(5);

			// --- tooltip: every material is missing (red ✘)
			List<String> tooltip = ctx.computeOnClient(mc -> mc.player.getMainHandItem()
					.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
					.stream().map(Component::getString).toList());
			long missing = tooltip.stream().filter(l -> l.contains("✘")).count();
			check(missing == MaterialCalculator.cost(TestModels.DEMO).size(), "tooltip should list all materials as missing: " + tooltip);
			System.out.println("[SolidDisplays test] OK tooltip: " + tooltip);

			// --- ghost sits on top of the block under the crosshair
			BlockPos anchor = ctx.computeOnClient(GhostRenderer::currentAnchor);
			BlockPos aimed = ctx.computeOnClient(mc -> ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos());
			check(anchor != null && anchor.equals(aimed.above()), "ghost anchor " + anchor + ", aimed at " + aimed);
			System.out.println("[SolidDisplays test] OK ghost anchor " + anchor);

			// --- R rotates (client and server), Shift+scroll rotates without changing the hotbar slot
			ctx.getInput().pressKey(InputConstants.KEY_R);
			ctx.waitTicks(3);
			int afterR = ctx.computeOnClient(mc -> BlueprintPlacement.rotation(mc.player.getMainHandItem()));
			int serverAfterR = server.computeOnServer(s -> BlueprintPlacement.rotation(player(s).getMainHandItem()));
			check(afterR == 1 && serverAfterR == 1, "R key: client " + afterR + ", server " + serverAfterR);
			ctx.getInput().holdKey(o -> o.keyShift);
			ctx.waitTick();
			ctx.getInput().scroll(1);
			ctx.waitTicks(3);
			ctx.getInput().releaseKey(o -> o.keyShift);
			int afterScroll = ctx.computeOnClient(mc -> BlueprintPlacement.rotation(mc.player.getMainHandItem()));
			int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
			check(afterScroll == 2 && slot == 0, "Shift+scroll: rotation " + afterScroll + ", hotbar slot " + slot);
			System.out.println("[SolidDisplays test] OK rotate with R and Shift+scroll (180°)");
			ctx.waitTicks(5);
			ctx.takeScreenshot("stage4_ghost_missing_materials");

			// --- right-click without materials: refused, nothing placed
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(5);
			int models = server.computeOnServer(s -> PlacedModels.get(s.overworld()).all().size());
			check(models == 0, "placed without materials");
			System.out.println("[SolidDisplays test] OK refused without materials");

			// --- with materials: the model appears exactly where the ghost was, materials are used up
			server.runOnServer(s -> Materials.toStacks(MaterialCalculator.cost(TestModels.DEMO))
					.forEach(st -> player(s).getInventory().add(st)));
			ctx.waitTicks(5);
			ctx.takeScreenshot("stage4_ghost_ok");
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			PlacedModel placed = server.computeOnServer(s -> PlacedModels.get(s.overworld()).all().stream().findFirst().orElse(null));
			check(placed != null && placed.anchor().equals(anchor), "model not placed at the ghost: " + placed);
			int expectedPieces = ModelPlacer.colliderPieces(TestModels.DEMO, 2).size();
			List<AABB> boxes = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ModelColliderEntity.class,
					new AABB(anchor).inflate(8)).stream().map(e -> e.getBoundingBox().move(-anchor.getX(), -anchor.getY(), -anchor.getZ())).toList());
			List<AABB> expected = ModelPlacer.colliderPieces(TestModels.DEMO, 2).stream().map(ModelPlacer.Piece::box).toList();
			check(boxes.size() == expectedPieces && boxes.containsAll(expected), "placed boxes do not match a 180° rotation");
			boolean used = server.computeOnServer(s -> MaterialCalculator.cost(TestModels.DEMO).stream()
					.allMatch(c -> Materials.count(player(s).getInventory(), c.getItem()) == 0));
			boolean kept = server.computeOnServer(s -> player(s).getMainHandItem().is(ModItems.BLUEPRINT));
			check(used && kept, "materials used: " + used + ", blueprint kept: " + kept);
			System.out.println("[SolidDisplays test] OK placed at the ghost (180°), materials used, blueprint kept");
			server.runCommand(String.format("tp @a %d.5 %d %d.5 160 25", anchor.getX() + 2, anchor.getY(), anchor.getZ() + 5));
			ctx.waitTicks(10);
			ctx.takeScreenshot("stage4_placed");

			// --- Dismantle Tool: aim at the pillar, click twice -> gone, materials drop
			server.runOnServer(s -> player(s).getInventory().setItem(0, new ItemStack(ModItems.DISMANTLE_TOOL)));
			ModelElement pillar = TestModels.DEMO.elements().get(7);
			Vector3f c = ModelGeometry.rotatedCenter(pillar, 2).add(anchor.getX(), anchor.getY() + 1, anchor.getZ());
			// Survival entity reach is 3 blocks: step next to the pillar (outside the model) first.
			server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %d %.2f", c.x, anchor.getY(), c.z - 2.5));
			ctx.waitTicks(5);
			ctx.getInput().lookAt(new BlockPos((int) Math.floor(c.x), (int) Math.floor(c.y), (int) Math.floor(c.z)));
			ctx.waitTicks(5);
			boolean aimingAtPart = ctx.computeOnClient(mc -> mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
					&& hit.getEntity() instanceof ModelColliderEntity);
			check(aimingAtPart, "crosshair is not on a model part");
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(5);
			int stillThere = server.computeOnServer(s -> PlacedModels.get(s.overworld()).all().size());
			check(stillThere == 1, "first click must only ask for confirmation");
			ctx.getInput().pressKey(o -> o.keyUse);
			ctx.waitTicks(10);
			int gone = server.computeOnServer(s -> PlacedModels.get(s.overworld()).all().size());
			int dropped = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(6))
					.stream().mapToInt(e -> e.getItem().getCount()).sum());
			int cost = MaterialCalculator.cost(TestModels.DEMO).stream().mapToInt(ItemStack::getCount).sum();
			check(gone == 0 && dropped == cost, "dismantle tool: models left " + gone + ", items dropped " + dropped + "/" + cost);
			System.out.println("[SolidDisplays test] OK dismantle tool, " + dropped + " items dropped");
			ctx.takeScreenshot("stage4_dismantled");
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
