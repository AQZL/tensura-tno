package snownee.jade.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;

public interface Accessor<T extends HitResult> {
    Player getPlayer();

    CompoundTag getServerData();

    boolean showDetails();
}
