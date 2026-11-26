package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.util.ThunderKatanaHandler;

import java.util.List;
import java.util.Locale;

/**
 * 雷电太刀：后期纯数值太刀 + 一套以「落雷」为核心的技能。
 *
 * <p>面板：总伤 = 1(空手) + 追加 8 + 材质 12 = 21，攻速 2.05。
 * 紫金剑 15 / 1.80、末地合金剑 20 / 1.80——太刀走「刀轻、出手快」，
 * 单下不如剑冢系列，靠连砍节奏和雷纹的攻速叠加把差距补回来。
 *
 * <ol>
 *   <li><b>被动 · 雷纹</b>：每刀叠一层（最多 5 层、6 秒掉落），每层 +4% 攻速；
 *       叠满之后<em>下一刀</em>自动落一道小雷，追加 5 点雷伤（{@link ThunderKatanaHandler}）；</li>
 *   <li><b>右键 · 万雷</b>：准心 18 格内定一片 7 格半径的雷雨，1.8 秒内落满 18 道落雷，
 *       每道对 2.5 格内造成 6 点雷伤并麻痹 1 秒，冷却 12 秒；</li>
 *   <li><b>X 键 · 雷怒</b>：15 秒内攻击力 +5、攻速 +30%，期间每一刀都引一道落雷
 *       （7 点雷伤 + 2 格内 3 点溅射），冷却 40 秒。</li>
 * </ol>
 *
 * <p>冷却分两条：右键走 {@code ItemCooldownManager}（和别的右键武器一致），
 * X 键走 Handler 自己的表——共用一条会互相锁住。
 * 技能一律不扣耐久，只有冷却管频率。
 */
public class ThunderKatanaItem extends SwordItem implements ISkillWeapon {

    /** 攻速修正；SwordItem 没留 getter，自己存一份给说明文字用 */
    private final float attackSpeed;

    public ThunderKatanaItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        this.attackSpeed = attackSpeed;
    }

    // ================== 【被动 · 雷纹】 ==================

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker instanceof ServerPlayerEntity player) {
            ThunderKatanaHandler.onHit(player, target);
        }
        return result;
    }

    // ================== 【右键 · 万雷】 ==================

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        return castVolley(player, stack) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
    }

    /**
     * 对着生物右键走的是 {@code interactMob → useOnEntity}，不会调用 {@link #use}。
     * 不补这一条，正对着怪按右键就一点反应都没有。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player && castVolley(player, stack)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private boolean castVolley(ServerPlayerEntity player, ItemStack stack) {
        // 冷却按技能算。原来用的物品冷却会把同一件武器的 X 键「雷怒」也连累进去。
        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, ThunderKatanaHandler.SKILL_VOLLEY)) {
            org.examplee.wangsword.util.SkillCooldowns.notifyCooldown(player,
                    ThunderKatanaHandler.SKILL_VOLLEY, "万雷");
            return false;
        }
        if (!ThunderKatanaHandler.castVolley(player)) {
            return false;
        }
        org.examplee.wangsword.util.SkillCooldowns.start(player,
                ThunderKatanaHandler.SKILL_VOLLEY, ThunderKatanaHandler.VOLLEY_COOLDOWN);
        player.sendMessage(Text.translatable("message.wangsword.thunder_katana.volley"), true);
        return true;
    }

    // ================== 【X 键 · 雷怒】 ==================

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof ThunderKatanaItem)) {
            return;
        }
        // 冷却在 Handler 里：这里只负责转发
        ThunderKatanaHandler.castWrath(player);
    }

    // ================== 【说明文字】 ==================

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        // 攻击力/攻速不再自己写一行：原版属性修饰符已经会显示这两项，
        // 再拼一条就是重复（而且两处数值还可能对不上）。
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.passive.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.passive.desc",
                ThunderKatanaHandler.MAX_STACKS,
                ThunderKatanaHandler.STACK_DECAY / 20,
                (int) Math.round(ThunderKatanaHandler.ATTACK_SPEED_PER_STACK * 100),
                (int) ThunderKatanaHandler.SMALL_BOLT_DAMAGE));

        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.volley.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.volley.desc",
                ThunderKatanaHandler.VOLLEY_COUNT,
                (int) ThunderKatanaHandler.VOLLEY_SPREAD,
                (int) ThunderKatanaHandler.VOLLEY_RADIUS,
                (int) ThunderKatanaHandler.VOLLEY_DAMAGE,
                seconds(ThunderKatanaHandler.VOLLEY_PARALYZE_TICKS),
                ThunderKatanaHandler.VOLLEY_COOLDOWN / 20));

        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.wrath.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.thunder_katana.wrath.desc",
                ThunderKatanaHandler.WRATH_DURATION / 20,
                (int) ThunderKatanaHandler.WRATH_ATTACK_BONUS,
                (int) Math.round(ThunderKatanaHandler.WRATH_SPEED_BONUS * 100),
                (int) ThunderKatanaHandler.WRATH_BOLT_DAMAGE,
                ThunderKatanaHandler.WRATH_COOLDOWN / 20));
    }

    /** tick 转成一行里看的秒数：30 tick 要显示 1.5 而不是 30 */
    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
