package org.examplee.wangsword.registry;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.effect.FrozenBossEffect;
import org.examplee.wangsword.effect.FrozenEffect;
import org.examplee.wangsword.effect.OathBlessingEffect;

/** 自定义状态效果注册。 */
public class EffectRegistry {

    /** 誓约的祝福：同时持有誓约胜利之剑 + 阿瓦隆时获得 */
    public static final StatusEffect OATH_BLESSING = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier(Wangsword.MOD_ID, "oath_blessing"),
            new OathBlessingEffect());

    /** 冰封：把生物定在原地，无法行走也无法被击退 */
    public static final StatusEffect FROZEN = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier(Wangsword.MOD_ID, "frozen"),
            new FrozenEffect());

    /** 霜缚：冰封的 Boss 级降级版，只减速，保留 AI 与攻击 */
    public static final StatusEffect FROSTBOUND = Registry.register(
            Registries.STATUS_EFFECT,
            new Identifier(Wangsword.MOD_ID, "frostbound"),
            new FrozenBossEffect());

    public static void init() {
        Wangsword.LOGGER.info("状态效果模块加载完毕");
    }
}
