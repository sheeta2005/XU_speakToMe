package com.xiyou.speakToMe.canteen.controller;

import com.xiyou.speakToMe.canteen.dto.CampusVO;
import com.xiyou.speakToMe.canteen.service.CampusService;
import com.xiyou.speakToMe.framework.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 校区接口（公开：已加入登录白名单）。
 */
@RestController
@RequestMapping("/api/v1/campuses")
@RequiredArgsConstructor
public class CampusController {

    private final CampusService campusService;

    @GetMapping
    public Result<List<CampusVO>> list() {
        return Result.ok(campusService.listEnabled());
    }
}
