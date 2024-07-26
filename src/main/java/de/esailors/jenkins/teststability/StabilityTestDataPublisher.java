/*
 * The MIT License
 * 
 * Copyright (c) 2013, eSailors IT Solutions GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.esailors.jenkins.teststability;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.junit.ClassResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import de.esailors.jenkins.teststability.StabilityTestData.Result;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@link TestDataPublisher} for the test stability history.
 * 
 * @author ckutz
 */
public class StabilityTestDataPublisher extends TestDataPublisher {
	
	public static final boolean DEBUG = false; 
	
	@DataBoundConstructor
	public StabilityTestDataPublisher() {
	}
	
	// param is top level TestResult for a build
	@Override
	public Data contributeTestData(Run<?, ?> run, @Nonnull FilePath workspace, Launcher launcher, TaskListener listener,
								   TestResult testResult) throws IOException, InterruptedException {

		Map<String,CircularStabilityHistory> stabilityHistoryPerTest = new HashMap<String,CircularStabilityHistory>();

		// NB: abstract TestResult
		Collection<hudson.tasks.test.TestResult> classAndCaseResults = getClassAndCaseResults(testResult);
		debug("Found " + classAndCaseResults.size() + " test results", listener);
		// NB: abstract TestResult
		for (hudson.tasks.test.TestResult result: classAndCaseResults) {
			
			CircularStabilityHistory history = getPreviousHistory(result);

			if (history != null) {
				if (result.isPassed()) {
					history.add(run.getNumber(), true);
					
					if (history.isAllPassed()) {
						history = null;
					}
					
				} else if (result.getFailCount() > 0) {
					history.add(run.getNumber(), false);
				}
				// else test is skipped and we leave history unchanged
				
				if (history != null) {
					stabilityHistoryPerTest.put(result.getId(), history);
				} else {
					stabilityHistoryPerTest.remove(result.getId());
				}
				// TODO perhaps it would be better to buildUpInitialHistory for passing tests too (after JENKINS-33168 is fixed)
			} else if (result.getFailCount() > 0) {
				// StabilityTestDataPublisher doesn't have a previous record of this failing test
				// (eg StabilityTestDataPublisher wasn't enabled when it last failed)
				debug("Found failed test " + result.getId(), listener);
				int maxHistoryLength = getDescriptor().getMaxHistoryLength();
				CircularStabilityHistory ringBuffer = new CircularStabilityHistory(maxHistoryLength);
				
				// add previous results (if there are any):
				buildUpInitialHistory(ringBuffer, result, maxHistoryLength - 1);
				
				ringBuffer.add(run.getNumber(), false);
				stabilityHistoryPerTest.put(result.getId(), ringBuffer);
			}
		}
		
		return new StabilityTestData(stabilityHistoryPerTest);
	}
	
	private void debug(String msg, TaskListener listener) {
		if (StabilityTestDataPublisher.DEBUG) {
			listener.getLogger().println(msg);
		}
	}

	// NB: abstract TestResult
	private CircularStabilityHistory getPreviousHistory(hudson.tasks.test.TestResult result) {
		// NB: abstract TestResult
		hudson.tasks.test.TestResult previous = getPreviousResultSafely(result);

		if (previous != null) {
			StabilityTestAction previousAction = previous.getTestAction(StabilityTestAction.class);
			if (previousAction != null) {
				CircularStabilityHistory prevHistory = previousAction.getRingBuffer();
				
				if (prevHistory == null) {
					return null;
				}
				
				// copy to new to not modify the old data
				CircularStabilityHistory newHistory = new CircularStabilityHistory(getDescriptor().getMaxHistoryLength());
				newHistory.addAll(prevHistory.getData());
				return newHistory;
			}
		}
		return null;
	}

	// NB: abstract TestResult
	private void buildUpInitialHistory(CircularStabilityHistory ringBuffer, hudson.tasks.test.TestResult result, int number) {
		List<Result> testResultsFromNewestToOldest = new ArrayList<Result>(number);
		// NB: abstract TestResult
		hudson.tasks.test.TestResult previousResult = getPreviousResultSafely(result);
		while (previousResult != null) {
			testResultsFromNewestToOldest.add(
					new Result(previousResult.getRun().getNumber(), previousResult.isPassed()));
			previousResult = previousResult.getPreviousResult();
		}

		for (int i = testResultsFromNewestToOldest.size() - 1; i >= 0; i--) {
			ringBuffer.add(testResultsFromNewestToOldest.get(i));
		}
	}


