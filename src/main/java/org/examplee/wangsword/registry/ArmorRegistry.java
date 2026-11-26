package org.examplee.wangsword.registry;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.RedGoldArmorMaterial;
import org.examplee.wangsword.Material.WsArmorMaterial;
import org.examplee.wangsword.Wangsword;

/**
 * 紫金盔甲（wsarmor）。
 *
 * <p>四个部位共用一套材质，数值在 WsArmorMaterial 里改。
 */
public class ArmorRegistry {

    public static final Item WSARMOR_HELMET = register("wsarmor_helmet", ArmorItem.Type.HELMET);
    public static final Item WSARMOR_CHESTPLATE = register("wsarmor_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final Item WSARMOR_LEGGINGS = register("wsarmor_leggings", ArmorItem.Type.LEGGINGS);
    public static final Item WSARMOR_BOOTS = register("wsarmor_boots", ArmorItem.Type.BOOTS);

    /**
     * 熔岩战盔：熔核沉坛的 3D 头盔。
     *
     * <p>材质直接复用 {@link WsArmorMaterial}，所以护甲值/耐久/韧性/击退抗性
     * 与紫金头盔一样；但它只有外观（GeckoLib 模型），不提供任何技能与套装效果。
     */
    public static final Item CALDERA_HELM = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_caldera_helm"),
            new org.examplee.wangsword.item.unique.CalderaHelmItem(
                    WsArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Settings())
    );

    /**
     * 熔岩胸甲：熔核沉坛的 3D 胸甲，材质同样复用 {@link WsArmorMaterial}。
     */
    public static final Item CALDERA_CUIRASS = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_caldera_cuirass"),
            new org.examplee.wangsword.item.unique.CalderaCuirassItem(
                    WsArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Settings())
    );

    /**
     * 熔岩护腿：3D 护腿，材质同样复用 {@link WsArmorMaterial}。
     */
    public static final Item CALDERA_GREAVES = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_caldera_greaves"),
            new org.examplee.wangsword.item.unique.CalderaGreavesItem(
                    WsArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Settings())
    );

    /**
     * 熔岩战靴：3D 靴子，材质同样复用 {@link WsArmorMaterial}。
     */
    public static final Item CALDERA_BOOTS = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_caldera_boots"),
            new org.examplee.wangsword.item.unique.CalderaBootsItem(
                    WsArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Settings())
    );

    /**
     * 幽卫钢战盔：击败守卫后可制作的 3D 头盔。
     *
     * <p>材质用 {@link RedGoldArmorMaterial}，所以护甲值/耐久/韧性/击退抗性
     * 与赤金头盔完全一致；暂时没有技能。
     */
    public static final Item FULGUR_HELM = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_fulgur_helm"),
            new org.examplee.wangsword.item.unique.FulgurHelmItem(
                    RedGoldArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new Item.Settings())
    );

    /**
     * 幽卫钢胸甲：与战盔同一套，材质同样是 {@link RedGoldArmorMaterial}，数值对齐赤金胸甲。
     */
    public static final Item FULGUR_CUIRASS = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_fulgur_cuirass"),
            new org.examplee.wangsword.item.unique.FulgurCuirassItem(
                    RedGoldArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new Item.Settings())
    );

    /**
     * 幽卫钢护腿：3D 模型护腿，材质同为 {@link RedGoldArmorMaterial}（= 赤金护腿数值）。
     */
    public static final Item FULGUR_GREAVES = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_fulgur_greaves"),
            new org.examplee.wangsword.item.unique.FulgurGreavesItem(
                    RedGoldArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new Item.Settings())
    );

    /** 幽卫钢战靴：3D 模型战靴，材质同为 {@link RedGoldArmorMaterial}（= 赤金靴子数值） */
    public static final Item FULGUR_BOOTS = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_fulgur_boots"),
            new org.examplee.wangsword.item.unique.FulgurBootsItem(
                    RedGoldArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new Item.Settings())
    );

    private static Item register(String id, ArmorItem.Type type) {
        return Registry.register(
                Registries.ITEM,
                new Identifier(Wangsword.MOD_ID, id),
                new org.examplee.wangsword.item.unique.AmethystArmorItem(WsArmorMaterial.INSTANCE, type, new Item.Settings())
        );
    }

    /** 客户端 -> 服务端：请求展开鞘翅滑翔 */
    public static final net.minecraft.util.Identifier ELYTRA_PACKET =
            new net.minecraft.util.Identifier(Wangsword.MOD_ID, "elytra_start");

    /** 客户端 -> 服务端：滑翔中用烟花推进 */
    public static final net.minecraft.util.Identifier FIREWORK_PACKET =
            new net.minecraft.util.Identifier(Wangsword.MOD_ID, "firework_boost");

    /** 是否穿齐四件紫金盔甲 */
    public static boolean isFullSet(net.minecraft.entity.LivingEntity entity) {
        return entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).getItem() == WSARMOR_HELMET
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem() == WSARMOR_CHESTPLATE
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS).getItem() == WSARMOR_LEGGINGS
                && entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET).getItem() == WSARMOR_BOOTS;
    }

    public static void init() {
        Wangsword.LOGGER.info("紫金盔甲模块加载完毕！");
    }
}