package com.tensura_tno.mixin.client;

import io.github.manasmods.tensura.menu.ReincarnationMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ReincarnationMenu.class, remap = false)
public interface ReincarnationMenuAccessor {
    @Accessor("racePool")
    int tensuraTno$getRacePoolType();
}
