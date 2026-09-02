package com.medirag.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medirag.entity.SysUser;
import com.medirag.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/**
 * 演示账号初始化器。
 *
 * <p>仅当 sys_user 表完全为空时（即全新部署），写入 3 个演示账号，
 * 方便开箱即用；已有用户数据的库不会做任何改动。
 *
 * <p>账号与 README 保持一致：
 * admin/Admin@123456（管理员）、doctor1/Doctor@123（医护）、user1/User@123456（用户）。
 * 生产部署请务必删除演示账号并启用强密码策略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("aiConfigInitializer")
public class DemoAccountInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0));
        if (count != null && count > 0) {
            log.info("[DemoAccountInitializer] 已有 {} 个用户，跳过演示账号初始化", count);
            return;
        }

        createDemoUser("admin", "Admin@123456", "系统管理员", "admin");
        createDemoUser("doctor1", "Doctor@123", "演示医生", "doctor");
        createDemoUser("user1", "User@123456", "演示用户", "user");
        log.info("[DemoAccountInitializer] 演示账号初始化完成（生产环境请删除！）");
    }

    private void createDemoUser(String username, String rawPassword, String nickname, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        // createTime/updateTime 由 MyBatis-Plus MetaObjectHandler 自动填充
        userMapper.insert(user);
        log.info("[DemoAccountInitializer] 创建演示账号: {} ({})", username, role);
    }
}
