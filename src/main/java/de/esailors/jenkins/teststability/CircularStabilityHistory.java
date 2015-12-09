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

import jenkins.model.Jenkins;
import java.util.*;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import de.esailors.jenkins.teststability.StabilityTestData.Result;

/**
 * Circular history of test results.
 * <p>
 * Old records are dropped when <code>maxSize</code> is exceeded.
 * 
 * @author ckutz, vincentballet (UIUC CS427)
 */
public class CircularStabilityHistory {

	private Result[] data;
	private int head;
	private int tail;
	// number of elements in queue
	private int size = 0;
	private int failed = 0;
	private int testStatusChanges = 0;
	private String name = "";

	private boolean shouldPublish = false;

	private int flakiness;
	private int stability;

	private Set<CircularStabilityHistory> children = new HashSet<CircularStabilityHistory>();
	private CircularStabilityHistory parent = null;

	private CircularStabilityHistory() {
	}

	public void updateResultForChildren() {
		int oldTail = tail - 1;
		if (tail == 0) {
			oldTail = data.length - 1;
		}
		if (children.isEmpty()) {
			return;
		}
		boolean newResult = true;

		data[oldTail].passed = newResult;
	}

	public CircularStabilityHistory(int maxSize) {
		flakiness = 0;
		stability = 100;
		data = new Result[maxSize];
		head = 0;
		tail = 0;
	}

	public boolean isMostRecentTestRegressed() {
		if (size < 2) {
			return false;
		}

		int oldTail = (tail - 1 + this.data.length) % this.data.length;
		int oldPrevTail = (oldTail - 1 + this.data.length) % this.data.length;

		return (this.data[oldPrevTail].passed && !this.data[oldTail].passed);
	}

	public String getName() {
		return name;
	}

	public void setName(String newName) {
		name = newName;
	}

	public boolean isShouldPublish() {
		return this.shouldPublish;
	}

	public void setShouldPublish(boolean shouldPublish) {
		this.shouldPublish = shouldPublish;
	}

	public int getSize() {
		return size;
	}

	public boolean add(Result value) {
		data[tail] = value;
		tail++;
		if (tail == data.length) {
			tail = 0;
		}

		if (size == data.length) {
			head = (head + 1) % data.length;
		} else {
			size++;
		}
		return true;
	}

	public Result[] getData() {
		Result[] copy = new Result[size];

		for (int i = 0; i < size; i++) {
			copy[i] = data[(head + i) % data.length];
		}
		return copy;
	}

	public boolean isEmpty() {
		return data.length == 0;
	}

	public int getMaxSize() {
		return this.data.length;
	}

	private void computeStability() {
		this.failed = 0;

		for (Result r : this.getData()) {
			if (!r.passed) {
				this.failed++;
			}
		}

		this.stability = 100 * (size - failed) / (size == 0 ? 1 : size);
	}

	/**
	 * Computes the flakiness in percent. (Is this really flakiness? Doesn't
	 * check if code changed or not, but this is a problem for later)
	 */
	private void computeFlakiness() {
		Boolean previousPassed = null;

		this.testStatusChanges = 0;

		for (Result r : this.getData()) {
			boolean thisPassed = r.passed;
			if (previousPassed != null && previousPassed != thisPassed) {
				this.testStatusChanges++;
			}
			previousPassed = thisPassed;
		}

		if (size > 1) {
			this.flakiness = 100 * testStatusChanges / (size - 1);
		} else {
			this.flakiness = 0;
		}
	}

	/*
	 * Returns the flakiness in percent
	 */
	public int getFlakiness() {
		computeFlakiness();
		return this.flakiness;
	}

	public int getStability() {
		computeStability();
		return this.stability;
	}

	public int getFailed() {
		this.failed = 0;

		for (Result r : this.getData()) {
			if (!r.passed) {
				this.failed++;
			}
		}

		return this.failed;
	}

	/*
	 * Getter function for children
	 */
	public Set<CircularStabilityHistory> getChildren() {
		return children;
	}

	public CircularStabilityHistory getParent() {
		return parent;
	}

	public void addChild(CircularStabilityHistory newChild) {
		if (children.add(newChild)) {
			newChild.parent = this;
		}
	}

	// Spike test : remove children due to filter
	public void removeChild(String childName) {
		for (CircularStabilityHistory child : children) {
			if ((child.name).equals(childName)) {
				children.remove(child);
			}
		}
	}

