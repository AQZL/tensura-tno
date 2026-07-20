package com.tensura_tno.compat.jade;

import com.tensura_tno.TensuraTNOMod;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.TensuraSkill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum TensuraEntitySkillProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "entity_skills");

    private static final String DATA_KEY = "TensuraTNOEntitySkills";
    private static final String SKILLS_KEY = "Skills";
    private static final String TOTAL_KEY = "Total";
    private static final String SOURCE_KEY = "Source";
    private static final int NORMAL_LIMIT = 5;
    private static final int DETAIL_LIMIT = 15;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public int getDefaultPriority() {
        return 6000;
    }

    @Override
    public boolean shouldRequestData(EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        return entity instanceof LivingEntity;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        Entity entity = accessor.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        List<ResourceLocation> ids = getSkillIds(living);
        if (ids.isEmpty()) {
            return;
        }

        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < Math.min(ids.size(), DETAIL_LIMIT); i++) {
            list.add(StringTag.valueOf(ids.get(i).toString()));
        }
        root.put(SKILLS_KEY, list);
        root.putInt(TOTAL_KEY, ids.size());
        root.putString(SOURCE_KEY, entity.getStringUUID());
        data.put(DATA_KEY, root);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!config.get(UID)) {
            return;
        }

        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag root = serverData.getCompound(DATA_KEY);
        ListTag skillList = root.getList(SKILLS_KEY, Tag.TAG_STRING);
        if (skillList.isEmpty()) {
            return;
        }

        List<String> skillIds = new ArrayList<>();
        for (int i = 0; i < skillList.size(); i++) {
            skillIds.add(skillList.getString(i));
        }
        appendSkillTooltip(tooltip, skillIds, Math.max(root.getInt(TOTAL_KEY), skillIds.size()), accessor.showDetails());
    }

    private static void appendSkillTooltip(ITooltip tooltip, List<String> skillIds, int total, boolean showDetails) {
        int limit = showDetails ? DETAIL_LIMIT : NORMAL_LIMIT;
        int shown = Math.min(limit, skillIds.size());

        for (int i = 0; i < shown; i++) {
            String rawId = skillIds.get(i);
            Component skillName = getSkillName(rawId);
            tooltip.add(Component.literal(" - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(skillName));
        }

        if (total > shown) {
            tooltip.add(Component.translatable("tooltip.tensura_tno.jade.skills.more", total - shown)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static List<ResourceLocation> getSkillIds(LivingEntity living) {
        List<ResourceLocation> ids = new ArrayList<>();
        try {
            for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(living).getLearnedSkills()) {
                if (instance == null || instance.getMastery() < 0.0D) {
                    continue;
                }

                ManasSkill skill = getSkill(instance);
                if (skill != null && skill.getRegistryName() != null) {
                    ids.add(skill.getRegistryName());
                }
            }
        } catch (RuntimeException exception) {
            return List.of();
        }

        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return ids;
    }

    private static ManasSkill getSkill(ManasSkillInstance instance) {
        if (instance == null) {
            return null;
        }
        try {
            return instance.getSkill();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Component getSkillName(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return Component.literal(rawId);
        }

        try {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (skill != null) {
                Component name = skill instanceof TensuraSkill tensuraSkill ? tensuraSkill.getColoredName() : skill.getName();
                if (name != null) {
                    return name;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return Component.literal(id.toString());
    }
}
