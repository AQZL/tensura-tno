package snownee.jade.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public interface IWailaClientRegistration {
    void addConfig(ResourceLocation key, boolean defaultValue);

    void registerEntityComponent(IComponentProvider<EntityAccessor> provider,
                                 Class<? extends Entity> entityClass);
}
