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

import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.junit.CaseResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;

/**
 * {@link Data} for the test stability history.
 * 
 * @author ckutz
 */
@SuppressWarnings("deprecation")
class StabilityTestData extends Data {
	
	static {
		// TODO: this doesn't seem to work
		Jenkins.XSTREAM2.aliasType("circularStabilityHistory", CircularStabilityHistory.class);
	}
	
	private final Map<String,CircularStabilityHistory> stability;
	
	/**
	 * IDs of the tests that the {@code junit} step which produced this {@link Data}
	 * actually parsed.
	 * 
	 * <p>A build may run the {@code junit} step more than once, e.g. once per pipeline
	 * stage. Jenkins keeps a single {@code TestResultAction} per build and appends one
	 * {@link Data} per call to it, then concatenates the {@link TestAction}s of every
	 * {@link Data} when it renders a test. Without this set each {@link Data} answers
	 * for every test in the build, so a test gets annotated once per {@code junit}
	 * call: its real verdict plus a "No known failures" default from every other call.
	 * 
	 * <p>{@code null} for data serialized by an earlier release of this plugin, in
	 * which case we fall back to the previous build-wide behaviour.
	 */
	private final Set<String> coveredTestIds;
	
	public StabilityTestData(Map<String, CircularStabilityHistory> stabilityHistory, Set<String> coveredTestIds) {
		this.stability = stabilityHistory;
		this.coveredTestIds = coveredTestIds;
	}

	@Override
	public List<? extends TestAction> getTestAction(TestObject testObject) {
		
		if (testObject instanceof CaseResult || testObject instanceof ClassResult) {
			String id = testObject.getId();
			
			// A different junit() call in this build parsed this test, and its own Data
			// will annotate it. Stay quiet so the test is annotated exactly once.
			if (coveredTestIds != null && !coveredTestIds.contains(id)) {
				return Collections.emptyList();
			}
			
			// A null ring buffer means "nothing recorded against a test we did parse",
			// which StabilityTestAction renders as the "No known failures" verdict.
			CircularStabilityHistory ringBuffer = stability.get(id);
			return Collections.singletonList(new StabilityTestAction(ringBuffer));
		}
		
		return Collections.emptyList();
	}
	
	
	
	public static class Result {
		int buildNumber;
		boolean passed;
		
		public Result(int buildNumber, boolean passed) {
			super();
			this.buildNumber = buildNumber;
			this.passed = passed;
		}
	}
	
	
}
