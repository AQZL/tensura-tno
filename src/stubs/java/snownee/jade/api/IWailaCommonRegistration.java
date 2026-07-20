package snownee.jade.api;

import net.minecraft.world.entity.Entity;

public interface IWailaCommonRegistration {
    void registerEntityDataProvider(IServerDataProvider<EntityAccessor> dataProvider,
                                    Class<? extends Entity> entityClass);
}
