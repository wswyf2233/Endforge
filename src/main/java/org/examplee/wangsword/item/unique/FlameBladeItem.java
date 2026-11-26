package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.util.CombatTargets;
import org.examplee.wangsword.util.FlameBladeHandler;
import org.examplee.wangsword.util.SkillCooldowns;

import java.util.List;

/**
 * 烈火大刀：中期单手大刀，攻防两端都靠火。
 *
 * <ol>
 *   <li><b>被动 · 焚身领域</b>：只要握在手上，周身 {@link #AURA_RADIUS} 格亮起一圈火焰边界，
 *       范围内的敌对生物每 0.5 秒吃一发 {@link #AURA_DAMAGE} 点魔法伤害并被重新点燃；</li>
 *   <li><b>右键 · 通天火柱</b>：准心落点竖起一道 30 格高的火柱（{@link FlameBladeHandler}）；</li>
 *   <li><b>X 键 · 烈火喷射</b>：朝视线方向持续喷火 3 秒，射程 12 格。</li>
 * </ol>
 *
 * <p>被动只认 {@link CombatTargets#isHostile}：村民、动物、傀儡站在圈里不会被点着，
 * 判定和剑阵的索敌是同一套。
 */
public class FlameBladeItem extends SwordItem implements ISkillWeapon {

    /** 被动范围（格） */
    public static final double AURA_RADIUS = 5.0D;
    /** 被动重新点燃 / 灼烧的间隔：0.5 秒 */
    private static final int AURA_IGNITE_INTERVAL = 10;
    /** 被动一次点燃的秒数 */
    private static final int AURA_FIRE_SECONDS = 3;
    /** 被动每次灼烧的直接伤害（魔法伤害，无视护甲） */
    public static final float AURA_DAMAGE = 2.0F;

    /** 近战命中自带的点燃秒数（原版火焰附加 I 是 4 秒、II 是 8 秒，取中间偏上） */
    public static final int ATTACK_FIRE_SECONDS = 5;

    /** 攻速修正；SwordItem 没留 getter，自己存一份给说明文字用 */
    private final float attackSpeed;

    public FlameBladeItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        this.attackSpeed = attackSpeed;
    }

    // ================== 【近战 · 自带火焰附加】 ==================

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // super 会先结算附魔和耐久；这里再补一把火。
        // 不直接覆盖火苗，只有原来的火更短时才续上——不然给武器附了火焰附加反而会被压短
        boolean result = super.postHit(stack, target, attacker);
        if (!target.getWorld().isClient() && target.isAlive()
                && target.getFireTicks() < ATTACK_FIRE_SECONDS * 20) {
            target.setOnFireFor(ATTACK_FIRE_SECONDS);
        }
        return result;
    }

    // ================== 【右键 · 通天火柱】 ==================

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        return castPillar(player, stack)
                ? TypedActionResult.success(stack)
                : TypedActionResult.fail(stack);
    }

    /**
     * 对着生物右键不会走 {@link #use}（原版走 {@code interactMob → useOnEntity}），
     * 补上这条分支，否则「正对着怪按右键」这个最自然的用法完全没反应。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player && castPillar(player, stack)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** 烈焰十字的真正入口，两个右键分支共用 */
    private boolean castPillar(ServerPlayerEntity player, ItemStack stack) {
        // 冷却按技能记，不用物品冷却——物品级冷却会把 X 键的喷射一起锁住
        if (!SkillCooldowns.isReady(player, FlameBladeHandler.SKILL_PILLAR)) {
            SkillCooldowns.notifyCooldown(player, FlameBladeHandler.SKILL_PILLAR, "烈焰十字");
            return false;
        }
        FlameBladeHandler.castCrossSlash(player);
        SkillCooldowns.start(player, FlameBladeHandler.SKILL_PILLAR, FlameBladeHandler.PILLAR_COOLDOWN);
        return true;
    }

    // ================== 【X 键 · 烈火喷射】 ==================

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof FlameBladeItem)) {
            return;
        }
        // 只受冷却限制，不扣耐久
        FlameBladeHandler.castJet(player);
    }

    // ================== 【被动 · 焚身领域】 ==================

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);

        if (world.isClient() || !(entity instanceof ServerPlayerEntity player)) {
            return;
        }
        // 只认主手这把：快捷栏里挑中但实际拿在副手时不该生效
        boolean active = selected && player.getMainHandStack().getItem() == this;
        // 边界改用贴图火环（常驻实体、跟着脚走），不再每 4 tick 撒一圈粒子
        FlameBladeHandler.tickAuraRing(player, active, AURA_RADIUS);
        if (!active) {
            return;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        long time = world.getTime();

        // 灼烧：圈内的敌对生物每 0.5 秒吃一发魔法伤害，并续一次火，跑出去就自然灭
        if (time % AURA_IGNITE_INTERVAL == 0L) {
            double rangeSq = AURA_RADIUS * AURA_RADIUS;
            for (LivingEntity target : serverWorld.getEntitiesByClass(
                    LivingEntity.class, player.getBoundingBox().expand(AURA_RADIUS),
                    CombatTargets::isHostile)) {
                if (!target.isAlive() || target.squaredDistanceTo(player) > rangeSq) {
                    continue;
                }
                // 清一下受击无敌帧，否则和玩家的普攻抢冷却、有时会吞掉这一跳
                target.timeUntilRegen = 0;
                target.damage(serverWorld.getDamageSources().magic(), AURA_DAMAGE);
                target.setOnFireFor(AURA_FIRE_SECONDS);
            }
        }
    }

    // ================== 【说明文字】 ==================

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        // 攻击力/攻速由原版属性提示显示，这里不再重复
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.onhit", ATTACK_FIRE_SECONDS));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.aura.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.aura.desc",
                (int) AURA_RADIUS, (int) AURA_DAMAGE));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.pillar.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.pillar.desc",
                (int) FlameBladeHandler.CROSS_DAMAGE));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.jet.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.flame_blade.jet.desc",
                FlameBladeHandler.JET_DURATION / 20, (int) FlameBladeHandler.JET_DAMAGE));
    }
}
