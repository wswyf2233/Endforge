package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class DeepSeaStormEntity extends Entity {
    private LivingEntity caster;
    private int lifeTick;
    private final int maxLife = 100; // 存活5秒 (100 tick)

    public DeepSeaStormEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public DeepSeaStormEntity(EntityType<? extends DeepSeaStormEntity> type, World world, LivingEntity caster) {
        super(type, world);
        this.caster = caster;
    }

    @Override
    public void tick() {
        super.tick();
        lifeTick++;

        // 服务端逻辑：聚怪 + 伤害
        if (!getWorld().isClient()) {
            ServerWorld sw = (ServerWorld) getWorld();

            // 每次tick，把周围8格内的怪物往中心吸
            Box pullBox = this.getBoundingBox().expand(8.0);
            for (Entity entity : sw.getOtherEntities(this, pullBox)) {
                if (entity instanceof LivingEntity target && target != caster) {
                    // 计算吸力向量（从怪物指向漩涡中心）
                    double dx = this.getX() - target.getX();
                    double dz = this.getZ() - target.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 0.5) { // 太近就不吸了，防止重叠卡死
                        // 越远吸力越小，稍微加一点向上的力
                        target.addVelocity((dx / distance) * 0.1, 0.05, (dz / distance) * 0.1);
                        target.velocityModified = true;
                    }
                    // 每秒造成伤害 (每20tick)
                    if (lifeTick % 20 == 0) {
                        target.damage(getDamageSources().magic(), 20.0F);
                    }
                }
            }

            // 随机生成点水花粒子
            if (lifeTick % 2 == 0) {
                sw.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 5, 2.0, 0.5, 2.0, 0.1);
            }
        } else {
            // 客户端逻辑：这里你可以用之前的 VertexConsumer 手搓一个旋转的蓝色水环
            // 为了测试，我们先用原版粒子在客户端转圈
            for (int i = 0; i < 3; i++) {
                double angle = (lifeTick * 0.2) + (i * Math.PI * 2 / 3);
                double radius = 2.0;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                getWorld().addParticle(net.minecraft.particle.ParticleTypes.END_ROD, x, this.getY() + 1, z, 0, 0.2, 0);
            }
        }

        if (lifeTick >= maxLife) {
            this.discard();
        }
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {}
}