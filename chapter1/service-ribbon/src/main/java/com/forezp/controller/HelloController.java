package com.forezp.controller;

import com.forezp.service.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@Autowired
	private HelloService helloService;

	/**
	 * 负载均衡测试接口
	 * @param name 用户名称
	 * @return 来自不同服务实例的问候信息
	 */
	@GetMapping(value = "/hi")
	public String hi(@RequestParam String name) {
		return helloService.hiService(name);
	}
}