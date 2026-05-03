package com.example.spring_boot_1;

import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.UserData.UserDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class SpringBoot1ApplicationTests {

	@Autowired
	private LinkDataRepository linkDataRepository;
	@Autowired
	private UserDataRepository userDataRepository;

	@Test
	void testJpa() {

	}

}
