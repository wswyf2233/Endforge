package org.examplee.wangsword.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.examplee.wangsword.registry.EntityRegistry;

/**
 * 裂冠幽灵的远程光弹：直线飞行，命中实体或方块后原地炸开一团幽蓝冲击。
 *
 * <p>继承 {@link PersistentProjectileEntity} 是为了直接用它那套"按速度分步做碰撞检测"，
 * 箭的伤害结算反而要绕开（{@link #onEntityHit} / {@link #onBlockHit} 都不调 super），
 * 只由 {@link #explode} 统一结算范围伤害。
 */
public class GhostOrbEntity extends PersistentProjectileEntity {

    /** 爆炸威力：TNT 是 4.0，光弹略小一点。NONE 类型不破坏方块，但伤害和击退照旧 */
    public static final float POWER = 2.4F;
    /** 飞行速度（格/tick） */
    public static final float SPEED = 0.8F;
    /** 没命中任何东西时多久消失 */
    private static final int MAX_LIFE = 80;

    public GhostOrbEntity(EntityType<? extends GhostOrbEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        // 不是箭，不能被捡起来
        this.pickupType = PickupPermission.DISALLOWED;
    }

    public static GhostOrbEntity create(World world, LivingEntity owner, Vec3d dir) {
        GhostOrbEntity orb = new GhostOrbEntity(EntityRegistry.GHOST_ORB, world);
        orb.setOwner(owner);
        Vec3d from = new Vec3d(owner.getX(), owner.getY() + owner.getHeight() * 0.65D, owner.getZ());
        orb.setPosition(from.x + dir.x * 0.7D, from.y + dir.y * 0.7D, from.z + dir.z * 0.7D);
        orb.setVelocity(dir.normalize().multiply(SPEED));
        return orb;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient()) {
            // 打空也得自己消失，不然会一直飞到区块加载边界
            if (this.age > MAX_LIFE) {
                this.discard();
            }
            return;
        }
        // 客户端拖尾：幽蓝的灵魂火
        this.getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                this.getX() + (this.random.nextDouble() - 0.5D) * 0.18D,
                this.getY() + (this.random.nextDouble() - 0.5D) * 0.18D,
                this.getZ() + (this.random.nextDouble() - 0.5D) * 0.18D,
                0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void onEntityHit(EntityHitResult result) {
        this.explode(result.getPos());
    }

    @Override
    protected void onBlockHit(BlockHitResult result) {
        this.explode(result.getPos());
    }

    private void explode(Vec3d pos) {
        if (this.getWorld().isClient()) {
            // 客户端不结算，等服务端的销毁包
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();

        // 真正的爆炸：冲击波、按距离衰减的伤害、击退、爆炸粒子与音效全部走原版这一套。
        // NONE = 不破坏方块、不点燃周围，所以不会有 TNT 那种炸坑。
        world.createExplosion(this, pos.x, pos.y, pos.z, POWER, World.ExplosionSourceType.NONE);

        // 幽灵自己的特色：中心再散一圈灵魂火
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z,
                28, 0.9D, 0.6D, 0.9D, 0.06D);

        this.discard();
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY;
    }
}
