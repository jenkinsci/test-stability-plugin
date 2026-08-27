package de.esailors.jenkins.teststability;

import hudson.model.Actionable;
import hudson.model.FreeStyleBuild;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResultAction;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Helper {

    static TestResultAction testResult(Actionable build) {
        TestResultAction action = build.getAction(TestResultAction.class);
        assertThat(action).isNotNull();
        return action;
    }

    static ClassResult getClassResult(TestResultAction action, String packageName, String className) {
        return action.getResult().byPackage(packageName).getClassResult(className);
    }

    // Every StabilityTestAction attached to a test, rather than just the first one that
    // getTestAction(Class) would return. A test must carry exactly one however many times
    // the build called the junit step.
    static List<StabilityTestAction> stabilityActionsOf(TestObject testObject) {
        List<StabilityTestAction> actions = new ArrayList<>();
        for (TestAction action : testObject.getTestActions()) {
            if (action instanceof StabilityTestAction) {
                actions.add((StabilityTestAction) action);
            }
        }
        return actions;
    }

    // If this is the first build, or if all previous builds have had exactly the same test results,
    // then successful tests have stability 100, and failing tests have stability 0.
    static void expectConsistentMixedResults(TestResultAction testResultAction) {
        ClassResult classResult1 = getClassResult(testResultAction, "test.foo.bar", "DefaultIntegrationTest");

        StabilityTestAction stability1 = classResult1.getTestAction(StabilityTestAction.class);
        assertThat(stability1).isNotNull();
        assertThat(stability1.getStability()).isEqualTo(100);
        assertThat(stability1.getFlakiness()).isEqualTo(0);

        ClassResult classResult2 = getClassResult(testResultAction, "test.foo.bar", "ProjectSettingsTest");

        StabilityTestAction stability2 = classResult2.getTestAction(StabilityTestAction.class);
        assertThat(stability2).isNotNull();
        assertThat(stability2.getStability()).isEqualTo(0);
        assertThat(stability2.getFlakiness()).isEqualTo(0);
    }

    static void expectSuccessAfter2MixedResults(TestResultAction testResultAction) {
        ClassResult classResult1 = getClassResult(testResultAction, "test.foo.bar", "DefaultIntegrationTest");

        StabilityTestAction stability1 = classResult1.getTestAction(StabilityTestAction.class);
        assertThat(stability1).isNotNull();
        assertThat(stability1.getStability()).isEqualTo(100);
        assertThat(stability1.getFlakiness()).isEqualTo(0);

        ClassResult classResult2 = getClassResult(testResultAction, "test.foo.bar", "ProjectSettingsTest");

        StabilityTestAction stability2 = classResult2.getTestAction(StabilityTestAction.class);
        assertThat(stability2).isNotNull();
        // 2 failures followed by 1 success: 33% stability, 50% flakiness
        assertThat(stability2.getStability()).isEqualTo(33);
        assertThat(stability2.getFlakiness()).isEqualTo(50);
    }

    static void expectMixedResultsAfterSuccess(TestResultAction testResultAction) {
        ClassResult classResult1 = getClassResult(testResultAction, "test.foo.bar", "DefaultIntegrationTest");

        StabilityTestAction stability1 = classResult1.getTestAction(StabilityTestAction.class);
        assertThat(stability1).isNotNull();
        assertThat(stability1.getStability()).isEqualTo(100);
        assertThat(stability1.getFlakiness()).isEqualTo(0);

        ClassResult classResult2 = getClassResult(testResultAction, "test.foo.bar", "ProjectSettingsTest");

        StabilityTestAction stability2 = classResult2.getTestAction(StabilityTestAction.class);
        assertThat(stability2).isNotNull();
        // 1 success followed by 1 failure: 33% stability, 100% flakiness
        assertThat(stability2.getStability()).isEqualTo(50);
        assertThat(stability2.getFlakiness()).isEqualTo(100);
    }

}
