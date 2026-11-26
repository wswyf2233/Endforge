package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.effect.FrozenEffect;
import org.examplee.wangsword.registry.EffectRegistry;
import org.examplee.wangsword.util.FrostBladeHandler;
import org.examplee.wangsword.util.SkillCooldowns;

import java.util.List;
import java.util.Locale;

/**
 * 冰霜之刃：前期单手剑，面板只比钻石剑高一点，强度全在冰封这一套上。
 *
 * <ol>
 *   <li><b>被动 · 霜噬</b>：每次命中给目标挂 {@link #FREEZE_SECONDS} 秒冰封
 *       （{@link FrozenEffect}），被冻住的生物移动速度归零、也打不飞。攻速 1.6 意味着
 *       连续挥砍时冰封会被不断刷新，贴身的怪基本动不了；</li>
 *   <li><b>被动 · 碎冰</b>：砍在<em>已经冻住</em>的目标上，额外造成 {@link #SHATTER_DAMAGE}
 *       点伤害并炸出一圈碎屑。和霜噬凑成节奏——第一刀冻上，第二刀开始每一刀都在砸冰壳；</li>
 *   <li><b>右键 · 霜环</b>：以自身为圆心 7 格推出一圈冰环，范围内生物吃 2 点魔法伤害
 *       并被冰封 1.5 秒（{@link FrostBladeHandler}）；</li>
 *   <li><b>X 键 · 冰棱穿刺</b>：朝准心射出一根会飞的冰棱，穿透路径上所有生物，
 *       每个吃 5 点伤害并被冰封 2 秒。</li>
 * </ol>
 *
 * <p>三个技能的分工：被动管贴脸单体，霜环管被围，冰棱管远程——都不动方块，
 * 所以在村庄、树林里放也不会拆家。索敌走 {@code CombatTargets.isSkillTarget}：
 * 玩家按出来的技能打所有活物（自己除外），不限于敌对生物。
 *
 * <p>Boss 级（剑冢守卫、监守者、劫掠兽、血量 ≥100 的自定义 Boss）只吃降级版的霜缚：
 * 移动速度 −55%、AI 与攻击完全保留，不会变成站着挨打的木桩。
 * 末影龙与凋灵完全不吃——它们的移动由阶段/飞行逻辑写死，减速也压不动，
 * 挂上去只会显示一个假图标，所以 {@code canFreeze} 直接排除。
 */
public class FrostBladeItem extends SwordItem implements ISkillWeapon {

    /** 命中附带的冰封秒数 */
    public static final int FREEZE_SECONDS = 2;

    /** 碎冰：砍在已冰封的目标上的追加伤害 */
    public static final float SHATTER_DAMAGE = 2.0F;

    /** 攻速修正；SwordItem 没留 getter，自己存一份给说明文字用 */
    private final float attackSpeed;

