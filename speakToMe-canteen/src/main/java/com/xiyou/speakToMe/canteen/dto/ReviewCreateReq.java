package com.xiyou.speakToMe.canteen.dto;

import lombok.Data;

/**
 * 评论提交请求（一期：module=food, targetType=dish）。
 */
@Data
public class ReviewCreateReq {

    /** 业务模块，默认 food */
    private String module = "food";

    /** 目标类型：dish */
    private String targetType = "dish";

    private Long targetId;

    /** 1-5 星 */
    private Integer rating;

    /** 10-200 字 */
    private String content;
}
