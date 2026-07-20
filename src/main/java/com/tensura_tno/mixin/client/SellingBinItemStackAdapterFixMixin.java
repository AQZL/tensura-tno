package com.tensura_tno.mixin.client;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Fixes SellingBin crash when a hopper pushes items into the bin.
 *
 * Root cause: ItemStackAdapter.readNbt() and writeNbt() call
 * SellingBinMod.SERVER.registryAccess(), but SERVER is null on the client
 * when chunk sync packets trigger BlockEntity deserialization.
 *
 * Fix: override readNbt/writeNbt to use a client-safe RegistryAccess
 * obtained via reflection + Minecraft.getInstance().level fallback.
 */
@Pseudo
@Mixin(targets = "bigchadguys.sellingbin.data.adapter.util.ItemStackAdapter", remap = false)
public class SellingBinItemStackAdapterFixMixin {

    @Inject(method = "readNbt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tensuraTno$safeReadNbt(Tag nbt, CallbackInfoReturnable<Optional<ItemStack>> cir) {
        if (!(nbt instanceof CompoundTag)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        RegistryAccess access = getSafeRegistryAccess();
        if (access == null) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        try {
            cir.setReturnValue(ItemStack.parse(access, nbt));
        } catch (Exception e) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "writeNbt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tensuraTno$safeWriteNbt(ItemStack value, CallbackInfoReturnable<Optional<Tag>> cir) {
        if (value == null) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        RegistryAccess access = getSafeRegistryAccess();
        if (access == null) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        try {
            cir.setReturnValue(Optional.of(value.save(access)));
        } catch (Exception e) {
            cir.setReturnValue(Optional.empty());
        }
    }

    private static RegistryAccess getSafeRegistryAccess() {
        try {
            Class<?> modClass = Class.forName("bigchadguys.sellingbin.SellingBinMod");
            java.lang.reflect.Field serverField = modClass.getDeclaredField("SERVER");
            serverField.setAccessible(true);
            Object server = serverField.get(null);
            if (server instanceof net.minecraft.server.MinecraftServer ms) {
                return ms.registryAccess();
            }
        } catch (Exception ignored) {}

        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = minecraftClass.getMethod("getInstance").invoke(null);
            if (mc != null) {
                Object level = minecraftClass.getField("level").get(mc);
                if (level != null) {
                    Object access = level.getClass().getMethod("registryAccess").invoke(level);
                    if (access instanceof RegistryAccess registryAccess) {
                        return registryAccess;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
