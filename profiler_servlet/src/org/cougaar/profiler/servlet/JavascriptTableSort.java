/*
 * <copyright>
 *  Copyright 2000-2003 Cougaar Software, Inc.
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

/**
 * Javascript for client-side table sorting.
 * <p>
 * This is somewhat quick, taking about a second to sort 100 rows.
 * Avoid this technique if you'll display more than a couple hundred
 * rows, otherwise the response time will annoy users.
 * <p>
 * There are several benefits to client-side sorting:<ul>
 *   <li>If the data is "live", then asking the server for a sorted
 *       view may show an updated view instead of sorting the
 *       displayed data</li>
 *   <li>Allows the user to save the page and still perform
 *       sorting offline</li>
 *   <li>Reduces server load</li>
 * </ul>
 * The primary downside is the processing time, which is often much
 * slower in javascript than on the server.  Also, this script may
 * not work on all browsers.
 * <p>
 * Here we've copied the source to Java for easy servlet access
 * (easier than reading a file and worrying about it not existing).
 * The downside is that this source may be interned by the VM. 
 */
public final class JavascriptTableSort {

  /*
   * Example use:
   *
   * Say the table looks like: 
   *     Name         ID     Bonus
   *    ----------- ------ -------
   *    John Doe     12345  500.00
   *    Mary Doe       111  765.43
   *    James Baker    555    0.99
   *
   * Have one URL print the javascript SOURCE string, e.g.
   *   if (req.hasParameter("sorttable")) {
   *     res.setContentType("text/javascript");
   *     PrintWriter out = res.getWriter(); 
   *     out.print(JavascriptTableSort.SOURCE); 
   *     out.flush();
   *     out.close(); 
   *   }
   * Then, in your servlet where you have a table, do something like:
   *   <html>
   *     <head>
   *       <script LANGUAGE="JavaScript" SRC="
   *          !!! URL to the above "?sorttable" page !!!
   *       "/>
   *     </head>
   *     <body>
   *       ...
   *       <a name="sort"/> 
   *       <table>
   *         <thead>
   *           <tr>
   *             <th><a href="#sort" onclick="return sortTable('tbl', 0, true);">Name</a></th>
   *             <th><a href="#sort" onclick="return sortTable('tbl', 1, true);">ID</a></th>
   *             <th><a href="#sort" onclick="return sortTable('tbl', 2, true);">Bonus</a></th>
   *           </tr> 
   *         </thead>
   *         <tbody id="tbl">
   *           <tr><td>John Doe</td><td>12345</td><td>500.00</td></tr>
   *           <tr><td>Mary Doe</td><td>111</td><td>765.43</td></tr>
   *           <tr><td>James Baker</td><td>555</td><td>0.99</td></tr>
   *         </tbody>
   *       </table>
   *     </body>
   *   </html>
   */

  private JavascriptTableSort() {}

  public static final String sortLink(
      String tableName,
      String columnName,
      int columnIndex,
      boolean defaultAsReverse) {
    return 
      "<a href=\"#sort\" onclick=\"return sortTable('"+
      tableName+
      "', "+
      columnIndex+
      ", "+
      (!defaultAsReverse)+
      ");\">"+
      columnName+
      "</a>";
  }

