package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Stall;
import lombok.Data;

@Data
public class StallVO {

    private Long id;

    private Long siteId;

    private String name;

    private String floor;

    private String category;

    public static StallVO from(Stall stall) {
        StallVO vo = new StallVO();
        vo.setId(stall.getId());
        vo.setSiteId(stall.getSiteId());
        vo.setName(stall.getName());
        vo.setFloor(stall.getFloor());
        vo.setCategory(stall.getCategory());
        return vo;
    }
}