	// NB: abstract TestResult
	@SuppressFBWarnings(value = "DCN_NULLPOINTER_EXCEPTION", justification = "Needs investigation to see if a bug in JUnit or if something this plugin is doing")
	private @Nullable hudson.tasks.test.TestResult getPreviousResultSafely(hudson.tasks.test.TestResult result) {
		try {
			return result.getPreviousResult();
		} catch (NullPointerException e) {
			// there's a bug (only on freestyle builds!) that getPreviousResult may throw a NPE (only for ClassResults!) in Jenkins 1.480
			// Note: doesn't seem to occur anymore in Jenkins 1.520
			// Don't know about the versions between 1.480 and 1.520
			// Update: This also happens when running pipeline integration tests

			if (result instanceof ClassResult) {
				ClassResult classResult = (ClassResult) result;
				PackageResult pkgResult = classResult.getParent();
				TestResult topLevel = pkgResult.getParent();
				hudson.tasks.test.TestResult prevTopLevel = topLevel.getPreviousResult();
				if (prevTopLevel != null) {
					if (prevTopLevel instanceof TestResult) {
						TestResult prevJunitTestResult = (TestResult) prevTopLevel;
						PackageResult prevPkgResult = prevJunitTestResult.byPackage(pkgResult.getName());
						if (prevPkgResult != null) {
							return prevPkgResult.getClassResult(classResult.getName());
						}
					}
					// else something is weird if the previousResult has a different class!
				}
			}
			// TODO log something, or just throw e
			return null;
		}
	}

//	/**
//	 * TestResult.getPreviousResult() throws NPE when getParentAction() returns null (eg try running PipelineTest),
//	 * so this method emulates it but uses its own implementation of getParentAction().
//	 * @see hudson.tasks.test.TestResult#getPreviousResult()
//	 * @see hudson.tasks.test.TestResult#getParentAction()
//	 * @param testResult
//	 * @return
//	 */
//	// NB: abstract TestResult
//	private @Nullable hudson.tasks.test.TestResult getPreviousResult(hudson.tasks.test.TestResult testResult) {
//		@Nullable AbstractTestResultAction parentAction = getParentAction(testResult);
//		if (parentAction == null) {
//			// This probably shouldn't happen
//			return null;
//		}
//		Run<?,?> run = testResult.getRun();
//		if (run == null) {
//			// This probably shouldn't happen either
//			return null;
//		}
//		while (true) {
//			run = run.getPreviousBuild();
//			if (run == null) {
//				return null;
//			}
//			// get corresponding action for 'run'
//			AbstractTestResultAction r = run.getAction(parentAction.getClass());
//			if (r != null) {
//				hudson.tasks.test.TestResult result = r.findCorrespondingResult(testResult.getId());
//				if (result != null) {
//					return result;
//				}
//			}
//		}
//	}
//
//	private AbstractTestResultAction getParentAction(hudson.tasks.test.TestResult thisResult) {
//		if (thisResult instanceof PackageResult) {
//			return getParentAction((PackageResult) thisResult);
//		} else if (thisResult instanceof ClassResult) {
//			return getParentAction((ClassResult) thisResult);
//		} else if (thisResult instanceof CaseResult) {
//			return getParentAction((CaseResult) thisResult);
//		} else if (thisResult instanceof TestResult) {
//			return getParentAction((TestResult) thisResult);
//		} else {
//			// (just in case) probably SimpleCaseResult
//			return thisResult.getParentAction();
//		}
//	}
//
//	private AbstractTestResultAction getParentAction(CaseResult caseResult) {
//		return getParentAction(caseResult.getParent());
//	}
//
//	private AbstractTestResultAction getParentAction(ClassResult classResult) {
//		return getParentAction(classResult.getParent());
//	}
//
//	private AbstractTestResultAction getParentAction(PackageResult packageResult) {
//		return getParentAction(packageResult.getParent());
//	}
//
//	private AbstractTestResultAction getParentAction(hudson.tasks.junit.TestResult buildTestResult) {
//		return buildTestResult.getParentAction();
//	}

	// NB: param is top level TestResult for a build, returns abstract TestResults (classes and their cases)
	private Collection<hudson.tasks.test.TestResult> getClassAndCaseResults(TestResult testResult) {
		// NB: abstract TestResult
		List<hudson.tasks.test.TestResult> results = new ArrayList<hudson.tasks.test.TestResult>();
		
		Collection<PackageResult> packageResults = testResult.getChildren();
		for (PackageResult pkgResult : packageResults) {
			Collection<ClassResult> classResults = pkgResult.getChildren();
			for (ClassResult cr : classResults) {
				results.add(cr);
				results.addAll(cr.getChildren());
			}
		}

		return results;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
	

	@Extension
	public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
		
		private int maxHistoryLength = 30;

		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			this.maxHistoryLength = json.getInt("maxHistoryLength");
			
			save();
            return super.configure(req,json);
		}
		
		public int getMaxHistoryLength() {
			return this.maxHistoryLength;
		}

		@Override
		public String getDisplayName() {
			return "Test stability history";
		}
	}
}
