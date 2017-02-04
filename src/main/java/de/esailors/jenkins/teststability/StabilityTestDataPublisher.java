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

import java.util.logging.*;

import javax.mail.MessagingException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.scm.ChangeLogSet;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.junit.CaseResult;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import de.esailors.jenkins.teststability.StabilityTestData.Result;

/**
 * {@link TestDataPublisher} for the test stability history.
 * 
 * @author ckutz, vincentballet (UIUC CS427)
 */
public class StabilityTestDataPublisher extends TestDataPublisher {

	public static final boolean DEBUG = false;
	private static final Logger myLog = Logger.getLogger(StabilityTestDataPublisher.class.getName());

	private boolean useMails = false;
	private String recipients = "";

	@DataBoundConstructor
	public StabilityTestDataPublisher(String recipients, boolean useMails) {
		this.recipients = recipients;
		this.useMails = useMails;
	}

	/**
	 * Getter for sending email notifications boolean
	 * 
	 * @return Boolean toggle for sending emails for build status
	 */
	public boolean getUseMails() {
		return useMails;
	}

	/**
	 * Getter for list of email addresses
	 * 
	 * @return List of whitespace separated email addresses for email
	 *         notifications
	 */
	public String getRecipients() {
		return recipients;
	}

	@Override
	/**
	 * Builds the circular stability history from previous histories and adds
	 * the current build data through the addResultToMap function. If the build
	 * was triggered from an SCM poll, it will check for and send email/text
	 * notifications to the specified list of recipients.
	 * 
	 * @param build
	 *            Object representing the current Jenkins build
	 * @param launcher
	 *            Object that starts a process and inherits environemtn
	 *            variables
	 * @param listener
	 *            Object that listens for build notifications
	 * @param testResult
	 *            Object that contains the root of all test results for one
	 *            build
	 * @return A new stability test data that contains information about the
	 *         build
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public Data getTestData(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, TestResult testResult)
			throws IOException, InterruptedException {

		Date date = new Date();

		Map<String, CircularStabilityHistory> stabilityHistoryPerTest = new HashMap<String, CircularStabilityHistory>();
		int maxHistoryLength = getDescriptor().getMaxHistoryLength();

		// Build the new CircularStabilityHistory
		addResultToMap(build.getNumber(), listener, stabilityHistoryPerTest, testResult, maxHistoryLength,
				date.getTime());

		ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = build.getChangeSet();
		if (changeSet != null && !changeSet.isEmptySet()) {
			Object item = changeSet.getItems()[0];
			String author = ((ChangeLogSet.Entry) item).getAuthor().getId();

			if (this.useMails) {
				RegressionReportNotifier rrNotifier = new RegressionReportNotifier();
				for (Map.Entry<String, CircularStabilityHistory> e : stabilityHistoryPerTest.entrySet()) {
					CircularStabilityHistory h = e.getValue();
					if (h != null && h.isMostRecentTestRegressed() && h.isShouldPublish()) {
						rrNotifier.addResult(e.getKey(), h);
					}
				}
				try {
					rrNotifier.mailReport(recipients, author, listener, build);
				} catch (MessagingException e) {
					myLog.log(Level.FINE, "MessagingException to be handled");
				}
			}
		}
		return new StabilityTestData(stabilityHistoryPerTest);
	}

	private CircularStabilityHistory addResultToMap(int buildNumber, BuildListener listener,
			Map<String, CircularStabilityHistory> stabilityHistoryPerTest, hudson.tasks.test.TestResult result,
			int maxHistoryLength, long currentTime) {

		// Commented Out by Forrest, Testing get test results history for all
		// passed tests
		CircularStabilityHistory history = getPreviousHistory(result);
		if (history == null) {
			history = new CircularStabilityHistory(maxHistoryLength);
			buildUpInitialHistory(history, result, maxHistoryLength - 1);
		}

		if (history != null) {
			if (result.isPassed()) {
				history.add(buildNumber, true);

				// A design decision that simply puts all null buffer for the
				// plugin to output 100% success and 0% flakiness
				if (history.isAllPassed()) {
					// Commented out by Forrest
					// history = null;
				}

			} else if (result.getFailCount() > 0) {
				history.add(buildNumber, false);
			}
			// else test is skipped and we leave history unchanged

			if ((result instanceof TabulatedResult)) {
				for (hudson.tasks.test.TestResult child : ((TabulatedResult) result).getChildren()) {
					CircularStabilityHistory childBuffer = addResultToMap(buildNumber, listener,
							stabilityHistoryPerTest, child, maxHistoryLength, currentTime);
					if (childBuffer != null) {
						history.addChild(childBuffer);
					}
				}
			}

			if (result instanceof CaseResult && ((CaseResult) result).getStatus() == CaseResult.Status.FIXED) {
				myLog.log(Level.FINE, "Name: " + result.getName());
				myLog.log(Level.FINE, "Status: " + ((CaseResult) result).getStatus());
				myLog.log(Level.FINE, "Error Trace: " + result.getErrorStackTrace());
			}

			history.setName(result.getName());
			if (result instanceof CaseResult) {
				history.setShouldPublish(true);
			}
			stabilityHistoryPerTest.put(result.getId(), history);

			return history;
		}

		return null;
	}

	private void debug(String msg, BuildListener listener) {
		if (StabilityTestDataPublisher.DEBUG) {
			listener.getLogger().println(msg);
		}
	}

	private CircularStabilityHistory getPreviousHistory(hudson.tasks.test.TestResult result) {
		hudson.tasks.test.TestResult previous = getPreviousResult(result);

		if (previous != null) {
			StabilityTestAction previousAction = previous.getTestAction(StabilityTestAction.class);
			if (previousAction != null) {
				CircularStabilityHistory prevHistory = previousAction.getRingBuffer();

				if (prevHistory == null) {
					return null;
				}

				// copy to new to not modify the old data
				CircularStabilityHistory newHistory = new CircularStabilityHistory(
						getDescriptor().getMaxHistoryLength());
				newHistory.addAll(prevHistory.getData());
				return newHistory;
			}
		}
		return null;
	}

	private boolean isFirstTestFailure(hudson.tasks.test.TestResult result,
			CircularStabilityHistory previousRingBuffer) {
		return previousRingBuffer == null && result.getFailCount() > 0;
	}

	private void buildUpInitialHistory(CircularStabilityHistory ringBuffer, hudson.tasks.test.TestResult result,
			int number) {
		List<Result> testResultsFromNewestToOldest = new ArrayList<Result>(number);
		hudson.tasks.test.TestResult previousResult = getPreviousResult(result);
		while (previousResult != null) {
			testResultsFromNewestToOldest
					.add(new Result(previousResult.getOwner().getNumber(), previousResult.isPassed()));
			previousResult = previousResult.getPreviousResult();
		}

		for (int i = testResultsFromNewestToOldest.size() - 1; i >= 0; i--) {
			ringBuffer.add(testResultsFromNewestToOldest.get(i));
		}
	}

	private hudson.tasks.test.TestResult getPreviousResult(hudson.tasks.test.TestResult result) {
		try {
			return result.getPreviousResult();
		} catch (RuntimeException e) {
			// there's a bug (only on freestyle builds!) that getPreviousResult
			// may throw a NPE (only for ClassResults!) in Jenkins 1.480
			// Note: doesn't seem to occur anymore in Jenkins 1.520
			// Don't know about the versions between 1.480 and 1.520

			// TODO: Untested:
			// if (result instanceof ClassResult) {
			// ClassResult cr = (ClassResult) result;
			// PackageResult pkgResult = cr.getParent();
			// hudson.tasks.test.TestResult topLevelPrevious =
			// pkgResult.getParent().getPreviousResult();
			// if (topLevelPrevious != null) {
			// if (topLevelPrevious instanceof TestResult) {
			// TestResult junitTestResult = (TestResult) topLevelPrevious;
			// PackageResult prvPkgResult =
			// junitTestResult.byPackage(pkgResult.getName());
			// if (pkgResult != null) {
			// return pkgResult.getClassResult(cr.getName());
			// }
			// }
			// }

			// }

			return null;
		}
	}

	private Collection<hudson.tasks.test.TestResult> getClassAndCaseResults(TestResult testResult) {
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
		return (DescriptorImpl) super.getDescriptor();
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TestDataPublisher> {

		private int maxHistoryLength = 30;

		@Override
		public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
			this.maxHistoryLength = json.getInt("maxHistoryLength");

			save();
			return super.configure(req, json);
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
