package com.xiyou.speakToMe.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xiyou.speakToMe.canteen.dto.SiteVO;
import com.xiyou.speakToMe.canteen.dto.StallVO;
import com.xiyou.speakToMe.canteen.entity.Site;
import com.xiyou.speakToMe.canteen.entity.Stall;
import com.xiyou.speakToMe.canteen.mapper.SiteMapper;
import com.xiyou.speakToMe.canteen.mapper.StallMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 美食地点服务：地点列表 + 档口列表（对象由管理端维护）。
 */
@Service
public class SiteService {

    private final SiteMapper siteMapper;
    private final StallMapper stallMapper;

    public SiteService(SiteMapper siteMapper, StallMapper stallMapper) {
        this.siteMapper = siteMapper;
        this.stallMapper = stallMapper;
    }

    /** 某校区启用中的地点列表 */
    public List<SiteVO> listByCampus(Long campusId) {
        return siteMapper.selectList(
                        new LambdaQueryWrapper<Site>()
                                .eq(Site::getCampusId, campusId)
                                .eq(Site::getStatus, 1)
                                .orderByAsc(Site::getSort))
                .stream().map(SiteVO::from).toList();
    }

    /** 某地点下的档口/店铺列表 */
    public List<StallVO> listStalls(Long siteId) {
        return stallMapper.selectList(
                        new LambdaQueryWrapper<Stall>()
                                .eq(Stall::getSiteId, siteId)
                                .eq(Stall::getStatus, 1)
                                .orderByAsc(Stall::getSort))
                .stream().map(StallVO::from).toList();
    }
}
