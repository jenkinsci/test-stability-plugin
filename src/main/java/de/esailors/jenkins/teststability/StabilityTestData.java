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
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.PackageResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
	
	public StabilityTestData(Map<String, CircularStabilityHistory> stabilityHistory) {
		this.stability = stabilityHistory;
	}

	@Override
	public List<? extends TestAction> getTestAction(TestObject testObject) {
		
		if (testObject instanceof CaseResult || testObject instanceof ClassResult || testObject instanceof PackageResult || testObject instanceof TestResult) {
			CircularStabilityHistory ringBuffer = stability.get(testObject.getId());
				StabilityTestAction action = new StabilityTestAction(ringBuffer);
				return Collections.singletonList(action);
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
