package com.dwinovo.numen.pathing.search;

import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;

import com.dwinovo.numen.pathing.plan.WorldView;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 搜索读的世界:派发那一刻的只读快照({@link WorldSnapshot}),外加哪些列在快照里。快照之外的列读出来是空气,那不是
 * 测量,所以搜索碰到它们就停在边上,不往里走。
 */
public interface SearchView extends WorldView {

    /** {@code (x, z)} 这一列所在的区块在快照里。 */
    boolean isLoaded(int x, int z);

    /**
     * 快照里状态让 {@code wanted} 答是的每一格,回调它的状态与所在的层(格的 y)。估价靠它知道每一层有什么台阶、水、梯子。
     * {@code wanted} 会对调色板里列着的每种状态问一遍(调色板可能多列几种已经没有的),只有答是的状态才逐格找。
     */
    void forEachBlock(Predicate<BlockState> wanted, ObjIntConsumer<BlockState> visitor);
}
