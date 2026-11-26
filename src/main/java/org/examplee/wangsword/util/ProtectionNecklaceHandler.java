package org.examplee.wangsword.util;

import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 保护项链：把抗性提升戒指熔进赤金项链，效果是两层叠起来的减伤。
 *
 * <p>第一层是<b>抗性提升 III</b>，和戒指给的完全一致，靠 {@code tick} 续期。
 *
 * <p>第二层是<b>真减伤</b>：在护甲、抗性、保护附魔全部结算完之后，再把最终伤害砍掉
 * {@value #FINAL_REDUCTION_PERCENT}%。这一层的意义在于它不受原版「抗性效果最高 80%」
 * 这条上限约束——抗性已经顶到 IV 时再戴项链，实际减伤仍会继续往上走。
 *
 * <p>第二层的注入点在 {@code LivingEntity#modifyAppliedDamage} 的 RETURN，
 * 见 {@code mixin/ProtectionNecklaceMixin}。
 */
public final class ProtectionNecklaceHandler {

    /** 最终伤害再砍掉的比例（0.30 = 30%） */
    public static final float FINAL_REDUCTION = 0.30F;
    /** 提示文本里用的整数百分比 */
    public static final int FINAL_REDUCTION_PERCENT = 30;

    /** 抗性提升等级：2 = III 级，与抗性提升戒指一致 */
    private static final int RESIST_AMPLIFIER = 2;
    /** 每次续期的持续时间（tick），400 = 20 秒 */
    private static final int RESIST_DURATION = 400;

    private ProtectionNecklaceHandler() {
    }

    /** 每 tick 由项链物品推一次：维持抗性提升 III */
    public static void onTick(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
        if (current == null || current.getAmplifier() < RESIST_AMPLIFIER || current.getDuration() < 120) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, RESIST_DURATION, RESIST_AMPLIFIER, false, false, true));
        }
    }

    /** 最终伤害再砍 30%；掉虚空不参与，那是无法靠减伤脱身的 */
    public static float reduceFinalDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return amount;
        }
        if (source.isOf(DamageTypes.OUT_OF_WORLD)) {
            return amount;
        }
        if (!isWearing(player)) {
            return amount;
        }
        return amount * (1.0F - FINAL_REDUCTION);
    }

    public static boolean isWearing(ServerPlayerEntity player) {
        return AccessoriesCapability.getOptionally(player)
                .map(capability -> capability.isEquipped(ItemsRegistry.WS_NECKLACE_PROTECTION))
                .orElse(false);
    }
}
