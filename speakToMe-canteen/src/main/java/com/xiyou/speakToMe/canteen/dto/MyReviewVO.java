package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Dish;
import com.xiyou.speakToMe.canteen.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;

/** 10/15：本人点评视图，保留审核状态并附带菜品信息，避免暴露用户内部字段。 */
@Data
public class MyReviewVO {

    private Long id;
    private Long dishId;
    private String dishName;
    private Integer rating;
    private String content;
    private Integer auditStatus;
    private LocalDateTime createdAt;

    public static MyReviewVO from(Review review, Dish dish) {
        MyReviewVO vo = new MyReviewVO();
        vo.setId(review.getId());
        vo.setDishId(review.getTargetId());
        vo.setDishName(dish == null ? "菜品已移除" : dish.getName());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setAuditStatus(review.getAuditStatus());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }
}
