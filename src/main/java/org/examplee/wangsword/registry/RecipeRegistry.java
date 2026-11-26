package org.examplee.wangsword.registry;

import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;
import org.examplee.wangsword.recipe.GreatswordUpgradeRecipe;
import org.examplee.wangsword.recipe.OathForgeRecipe;

/**
 * 自定义配方类型注册。
 *
 * <p>必须在数据包加载之前完成——RecipeManager 一读到 {@code "type": "wangsword:greatsword_upgrade"}
 * 就要找这个序列化器，找不到会整份数据包解析失败。
 */
public class RecipeRegistry {

    public static final RecipeSerializer<GreatswordUpgradeRecipe> GREATSWORD_UPGRADE =
            Registry.register(
                    Registries.RECIPE_SERIALIZER,
                    new Identifier(Wangsword.MOD_ID, "greatsword_upgrade"),
                    GreatswordUpgradeRecipe.Serializer.INSTANCE
            );

    /** 四核心 + Pro Max → 守誓，产物保留附魔/耐久/名字 */
    public static final RecipeSerializer<OathForgeRecipe> OATH_FORGE =
            Registry.register(
                    Registries.RECIPE_SERIALIZER,
                    new Identifier(Wangsword.MOD_ID, "oath_forge"),
                    OathForgeRecipe.Serializer.INSTANCE
            );

    public static void register() {
        // 静态字段已经完成注册，这里只是为了有个明确的调用点
    }
}
