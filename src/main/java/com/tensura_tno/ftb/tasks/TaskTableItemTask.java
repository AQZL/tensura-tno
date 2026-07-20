package com.tensura_tno.ftb.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem.ComponentMatchType;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskTableItemTask extends Task {

    private static final int MAX_TRACKED_ITEMS = 63;
    private static final ComponentMatchType DEFAULT_MATCH_COMPONENTS = ComponentMatchType.FUZZY;
    private static final Method MATCH_WITH_LOOKUP = findMatchMethod(
            ItemStack.class, ItemStack.class, ComponentMatchType.class, HolderLookup.Provider.class);
    private static final Method MATCH_LEGACY = findMatchMethod(
            ItemStack.class, ItemStack.class, ComponentMatchType.class);

    public static TaskType TASK_TABLE_ITEM_TASK;

    private List<ItemStack> items = new ArrayList<>();
    private String rewardTable = "";
    private boolean requireAll = false;
    private Tristate consumeItems = Tristate.DEFAULT;
    private ComponentMatchType matchComponents = DEFAULT_MATCH_COMPONENTS;

    public TaskTableItemTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TASK_TABLE_ITEM_TASK;
    }

    @Override
    public long getMaxProgress() {
        if (!requireAll) {
            return Math.max(1L, entries().size());
        }

        int size = entries().size();
        if (size <= 0) {
            return 1L;
        }

        return size >= MAX_TRACKED_ITEMS ? Long.MAX_VALUE : (1L << size) - 1L;
    }

    @Override
    public String formatMaxProgress() {
        return requireAll ? Integer.toString(entries().size()) : "1";
    }

    @Override
    public String formatProgress(TeamData teamData, long progress) {
        return requireAll ? Integer.toString(Long.bitCount(progress & getMaxProgress())) : Long.toString(Math.min(progress, 1L));
    }

    @Override
    public boolean hideProgressNumbers() {
        return false;
    }

    @Override
    public boolean consumesResources() {
        return consumeItems.get(getQuest().getChapter().consumeItems());
    }

    @Override
    public boolean canInsertItem() {
        return consumesResources();
    }

    @Override
    public boolean submitItemsOnInventoryChange() {
        return !requireAll && !consumesResources();
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        if (requireAll) {
            return 0;
        }
        return consumesResources() ? 0 : 20;
    }

    @Override
    public boolean checkOnLogin() {
        return !requireAll && !consumesResources();
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);

        ListTag list = new ListTag();
        for (ItemStack stack : sanitize(items)) {
            list.add(stack.save(provider));
        }
        nbt.put("items", list);

        if (!rewardTable.isEmpty()) {
            nbt.putString("reward_table", rewardTable);
        }
        if (requireAll) {
            nbt.putBoolean("require_all", true);
        }
        consumeItems.write(nbt, "consume_items");
        nbt.putString("match_components", ComponentMatchType.NAME_MAP.getName(matchComponents));
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);

        List<ItemStack> loaded = new ArrayList<>();
        ListTag list = nbt.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && loaded.size() < MAX_TRACKED_ITEMS; i++) {
            ItemStack stack = itemOrMissingFromNBT(list.getCompound(i), provider);
            if (!stack.isEmpty()) {
                loaded.add(stack.copy());
            }
        }
        items = loaded;

        rewardTable = nbt.getString("reward_table");
        requireAll = nbt.getBoolean("require_all");
        consumeItems = Tristate.read(nbt, "consume_items");
        matchComponents = nbt.contains("match_components")
                ? ComponentMatchType.NAME_MAP.get(nbt.getString("match_components"))
                : DEFAULT_MATCH_COMPONENTS;
        if (matchComponents == null) {
            matchComponents = DEFAULT_MATCH_COMPONENTS;
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        List<ItemStack> safeItems = sanitize(items);
        buffer.writeVarInt(safeItems.size());
        for (ItemStack stack : safeItems) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeUtf(rewardTable);
        buffer.writeBoolean(requireAll);
        consumeItems.write(buffer);
        ComponentMatchType.NAME_MAP.write(buffer, matchComponents);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        int size = Math.min(buffer.readVarInt(), MAX_TRACKED_ITEMS);
        List<ItemStack> loaded = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            if (!stack.isEmpty()) {
                loaded.add(stack.copy());
            }
        }
        items = loaded;
        rewardTable = buffer.readUtf();
        requireAll = buffer.readBoolean();
        consumeItems = Tristate.read(buffer);
        matchComponents = ComponentMatchType.NAME_MAP.read(buffer);
        if (matchComponents == null) {
            matchComponents = DEFAULT_MATCH_COMPONENTS;
        }
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addList("items", items, new ItemStackConfig(true, false), v -> items = sanitize(v), ItemStack.EMPTY)
                .setNameKey("tensura_tno.task.task_table_item.items");
        config.addString("reward_table", rewardTable, v -> rewardTable = v, "")
                .setNameKey("tensura_tno.task.task_table_item.reward_table");
        config.addBool("require_all", requireAll, v -> requireAll = v, false)
                .setNameKey("tensura_tno.task.task_table_item.require_all");
        config.addEnum("consume_items", consumeItems, v -> consumeItems = v, Tristate.NAME_MAP)
                .setNameKey("ftbquests.task.ftbquests.item.consume_items");
        config.addEnum("match_components", matchComponents, v -> matchComponents = v, ComponentMatchType.NAME_MAP)
                .setNameKey("ftbquests.task.ftbquests.item.match_components");
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.tensura_tno.task_table_item");
    }

    @Override
    public Icon getAltIcon() {
        List<ItemStack> safeItems = entries();
        if (safeItems.isEmpty()) {
            return Icons.CHECK;
        }
        return ItemIcon.getItemIcon(displayStack(safeItems.get(animatedIndex(safeItems.size()))));
    }

    @Override
    public void addMouseOverText(TooltipList list, TeamData teamData) {
        List<ItemStack> safeItems = entries();
        if (safeItems.isEmpty()) {
            list.blankLine();
            list.add(Component.translatable("tensura_tno.task.task_table_item.no_items").withStyle(ChatFormatting.RED));
            return;
        }

        list.blankLine();
        list.add(Component.translatable(requireAll
                ? "tensura_tno.task.task_table_item.requires_all"
                : "tensura_tno.task.task_table_item.requires_any").withStyle(ChatFormatting.GRAY));

        long progress = teamData == null ? 0L : teamData.getProgress(this);
        int totalItems = safeItems.size();
        int pageSize = tooltipItemLimit(totalItems);
        int page = tooltipPage(totalItems, pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(totalItems, startIndex + pageSize);

        if (totalItems > pageSize) {
            list.add(Component.translatable("tensura_tno.task.task_table_item.showing_items",
                    startIndex + 1, endIndex, totalItems, page + 1, tooltipPageCount(totalItems, pageSize))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack stack = safeItems.get(i);
            boolean done = requireAll ? isEntryDone(progress, i) : isAnyEntryDone(progress, i, totalItems);
            Component prefix = Component.literal(done ? "[x] " : "[ ] ")
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
            list.add(prefix.copy().append(title(stack)));
        }

        if (consumesResources() && teamData != null && !teamData.isCompleted(this)) {
            list.blankLine();
            list.add(Component.translatable("ftbquests.task.click_to_submit").withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE));
        }
    }

    @Override
    public void onButtonClicked(Button button, boolean canClick) {
        if (canClick) {
            super.onButtonClicked(button, true);
        } else {
            button.playClickSound();
        }
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (player == null || teamData == null || !checkTaskSequence(teamData) || teamData.isCompleted(this)) {
            return;
        }

        List<ItemStack> safeItems = entries();
        if (safeItems.isEmpty()) {
            return;
        }

        if (!requireAll) {
            submitAny(teamData, player, safeItems);
        } else {
            submitAll(teamData, player, safeItems);
        }
    }

    private void submitAny(TeamData teamData, ServerPlayer player, List<ItemStack> safeItems) {
        if (consumesResources()) {
            for (int i = 0; i < safeItems.size(); i++) {
                ItemStack target = safeItems.get(i);
                if (consumeTarget(player, target)) {
                    completeAny(teamData, i);
                    return;
                }
            }
        } else {
            for (int i = 0; i < safeItems.size(); i++) {
                ItemStack target = safeItems.get(i);
                if (countMatching(player, target) >= target.getCount()) {
                    completeAny(teamData, i);
                    return;
                }
            }
        }
    }

    private void completeAny(TeamData teamData, int index) {
        teamData.setProgress(this, Math.min(Math.max(0, index) + 1L, getMaxProgress()));
        if (!teamData.isCompleted(this)) {
            teamData.markTaskCompleted(this);
        }
    }

    private void submitAll(TeamData teamData, ServerPlayer player, List<ItemStack> safeItems) {
        long progress = teamData.getProgress(this);

        for (int i = 0; i < safeItems.size(); i++) {
            long bit = 1L << i;
            if ((progress & bit) != 0L) {
                continue;
            }

            ItemStack target = safeItems.get(i);
            boolean submitted = consumesResources()
                    ? consumeTarget(player, target)
                    : countMatching(player, target) >= target.getCount();
            if (submitted) {
                progress |= bit;
            }
        }

        teamData.setProgress(this, progress & getMaxProgress());
    }

    private boolean consumeTarget(ServerPlayer player, ItemStack target) {
        int remaining = target.getCount();
        List<ItemStack> stacks = player.getInventory().items;

        for (ItemStack stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            if (matches(target, stack)) {
                remaining -= Math.min(remaining, stack.getCount());
            }
        }

        if (remaining > 0) {
            return false;
        }

        remaining = target.getCount();
        boolean changed = false;
        for (int i = 0; i < stacks.size() && remaining > 0; i++) {
            ItemStack stack = stacks.get(i);
            if (!matches(target, stack)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            changed = true;
            if (stack.isEmpty()) {
                stacks.set(i, ItemStack.EMPTY);
            }
        }

        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
        return changed;
    }

    private long countMatching(ServerPlayer player, ItemStack target) {
        long total = 0L;
        for (ItemStack stack : player.getInventory().items) {
            if (matches(target, stack)) {
                total += stack.getCount();
                if (total >= target.getCount()) {
                    return total;
                }
            }
        }
        return total;
    }

    private boolean matches(ItemStack target, ItemStack stack) {
        ItemStack filter = displayStack(target);
        if (stack.isEmpty()) {
            return false;
        }

        Boolean ftbResult = invokeFtbMatcher(filter, stack);
        return ftbResult != null ? ftbResult : fallbackMatches(filter, stack);
    }

    private Boolean invokeFtbMatcher(ItemStack filter, ItemStack stack) {
        if (MATCH_WITH_LOOKUP != null) {
            try {
                return (Boolean) MATCH_WITH_LOOKUP.invoke(ItemMatchingSystem.INSTANCE, filter, stack, matchComponents, getQuestFile().holderLookup());
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }

        if (MATCH_LEGACY != null) {
            try {
                return (Boolean) MATCH_LEGACY.invoke(ItemMatchingSystem.INSTANCE, filter, stack, matchComponents);
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }

        return null;
    }

    private static Method findMatchMethod(Class<?>... parameterTypes) {
        try {
            return ItemMatchingSystem.class.getMethod("doesItemMatch", parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private boolean fallbackMatches(ItemStack filter, ItemStack stack) {
        if (filter == stack) {
            return true;
        }
        if (filter.getItem() != stack.getItem()) {
            return false;
        }

        return switch (matchComponents) {
            case NONE -> true;
            case FUZZY -> filter.getComponents().stream()
                    .allMatch(component -> stack.getComponents().has(component.type())
                            && stack.getComponents().get(component.type()).equals(component.value()));
            case STRICT -> ItemStack.isSameItemSameComponents(filter, stack);
        };
    }

    private boolean isEntryDone(long progress, int index) {
        return index >= 0 && index < MAX_TRACKED_ITEMS && (progress & (1L << index)) != 0L;
    }

    private boolean isAnyEntryDone(long progress, int index, int totalItems) {
        if (progress <= 0L || index < 0 || totalItems <= 0) {
            return false;
        }
        int submittedIndex = Math.min((int) progress, totalItems) - 1;
        return index == submittedIndex;
    }

    private int animatedIndex(int size) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || size <= 1) {
            return 0;
        }
        return (int) ((minecraft.level.getGameTime() / 20L) % size);
    }

    private int tooltipItemLimit(int totalItems) {
        Minecraft minecraft = Minecraft.getInstance();
        int limit = 12;
        if (minecraft.screen != null) {
            int availableHeight = Math.max(80, minecraft.screen.height - 120);
            limit = Math.max(8, availableHeight / (minecraft.font.lineHeight + 2));
        }
        return Math.max(1, Math.min(totalItems, limit));
    }

    private int tooltipPage(int totalItems, int pageSize) {
        int pageCount = tooltipPageCount(totalItems, pageSize);
        if (pageCount <= 1) {
            return 0;
        }
        return (int) ((System.currentTimeMillis() / 2000L) % pageCount);
    }

    private int tooltipPageCount(int totalItems, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (totalItems + pageSize - 1) / pageSize);
    }

    private List<ItemStack> entries() {
        List<ItemStack> safe = sanitize(items);
        if (safe.size() >= MAX_TRACKED_ITEMS) {
            return safe;
        }

        RewardTable table = resolveRewardTable();
        if (table == null) {
            return safe;
        }

        for (WeightedReward weightedReward : table.getWeightedRewards()) {
            if (safe.size() >= MAX_TRACKED_ITEMS) {
                break;
            }

            Reward reward = weightedReward.getReward();
            if (reward instanceof ItemReward itemReward && !itemReward.getItem().isEmpty()) {
                ItemStack stack = itemReward.getItem().copy();
                stack.setCount(Math.max(1, itemReward.getCount()));
                safe.add(stack);
            }
        }
        return safe;
    }

    private RewardTable resolveRewardTable() {
        if (rewardTable == null || rewardTable.isBlank()) {
            return null;
        }

        RewardTable byId = getQuestFile().getRewardTable(getQuestFile().getID(rewardTable));
        if (byId != null) {
            return byId;
        }

        String normalized = rewardTable.toLowerCase(Locale.ROOT);
        for (RewardTable table : getQuestFile().getRewardTables()) {
            if (table.getFilename().equalsIgnoreCase(rewardTable)
                    || table.getCodeString().equalsIgnoreCase(rewardTable)
                    || table.getRawTitle().toLowerCase(Locale.ROOT).equals(normalized)) {
                return table;
            }
        }

        return null;
    }

    private List<ItemStack> sanitize(List<ItemStack> source) {
        List<ItemStack> safe = new ArrayList<>();
        if (source == null) {
            return safe;
        }

        for (ItemStack stack : source) {
            if (safe.size() >= MAX_TRACKED_ITEMS) {
                break;
            }
            if (stack != null && !stack.isEmpty()) {
                ItemStack copy = stack.copy();
                copy.setCount(Math.max(1, copy.getCount()));
                safe.add(copy);
            }
        }
        return safe;
    }

    private ItemStack displayStack(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private Component title(ItemStack stack) {
        if (stack.getCount() > 1) {
            return Component.literal(stack.getCount() + "x ").append(stack.getHoverName());
        }
        return stack.getHoverName();
    }
}
