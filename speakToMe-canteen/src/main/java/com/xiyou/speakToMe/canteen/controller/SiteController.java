package com.xiyou.speakToMe.canteen.controller;

import com.xiyou.speakToMe.canteen.dto.SiteVO;
import com.xiyou.speakToMe.canteen.dto.StallVO;
import com.xiyou.speakToMe.canteen.service.SiteService;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 美食地点接口：地点列表 + 档口/店铺列表。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    /** 某校区的地点列表（校内食堂/校外区域） */
    @GetMapping("/sites")
    public Result<List<SiteVO>> list(@RequestParam Long campusId) {
        if (campusId == null || campusId <= 0) {
            throw new BizException(ErrorCode.BAD_PARAM, "校区参数错误");
        }
        return Result.ok(siteService.listByCampus(campusId));
    }

    /** 某地点下的档口/店铺列表 */
    @GetMapping("/sites/{siteId}/stalls")
    public Result<List<StallVO>> stalls(@PathVariable Long siteId) {
        return Result.ok(siteService.listStalls(siteId));
    }
}
