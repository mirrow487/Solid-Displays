package com.soliddisplays.registry;

import com.soliddisplays.SolidDisplays;
import com.soliddisplays.entity.ModelColliderEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> COLLIDER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SolidDisplays.id("model_collider"));

	/**
	 * The size here is only a placeholder; the real box comes from synced data.
	 * {@code updateInterval} is large because the entity never moves.
	 */
	public static final EntityType<ModelColliderEntity> MODEL_COLLIDER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, COLLIDER_KEY,
			EntityType.Builder.<ModelColliderEntity>of(ModelColliderEntity::new, MobCategory.MISC)
					.sized(1f, 1f)
					.fireImmune()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.updateInterval(Integer.MAX_VALUE)
					.build(COLLIDER_KEY));

	private ModEntities() {
	}

	public static void init() {
	}
}
