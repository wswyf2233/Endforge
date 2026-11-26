package org.examplee.wangsword.registry;

import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.WsToolMaterial;
import org.examplee.wangsword.Wangsword;

/**
 * 紫金工具五件套：剑、斧、镐、锹、锄。
 *
 * <p>数值参考下界合金，但全面加强。
 */
public class ToolRegistry {

    public static final Item WS_PURPLE_SWORD = register("ws_purple_sword",
            new org.examplee.wangsword.item.unique.PurpleSwordItem(WsToolMaterial.INSTANCE, 5, -2.2F, new Item.Settings()));

    public static final Item WS_PURPLE_AXE = register("ws_purple_axe",
            new AxeItem(WsToolMaterial.INSTANCE, 7.0F, -2.8F, new Item.Settings()));

    public static final Item WS_PURPLE_PICKAXE = register("ws_purple_pickaxe",
            new PickaxeItem(WsToolMaterial.INSTANCE, 3, -2.6F, new Item.Settings()));

    public static final Item WS_PURPLE_SHOVEL = register("ws_purple_shovel",
            new ShovelItem(WsToolMaterial.INSTANCE, 3.5F, -2.8F, new Item.Settings()));

    public static final Item WS_PURPLE_HOE = register("ws_purple_hoe",
            new HoeItem(WsToolMaterial.INSTANCE, -2, 0.0F, new Item.Settings()));

    /** 末地合金剑：紫金剑的末地合金升级版 */
    public static final Item WS_TAIGU_SWORD = register("ws_taigu_sword",
            new org.examplee.wangsword.item.unique.TaiguSwordItem(org.examplee.wangsword.Material.TaiguToolMaterial.INSTANCE, 8, -2.2F, new Item.Settings()));

    /** 末地合金斧 */
    public static final Item WS_TAIGU_AXE = register("ws_taigu_axe",
            new AxeItem(org.examplee.wangsword.Material.TaiguToolMaterial.INSTANCE, 9.0F, -2.8F, new Item.Settings()));

    /** 末地合金镐 */
    public static final Item WS_TAIGU_PICKAXE = register("ws_taigu_pickaxe",
            new PickaxeItem(org.examplee.wangsword.Material.TaiguToolMaterial.INSTANCE, 4, -2.6F, new Item.Settings()));

    /** 末地合金锹 */
    public static final Item WS_TAIGU_SHOVEL = register("ws_taigu_shovel",
            new ShovelItem(org.examplee.wangsword.Material.TaiguToolMaterial.INSTANCE, 4.5F, -2.8F, new Item.Settings()));

    /** 末地合金锄 */
    public static final Item WS_TAIGU_HOE = register("ws_taigu_hoe",
            new HoeItem(org.examplee.wangsword.Material.TaiguToolMaterial.INSTANCE, -3, 0.0F, new Item.Settings()));

    /**
     * 雷电太刀：后期太刀，紫金剑被八枚雷电宝石围住淬出来的。
     *
     * <p>总伤 = 1(空手) + 8 + 材质 12 = 21，攻速 2.05，耐久 4200。
     * 紫金剑 15 / 1.80、末地合金剑 20 / 1.80，所以单下略高于末地合金剑，
     * 真正拉开差距的是出刀速度。技能还没定，先当纯数值武器。
     */
    public static final Item WS_THUNDER_KATANA = register("ws_thunder_katana",
            new org.examplee.wangsword.item.unique.ThunderKatanaItem(
                    org.examplee.wangsword.Material.ThunderToolMaterial.INSTANCE,
                    8, -1.95F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC)));

    /** 血帆弯刀：死神系列的快刀，被动吸血。技能与配方待定 */
    public static final Item WS_BLOODSAIL_CUTLASS = register("ws_bloodsail_cutlass",
            new org.examplee.wangsword.item.unique.BloodsailCutlassItem(
                    org.examplee.wangsword.Material.RedGoldToolMaterial.INSTANCE,
                    4, -1.9F,
                    new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.RARE)));

    private static Item register(String id, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(Wangsword.MOD_ID, id), item);
    }

    public static void init() {
        Wangsword.LOGGER.info("紫金工具模块加载完毕！");
    }
}
