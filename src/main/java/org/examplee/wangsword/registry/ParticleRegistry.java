package org.examplee.wangsword.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 模组自定义粒子。
 *
 * <p>目前只有一颗：{@code wangsword:sword_wave}——剑冢守卫终极大招甩出巨剑时的
 * 空气冲击环。贴图是照着守卫自己的蓝白配色画的（深蓝 → 青 → 白热），
 * 路径 {@code assets/wangsword/textures/particle/sword_wave.png}，
 * 粒子清单在 {@code assets/wangsword/particles/sword_wave.json}。
 *
 * <p>粒子类型本身要在双端注册（服务端负责 {@code spawnParticles} 时认得这个 id），
 * 客户端另外在 {@code WangSwordClientInit} 里注册工厂，才有具体的样子。
 */
public class ParticleRegistry {

    /** 巨剑音爆：蓝白能量冲击环 */
    public static final DefaultParticleType SWORD_WAVE = FabricParticleTypes.simple(true);

    /** 血帆弯刀突进：血色刀光 */
    public static final DefaultParticleType CUTLASS_SLASH = FabricParticleTypes.simple(true);

    /** 血帆弯刀命中：血花 */
    public static final DefaultParticleType BLOOD_BURST = FabricParticleTypes.simple(true);

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(Wangsword.MOD_ID, "sword_wave"), SWORD_WAVE);
        Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(Wangsword.MOD_ID, "cutlass_slash"), CUTLASS_SLASH);
        Registry.register(Registries.PARTICLE_TYPE,
                new Identifier(Wangsword.MOD_ID, "blood_burst"), BLOOD_BURST);
    }
}
