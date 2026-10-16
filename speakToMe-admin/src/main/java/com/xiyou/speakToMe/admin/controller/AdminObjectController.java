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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyou.speakToMe.framework.result.PageResult;
import java.math.BigDecimal;
import java.util.Map;

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

    /** 10/16：维护列表包含停用对象，供管理员恢复；分页上限为 50。 */
    @GetMapping("/campuses")
    public Result<PageResult<Campus>> campuses(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "20") long size) {
        checkPage(page, size);
        return Result.ok(PageResult.of(campusMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Campus>().orderByAsc(Campus::getSort, Campus::getId))));
    }

    @GetMapping("/sites")
    public Result<PageResult<Site>> sites(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        checkPage(page, size);
        return Result.ok(PageResult.of(siteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Site>().orderByAsc(Site::getSort, Site::getId))));
    }

    @GetMapping("/stalls")
    public Result<PageResult<Stall>> stalls(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        checkPage(page, size);
        return Result.ok(PageResult.of(stallMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Stall>().orderByAsc(Stall::getSort, Stall::getId))));
    }

    @GetMapping("/dishes")
    public Result<PageResult<Dish>> dishes(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        checkPage(page, size);
        return Result.ok(PageResult.of(dishMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Dish>().orderByAsc(Dish::getSort, Dish::getId))));
    }

    /** 10/16：选择归属时展示层级名称，不要求管理员手填对象编号。 */
    @GetMapping("/object-options")
    public Result<Map<String, Object>> options() {
        adminGuard.requireAdmin();
        return Result.ok(Map.of(
                "campuses", campusMapper.selectList(new LambdaQueryWrapper<Campus>()
                        .select(Campus::getId, Campus::getName, Campus::getStatus).orderByAsc(Campus::getId)),
                "sites", siteMapper.selectList(new LambdaQueryWrapper<Site>()
                        .select(Site::getId, Site::getCampusId, Site::getName, Site::getStatus).orderByAsc(Site::getId)),
                "stalls", stallMapper.selectList(new LambdaQueryWrapper<Stall>()
                        .select(Stall::getId, Stall::getSiteId, Stall::getName, Stall::getStatus).orderByAsc(Stall::getId))));
    }

    // ---------- 校区 ----------

    @PostMapping("/campuses")
    public Result<Long> addCampus(@RequestBody Campus campus) {
        adminGuard.requireAdmin();
        validateCampus(campus);
        campus.setId(null);
        if (campus.getSort() == null) {
            campus.setSort(0);
        }
        if (campus.getStatus() == null) campus.setStatus(1);
        campusMapper.insert(campus);
        return Result.ok(campus.getId());
    }

    @PutMapping("/campuses/{id}")
    public Result<Void> updateCampus(@PathVariable Long id, @RequestBody Campus campus) {
        adminGuard.requireAdmin();
        requireExists(campusMapper.selectById(id));
        validateCampus(campus);
        campus.setId(id);
        campusMapper.updateById(campus);
        return Result.ok();
    }

    // ---------- 美食地点（校内食堂/校外区域） ----------

    @PostMapping("/sites")
    public Result<Long> addSite(@RequestBody Site site) {
        adminGuard.requireAdmin();
        validateSite(site);
        site.setId(null);
        if (site.getSiteType() == null || site.getSiteType() < 1 || site.getSiteType() > 3) {
            site.setSiteType(1);
        }
        if (site.getSort() == null) {
            site.setSort(0);
        }
        if (site.getStatus() == null) site.setStatus(1);
        siteMapper.insert(site);
        return Result.ok(site.getId());
    }

    @PutMapping("/sites/{id}")
    public Result<Void> updateSite(@PathVariable Long id, @RequestBody Site site) {
        adminGuard.requireAdmin();
        requireExists(siteMapper.selectById(id));
        validateSite(site);
        site.setId(id);
        siteMapper.updateById(site);
        return Result.ok();
    }

    // ---------- 档口/店铺 ----------

    @PostMapping("/stalls")
    public Result<Long> addStall(@RequestBody Stall stall) {
        adminGuard.requireAdmin();
        validateStall(stall);
        stall.setId(null);
        if (stall.getSort() == null) {
            stall.setSort(0);
        }
        if (stall.getStatus() == null) stall.setStatus(1);
        stallMapper.insert(stall);
        return Result.ok(stall.getId());
    }

    @PutMapping("/stalls/{id}")
    public Result<Void> updateStall(@PathVariable Long id, @RequestBody Stall stall) {
        adminGuard.requireAdmin();
        requireExists(stallMapper.selectById(id));
        validateStall(stall);
        stall.setId(id);
        stallMapper.updateById(stall);
        return Result.ok();
    }

    // ---------- 菜品 ----------

    @PostMapping("/dishes")
    public Result<Long> addDish(@RequestBody Dish dish) {
        adminGuard.requireAdmin();
        validateDish(dish);
        dish.setId(null);
        if (dish.getSort() == null) {
            dish.setSort(0);
        }
        if (dish.getStatus() == null) dish.setStatus(1);
        if (dish.getIsAvailable() == null) {
            dish.setIsAvailable(1);
        }
        dishMapper.insert(dish);
        return Result.ok(dish.getId());
    }

    @PutMapping("/dishes/{id}")
    public Result<Void> updateDish(@PathVariable Long id, @RequestBody Dish dish) {
        adminGuard.requireAdmin();
        requireExists(dishMapper.selectById(id));
        validateDish(dish);
        dish.setId(id);
        // 10/16：价格留空可清除旧价格；聚合评分字段不接收客户端输入。
        dishMapper.update(dish, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, id).set(dish.getPrice() == null, Dish::getPrice, null));
        return Result.ok();
    }

    private void checkName(String name) {
        if (StrUtil.isBlank(name) || name.trim().length() > 64) {
            throw new BizException(ErrorCode.BAD_PARAM, "名称需为 1-64 个字符");
        }
    }

    // 10/16：完整编辑表单统一校验状态、长度和归属，并屏蔽非运营字段。
    private void validateCampus(Campus value) {
        checkName(value.getName());
        if (value.getName().trim().length() > 32) bad("校区名称不能超过 32 字");
        checkState(value.getSort(), value.getStatus());
        value.setName(value.getName().trim());
        value.setCreatedAt(null);
        value.setUpdatedAt(null);
    }

    private void validateSite(Site value) {
        checkName(value.getName());
        checkState(value.getSort(), value.getStatus());
        requireExists(value.getCampusId() == null ? null : campusMapper.selectById(value.getCampusId()));
        if (value.getSiteType() != null && (value.getSiteType() < 1 || value.getSiteType() > 3)) bad("地点类型错误");
        value.setName(value.getName().trim());
        value.setLocation(text(value.getLocation(), 255));
        value.setOpenTime(text(value.getOpenTime(), 64));
        value.setCoverUrl(null);
        value.setAvgRating(null);
        value.setRatingCount(null);
        value.setCreatedAt(null);
        value.setUpdatedAt(null);
    }

    private void validateStall(Stall value) {
        checkName(value.getName());
        checkState(value.getSort(), value.getStatus());
        requireExists(value.getSiteId() == null ? null : siteMapper.selectById(value.getSiteId()));
        value.setName(value.getName().trim());
        value.setFloor(text(value.getFloor(), 16));
        value.setCategory(text(value.getCategory(), 32));
        value.setCreatedAt(null);
        value.setUpdatedAt(null);
    }

    private void validateDish(Dish value) {
        checkName(value.getName());
        checkState(value.getSort(), value.getStatus());
        requireExists(value.getStallId() == null ? null : stallMapper.selectById(value.getStallId()));
        if (value.getIsAvailable() != null && value.getIsAvailable() != 0 && value.getIsAvailable() != 1) bad("在售状态错误");
        BigDecimal price = value.getPrice();
        if (price != null && (price.signum() < 0 || price.compareTo(new BigDecimal("999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2)) bad("价格需为非负金额，最多两位小数");
        value.setName(value.getName().trim());
        value.setDescription(text(value.getDescription(), 255));
        value.setAvgRating(null);
        value.setRatingCount(null);
        value.setCreatedAt(null);
        value.setUpdatedAt(null);
    }

    private void checkPage(long page, long size) {
        adminGuard.requireAdmin();
        if (page < 1 || size < 1 || size > 50) bad("分页参数错误");
    }

    private void checkState(Integer sort, Integer status) {
        if (sort != null && (sort < 0 || sort > 255)) bad("排序需为 0-255 的整数");
        if (status != null && status != 0 && status != 1) bad("启停状态错误");
    }

    private String text(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) bad("文本长度不能超过 " + max + " 字");
        return result;
    }

    private void requireExists(Object value) {
        if (value == null) throw new BizException(ErrorCode.TARGET_NOT_FOUND, "对象或所属对象不存在");
    }

    private void bad(String message) { throw new BizException(ErrorCode.BAD_PARAM, message); }
}
