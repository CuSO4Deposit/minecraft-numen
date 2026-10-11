package com.dwinovo.numen.pathing.drive;

import com.dwinovo.numen.api.entity.Controls.Key;
import com.dwinovo.numen.pathing.plan.Maneuver;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 攀爬:往上是在可攀爬的格里按住跳(原版按住跳就往上爬,藤蔓不必贴墙),往下是顺着滑下去(爬进脚手架要按住潜行);水平方向守在这一列
 * 正中。头顶要开的活板门、要挖的格先做:挂在梯子上做改动时按住潜行,原版攀爬时潜行不往下滑。
 */
final class ClimbControl extends Control {

    ClimbControl(Rig rig, Maneuver m, Gait.Stride stride) {
        super(rig, m, stride);
    }

    @Override
    Beat tick() {
        Beat edits = editsInPlace(allEdits());
        if (edits != null) {
            return edits;
        }
        keys().release(Key.SPRINT);
        boolean up = m.to().getY() > m.from().getY();
        // 脚手架每一格顶上的板托得住人,只有潜行(原版碰撞上下文里的"在下降")才让开:往下爬进脚手架要按住潜行
        keys().set(Key.SNEAK, !up && rig.world().getBlockState(m.to()).is(Blocks.SCAFFOLDING));
        keys().set(Key.JUMP, up);
        Vec3 c = center(m.to());
        Steering.stop(rig, c.x, c.z, m.landing().feetY());
        return Beat.IDLE;
    }

    @Override
    boolean falls() {
        return m.to().getY() < m.from().getY();
    }
}
