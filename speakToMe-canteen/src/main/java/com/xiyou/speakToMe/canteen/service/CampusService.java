package com.xiyou.speakToMe.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.canteen.dto.CampusVO;
import com.xiyou.speakToMe.canteen.entity.Campus;
import com.xiyou.speakToMe.canteen.mapper.CampusMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 校区服务。
 */
@Service
public class CampusService {

    private final CampusMapper campusMapper;

    public CampusService(CampusMapper campusMapper) {
        this.campusMapper = campusMapper;
    }

    /** 启用中的校区列表（公开接口，白名单放行） */
    public List<CampusVO> listEnabled() {
        return campusMapper.selectList(
                        new LambdaQueryWrapper<Campus>()
                                .eq(Campus::getStatus, 1)
                                .orderByAsc(Campus::getSort))
                .stream().map(CampusVO::from).toList();
    }
}
