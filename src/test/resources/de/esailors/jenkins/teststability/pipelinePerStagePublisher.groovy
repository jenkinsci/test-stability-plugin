package de.esailors.jenkins.teststability
/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
 *
 */

// Publishes a disjoint slice of the build's tests from each of two junit calls, the way a
// pipeline that runs a suite per stage does. Jenkins appends one Data per junit call to the
// build's single TestResultAction, so this is what makes a test visible to a Data that never
// parsed it.
//
// The calls are not wrapped in stage(), because pipeline-stage-step is not on the test
// classpath and stages are not what drives this. Each junit call is its own FlowNode and
// contributes its own Data whether or not a stage encloses it.
node {
    junit testResults: 'reportA.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
    junit testResults: 'reportB.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
}
