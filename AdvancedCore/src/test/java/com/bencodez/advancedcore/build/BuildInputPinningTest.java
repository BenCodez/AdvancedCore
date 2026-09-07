package com.bencodez.advancedcore.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BuildInputPinningTest {

	@Test
	void javadocPublisherUsesImmutableRevisionAndExplicitPermissions() throws IOException {
		String workflow = Files.readString(Path.of("..", ".github", "workflows", "publish-javadoc.yml"));
		String normalizedWorkflow = workflow.replace("\r\n", "\n");

		assertFalse(workflow.contains("MathieuSoysal/Javadoc-publisher"));
		assertTrue(workflow.matches("(?s).*actions/checkout@[0-9a-f]{40}.*"));
		assertTrue(workflow.matches("(?s).*actions/setup-java@[0-9a-f]{40}.*"));
		assertTrue(workflow.matches("(?s).*actions/deploy-pages@[0-9a-f]{40}.*"));
		assertTrue(normalizedWorkflow.contains("permissions:\n  contents: read\n  pages: write\n  id-token: write"));
		assertTrue(workflow.contains("maven-javadoc-plugin:3.12.0:javadoc"));
		assertFalse(workflow.contains("Javadoc-publisher.yml@main"));
	}

	@Test
	void simpleApiDependencyUsesImmutableSnapshotBuild() throws IOException {
		String pom = Files.readString(Path.of("pom.xml"));
		int dependency = pom.indexOf("<artifactId>simpleapi</artifactId>");

		assertTrue(dependency >= 0);
		String declaration = pom.substring(dependency, Math.min(pom.length(), dependency + 200));
		assertFalse(declaration.contains("SNAPSHOT"));
		assertTrue(declaration.contains("1.0.2-20260905.234759-10"));
	}
}
