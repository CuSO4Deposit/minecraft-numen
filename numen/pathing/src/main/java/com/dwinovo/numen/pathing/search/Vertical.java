package com.dwinovo.numen.pathing.search;

import java.util.IdentityHashMap;
import java.util.Map;

import com.dwinovo.numen.pathing.plan.CostModel;
import com.dwinovo.numen.pathing.world.BodyStats;
import com.dwinovo.numen.pathing.world.Footing;
import com.dwinovo.numen.pathing.world.Kinematics;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 竖直方向的额外代价下界:从节点走到目标,脚所在的格要升或降几层,每一层至少比"按疾跑走过这一步的水平距离"多花多少刻。
 * 估价 = 水平底价({@link Goal#estimate})+ 挖开的额外代价({@link Burial})+ 这里的竖直额外代价({@link Estimate})。
 *
 * <p>怎么算:放宽问题的精确解。放宽成水平上随便走、不管站不站得住、不计挖,竖直每层各付各的,每一步付的额外代价是
 * "这一步真实代价减去疾跑走完它的水平距离"的下界,各种走法逐一核对过({@link EstimateConsistencyTest}):
 * <ul>
 *   <li><b>往上一个节点</b>:跳上去的一步(上一级)至少一个起跳间隔({@link Kinematics#jumpCycleTicks})加规格的起跳罚分,
 *       水平挪一格,水平底价已经付了一个疾跑一格,剩下的是额外代价(垫柱、攀爬、游往上更贵,都不比它低)。
 *       脚只抬半格也升一个节点的台阶、半砖不用跳,耗时只是一步走路,额外代价为零——只要快照里<b>这一层</b>出现过这样的落脚
 *       (顶面离下一格底不到迈步高度的方块:{@link Footing#tops});规格许跑酷时,跑酷落在高一级上,空中的工夫由跨过的几格水平距离
 *       付了,只剩起跳罚分;</li>
 *   <li><b>往下 {@code n} 个节点</b>:一次落脚(下一级、下落)是走出边沿再落到地上,至少付走出边沿的工夫加落下去的工夫
 *       ({@link Kinematics#fallLandingTicks})减去水平底价;规格许挖时,向下挖只付落下去的工夫。落差至少是节点数减去落脚那一层
 *       落脚高度的小数部分,最高是规格的无水落差;落脚那一层出现过液体、可攀爬的方块(活板门也可能接在梯子上),或身上带着水桶
 *       (倒水接住),落脚不受这个上限。落 {@code n} 个节点最少付多少,是把 {@code n} 拆成若干次落脚、每次付的和最小。
 *       斜走不升降节点,它的升降落差由跳与落带过。</li>
 * </ul>
 * 目标交出到了时脚所在的高度范围({@link Goal#heights()}):节点比它低就要升,比它高就要降,在范围里不用。没有范围的目标不加。
 * 开搜时读一遍快照里有这些方块的层(只扫调色板里有它们的方块段),之后每个节点只查一张表。
 */
final class Vertical {

    /** 不加。 */
    static final Vertical NONE = new Vertical(0, 0, 0, new double[0], new double[0]);

    /** 表的下标 0 对应的层。 */
    private final int base;
    private final int lo;
    private final int hi;
    /** {@code up[y - base]}:节点在第 {@code y} 层(低于 {@code lo})走到 {@code lo} 层至少多花的刻数。 */
    private final double[] up;
    /** {@code down[y - base]}:节点在第 {@code y} 层(高于 {@code hi})落到 {@code hi} 层至少多花的刻数。 */
    private final double[] down;

    private Vertical(int base, int lo, int hi, double[] up, double[] down) {
        this.base = base;
        this.lo = lo;
        this.hi = hi;
        this.up = up;
        this.down = down;
    }

    /** 脚在第 {@code y} 层的节点走进目标,竖直方向至少还要多付的刻数。 */
    double floor(int y) {
        double[] table = y < lo ? up : y > hi ? down : null;
        int i = y - base;
        return table != null && i >= 0 && i < table.length ? table[i] : 0;
    }

    /** 这次搜索的竖直额外代价:读 {@code view} 里的方块,按 {@code model} 的身体与规格,目标交出的高度范围。 */
    static Vertical of(SearchView view, CostModel model, Goal goal) {
        Goal.Heights heights = goal.heights();
        if (heights == null) {
            return NONE;
        }
        BodyStats body = model.body().stats();
        Levels levels = Levels.of(view, body);
        int base = view.getMinBuildHeight();
        int top = base + view.getHeight();
        return new Vertical(base, heights.lo(), heights.hi(), up(model, body, levels, heights.lo(), base, top),
                down(model, body, levels, heights.hi(), base, top));
    }

    private static double[] up(CostModel model, BodyStats body, Levels levels, int lo, int base, int top) {
        double penalty = model.spec().jumpPenalty();
        double jumping = Math.max(0, Kinematics.jumpCycleTicks(body, 1.0, body.stepHeight()) + penalty - Kinematics.sprintTicksPerBlock(body));
        double jump = model.spec().parkour() ? Math.min(jumping, penalty) : jumping;
        double[] up = new double[top - base];
        double sum = 0;
        for (int y = Math.min(lo, top) - 1; y >= base; y--) {
            sum += levels.smoothRise(y, body) ? 0 : jump;
            up[y - base] = sum;
        }
        return up;
    }

    private static double[] down(CostModel model, BodyStats body, Levels levels, int hi, int base, int top) {
        double sprint = Kinematics.sprintTicksPerBlock(body);
        boolean bucket = model.body().carriesWaterBucket();
        double[] fresh = new double[top - base + 1];
        for (int k = 1; k < fresh.length; k++) {
            fresh[k] = landing(model, body, sprint, k, 0);
        }
        double[] down = new double[top - base];
        for (int y = Math.max(hi + 1, base); y < top; y++) {
            double best = Double.POSITIVE_INFINITY;
            for (int k = 1; y - k >= hi; k++) {
                int land = y - k;
                double fraction = levels.fraction(land);
                if (!bucket && !levels.bypass(land)
                        && k > Math.max(1, (int) Math.floor(model.spec().maxFallHeightNoWater() + fraction + Footing.EPSILON))) {
                    continue;
                }
                double once = fraction == 0 ? fresh[k] : landing(model, body, sprint, k, fraction);
                best = Math.min(best, once + (land > hi ? down[land - base] : 0));
            }
            down[y - base] = best;
        }
        return down;
    }

    /**
     * 一次落 {@code k} 个节点的落脚至少比水平底价多付多少:走出边沿加落下去的工夫减去疾跑一格。落差至少 {@code k} 减去落脚高度的
     * 小数部分 {@code fraction}。落一个节点还有向下挖(只付落下去的工夫,挖的钱在埋深里),规格许挖时取便宜的。
     */
    private static double landing(CostModel model, BodyStats body, double sprint, int k, double fraction) {
        double landing = Kinematics.fallLandingTicks(body, k - fraction);
        double straight = Math.max(0, Kinematics.walkOffEdgeTicks(body) + landing - sprint);
        return k == 1 && model.spec().dig() ? Math.min(straight, landing) : straight;
    }

    /** 快照里每一层有什么落脚:顶面的小数部分、落进去不摔的液体与可攀爬的方块。 */
    private static final class Levels {
        private final int base;
        /** 节点在这一层、脚踩在矮方块上,脚在格里最高的小数部分。 */
        private final double[] fraction;
        /** 这一层有液体或可攀爬的方块。 */
        private final boolean[] bypass;

        private Levels(int base, int size) {
            this.base = base;
            this.fraction = new double[size];
            this.bypass = new boolean[size];
        }

        double fraction(int y) {
            int i = y - base;
            return i >= 0 && i < fraction.length ? fraction[i] : 0;
        }

        boolean bypass(int y) {
            int i = y - base;
            return i >= 0 && i < bypass.length && bypass[i];
        }

        /** 从第 {@code y} 层的节点升到上一层不用跳:这一层有顶面离下一格底不到迈步高度的落脚。 */
        boolean smoothRise(int y, BodyStats body) {
            return 1 - fraction(y) <= body.stepHeight() + Footing.EPSILON;
        }

        static Levels of(SearchView view, BodyStats body) {
            Levels levels = new Levels(view.getMinBuildHeight(), view.getHeight());
            Map<BlockState, Info> known = new IdentityHashMap<>();
            view.forEachBlock(state -> known.computeIfAbsent(state, s -> Info.of(body, s)).interesting(), (state, y) -> {
                Info info = known.get(state);
                int i = y - levels.base;
                if (info.bypass() && i >= 0 && i < levels.bypass.length) {
                    levels.bypass[i] = true;
                }
                for (double top : info.tops()) {
                    int at = y + (int) Math.floor(top + Footing.EPSILON) - levels.base;
                    if (at >= 0 && at < levels.fraction.length) {
                        levels.fraction[at] = Math.max(levels.fraction[at], top - Math.floor(top + Footing.EPSILON));
                    }
                }
            });
            return levels;
        }
    }

    /**
     * 一种方块状态:碰撞箱顶面的高度、能不能落进去不摔;以及值得逐格找出来的({@code interesting}):顶面有小数(不是整格也不是空),
     * 或落进去不摔。调色板里每种状态每个方块段都问一遍,所以开搜时算定。
     */
    private record Info(double[] tops, boolean bypass, boolean interesting) {

        static Info of(BodyStats body, BlockState state) {
            boolean bypass = !state.getFluidState().isEmpty() || state.is(BlockTags.CLIMBABLE) || state.getBlock() instanceof TrapDoorBlock;
            double[] tops = Footing.tops(body, state);
            boolean interesting = bypass;
            for (double top : tops) {
                interesting |= top - Math.floor(top + Footing.EPSILON) > Footing.EPSILON;
            }
            return new Info(tops, bypass, interesting);
        }
    }
}
