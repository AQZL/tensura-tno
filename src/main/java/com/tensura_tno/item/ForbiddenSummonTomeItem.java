package com.tensura_tno.item;

import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 禁忌召唤术魔法书 — 使用后教会玩家 tensura_tno:edo_tensei 技能。
 * 概率匹配主模组巫师塔 epic 魔法书 (weight 3/118 ≈ 2.5%)。
 */
public class ForbiddenSummonTomeItem extends Item {

    private static final ResourceLocation SKILL_ID =
            ResourceLocation.fromNamespaceAndPath("tensura_tno", "edo_tensei");

    public ForbiddenSummonTomeItem() {
        super(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> list, TooltipFlag flag) {
        ManasSkill skill = getSkill();
        if (skill != null) {
            list.add(skill.getChatDisplayName(false));
            list.add(Component.translatable("tensura_tno.skill.edo_tensei.description")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 200;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack.getItem()) && !player.getAbilities().instabuild) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        TensuraParticleHelper.spawnEnchantingTableParticle(level,
                entity.getEyePosition().add(0, 0.5, 0), ParticleTypes.ENCHANT, 8);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int time) {
        if (level.isClientSide()) return;
        int useTicks = getUseDuration(stack, entity) - time;
        if (useTicks < 10) return;

        ManasSkill skill = getSkill();
        if (skill == null) return;

        if (!(entity instanceof ServerPlayer player)) return;

        TensuraSkillInstance instance = new TensuraSkillInstance(skill);
        if (skill instanceof TensuraSkill ts) {
            instance.setMastery(ts.getAcquirementMastery(entity));
        } else {
            instance.setMastery(TensuraSkill.BASE_CONFIG.Learning.learningPointRequirement * -1);
        }

        if (SkillHelper.learnSkill(entity, (ManasSkillInstance) instance)) {
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            TensuraParticleHelper.addServerParticlesAroundSelf(entity, ParticleTypes.TOTEM_OF_UNDYING, 1.0);
            TensuraParticleHelper.addServerParticlesAroundSelf(entity, ParticleTypes.FLASH, 1.0);
        } else {
            entity.sendSystemMessage(Component.translatable(
                    "tensura.skill.temporary.already_have",
                    instance.getChatDisplayName(true)).withStyle(ChatFormatting.RED));
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    TensuraSoundEvents.GENERIC_CAST_FAIL, SoundSource.PLAYERS, 2.0F, 1.0F);
            TensuraParticleHelper.addServerParticlesAroundSelf(entity, ParticleTypes.ANGRY_VILLAGER, 1.0);
        }

        if (!player.hasInfiniteMaterials()) {
            player.getCooldowns().addCooldown(stack.getItem(), 200);
            stack.shrink(1);
        }
    }

    private static ManasSkill getSkill() {
        return (ManasSkill) SkillAPI.getSkillRegistry().get(SKILL_ID);
    }
}
