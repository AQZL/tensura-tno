package com.tensura_tno.client;

import java.lang.reflect.Method;

import com.tensura_tno.TensuraTNOMod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端兜底：当 block_factorys_bosses 的 cinematic camera 因动画 keyframe 时序错位
 * 而未能正常释放时，强制解锁玩家镜头，防止视角永久卡在 boss 骨头上。
 *
 * <h3>问题背景</h3>
 *
 * BossesRise 的 cinematic camera 由 GeckoLib 动画 keyframe 驱动：
 * <ul>
 *   <li>{@code cinematic_start;CAMERA;camera;} 把镜头切到 boss 骨头上</li>
 *   <li>{@code cinematic_end;} 释放所有 cinematic handler（含 CAMERA）</li>
 * </ul>
 * 释放只在「持有 cinematic-end keyframe 的那段动画完整播完」时才发生。当 boss 状态机
 * 切换 state（例如 phase 转换完成进入 idle）后，原 cinematic 动画被新 state 的 idle
 * 动画取代，cinematic-end keyframe 永远不会触发，{@code activeHandlers} 中的
 * {@code CAMERA} 项不被移除，{@code CameraRenderer.computeCameraAngles} 持续把玩家
 * 镜头位置 / yaw / pitch 覆写到 boss 骨头上 —— 玩家被永久锁定在 boss 视角。
 *
 * <p>赫尔瓦（Helvar, the Underworld Knight）的二阶段转换是已知触发该 bug 的场景。
 * 原版黑屏盖住了这段视角异常，玩家从未察觉；当我们禁用全屏黑屏后这个问题暴露出来。
 *
 * <h3>修法</h3>
 *
 * 监听客户端 {@link ClientTickEvent.Post}，每 tick 检查 BossesRise 是否仍在
 * cinematic 状态。两条解锁条件：
 * <ol>
 *   <li><b>超时</b>：cinematic 持续 ≥ {@link #STUCK_TICKS_THRESHOLD} tick (~35 秒)。
 *       BossesRise 已知最长合法 cinematic 动画为 24.75 秒 (495 tick)，35 秒留出
 *       充足缓冲，确保不打断任何合法演出，仅在 keyframe 时序错位卡死时介入。</li>
 *   <li><b>距离过远</b>：玩家与 trackedTarget 距离 &gt; {@link #MAX_TRACKED_DISTANCE}
 *       格。这等价于 BossesRise 自己在玩家断线 / 重生 / 离开实体追踪范围时调用的
 *       {@code stopCinematicCamera} 兜底，但触发更早（玩家逃出 boss 房后立即解锁）。</li>
 * </ol>
 *
 * 解锁通过反射调用 {@code BossesRiseClientCinematicCamera.stopCinematicCamera()}。
 * 反射类不可达（mod 未加载 / 内部 API 改名）时永久关闭兜底，不污染日志。
 *
 * <h3>不影响合法 cinematic</h3>
 *
 * 本类只在「卡死」边界条件下介入：35 秒超时仅当 keyframe 永远不触发才会到达，
 * 距离阈值仅当 trackedTarget 已经远离视野才触发。所有正常的 boss intro / phase
 * 转换 / death 等 cinematic 动画都会按 BossesRise 自身的 keyframe 流程播完并释放。
 */
@EventBusSubscriber(modid = TensuraTNOMod.MOD_ID, value = Dist.CLIENT)
public final class BossesRiseCinematicWatchdog {

    private static final String MOD_ID = "block_factorys_bosses";
    private static final String CAMERA_CLASS =
            "net.unusual.block_factorys_bosses.client.camera.BossesRiseClientCinematicCamera";

    /**
     * Cinematic 持续时长（client tick）阈值。
     *
     * <p>BossesRise 自带最长 cinematic 动画 24.75 秒 = 495 tick（赫尔瓦二阶段
     * 转换），加上动画 lerp / GeckoLib 调度抖动给出 ~205 tick (10 秒) 缓冲，最终阈
     * 值 700 tick (35 秒)。低于该值时不介入，确保所有合法演出按 BossesRise 自己
     * 的 keyframe 流程结束。
     */
    private static final int STUCK_TICKS_THRESHOLD = 35 * 20;

    /**
     * 玩家与 trackedTarget 的最大距离（方块）。
     *
     * <p>BossesRise boss 战场半径上限 ~64 格 (战斗区域常驻 32 - 48)；80 格意味着
     * 玩家已远离战场，此时锁视角无意义。
     */
    private static final double MAX_TRACKED_DISTANCE = 80.0D;

    /** BossesRise 客户端类反射缓存。{@code null} 表示 mod 未加载或反射失败。 */
    private static volatile Class<?> cameraClass;
    private static volatile Method isCameraActiveMethod;
    private static volatile Method getTrackedObjectMethod;
    private static volatile Method stopCinematicCameraMethod;

    /** 一次失败后永久关闭 watchdog 以避免每 tick 抛异常 + 刷日志。 */
    private static volatile boolean disabled = false;

    /** Cinematic 持续 tick 计数；非 cinematic 状态归零。 */
    private static int cinematicTicks = 0;

    private BossesRiseCinematicWatchdog() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (disabled) return;
        if (!ModList.get().isLoaded(MOD_ID)) {
            disabled = true;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            cinematicTicks = 0;
            return;
        }
        String playerName = player.getGameProfile().getName();
        Vec3 playerPos = player.position();

        try {
            ensureReflectionInitialized();

            boolean active = (Boolean) isCameraActiveMethod.invoke(null);
            if (!active) {
                cinematicTicks = 0;
                return;
            }

            cinematicTicks++;

            // 条件 1：超时。
            if (cinematicTicks >= STUCK_TICKS_THRESHOLD) {
                forceStop(playerName,
                        "cinematic camera stuck for " + cinematicTicks
                        + " ticks (>= " + STUCK_TICKS_THRESHOLD + "), forcing release");
                return;
            }

            // 条件 2：玩家远离 trackedTarget。仅对 Entity 类型 trackedTarget 检查
            // （BlockEntity 类型 trackedTarget 一般是 boss 房门动画，距离判断不适用）。
            Object tracked = getTrackedObjectMethod.invoke(null);
            if (tracked instanceof Entity entity) {
                Vec3 entityPos = entity.position();
                double dx = playerPos.x - entityPos.x;
                double dz = playerPos.z - entityPos.z;
                double horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > MAX_TRACKED_DISTANCE * MAX_TRACKED_DISTANCE) {
                    forceStop(playerName,
                            "player ("
                            + String.format("%.1f", Math.sqrt(horizontalSq))
                            + " blocks from tracked target) > " + MAX_TRACKED_DISTANCE
                            + ", forcing release");
                }
            }
        } catch (ReflectiveOperationException e) {
            disabled = true;
            cinematicTicks = 0;
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] BossesRiseCinematicWatchdog disabled: reflection failed", e);
        }
    }

    private static void ensureReflectionInitialized() throws ReflectiveOperationException {
        if (cameraClass != null) return;
        Class<?> clazz = Class.forName(CAMERA_CLASS);
        cameraClass = clazz;
        isCameraActiveMethod = clazz.getMethod("isCameraActive");
        getTrackedObjectMethod = clazz.getMethod("getTrackedObject");
        stopCinematicCameraMethod = clazz.getMethod("stopCinematicCamera");
    }

    private static void forceStop(String playerName, String reason) {
        try {
            stopCinematicCameraMethod.invoke(null);
            cinematicTicks = 0;
            TensuraTNOMod.LOGGER.info(
                    "[TensuraTNO][BossesRiseWatchdog] {} (player={})",
                    reason, playerName);
        } catch (ReflectiveOperationException e) {
            disabled = true;
            TensuraTNOMod.LOGGER.error(
                    "[TensuraTNO] BossesRiseCinematicWatchdog forceStop failed", e);
        }
    }
}