    public FrostBladeItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        this.attackSpeed = attackSpeed;
    }

    // ================== 【被动 · 霜噬】 ==================

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);

        if (target.getWorld().isClient() || !target.isAlive()) {
            return result;
        }

        // 碎冰必须在「重新上冰」之前判：这一刀落下去时目标是不是已经冻着。
        // 判断放到 freeze 后面的话，每次都被自己刚刷的那层冰骗成「已冻住」，
        // 等于刀刀都吃加成，那就不是碎冰而是常驻伤害了。
        // 只认冰封不认霜缚——Boss 不吃定身，也就不吃碎冰，前期武器不该对 Boss 加码。
        if (target.hasStatusEffect(EffectRegistry.FROZEN)) {
            shatter(target, attacker);
        }

        // 末影龙/凋灵的免疫、Boss 级降级为霜缚，判断都在 freeze 内部
        FrozenEffect.freeze(target, FREEZE_SECONDS);
        return result;
    }

    /**
     * 砸开冰壳：补一次伤害 + 碎屑 + 玻璃裂响。
     *
     * <p>主伤害刚刚结算完，无敌帧（{@code timeUntilRegen}）还压着，不先清零这 2 点会被直接吞掉。
     *
     * <p>伤害源优先用 {@code playerAttack}：这样击杀正常算在玩家头上，稀有掉落和死亡信息都对；
     * 非玩家持有（比如被别的 mod 塞给怪）时退回魔法伤害。
     */
    private static void shatter(LivingEntity target, LivingEntity attacker) {
        DamageSource source = attacker instanceof PlayerEntity player
                ? player.getDamageSources().playerAttack(player)
                : attacker.getDamageSources().magic();

        target.timeUntilRegen = 0;
        target.damage(source, SHATTER_DAMAGE);

        if (target.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.ITEM_SNOWBALL,
                    target.getX(), target.getBodyY(0.5D), target.getZ(),
                    14, 0.32D, 0.36D, 0.32D, 0.09D);
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    target.getX(), target.getBodyY(0.6D), target.getZ(),
                    9, 0.28D, 0.30D, 0.28D, 0.02D);
            world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_HIT,
                    SoundCategory.PLAYERS, 0.85F, 1.75F);
        }
    }

    // ================== 【右键 · 霜环】 ==================

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.success(stack);
        }
        return castNova(player, stack)
                ? TypedActionResult.success(stack)
                : TypedActionResult.fail(stack);
    }

    /**
     * 对着生物右键：原版走的是 {@code PlayerEntity.interact → interactMob → useOnEntity}，
     * <b>根本不会调用 {@link #use}</b>。
     *
     * <p>不补这一条，霜环就只在「对着空气或普通方块右键」时生效，正对着僵尸按右键毫无反应——
     * 玩家最自然的用法（对着怪按）恰好是唯一失效的那个。
     */
    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user instanceof ServerPlayerEntity player && castNova(player, stack)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /**
     * 霜环的真正入口，{@link #use} 与 {@link #useOnEntity} 共用。
     *
     * @return 是否放出去了（冷却中返回 false）
     */
    private boolean castNova(ServerPlayerEntity player, ItemStack stack) {
        // 冷却按技能记，不用物品冷却——物品级冷却会把 X 键的冰棱一起锁住
        if (!SkillCooldowns.isReady(player, FrostBladeHandler.SKILL_NOVA)) {
            SkillCooldowns.notifyCooldown(player, FrostBladeHandler.SKILL_NOVA, "霜环");
            return false;
        }
        FrostBladeHandler.castNova(player);
        SkillCooldowns.start(player, FrostBladeHandler.SKILL_NOVA, FrostBladeHandler.NOVA_COOLDOWN);
        return true;
    }

    // ================== 【X 键 · 冰棱穿刺】 ==================

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof FrostBladeItem)) {
            return;
        }

        // 冷却不走 ItemCooldownManager：那和右键共用一条，会互相锁住。
        // 冷却表由 FrostBladeHandler 自己维护；技能不扣耐久。
        FrostBladeHandler.castSpike(player);
    }

    // ================== 【说明文字】 ==================

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        // 攻击力/攻速由原版属性提示显示，这里不再重复
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.passive.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.passive.desc", FREEZE_SECONDS));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.shatter.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.shatter.desc", (int) SHATTER_DAMAGE));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.nova.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.nova.desc",
                (int) FrostBladeHandler.NOVA_RADIUS,
                (int) FrostBladeHandler.NOVA_DAMAGE,
                seconds(FrostBladeHandler.NOVA_FREEZE_TICKS),
                FrostBladeHandler.NOVA_COOLDOWN / 20));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.spike.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.frost_blade.spike.desc",
                (int) Math.round(FrostBladeHandler.SPIKE_RANGE),
                (int) FrostBladeHandler.SPIKE_DAMAGE,
                seconds(FrostBladeHandler.SPIKE_FREEZE_TICKS),
                FrostBladeHandler.SPIKE_COOLDOWN / 20));
    }

    /** tick 转成一行里看的秒数：30 tick 要显示 1.5 而不是 30 */
    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }
}
