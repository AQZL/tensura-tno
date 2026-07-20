package snownee.jade.api;

import snownee.jade.api.config.IPluginConfig;

public interface IComponentProvider<T extends Accessor<?>> extends IToggleableProvider {
    void appendTooltip(ITooltip tooltip, T accessor, IPluginConfig config);
}
