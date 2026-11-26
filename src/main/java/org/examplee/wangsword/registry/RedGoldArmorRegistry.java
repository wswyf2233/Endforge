package org.examplee.wangsword.registry;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.RedGoldArmorMaterial;
import org.examplee.wangsword.Wangsword;

/**
 * 赤金盔甲四件套。
 *
 * <p>材质数值见 {@link RedGoldArmorMaterial}，比下界合金略高。
 */
public final class RedGoldArmorRegistry {

    public static final Item REDGOLD_HELMET =
            register("ws_redgolden_helmet", ArmorItem.Type.HELMET);

    public static final Item REDGOLD_CHESTPLATE =
            register("ws_redgolden_chestplate", ArmorItem.Type.CHESTPLATE);

    public static final Item REDGOLD_LEGGINGS =
            register("ws_redgolden_leggings", ArmorItem.Type.LEGGINGS);

    public static final Item REDGOLD_BOOTS =
            register("ws_redgolden_boots", ArmorItem.Type.BOOTS);

    private RedGoldArmorRegistry() {
    }

    private static Item register(String id, ArmorItem.Type type) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(Wangsword.MOD_ID, id),
                new ArmorItem(RedGoldArmorMaterial.INSTANCE, type, new Item.Settings()));
    }

    public static void init() {
        // 静态字段在类加载时已完成注册，这里只是显式触发一次
        Wangsword.LOGGER.info("赤金盔甲加载完毕");
    }
}
