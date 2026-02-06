package com.forezp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第二个服务实例应用类
 * 用于演示负载均衡功能
 */
@SpringBootApplication
@EnableEurekaClient
@RestController
public class ServiceHiSecondApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceHiSecondApplication.class, args);
	}

	@Value("${server.port}")
	private String port;
	
	/**
	 * 处理/hi路径的HTTP请求，返回来自第二个实例的问候信息
	 * @param name 请求参数，用户名称
	 * @return 包含用户名称和服务器端口的问候字符串
	 */
	@RequestMapping("/hi")
	public String home(@RequestParam String name) {
		return "hi "+name+",i am from SECOND instance, port:" +port;
	}
}