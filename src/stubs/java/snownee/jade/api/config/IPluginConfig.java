package snownee.jade.api.config;

import net.minecraft.resources.ResourceLocation;

public interface IPluginConfig {
    boolean get(ResourceLocation key);
}
