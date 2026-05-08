package com.cciworld;

import com.cciworld.command.CCIWorldCommands;
import com.cciworld.config.CCIWorldConfig;
import com.cciworld.generator.ClusterGeneratorEngine;
import com.cciworld.policy.AutomaticPolicyEngine;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(cciworldveinmanipulation.MODID)
public class cciworldveinmanipulation {

    public static final String MODID = "cci_world";
    public static final Logger LOGGER = LogUtils.getLogger();

    public cciworldveinmanipulation(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CCIWorldConfig.SPEC, "cci_world-common.toml");
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(AutomaticPolicyEngine::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(AutomaticPolicyEngine::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ClusterGeneratorEngine::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(ClusterGeneratorEngine::onChunkLoad);
        LOGGER.info("CCI World loaded");
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CCIWorldCommands.register(event.getDispatcher());
        LOGGER.info("CCI World commands registered");
    }
}
