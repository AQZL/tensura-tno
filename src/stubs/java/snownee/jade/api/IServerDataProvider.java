package snownee.jade.api;

import net.minecraft.nbt.CompoundTag;

public interface IServerDataProvider<T extends Accessor<?>> extends IJadeProvider {
    void appendServerData(CompoundTag data, T accessor);

    default boolean shouldRequestData(T accessor) {
        return true;
    }
}
