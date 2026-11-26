package org.examplee.wangsword.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * 剑冢守卫的「巨剑音爆」粒子：一张蓝白冲击环，先张开再淡出。
 *
 * <p>贴图见 {@code textures/particle/sword_wave.png}。用
 * {@link ParticleTextureSheet#PARTICLE_SHEET_TRANSLUCENT} 是因为环本身有半透明渐变，
 * 走不透明层会把光晕切出一圈硬边。
 *
 * <p>生命周期里只做两件事：{@code scale} 从 1.6 张到约 3.5 格（缓出），
 * {@code alpha} 在后 3/4 的时间里线性淡掉。旋转角度随机，所以沿一条线甩出去时
 * 每一环的缺口都朝不同方向，不会像是复制粘贴的。
 */
public class SwordWaveParticle extends SpriteBillboardParticle {

    /** 起始尺寸（格） */
    private static final float START_SCALE = 1.6F;
    /** 张开倍率：起始 × 这个值就是最终直径 */
    private static final float GROW_FACTOR = 2.2F;
    /** 前这么大比例的生命里保持满不透明 */
    private static final float HOLD = 0.25F;

    private final float startScale;
    private final float endScale;

    protected SwordWaveParticle(ClientWorld world, double x, double y, double z,
                                double velocityX, double velocityY, double velocityZ,
                                SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.startScale = START_SCALE * (0.85F + this.random.nextFloat() * 0.3F);
        this.endScale = this.startScale * (GROW_FACTOR - 0.3F + this.random.nextFloat() * 0.6F);
        this.scale = this.startScale;
        this.maxAge = 9 + this.random.nextInt(6);
        this.setSprite(spriteProvider);
        // 随机转角：每一环的缺口方向都不一样
        this.angle = this.random.nextFloat() * 360.0F;
        this.prevAngle = this.angle;
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;
        this.velocityMultiplier = 0.92F;
    }

    @Override
    public void tick() {
        this.prevAngle = this.angle;
        super.tick();

        float life = (float) this.age / (float) this.maxAge;
        // 缓出：一出手就撑开，后面越张越慢
        float grow = 1.0F - (1.0F - life) * (1.0F - life);
        this.scale = MathHelper.lerp(grow, this.startScale, this.endScale);
        this.alpha = life < HOLD
                ? 1.0F
                : MathHelper.clamp(1.0F - (life - HOLD) / (1.0F - HOLD), 0.0F, 1.0F);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 客户端工厂：{@code sprites} 由 Fabric 按 sword_wave.json 里的贴图列表提供 */
    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new SwordWaveParticle(world, x, y, z,
                    velocityX, velocityY, velocityZ, this.sprites);
        }
    }
}
