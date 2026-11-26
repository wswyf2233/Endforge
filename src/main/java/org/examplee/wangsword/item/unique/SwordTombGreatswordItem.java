package org.examplee.wangsword.item.unique;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.FlyingSwordEntity;
import org.examplee.wangsword.entity.SentinelSwordEntity;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.registry.SoundRegistry;
import org.examplee.wangsword.util.CombatTargets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 剑冢巨剑：守卫掉落的整把大剑，四阶（巨剑 / Plus / Pro / Pro Max）。
 *
 * <p>三个技能互为资源，一套循环：
 * <ol>
 *   <li><b>右键 · 剑冢环伺</b>：召 3~6 把剑环绕 12 秒，每 2 秒各自斩击附近敌人。</li>
 *   <li><b>被动 · 葬魂</b>：击杀叠层，每层让每次命中追加魔法伤害，脱战慢慢掉。</li>
 *   <li><b>X 键 · 万剑归宗</b>：把环绕的剑全部倾泻出去，消耗全部葬魂层数增伤；
 *       身上没剑时改从头顶降下剑雨。</li>
 * </ol>
 *
 * <p>四个阶级共用同一张贴图和模型，靠名字颜色、数值、剑阵数量区分。
 */
public class SwordTombGreatswordItem extends SwordItem implements ISkillWeapon {

    /** 最高阶下标（0 = 巨剑，3 = Pro Max） */
    public static final int MAX_TIER = 3;

    // ===================== 【技能数值】 =====================
    /** 右键环伺冷却：20 秒 */
    public static final int SENTINEL_COOLDOWN = 400;
    /** 剑阵持续时间：12 秒 */
    private static final int SENTINEL_DURATION = 240;
    /** 每把剑射出的光刃伤害 = 基准伤害 × 这个系数 */
    private static final float SENTINEL_BLADE_RATIO = 0.35F;
    /** X 键冷却：45 秒 */
    public static final int ULT_COOLDOWN = 900;
    /** 大招：环伺之剑倾泻出去的伤害倍率 */
    private static final float ULT_BLADE_RATIO = 1.2F;
    /** 大招：无剑时降下的剑雨数量与伤害倍率 */
    private static final int ULT_RAIN_COUNT = 6;
    private static final float ULT_RAIN_RATIO = 0.8F;
    /** 葬魂每层追加的魔法伤害 */
    public static final float SOUL_DAMAGE_PER_STACK = 0.5F;
    /** 大招：每层葬魂的额外增伤 */
    public static final float SOUL_ULT_BONUS = 0.08F;
    /** 脱战多久掉一层葬魂：40 秒 */
    private static final int SOUL_DECAY_TICKS = 800;
    // ======================================================

    public static final String NBT_SOUL = "TombSoul";
    public static final String NBT_SOUL_TICK = "TombSoulTick";

    /** 技能 ID：环伺 / 倾泻。两个技能以前一个只有灰遮罩、一个完全没有反馈 */
    public static final String SKILL_SENTINEL = "greatsword_sentinel";
    public static final String SKILL_ULT = "greatsword_ult";

    private final int tier;

