package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.BowMaterial;
import org.examplee.wangsword.item.unique.FatalisCrossbowItem;
import org.examplee.wangsword.item.unique.WangBowItem;

public class BowRegistery {

    // 流星弓
    public static final Item WS_BOW = new WangBowItem(
            new Item.Settings().maxDamage(BowMaterial.INSTANCE.getDurability()));

    // 黑龙弩：不装填，左键连发
    public static final Item FATALIS_CROSSBOW = new FatalisCrossbowItem(
            new Item.Settings().maxCount(1).maxDamage(800));

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier("wangsword", "ws_bow"), WS_BOW);
        Registry.register(Registries.ITEM, new Identifier("wangsword", "fatalis_crossbow"), FATALIS_CROSSBOW);
    }
}