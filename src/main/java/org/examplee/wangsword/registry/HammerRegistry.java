package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import org.examplee.wangsword.item.unique.GreatHammerItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 巨锤（ws_greathammer）。
 *
 * <p>数值全部集中在下面几个常量里，改这里就行。
 */
public class HammerRegistry {

    /** 追加伤害：材料 4 + 追加 30 + 玩家基础 1 = 面板 35 */
    public static final int HAMMER_ADD_ATTACK = 30;

    /** 攻速修正：玩家基础 4.0 + (-3.0) = 总攻速 1.0，约 20 tick 一下 */
    public static final float HAMMER_ATTACK_SPEED = -3.0F;

    /** 耐久 */
    public static final int HAMMER_DURABILITY = 8888;

    public static final Item WS_GREATHAMMER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_greathammer"),
            new GreatHammerItem(
                    ItemsRegistry.CUSTOM_NETHERITE_MATERIAL,
                    HAMMER_ADD_ATTACK,
                    HAMMER_ATTACK_SPEED,
                    new Item.Settings().maxCount(1).maxDamage(HAMMER_DURABILITY))
    );

    public static void init() {
        Wangsword.LOGGER.info("巨锤模块加载完毕！");
    }
}