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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import de.esailors.jenkins.teststability.StabilityTestData.Result;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.logging.Logger;

/**
 * Circular history of test results.
 * <p>
 * Old records are dropped when <code>maxSize</code> is exceeded.
 * 
 * @author ckutz
 */
public class CircularStabilityHistory {
	
	  private Result[] data;
	  private int head; 
	  private int tail;
	  // number of elements in queue
      private int size = 0;

	  private transient static final Logger log = Logger.getLogger(CircularStabilityHistory.class.getName());

      private CircularStabilityHistory() {}
      
	  public CircularStabilityHistory(int maxSize) {
	    data = new Result[maxSize];
	    head = 0;
	    tail = 0;
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
	
	static {
		try {
			Jenkins.XSTREAM2.registerConverter(new ConverterImpl());
		} catch (Exception e) {
			log.severe(ExceptionUtils.getStackTrace(e));
		}
	}
	
	public static class ConverterImpl implements Converter {

		public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
			try {
				return CircularStabilityHistory.class.isAssignableFrom(type);
			} catch (Exception e) {
				log.severe(ExceptionUtils.getStackTrace(e));
			}
			return false;
		}

		public void marshal(Object source, HierarchicalStreamWriter writer,
				MarshallingContext context) {
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
				if(d == null) {
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

		public CircularStabilityHistory unmarshal(HierarchicalStreamReader r,
				UnmarshallingContext context) {
			
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
		
		private  Result[] stringToData(String s) {
			String[] split = s.split(",", -1);
			Result d[] = new Result[split.length];
			
			int i = 0;
			for(String testResult : split) {
				
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