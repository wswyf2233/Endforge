package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Material.TridentMaterial;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.item.unique.MyTridentItem;

public class TridentRegistry {

    // 注册这把专属的三叉戟
    public static final Item MY_TRIDENT = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_trident"),
            new MyTridentItem(TridentMaterial.INSTANCE, 8, -2.0F,
                    new Item.Settings().maxCount(1).maxDamage(250))
    );

    // 写个静态方法，用于在主类触发类加载
    public static void init() {
        Wangsword.LOGGER.info("三叉戟模块加载完毕！");
    }
}