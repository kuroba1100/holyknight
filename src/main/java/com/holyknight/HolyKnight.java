package com.holyknight;

import com.holyknight.spell.HolyRaySpell;
import com.holyknight.spell.HolyRayVisualEntity;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(HolyKnight.MODID)
public class HolyKnight {

    public static final String MODID = "holyknight";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<HolyKnightEntity>> HOLY_KNIGHT =
            ENTITIES.register("holy_knight", () ->
                    EntityType.Builder.of(HolyKnightEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 3.9F)
                            .clientTrackingRange(10)
                            .build("holy_knight"));

    public static final RegistryObject<EntityType<HolyRayVisualEntity>> HOLY_RAY_VISUAL =
            ENTITIES.register("holy_ray_visual", () ->
                    EntityType.Builder.<HolyRayVisualEntity>of(HolyRayVisualEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .build("holy_ray_visual"));

    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, MODID);

    public static final RegistryObject<AbstractSpell> HOLY_RAY = SPELLS.register("holy_ray", HolyRaySpell::new);

    public HolyKnight() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        SPELLS.register(modBus);
        modBus.addListener(this::onAttributeCreation);
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(HOLY_KNIGHT.get(), HolyKnightEntity.createAttributes().build());
    }
}
