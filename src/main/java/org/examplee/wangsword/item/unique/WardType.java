package org.examplee.wangsword.item.unique;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import org.examplee.wangsword.util.WardHandler;

import java.util.List;

/**
 * 护符的免疫族别。
 *
 * <p>每一族负责两件事：判断这次伤害算不算自家领域（{@link #matches}），以及免疫生效时的反馈
 * （{@link #onAbsorb}）。有些族还有常驻的小效果，走 {@link #tick}。
 *
 * <p>免疫本身只是一张「保险」，护符槽一共 3 格而族别有 5 个，所以真正的选择是
 * <b>带哪三种保险出门</b>，而不是无脑全带。
 */
public enum WardType {

    /** 焚身符：火焰与熔岩。着火时跑得更快，所以挨一点火也不慌。 */
    EMBER("ember") {
        @Override
        public boolean matches(DamageSource source) {
            return source.isIn(DamageTypeTags.IS_FIRE);
        }

        @Override
        public void onAbsorb(ServerPlayerEntity player, DamageSource source, float amount) {
            // 火免疫还附带灭火：不然从熔岩里出来身上还在烧，屏幕一直红着很难受
            player.extinguish();
            ServerWorld world = player.getServerWorld();
            world.spawnParticles(ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    10, 0.35D, 0.5D, 0.35D, 0.02D);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.PLAYERS, 0.7F, 1.3F);
        }

