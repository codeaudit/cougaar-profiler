/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 *
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package org.cougaar.profiler.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.cougaar.core.servlet.ComponentServlet;
import org.cougaar.profiler.ClassStats;
import org.cougaar.profiler.ClassTracker;
import org.cougaar.profiler.Comparators;
import org.cougaar.profiler.Groupings;
import org.cougaar.profiler.InstanceStats;
import org.cougaar.profiler.MemoryStats;
import org.cougaar.profiler.MemoryStatsImpl;
import org.cougaar.profiler.Options;

/**
 * Servlet to view MemoryTracker data.
 * <p>
 * ComponentServlet extends HttpServlet and simplifies loading
 * within Cougaar agents.  Non-Cougaar developers can easily
 * replace the base class with plain HttpServlet.
 */
public class ProfilerServlet
extends ComponentServlet
{

  protected String getPath() {
    return "/profiler";
  }

  public void doGet(
      HttpServletRequest req,
      HttpServletResponse res) throws IOException {
    // create a new handler per request, so we don't mangle our
    // per-request variables
    String name = getEncodedAgentName();
    MemoryStats memoryStats = MemoryStatsImpl.getInstance();
    MyHandler h = new MyHandler(name, memoryStats);
    h.execute(req, res);
  }

  private static class MyHandler {

    // Values of servlet parameters
    private static final String REQ_ACTION_SCRIPT = "script";
    private static final String REQ_ACTION_TYPE = "type";
    private static final String REQ_ACTION_INSTANCES = "instances";

    // Names of servlet parameters
    private static final String REQ_ACTION = "action";
    private static final String REQ_GC = "gc";
    private static final String REQ_TYPE = "type";
    private static final String REQ_INCREASING = "inc";
    private static final String REQ_SORT = "sort";
    private static final String REQ_SAMPLE = "sample";
    private static final String REQ_ROWS = "rows";
    private static final String REQ_STACK_LINES = "lines";
    private static final String REQ_TO_STRING_ENABLE = "stringEnable";
    private static final String REQ_TO_STRING_LIMIT = "stringLimit";

    /* Skip the first 5 stackframes, since they are within the
     * profiler:
     */
    private static final int STACK_LINES_TO_SKIP = 5;

    private final String name;
    private final MemoryStats memoryStats;

    private HttpServletRequest req;
    private PrintWriter out;

    private String action;
    private boolean gc;
    private String type;
    private boolean increasing;
    private String sort;
    private int sample;
    private int rows;
    private int stackLines;
    private int toStringLimit;

    public MyHandler(String name, MemoryStats memoryStats) {
      this.name = name;
      this.memoryStats = memoryStats;
    }

    public void execute(
        HttpServletRequest sreq,
        HttpServletResponse res) throws IOException {

      this.req = sreq;

      parseParams();

      if (REQ_ACTION_SCRIPT.equals(action)) {
        res.setContentType("text/javascript");
        out = res.getWriter();
        out.print(JavascriptTableSort.SOURCE);
        out.flush();
        out.close();
        return;
      }

      res.setContentType("text/html");
      out = res.getWriter();

      Date date = new Date();
      out.println(
          "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">"+
          "<html>"+
          "<head>"+
          "<title>"+name+" Memory Profiler</title>\n"+
          "<script LANGUAGE=\"JavaScript\" SRC=\""+
          req.getRequestURI()+
          "?"+REQ_ACTION+
          "="+REQ_ACTION_SCRIPT+
          "\"></script>\n"+
          "</head>"+
          "<body>"+
          "<h2>"+name+" Memory Profiler</h2>"+
          "<p>"+
          "Date: "+
          date+
          " ("+date.getTime()+")"+
          "<p>");

      try {
        if (REQ_ACTION_INSTANCES.equals(action)) {
          printInstances();
        } else if (REQ_ACTION_TYPE.equals(action)) {
          printType();
        } else {
          printAll();
        }
      } catch (Exception e) {
        out.println("<font color=red><pre>Error: "+e);
        e.printStackTrace(out);
        out.println("</pre></font>");
      }

      out.println("</body></html>");
      out.flush();
      out.close();
    }

    private boolean getBoolean(String name, boolean def) {
      String s = (String) req.getParameter(name);
      return (s == null ? def : "true".equals(s));
    }
    private int getInt(String name, int def) {
      String s = (String) req.getParameter(name);
      return (s == null ? def : Integer.parseInt(s));
    }
    private String getString(String name, String def) {
      String s = req.getParameter(name);
      return (s == null ? def : s);
    }
    private void parseParams() {
      action = getString(REQ_ACTION, null);
      gc = getBoolean(REQ_GC, false);
      type = getString(REQ_TYPE, null);

      if (REQ_ACTION_INSTANCES.equals(action)) {
        increasing = getBoolean(REQ_INCREASING, false);
        sort = getString(REQ_SORT, null);
        sample = getInt(REQ_SAMPLE, 0);
        rows = getInt(REQ_ROWS, 0);
        stackLines = getInt(REQ_STACK_LINES, 0);
        toStringLimit = 
          (getBoolean(REQ_TO_STRING_ENABLE, false) ?
           getInt(REQ_TO_STRING_LIMIT, -1) :
           -1);
      }
    }

    private void printGC() {
      if (gc) {
        Runtime.getRuntime().gc();
      }

      Runtime rt = Runtime.getRuntime();
      long free = rt.freeMemory();
      long total = rt.totalMemory();
      long used = (total - free);
      long max = rt.maxMemory();

      out.println(
          "<form action=\""+
          req.getRequestURI()+
          "\" method =\"get\">\n"+
          "<input type=hidden name="+
          REQ_ACTION+
          " value="+
          action+">\n"+
          "<input type=hidden name="+
          REQ_TYPE+
          " value="+
          type+">\n"+
          "<input type=hidden name="+
          REQ_GC+
          " value=\"true\">\n"+
          "<table border=\"2\">\n"+
          "Memory in Megabytes:"+
          "<tr><th>Used</th><th>Free</th>"+
          "<th>Total</th><th>Max</th>"+
          "<td rowspan=2><input type=\"submit\" value=\"Force GC\"/>"+
          "</td>"+
          "</tr>\n"+
          "<tr><td align=right>"+
          getMegabytes(used)+
          "</td><td align=right>"+
          getMegabytes(free)+
          "</td><td align=right>"+
          getMegabytes(total)+
          "</td><td align=right>"+
          getMegabytes(max)+
          "</td></tr>\n"+
          "</table>\n"+
          "</form>\n");
    }

    private void printAll() throws IOException {
      printGC();
      beginTable(false);

      String[] classes = memoryStats.getClassNames();
      Arrays.sort(classes);
      int n = classes.length;
      for (int i = 0; i < n; i++) {
        String cl = classes[i];
        ClassTracker ct = memoryStats.getClassTracker(cl);
        //ct.update();
        ClassStats cs = ct.getOverallStats();
        double trackRatio = ct.getOptions().getSampleRatio();
        int bytes = ct.getObjectSize();
        printType(null, cs, cl, trackRatio, bytes, true);
      }

      endTable(false);
    }

    private void beginTable(boolean showAgent) {
      out.println(
          "<table border=\"2\">\n"+
          "<thead>\n"+
          "<tr>");
      int i = 0;
      if (showAgent) {
        out.println(
            "<th rowspan=2>"+tableColumn("Agent", i++)+"</th>");
      }
      out.println(
          "<th rowspan=2>"+tableColumn("Type", i++)+"</th>"+
          "<th rowspan=2>"+tableColumn("Sample%", i++)+"</th>"+
          "<th colspan=3>Instances</th>"+
          "<th colspan=3>Memory</th>"+
          "<th colspan=4>Size</th>"+
          "<th colspan=4>Capacity</th>"+
          "</tr>\n"+
          "<tr>"+
          "<th>"+tableColumn("Live", i++)+"</th>"+
          "<th>"+tableColumn("GC'd", i++)+"</th>"+
          "<th>"+tableColumn("Total", i++)+"</th>"+
          "<th>"+tableColumn("Bytes Each", i++)+"</th>"+
          "<th>"+tableColumn("*Live", i++)+"</th>"+
          "<th>"+tableColumn("+Capacity Bytes", i++)+"</th>"+
          "<th>"+tableColumn("Sum", i++)+"</th>"+
          "<th>"+tableColumn("Max", i++)+"</th>"+
          "<th>"+tableColumn("Max Ever", i++)+"</th>"+
          "<th>"+tableColumn("Mean", i++)+"</th>"+
          "<th>"+tableColumn("Sum", i++)+"</th>"+
          "<th>"+tableColumn("Max", i++)+"</th>"+
          "<th>"+tableColumn("Max Ever", i++)+"</th>"+
          "<th>"+tableColumn("Mean", i++)+"</th>"+
          "</tr>\n"+
          "</thead>\n"+
          "<tbody id=\"tbl\">");
    }
    private static String tableColumn(String colname, int i) {
      return JavascriptTableSort.sortLink("tbl", colname, i, false);
    }
    private void endTable(boolean showAgent) {
      out.println(
          "</tbody>\n"+
          "</table>");
    }

    private void printType(
        String agent,
        ClassStats cs,
        String cl,
        double trackRatio,
        int bytes,
        boolean link) {
      out.print("<tr align=right><td align=left>");
      if (link) {
        out.print(
            "<a href='"+req.getRequestURI()+
            "?"+REQ_ACTION+"="+REQ_ACTION_TYPE+
            "&"+REQ_TYPE+"="+cl+
            "'>");
      }
      out.print(cl);
      if (link) {
        out.print("</a>");
      }
      long live = cs.getInstances();
      long dead = cs.getGarbageCollected();
      long cap = cs.getSumCapacityBytes();
      long sumSize = cs.getSumSize();
      long sumCap = cs.getSumCapacityCount();
      if (trackRatio < 1.0) {
        if (trackRatio > 0.0) {
          live = (long) ((double) live / trackRatio);
          dead = (long) ((double) dead / trackRatio);
          cap = (long) ((double) cap / trackRatio);
          sumSize = (long) ((double) sumSize / trackRatio);
          sumCap = (long) ((double) sumCap / trackRatio);
        } else {
          live = 0;
          dead = 0;
          cap = 0;
          sumSize = 0;
          sumCap = 0;
        }
      }
      double meanSize =
        (live > 0 ? ((double) sumSize / live) : 0.0);
      double meanCap =
        (live > 0 ? ((double) sumCap / live) : 0.0);
      out.print(
          "</td><td>"+
          format(100.0*trackRatio)+"</td><td>"+
          live+"</td><td>"+
          dead+"</td><td>"+
          (live + dead)+"</td><td>"+
          bytes+"</td><td>"+
          (live * bytes)+"</td><td>"+
          ((live * bytes)+cap)+"</td><td>"+
          sumSize+"</td><td>"+
          cs.getMaximumSize()+"</td><td>"+
          cs.getMaximumEverSize()+"</td><td>"+
          format(meanSize)+"</td><td>"+
          sumCap+"</td><td>"+
          cs.getMaximumCapacityCount()+"</td><td>"+
          cs.getMaximumEverCapacityCount()+"</td><td>"+
          format(meanCap)+"</td></tr>\n");
    }

    private void printType() {
      printGC();

      // update and show latest stats
      ClassTracker ct = memoryStats.getClassTracker(type);
      if (ct == null) {
        out.println(
            "<font color=red>Unknown type: "+
            type+"</font>");
        return;
      }
      ct.update();
      ClassStats cs = ct.getOverallStats();
      int bytes = ct.getObjectSize();
      Options options = ct.getOptions();
      double trackRatio = options.getSampleRatio();

      String[] agents = ct.getAgentNames();
      int numAgents = (agents == null ? 0 : agents.length);
      boolean hasAgent = (numAgents > 0);

      beginTable(hasAgent);
      printType(
          (hasAgent ? "*" : null),
          cs, type, trackRatio, bytes, false);
      for (int i = 0; i < numAgents; i++) {
        String agent = agents[i];
        ClassStats acs = ct.getAgentStats(agent);
        printType(agent, acs, type, trackRatio, bytes, false);
      }
      endTable(hasAgent);

      // create form to see instances
      //
      // we use "get" instead of "post" to allow redirects.
      // See the Cougaar Developer's Guide for details. 
      out.println(
          "<form action=\""+
          req.getRequestURI()+
          "\" method =\"get\">\n"+
          "<input type=hidden name="+
          REQ_TYPE+
          " value="+
          type+
          ">"+
          "<input type=hidden name="+
          REQ_ACTION+
          " value="+
          REQ_ACTION_INSTANCES+
          ">");

      // instance view options:
      out.println(
          "<p>"+
          "<i>Sort by:</i>"+
          "<select name=\""+REQ_INCREASING+"\">"+
          "  <option selected value=\"false\">decreasing</option>"+
          "  <option value=\"true\">increasing</option>"+
          "</select>"+
          "<select name=\""+REQ_SORT+"\">");
      String[] comp_names = Comparators.getNames(options);
      for (int i = 0; i < comp_names.length; i++) {
        String s = comp_names[i];
        out.println(
            "  <option"+
            (i == 0 ? " selected" : "")+
            ">"+s+"</option>");
      }
      String[] group_names = Groupings.getNames("uniq_", options);
      for (int i = 0; i < group_names.length; i++) {
        String s = group_names[i];
        out.println(
            "  <option"+
            ((i == 0 && comp_names.length == 0) ?
             " selected" :
             "")+
            ">"+s+"</option>");
      }
      out.println(
          "  <option>none</option>"+
          "</select>"+
          "<br>\n"+
          "<i>Sample (-1 is all):</i>"+
          "<input name=\""+REQ_SAMPLE+
          "\" type=\"text\" value=\"1000\">"+
          "<br/>\n"+
          "<i>Number of rows:</i>"+
          "<input name=\""+REQ_ROWS+
          "\" type=\"text\" value=\"20\"><br/>");
      if (options.isStackEnabled()) {
        out.println(
            "<i>Number of lines in stack trace:</i>"+
            "<input name=\""+REQ_STACK_LINES+
            "\" type=\"text\" value=\"8\"><br/>");
      }
      out.println(
          "<i>Show toString:</i>"+
          "<input type=\"checkbox\""+
          " name=\""+REQ_TO_STRING_ENABLE+
          "\" value=\"true\">"+
          "<input name=\""+REQ_TO_STRING_LIMIT+
          "\" type=\"text\" value=\"1000\"><br/>"+
          "<input type=\"submit\" value=\"Submit\"/>"+
          "<br/>\n"+
          "</form>");
    }

    private void printInstances() {
      ClassTracker ct = memoryStats.getClassTracker(type);
      if (ct == null) {
        out.println(
            "<font color=red>Unknown type: "+
            type+"</font>");
        return;
      }

      // force an update, get the per-element stats
      InstanceStats[] iss = ct.update();
      int total = (iss == null ? 0 : iss.length);

      out.println(
          "Showing <code>"+type+"</code>'s"+
          (sort == null ?
           "" :
           (" by "+
            (increasing ? "increasing" : "decreasing")+
            " "+
            sort))+
          "<p/>");

      double trackRatio = ct.getOptions().getSampleRatio();
      out.println(
          "Tracked "+
          (trackRatio < 1.0 ?
           format(100.0*trackRatio) :
           "100.0")+
          "% of allocations<br/>");

      out.println(
          "Found "+total+" live instances"+
          (trackRatio < 1.0 ?
           ", estimated at "+
           (trackRatio > 0.0 ?
            format((double) total/trackRatio) :
            "<i>0</i>") :
           "")+
          "<br/>");

      double multiplier = -1.0;
      if (sample >= 0 && total > sample) {
        // randomly sample from the array
        InstanceStats[] tmp = new InstanceStats[sample]; 
        Random rand = new Random();
        for (int i = 0; i < sample; i++) {
          InstanceStats is;
          int j = rand.nextInt(sample);
          while (true) {
            is = iss[j];
            if (is != null) {
              iss[j] = null;
              break;
            }
            j++;
            if (j >= total) {
              j = 0;
            }
          }
          tmp[i] = is;
        }
        double ratio = (total > 0 ? ((double) sample / total) : 1.0);
        out.println(
            "Randomly sampled "+sample+" of "+total+
            " = "+format(100.0*ratio)+"%<br/>");
        iss = tmp;
        multiplier = (sample > 0 ? ((double) total / sample) : 0.0);
        total = sample;
      }

      if (trackRatio < 1.0) {
        if (trackRatio > 0.0) {
          if (multiplier > 0.0) {
            multiplier /= trackRatio;
          } else {
            multiplier = (1.0/trackRatio);
          }
        } else {
          multiplier = 0.0;
        }
      }

      if (sort != null && sort.startsWith("uniq_")) {
        String group = sort.substring(5);
        Groupings.Count[] counts = Groupings.uniq(iss, group);
        int ncounts = (counts == null ? 0 : counts.length);
        int lines = Math.min(rows, ncounts);
        double ratio = 
          (ncounts > 0 ? ((double) lines / ncounts) : 0.0);
        out.println(
            "Displaying groups "+
            lines+" of "+ncounts+
            " = "+format(100.0*ratio)+"%<p/>");
        Comparators.sort(
            counts, increasing, Comparators.GROUP_COUNT);
        printCounts(counts, group, multiplier);
      } else {
        int lines = Math.min(rows, total);
        double ratio = 
          (total > 0 ? ((double) lines / total) : 0.0);
        out.println(
            "Displaying instances "+
            lines+" of "+total+
            " = "+format(100.0*ratio)+"%<p/>");
        Comparators.sort(iss, increasing, sort);
        printInstances(iss);
      }
    }

    private void printCounts(
        Groupings.Count[] counts,
        String group,
        double multiplier) {
      boolean hasMultiplier = (multiplier > 0.0);
      out.println(
          "<table align=\"center\" border=\"2\">"+
          "<tr>"+
          (hasMultiplier ? 
           "<th>Sampled</th>"+
           "<th>Estimated Count</th>" :
           "<th>Count</th>")+
          "<th>Value</th>"+
          "</tr>");
      int n = (counts == null ? 0 : counts.length);
      if (n > rows && rows >= 0) {
        n = rows;
      }
      for (int i = 0; i < n; i++) {
        Groupings.Count gc = counts[i];
        int count = gc.getCount();
        Object obj = gc.getObject();
        out.println(
            "<tr><td align=right>"+
            count+
            "</td>"+
            (hasMultiplier ?
             "<td align=right>"+
             format(((double) count) * multiplier) +
             "</td>" : "")+
            "<td align=left>");
        if (obj instanceof Throwable) {
          // stack
          printStack((Throwable) obj);
        } else if (obj instanceof Number) {
          // time or hashcode
          out.print(obj);
        } else {
          // raw object
          if (toStringLimit <= 0) {
            int hc = System.identityHashCode(obj);
            String hex = Integer.toHexString(hc);
            out.print(hex);
          } else {
            printObject(obj, toStringLimit);
          }
        }
        out.println("</td></tr>");
      }
      out.println("</table>");
    }

    private void printInstances(InstanceStats[] iss) {
      out.println(
          "<table align=\"center\" border=\"2\">"+
          "<tr>"+
          "<th rowspan=2>System HashCode</th>"+
          "<th colspan=2>Allocation Time</th>"+
          "<th rowspan=2>Size</th>"+
          "<th rowspan=2>Capacity</th>"+
          "<th rowspan=2>Context</th>"+
          "<th rowspan=2>Stack Trace</th>");
      if (toStringLimit > 0) {
        out.println(
            "<th rowspan=2>toString</th>");
      }
      out.println(
          "</tr>"+
          "<tr>"+
          "<th>Clock</th>"+
          "<th>Offset</th>"+
          "</tr>");

      long now = System.currentTimeMillis();
      int n = (iss == null ? 0 : iss.length);
      if (n > rows && rows >= 0) {
        n = rows;
      }
      for (int i = 0; i < n; i++) {
        InstanceStats is = iss[i];
        out.println("<tr>");
        printInstanceStats(is, now);
        out.println("</tr>");
      }

      out.println("</table>");
    }

    private void printInstanceStats(InstanceStats is, long now) {
      out.println("<td align=right>");
      Object o = is.get();
      if (o == null) {
        out.print("null");
      } else {
        int hc = System.identityHashCode(o);
        String hex = Integer.toHexString(hc);
        out.print(hex);
      }
      out.println("</td>");
      long t = is.getAllocationTime();
      out.println("<td align=right>"+t+"</td>");
      out.println("<td align=right>"+(t - now)+"</td>");
      out.println("<td align=right>"+is.getSize()+"</td>");
      out.println("<td align=right>"+is.getCapacityCount()+"</td>");

      out.println("<td>");
      out.println("<font size=\"2\">");
      String agent = is.getAgentName();
      if (agent == null) {
        agent = "";
      }
      out.println("Agent: <b>"+agent+"</b><br/>");
      // we could print additional InstanceContext info here!
      out.println("</td>");
      out.println("<td>");
      Throwable throwable = is.getThrowable();
      printStack(throwable);
      out.println("</td>");
      if (toStringLimit > 0) {
        out.print("<td>");
        printObject(o, toStringLimit);
        out.println("</td>");
      }
    }

    private void printStack(Throwable throwable) {
      if (throwable == null) {
        out.println("<i>disabled</i>");
      } else {
        out.print("<pre>");
        StackTraceElement ste[] = throwable.getStackTrace();
        int jmax =
          Math.min(
              ste.length,
              stackLines + STACK_LINES_TO_SKIP);
        for (int j = STACK_LINES_TO_SKIP; j < jmax; j++) {
          StackTraceElement stack = ste[j];
          String s =
            "\n"+
            stack.getClassName()+"."+
            stack.getMethodName()+"("+
            stack.getFileName()+":"+
            stack.getLineNumber()+")";
          s = encodeHTML(s);
          out.print(s);
        }
        if (ste.length > jmax) {
          out.print(
              "<font color=red>+"+
              (ste.length - jmax)+
              "</font>");
        }
        out.print("\n</pre>");
      }
    }

    private void printObject(Object o, int toStringLimit) {
      if (toStringLimit <= 0) {
        return;
      }
      try {
        String s = null;
        if (o != null) {
          s = o.toString();
        }
        if (s == null) {
          out.print("null");
        } else {
          int len = s.length();
          if (len > toStringLimit) {
            s = s.substring(0, toStringLimit);
          }
          s = encodeHTML(s);
          out.print(s);
          if (len > toStringLimit) {
            out.print(
                "<font color=red>+"+
                (len - toStringLimit)+
                "</font>");
          }
        }
      } catch (Exception e) {
        out.print("<pre>\n<font color=red>");
        e.printStackTrace(out);
        out.print("\n</font></pre>");
      }
    }

    private static String getMegabytes(long bytes) {
      double mb = (((double) bytes) / (1<<20));
      return format(mb);
    }

    private static String format(double d) {
      // we want "#0.0#"
      //
      // ideally we'd return this double with fewer decimal places:
      //   d = (((double) Math.round(d * 100)) / 100);
      //   return Double.toString(d); 
      // however this occasionally causes Sun's "Assertion botch"
      // bug 4916788.  I suspect that DecimalFormat has the same
      // problem, so here we do it manually.  We don't expect
      // oddities like  NaNs/infinites/etc.
      double floor = Math.floor(d);
      double rem = d - floor;
      long shortrem = Math.round(rem * 100);
      return 
        (((long) floor)+
         "."+
         shortrem+
         (shortrem < 10 ? "0" : ""));
    } 
  }

  // move me to "org.cougaar.util.StringUtility"!
  private static final String encodeHTML(String s) {
    return encodeHTML(s, false);
  }
  private static final String encodeHTML(String s, boolean noBreakSpaces) {
    StringBuffer buf = null;  // In case we need to edit the string
    int ix = 0;               // Beginning of uncopied part of s
    for (int i = 0, n = s.length(); i < n; i++) {
      String replacement = null;
      char ch = s.charAt(i);
      switch (ch) {
        case '"': 
          replacement = "&quot;";
          break;
        case '<':
          replacement = "&lt;";
          break;
        case '>':
          replacement = "&gt;";
          break;
        case '&':
          replacement = "&amp;";
          break;
        case ' ':
          if (noBreakSpaces) {
            replacement = "&nbsp;";
          }
          break;
        default:
          if ((ch < ' ' || ch > '~') &&
              (ch != '\n') &&
              (ch != '\t') &&
              (ch != '\r')) {
            replacement = 
              "<u><i>"+
              Integer.toHexString(ch)+
              "</i></u>";
          }
      }
      if (replacement != null) {
        if (buf == null) {
          buf = new StringBuffer();
        }
        buf.append(s.substring(ix, i));
        buf.append(replacement);
        ix = i + 1;
      }
    }
    if (buf == null) {
      return s;
    } else {
      buf.append(s.substring(ix));
      return buf.toString();
    }
  }
}
