package com.xiyou.speakToMe.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyou.speakToMe.admin.security.AdminGuard;
import com.xiyou.speakToMe.common.constant.ErrorCode;
import com.xiyou.speakToMe.common.exception.BizException;
import com.xiyou.speakToMe.content.entity.SensitiveWord;
import com.xiyou.speakToMe.content.filter.SensitiveWordFilter;
import com.xiyou.speakToMe.content.mapper.SensitiveWordMapper;
import com.xiyou.speakToMe.framework.result.PageResult;
import com.xiyou.speakToMe.framework.result.Result;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感词库管理（管理端）：分页查询、新增（含补词即生效）、停用。
 */
@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
@RequiredArgsConstructor
public class AdminSensitiveWordController {

    private final AdminGuard adminGuard;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    @Data
    public static class AddReq {
        private String word;
        /** 1政治 2色情 3辱骂 4广告 5学术不端 6其他 */
        private Integer category;
        /** 1直接拦截 2人工复审 */
        private Integer level;
    }

    /** 词库分页 */
    @GetMapping
    public Result<PageResult<SensitiveWord>> list(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size) {
        adminGuard.requireAdmin();
        Page<SensitiveWord> result = sensitiveWordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SensitiveWord>().orderByDesc(SensitiveWord::getCreatedAt));
        return Result.ok(PageResult.of(result));
    }

    /** 新增词并热更新 Trie（补词即生效） */
    @PostMapping
    public Result<Long> add(@RequestBody AddReq req) {
        adminGuard.requireAdmin();
        if (StrUtil.isBlank(req.getWord()) || req.getWord().trim().length() > 64) {
            throw new BizException(ErrorCode.BAD_PARAM, "敏感词需为 1-64 个字符");
        }
        if (req.getCategory() == null || req.getCategory() < 1 || req.getCategory() > 6) {
            throw new BizException(ErrorCode.BAD_PARAM, "分类错误");
        }
        if (req.getLevel() == null || (req.getLevel() != 1 && req.getLevel() != 2)) {
            req.setLevel(1);
        }
        Long exists = sensitiveWordMapper.selectCount(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getWord, req.getWord().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.BAD_PARAM, "该敏感词已存在");
        }
        SensitiveWord word = new SensitiveWord();
        word.setWord(req.getWord().trim());
        word.setCategory(req.getCategory());
        word.setLevel(req.getLevel());
        word.setStatus(1);
        word.setSource("manual");
        sensitiveWordMapper.insert(word);
        sensitiveWordFilter.reload();
        return Result.ok(word.getId());
    }

    /** 停用词（软停用，保留审计） */
    @DeleteMapping("/{id}")
    public Result<Void> disable(@PathVariable Long id) {
        adminGuard.requireAdmin();
        SensitiveWord word = sensitiveWordMapper.selectById(id);
        if (word == null) {
            throw new BizException(ErrorCode.TARGET_NOT_FOUND, "敏感词不存在");
        }
        word.setStatus(0);
        sensitiveWordMapper.updateById(word);
        sensitiveWordFilter.reload();
        return Result.ok();
    }
}
