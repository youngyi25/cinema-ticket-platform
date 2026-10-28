package com.xinghe.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 星河影城票务平台启动类。
 *
 * <p>七个服务模块（cinema / movie / schedule / ticket / payment / member / order）在同一进程内装配，
 * 保留模块边界与依赖方向（order → ticket / payment / schedule），
 * 因此无需 Nacos / Gateway 也能跑通完整链路；需要拆成独立进程时，把每个模块加一个 main 类即可。</p>
 */
@SpringBootApplication(scanBasePackages = "com.xinghe.cinema")
@EnableScheduling
public class CinemaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinemaApplication.class, args);
    }
}