	public CircularStabilityHistory getFlakiestDecendent() {
		CircularStabilityHistory flakiestChild = getFlakiestChild();

		if (flakiestChild != null) {
			CircularStabilityHistory flakiestDec = flakiestChild.getFlakiestDecendent();

			if (flakiestDec != null) {
				return flakiestDec;
			} else {
				return flakiestChild;
			}
		} else {
			return null;
		}
	}

	/*
	 * Returns the most flaky child
	 */
	public CircularStabilityHistory getFlakiestChild() {
		CircularStabilityHistory flakiestChild = null;

		// myLog.log(Level.FINE, "Finding flakiest child of " + this.getName());

		for (CircularStabilityHistory child : children) {
			// myLog.log(Level.FINE, "Checking flakiness of child " +
			// child.getName());
			if (flakiestChild == null || flakiestChild.getFlakiness() < child.getFlakiness()) {
				flakiestChild = child;
			}
		}

		return flakiestChild;
	}

	/*
	 * Returns the least stable decendent
	 */
	public CircularStabilityHistory getLeastStableDecendent() {
		return getLeastStableChild();
	}

	/*
	 * Returns the least stable child
	 */
	public CircularStabilityHistory getLeastStableChild() {
		CircularStabilityHistory leastStableChild = null;

		for (CircularStabilityHistory child : children) {
			if (leastStableChild == null || leastStableChild.getStability() > child.getStability()) {
				leastStableChild = child;
			}
		}

		return leastStableChild;
	}

	static {
		Jenkins.XSTREAM2.registerConverter(new ConverterImpl());
	}

	public static class ConverterImpl implements Converter {

		public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
			return CircularStabilityHistory.class.isAssignableFrom(type);
		}

		public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
			CircularStabilityHistory b = (CircularStabilityHistory) source;

			writer.startNode("head");
			writer.setValue(Integer.toString(b.head));
			writer.endNode();

			writer.startNode("tail");
			writer.setValue(Integer.toString(b.tail));
			writer.endNode();

			writer.startNode("size");
			writer.setValue(Integer.toString(b.size));
			writer.endNode();

			writer.startNode("data");
			writer.setValue(dataToString(b.data));
			writer.endNode();
		}

		private String dataToString(Result[] data) {
			StringBuilder buf = new StringBuilder();
			for (Result d : data) {
				if (d == null) {
					buf.append(",");
					continue;
				}
				if (d.passed) {
					buf.append(d.buildNumber).append(";").append("1,");
				} else {
					buf.append(d.buildNumber).append(";").append("0,");
				}
			}

			if (buf.length() > 0) {
				buf.deleteCharAt(buf.length() - 1);
			}

			return buf.toString();
		}

		public CircularStabilityHistory unmarshal(HierarchicalStreamReader r, UnmarshallingContext context) {

			r.moveDown();
			int head = Integer.parseInt(r.getValue());
			r.moveUp();

			r.moveDown();
			int tail = Integer.parseInt(r.getValue());
			r.moveUp();

			r.moveDown();
			int size = Integer.parseInt(r.getValue());
			r.moveUp();

			r.moveDown();
			String data = r.getValue();
			r.moveUp();

			CircularStabilityHistory buf = new CircularStabilityHistory();
			Result[] b = stringToData(data);

			buf.data = b;
			buf.head = head;
			buf.size = size;
			buf.tail = tail;

			return buf;
		}

		private Result[] stringToData(String s) {
			String[] split = s.split(",", -1);
			Result d[] = new Result[split.length];

			int i = 0;
			for (String testResult : split) {

				if (testResult.isEmpty()) {
					i++;
					continue;
				}

				String[] split2 = testResult.split(";");
				int buildNumber = Integer.parseInt(split2[0]);

				// TODO: check that '0' is the only other allowed value:
				boolean buildResult = "1".equals(split2[1]) ? true : false;

				d[i] = new Result(buildNumber, buildResult);

				i++;
			}

			return d;
		}

	}

	public void addAll(Result[] results) {
		for (Result b : results) {
			add(b);
		}
	}

	public void add(int buildNumber, boolean passed) {
		add(new Result(buildNumber, passed));
	}

	public boolean isAllPassed() {

		if (size == 0) {
			return true;
		}

		for (Result r : data) {
			if (r != null && !r.passed) {
				return false;
			}
		}

		return true;
	}

}