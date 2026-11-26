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
 * 血帆弯刀命中时的血花：一小簇暗红血点，带初速和微弱重力，飞散后淡掉。
 *
 * <p>贴图 {@code textures/particle/blood_burst.png}（16×16）。
 * 速度由调用方传入，所以同一个粒子既能在命中时向外炸开，
 * 也能在血怒起手时绕着玩家撒一圈。
 */
public class BloodBurstParticle extends SpriteBillboardParticle {

    protected BloodBurstParticle(ClientWorld world, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteProvider spriteProvider) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.scale = 0.32F + this.random.nextFloat() * 0.26F;
        this.maxAge = 9 + this.random.nextInt(7);
        this.setSprite(spriteProvider);
        this.angle = this.random.nextFloat() * 360.0F;
        this.prevAngle = this.angle;
        this.collidesWithWorld = false;
        this.gravityStrength = 0.16F;
        this.velocityMultiplier = 0.88F;
    }

    @Override
    public void tick() {
        this.prevAngle = this.angle;
        super.tick();

        float life = (float) this.age / (float) this.maxAge;
        this.alpha = MathHelper.clamp(1.0F - life * life, 0.0F, 1.0F);
        this.scale *= 0.97F;
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
            return new BloodBurstParticle(world, x, y, z,
                    velocityX, velocityY, velocityZ, this.sprites);
        }
    }
}
