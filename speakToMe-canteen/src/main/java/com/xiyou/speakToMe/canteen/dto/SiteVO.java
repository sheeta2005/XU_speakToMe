package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Site;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 美食地点视图。
 */
@Data
public class SiteVO {

    private Long id;

    private Long campusId;

    private String name;

    /** 1 校内食堂 2 校外区域 3 其他 */
    private Integer siteType;

    private String location;

    private String openTime;

    private BigDecimal avgRating;

    private Integer ratingCount;

    public static SiteVO from(Site site) {
        SiteVO vo = new SiteVO();
        vo.setId(site.getId());
        vo.setCampusId(site.getCampusId());
        vo.setName(site.getName());
        vo.setSiteType(site.getSiteType());
        vo.setLocation(site.getLocation());
        vo.setOpenTime(site.getOpenTime());
        vo.setAvgRating(site.getAvgRating());
        vo.setRatingCount(site.getRatingCount());
        return vo;
    }
}
