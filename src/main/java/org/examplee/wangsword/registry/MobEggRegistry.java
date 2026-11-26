package org.examplee.wangsword.registry;

import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 生物的刷怪蛋。
 *
 * <p>只需要在这里注册一次；颜色是 {@code SpawnEggItem} 的底色 / 斑点色。
 */
public class MobEggRegistry {

    /** 剑冢守卫刷怪蛋：暗紫底 + 青色斑点 */
    public static final Item SWORD_TOMB_GUARDIAN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "sword_tomb_guardian_spawn_egg"),
            new SpawnEggItem(EntityRegistry.SWORD_TOMB_GUARDIAN,
                    0x2A2438, 0x7FE3FF, new Item.Settings())
    );

    /** 裂冠幽灵刷怪蛋：幽暗蓝底 + 冷白斑点 */
    public static final Item GHOST_CROWN_WRAITH_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "ghost_crown_wraith_spawn_egg"),
            new SpawnEggItem(EntityRegistry.GHOST_CROWN_WRAITH,
                    0x2A3442, 0xC8ECFF, new Item.Settings())
    );

    /** 熔核巨兽刷怪蛋：焦黑玄武岩底 + 熔岩橙斑点 */
    public static final Item CINDER_COLOSSUS_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "cinder_colossus_spawn_egg"),
            new SpawnEggItem(EntityRegistry.CINDER_COLOSSUS,
                    0x1C1A1F, 0xFF6A1E, new Item.Settings())
    );

    /** 熔岩蝠怪刷怪蛋：焦黑岩底 + 火橙斑点 */
    public static final Item CALDERA_BAT_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(Wangsword.MOD_ID, "caldera_bat_spawn_egg"),
            new SpawnEggItem(EntityRegistry.CALDERA_BAT,
                    0x2A1410, 0xFF5A1E, new Item.Settings())
    );

    public static void init() {
        // 触发静态字段注册
    }
}
