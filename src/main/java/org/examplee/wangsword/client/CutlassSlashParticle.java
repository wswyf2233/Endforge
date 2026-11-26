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
 * 血帆弯刀突进的刀光：一张横向血色弧线，贴着玩家身后铺开，很快淡掉。
 *
 * <p>贴图是 128×128 的正方形画布（原图 128×64 上下补了透明边），
 * 所以直接用 {@link SpriteBillboardParticle} 的正方形 quad 不会把弧线拉变形。
 *
 * <p>生命周期里只做小幅放大 + 淡出，不做位移：刀光的"移动"由突进本身完成，
 * 粒子留在原地更像残影。
 */
public class CutlassSlashParticle extends SpriteBillboardParticle {

    /** 起始直径（格） */
    private static final float START_SCALE = 2.4F;
    /** 结束时直径 */
    private static final float END_SCALE = 3.1F;
    /** 前这么大比例的生命里保持满不透明 */
    private static final float HOLD = 0.15F;

    private final float endScale;

    protected CutlassSlashParticle(ClientWorld world, double x, double y, double z,
                                   double velocityX, double velocityY, double velocityZ,
                                   SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.scale = START_SCALE * (0.9F + this.random.nextFloat() * 0.2F);
        this.endScale = this.scale * (END_SCALE / START_SCALE);
        this.maxAge = 8 + this.random.nextInt(4);
        this.setSprite(spriteProvider);
        this.angle = (this.random.nextFloat() - 0.5F) * 24.0F;
        this.prevAngle = this.angle;
        this.collidesWithWorld = false;
        this.gravityStrength = 0.0F;
        this.velocityMultiplier = 0.80F;
    }

    @Override
    public void tick() {
        this.prevAngle = this.angle;
        super.tick();

        float life = (float) this.age / (float) this.maxAge;
        this.scale = MathHelper.lerp(life, START_SCALE, this.endScale);
        this.alpha = life < HOLD
                ? 1.0F
                : MathHelper.clamp(1.0F - (life - HOLD) / (1.0F - HOLD), 0.0F, 1.0F);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            return new CutlassSlashParticle(world, x, y, z,
                    velocityX, velocityY, velocityZ, this.sprites);
        }
    }
}
