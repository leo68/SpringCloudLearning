package com.forezp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class HelloService {

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * 通过服务名调用hi服务，实现负载均衡
	 * @param name 用户名称参数
	 * @return 来自不同服务实例的响应结果
	 */
	public String hiService(String name) {
		// 注意：这里使用服务名"service-hi"而不是具体的URL
		return restTemplate.getForObject(
			"http://service-hi/hi?name=" + name, 
			String.class
		);
	}
}