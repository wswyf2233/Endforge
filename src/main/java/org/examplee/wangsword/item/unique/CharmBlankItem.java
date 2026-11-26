package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

/**
 * 符坯：护符的空白底子，只有壳、没有铭刻。
 *
 * <p>它是可佩戴的——和赤金项链、赤金戒指一样，即使还没铭刻任何祝祷，也自带一份基础生命力。
 * 铭成真正的护符后这份属性会继承过去，所以戴着符坯等材料不会白占一个槽位。
 *
 * <p>它<b>不是</b>护符：没有免疫，也不吃 {@code WardHandler} 那套伤害拦截。
 */
public class CharmBlankItem extends Item implements Accessory {

    /** 基础最大生命加成，与 {@link WardCharmItem} 保持一致 */
    private static final double MAX_HEALTH_BONUS = 10.0D;

    public CharmBlankItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        builder.addStackable(EntityAttributes.GENERIC_MAX_HEALTH,
                new Identifier("wangsword", "charm_blank"), MAX_HEALTH_BONUS,
                EntityAttributeModifier.Operation.ADDITION);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.charm_blank.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.charm_blank.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.charm_blank.hint").formatted(Formatting.DARK_GRAY));
    }
}
