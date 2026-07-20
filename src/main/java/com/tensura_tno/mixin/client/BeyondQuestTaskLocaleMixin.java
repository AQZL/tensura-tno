package com.tensura_tno.mixin.client;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Localizes QuestTask's hardcoded "Quest Completed: questName" system message
 * emitted inside the protected static updateTasksOfType helper.
 */
@Pseudo
@Mixin(targets = "com.trbeyond.quest.task.base.QuestTask", remap = false)
public class BeyondQuestTaskLocaleMixin {

    private static final String QUEST_COMPLETED_PREFIX = "Quest Completed: ";

    @Redirect(
        method = "updateTasksOfType",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"),
        remap = false, require = 0
    )
    private static void tensura_tno$localizeQuestCompleted(ServerPlayer player, Component original) {
        String text = original.getString();
        String name = text.startsWith(QUEST_COMPLETED_PREFIX)
                ? text.substring(QUEST_COMPLETED_PREFIX.length())
                : text;
        player.sendSystemMessage(Component.translatable("tensura_tno.beyond.quest.completed", name));
    }
}
