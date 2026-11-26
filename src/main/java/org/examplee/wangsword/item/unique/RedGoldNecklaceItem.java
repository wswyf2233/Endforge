package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.examplee.wangsword.util.NecklaceAttributes;

import java.util.List;

/**
 * 赤金项链（Accessories 的 necklace 槽）。
 *
 * <p>无功能的基础项链，佩戴时提供 +10 最大生命。这条加成所有项链共用，
 * 见 {@link NecklaceAttributes}。
 */
public class RedGoldNecklaceItem extends Item implements Accessory {

    public RedGoldNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
    }
}
