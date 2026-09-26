package com.bencodez.advancedcore.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class BuildInputPinningTest {

	@Test
	void javadocPublisherUsesImmutableRevisionAndExplicitPermissions() throws IOException {
		String workflow = Files.readString(Path.of("..", ".github", "workflows", "publish-javadoc.yml"));
		String releaseEligibilityJob = job(workflow, "release-eligibility");
		String buildJob = job(workflow, "build");
		String deployJob = job(workflow, "deploy");

		assertFalse(workflow.contains("MathieuSoysal/Javadoc-publisher"));
		assertTrue(workflow.matches("(?s).*actions/checkout@[0-9a-f]{40}.*"));
		assertTrue(workflow.matches("(?s).*actions/setup-java@[0-9a-f]{40}.*"));
		assertTrue(workflow.matches("(?s).*actions/deploy-pages@[0-9a-f]{40}.*"));
		assertTrue(buildJob.contains("contents: read"));
		assertFalse(buildJob.contains("pages: write"));
		assertFalse(buildJob.contains("id-token: write"));
		assertTrue(deployJob.contains("pages: write"));
		assertTrue(deployJob.contains("id-token: write"));
		assertTrue(releaseEligibilityJob.contains("git merge-base --is-ancestor \"$tag_commit\" origin/master"));
		assertTrue(releaseEligibilityJob.contains("commit: ${{ steps.eligibility.outputs.commit }}"));
		assertTrue(releaseEligibilityJob.contains("echo \"commit=$tag_commit\" >> \"$GITHUB_OUTPUT\""));
		assertFalse(workflow.contains("target_commitish"));
		assertTrue(buildJob.contains("needs: release-eligibility"));
		assertTrue(buildJob.contains("needs.release-eligibility.outputs.eligible == 'true'"));
		assertTrue(buildJob.contains("ref: ${{ needs.release-eligibility.outputs.commit }}"));
		assertTrue(deployJob.contains("needs: [release-eligibility, build]"));
		assertTrue(deployJob.contains("needs.release-eligibility.outputs.eligible == 'true'"));
		assertTrue(workflow.contains("maven-javadoc-plugin:3.12.0:javadoc"));
		assertFalse(workflow.contains("Javadoc-publisher.yml@main"));
	}

	@Test
	void dependencySubmissionCheckoutDoesNotPersistWriteScopedCredentials() throws IOException {
		String workflow = Files.readString(Path.of("..", ".github", "workflows", "maven.yml"));
		String dependencySubmissionJob = job(workflow, "dependency-submission");

		assertTrue(dependencySubmissionJob.contains("contents: write"));
		assertTrue(dependencySubmissionJob.contains("persist-credentials: false"));
	}

	private static String job(String workflow, String name) {
		Matcher matcher = Pattern.compile("(?ms)^  " + Pattern.quote(name) + ":\\R(?<job>.*?)(?=^  [A-Za-z0-9_-]+:\\R|\\z)")
				.matcher(workflow);
		assertTrue(matcher.find(), () -> "Missing " + name + " job");
		return matcher.group("job");
	}
}
