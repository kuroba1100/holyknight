package com.holynight;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(HolyNight.MODID)
public class HolyNight {

    public static final String MODID = "holynight";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<HolyKnightEntity>> HOLY_KNIGHT =
            ENTITIES.register("holy_knight", () ->
                    EntityType.Builder.of(HolyKnightEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 3.9F)
                            .clientTrackingRange(10)
                            .build("holy_knight"));

    public HolyNight() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        modBus.addListener(this::onAttributeCreation);
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(HOLY_KNIGHT.get(), HolyKnightEntity.createAttributes().build());
    }
}
