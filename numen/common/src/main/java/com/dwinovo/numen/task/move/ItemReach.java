package com.dwinovo.numen.task.move;

import com.dwinovo.numen.pathing.plan.Stance;
import com.dwinovo.numen.pathing.search.Goal;
import com.dwinovo.numen.pathing.world.Kinematics;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * 掉在地上的东西"走到旁边"是走到身体捡得到它的地方:原版玩家每刻拿自己的碰撞盒各向外放 {@value #GROW_XZ}(水平)、{@value #GROW_Y}(竖直)
 * 去碰物品的盒子,碰上了就捡。判据与原版是同一个,所以不必估"离它几格":贴着台沿的东西坐标取整落在悬空的格里,那一格站不进去,
 * 可从旁边一格就捡得到。
 */
final class ItemReach implements Goal {

    /** 原版捡东西时玩家盒子向外放的量。 */
    static final double GROW_XZ = 1.0;
    static final double GROW_Y = 0.5;
    /** 一格的半宽。 */
    private static final double CELL_HALF = 0.5;

    private final AABB item;

    ItemReach(AABB item) {
        this.item = item;
    }

    /**
     * 节点这一格里站在哪儿都捡得到:身体停在格子里哪个位置不定(搜索只知道在哪一格),所以水平上按格里离物品最远的那一点算,
     * 竖直上按脚底的高度(落脚的格定了高度)。
     */
    @Override
    public boolean contains(int x, int y, int z, Stance stance) {
        double feet = stance.feetY();
        return Math.abs(x + 0.5 - item.getCenter().x) + CELL_HALF <= reachX()
                && Math.abs(z + 0.5 - item.getCenter().z) + CELL_HALF <= reachZ()
                && feet - GROW_Y <= item.maxY && feet + EntityType.PLAYER.getHeight() + GROW_Y >= item.minY;
    }

    /** 下界:节点中心离"能算到了的那一圈"的水平距离;欧氏距离不超过任何走法的格数。 */
    @Override
    public double estimate(int x, int y, int z, Kinematics.Pace pace) {
        double dx = Math.max(0, Math.abs(x + 0.5 - item.getCenter().x) - (reachX() - CELL_HALF));
        double dz = Math.max(0, Math.abs(z + 0.5 - item.getCenter().z) - (reachZ() - CELL_HALF));
        return pace.estimate(Math.hypot(dx, dz), 0);
    }

    /** 水平上物品盒的中心到身体中心多远以内碰得上:身体半宽加向外放的量加物品半宽。 */
    private double reachX() {
        return EntityType.PLAYER.getWidth() / 2 + GROW_XZ + item.getXsize() / 2;
    }

    private double reachZ() {
        return EntityType.PLAYER.getWidth() / 2 + GROW_XZ + item.getZsize() / 2;
    }

    @Override
    public String toString() {
        return "pickUp(" + item + ")";
    }
}
