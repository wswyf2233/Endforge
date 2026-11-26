package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/** 死花短刀（ws_poisondagger）：诅咒武器，只会伤到自己。 */
public class DaggerRegistry {

    public static final int DAGGER_ADD_ATTACK = 1;
    public static final float DAGGER_ATTACK_SPEED = -1.0F;
    public static final int DAGGER_DURABILITY = 1500;

    public static final Item WS_POISONDAGGER = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ws_poisondagger"),
            new org.examplee.wangsword.item.unique.PoisonDaggerItem(
                    ItemsRegistry.CUSTOM_NETHERITE_MATERIAL,
                    DAGGER_ADD_ATTACK,
                    DAGGER_ATTACK_SPEED,
                    new Item.Settings().maxCount(1).maxDamage(DAGGER_DURABILITY))
    );

    public static void init() {
        Wangsword.LOGGER.info("死花短刀模块加载完毕！");
    }
}