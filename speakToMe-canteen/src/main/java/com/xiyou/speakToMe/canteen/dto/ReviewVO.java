package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Review;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论视图（对外）。
 */
@Data
public class ReviewVO {

    private Long id;

    /** 点评人昵称 */
    private String nickname;

    private String avatarUrl;

    private Integer rating;

    private String content;

    /** 1 通过 3 人工复审（审核中仅本人可见） */
    private Integer auditStatus;

    private LocalDateTime createdAt;

    /** 是否可删除（本人评论为 true） */
    private Boolean deletable;

    public static ReviewVO from(Review review, String nickname, String avatarUrl, boolean deletable) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setNickname(nickname);
        vo.setAvatarUrl(avatarUrl);
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setAuditStatus(review.getAuditStatus());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setDeletable(deletable);
        return vo;
    }
}