  public static final String SOURCE =
    "// From: http://www.faqts.com\n"+
    "Node.prototype.swapNode = function (node) {\n"+
    "  var nextSibling = this.nextSibling;\n"+
    "  var parentNode = this.parentNode;\n"+
    "  node.parentNode.replaceChild(this, node);\n"+
    "  parentNode.insertBefore(node, nextSibling);  \n"+
    "}\n"+
    "\n"+
    "// Optimized from:\n"+
    "//   http://www.brainjar.com/dhtml/tablesort\n"+
    "//\n"+
    "// This takes about 4 seconds to sort 1000 rows, where the\n"+
    "// approximate time breakdown is:\n"+
    "//   25% parse table data\n"+
    "//   10% sort\n"+
    "//   65% update table\n"+
    "function sortTable(tableID, col, initRev) {\n"+
    "\n"+
    "  var startmillis = (new Date()).getTime();\n"+
    "\n"+
    "  // get the table or table section to sort.\n"+
    "  var tblEl = document.getElementById(tableID);\n"+
    "\n"+
    "  // init, figure our which sort order we want for this column\n"+
    "  if (tblEl.reverseSort == null) {\n"+
    "    tblEl.reverseSort = new Array();\n"+
    "    tblEl.lastColumn = 1;\n"+
    "  }\n"+
    "  if (tblEl.reverseSort[col] == null) {\n"+
    "    tblEl.reverseSort[col] = initRev;\n"+
    "  }\n"+
    "  if (col == tblEl.lastColumn) {\n"+
    "    tblEl.reverseSort[col] = !tblEl.reverseSort[col];\n"+
    "  }\n"+
    "  var rev = tblEl.reverseSort[col];\n"+
    "  tblEl.lastColumn = col;\n"+
    "\n"+
    "  var initmillis = (new Date()).getTime();\n"+
    "\n"+
    "  var i, j;\n"+
    "  var n = tblEl.rows.length;\n"+
    "\n"+
    "  // parse values\n"+
    "  var values = new Array(n);\n"+
    "  for (i = 0; i < n; i++) {\n"+
    "    var v = getTextValue(tblEl.rows[i].cells[col]);\n"+
    "    var f = parseFloat(v);\n"+
    "    if (isNaN(f)) {\n"+
    "      values[i] = v;\n"+
    "    } else {\n"+
    "      values[i] = f;\n"+
    "    }\n"+
    "  }\n"+
    "\n"+
    "  var parsemillis = (new Date()).getTime();\n"+
    "\n"+
    "  // bubblesort (not worth optimizing, since this is ~10% of the\n"+
    "  // overall time)\n"+
    "  var swap = new Array(n);\n"+
    "  var testVal;\n"+
    "  var minVal, minIdx;\n"+
    "  var cmp;\n"+
    "  for (i = 0; i < n; i++) {\n"+
    "    // assume the current row has the minimum value.\n"+
    "    minIdx = i;\n"+
    "    minVal = values[i];\n"+
    "    // find later row with smaller value\n"+
    "    for (j = n-1; j > i; j--) {\n"+
    "      testVal = values[j];\n"+
    "      cmp;\n"+
    "      if (minVal == testVal) {\n"+
    "        cmp = 0;\n"+
    "      } else if (minVal > testVal) {\n"+
    "        cmp = 1;\n"+
    "      } else {\n"+
    "        cmp = -1;\n"+
    "      }\n"+
    "      // negate the comparison result if the reverse sort flag is set.\n"+
    "      if (rev) {\n"+
    "        cmp = -cmp;\n"+
    "      }\n"+
    "      // if this row has a smaller value than the current minimum,\n"+
    "      // remember its position and update the current minimum value.\n"+
    "      if (cmp > 0) {\n"+
    "        minIdx = j;\n"+
    "        minVal = testVal;\n"+
    "      }\n"+
    "    }\n"+
    "    // swap\n"+
    "    swap[i] = minIdx;\n"+
    "    if (minIdx > i) {\n"+
    "      values[minIdx] = values[i];\n"+
    "      values[i] = minVal;\n"+
    "    }\n"+
    "  }\n"+
    "\n"+
    "  var sortmillis = (new Date()).getTime();\n"+
    "\n"+
    "  // update table\n"+
    "  for (i = 0; i < n; i++) {\n"+
    "    minIdx = swap[i];\n"+
    "    if (minIdx > i) {\n"+
    "      tblEl.rows[i].swapNode(tblEl.rows[minIdx]);\n"+
    "    }\n"+
    "  }\n"+
    "\n"+
    "  var updatemillis = (new Date()).getTime();\n"+
    "\n"+
    "  window.status=\n"+
    "    \"Sorted in \"+(updatemillis - startmillis)+\n"+
    "    \" milliseconds (init=\"+(initmillis - startmillis)+\n"+
    "    \", parse=\"+(parsemillis - initmillis)+\n"+
    "    \", sort=\"+(sortmillis - parsemillis)+\n"+
    "    \", update=\"+(updatemillis - sortmillis)+\n"+
    "    \")\";\n"+
    "\n"+
    "  return false;\n"+
    "}\n"+
    "\n"+
    "// this code is necessary for browsers that don't reflect the DOM\n"+
    "// constants (like IE).\n"+
    "if (document.ELEMENT_NODE == null) {\n"+
    "  document.ELEMENT_NODE = 1;\n"+
    "  document.TEXT_NODE = 3;\n"+
    "}\n"+
    "\n"+
    "function getTextValue(el) {\n"+
    "  // find and concatenate the values of all text nodes\n"+
    "  // (probably overkill...)\n"+
    "  var i;\n"+
    "  var s;\n"+
    "  s = \"\";\n"+
    "  for (i = 0; i < el.childNodes.length; i++) {\n"+
    "    if (el.childNodes[i].nodeType == document.TEXT_NODE) {\n"+
    "      s += el.childNodes[i].nodeValue;\n"+
    "    } else if (el.childNodes[i].nodeType == document.ELEMENT_NODE &&\n"+
    "               el.childNodes[i].tagName == \"BR\") {\n"+
    "      s += \" \";\n"+
    "    } else {\n"+
    "      // use recursion to get text within sub-elements.\n"+
    "      s += getTextValue(el.childNodes[i]);\n"+
    "    }\n"+
    "  }\n"+
    "  return normalizeString(s);\n"+
    "}\n"+
    "\n"+
    "// regular expressions for normalizing white space.\n"+
    "var whtSpEnds = new RegExp(\"^\\s*|\\s*$\", \"g\");\n"+
    "var whtSpMult = new RegExp(\"\\s\\s+\", \"g\");\n"+
    "\n"+
    "function normalizeString(s) {\n"+
    "  s = s.replace(whtSpMult, \" \");  // collapse any multiple whites space.\n"+
    "  s = s.replace(whtSpEnds, \"\");   // remove leading or trailing white space.\n"+
    "  return s;\n"+
    "}\n";
}
