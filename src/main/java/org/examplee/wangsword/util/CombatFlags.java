package org.examplee.wangsword.util;

/** 跨 mixin 共享的临时标记（Mixin 类内不能放公共静态字段）。 */
public final class CombatFlags {

    /** 凋灵双倍伤害正在重入，避免无限递归 */
    public static boolean doublingWither;
    /** 凋灵杀手命中凋灵期间，屏蔽击退 */
    public static boolean noWitherKnockback;
    /** 幽卫钢套的 BOSS 减伤正在重入，避免无限递归 */
    public static boolean reducingFulgurBoss;

    private CombatFlags() {
    }
}