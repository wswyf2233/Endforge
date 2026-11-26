package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.examplee.wangsword.util.EchoNecklaceHandler;
import org.examplee.wangsword.util.NecklaceAttributes;

import java.util.List;

/**
 * 残响项链（Accessories 的 necklace 槽）。
 *
 * <p>把一次大伤害拆成 4 秒 8 段，给你留出回血与拉开距离的窗口。逻辑全在
 * {@link EchoNecklaceHandler}，物品这边只负责摘下时结清。
 */
public class EchoNecklaceItem extends Item implements Accessory {

    public EchoNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        if (reference.entity() instanceof ServerPlayerEntity player) {
            EchoNecklaceHandler.onUnequip(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.echo_necklace.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.echo_necklace.desc2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.echo_necklace.desc3").formatted(Formatting.DARK_GRAY));
    }
}
