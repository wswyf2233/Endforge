package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 盾牌（ws_shield）。
 *
 * <p>使用 Blockbench 3D 模型（models/item/ws_shieldmodel.json）渲染，
 * 贴图为 textures/item/ws_shield_model.png，不走原版盾的 BEWLR。
 */
public class ShieldRegistry {

    /** 耐久 */
    public static final int SHIELD_DURABILITY = 1200;

    public static final Item WS_SHIELD = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_shield"),
            new ShieldItem(new Item.Settings().maxCount(1).maxDamage(SHIELD_DURABILITY))
    );

    public static void init() {
        Wangsword.LOGGER.info("盾牌模块加载完毕！");
    }
}