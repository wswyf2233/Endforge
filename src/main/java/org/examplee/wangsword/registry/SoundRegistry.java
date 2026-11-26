package org.examplee.wangsword.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.examplee.wangsword.Wangsword;

/**
 * 自定义音效注册表。
 *
 * <p>目前只有一个：剑气（swordws.ogg）。
 */
public class SoundRegistry {

    /** 剑气 */
    public static final SoundEvent SWORD_WS = registerSound("swordws");

    /** 流星弓射击 */
    public static final SoundEvent BOW_SHOOT = registerSound("bowshoot");

    /** 黑龙弩右键炮弹 */
    public static final SoundEvent CROSSBOW_SHOOT = registerSound("shoot_01");

    /** 黑龙弩左键连发 */
    public static final SoundEvent CROSSBOW_FIRE = registerSound("shoot_02");

    /** 王之冲击（冲锋）的冲击音 */
    public static final SoundEvent SLASH_IMPACT = registerSound("slash");

    /** 誓约胜利之剑大招音效 */
    public static final SoundEvent EXCALIBUR = registerSound("excalibur");

    /** 誓约胜利之剑右键·风王结界 */
    public static final SoundEvent EXCALIBUR_WIND = registerSound("excalibur_wind");

    /** 誓约胜利之剑·普通攻击挥砍 */
    public static final SoundEvent EXCALIBUR_SWING = registerSound("excalibur_swing");

    private static SoundEvent registerSound(String name) {
        Identifier id = new Identifier(Wangsword.MOD_ID, name);
        SoundEvent soundEvent = SoundEvent.of(id);
        Registry.register(Registries.SOUND_EVENT, id, soundEvent);
        return soundEvent;
    }

    public static void registerAll() {
        // 引用静态字段即触发注册
    }
}