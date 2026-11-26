package org.examplee.wangsword.item.unique;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.entity.MoltenBladeEntity;
import org.examplee.wangsword.item.ISkillWeapon;
import org.examplee.wangsword.registry.EntityRegistry;
import org.examplee.wangsword.util.SkillCooldowns;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 熔核封岩巨剑：封岩巨剑吸收熔岩核心后的形态。
 *
 * <p>三件事都和「熔岩」的物理属性有关，不走火焰那种持续灼烧路线：
 * <ul>
 *   <li><b>被动 · 熔壳</b>：每次命中给目标叠一层，越打越慢、越打越疼，
 *       满三层时炸一发小范围熔岩爆裂并清空层数；</li>
 *   <li><b>右键 · 熔核喷发</b>：把剑插进地面，沿准心方向推进三段熔岩爆裂，
 *       最后留下四秒的熔岩池；</li>
 *   <li><b>X · 熔核崩解</b>：蓄力半秒后向前冲 12 格，撞到的敌人被拖行，
 *       终点十二格大爆炸。</li>
 * </ul>
 *
 * <p>插地那把剑是 {@link MoltenBladeEntity}，纯视觉、活 8 tick；
 * 喷发和冲锋都要跨 tick 推进，所以状态挂在这两张静态表里，
 * 由 {@link #init()} 注册的服务端 tick 驱动。
 */
public class MoltenSealedGreatswordItem extends SwordItem implements ISkillWeapon {

    // ---------------------------------------------------------------- 被动：熔壳

    private static final int SHELL_MAX_LAYERS = 3;
    /** 每层追加的真实伤害 = 攻击力 × 这个比例 */
    private static final float SHELL_TRUE_PER_LAYER = 0.08F;
    /** 满层爆裂的伤害与半径 */
    private static final float SHELL_BURST_DAMAGE = 16.0F;
    private static final double SHELL_BURST_RADIUS = 2.2D;
    /** 熔壳持续时间 */
    private static final int SHELL_DURATION = 80;

    // ---------------------------------------------------------------- 右键：熔核喷发

    public static final String SKILL_ERUPTION = "molten_sealed_eruption";
    /** 8 秒 */
    public static final int ERUPTION_COOLDOWN = 160;
    /** 三段落点：2 / 5.4 / 8.8 格 */
    private static final int ERUPTION_SEGMENTS = 3;
    private static final int ERUPTION_STEP_TICKS = 3;
    private static final double ERUPTION_START = 2.0D;
    private static final double ERUPTION_STRIDE = 3.4D;
    private static final float ERUPTION_DAMAGE_RATIO = 0.70F;
    private static final double ERUPTION_HIT_RADIUS = 2.4D;
    /** 熔岩池：4 秒，每秒一跳 */
    private static final int POOL_LIFE = 80;
    private static final int POOL_PULSE = 20;
    private static final float POOL_DAMAGE = 6.0F;
    private static final double POOL_RADIUS = 4.0D;

    // ---------------------------------------------------------------- X：熔核崩解

    public static final String SKILL_BREAK = "molten_sealed_break";
    /** 30 秒 */
    public static final int BREAK_COOLDOWN = 600;
    private static final int BREAK_WINDUP = 10;
    private static final int BREAK_DASH_TICKS = 12;
    private static final double BREAK_SPEED = 1.1D;
    private static final float BREAK_IMPACT_RATIO = 1.2F;
    private static final double BREAK_BLAST_RADIUS = 12.0D;
    private static final float BREAK_BLAST_RATIO = 1.8F;

    private static final Map<UUID, Eruption> ERUPTIONS = new HashMap<>();
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    public MoltenSealedGreatswordItem(ToolMaterial material, int attackDamage, float attackSpeed,
                                      Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    /** 注册服务端 tick 驱动：推进喷发与冲锋 */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ERUPTIONS.isEmpty() && CHARGES.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<UUID, Eruption>> eruptionIt = ERUPTIONS.entrySet().iterator();
            while (eruptionIt.hasNext()) {
                Map.Entry<UUID, Eruption> entry = eruptionIt.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || tickEruption(player, entry.getValue())) {
                    eruptionIt.remove();
                }
            }

            Iterator<Map.Entry<UUID, Charge>> chargeIt = CHARGES.entrySet().iterator();
            while (chargeIt.hasNext()) {
                Map.Entry<UUID, Charge> entry = chargeIt.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || tickCharge(player, entry.getValue())) {
                    chargeIt.remove();
                }
            }
        });
    }

    // ---------------------------------------------------------------- 被动

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld().isClient() || !(attacker instanceof ServerPlayerEntity player)) {
            return result;
        }
        addShell(target, player, 1);
        return result;
    }

    /** 给目标叠熔壳：追加真实伤害、刷新减速层数，满层炸一发 */
    private static void addShell(LivingEntity target, ServerPlayerEntity player, int layers) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        float attack = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        int current = 0;
        StatusEffectInstance slow = target.getStatusEffect(StatusEffects.SLOWNESS);
        if (slow != null) {
            current = Math.min(SHELL_MAX_LAYERS, slow.getAmplifier() + 1);
        }
        int next = Math.min(SHELL_MAX_LAYERS, current + layers);
        if (next <= current) {
            next = SHELL_MAX_LAYERS;
        }

        // 追加真伤：按本次之后的层数算，和主刀同 tick，先清无敌帧
        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().playerAttack(player),
                attack * SHELL_TRUE_PER_LAYER * next);
        world.spawnParticles(ParticleTypes.LAVA,
                target.getX(), target.getY() + target.getHeight() * 0.6D, target.getZ(),
                4, 0.3D, 0.3D, 0.3D, 0.0D);

        if (next >= SHELL_MAX_LAYERS && current < SHELL_MAX_LAYERS) {
            target.removeStatusEffect(StatusEffects.SLOWNESS);
            shellBurst(world, player, target);
        } else {
            target.removeStatusEffect(StatusEffects.SLOWNESS);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    SHELL_DURATION, next - 1), player);
        }
    }

    /** 满层爆裂：目标周围一小圈，额外的真实伤害 */
    private static void shellBurst(ServerWorld world, ServerPlayerEntity player, LivingEntity center) {
        Vec3d pos = center.getPos();
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y + center.getHeight() * 0.5D, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5D, pos.z, 24, 1.0D, 0.6D, 1.0D, 0.0D);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0F, 1.35F);

        Box area = new Box(pos, pos).expand(SHELL_BURST_RADIUS, 2.0D, SHELL_BURST_RADIUS);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target == player || target.isSpectator()) {
                continue;
            }
            if (target.squaredDistanceTo(center) > SHELL_BURST_RADIUS * SHELL_BURST_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().playerAttack(player), SHELL_BURST_DAMAGE);
        }
    }

    // ---------------------------------------------------------------- 右键

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }
        if (!SkillCooldowns.tryUse(player, SKILL_ERUPTION, "熔核喷发", ERUPTION_COOLDOWN)) {
            return TypedActionResult.fail(stack);
        }
        startEruption(player, hand);
        return TypedActionResult.success(stack);
    }

    private void startEruption(ServerPlayerEntity player, Hand hand) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Vec3d dir = flatLook(player);
        Vec3d origin = new Vec3d(player.getX(), player.getY(), player.getZ());

        // 剑尖朝下插进身前的地里，只活 8 tick
        MoltenBladeEntity blade = new MoltenBladeEntity(EntityRegistry.MOLTEN_BLADE, world);
        blade.refreshPositionAndAngles(
                origin.x + dir.x * 1.6D, player.getY(), origin.z + dir.z * 1.6D,
                player.getYaw(), 0.0F);
        world.spawnEntity(blade);

        player.swingHand(hand);
        ERUPTIONS.put(player.getUuid(), new Eruption(dir, origin));

        world.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.2F, 0.55F);
        world.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.0F, 0.6F);
    }

    /** @return true = 这一轮结束 */
    private static boolean tickEruption(ServerPlayerEntity player, Eruption eruption) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return true;
        }
        eruption.timer++;

        if (eruption.segment < ERUPTION_SEGMENTS
                && eruption.timer % ERUPTION_STEP_TICKS == 0) {
            double dist = ERUPTION_START + eruption.segment * ERUPTION_STRIDE;
            Vec3d center = eruption.origin.add(eruption.dir.multiply(dist));
            float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                    * ERUPTION_DAMAGE_RATIO;

            damageAt(world, player, center, ERUPTION_HIT_RADIUS, damage, 0.45D, 0.3D);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    center.x, center.y + 0.4D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticles(ParticleTypes.FLAME,
                    center.x, center.y + 0.5D, center.z, 30, 1.2D, 0.8D, 1.2D, 0.07D);
            world.spawnParticles(ParticleTypes.LAVA,
                    center.x, center.y + 0.2D, center.z, 18, 1.0D, 0.4D, 1.0D, 0.0D);
            world.spawnParticles(ParticleTypes.CLOUD,
                    center.x, center.y + 0.2D, center.z, 20, 1.4D, 0.15D, 1.4D, 0.05D);
            world.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2F, 0.65F);

            eruption.segment++;
            if (eruption.segment >= ERUPTION_SEGMENTS) {
                eruption.poolCenter = center;
                eruption.poolTicks = POOL_LIFE;
            }
        }

        if (eruption.poolTicks > 0) {
            eruption.poolTicks--;
            eruption.poolPulse++;
            Vec3d center = eruption.poolCenter;
            if (center != null) {
                if (eruption.poolPulse % POOL_PULSE == 0) {
                    damageAt(world, player, center, POOL_RADIUS, POOL_DAMAGE, 0.0D, 0.0D);
                }
                // 地面上的一圈火与熔岩
                for (int i = 0; i < 10; i++) {
                    double angle = Math.PI * 2.0D * i / 10 + eruption.timer * 0.15D;
                    double px = center.x + Math.cos(angle) * POOL_RADIUS * 0.75D;
                    double pz = center.z + Math.sin(angle) * POOL_RADIUS * 0.75D;
                    world.spawnParticles(ParticleTypes.FLAME,
                            px, center.y + 0.15D, pz, 1, 0.06D, 0.04D, 0.06D, 0.01D);
                    if (i % 3 == 0) {
                        world.spawnParticles(ParticleTypes.LAVA,
                                px, center.y + 0.1D, pz, 1, 0.05D, 0.02D, 0.05D, 0.0D);
                    }
                }
            }
        }

        return eruption.segment >= ERUPTION_SEGMENTS && eruption.poolTicks <= 0;
    }

    // ---------------------------------------------------------------- X

    @Override
    public void triggerUltimate(ServerPlayerEntity player, boolean charged) {
        if (!SkillCooldowns.tryUse(player, SKILL_BREAK, "熔核崩解", BREAK_COOLDOWN)) {
            return;
        }
        Vec3d dir = flatLook(player);
        CHARGES.put(player.getUuid(), new Charge(dir));
        // 蓄力期间压住移动输入，冲刺靠服务端推速度
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                BREAK_WINDUP + 6, 6), player);

        if (player.getWorld() instanceof ServerWorld world) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.2F, 0.55F);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 18, 0.9D, 0.6D, 0.9D, 0.05D);
        }
    }

    /** @return true = 这一轮结束 */
    private static boolean tickCharge(ServerPlayerEntity player, Charge charge) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return true;
        }

        if (charge.windup > 0) {
            charge.windup--;
            world.spawnParticles(ParticleTypes.FLAME,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 3, 0.7D, 0.9D, 0.7D, 0.02D);
            if (charge.windup == 0) {
                player.setVelocity(charge.dir.x * BREAK_SPEED, 0.05D, charge.dir.z * BREAK_SPEED);
                player.velocityModified = true;
            }
            return false;
        }

        charge.dash++;
        player.setVelocity(charge.dir.x * BREAK_SPEED, player.getVelocity().y,
                charge.dir.z * BREAK_SPEED);
        player.velocityModified = true;
        player.fallDistance = 0.0F;

        // 沿途撞人
        float impact = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * BREAK_IMPACT_RATIO;
        for (Entity entity : world.getOtherEntities(player, player.getBoundingBox().expand(2.0D))) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target == player || target.isSpectator()) {
                continue;
            }
            if (!charge.hit.add(entity.getUuid())) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (target.damage(world.getDamageSources().playerAttack(player), impact)) {
                addShell(target, player, 2);
                Vec3d push = charge.dir.multiply(0.9D);
                target.addVelocity(push.x, 0.4D, push.z);
                target.velocityModified = true;
            }
        }

        world.spawnParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.8D, 0.8D, 0.8D, 0.04D);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.4D, player.getZ(), 4, 0.7D, 0.3D, 0.7D, 0.03D);

        if (charge.dash >= BREAK_DASH_TICKS || (charge.dash > 2 && player.horizontalCollision)) {
            breakBurst(player, world, charge.dir);
            return true;
        }
        return false;
    }

    /** 冲锋终点的大爆炸 */
    private static void breakBurst(ServerPlayerEntity player, ServerWorld world, Vec3d dir) {
        float attack = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float damage = attack * BREAK_BLAST_RATIO;
        Vec3d center = new Vec3d(player.getX(), player.getY(), player.getZ());

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                center.x, center.y + 1.0D, center.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                center.x, center.y + 1.0D, center.z, 12, 2.6D, 1.2D, 2.6D, 0.0D);
        world.spawnParticles(ParticleTypes.FLAME,
                center.x, center.y + 1.0D, center.z, 90, 3.4D, 1.6D, 3.4D, 0.1D);
        world.spawnParticles(ParticleTypes.LAVA,
                center.x, center.y + 0.3D, center.z, 48, 3.0D, 0.4D, 3.0D, 0.0D);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0F, 0.42F);
        world.playSound(null, center.x, center.y, center.z,
                SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 1.2F, 0.5F);

        for (Entity entity : world.getOtherEntities(player,
                new Box(center, center).expand(BREAK_BLAST_RADIUS, 4.0D, BREAK_BLAST_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target == player || target.isSpectator()) {
                continue;
            }
            if (target.squaredDistanceTo(player) > BREAK_BLAST_RADIUS * BREAK_BLAST_RADIUS) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (!target.damage(world.getDamageSources().playerAttack(player), damage)) {
                continue;
            }
            Vec3d push = target.getPos().subtract(center);
            if (push.lengthSquared() > 1.0E-4D) {
                push = push.normalize().multiply(1.4D);
                target.addVelocity(push.x, 0.55D, push.z);
                target.velocityModified = true;
            }
            target.setOnFireFor(8);
            // 爆炸命中直接挂满熔壳
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    SHELL_DURATION, SHELL_MAX_LAYERS - 1), player);
        }
        // 玩家自己被后坐力推一下
        player.addVelocity(-dir.x * 0.35D, 0.15D, -dir.z * 0.35D);
        player.velocityModified = true;
    }

    // ---------------------------------------------------------------- 工具

    /** 准心的水平方向，太竖直时退回 +Z */
    private static Vec3d flatLook(PlayerEntity player) {
        Vec3d look = player.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0.0D, look.z);
        return flat.lengthSquared() < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 1.0D) : flat.normalize();
    }

    /** 对圆内所有活物结算一次伤害，push > 0 时向外推 */
    private static void damageAt(ServerWorld world, ServerPlayerEntity player, Vec3d center,
                                 double radius, float damage, double push, double lift) {
        Box area = new Box(center, center).expand(radius, 2.5D, radius);
        for (Entity entity : world.getOtherEntities(player, area)) {
            if (!(entity instanceof LivingEntity target) || !target.isAlive()
                    || target == player || target.isSpectator()) {
                continue;
            }
            if (target.squaredDistanceTo(center) > radius * radius) {
                continue;
            }
            target.timeUntilRegen = 0;
            if (!target.damage(world.getDamageSources().playerAttack(player), damage)) {
                continue;
            }
            if (push > 0.0D) {
                Vec3d away = target.getPos().subtract(center);
                if (away.lengthSquared() > 1.0E-4D) {
                    away = away.normalize().multiply(push);
                    target.addVelocity(away.x, lift, away.z);
                    target.velocityModified = true;
                }
            }
            target.setOnFireFor(5);
        }
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.wangsword.sealed_rock.bane"));
        tooltip.add(Text.translatable("tooltip.wangsword.molten_sealed.shell",
                (int) (SHELL_TRUE_PER_LAYER * 100), (int) SHELL_BURST_DAMAGE));
        tooltip.add(Text.translatable("tooltip.wangsword.molten_sealed.eruption.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.molten_sealed.eruption.desc",
                (int) (ERUPTION_DAMAGE_RATIO * 100)));
        tooltip.add(Text.translatable("tooltip.wangsword.molten_sealed.break.title"));
        tooltip.add(Text.translatable("tooltip.wangsword.molten_sealed.break.desc",
                BREAK_DASH_TICKS, (int) (BREAK_BLAST_RATIO * 100)));
    }

    // ---------------------------------------------------------------- 状态

    private static final class Eruption {
        final Vec3d dir;
        final Vec3d origin;
        int timer;
        int segment;
        int poolTicks;
        int poolPulse;
        Vec3d poolCenter;

        Eruption(Vec3d dir, Vec3d origin) {
            this.dir = dir;
            this.origin = origin;
        }
    }

    private static final class Charge {
        final Vec3d dir;
        int windup = BREAK_WINDUP;
        int dash;
        final Set<UUID> hit = new HashSet<>();

        Charge(Vec3d dir) {
            this.dir = dir;
        }
    }
}
