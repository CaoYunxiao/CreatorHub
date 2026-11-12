package com.creatorhub.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.creatorhub.backend.repository.WorkRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.UserRepository;

// 普通上下文测试不连接数据库；真实连接由显式开启的专用测试验证。
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
class BackendApplicationTests {

	@MockitoBean
	WorkRepository workRepository;

	@MockitoBean
	WorkImageRepository workImageRepository;

	@MockitoBean
	UserRepository userRepository;

	@Test
	void contextLoads() {
	}

}
