package org.examplee.wangsword.registry;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.TaiguArmorMaterial;
import org.examplee.wangsword.Wangsword;

/**
 * 末地合金盔甲四件套。
 *
 * <p>数值见 {@link TaiguArmorMaterial}，高于紫金套。技能与配方暂未实现。
 */
public final class TaiguArmorRegistry {

    public static final Item TAIGU_HELMET =
            register("ws_taiguarmor_helmet", ArmorItem.Type.HELMET);

    public static final Item TAIGU_CHESTPLATE =
            register("ws_taiguarmor_chestplate", ArmorItem.Type.CHESTPLATE);

    public static final Item TAIGU_LEGGINGS =
            register("ws_taiguarmor_leggings", ArmorItem.Type.LEGGINGS);

    public static final Item TAIGU_BOOTS =
            register("ws_taiguarmor_boots", ArmorItem.Type.BOOTS);

    private TaiguArmorRegistry() {
    }

    private static Item register(String id, ArmorItem.Type type) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(Wangsword.MOD_ID, id),
                new org.examplee.wangsword.item.unique.TaiguArmorItem(
                        TaiguArmorMaterial.INSTANCE, type, new Item.Settings()));
    }

    /** 是否穿齐四件末地合金盔甲 */
    public static boolean isFullSet(net.minecraft.entity.LivingEntity entity) {
        return entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(TAIGU_HELMET)
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(TAIGU_CHESTPLATE)
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS).isOf(TAIGU_LEGGINGS)
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET).isOf(TAIGU_BOOTS);
    }

    public static void init() {
        Wangsword.LOGGER.info("末地合金盔甲加载完毕");
    }
}
