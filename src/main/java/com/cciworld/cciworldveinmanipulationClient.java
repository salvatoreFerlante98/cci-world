package com.cciworld;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = cciworldveinmanipulation.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = cciworldveinmanipulation.MODID, value = Dist.CLIENT)
public class cciworldveinmanipulationClient {

    public cciworldveinmanipulationClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        cciworldveinmanipulation.LOGGER.info("CCI World client setup");
    }
}