    public SwordTombGreatswordItem(ToolMaterial material, int attackDamage, float attackSpeed,
                                   int tier, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /** 这一阶的基准单次伤害（含材料基础）：16 / 18 / 20 / 22 */
    public float baseDamage() {
        return 16.0F + tier * 2.0F;
    }

    /** 环伺剑数量：3 / 4 / 5 / 6 */
    public int sentinelCount() {
        return 3 + tier;
    }

    /** 葬魂层数上限：6 / 8 / 10 / 12 */
    public int soulCap() {
        return 6 + tier * 2;
    }

    private String tierName() {
        return switch (tier) {
            case 1 -> "Plus";
            case 2 -> "Pro";
            case 3 -> "Pro Max";
            default -> "";
        };
    }

    /** 攻速显示值：1.10 / 1.15 / 1.20 / 1.25 */
    private String attackSpeedText() {
        return switch (tier) {
            case 1 -> "1.15";
            case 2 -> "1.20";
            case 3 -> "1.25";
            default -> "1.10";
        };
    }

    /** 耐久：跟 SwordTombToolMaterial 的四档对齐 */
    private int durability() {
        return switch (tier) {
            case 1 -> 4200;
            case 2 -> 5400;
            case 3 -> 7000;
            default -> 3200;
        };
    }

    // ================== 【右键 · 剑冢环伺】 ==================

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack, true);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        ServerWorld serverWorld = (ServerWorld) world;

        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_SENTINEL)) {
            int left = org.examplee.wangsword.util.SkillCooldowns.remainingSeconds(player, SKILL_SENTINEL);
            player.sendMessage(Text.translatable("message.wangsword.greatsword.sentinel_cd", left), true);
            return TypedActionResult.fail(stack);
        }

        // 已经有一圈剑在转：不叠加，直接告诉他别浪费冷却
        if (!serverWorld.getEntitiesByClass(SentinelSwordEntity.class,
                player.getBoundingBox().expand(10.0D), e -> e.isOwnedBy(player)).isEmpty()) {
            player.sendMessage(Text.translatable("message.wangsword.greatsword.sentinel_active"), true);
            return TypedActionResult.fail(stack);
        }

        // 冷却按技能记；不再写物品冷却，这样才有右侧图标，也不会连带锁别的技能
        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_SENTINEL, SENTINEL_COOLDOWN);

        int count = sentinelCount();
        float bladeDamage = baseDamage() * SENTINEL_BLADE_RATIO;
        for (int i = 0; i < count; i++) {
            SentinelSwordEntity blade = new SentinelSwordEntity(EntityRegistry.SENTINEL_SWORD,
                    serverWorld, player, i, count, tier, SENTINEL_DURATION, bladeDamage);
            serverWorld.spawnEntity(blade);
        }

        // 起手表现：一圈魂火从脚下荡开，配一声归鞘
        Vec3d center = player.getPos().add(0.0D, 1.1D, 0.0D);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64.0D;
            serverWorld.spawnParticles(ParticleTypes.SOUL,
                    center.x + Math.cos(a) * 2.3D, center.y, center.z + Math.sin(a) * 2.3D,
                    1, 0.0D, 0.05D, 0.0D, 0.0D);
        }
        serverWorld.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                center.x, center.y, center.z, 4, 1.6D, 0.2D, 1.6D, 0.0D);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN,
                SoundCategory.PLAYERS, 1.1F, 0.65F);
        serverWorld.playSound(null, player.getBlockPos(), SoundRegistry.SLASH_IMPACT,
                SoundCategory.PLAYERS, 1.2F, 0.6F);
        player.sendMessage(Text.translatable("message.wangsword.greatsword.sentinel_start", count), true);

        return TypedActionResult.success(stack);
    }

    // ================== 【X 键 · 万剑归宗】 ==================

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        if (!(player.getMainHandStack().getItem() instanceof SwordTombGreatswordItem held)
                || held != this) {
            return;
        }

        ServerWorld world = (ServerWorld) player.getWorld();

        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, SKILL_ULT)) {
            int left = org.examplee.wangsword.util.SkillCooldowns.remainingSeconds(player, SKILL_ULT);
            player.sendMessage(Text.translatable("message.wangsword.greatsword.ult_cd", left), true);
            return;
        }

        org.examplee.wangsword.util.SkillCooldowns.start(player, SKILL_ULT, ULT_COOLDOWN);
        // 注意：这里**不**占用原版的物品冷却条。物品冷却按 Item 记，只有一条，
        // 大招写进去会把右键技能的 20 秒倒计时覆盖成 45 秒，玩家会以为右键坏了。

        // 葬魂层数：这次全打出去，每层再乘一点增伤
        NbtCompound nbt = player.getMainHandStack().getOrCreateNbt();
        int soul = Math.min(nbt.getInt(NBT_SOUL), soulCap());
        nbt.putInt(NBT_SOUL, 0);
        float multiplier = 1.0F + soul * SOUL_ULT_BONUS;

        List<SentinelSwordEntity> blades = world.getEntitiesByClass(SentinelSwordEntity.class,
                player.getBoundingBox().expand(10.0D), e -> e.isOwnedBy(player));

        if (!blades.isEmpty()) {
            for (SentinelSwordEntity blade : blades) {
                Vec3d from = blade.getPos();
                Vec3d aim = aimFrom(world, player, from);
                spawnBlade(world, player, from, aim, baseDamage() * ULT_BLADE_RATIO * multiplier);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        from.x, from.y, from.z, 10, 0.12D, 0.12D, 0.12D, 0.03D);
                blade.discard();
            }
        } else {
            // 身上没剑：从头顶砸下剑雨，保证大招永远不是空的
            Vec3d look = player.getRotationVector();
            for (int i = 0; i < ULT_RAIN_COUNT; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2.0D;
                double radius = 2.0D + world.random.nextDouble() * 4.0D;
                double x = player.getX() + look.x * 4.0D + Math.cos(angle) * radius;
                double z = player.getZ() + look.z * 4.0D + Math.sin(angle) * radius;
                double y = player.getY() + 7.0D + world.random.nextDouble() * 3.0D;

                Vec3d from = new Vec3d(x, y, z);
                spawnBlade(world, player, from, new Vec3d(0.0D, -1.0D, 0.0D),
                        baseDamage() * ULT_RAIN_RATIO * multiplier);
                world.spawnParticles(ParticleTypes.SOUL, x, y, z, 8, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }

        // 收尾：一圈冲击波 + 两声剑鸣，让"打完"这件事有重量
        Vec3d center = player.getPos().add(0.0D, 1.0D, 0.0D);
        for (int ring = 0; ring < 2; ring++) {
            double r = 1.2D + ring * 0.9D;
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40.0D;
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        center.x + Math.cos(a) * r, center.y + ring * 0.4D, center.z + Math.sin(a) * r,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                SoundCategory.PLAYERS, 0.8F, 0.55F);
        world.playSound(null, player.getBlockPos(), SoundRegistry.SLASH_IMPACT,
                SoundCategory.PLAYERS, 1.4F, 0.7F);
        player.sendMessage(blades.isEmpty()
                ? Text.translatable("message.wangsword.greatsword.ult_rain", soul)
                : Text.translatable("message.wangsword.greatsword.ult_blades", soul, blades.size()), true);
    }

    /** 从某点朝最近的敌人瞄；没敌人就朝玩家视线 */
    private static Vec3d aimFrom(ServerWorld world, PlayerEntity owner, Vec3d from) {
        LivingEntity target = world.getEntitiesByClass(LivingEntity.class,
                        new net.minecraft.util.math.Box(from, from).expand(16.0D),
                        e -> CombatTargets.isAutoAttackTarget(e, owner))
                .stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(from), b.squaredDistanceTo(from)))
                .orElse(null);
        if (target == null) {
            return owner.getRotationVector().normalize();
        }
        return target.getEyePos().subtract(from).normalize();
    }

    private static void spawnBlade(ServerWorld world, PlayerEntity owner, Vec3d from,
                                   Vec3d direction, float damage) {
        // 自动放出去的剑只认敌对生物：没瞄准目标时光刃沿视线乱飞，不能顺手把村民串了
        FlyingSwordEntity blade = new FlyingSwordEntity(EntityRegistry.FLYING_SWORD, world, owner,
                ItemStack.EMPTY, damage, direction.normalize().multiply(1.1D), true)
                .setHostileOnly(true);
        blade.setPosition(from);
        world.spawnEntity(blade);
    }

    // ================== 【被动 · 葬魂】 ==================

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient() || !(attacker instanceof ServerPlayerEntity player)) {
            return result;
        }

        ServerWorld world = (ServerWorld) attacker.getWorld();
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putLong(NBT_SOUL_TICK, world.getTime());

        int soul = Math.min(nbt.getInt(NBT_SOUL), soulCap());

        // 层数换算成追加伤害：走魔法伤害源，不吃物理无敌帧，也不会把目标再击退一次
        if (soul > 0 && target.isAlive()) {
            target.damage(world.getDamageSources().magic(), soul * SOUL_DAMAGE_PER_STACK);
        }

        if (!target.isAlive() && soul < soulCap()) {
            nbt.putInt(NBT_SOUL, soul + 1);
            world.spawnParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                    16, 0.3D, 0.3D, 0.3D, 0.05D);
            world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_SOUL_SAND_STEP,
                    SoundCategory.PLAYERS, 0.8F, 0.6F);
        }

        return result;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient() || !(entity instanceof ServerPlayerEntity)) {
            return;
        }
        // 每 20 tick 检查一次就够了，不必每 tick 摸 NBT
        if (world.getTime() % 20L != 0L) {
            return;
        }
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) {
            return;
        }
        int soul = nbt.getInt(NBT_SOUL);
        if (soul <= 0) {
            return;
        }
        long last = nbt.getLong(NBT_SOUL_TICK);
        if (world.getTime() - last >= SOUL_DECAY_TICKS) {
            nbt.putInt(NBT_SOUL, soul - 1);
            nbt.putLong(NBT_SOUL_TICK, world.getTime());
        }
    }

    // ================== 【说明文字】 ==================

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        int soul = nbt == null ? 0 : Math.min(nbt.getInt(NBT_SOUL), soulCap());

        Text tierLabel = tier == 0
                ? Text.translatable("tooltip.wangsword.greatsword.tier.base")
                : Text.literal(tierName());

        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.header", tierLabel));
        // 攻击力/攻速由原版属性提示显示，这里不再重复
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.sentinel.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.sentinel.desc", sentinelCount()));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.ult.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.ult.desc"));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.desc", SOUL_DAMAGE_PER_STACK));
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.ult",
                (int) (SOUL_ULT_BONUS * 100)));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.greatsword.soul.count", soul, soulCap()));
        if (tier < MAX_TIER) {
            tooltip.add(Text.translatable("tooltip.wangsword.greatsword.upgrade",
                    this.getName(),
                    org.examplee.wangsword.recipe.GreatswordUpgradeRecipe.upgradedItem(tier).getName()));
        } else {
            tooltip.add(Text.translatable("tooltip.wangsword.greatsword.oath"));
        }
        super.appendTooltip(stack, world, tooltip, context);
    }
}
