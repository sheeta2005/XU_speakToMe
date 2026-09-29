package com.xiyou.speakToMe.canteen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyou.speakToMe.canteen.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 菜品 Mapper（含排行榜聚合查询）。
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /** 排行榜聚合行 */
    record HotDishRow(Long dishId, String dishName, String siteName, String stallName,
                      BigDecimal avgRating, Integer recentReviewCount, BigDecimal hotScore) {
    }

    /**
     * 近 N 天最火菜品聚合：热度分 = 评论数 x 2 + 平均分 x 10（时间衰减加权在应用层二次计算）。
     * 口径与详情评分一致：module=food / target_type=dish / audit_status=1 / is_deleted=0。
     */
    @Select("""
            SELECT d.id AS dish_id, d.name AS dish_name,
                   s.name AS site_name, st.name AS stall_name,
                   d.avg_rating AS avg_rating, COUNT(r.id) AS recent_review_count,
                   ROUND(COUNT(r.id) * 2 + AVG(r.rating) * 10, 2) AS hot_score
            FROM review r
            JOIN dish d  ON d.id = r.target_id
            JOIN stall st ON st.id = d.stall_id
            JOIN site s  ON s.id = st.site_id
            WHERE r.module = 'food' AND r.target_type = 'dish'
              AND r.audit_status = 1 AND r.is_deleted = 0
              AND r.created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
              AND d.status = 1 AND d.is_available = 1
            GROUP BY d.id, d.name, s.name, st.name, d.avg_rating
            ORDER BY hot_score DESC
            LIMIT 50
            """)
    List<HotDishRow> selectHotDishes(@Param("days") int days);
}
