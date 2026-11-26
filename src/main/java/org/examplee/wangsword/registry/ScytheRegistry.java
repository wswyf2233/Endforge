package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 死神镰刀（ws_deathscythe）。
 *
 * <p>双手武器，攻速慢、单次伤害高；攻击动作由 Better Combat 的 scythe 模组提供。
 */
public class ScytheRegistry {

    /** 追加伤害：材质 4 + 追加 12 + 玩家基础 1 = 面板 17 */
    public static final int SCYTHE_ADD_ATTACK = 12;

    /** 攻速修正：基础 4.0 + (-2.8) = 1.2，约 20 tick 一刀 */
    public static final float SCYTHE_ATTACK_SPEED = -2.8F;

    /** 耐久 */
    public static final int SCYTHE_DURABILITY = 2600;

    public static final Item WS_DEATHSCYTHE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_deathscythe"),
            new org.examplee.wangsword.item.unique.DeathScytheItem(
                    ItemsRegistry.CUSTOM_NETHERITE_MATERIAL,
                    SCYTHE_ADD_ATTACK,
                    SCYTHE_ATTACK_SPEED,
                    new Item.Settings().maxCount(1).maxDamage(SCYTHE_DURABILITY))
    );

    public static void init() {
        Wangsword.LOGGER.info("死神镰刀模块加载完毕！");
    }
}
