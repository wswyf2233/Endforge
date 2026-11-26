package org.examplee.wangsword.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.EntityRegistry;

/**
 * 熔核巨兽 attack3 召唤的火球。
 *
 * <p>沿用原版小火球的飞行与碰撞，命中后自己结算：3.4 格范围火焰伤害、
 * 轻微击退、点燃 6 秒。伤害按施法者攻击力折算，所以 BOSS 打出来的
 * 比玩家法杖的烈焰弹重得多。不破坏地形。
 */
public class CinderFireballEntity extends SmallFireballEntity {

    /** 没撞到任何东西时的寿命上限：约 35 格 */
    private static final int MAX_LIFE = 70;
    private static final double BLAST_RADIUS = 3.4D;
    private static final float DAMAGE_RATIO = 0.55F;
    private static final int FIRE_SECONDS = 6;

    public CinderFireballEntity(EntityType<? extends CinderFireballEntity> type, World world) {
        super(type, world);
    }

    public static CinderFireballEntity create(ServerWorld world, LivingEntity owner,
                                              Vec3d origin, Vec3d dir, double speed) {
        CinderFireballEntity ball = new CinderFireballEntity(EntityRegistry.CINDER_FIREBALL, world);
        ball.setOwner(owner);
        ball.setPosition(origin.x, origin.y, origin.z);
        ball.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed);
        return ball;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) {
            Vec3d velocity = this.getVelocity();
            if (velocity.lengthSquared() > 1.0E-6D) {
                Vec3d back = velocity.normalize().multiply(-0.45D);
                for (int i = 0; i < 3; i++) {
                    this.getWorld().addParticle(ParticleTypes.FLAME,
                            this.getX() + back.x + (this.random.nextDouble() - 0.5D) * 0.45D,
                            this.getY() + back.y + (this.random.nextDouble() - 0.5D) * 0.45D,
                            this.getZ() + back.z + (this.random.nextDouble() - 0.5D) * 0.45D,
                            0.0D, 0.0D, 0.0D);
                }
                if (this.age % 3 == 0) {
                    this.getWorld().addParticle(ParticleTypes.LAVA,
                            this.getX() + back.x, this.getY() + back.y, this.getZ() + back.z,
                            0.0D, 0.0D, 0.0D);
                }
            }
        } else if (this.age > MAX_LIFE) {
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        // 客户端也跑本地碰撞，跟着 discard 会让火球擦到方块就消失，伤害却在服务端结算
        if (this.getWorld().isClient()) {
            return;
        }
        if (this.getWorld() instanceof ServerWorld world) {
            this.explode(world, hitResult.getPos());
        }
        this.discard();
    }

    private void explode(ServerWorld world, Vec3d pos) {
        LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
        float damage = owner != null
                ? (float) owner.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * DAMAGE_RATIO
                : 10.0F;

        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 4, 0.6D, 0.5D, 0.6D, 0.0D);
        world.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 40, 1.4D, 1.0D, 1.4D, 0.08D);
        world.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 18, 1.2D, 0.5D, 1.2D, 0.0D);
        world.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0F, 1.3F);

        for (Entity entity : world.getOtherEntities(this, new Box(pos, pos).expand(BLAST_RADIUS))) {
            if (!(entity instanceof LivingEntity target) || target == owner || !target.isAlive()) {
                continue;
            }
            if (target instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            // 同一个主人打出来的火球不炸同类：蝠怪成群，互相炸会自己先死一半
            if (owner != null && target.getClass() == owner.getClass()) {
                continue;
            }
            Vec3d push = target.getPos().subtract(pos);
            if (push.lengthSquared() > 1.0E-4D) {
                push = push.normalize().multiply(0.55D);
                target.addVelocity(push.x, 0.25D, push.z);
                target.velocityModified = true;
            }
            target.damage(owner != null
                    ? this.getDamageSources().mobAttack(owner)
                    : this.getDamageSources().magic(), damage);
            target.setOnFireFor(FIRE_SECONDS);
        }
    }
}
