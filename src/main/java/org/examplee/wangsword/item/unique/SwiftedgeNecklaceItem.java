package org.examplee.wangsword.item.unique;

import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.examplee.wangsword.util.EquippedTickable;
import org.examplee.wangsword.util.NecklaceAttributes;
import org.examplee.wangsword.util.SwiftedgeHandler;

import java.util.List;

/**
 * 迅锋项链（Accessories 的 necklace 槽）。
 *
 * <p>佩戴即 +12% 攻击速度；冲刺时再叠 +18%，合计 +30%。
 * 冲刺那一档在 {@link SwiftedgeHandler} 里按瞬时状态实时挂/摘。
 */
public class SwiftedgeNecklaceItem extends Item implements Accessory, EquippedTickable {

    /** 常驻攻速加成（MULTIPLY_TOTAL） */
    private static final double BASE_ATTACK_SPEED_BONUS = 0.12D;

    public SwiftedgeNecklaceItem(Settings settings) {
        super(settings);
    }

    @Override
    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        NecklaceAttributes.applyMaxHealth(builder);
        builder.addStackable(EntityAttributes.GENERIC_ATTACK_SPEED,
                new Identifier("wangsword", "swiftedge_necklace"), BASE_ATTACK_SPEED_BONUS,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void tickEquipped(ServerPlayerEntity player) {
        SwiftedgeHandler.tick(player);
    }

    @Override
    public void onUnequip(ItemStack stack, SlotReference reference) {
        if (reference.entity() instanceof ServerPlayerEntity player) {
            SwiftedgeHandler.clear(player);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.necklace.base").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.swiftedge.desc").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.swiftedge.desc2").formatted(Formatting.GRAY));
    }
}
