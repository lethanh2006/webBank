package com.webbank;

import org.junit.jupiter.api.Test;

import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Fails the build when a module reaches into another module's internals or when module
 * dependencies form a cycle. Also writes C4/PlantUML diagrams to target/spring-modulith-docs.
 */
class ModularityTests {

	static final ApplicationModules modules = ApplicationModules.of(WebbankApplication.class);

	@Test
	void modulesRespectTheirBoundaries() {
		modules.verify();
	}

	@Test
	void writeModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
