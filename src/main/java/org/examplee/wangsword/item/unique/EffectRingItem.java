package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import org.examplee.wangsword.util.EquippedTickable;

/**
 * 药水效果戒指。
 *
 * <p>戴在戒指槽时持续提供对应效果；取下来约 20 秒后效果自然消失。
 *
 * <p>续期走 {@link EquippedTickable}（由 {@code AccessoryTicker} 每 tick 扫槽位调用），
 * 不走 {@code Accessory#tick}：后者依赖 Accessories 自己的容器派发，重新登录后可能整段不跑，
 * 效果就断了，必须摘下来再戴一次。
 */
public class EffectRingItem extends Item implements Accessory, EquippedTickable {

    /** 每次续期的持续时间（tick），够长到不会闪烁 */
    private static final int REFRESH_DURATION = 400;

    private final StatusEffect effect;
    private final int amplifier;

    public EffectRingItem(Settings settings, StatusEffect effect, int amplifier) {
        super(settings);
        this.effect = effect;
        this.amplifier = amplifier;
    }

    public StatusEffect getEffect() {
        return effect;
    }

    public int getAmplifier() {
        return amplifier;
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 120) {
            player.addStatusEffect(
                    new StatusEffectInstance(effect, REFRESH_DURATION, amplifier, false, false, true));
        }
    }
}
