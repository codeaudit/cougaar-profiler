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
package org.cougaar.profiler.transform;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Base class to recursively find and modify files.
 * <p> 
 * Recursively finds all "*.class" files for the specified
 * directories, then creates a "new*" directory for each
 * directory with modified class files.
 * <p> 
 * This could be done with a shell `find` and separate modify
 * calls, but this class is much faster since it only loads the
 * JVM and jars once.
 */
public abstract class FindAndModify {

  private void usage() {
    String c = getClass().getName();
    String s = getDefaultSource();
    String t = getDefaultTarget();
    String x = getDefaultSuffix();
    System.err.println(
        "Usage: "+c+" [--help] [-v] [-C DIR] [-d DIR] [OPTIONS] [DIR]..\n"+
        "\n"+
        "  --help   display this help and exit\n"+
        "  -v       verbose output (default is quiet)\n"+
        "  -C DIR   set the source directory (default is \""+s+"\")\n"+
        "  -d DIR   set the target directory (default is \"./"+t+"\")\n"+
        "  OPTIONS  subclass-defined options\n"+ 
        "  DIR      directory with \"*"+x+"\" files\n"+
        "\n"+
        "Runs class modifier on all \"*"+x+"\" files found in\n"+
        "the specified directories.  Writes the new files\n"+
        "to a \"new*\" directory, creating directories as needed.\n"+
        "\n"+
        "For example, '"+c+" foo' would find all \"*"+x+"\n"+
        "files in ./foo and create modified files in ./"+t+"/foo");
  }

  private boolean verbose;
  private String source;
  private String target;
  private String suffix;
  private Set dirs;

  /**
   * Configure with the optional command-line parameters.
   * @return true if failure 
   */
  protected abstract boolean configure(String[] args);

  /**
   * Read the file and write the modified output to the stream.
   */
  protected abstract void modifyFile(
      String filename,
      OutputStream os) throws Exception;

  protected String getDefaultSource() { return "."; }
  protected String getDefaultTarget() { return "new"; }
  protected String getDefaultSuffix() { return ".class"; }

  protected void run(String[] args) throws Exception {
    if (parseOptions(args)) {
      execute();
    } else {
      usage();
    }
  }

  protected boolean parseOptions(String[] args) throws Exception {
    verbose = false;
    source = getDefaultSource();
    target = getDefaultTarget();
    suffix = getDefaultSuffix();
    dirs = new HashSet(args.length);

    List options = null;

    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      if (!s.startsWith("-")) {
        dirs.add(s);
        continue;
      }
      if (s.equals("--help")) {
        return false;
      } else if (s.equals("-v")) {
        verbose = true;
      } else if (s.equals("-d")) {
        if (++i < args.length) {
          target = args[i];
        } else {
          System.err.println("Missing -d DIR\n");
          return false;
        }
      } else if (s.equals("-C")) {
        if (++i < args.length) {
          source = args[i];
        } else {
          System.err.println("Missing -C DIR\n");
          return false;
        }
      } else {
        if (options == null) {
          options = new ArrayList();
        }
        options.add(s);
      }
    }

    if (dirs.isEmpty()) {
      return false;
    }

    String[] sa;
    if (options == null) {
      sa = new String[0];
    } else {
      sa = (String[]) options.toArray(new String[0]);
    }
    if (!configure(sa)) {
      return false;
    }

    return true;
  }

  public void execute() throws Exception {
    // execute
    File baseDir = new File(source);
    File[] files = baseDir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File f = files[i];
      if (f.isDirectory()) {
        String fname = f.getName();
        if (dirs.contains(fname)) {
          processDir(f);
        }
      }
    }
  }

  protected void processDir(File dir) throws Exception {
    if (verbose) {
      System.out.println("processing "+dir.getName());
    }
    // find files
    Set files = new HashSet();
    findFiles(files, dir);
    if (files.isEmpty()) {
      return;
    }
    // modify them
    modifyFiles(files); 
  }

  protected void modifyFiles(Set files) throws Exception {
    Set targets = new HashSet();
    for (Iterator iter = files.iterator();
        iter.hasNext();
        ) {
      String name = (String) iter.next();
      int sep = name.lastIndexOf(File.separatorChar);
      if (sep < 0) {
        System.out.println("Skipping unexpected filename: "+name);
        continue;
      }
      String base = name.substring(0, sep);
      String clazz = name.substring(sep+1);

      // mkdir
      String full_target = 
        target+
        File.separatorChar+
        base;
      if (!targets.contains(full_target)) {
        try {
          if (verbose) {
            System.out.println("  mkdir -p "+full_target);
          }
          File targetFile = new File(full_target);
          if (targetFile.exists() && targetFile.isDirectory()) {
            // already exists?
          } else if (!targetFile.mkdirs()) {
            throw new RuntimeException(
                "mkdirs returned false");
          }
        } catch (Exception e) {
          throw new RuntimeException(
              "Unable to `mkdir -p "+full_target+"`", e);
        }
        targets.add(full_target);
      }

      // modify
      if (verbose) { 
        System.out.println(
            "    modify "+name+
            " > "+full_target+File.separatorChar+clazz);
      }
      OutputStream os =
        new BufferedOutputStream(
          new FileOutputStream(
              full_target+
              File.separatorChar+
              clazz));
      try {
        modifyFile(name, os);
      } catch (Exception e) {
        System.err.println("Unable to modify "+name);
        e.printStackTrace();
      }
      os.close();
    }
  }
  
  /**
   * Recursively finds files.
   * @note recursive!
   */
  protected void findFiles(
      Set toFiles,
      File dir) throws Exception {
    // add my subdirectories and files
    File[] files = dir.listFiles();
    for (int i = 0; i < files.length; i++) {
      File f = files[i];
      if (f.isDirectory()) {
        // recurse!
        findFiles(toFiles, f);
      } else {
        String path = f.getPath();
        if (path.endsWith(suffix)) {
          if (path.startsWith(source+"/")) {
            path = path.substring(2);
          }
          toFiles.add(path);
        }
      }
    }
  }
}
