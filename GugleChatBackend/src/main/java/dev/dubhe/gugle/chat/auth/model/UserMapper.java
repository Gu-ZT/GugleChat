package dev.dubhe.gugle.chat.auth.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    default User findByUsername(String username) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    default User findByEmail(String email) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    default boolean existsByUsername(String username) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    default boolean existsByEmail(String email) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0;
    }
}
