package com.soliddisplays.gametest;

import com.soliddisplays.entity.ModelColliderEntity;
import com.soliddisplays.model.MaterialCalculator;
import com.soliddisplays.model.TestModels;
import com.soliddisplays.placement.Materials;
import com.soliddisplays.placement.ModelPlacer;
import com.soliddisplays.placement.PlacedModel;
import com.soliddisplays.placement.PlacedModels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * End-to-end collision test in a real client, using real player physics
 * (keyboard input, gravity, step-up), plus persistence and a dedicated server.
 * <p>
 * 10 elements -> 10 displays + collider pieces (platform and pillar are split, the 45° block
 * is approximated by several boxes).
 * Model-space layout of {@link TestModels#DEMO} (anchor = origin, no rotation):
 * platform x0..5 z0..3 h0.5, table top y1.375..1.5 over x0.5..2.5 z0.5..1.5,
 * pillar x4.25..4.75 z2.25..2.75 y0.5..4.5, poppy (no collision) x0.25..0.75 z2.25..2.75.
 */
public class CollisionClientGameTest implements FabricClientGameTest {
	private static final double EPS = 1e-4;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		// SD_ONLY=<ClassName> runs a single test class (handy while iterating).
		String only = System.getenv("SD_ONLY");
		if (only != null && !only.equals(getClass().getSimpleName())) {
			return;
		}
		singleplayer(ctx);
		dedicatedServer(ctx);
	}

	// ---- Singleplayer: physics, sync, persistence, dismantle ------------------

	private void singleplayer(ClientGameTestContext ctx) {
		BlockPos anchor;
		UUID modelA;
		TestWorldSave save;
		int pieces = ModelPlacer.colliderPieces(TestModels.DEMO, 0).size();
		int perModel = 10 + pieces;
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			save = sp.getWorldSave();
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("gamerule spawn_mobs false");

			anchor = server.computeOnServer(s -> groundAt(s.overworld(), 0, 0));
			BlockPos a = anchor;
			// The player spawns where the model goes; placement refuses to trap them, so step aside.
			teleport(server, anchor, -6, 0, -6, 0f);
			ctx.waitTicks(5);
			modelA = server.computeOnServer(s -> ModelPlacer.place(s.overworld(), player(s), TestModels.DEMO, a, 0, List.of())
					.model().orElseThrow().id());
			ctx.waitTicks(10);

			// --- client and server must see exactly the same boxes
			List<AABB> serverBoxes = server.computeOnServer(s -> colliderBoxes(s.overworld().getAllEntities()));
			List<AABB> clientBoxes = ctx.computeOnClient(mc -> colliderBoxes(mc.level.entitiesForRendering()));
			System.out.println("[SolidDisplays test] test model has " + pieces + " collider pieces");
			check(pieces > 12, "rotated block should be approximated by several boxes");
			check(serverBoxes.size() == pieces, "expected " + pieces + " collider pieces, got " + serverBoxes.size());
			check(serverBoxes.equals(clientBoxes), "client boxes differ from server boxes\n" + serverBoxes + "\n" + clientBoxes);
			int parts = server.computeOnServer(s -> countParts(s.overworld()));
			check(parts == perModel, "expected 10 displays + " + pieces + " colliders, got " + parts);

			double base = anchor.getY();
			// --- stand on the platform (middle, on a split seam, and overhanging an edge)
			standOn(ctx, server, anchor, 3.5, 2.5, base + 0.5, "platform middle");
			standOn(ctx, server, anchor, 2.5, 2.5, base + 0.5, "platform seam between split pieces");
			standOn(ctx, server, anchor, 0.2, 2.85, base + 0.5, "platform edge overhang");
			// --- 1/8-thick table top on 1/8 legs
			standOn(ctx, server, anchor, 1.5, 1.0, base + 1.5, "table top");
			// --- top of the 4-block pillar (upper split piece)
			standOn(ctx, server, anchor, 4.5, 2.5, base + 4.5, "pillar top");

			// --- 45° rotated block: follows the real shape, not the enclosing box.
			// An item dropped in the empty corner of the enclosing box must fall past the
			// block onto the platform; one dropped on the middle must stay on top.
			double cornerY = dropItemAndGetY(ctx, server, anchor, 3.15, 0.15);
			double middleY = dropItemAndGetY(ctx, server, anchor, 3.75, 0.75);
			check(Math.abs(cornerY - (base + 0.5)) < 0.01, "item in the corner should land on the platform, y=" + cornerY);
			check(Math.abs(middleY - (base + 1.5)) < 0.01, "item on the rotated block should rest on top, y=" + middleY);
			System.out.println("[SolidDisplays test] OK rotated block: corner item fell to " + cornerY + ", middle item rests at " + middleY);

			// --- walk north into the pillar: step up onto the platform, then stop at the pillar
			teleport(server, anchor, 4.5, 0, 4.0, 180f);
			ctx.waitTicks(10);
			ctx.getInput().holdKeyFor(o -> o.keyUp, 40);
			ctx.waitTicks(5);
			Vec3 p = ctx.computeOnClient(mc -> mc.player.position());
			check(p.y > base + 0.5 - EPS, "did not step up onto the platform: " + p);
			check(p.z - anchor.getZ() > 2.75 + 0.3 - 0.01, "walked through the pillar: " + p);
			System.out.println("[SolidDisplays test] OK walked into pillar, stopped at z=" + (p.z - anchor.getZ()));

			// --- walk south through the poppy (no collision) and off the platform
			teleport(server, anchor, 0.5, 0.6, 1.9, 0f);
			ctx.waitTicks(10);
			ctx.getInput().holdKeyFor(o -> o.keyUp, 20);
			ctx.waitTicks(10);
			p = ctx.computeOnClient(mc -> mc.player.position());
			check(p.z - anchor.getZ() > 3.0, "blocked by a part without collision: " + p);
			System.out.println("[SolidDisplays test] OK walked through poppy, z=" + (p.z - anchor.getZ()));

			// --- pistons and explosions must not move or break anything
			List<AABB> boxesBefore = server.computeOnServer(s -> colliderBoxes(s.overworld().getAllEntities()));
			List<Vec3> displaysBefore = server.computeOnServer(s -> displayPositions(s.overworld()));
			int ax = anchor.getX(), ay = anchor.getY(), az = anchor.getZ();
			// Piston head extends into the platform's first block.
			server.runCommand(String.format("setblock %d %d %d piston[facing=east]", ax - 1, ay, az + 1));
			server.runCommand(String.format("setblock %d %d %d redstone_block", ax - 2, ay, az + 1));
			ctx.waitTicks(10);
			server.runCommand(String.format("summon tnt %d %d %d {fuse:1}", ax + 2, ay + 1, az + 1));
			ctx.waitTicks(20);
			List<AABB> boxesAfter = server.computeOnServer(s -> colliderBoxes(s.overworld().getAllEntities()));
			List<Vec3> displaysAfter = server.computeOnServer(s -> displayPositions(s.overworld()));
			check(boxesBefore.equals(boxesAfter), "colliders moved/broke after piston + explosion");
			check(displaysBefore.equals(displaysAfter), "displays moved/broke after piston + explosion");
			System.out.println("[SolidDisplays test] OK piston + TNT explosion left the model intact");

			// --- rotated copy (90°) next to it
			BlockPos b = anchor.offset(0, 0, 12);
			server.runOnServer(s -> ModelPlacer.place(s.overworld(), player(s), TestModels.DEMO, b, 1, List.of()));

			// --- orphan cleanup: forget model C without removing its entities
			BlockPos c = anchor.offset(12, 0, 0);
			server.runOnServer(s -> {
				PlacedModel placed = ModelPlacer.place(s.overworld(), player(s), TestModels.DEMO, c, 0, List.of()).model().orElseThrow();
				PlacedModels.get(s.overworld()).remove(placed.id());
			});
			ctx.waitTicks(10);
			int before = server.computeOnServer(s -> countParts(s.overworld()));
			check(before == 3 * perModel, "expected 3 models before reload, got " + before + " entities");

			// --- screenshot (debug hitbox lines do not render on the software renderer, so none here)
			teleport(server, anchor, -3.5, 2.5, -3.5, -40f);
			ctx.runOnClient(mc -> mc.player.setXRot(28f));
			ctx.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("stage2_model");
		}

		// --- reopen the world: models A and B come back with their boxes; orphan C is deleted
		try (TestSingleplayerContext sp = save.open()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			int after = server.computeOnServer(s -> countParts(s.overworld()));
			check(after == 2 * perModel, "after reload expected 2 models, got " + after + " entities");
			int clientColliders = ctx.computeOnClient(mc -> colliderBoxes(mc.level.entitiesForRendering()).size());
			check(clientColliders == 2 * pieces, "after reload client sees " + clientColliders + " colliders");
			standOn(ctx, server, anchor, 1.5, 1.0, anchor.getY() + 1.5, "table top after reload");
			System.out.println("[SolidDisplays test] OK persistence + orphan cleanup");

			// --- dismantle model A with two sneak-clicks (the first only asks for confirmation)
			UUID id = modelA;
			server.runOnServer(s -> ModelPlacer.requestDismantle(s.overworld(), player(s), id));
			int afterFirst = server.computeOnServer(s -> countParts(s.overworld()));
			check(afterFirst == 2 * perModel, "first click must only ask for confirmation");
			server.runOnServer(s -> ModelPlacer.requestDismantle(s.overworld(), player(s), id));
			ctx.waitTicks(10);
			int afterSecond = server.computeOnServer(s -> countParts(s.overworld()));
			int clientAfter = ctx.computeOnClient(mc -> colliderBoxes(mc.level.entitiesForRendering()).size());
			check(afterSecond == perModel && clientAfter == pieces, "dismantle left " + afterSecond + " / client " + clientAfter);
			System.out.println("[SolidDisplays test] OK dismantle with confirmation");

			// --- survival placement costs materials; dismantling drops exactly those back
			List<ItemStack> cost = MaterialCalculator.cost(TestModels.DEMO);
			BlockPos d = anchor.offset(24, 0, 0);
			server.runCommand("clear @a");
			// Placement now requires being within reach (config maxPlaceDistance): stand next to the spot.
			teleport(server, anchor, 22, 0, -4, 0f);
			ctx.waitTicks(5);
			server.runOnServer(s -> Materials.toStacks(cost).forEach(st -> player(s).getInventory().add(st)));
			boolean placed = server.computeOnServer(s ->
					ModelPlacer.placeWithMaterials(s.overworld(), player(s), TestModels.DEMO, d, 0).model().isPresent());
			check(placed, "survival placement with materials failed");
			boolean emptied = server.computeOnServer(s -> cost.stream().allMatch(c -> Materials.count(player(s).getInventory(), c.getItem()) == 0));
			check(emptied, "materials were not taken from the inventory");
			boolean refused = server.computeOnServer(s ->
					ModelPlacer.placeWithMaterials(s.overworld(), player(s), TestModels.DEMO, d.offset(0, 0, -6), 0).model().isEmpty());
			check(refused, "placement without materials must be refused");
			UUID modelD = server.computeOnServer(s -> PlacedModels.get(s.overworld()).all().stream()
					.filter(m -> m.anchor().equals(d)).findFirst().orElseThrow().id());
			server.runOnServer(s -> ModelPlacer.dismantle(s.overworld(), modelD, player(s)));
			ctx.waitTicks(5);
			for (ItemStack c : cost) {
				int dropped = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class,
						new AABB(d).inflate(4), e -> e.getItem().is(c.getItem())).stream().mapToInt(e -> e.getItem().getCount()).sum());
				check(dropped == c.getCount(), "dropped " + dropped + " " + c.getItem() + ", expected " + c.getCount());
			}
			System.out.println("[SolidDisplays test] OK materials taken on place and dropped on dismantle: " + cost);
		}
	}

	// ---- Dedicated server: no "flying" kick while standing on a floating model ---------
	// The anti-fly check skips players the server itself considers on ground, and the
	// server-side movement includes entity collisions, so no mixin is needed. This
	// test guards that behaviour.

	private void dedicatedServer(ClientGameTestContext ctx) {
		Properties props = new Properties();
		props.setProperty("allow-flight", "false");
		props.setProperty("spawn-protection", "0");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props);
				TestDedicatedServerConnection conn = server.connect()) {
			conn.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			BlockPos ground = server.computeOnServer(s -> groundAt(s.overworld(), 0, 0));
			BlockPos anchor = ground.above(10);
			teleport(server, ground, -6, 0, -6, 0f);
			ctx.waitTicks(5);
			server.runOnServer(s -> ModelPlacer.place(s.overworld(), player(s), TestModels.DEMO, anchor, 0, List.of()));
			ctx.waitTicks(10);
			teleport(server, anchor, 3.5, 2.0, 2.5, 0f);
			// Vanilla kicks after 80 ticks of "floating"; wait well past that.
			ctx.waitTicks(160);
			boolean connected = ctx.computeOnClient(mc -> mc.player != null && mc.getConnection() != null && mc.level != null);
			check(connected, "player was disconnected while standing on a floating model");
			Vec3 p = ctx.computeOnClient(mc -> mc.player.position());
			boolean onGround = ctx.computeOnClient(mc -> mc.player.onGround());
			double serverY = server.computeOnServer(s -> player(s).getY());
			check(Math.abs(p.y - (anchor.getY() + 0.5)) < EPS && onGround, "not standing on floating platform: " + p);
			check(Math.abs(serverY - p.y) < EPS, "server/client Y mismatch: " + serverY + " vs " + p.y);
			System.out.println("[SolidDisplays test] dedicated server: stood 8s on floating model, still connected");
			ctx.takeScreenshot("stage2_dedicated_server");
		}
	}

	// ---- helpers ----------------------------------------------------------------

	/** Teleports onto (x, z) from above, lets the player fall, and checks the landing height and stability. */
	private void standOn(ClientGameTestContext ctx, TestServerContext server, BlockPos anchor, double x, double z,
			double expectedY, String what) {
		teleport(server, anchor, x, expectedY - anchor.getY() + 1.5, z, 0f);
		ctx.waitTicks(30);
		List<Double> ys = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ys.add(ctx.computeOnClient(mc -> mc.player.getY()));
			ctx.waitTick();
		}
		double min = ys.stream().min(Double::compare).orElseThrow();
		double max = ys.stream().max(Double::compare).orElseThrow();
		boolean onGround = ctx.computeOnClient(mc -> mc.player.onGround());
		check(Math.abs(min - expectedY) < EPS, what + ": expected y=" + expectedY + " but player is at " + min);
		check(max - min < 1e-9, what + ": jitter " + (max - min));
		check(onGround, what + ": not on ground");
		System.out.println("[SolidDisplays test] OK " + what + " y=" + min);
	}

	/** Spawns a motionless item above (x, z) in model space and returns where it settles. */
	private static double dropItemAndGetY(ClientGameTestContext ctx, TestServerContext server, BlockPos anchor, double x, double z) {
		int id = server.computeOnServer(s -> {
			ItemEntity item = new ItemEntity(s.overworld(), anchor.getX() + x, anchor.getY() + 2.5, anchor.getZ() + z,
					new ItemStack(Items.STICK));
			item.setDeltaMovement(Vec3.ZERO);
			item.setPickUpDelay(32767);
			s.overworld().addFreshEntity(item);
			return item.getId();
		});
		ctx.waitTicks(40);
		return server.computeOnServer(s -> {
			Entity item = s.overworld().getEntity(id);
			double y = item.getY();
			item.discard();
			return y;
		});
	}

	private static void teleport(TestServerContext server, BlockPos anchor, double x, double y, double z, float yaw) {
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.4f %.4f %.4f %.1f 0",
				anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z, yaw));
	}

	private static BlockPos groundAt(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static List<AABB> colliderBoxes(Iterable<Entity> entities) {
		List<AABB> boxes = new ArrayList<>();
		for (Entity e : entities) {
			if (e instanceof ModelColliderEntity) {
				boxes.add(e.getBoundingBox());
			}
		}
		boxes.sort(Comparator.<AABB>comparingDouble(a -> a.minX).thenComparingDouble(a -> a.minY).thenComparingDouble(a -> a.minZ));
		return boxes;
	}

	private static List<Vec3> displayPositions(ServerLevel level) {
		List<Vec3> out = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof Display && e.entityTags().contains(ModelPlacer.PART_TAG)) {
				out.add(e.position());
			}
		}
		out.sort(Comparator.<Vec3>comparingDouble(v -> v.x).thenComparingDouble(v -> v.y).thenComparingDouble(v -> v.z));
		return out;
	}

	private static int countParts(ServerLevel level) {
		int n = 0;
		for (Entity e : level.getAllEntities()) {
			if (e instanceof ModelColliderEntity || (e instanceof Display && e.entityTags().contains(ModelPlacer.PART_TAG))) {
				n++;
			}
		}
		return n;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
