package org.examplee.wangsword.item.unique;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.ArcSlashEntity;
import org.examplee.wangsword.registry.SoundRegistry;
import net.minecraft.text.Text;
import net.minecraft.client.item.TooltipContext;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.item.ISkillWeapon; // 🌟 必须导入这个接口

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 🌟 实现接口
public class WangShadowSwordItem extends SwordItem implements ISkillWeapon {

    private final Set<UUID> hitEntities = new HashSet<>();
    private int dashTick = 0;
    private final int DASH_DURATION = 8;

    public WangShadowSwordItem(ToolMaterial toolMaterial, int attackDamage, float attackSpeed, Settings settings) {
        super(toolMaterial, attackDamage, attackSpeed, settings);
    }

    // ================== 【左键剑气】 ==================
    public static final Identifier SLASH_FIRE_PACKET = new Identifier("wangsword", "slash_fire");
    private static final Map<UUID, Long> LAST_FIRE_TICK = new HashMap<>();

    /**
     * 左键剑气间隔：跟武器攻速同步（攻速 2.6 -> 约 8 tick 一发）。
     *
     * <p>注意：客户端发射剑气时**不要**调用 {@code swingHand}，
     * 否则 Better Combat 会把挥手包当成一次攻击，把攻击冷却清零，近战伤害直接掉到 20%。
     */
    public static int getFireIntervalTicks(PlayerEntity player) {
        double attackSpeed = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED);
        if (attackSpeed < 0.1) attackSpeed = 0.1;
        return Math.max(1, (int) Math.round(20.0 / attackSpeed));
    }

    public static void fireSlash(World world, PlayerEntity user, boolean breakBlocks) {
        if (world.isClient()) return;
        Vec3d forward = user.getRotationVector().normalize();
        Vec3d up = forward.crossProduct(forward.crossProduct(new Vec3d(0.0, 1.0, 0.0)));
        if (up.lengthSquared() < 1.0E-6) {
            up = new Vec3d(1.0, 0.0, 0.0);
        } else {
            up = up.normalize();
        }
        ArcSlashEntity slash = new ArcSlashEntity(EntityRegistry.ARC_SLASH, user, world, 100.0F, forward, up);
        slash.setBreakBlocks(breakBlocks);
        // 出生点前移，避免判定框覆盖脚下
        slash.setPosition(user.getEyePos().add(forward.multiply(3.0)));
        world.spawnEntity(slash);
    }

    public static void fireParticleSlash(World world, PlayerEntity user) {
        if (world.isClient()) return;
        Vec3d forward = user.getRotationVector().normalize();
        if (forward.lengthSquared() < 1.0E-6) {
            forward = new Vec3d(0.0, 0.0, 1.0);
        }
        ArcSlashEntity slash = new ArcSlashEntity(EntityRegistry.ARC_SLASH, user, world, 40.0F,
                forward, new Vec3d(0.0, 1.0, 0.0));
        slash.setBreakBlocks(false);
        slash.setParticleMode(true);
        slash.setMoveSpeed(1.6);
        slash.setMaxLife(30);
        slash.setPosition(user.getEyePos().add(forward.multiply(3.0)));
        world.spawnEntity(slash);

        // 左键剑气音效：绑在点击本身，不等实体
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.SWORD_WS,
                user.getSoundCategory(), 1.0F, 1.0F);
    }

    public static void handleFireRequest(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) return;
        if (!(player.getMainHandStack().getItem() instanceof WangShadowSwordItem)) return;

        World world = player.getWorld();
        long now = world.getTime();
        int interval = getFireIntervalTicks(player);
        Long last = LAST_FIRE_TICK.get(player.getUuid());
        if (last != null && now - last < interval) return;

        LAST_FIRE_TICK.put(player.getUuid(), now);
        fireParticleSlash(world, player);
    }

    // ================== 【X 键大招】 ==================
    public static final Identifier ULT_PACKET = new Identifier("wangsword", "ultimate");

    /** 客户端蓄力状态（只用于贴图切换） */
    public static boolean CHARGING = false;
    public static final int CHARGE_TICKS = 30;
    public static final int X_COOLDOWN = 10;
    public static final int CHARGED_COOLDOWN = 40;
    /** 技能 ID：X 的两档。都低于图标门槛，只统一到技能池、不画格子 */
    public static final String SKILL_X = "shadow_x";
    public static final String SKILL_CHARGED = "shadow_charged";

    public static void fireXSlash(World world, PlayerEntity user) {
        if (world.isClient()) return;
        Vec3d forward = user.getRotationVector().normalize();
        Vec3d up = new Vec3d(0.0, 1.0, 0.0);

        for (float roll : new float[]{35.0F, -35.0F}) {
            ArcSlashEntity slash = new ArcSlashEntity(EntityRegistry.ARC_SLASH, user, world, 90.0F, forward, up);
            slash.setBreakBlocks(false);
            slash.setParticleMode(true);
            slash.setRollDegrees(roll);
            slash.setScaleMult(1.5F);
            slash.setMoveSpeed(1.4);
            slash.setMaxLife(36);
            slash.setPosition(user.getEyePos().add(forward.multiply(3.0)));
            world.spawnEntity(slash);
        }

        // X 剑气音效
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.SWORD_WS,
                user.getSoundCategory(), 1.1F, 0.9F);
    }

    public static void fireChargedSlash(World world, PlayerEntity user) {
        if (world.isClient()) return;
        Vec3d forward = user.getRotationVector().normalize();
        ArcSlashEntity slash = new ArcSlashEntity(EntityRegistry.ARC_SLASH, user, world, 300.0F,
                forward, new Vec3d(0.0, 1.0, 0.0));
        slash.setBreakBlocks(true);
        slash.setParticleMode(true);
        slash.setScaleMult(5F);
        slash.setMoveSpeed(5);
        slash.setMaxLife(100);
        slash.setPosition(user.getEyePos().add(forward.multiply(3.0)));
        world.spawnEntity(slash);

        // 蓄力斩音效：更低更响
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                org.examplee.wangsword.registry.SoundRegistry.SWORD_WS,
                user.getSoundCategory(), 1.4F, 0.75F);
    }

    // 🌟 改成了接口的方法，去掉了 static 和 多余的 instanceof 判断
    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (player.isDead() || player.isSpectator()) return;
        // 自我校验：确保手里还拿着这把剑（防止换手后误触发）
        if (!(player.getMainHandStack().getItem() instanceof WangShadowSwordItem)) return;

        World world = player.getWorld();
        String skill = charged ? SKILL_CHARGED : SKILL_X;
        int cooldown = charged ? CHARGED_COOLDOWN : X_COOLDOWN;

        if (!org.examplee.wangsword.util.SkillCooldowns.isReady(player, skill)) {
            return;
        }
        org.examplee.wangsword.util.SkillCooldowns.start(player, skill, cooldown);

        if (charged) {
            fireChargedSlash(world, player);
        } else {
            fireXSlash(world, player);
        }
    }

    // =================================================

    @Override
    public void appendTooltip(ItemStack itemStack, World world, List<Text> tooltip, TooltipContext tooltipContext) {
        super.appendTooltip(itemStack, world, tooltip, tooltipContext);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.impact.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.impact.desc"));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.moon.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.moon.desc"));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.flurry.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.flurry.desc"));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.ult.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.ult.tap"));
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.ult.hold"));
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.wang_sword.passive"));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            if (!world.isClient()) {
                fireSlash(world, user, true);
            }
            user.getItemCooldownManager().set(this, 0);
            return TypedActionResult.success(stack, world.isClient());
        } else {
            runWangDashSkill(world, user, stack);
            return TypedActionResult.success(stack, world.isClient());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof PlayerEntity player) || !selected) return;
        if (!world.isClient()) {
            if (dashTick <= 0) return;

            ServerWorld sw = (ServerWorld) world;
            sw.spawnParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.2, 0.2, 0.2, 0.05
            );

            dashTick--;
            double damage = 50.0F;
            double hitWidth = 1.0;
            Box hitBox = player.getBoundingBox().expand(hitWidth);
            for (Entity target : world.getOtherEntities(player, hitBox)) {
                if (target instanceof LivingEntity livingTarget && !hitEntities.contains(target.getUuid())) {
                    livingTarget.damage(player.getDamageSources().playerAttack(player), (float) damage);
                    hitEntities.add(target.getUuid());
                }
            }
        }
    }

    private static void spawnDashCastEffect(ServerWorld sw, PlayerEntity user, Vec3d look) {
        Vec3d right = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vec3d(1.0, 0.0, 0.0);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(look).normalize();
        Vec3d center = user.getEyePos().add(look.multiply(1.2));

        int layers = 3;
        int points = 18;
        double arcDegrees = 100.0;

        for (int slash = 0; slash < 2; slash++) {
            Vec3d curUp = up;
            if (slash == 1) {
                curUp = right.multiply(0.7071).add(up.multiply(0.7071)).normalize();
            }
            for (int layer = 0; layer < layers; layer++) {
                double radius = 1.6 + layer * 0.18;
                for (int i = 0; i <= points; i++) {
                    double t = i / (double) points;
                    double ang = Math.toRadians(t * arcDegrees - arcDegrees / 2.0);
                    Vec3d dir = look.multiply(Math.cos(ang)).add(curUp.multiply(Math.sin(ang))).normalize();
                    Vec3d pos = center.add(dir.multiply(radius));

                    sw.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                            pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        sw.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private void runWangDashSkill(World world, PlayerEntity user, ItemStack stack) {
        if (!world.isClient()) {
            // 冲锋音效：龙吼 + 自定义斩击（原爆炸层已换成 slash.ogg）
            world.playSound(null, user.getBlockPos(),
                    net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL,
                    user.getSoundCategory(), 0.9F, 0.5F);
            world.playSound(null, user.getBlockPos(),
                    org.examplee.wangsword.registry.SoundRegistry.SLASH_IMPACT,
                    user.getSoundCategory(), 1.3F, 1.0F);

            Vec3d lookVec = user.getRotationVector();
            user.setVelocity(lookVec.multiply(2.5));
            user.setVelocity(user.getVelocity().x, 0, user.getVelocity().z);
            user.velocityModified = true;

            dashTick = DASH_DURATION;
            hitEntities.clear();
            user.getItemCooldownManager().set(this, 0);

            ServerWorld sw = (ServerWorld) world;
            spawnDashCastEffect(sw, user, lookVec.normalize());

            sw.spawnParticles(ParticleTypes.SOUL,
                    user.getX(), user.getY() + 0.5, user.getZ(),
                    15, 0.4, 0.4, 0.4, 0.15
            );
        }
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        World world = attacker.getWorld();
        if (!attacker.getWorld().isClient()) {
            attacker.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, attacker.getSoundCategory(), 0.3f, 1.0f);
            ServerWorld sw = (ServerWorld) world;
            double x = target.getX();
            double y = target.getY() + target.getHeight() / 2;
            double z = target.getZ();
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 36, 0.2, 0.2, 0.2, 0.12);
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 24, 0.4, 0.3, 0.4, 0.08);
            sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 18, 0.6, 0.4, 0.6, 0.05);
        }
        return super.postHit(stack, target, attacker);
    }
}
