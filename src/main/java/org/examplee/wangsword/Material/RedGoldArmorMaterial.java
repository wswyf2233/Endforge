package org.examplee.wangsword.Material;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.examplee.wangsword.registry.ItemsRegistry;

/**
 * 赤金盔甲的材质。
 *
 * <p>数值比下界合金高一点：
 * <pre>
 *            赤金   下界合金
 * 护甲值     4/7/9/4   3/6/8/3
 * 韧性       4.0       3.0
 * 击退抗性   0.15      0.10
 * 附魔能力   20        15
 * 耐久系数   45        37
 * </pre>
 *
 * <p>getName() 必须是不带命名空间的纯名字，原版会拼成
 * {@code minecraft:textures/models/armor/<name>_layer_1.png}，
 * 所以层级贴图放在 {@code assets/minecraft/textures/models/armor/} 下。
 */
public class RedGoldArmorMaterial implements ArmorMaterial {

    public static final RedGoldArmorMaterial INSTANCE = new RedGoldArmorMaterial();

    /** 贴图名：ws_redgoldarmor_layer_1.png / ws_redgoldarmor_layer_2.png */
    public static final String NAME = "ws_redgoldarmor";

    @Override
    public int getDurability(ArmorItem.Type type) {
        return 60;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 6;
            case CHESTPLATE -> 14;
            case LEGGINGS -> 12;
            case BOOTS -> 8;
        };
    }

    @Override
    public int getEnchantability() {
        return 20;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
    }

    /** 修复材料：赤金锭 */
    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(ItemsRegistry.WS_REDGOLD_INGOT);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public float getToughness() {
        return 4.0F;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.15F;
    }
}
