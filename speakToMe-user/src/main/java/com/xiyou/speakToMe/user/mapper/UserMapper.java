package com.xiyou.speakToMe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyou.speakToMe.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（由启动类 @MapperScan 统一注册，@Mapper 冗余标注便于独立单元测试）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
