package org.examplee.wangsword.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 自动攻击（剑阵、飞剑、光环这类"自己会打人"的效果）的目标筛选。
 *
 * <p>为什么需要它：{@code world.getEntitiesByClass(LivingEntity.class, ...)} 拿到的是
 * <b>所有</b>活物，村民、牛羊、铁傀儡、盔甲架全在里面。剑阵只要按"最近的活物"开火，
 * 村庄里就会变成屠杀现场。所以凡是不指定目标的自动伤害，都必须先过这一层。
 *
 * <p>判定顺序：
 * <ol>
 *   <li>玩家永远不打；</li>
 *   <li>{@link Monster} 或刷怪组为 {@link SpawnGroup#MONSTER} 的算敌对——后者是为了兜住
 *       史莱姆、岩浆怪、恶魂、潜影贝这些没继承 {@code HostileEntity} 的家伙，以及 Mod 生物；</li>
 *   <li>其余（村民、动物、傀儡、盔甲架）默认不打；只有"正在打主人"的才会被反打，
 *       由调用方另行判断。</li>
 * </ol>
 */
public final class CombatTargets {

    private CombatTargets() {
    }

    /** 是否属于"会主动攻击人"的敌对生物 */
    public static boolean isHostile(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            return false;
        }
        if (entity instanceof Monster) {
            return true;
        }
        return entity.getType().getSpawnGroup() == SpawnGroup.MONSTER;
    }

    /** 自动攻击能否落到这个目标身上：敌对生物，或者正在打主人的中立生物 */
    public static boolean isAutoAttackTarget(LivingEntity entity, LivingEntity owner) {
        if (!isHostile(entity)) {
            // 铁傀儡、被激怒的狼这类"本来中立、现在追着主人打"的，允许反打
            return owner != null
                    && entity instanceof net.minecraft.entity.mob.MobEntity mob
                    && mob.getTarget() == owner;
        }
        return true;
    }

    /**
     * <b>玩家主动释放</b>的技能（霜环、冰痕突进这类按下才发生的一次性效果）能打谁。
     *
     * <p>和 {@link #isHostile} 是两件事，别混用：
     * <ul>
     *   <li>{@code isHostile} 管「自己会打人」的持续效果——剑阵、飞剑、焚身领域。
     *       它们不受玩家控制，一旦放宽就会把村庄变成屠宰场；</li>
     *   <li>这个管玩家自己按出来的技能。目标由玩家选定，所以牛、羊、猪这些中立/被动生物
     *       都应该吃伤害——只放敌对会让「对着牛放技能没反应」，那才是 bug。</li>
     * </ul>
     *
     * <p>唯一排除的是玩家（自己和其他玩家都跳过）与旁观者——这一条和烈火大刀的火柱、
     * 喷射保持一致，免得联机时一个霜环把队友也冻住。
     */
    public static boolean isSkillTarget(LivingEntity entity) {
        return entity.isAlive()
                && !entity.isSpectator()
                && !(entity instanceof PlayerEntity);
    }
}
