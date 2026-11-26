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
import org.examplee.wangsword.util.EquippedTickable;
import org.examplee.wangsword.util.HuntsoulHandler;
import org.examplee.wangsword.util.NecklaceAttributes;

import java.util.List;

/**
 * 猎魂项链（Accessories 的 necklace 槽）。
 *
 * <p>贴着同一个目标打叠印记，印记换攻击力，打死时炸开。逻辑全在 {@link HuntsoulHandler}。
 */
public class HuntsoulNecklaceItem extends Item implements Accessory, EquippedTickable {

    public HuntsoulNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        HuntsoulHandler.tick(player);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        if (reference.entity() instanceof ServerPlayerEntity player) {
            HuntsoulHandler.clear(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.huntsoul.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.huntsoul.desc2").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.huntsoul.desc3").formatted(Formatting.DARK_GRAY));
    }
}
