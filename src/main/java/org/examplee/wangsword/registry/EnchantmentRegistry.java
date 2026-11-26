package org.examplee.wangsword.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.enchantment.LaunchEnchantment;

/** 自定义附魔注册。 */
public class EnchantmentRegistry {

    /** 击飞 */
    public static final LaunchEnchantment LAUNCH = Registry.register(
            Registries.ENCHANTMENT,
            new Identifier(Wangsword.MOD_ID, "launch"),
            new LaunchEnchantment()
    );

    public static void init() {
        Wangsword.LOGGER.info("附魔模块加载完毕！");
    }
}