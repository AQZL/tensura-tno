package com.tensura_tno.mixin.client;

import com.tensura_tno.util.PonderEditorAccess;
import java.lang.reflect.Method;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
@SuppressWarnings("null")
public abstract class PonderEditButtonVisibilityMixin {

    @Inject(method = "addRenderableWidget", at = @At("HEAD"), cancellable = true)
    private <T extends GuiEventListener & Renderable & NarratableEntry> void tensuraTno$hidePonderEditButton(
        T widget,
        CallbackInfoReturnable<T> cir
    ) {
        if (PonderEditorAccess.isEnabled()) {
            return;
        }
        if (!"net.createmod.ponder.foundation.ui.PonderUI".equals(((Object) this).getClass().getName())) {
            return;
        }
        if (tensuraTno$isPondererEditButton(widget)) {
            cir.setReturnValue(widget);
        }
    }

    private static boolean tensuraTno$isPondererEditButton(Object widget) {
        if (!"net.createmod.ponder.foundation.ui.PonderButton".equals(widget.getClass().getName())) {
            return false;
        }

        try {
            Method getItem = widget.getClass().getMethod("getItem");
            Object value = getItem.invoke(widget);
            return value instanceof ItemStack stack && stack.is(Items.WRITABLE_BOOK);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