        @Override
        public void tick(ServerPlayerEntity player) {
            boolean burning = player.isOnFire() || player.isInLava();
            setSpeedBonus(player, burning, SPEED_UUID, "ws_charm_ember", 0.20D);
        }
    },

    /** 渊息符：溺水与冻伤。水下不耗氧气，粉雪里不结冰。 */
    TIDE("tide") {
        @Override
        public boolean matches(DamageSource source) {
            return source.isIn(DamageTypeTags.IS_DROWNING) || source.isIn(DamageTypeTags.IS_FREEZING);
        }

        @Override
        public void tick(ServerPlayerEntity player) {
            // 氧气一直满：溺水的判定根本走不到，气泡条也不会见底
            if (player.isSubmergedInWater()) {
                player.setAir(player.getMaxAir());
            }
            // 冻伤条清零：粉雪里不会被冻住，也不会有那层白霜挡住视野
            if (player.getFrozenTicks() > 0) {
                player.setFrozenTicks(0);
            }
        }
    },

    /** 坠星符：坠落、撞墙、仙人掌、甜浆果、石笋。高处落地会砸出一圈冲击。 */
    STARFALL("starfall") {
        @Override
        public boolean matches(DamageSource source) {
            return source.isIn(DamageTypeTags.IS_FALL)
                    || source.isOf(DamageTypes.FLY_INTO_WALL)
                    || source.isOf(DamageTypes.CACTUS)
                    || source.isOf(DamageTypes.SWEET_BERRY_BUSH)
                    || source.isOf(DamageTypes.STALAGMITE);
        }

        @Override
        public void tick(ServerPlayerEntity player) {
            // 冲击判定放在 tick 里自己记账：伤害事件触发那一刻 fallDistance 可能已被原版清零
            WardHandler.trackFall(player);
        }
    },

    /** 雷殛符：闪电。被劈中的瞬间把电弧甩给周围的敌人。 */
    THUNDER("thunder") {
        @Override
        public boolean matches(DamageSource source) {
            return source.isIn(DamageTypeTags.IS_LIGHTNING);
        }

        @Override
        public void onAbsorb(ServerPlayerEntity player, DamageSource source, float amount) {
            ServerWorld world = player.getServerWorld();
            List<LivingEntity> targets = hostiles(world, player, ARC_RADIUS);
            for (LivingEntity target : targets) {
                target.damage(world.getDamageSources().magic(), ARC_DAMAGE);
                target.setOnFireFor(ARC_FIRE_SECONDS);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        target.getX(), target.getY() + 0.9D, target.getZ(),
                        14, 0.3D, 0.4D, 0.3D, 0.25D);
            }
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    40, 0.6D, 0.9D, 0.6D, 0.4D);
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER,
                    SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
    },

    /** 爆裂符：爆炸。免伤的同时把冲击推回去。 */
    BLAST("blast") {
        @Override
        public boolean matches(DamageSource source) {
            return source.isIn(DamageTypeTags.IS_EXPLOSION);
        }

        @Override
        public void onAbsorb(ServerPlayerEntity player, DamageSource source, float amount) {
            ServerWorld world = player.getServerWorld();
            for (LivingEntity target : hostiles(world, player, BLAST_RADIUS)) {
                target.damage(world.getDamageSources().magic(), BLAST_DAMAGE);
                double dx = target.getX() - player.getX();
                double dz = target.getZ() - player.getZ();
                double length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                target.takeKnockback(0.6D, dx / length, dz / length);
            }
            world.spawnParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.8D, player.getZ(),
                    18, BLAST_RADIUS * 0.5D, 0.3D, BLAST_RADIUS * 0.5D, 0.06D);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS, 0.6F, 1.6F);
        }
    };

    /** 淬火加速用的修正器 id */
    private static final java.util.UUID SPEED_UUID =
            java.util.UUID.fromString("a1f7d2c4-8b3e-4d95-9c62-71e0b45f8a20");

    /** 坠星冲击：至少掉这么多格才触发 */
    public static final float MIN_SLAM_FALL = 4.0F;
    /** 坠星冲击的最高伤害 */
    private static final float SLAM_MAX_DAMAGE = 10.0F;
    private static final float SLAM_RADIUS = 3.0F;

    /** 引雷 */
    private static final float ARC_RADIUS = 6.0F;
    private static final float ARC_DAMAGE = 8.0F;
    private static final int ARC_FIRE_SECONDS = 3;

    /** 破爆 */
    private static final float BLAST_RADIUS = 4.0F;
    private static final float BLAST_DAMAGE = 6.0F;

    private final String id;

    WardType(String id) {
        this.id = id;
    }

    /** 用于拼语言键与贴图名 */
    public String id() {
        return id;
    }

    /** 这次伤害是否属于本族领域 */
    public abstract boolean matches(DamageSource source);

    /** 免疫生效时的反馈，默认什么都不做 */
    public void onAbsorb(ServerPlayerEntity player, DamageSource source, float amount) {
    }

    /** 佩戴时的常驻效果，默认没有 */
    public void tick(ServerPlayerEntity player) {
    }

    /** 取范围内的敌对生物（不含自己、不含已死亡） */
    private static List<LivingEntity> hostiles(ServerWorld world, ServerPlayerEntity player, float radius) {
        Box box = player.getBoundingBox().expand(radius);
        return world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity instanceof HostileEntity && entity.isAlive() && entity != player);
    }

    /**
     * 坠星冲击：由 {@code WardHandler#trackFall} 在着地那一 tick 调用。
     *
     * @param distance 这一跳攒下的下落高度
     */
    public static void slam(ServerPlayerEntity player, float distance) {
        ServerWorld world = player.getServerWorld();
        float damage = Math.min(SLAM_MAX_DAMAGE, distance);
        for (LivingEntity target : hostiles(world, player, SLAM_RADIUS)) {
            target.damage(world.getDamageSources().magic(), damage);
        }
        world.spawnParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.15D, player.getZ(),
                24, SLAM_RADIUS * 0.55D, 0.1D, SLAM_RADIUS * 0.55D, 0.08D);
        world.spawnParticles(ParticleTypes.CRIT,
                player.getX(), player.getY() + 0.3D, player.getZ(),
                18, SLAM_RADIUS * 0.5D, 0.2D, SLAM_RADIUS * 0.5D, 0.15D);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_BIG_FALL,
                SoundCategory.PLAYERS, 0.9F, 0.8F);
    }

    /** 挂上/摘掉一个移动速度百分比修正器；已经处于目标状态时不重复操作 */
    private static void setSpeedBonus(ServerPlayerEntity player, boolean active,
                                      java.util.UUID uuid, String name, double amount) {
        var instance = player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        var existing = instance.getModifier(uuid);
        if (active && existing == null) {
            instance.addTemporaryModifier(new net.minecraft.entity.attribute.EntityAttributeModifier(
                    uuid, name, amount,
                    net.minecraft.entity.attribute.EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!active && existing != null) {
            instance.removeModifier(existing);
        }
    }

    /** 摘下护符时把常驻效果收干净（目前只有焚身符有常驻加速） */
    public static void clearBonuses(ServerPlayerEntity player) {
        setSpeedBonus(player, false, SPEED_UUID, "ws_charm_ember", 0.0D);
    }
}
