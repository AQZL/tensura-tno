package com.tensura_tno.network;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.tensura_tno.TensuraTNOMod;
import com.tensura_tno.compat.BetterSubsFoxCompat;
import com.tensura_tno.entity.spirit.FoxSpiritEntity;
import com.tensura_tno.registry.TensuraTNOEntities;
import com.tensura_tno.registry.TensuraTNOSkills;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * 契约小狐技能的网络包与服务端逻辑入口。
 *
 * <p>状态存储：玩家持久 NBT 下的 {@code TAG_ROOT}：
 * <ul>
 *   <li>{@code FoxUUID} —— 当前活在世界里的小狐灵 UUID（被收回 / 死亡 / 卸载会清空）；</li>
 *   <li>{@code RecalledStats} —— 被"收回"时存档下的属性快照；下次召唤时恢复到这些数值。</li>
 *   <li>{@code SummonCooldownUntilTick} —— 狐灵死亡后禁止再次召唤，值为服务端 {@link net.minecraft.server.MinecraftServer#getTickCount()} 的截止时间。</li>
 * </ul>
 *
 * <p>"狐灵死亡 / 玩家死亡" 会让两者都被清空，下次召唤按"玩家当前 50%"的规则重置；狐灵死亡后额外有 60 秒召唤冷却。
 */
public final class ContractLittleFoxPackets {

    private static final String TAG_ROOT = "tensura_tno_fox_spirit";
    private static final String TAG_FOX_UUID = "FoxUUID";
    private static final String TAG_RECALLED_STATS = "RecalledStats";
    /** 服务端 tick，{@code getTickCount() < 此值} 时禁止召唤（狐灵死亡后 60 秒）。 */
    private static final String TAG_SUMMON_COOLDOWN_UNTIL_TICK = "SummonCooldownUntilTick";
    /** 狐灵死亡后重新召唤冷却：60 秒。 */
    private static final int SUMMON_COOLDOWN_AFTER_DEATH_TICKS = 60 * 20;

    // RecalledStats 子键
    private static final String K_HP = "HP";
    private static final String K_MAX_HP = "MaxHP";
    private static final String K_RECALLED_TIME = "RecalledTime";
    private static final String K_AURA = "Aura";
    private static final String K_MAX_AURA = "MaxAura";
    private static final String K_MAGIC = "Magic";
    private static final String K_MAX_MAGIC = "MaxMagic";
    private static final String K_SHP = "SHP";
    private static final String K_MAX_SHP = "MaxSHP";
    private static final String K_ATK = "Atk";

    /** 收回存档：ManasCore 技能列表（与 SkillStorage.save 格式一致）。 */
    private static final String TAG_TNO_LEARNED_SKILLS = "TnoLearnedSkills";

    // ─────────────── 网络包定义 ───────────────

    /** S2C：让客户端打开/刷新面板。所有数值已就绪（HP/EP/MP 用 float，节省一点带宽）。 */
    public record OpenPanelPayload(
            int state,                 // 0=NONE 1=ALIVE 2=RECALLED
            float foxHp, float foxMaxHp,
            float foxAura, float foxMaxAura,
            float foxMagic, float foxMaxMagic,
            // 玩家自身的瞬时数值，方便客户端参考；客户端也可直接读本地玩家
            float playerHp, float playerMaxHp,
            float playerAura, float playerMaxAura,
            float playerMagic, float playerMaxMagic,
            // 当前活体小狐灵的 UUID（state==1 时有值，否则是全零 UUID）
            UUID foxUuid,
            /** 死亡后召唤剩余冷却（秒），无冷却时为 0。 */
            int summonCooldownSeconds
    ) implements CustomPacketPayload {
        public static final Type<OpenPanelPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "fox_open_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPanelPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeVarInt(p.state);
                            buf.writeFloat(p.foxHp);     buf.writeFloat(p.foxMaxHp);
                            buf.writeFloat(p.foxAura);   buf.writeFloat(p.foxMaxAura);
                            buf.writeFloat(p.foxMagic);  buf.writeFloat(p.foxMaxMagic);
                            buf.writeFloat(p.playerHp);  buf.writeFloat(p.playerMaxHp);
                            buf.writeFloat(p.playerAura);buf.writeFloat(p.playerMaxAura);
                            buf.writeFloat(p.playerMagic);buf.writeFloat(p.playerMaxMagic);
                            UUIDUtil.STREAM_CODEC.encode(buf, p.foxUuid == null ? new UUID(0,0) : p.foxUuid);
                            buf.writeVarInt(p.summonCooldownSeconds);
                        },
                        buf -> new OpenPanelPayload(
                                buf.readVarInt(),
                                buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(),
                                UUIDUtil.STREAM_CODEC.decode(buf),
                                buf.readVarInt()
                        ));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S：玩家点击"召唤"或"收回"按钮。 */
    public record ActionPayload(int action) implements CustomPacketPayload {
        public static final int SUMMON = 0;
        public static final int RECALL = 1;
        public static final Type<ActionPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraTNOMod.MOD_ID, "fox_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, ActionPayload::action,
                        ActionPayload::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─────────────── 注册 ───────────────

    public static void registerC2S() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                ActionPayload.TYPE, ActionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(() -> handleAction(payload, ctx)));
    }

    public static void registerS2C() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
                OpenPanelPayload.TYPE, OpenPanelPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.queue(() -> openClientScreen(payload)));
    }

    // ─────────────── 客户端打开屏幕（用反射避免在通用代码里直接引用客户端类） ───────────────

    private static void openClientScreen(OpenPanelPayload payload) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Class<?> screenCls = Class.forName("com.tensura_tno.client.screen.ContractLittleFoxScreen");

            // 若当前屏幕已是契约小狐面板，原地刷新数据（避免闪烁与鼠标居中）
            Object currentScreen = minecraftClass.getField("screen").get(minecraft);
            if (screenCls.isInstance(currentScreen)) {
                screenCls.getMethod("updatePayload", OpenPanelPayload.class)
                        .invoke(currentScreen, payload);
                return;
            }

            Object screen = screenCls
                    .getConstructor(OpenPanelPayload.class)
                    .newInstance(payload);
            minecraftClass.getMethod("setScreen",
                    Class.forName("net.minecraft.client.gui.screens.Screen"))
                    .invoke(minecraft, screen);
        } catch (Exception ignored) {}
    }

    // ─────────────── 服务端逻辑入口 ───────────────

    /** 只读：基于玩家当前状态构建 OpenPanelPayload 并下发。 */
    public static void openPanel(ServerPlayer player) {
        OpenPanelPayload payload = buildPanelPayload(player);
        NetworkManager.sendToPlayer(player, payload);
    }

    private static void handleAction(ActionPayload payload, NetworkManager.PacketContext ctx) {
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
        // 玩家必须拥有契约小狐技能才能操作（防止外挂客户端构造请求）
        Skills skills = SkillAPI.getSkillsFrom(player);
        Optional<ManasSkillInstance> opt = skills.getSkill(TensuraTNOSkills.CONTRACT_LITTLE_FOX.get());
        if (opt.isEmpty()) return;

        switch (payload.action) {
            case ActionPayload.SUMMON -> doSummon(player);
            case ActionPayload.RECALL -> doRecall(player);
            default -> { /* ignore */ }
        }
        // 操作完成后立即把最新面板状态推送给玩家
        openPanel(player);
    }

    // ─────────────── 召唤 ───────────────

    private static void doSummon(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // 已有活体狐灵——拒绝（同时只能 1 只）
        FoxSpiritEntity existing = findActiveFox(player);
        if (existing != null && existing.isAlive()) {
            player.displayClientMessage(
                    Component.translatable("tensura_tno.contract_little_fox.already_summoned"), true);
            return;
        }

        long nowTick = player.level().getGameTime();
        CompoundTag rootForCd = getRoot(player);
        if (rootForCd.contains(TAG_SUMMON_COOLDOWN_UNTIL_TICK, Tag.TAG_LONG)) {
            long until = rootForCd.getLong(TAG_SUMMON_COOLDOWN_UNTIL_TICK);
            if (nowTick < until) {
                return;
            }
            rootForCd.remove(TAG_SUMMON_COOLDOWN_UNTIL_TICK);
            saveRoot(player, rootForCd);
        }

        FoxSpiritEntity fox = TensuraTNOEntities.FOX_SPIRIT.get().create(level);
        if (fox == null) return;

        // 从玩家面前 1 格的位置生成
        var look = player.getLookAngle();
        double sx = player.getX() + look.x;
        double sy = player.getY();
        double sz = player.getZ() + look.z;
        fox.moveTo(sx, sy, sz, player.getYRot(), 0.0F);
        fox.setSummonerUUID(player.getUUID());
        // 让狐狸不再视玩家为天敌（trustedUUIDs 是 Fox 内部的"信任列表"）
        addTrustedFallback(fox, player.getUUID());

        level.addFreshEntity(fox);

        // 先加入世界再还原属性/技能存储，确保 ManasCore / bett 的 EntityStorage 已就绪
        CompoundTag root = getRoot(player);
        if (root.contains(TAG_RECALLED_STATS)) {
            applyRecalledStats(fox, root.getCompound(TAG_RECALLED_STATS));
            root.remove(TAG_RECALLED_STATS);
        } else {
            FoxSpiritEntity.applyHalfStatsFrom(fox, player);
        }

        root.putUUID(TAG_FOX_UUID, fox.getUUID());
        root.remove(TAG_SUMMON_COOLDOWN_UNTIL_TICK);
        saveRoot(player, root);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);

        // 立即下发面板更新，避免客户端在属性同步前显示默认值
        openPanel(player);
    }

    /** 若 Fox 有 trustedUUIDs，把召唤者加进去（避免狐狸在生成的瞬间因为不信任玩家而逃跑）。 */
    private static void addTrustedFallback(FoxSpiritEntity fox, UUID summonerId) {
        try {
            // Fox 内部维护私有方法 addTrustedUUID(UUID)，反射调用
            java.lang.reflect.Method m = fox.getClass()
                    .getSuperclass() // FoxSpiritEntity → Fox
                    .getDeclaredMethod("addTrustedUUID", UUID.class);
            m.setAccessible(true);
            m.invoke(fox, summonerId);
        } catch (Exception ignored) {
            // 不存在或失败也无妨，行为目标会处理
        }
    }

    // ─────────────── 收回 ───────────────

    private static void doRecall(ServerPlayer player) {
        FoxSpiritEntity fox = findActiveFox(player);
        if (fox == null || !fox.isAlive()) {
            player.displayClientMessage(
                    Component.translatable("tensura_tno.contract_little_fox.no_summoned"), true);
            return;
        }

        // 把当前属性 / EP / MP / HP / SHP / 攻击 全部存到玩家 NBT
        CompoundTag stats = snapshotStats(fox);
        CompoundTag root = getRoot(player);
        root.put(TAG_RECALLED_STATS, stats);
        root.remove(TAG_FOX_UUID);
        saveRoot(player, root);

        // 让狐灵悄悄消失（不算"死亡"，避免触发死亡清理）
        fox.discard();

        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static CompoundTag snapshotStats(FoxSpiritEntity fox) {
        IExistence existence = TensuraStorages.getExistenceFrom(fox);
        CompoundTag t = new CompoundTag();
        t.putFloat(K_HP, fox.getHealth());
        t.putLong(K_RECALLED_TIME, fox.level().getGameTime());
        // ── 保存 base value（不含 AttributeModifier），避免收回/再召唤时数值叠加 ──
        var maxHpAttr = fox.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        t.putFloat(K_MAX_HP, maxHpAttr != null ? (float) maxHpAttr.getBaseValue() : fox.getMaxHealth());
        t.putDouble(K_AURA, existence.getAura());
        t.putDouble(K_MAX_AURA, EnergyHelper.getBaseMaxAura(fox));
        t.putDouble(K_MAGIC, existence.getMagicule());
        t.putDouble(K_MAX_MAGIC, EnergyHelper.getBaseMaxMagicule(fox));
        t.putDouble(K_SHP, existence.getSpiritualHealth());
        var maxShpAttr = fox.getAttribute(
                io.github.manasmods.tensura.registry.attribute.TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        t.putDouble(K_MAX_SHP, maxShpAttr != null ? maxShpAttr.getBaseValue() : 0.0);
        var atkAttr = fox.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        t.putDouble(K_ATK, atkAttr != null ? atkAttr.getBaseValue() : 0.0);

        CompoundTag skillsNbt = new CompoundTag();
        SkillAPI.getSkillsFrom(fox).save(skillsNbt);
        t.put(TAG_TNO_LEARNED_SKILLS, skillsNbt);
        BetterSubsFoxCompat.saveExpandedSkillStorage(fox, t);
        return t;
    }

    private static void applyRecalledStats(FoxSpiritEntity fox, CompoundTag t) {
        EnergyHelper.setMaxAura(fox, t.getDouble(K_MAX_AURA));
        EnergyHelper.setMaxMagicule(fox, t.getDouble(K_MAX_MAGIC));
        var maxHp = fox.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(Math.max(1.0, t.getDouble(K_MAX_HP)));
        var maxShp = fox.getAttribute(
                io.github.manasmods.tensura.registry.attribute.TensuraAttributes.MAX_SPIRITUAL_HEALTH);
        if (maxShp != null) maxShp.setBaseValue(t.getDouble(K_MAX_SHP));
        var atk = fox.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(t.getDouble(K_ATK));

        IExistence existence = TensuraStorages.getExistenceFrom(fox);
        double savedAura = t.getDouble(K_AURA);
        double maxAuraVal = t.getDouble(K_MAX_AURA);
        double savedMagic = t.getDouble(K_MAGIC);
        double maxMagicVal = t.getDouble(K_MAX_MAGIC);
        existence.setSpiritualHealth(t.getDouble(K_SHP));

        // 收回期间被动恢复：每秒恢复 2% 最大值（HP / EP / MP）
        float savedHp = Math.max(1.0F, t.getFloat(K_HP));
        float maxHpVal = fox.getMaxHealth();
        if (t.contains(K_RECALLED_TIME)) {
            long recalledTime = t.getLong(K_RECALLED_TIME);
            long now = fox.level().getGameTime();
            long elapsed = Math.max(0, now - recalledTime);
            float secondsElapsed = elapsed / 20.0F;
            float regenRate = 0.02F * secondsElapsed; // 2% per second
            savedHp = Math.min(maxHpVal, savedHp + maxHpVal * regenRate);
            savedAura = Math.min(maxAuraVal, savedAura + maxAuraVal * regenRate);
            savedMagic = Math.min(maxMagicVal, savedMagic + maxMagicVal * regenRate);
        }
        fox.setHealth(savedHp);
        existence.setAura(savedAura);
        existence.setMagicule(savedMagic);
        existence.markDirty();

        if (t.contains(TAG_TNO_LEARNED_SKILLS, Tag.TAG_COMPOUND)) {
            CompoundTag skillData = t.getCompound(TAG_TNO_LEARNED_SKILLS).copy();
            skillData.putBoolean("resetExistingData", true);
            SkillAPI.getSkillsFrom(fox).load(skillData);
        }
        BetterSubsFoxCompat.loadExpandedSkillStorage(fox, t);
    }

    // ─────────────── 服务端事件入口 ───────────────

    /** 玩家死亡：清理一切——活体狐灵立即消失，存档清空。 */
    public static void onPlayerDeath(ServerPlayer player) {
        FoxSpiritEntity fox = findActiveFox(player);
        if (fox != null) fox.discard();
        clearAllState(player);
    }

    /**
     * 種族重置 / 人物重置卷轴使用：完全重置契约狐灵状态。
     * <p>调用场景：当玩家使用 Tensura 重置卷轴导致 CONTRACT_LITTLE_FOX 技能
     * 被移除时调用。效果：
     * <ul>
     *   <li>消除当前活体狐灵</li>
     *   <li>清空 NBT（FoxUUID + RecalledStats + 冷却）</li>
     *   <li>下次重新获得技能后召唤将按 "50% 初始化"</li>
     * </ul>
     */
    public static void onSkillReset(ServerPlayer player) {
        FoxSpiritEntity fox = findActiveFox(player);
        if (fox != null) fox.discard();
        clearAllState(player);
    }

    /** 小狐灵死亡：清空 FoxUUID + RecalledStats，下次召唤将"按 50% 重置"。 */
    public static void onFoxSpiritDeath(FoxSpiritEntity fox) {
        UUID summonerId = fox.getSummonerUUID().orElse(null);
        if (summonerId == null) return;
        if (!(fox.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(summonerId);
        if (owner == null) return;
        clearSummonProgress(owner);
        applyDeathSummonCooldown(owner);
    }

    /** 玩家死亡：清空契约存档与死亡冷却。 */
    private static void clearAllState(ServerPlayer player) {
        CompoundTag root = getRoot(player);
        root.remove(TAG_FOX_UUID);
        root.remove(TAG_RECALLED_STATS);
        root.remove(TAG_SUMMON_COOLDOWN_UNTIL_TICK);
        saveRoot(player, root);
    }

    /** 狐灵死亡：清空 UUID 与收回快照，保留/写入新的召唤冷却。 */
    private static void clearSummonProgress(ServerPlayer player) {
        CompoundTag root = getRoot(player);
        root.remove(TAG_FOX_UUID);
        root.remove(TAG_RECALLED_STATS);
        saveRoot(player, root);
    }

    private static void applyDeathSummonCooldown(ServerPlayer player) {
        CompoundTag root = getRoot(player);
        long until = player.level().getGameTime() + (long) SUMMON_COOLDOWN_AFTER_DEATH_TICKS;
        root.putLong(TAG_SUMMON_COOLDOWN_UNTIL_TICK, until);
        saveRoot(player, root);
    }

    // ─────────────── 工具方法 ───────────────

    /**
     * 在玩家所在世界里找到当前活体小狐灵；找不到时若 NBT 里残留了 FoxUUID 就清掉它。
     * 返回 null 表示当前玩家没有"活体小狐灵"。
     */
    public static @Nullable FoxSpiritEntity findActiveFox(ServerPlayer player) {
        CompoundTag root = getRoot(player);
        if (!root.hasUUID(TAG_FOX_UUID)) return null;
        UUID foxId = root.getUUID(TAG_FOX_UUID);
        if (player.level() instanceof ServerLevel level) {
            Entity e = level.getEntity(foxId);
            if (e instanceof FoxSpiritEntity fox && fox.isAlive()) {
                // 检查归属一致
                UUID expected = fox.getSummonerUUID().orElse(null);
                if (Objects.equals(expected, player.getUUID())) {
                    return fox;
                }
            }
        }
        // 残留的 UUID 清掉
        root.remove(TAG_FOX_UUID);
        saveRoot(player, root);
        return null;
    }

    private static int computeSummonCooldownSecondsRemaining(ServerPlayer player, CompoundTag root) {
        if (!root.contains(TAG_SUMMON_COOLDOWN_UNTIL_TICK, Tag.TAG_LONG)) {
            return 0;
        }
        long until = root.getLong(TAG_SUMMON_COOLDOWN_UNTIL_TICK);
        long now = player.level().getGameTime();
        if (now >= until) {
            return 0;
        }
        return (int) Math.ceil((until - now) / 20.0);
    }

    private static OpenPanelPayload buildPanelPayload(ServerPlayer player) {
        FoxSpiritEntity fox = findActiveFox(player);
        CompoundTag root = getRoot(player);
        int cooldownSec = computeSummonCooldownSecondsRemaining(player, root);
        IExistence playerExistence = TensuraStorages.getExistenceFrom(player);
        float pHp = player.getHealth();
        float pMaxHp = player.getMaxHealth();
        float pAura = (float) playerExistence.getAura();
        float pMaxAura = (float) EnergyHelper.getBaseMaxAura(player);
        float pMagic = (float) playerExistence.getMagicule();
        float pMaxMagic = (float) EnergyHelper.getBaseMaxMagicule(player);

        if (fox != null && fox.isAlive()) {
            IExistence fe = TensuraStorages.getExistenceFrom(fox);
            return new OpenPanelPayload(
                    1,
                    fox.getHealth(), fox.getMaxHealth(),
                    (float) fe.getAura(), (float) EnergyHelper.getBaseMaxAura(fox),
                    (float) fe.getMagicule(), (float) EnergyHelper.getBaseMaxMagicule(fox),
                    pHp, pMaxHp, pAura, pMaxAura, pMagic, pMaxMagic,
                    fox.getUUID(),
                    0
            );
        }
        if (root.contains(TAG_RECALLED_STATS)) {
            CompoundTag s = root.getCompound(TAG_RECALLED_STATS);
            // 计算收回期间的被动恢复（HP / EP / MP，每秒 2%）
            float recalledHp = s.getFloat(K_HP);
            float recalledMaxHp = s.getFloat(K_MAX_HP);
            float recalledAura = (float) s.getDouble(K_AURA);
            float recalledMaxAura = (float) s.getDouble(K_MAX_AURA);
            float recalledMagic = (float) s.getDouble(K_MAGIC);
            float recalledMaxMagic = (float) s.getDouble(K_MAX_MAGIC);
            if (s.contains(K_RECALLED_TIME)) {
                long recalledTime = s.getLong(K_RECALLED_TIME);
                long now = player.level().getGameTime();
                long elapsed = Math.max(0, now - recalledTime);
                float secondsElapsed = elapsed / 20.0F;
                float regenRate = 0.02F * secondsElapsed;
                recalledHp = Math.min(recalledMaxHp, recalledHp + recalledMaxHp * regenRate);
                recalledAura = Math.min(recalledMaxAura, recalledAura + recalledMaxAura * regenRate);
                recalledMagic = Math.min(recalledMaxMagic, recalledMagic + recalledMaxMagic * regenRate);
            }
            return new OpenPanelPayload(
                    2,
                    recalledHp, recalledMaxHp,
                    recalledAura, recalledMaxAura,
                    recalledMagic, recalledMaxMagic,
                    pHp, pMaxHp, pAura, pMaxAura, pMagic, pMaxMagic,
                    new UUID(0, 0),
                    cooldownSec
            );
        }
        // 未召唤过：展示"50% 预览值"（使用最大值，不受玩家当前血量影响）
        return new OpenPanelPayload(
                0,
                pMaxHp * 0.5F, pMaxHp * 0.5F,
                pMaxAura * 0.5F, pMaxAura * 0.5F,
                pMaxMagic * 0.5F, pMaxMagic * 0.5F,
                pHp, pMaxHp, pAura, pMaxAura, pMagic, pMaxMagic,
                new UUID(0, 0),
                cooldownSec
        );
    }

    private static CompoundTag getRoot(ServerPlayer player) {
        return player.getPersistentData().getCompound(TAG_ROOT);
    }

    private static void saveRoot(ServerPlayer player, CompoundTag tag) {
        player.getPersistentData().put(TAG_ROOT, tag);
    }

    private ContractLittleFoxPackets() {}
}
