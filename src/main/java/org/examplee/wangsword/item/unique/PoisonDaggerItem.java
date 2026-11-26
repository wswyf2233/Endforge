package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 死花短刀：只会伤到自己的诅咒武器。
 *
 * <p>所有对外的攻击伤害都会被转移到自己身上（由 {@code Wangsword} 里的
 * ALLOW_DAMAGE 事件统一处理），命中时还会给自己挂上中毒。
 */
public class PoisonDaggerItem extends SwordItem {

    /** 命中时给自己中毒的时长（tick） */
    public static final int SELF_POISON_TICKS = 80;
    /** 中毒等级（0 = I） */
    public static final int SELF_POISON_AMPLIFIER = 1;

    public PoisonDaggerItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient()) {
            return result;
        }

        // 反噬：给自己上中毒
        attacker.addStatusEffect(new StatusEffectInstance(
                StatusEffects.POISON, SELF_POISON_TICKS, SELF_POISON_AMPLIFIER));

        if (attacker.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.ITEM_SLIME,
                    attacker.getX(), attacker.getY() + attacker.getHeight() * 0.6D, attacker.getZ(),
                    6, 0.25D, 0.3D, 0.25D, 0.02D);
            serverWorld.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.5F, 0.8F);
        }
        return result;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip,
                              net.minecraft.client.item.TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.title").formatted(Formatting.DARK_GREEN));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.stats").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.curse").formatted(Formatting.RED));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.poison").formatted(Formatting.GREEN));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.reward_title").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.reward_death").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.reward_damage").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.wangsword.poisondagger.reward_totem").formatted(Formatting.GRAY));
    }
}