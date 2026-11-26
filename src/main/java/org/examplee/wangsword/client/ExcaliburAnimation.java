package org.examplee.wangsword.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.AnimationStack;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.item.unique.ExcaliburItem;
import org.examplee.wangsword.registry.ItemsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 用 PlayerAnimator 播誓约胜利之剑的两套动作，并让它以「第三人称模型」的方式进第一人称
 * （{@link FirstPersonMode#THIRD_PERSON_MODEL}），这样第一人称看到的是同一套身体动画。
 *
 * <p>两套动作互斥，同一时刻只挂一层：
 * <ul>
 *   <li>按住 X 蓄力 / 下劈：{@code charge_slash}</li>
 *   <li>按住右键风王结界：{@code wind_barrier}</li>
 * </ul>
 */
public final class ExcaliburAnimation {

    private static final Logger LOGGER = LoggerFactory.getLogger("wangsword-anim");

    private static final String CHARGE = "charge_slash";
    private static final String WIND = "wind_barrier";

    /** 层优先级：比 Better Combat 的 1~4 高一位即可 */
    private static final int PRIORITY = 5;

    private static String currentId = null;
    private static KeyframeAnimationPlayer current = null;

    private ExcaliburAnimation() {
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        String wanted = wanted(client.player);
        if (wanted == null) {
            if (currentId != null) {
                stop(client.player);
            }
            return;
        }
        if (!wanted.equals(currentId)) {
            stop(client.player);
            play(client.player, wanted);
        }
    }

    /** PlayerAnimator 正在驱动这套动作时为 true；此时代码版姿势要让开，否则两边同时写骨骼会抖 */
    public static boolean isDriving() {
        return current != null && current.isActive();
    }

    public static void reset() {
        current = null;
        currentId = null;
    }

    /** 当前该播哪个动作；没有就返回 null */
    private static String wanted(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.getItem() != ItemsRegistry.WS_EXCALIBUR) {
            return null;
        }
        if (player.isUsingItem() && player.getActiveItem().getItem() == ItemsRegistry.WS_EXCALIBUR) {
            return WIND;
        }
        if (ExcaliburItem.CHARGE_ELAPSED > 0 || ExcaliburItem.FIRE_ANIM > 0) {
            return CHARGE;
        }
        return null;
    }

    private static void play(ClientPlayerEntity player, String id) {
        KeyframeAnimation animation = find(id);
        if (animation == null) {
            LOGGER.warn("[wangsword] 动画没加载上: {}", id);
            return;
        }
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            stack.removeLayer(PRIORITY);

            KeyframeAnimationPlayer instance = new KeyframeAnimationPlayer(animation);
            instance.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            instance.setFirstPersonConfiguration(
                    new FirstPersonConfiguration(true, true, true, true));

            stack.addAnimLayer(PRIORITY, instance);
            current = instance;
            currentId = id;
        } catch (Exception e) {
            LOGGER.error("[wangsword] 播放动作失败", e);
            current = null;
            currentId = null;
        }
    }

    private static void stop(ClientPlayerEntity player) {
        KeyframeAnimationPlayer instance = current;
        current = null;
        currentId = null;
        try {
            AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(player);
            if (instance != null) {
                stack.removeLayer(instance);
            }
            stack.removeLayer(PRIORITY);
        } catch (Exception ignored) {
            // 玩家动画栈已经没了
        }
    }

    private static KeyframeAnimation find(String id) {
        Identifier key = new Identifier("wangsword", id);
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(key);
        if (animation != null) {
            return animation;
        }
        for (Map.Entry<Identifier, KeyframeAnimation> entry
                : PlayerAnimationRegistry.getAnimations().entrySet()) {
            if (entry.getKey().toString().endsWith(id)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
