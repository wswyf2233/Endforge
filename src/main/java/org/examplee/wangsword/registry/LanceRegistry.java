package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.item.unique.LanceItem;

public class LanceRegistry {

    /** 面板攻击力：材料 4 + 追加 12 = 16（再加玩家基础 1 = 17） */
    public static final int LANCE_ADD_ATTACK = 12;
    /** 攻速修正：总攻速 2.2，冷却约 9 tick */
    public static final float LANCE_ATTACK_SPEED = -1.8F;

    public static final Item WS_LANCE = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_lance"),
            new LanceItem(ItemsRegistry.CUSTOM_NETHERITE_MATERIAL, LANCE_ADD_ATTACK, LANCE_ATTACK_SPEED,
                    new Item.Settings().maxCount(1).maxDamage(1200))
    );

    public static void init() {
        Wangsword.LOGGER.info("长矛模块加载完毕！");
    }
}
