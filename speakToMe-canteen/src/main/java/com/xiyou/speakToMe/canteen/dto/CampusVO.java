package com.xiyou.speakToMe.canteen.dto;

import com.xiyou.speakToMe.canteen.entity.Campus;
import lombok.Data;

@Data
public class CampusVO {

    private Long id;

    private String name;

    public static CampusVO from(Campus campus) {
        CampusVO vo = new CampusVO();
        vo.setId(campus.getId());
        vo.setName(campus.getName());
        return vo;
    }
}
