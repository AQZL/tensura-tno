package com.tensura_tno.registry;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.block.TnoStoveBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;

import java.util.function.Supplier;

public final class TensuraTNOBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, TensuraTNOMod.MOD_ID);

    public static final Supplier<BackpackBlock> LOW_MAGISTEEL_BACKPACK =
            BLOCKS.register("low_magisteel_backpack", () -> new BackpackBlock());
    public static final Supplier<BackpackBlock> HIGH_MAGISTEEL_BACKPACK =
            BLOCKS.register("high_magisteel_backpack", () -> new BackpackBlock());
    public static final Supplier<BackpackBlock> PURE_MAGISTEEL_BACKPACK =
            BLOCKS.register("pure_magisteel_backpack", () -> new BackpackBlock(1200));
    public static final Supplier<BackpackBlock> ADAMANTITE_BACKPACK =
            BLOCKS.register("adamantite_backpack", () -> new BackpackBlock(1200));
    public static final Supplier<BackpackBlock> HIHIIROKANE_BACKPACK =
            BLOCKS.register("hihiirokane_backpack", () -> new BackpackBlock(1200));
    public static final Supplier<TnoStoveBlock> TNO_STOVE =
            BLOCKS.register("tno_stove", TnoStoveBlock::new);

    private TensuraTNOBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
