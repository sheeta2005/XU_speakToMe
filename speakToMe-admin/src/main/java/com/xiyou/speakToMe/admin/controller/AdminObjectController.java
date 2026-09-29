package com.xiyou.speakToMe.admin.controller;

import com.xiyou.speakToMe.admin.security.AdminGuard;
import com.xiyou.speakToMe.canteen.entity.Campus;
import com.xiyou.speakToMe.canteen.entity.Dish;
import com.xiyou.speakToMe.canteen.entity.Site;
import com.xiyou.speakToMe.canteen.entity.Stall;
import com.xiyou.speakToMe.canteen.mapper.CampusMapper;
import com.xiyou.speakToMe.canteen.mapper.DishMapper;
import com.xiyou.speakToMe.canteen.mapper.SiteMapper;
import com.xiyou.speakToMe.canteen.mapper.StallMapper;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.framework.result.Result;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对象维护（管理端）：校区 / 美食地点 / 档口 / 菜品 新增、修改（status=0 即下线）。
 * 停用不影响历史评论与排行数据。
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminObjectController {

    private final AdminGuard adminGuard;
    private final CampusMapper campusMapper;
    private final SiteMapper siteMapper;
    private final StallMapper stallMapper;
    private final DishMapper dishMapper;

    // ---------- 校区 ----------

    @PostMapping("/campuses")
    public Result<Long> addCampus(@RequestBody Campus campus) {
        adminGuard.requireAdmin();
        checkName(campus.getName());
        if (campus.getSort() == null) {
            campus.setSort(0);
        }
        campus.setStatus(1);
        campusMapper.insert(campus);
        return Result.ok(campus.getId());
    }

    @PutMapping("/campuses/{id}")
    public Result<Void> updateCampus(@PathVariable Long id, @RequestBody Campus campus) {
        adminGuard.requireAdmin();
        if (StrUtil.isNotBlank(campus.getName())) {
            checkName(campus.getName());
        }
        campus.setId(id);
        campusMapper.updateById(campus);
        return Result.ok();
    }

    // ---------- 美食地点（校内食堂/校外区域） ----------

    @PostMapping("/sites")
    public Result<Long> addSite(@RequestBody Site site) {
        adminGuard.requireAdmin();
        checkName(site.getName());
        if (site.getCampusId() == null) {
            throw new BizException(ErrorCode.BAD_PARAM, "必须指定所属校区");
        }
        if (site.getSiteType() == null || site.getSiteType() < 1 || site.getSiteType() > 3) {
            site.setSiteType(1);
        }
        if (site.getSort() == null) {
            site.setSort(0);
        }
        site.setStatus(1);
        siteMapper.insert(site);
        return Result.ok(site.getId());
    }

    @PutMapping("/sites/{id}")
    public Result<Void> updateSite(@PathVariable Long id, @RequestBody Site site) {
        adminGuard.requireAdmin();
        if (StrUtil.isNotBlank(site.getName())) {
            checkName(site.getName());
        }
        site.setId(id);
        siteMapper.updateById(site);
        return Result.ok();
    }

    // ---------- 档口/店铺 ----------

    @PostMapping("/stalls")
    public Result<Long> addStall(@RequestBody Stall stall) {
        adminGuard.requireAdmin();
        checkName(stall.getName());
        if (stall.getSiteId() == null) {
            throw new BizException(ErrorCode.BAD_PARAM, "必须指定所属地点");
        }
        if (stall.getSort() == null) {
            stall.setSort(0);
        }
        stall.setStatus(1);
        stallMapper.insert(stall);
        return Result.ok(stall.getId());
    }

    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(@PathVariable Long id, @RequestBody Stall stall) {
        adminGuard.requireAdmin();
        if (StrUtil.isNotBlank(stall.getName())) {
            checkName(stall.getName());
        }
        stall.setId(id);
        stallMapper.updateById(stall);
        return Result.ok();
    }

    // ---------- 菜品 ----------

    @PostMapping("/dishes")
    public Result<Long> addDish(@RequestBody Dish dish) {
        adminGuard.requireAdmin();
        checkName(dish.getName());
        if (dish.getStallId() == null) {
            throw new BizException(ErrorCode.BAD_PARAM, "必须指定所属档口");
        }
        if (dish.getSort() == null) {
            dish.setSort(0);
        }
        dish.setStatus(1);
        if (dish.getIsAvailable() == null) {
            dish.setIsAvailable(1);
        }
        dishMapper.insert(dish);
        return Result.ok(dish.getId());
    }

    @PutMapping("/dishes/{id}")
    public Result<Void> updateDish(@PathVariable Long id, @RequestBody Dish dish) {
        adminGuard.requireAdmin();
        if (StrUtil.isNotBlank(dish.getName())) {
            checkName(dish.getName());
        }
        dish.setId(id);
        dishMapper.updateById(dish);
        return Result.ok();
    }

    private void checkName(String name) {
        if (StrUtil.isBlank(name) || name.trim().length() > 64) {
            throw new BizException(ErrorCode.BAD_PARAM, "名称需为 1-64 个字符");
        }
    }
}
