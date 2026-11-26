package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/** 凋灵杀手（ws_witherspawn_killer）。 */
public class WitherKillerRegistry {

    public static final int KILLER_ADD_ATTACK = 8;
    public static final float KILLER_ATTACK_SPEED = -2.2F;
    public static final int KILLER_DURABILITY = 2000;

    public static final Item WS_WITHERSPAWN_KILLER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_witherspawn_killer"),
            new org.examplee.wangsword.item.unique.WitherspawnKillerItem(
                    ItemsRegistry.CUSTOM_NETHERITE_MATERIAL,
                    KILLER_ADD_ATTACK,
                    KILLER_ATTACK_SPEED,
                    new Item.Settings().maxCount(1).maxDamage(KILLER_DURABILITY))
    );

    public static void init() {
        Wangsword.LOGGER.info("凋灵杀手模块加载完毕！");
    }
}