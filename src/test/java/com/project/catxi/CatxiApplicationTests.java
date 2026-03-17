package com.project.catxi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CatxiApplicationTests {

	@Test
	void mainClassLoads() {
		assertDoesNotThrow(() -> Class.forName("com.project.catxi.CatxiApplication"));
	}

}
