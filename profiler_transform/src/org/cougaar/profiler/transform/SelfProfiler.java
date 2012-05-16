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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.bcel.Constants; // inlined
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.BranchHandle;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.CompoundInstruction;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.INSTANCEOF;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionConstants; // inlined
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.StackInstruction;
import org.apache.bcel.generic.Type;

/**
 * Read in a class file and add instructions to record every
 * instance allocation of the class.
 * <p>
 * The class tracks it<i>self</i> by calling the profiler
 * in all of its own constructor/clone/readObject methods.
 * Any allocation of this class by any class in the VM will
 * be tracked.
 */
public class SelfProfiler {

  private static void usage() {
    System.err.println(
        "Usage: SelfProfiler [OPTION..] FILE\n"+
        "\n"+
        "  --this=BOOLEAN      profile this class (default is true)\n"+
        "  --arrays=BOOLEAN    profile array allocations (default is false)\n"+
        "\n"+
        "  --config=STRING     class name of Options factory class (default is\n"+
        "                      \"org.cougaar.profiler.DefaultOptionsFactory\")\n"+
        "\n"+
        "  --module=STRING     optional \"module\" name that will be passed to\n"+
        "                      the Options factory method (default is null)\n"+
        "\n"+
        "  --clinit=BOOLEAN    initialize the profiler field in the static\n"+
        "                      class init as opposed to the first alloc\n"+
        "                      (default is true)\n"+
        "  --size=BOOLEAN      capture size (default is true)\n"+
        "  --capacity=BOOLEAN  capture capacity (default is true)\n"+
        "  --uid=BOOLEAN       ensure serialVersionUID (default is true)\n"+
        "\n"+
        "  --help              print this help message and exit\n"+
        "\n"+
        "  FILE                input file, or - for STDIN\n"+
      "\n"+
      "Reads a Java class file and outputs a new class\n"+
      "file with added profiler calls.\n"+
      "\n"+
      "The above options factory class must look something like:\n"+
      "  package com.foo;\n"+
      "  import org.cougaar.profiler.Options;\n"+
      "  public class MyConfig {\n"+
      "    public static final Options getOptions(\n"+
      "      String module, String classname) {..}\n"+
      "  }\n"+
      "This will control the level of profiling.  For details see\n"+
      "the default implementation in the profiler source:\n"+
      "  org/cougaar/profiler/DefaultOptionsFactory.java\n"+
      "\n"+
      "Example usage:\n"+
      "  cat Bar.class |\\\n"+
      "    java -classpath bcel-5.1.jar:.\\\n"+
      "    SelfProfiler\\\n"+
      "    --config=com.foo.MyConfig\\\n"+
      "    --module=whatever\\\n"+
      "    -\\\n"+
      "    > tmp/Bar.class\n"+
      "\n"+
      "Also see the ProfileAll class to modify more than\n"+
      "one class using `find`");
  }

  /**
   * MemoryTracker API constants.
   * <p>
   * The API must look like:<pre>
   *   package org.cougaar.profiler;
   *   public class MemoryTracker {
   *     public static MemoryTracker getInstance(
   *         String type, int bytesEach, Options options) {..}
   *     public void add(Object o) {..}
   *   }
   *   public class Options {
   *     public Options mask(boolean size, boolean capacity) {..}
   *   }
   * </pre>
   */
  private static final String MEMORY_TRACKER_CLASS =
    "org.cougaar.profiler.MemoryTracker";
  private static final String GET_INSTANCE_METHOD =
    "getInstance";
  private static final String ADD_METHOD =
    "add";
  private static final String OPTIONS_CLASS =
    "org.cougaar.profiler.Options";
  private static final String MASK_METHOD =
    "mask";

  /**
   * Options factory API constants.
   * <p>
   * The API must look like:<pre>
   *   import org.cougaar.profiler.Options;
   *   public class Whatever {
   *     public static Options getOptions(
   *       String module, String classname) {..}
   *   }
   * </pre> 
   */
  private static final String DEFAULT_CONFIG =
    "org.cougaar.profiler.DefaultOptionsFactory";
  private static final String GET_OPTIONS_METHOD =
    "getOptions";

  /**
   * Optional methods to access object size and array lengths.
   * <p>
   * We want:<pre>
   *   import org.cougaar.profiler.Capacity;
   *   import org.cougaar.profiler.Size;
   *   ..
   *   public class ArrayList .. implements .. Size, Capacity {..
   *     public int $get_size() {
   *       return size;
   *     }
   *     public int $get_capacity_count() {
   *       Object[] buf = elementData;
   *       return (buf == null ? 0 : buf.length);
   *     }..
   *     public int $get_capacity_bytes() {
   *       Object[] buf = elementData;
   *       return (buf == null ? 0 : 12+4*buf.length);
   *     }..
   *   }
   * <pre>
   * This is generalized for any class.  The "$get_size" method is
   * added if there's an int field named:<pre>
   *   size / elementCount / count
   * </pre>  The "$get_capacity_*" methods are added if the class or
   * its parents contain non-static arrays.  "$get_capacity_bytes"
   * multiplies each array by its element byte size and adds the
   * array header size.
   * <p>
   * The API must look like:<pre>
   *   package org.cougaar.profiler;
   *   public interface Size {
   *     public int $get_size();
   *   }
   *   public interface Capacity {
   *     public int $get_capacity_count();
   *     public int $get_capacity_bytes();
   *   }
   * </pre>
   */
  private static final String SIZE_METHOD =
    "$get_size";
  private static final String CAPACITY_COUNT_METHOD =
    "$get_capacity_count";
  private static final String CAPACITY_BYTES_METHOD =
    "$get_capacity_bytes";
  private static final String SIZE_CLASS =
    "org.cougaar.profiler.Size";
  private static final String CAPACITY_CLASS =
    "org.cougaar.profiler.Capacity";

  /**
   * The name of our static profiler field and profiling
   * method.
   */
  private static final String PROFILER_FIELD_PREFIX =
    "$PROFILER_";
  private static final String PROFILE_METHOD_PREFIX =
    "$profile_";

  /**
   * Exclude "java.lang" classes used by the profiler.
   * <p>
   * "Class.class" could be transformed but at runtime there are
   * no allocations -- it must be a VM trick.
   * <p>
   * The string comparator is an odd case since it's statically
   * initialized in String.  It's easiest to ignore it since it's
   * a singleton anyways.
   */ 
  private static final Set JAVA_LANG_EXCLUDE =
    new HashSet(Arrays.asList(new String[] {
      "java.lang.Class",
      "java.lang.Object",
      "java.lang.String$CaseInsensitiveComparator",
      "java.lang.ThreadGroup",
      "java.lang.Throwable",
      "java.lang.ref.Reference",
      "java.lang.ref.SoftReference",
      "java.lang.ref.WeakReference",
    }));

  // options and their defaults
  private boolean trackThis = true;
  private boolean trackArrays = false;
  private String config = DEFAULT_CONFIG;
  private String module = null;
  private boolean enableSize = true;
  private boolean enableCapacity = true;
  private boolean staticInit = true;
  private boolean ensureUID = true;

  private String class_name;
  private String super_name;
  private String safe_class_name;
  private String safe_super_name;
  private boolean isSerial;
  private long newSerialVer;
  private JavaClass[] supers;
  private ClassGen cg;
  private ConstantPoolGen cp;
  private InstructionFactory factory;
  private boolean has_size;
  private boolean has_capacity;

  public static void main(String[] args) throws Exception {
    int n = args.length;
    if (n == 0 || args[0].equals("--help")) {
      usage();
      return;
    }

    String file_name = args[args.length-1];
    InputStream in = System.in;
    OutputStream out = System.out;

    String[] options;
    int shift = -1;
    n -= Math.abs(shift);
    if (n <= 0) {
      options = null;
    } else {
      options = new String[n];
      for (int i = 0; i < n; i++) {
        options[i] = args[shift+i];
      }
    }

    SelfProfiler transform = new SelfProfiler(options);

    transform.modifyClassToStream(file_name, in, out);
  }

  public SelfProfiler() {
    this(null);
  }

  public SelfProfiler(String[] args) {
    if (!parseOptions(args)) {
      String s = "Arguments: "+Arrays.asList(args);
      System.err.println(s+"\n");
      usage();
      throw new RuntimeException(s);
    }
  }

  private boolean parseOptions(String[] args) {
    int n = (args == null ? 0 : args.length);
    for (int i = 0; i < n; i++) {
      String s = args[i];
      if (!s.startsWith("--")) {
        return false;
      }
      int j = s.indexOf('=');
      if (j < 0) {
        return false;
      }
      String key = s.substring(2, j);
      String value = s.substring(j+1);
      if (key.equals("this")) {
        trackThis = "true".equals(value);
      } else if (key.equals("arrays")) {
        trackArrays = "true".equals(value);
      } else if (key.equals("config")) {
        if (value == null || value.length() == 0) {
          System.err.println("Must specify "+key+" class");
          return false;
        }
        config = value;
      } else if (key.equals("module")) {
        module = value;
      } else if (key.equals("clinit")) {
        staticInit = "true".equals(value);
      } else if (key.equals("size")) {
        enableSize = "true".equals(value);
      } else if (key.equals("capacity")) {
        enableCapacity = "true".equals(value);
      } else if (key.equals("uid")) {
        ensureUID = "true".equals(value);
      } else {
        System.err.println("Unknown option: "+s);
        return false;
      }
    }
    return true;
  }

  public void modifyClassToStream(
      String file_name,
      InputStream in,
      OutputStream out) {
    // load clazz
    JavaClass clazz;
    try {
      if ("-".equals(file_name)) {
        clazz = new ClassParser(in, "-").parse();
      } else {
        clazz = new ClassParser(file_name).parse();
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to parse file \""+file_name+"\"", e);
    }

    // transform
    JavaClass new_clazz = modifyClass(clazz);

    // pretty printer:
    //BCELifier bcelifier = new BCELifier(new_clazz, System.out);
    //bcelifier.start();

    // raw ".class" output:
    try {
      new_clazz.dump(out);
    } catch (Exception e) {
      throw new RuntimeException(
          "Unable to write class \""+file_name+"\" to stream", e);
    }
  }

  /**
   * See org.apache.bcel.util.ClassLoader for dynamic modification.
   * <p>
   * Your subclass of the BCEL ClassLoader would do something like:
   * <pre>
   *   private static final String[] options = null; // options
   *   private final SelfProfiler transform = new SelfProfiler(options);
   *   protected synchronized JavaClass modifyClass(JavaClass clazz) {
   *     if (!(we care about this class)) {
   *       return clazz;
   *     }
   *     return transform.modifyClass(clazz);
   *   }
   * </pre>
   * This adds a runtime overhead and potential security issues.
   * Also, it won't work on JDK-internal classes (e.g. String).
   */
  public JavaClass modifyClass(JavaClass clazz) {
    if (isExcluded(clazz)) {
      return clazz;
    }
    setClassNames(clazz);
    if (alreadyModified(clazz)) {
      return clazz;
    }
    createClassGen(clazz);
    run();
    JavaClass ret = cg.getJavaClass();
    reset();
    return ret;
  }

  private boolean isExcluded(JavaClass clazz) {
    if (clazz.isInterface()) {
      // we only track objects
      return true; 
    }
    String s = clazz.getClassName();
    if (s.startsWith("java.lang.") &&
        JAVA_LANG_EXCLUDE.contains(s)) {
      // exclude classes required by profiler
      //
      // some of JDK rt.jar's "sun.*" may also belong here
      System.err.println("  excluding: "+s);
      return true; 
    }
    return false;
  }

  private boolean isStaticInit() {
    // must delay init of java.lang profiler field, otherwise
    // the VM will throw an initialization error.
    //
    // this really isn't a big deal, since we'll set the
    // profiler field on the first alloc.  We'd simply prefer
    // to set the field as a "final" in the clinit.
    //
    // this could probably be trimmed to a smaller subset of
    // "java.lang.*"
    return
      (staticInit &&
       !class_name.startsWith("java.lang."));
  }

  private void setClassNames(JavaClass clazz) {
    class_name = clazz.getClassName();
    super_name = clazz.getSuperclassName();
    safe_class_name = encode(class_name);
    safe_super_name = encode(super_name);
  }

  private void createClassGen(JavaClass clazz) {
    // see if we'll need a serialVersionUID, and compute
    // it before we modify the class
    isSerial = isSerializable(clazz);
    newSerialVer =
      (isSerial && ensureUID ?
       computeSerialVersionUID(clazz) :
       -1);
    supers = clazz.getSuperClasses();
    cg = new ClassGen(clazz);
    cp = cg.getConstantPool();
    factory = new InstructionFactory(cg, cp);
  }

  private void reset() {
    class_name = null;
    super_name = null;
    safe_class_name = null;
    safe_super_name = null;
    isSerial = false;
    newSerialVer = 0;
    supers = null;
    cg = null;
    cp = null;
    factory = null;
    has_size = false;
    has_capacity = false;
  }

  private void run() {
    // make sure the serialVersionUID field exists, so we don't
    // break serialization
    addSerialVersionUID();

    if (trackThis) {
      // add "$get_size()" and "$get_capacity()" methods
      defineSize();
      defineCapacity();

      // add static profiler field
      defineProfilerField();

      // init profiler field in class init method
      initProfilerField();

      // override "profile_<super>" method to disable super's profiler
      disableSuperProfiler();

      // add our "profile_<class>" method
      addClassProfiler();

      // update constructors with "profile_<class>" calls
      // (excluding constructors that call "this(..)")
      //
      // note that the default constructor always exists
      callProfilerInConstructors();

      // catch "readObject", which is a hidden constructor.
      //
      // we can ignore "readExternal" since it calls the no-arg
      // constructor.
      callProfilerInReadObject();

      // handle "clone", which is yet another hidden constructor.
      callProfilerInClone();
    }

    if (trackArrays) {
      // record all "newarray" instructions
      recordArraysAllocations();
    }
  }

  private static String encode(String s) {
    // encode classname to a safe method name
    //
    // replace "." with "_"
    // replace "_" with "__"
    // "$" is fine
    int len = s.length();
    StringBuffer buf = null;
    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);
      String repl;
      if (ch == '.') {
        repl = "_";
      } else if (ch == '_') {
        repl = "__";
      } else {
        if (buf != null) {
          buf.append(ch);
        }
        continue;
      }
      if (buf == null) {
        buf = new StringBuffer(s.substring(0, i));
      }
      buf.append(repl);
    }
    if (buf == null) {
      return s;
    }
    return buf.toString();
  }

  //
  // Bytecode editting, mostly based upon BCELifier output:
  //

  private boolean alreadyModified(JavaClass clazz) {
    // look for:
    //   static MemoryTracker $PROFILER_Bar;
    Field[] fields = clazz.getFields();
    String matchname = PROFILER_FIELD_PREFIX+safe_class_name;
    for (int i = 0; i < fields.length; i++) {
      Field f = fields[i];
      if (((f.getAccessFlags() & Constants.ACC_STATIC) != 0) &&
          f.getName().equals(matchname) &&
          f.getType().equals(
            new ObjectType(MEMORY_TRACKER_CLASS))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSerializable(JavaClass clazz) {
    // look for:
    //   instanceof Serializable
    JavaClass[] ifcs = clazz.getAllInterfaces();
    for (int i = 0; i < ifcs.length; i++) {
      JavaClass ifc = ifcs[i];
      if (ifc.getClassName().equals("java.io.Serializable")) {
        return true;
      }
    }
    return false;
  }

  private static long computeSerialVersionUID(JavaClass clazz) {
    // if there's no "serialVersionUID" field then calculate
    // the correct value so we don't break serialization
    Field[] fields = clazz.getFields();
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      if (field.getName().equals("serialVersionUID")) {
        // already have one
        return -1;
      }
    }
    long ret = SerialVer.computeSerialVersionUID(clazz);
    return ret;
  }

  private int computeSize() {
    // minimally costs an object header
    int bytes = 8;
    int i = -1;
    int n = supers.length - 1;
    Field[] fields = cg.getFields();
    while (true) {
      for (int j = 0; j < fields.length; j++) {
        Field f = fields[j];
        if ((f.getAccessFlags() & Constants.ACC_STATIC) != 0) {
          continue;
        }
        // The VM may pack primitives into sub-word slots, aligning
        // by words as necessary but always within class/subclass
        // boundaries.  Here we simply assume that doubles and longs
        // cost 2 words and everything else costs 1
        int words =  f.getType().getSize();
        bytes += (words << 2);
      }
      if (++i >= n) {
        break;
      }
      fields = supers[i].getFields();
    }
    return bytes;
  }

  private void addSerialVersionUID() {
    if (newSerialVer != -1) {
      FieldGen field = new FieldGen(
          (Constants.ACC_STATIC |
           Constants.ACC_FINAL |
           Constants.ACC_PRIVATE),
          Type.LONG,
          "serialVersionUID",
          cp);
      field.setInitValue(newSerialVer);
      cg.addField(field.getField());
    }
  }

  private boolean subclassCanAccess(Field f, JavaClass supercl) {
    int acc = f.getAccessFlags();
    if ((acc & Constants.ACC_PRIVATE) != 0) {
      // private!
      return false;
    }
    if ((acc &
          (Constants.ACC_PUBLIC |
           Constants.ACC_PROTECTED)) == 0) {
      // check for package-private access
      int sep = class_name.lastIndexOf('.');
      String pack =
        (sep >= 0 ? class_name.substring(0, sep) : "");
      String superpack = supercl.getPackageName();
      if (!pack.equals(superpack)) {
        // package-private
        return false;
      }
    }
    return true;
  }

  private static String validateStaticAccess(
      String key, String value) {
    String ret = value;
    boolean is_method = key.endsWith("Method");
    if (value.indexOf('.') < 0) {
      System.err.println(
          "Invalid CLASSNAME."+
          (is_method ? "METHOD" : "FIELD")+
          ": "+value);
      return null;
    }
    if (is_method) {
      if (!value.endsWith("()")) {
        ret = value+"()";
      }
    } else {
      if (value.endsWith("()")) {
        System.err.println(
            "Use --"+
            key.substring(0, key.length()-"Field".length())+
            "Method="+value);
        return null;
      }
    }
    return ret;
  }

  private Instruction createStaticAccess(
      String desc, Type type) {
    // lookup value at runtime
    int sep = desc.lastIndexOf('.');
    String cl = desc.substring(0, sep);
    String name = desc.substring(sep+1);
    Instruction ret;
    if (name.endsWith("()")) {
      ret =
        factory.createInvoke(
            cl,
            name,
            type,
            Type.NO_ARGS,
            Constants.INVOKESTATIC);
    } else {
      ret =
        factory.createFieldAccess(
            cl,
            name,
            type,
            Constants.GETSTATIC);
    }
    return ret;
  }

  private void defineSize() {
    if (!enableSize) {
      // disabled
      return;
    }

    has_size = false;
    // look for custom "$get_size()"
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      String name = m.getName();
      // check name
      if (!SIZE_METHOD.equals(name)) {
        continue;
      }
      // check method signature
      if (((m.getAccessFlags() & Constants.ACC_PUBLIC) == 0) ||
          (!m.getReturnType().equals(Type.INT)) ||
          (m.getArgumentTypes().length != 0)) {
        System.err.println(
            "Invalid \"public int "+SIZE_METHOD+
            "()\" signature: "+m);
        return;
      }
      // add "Size" interface
      cg.addInterface(SIZE_CLASS);
      has_size = true;
      return;
    }
    // look for non-static int "size/elementCount/count"
    //
    // we don't want a "size()" method, since it may be costly
    // to compute.
    int i = -1;
    int n = supers.length - 1;
    Field[] fields = cg.getFields();
    String size_name = null;
    while (true) {
      for (int j = 0; j < fields.length; j++) {
        Field f = fields[j];
        // non-static int
        if ((f.getType().getType() != Constants.T_INT) ||
            ((f.getAccessFlags() & Constants.ACC_STATIC) != 0)) {
          continue;
        }
        // accessable by this class
        if (i >= 0 && !subclassCanAccess(f, supers[i])) {
          continue;
        }
        // one of the expected names
        String name = f.getName();
        if (!("size".equals(name) ||
              "elementCount".equals(name) ||
              "count".equals(name))) {
          continue;
        }
        // found our match
        size_name = name;
        break;
      }
      if (size_name != null || ++i >= n) {
        break;
      }
      fields = supers[i].getFields();
    }
    if (size_name == null) {
      return;
    }
    has_size = true;
    // add "Size" interface
    cg.addInterface(SIZE_CLASS);
    // add new method:
    //   public int $get_size() {
    //     return size;
    //   }
    InstructionList il = new InstructionList();
    MethodGen method = new MethodGen(
        Constants.ACC_PUBLIC,
        Type.INT,
        Type.NO_ARGS,
        new String[] {},
        SIZE_METHOD,
        class_name,
        il,
        cp);
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(
        factory.createFieldAccess(
          class_name,
          size_name,
          Type.INT,
          Constants.GETFIELD));
    il.append(factory.createReturn(Type.INT));
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }

  private void defineCapacity() {
    if (!enableCapacity) {
      // disabled
      return;
    }

    has_capacity = false;
    boolean has_capacity_count = false;
    boolean has_capacity_bytes = false;

    // look for custom "$get_capacity()"
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      String name = m.getName();
      // check name
      if (CAPACITY_COUNT_METHOD.equals(name)) {
        has_capacity_count = true;
      } else if (CAPACITY_BYTES_METHOD.equals(name)) {
        has_capacity_bytes = true;
      } else {
        continue;
      }
      // check method signature
      if (((m.getAccessFlags() & Constants.ACC_PUBLIC) == 0) ||
          (!m.getReturnType().equals(Type.INT)) ||
          (m.getArgumentTypes().length != 0)) {
        System.err.println(
            "Invalid \"public int "+name+
            "()\" signature: "+m);
        return;
      }
    }
    if (has_capacity_count && has_capacity_bytes) {
      // add "Capacity" interface
      has_capacity = true;
      cg.addInterface(CAPACITY_CLASS);
      return;
    }

    // look for non-static array fields
    //
    // we can't rely upon parent classes implementing
    // "$get_capacity()", since they may disabled at runtime.  Here
    // we add our method if we contain any arrays, otherwise we let
    // our parent classes handle it.  The risk is that our parent
    // will have a private array *and* this class has arrays, in
    // which case we'd miss the parent's array, but in practice this
    // rarely occurs.
    int i = -1;
    int n = supers.length - 1;
    Field[] fields = cg.getFields();
    List arrays = null;
    while (true) {
      for (int j = 0; j < fields.length; j++) {
        Field f = fields[j];
        // non-static array
        if ((f.getType().getType() != Constants.T_ARRAY) ||
            ((f.getAccessFlags() & Constants.ACC_STATIC) != 0)) {
          continue;
        }
        // accessable by this class
        if (i >= 0 && !subclassCanAccess(f, supers[i])) {
          continue;
        }
        if (arrays == null) {
          arrays = new ArrayList();
        }
        arrays.add(f);
      }
      if (++i >= n) {
        break;
      }
      fields = supers[i].getFields();
    }
    if (arrays == null) {
      return;
    }

    // add "Capacity" interface
    has_capacity = true;
    cg.addInterface(CAPACITY_CLASS);
    if (!has_capacity_count) {
      writeCapacity(arrays, false);
    }
    if (!has_capacity_bytes) {
      writeCapacity(arrays, true);
    }
  }
  private void writeCapacity(List arrays, boolean bytes) {
    // add new method:
    //   public int $get_capacity[_bytes]() {
    //     int ret = 0;
    //     foreach (arrays) {
    //       type[] a = field;
    //       if (a == null) {
    //         continue;
    //       }
    //       int tmp = a.length;
    //       if (_bytes) {
    //         tmp = 12 + len*sizeof(type);
    //       }
    //       ret += tmp;
    //     }
    //     return ret;
    //   }
    // use the stack for "ret"
    InstructionList il = new InstructionList();
    MethodGen method = new MethodGen(
        Constants.ACC_PUBLIC,
        Type.INT,
        Type.NO_ARGS,
        new String[] {},
        (bytes ? CAPACITY_BYTES_METHOD : CAPACITY_COUNT_METHOD),
        class_name,
        il,
        cp);
    il.append(InstructionConstants.ICONST_0);
    BranchInstruction if_null = null;
    for (int k = arrays.size() - 1; k >= 0; k--) {
      Field f = (Field) arrays.get(k);
      InstructionHandle load_this =
        il.append(factory.createLoad(Type.OBJECT, 0));
      if (if_null != null) {
        if_null.setTarget(load_this);
      }
      il.append(
          factory.createFieldAccess(
            class_name,
            f.getName(),
            f.getType(),
            Constants.GETFIELD));
      il.append(factory.createDup(1));
      il.append(factory.createStore(Type.OBJECT, 1));
      if_null =
        factory.createBranchInstruction(Constants.IFNULL, null);
      il.append(if_null);
      il.append(factory.createLoad(Type.OBJECT, 1));
      il.append(InstructionConstants.ARRAYLENGTH);
      if (bytes) {
        int sz;
        // ignore alignment and bit-packing issues
        switch (f.getType().getType()) {
          case Constants.T_BOOLEAN:
          case Constants.T_BYTE:
            sz = 1; break;
          case Constants.T_CHAR:
          case Constants.T_SHORT:
            sz = 2; break;
          case Constants.T_INT:
          case Constants.T_FLOAT:
          case Constants.T_OBJECT:
          default:
            sz = 4; break;
          case Constants.T_DOUBLE:
          case Constants.T_LONG:
            sz = 8; break;
        }
        if (sz != 1) {
          il.append(new PUSH(cp, sz));
          il.append(InstructionConstants.IMUL);
        }
        il.append(new PUSH(cp, 12));
        il.append(InstructionConstants.IADD);
      }
      il.append(InstructionConstants.IADD);
    }
    InstructionHandle ret_int =
      il.append(factory.createReturn(Type.INT));
    if (if_null != null) {
      if_null.setTarget(ret_int);
    }
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }

  private void defineProfilerField() {
    // add:
    //   private static final MemoryTracker $PROFILER_Bar;
    int acc =
      (Constants.ACC_PRIVATE |
       Constants.ACC_STATIC);
    if (isStaticInit()) {
      acc |= Constants.ACC_FINAL;
    }
    FieldGen field =
      new FieldGen(
          acc,
          new ObjectType(MEMORY_TRACKER_CLASS),
          PROFILER_FIELD_PREFIX+safe_class_name,
          cp);
    cg.addField(field.getField());
  }

  private void initProfilerField() {
    if (!isStaticInit()) {
      return;
    }

    // find "<clinit>" and update it
    Method clinit_method = null;
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      String name = m.getName();
      if ("<clinit>".equals(name)) {
        clinit_method = m;
        break;
      }
    }
    // also calculate estimated object size
    int bytes = computeSize();
    if (clinit_method == null) {
      newClassInit(bytes);
    } else {
      updateClassInit(clinit_method, bytes);
    }
  }
  private void newClassInit(int bytes) {
    // create new "<clinit>" to init our profiler field
    InstructionList il = new InstructionList();
    MethodGen method = new MethodGen(
        Constants.ACC_STATIC,
        Type.VOID,
        Type.NO_ARGS,
        new String[] {},
        "<clinit>",
        class_name,
        il,
        cp);
    writeClassInit(il, bytes, false);
    il.append(factory.createReturn(Type.VOID));
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }
  private void updateClassInit(Method orig_m, int bytes) {
    // insert our profiler field init at the start of the
    // "<clinit>".  We must do this at the start, since
    // the clinit may allocate an instance (e.g. a singleton)
    // and the constructor would see a null field.
    MethodGen method = new MethodGen(orig_m, class_name, cp);
    InstructionList il = new InstructionList();
    writeClassInit(il, bytes, false);
    InstructionList orig_il = method.getInstructionList();
    orig_il.insert(il);
    il.dispose();
    method.setMaxStack();
    method.setMaxLocals();
    cg.replaceMethod(orig_m, method.getMethod());
    orig_il.dispose();
  }
  private InstructionHandle writeClassInit(
      InstructionList il, int bytes, boolean dup) {
    InstructionHandle begin = il.append(new PUSH(cp, class_name));
    il.append(new PUSH(cp, bytes));

    // get options
    il.append(new PUSH(cp, module));
    il.append(new PUSH(cp, class_name));
    il.append(
        factory.createInvoke(
          config,
          GET_OPTIONS_METHOD,
          new ObjectType(OPTIONS_CLASS),
          new Type[] { Type.STRING, Type.STRING },
          Constants.INVOKESTATIC));

    // mask out size if !has_size, ditto for capacity
    if (!has_size || !has_capacity) {
      il.append(InstructionConstants.DUP);
      il.append(factory.createStore(Type.OBJECT, 1));
      BranchInstruction if_null =
        factory.createBranchInstruction(Constants.IFNULL, null);
      il.append(if_null);
      il.append(factory.createLoad(Type.OBJECT, 1));
      il.append(new PUSH(cp, has_size));
      il.append(new PUSH(cp, has_capacity));
      il.append(factory.createInvoke(
            OPTIONS_CLASS,
            MASK_METHOD,
            new ObjectType(OPTIONS_CLASS),
            new Type[] { Type.BOOLEAN, Type.BOOLEAN },
            Constants.INVOKEVIRTUAL));
      il.append(factory.createStore(Type.OBJECT, 1));
      InstructionHandle ih_load =
        il.append(factory.createLoad(Type.OBJECT, 1));
      if_null.setTarget(ih_load);
    }

    // get profiler
    il.append(factory.createInvoke(
          MEMORY_TRACKER_CLASS,
          GET_INSTANCE_METHOD,
          new ObjectType(MEMORY_TRACKER_CLASS),
          new Type[] {
            Type.STRING,
            Type.INT,
            new ObjectType(OPTIONS_CLASS)},
          Constants.INVOKESTATIC));
    if (dup) {
      il.append(InstructionConstants.DUP);
    }
    il.append(factory.createFieldAccess(
          class_name,
          PROFILER_FIELD_PREFIX+safe_class_name,
          new ObjectType(MEMORY_TRACKER_CLASS),
          Constants.PUTSTATIC));
    return begin;
  }

  private void disableSuperProfiler() {
    // replace:
    //   protected void $profile_Foo() {..}
    // with empty method:
    //   protected void $profile_Foo() {}
    // to prevent our super's constructor from tracking our
    // class.
    //
    // We must do this for all super classes, since we may be
    // profiling portions of the hierarchy, e.g.:
    //    class A { !profile_me! }
    //    class B extends A { }
    //    class C extends B { !profile_me! }
    //
    // We exclude the last superclass, Object, because we
    // never profile it (due to VM initializer limitations)
    if ("java.lang.Object".equals(super_name)) {
      return;
    }
    int i = 0;
    int n = supers.length - 1;
    String next_super = super_name;
    String safe_next_super = safe_super_name;
    while (true) {
      InstructionList il = new InstructionList();
      MethodGen method =
        new MethodGen(
            Constants.ACC_PROTECTED,
            Type.VOID,
            Type.NO_ARGS,
            new String[] {},
            PROFILE_METHOD_PREFIX+safe_next_super,
            class_name,
            il,
            cp);
      il.append(factory.createReturn(Type.VOID));
      method.setMaxStack();
      method.setMaxLocals();
      cg.addMethod(method.getMethod());
      il.dispose();
      // next
      if (++i >= n) {
        break;
      }
      next_super = supers[i].getClassName();
      safe_next_super = encode(next_super);
    }
  }

  private void addClassProfiler() {
    // if (isStaticInit() == false) then we do:
    //   protected void $profile_Bar() {
    //     if ($PROFILER_Bar == null) {
    //       return;
    //     }
    //     $PROFILER_Bar.add(this);
    //   }
    //
    // The $PROFILER_Bar field is almost always non-null, since it's
    // the first field set in <clinit>.  However, the parent class
    // may have a static reference in its <clinit>, which will
    // construct an instance *before* our class's <clinit>!
    //
    // Here's an example based upon log4j-1.2.7:
    //   public class Logger {
    //     public static void main(String[] args) {
    //       System.out.println(Level.class);
    //     }
    //     static class Priority {
    //       static final Level FATAL = new Level();
    //     }
    //     static class Level {
    //       static final Level INFO = new Level();
    //       public Level() { System.out.println("INFO: "+INFO); }
    //     }
    //   }
    // This will print "INFO: null" and the Level classname.
    // Our profiler field would also be null.
    //
    // If (isStaticInit() == false) then we do:
    //   protected void $profile_Bar() {
    //     if ($PROFILER_Bar == null) {
    //       Options options = OPTIONS_FACTORY.getOptions("Bar");
    //       $PROFILER_Bar = MemoryTracker.getInstance(
    //         "Bar", BYTES_EACH, options);
    //     }
    //     $PROFILER_Bar.add(this);
    //   }
    // We don't need to synchronize since the MemoryTracker is
    // internally synchronized and redundant "getInstance(..)"
    // calls will return identical ClassTrackers.
    InstructionList il = new InstructionList();
    MethodGen method =
      new MethodGen(
          Constants.ACC_PROTECTED,
          Type.VOID,
          Type.NO_ARGS,
          new String[] {},
          PROFILE_METHOD_PREFIX+safe_class_name,
          class_name,
          il,
          cp);
    il.append(
      factory.createFieldAccess(
        class_name,
        PROFILER_FIELD_PREFIX+safe_class_name,
        new ObjectType(MEMORY_TRACKER_CLASS),
        Constants.GETSTATIC));
    il.append(InstructionConstants.DUP);
    il.append(factory.createStore(Type.OBJECT, 1));
    BranchInstruction if_nonnull =
        factory.createBranchInstruction(Constants.IFNONNULL, null);
    il.append(if_nonnull);
    if (isStaticInit()) {
      il.append(factory.createReturn(Type.VOID));
    } else {
      int bytes = computeSize();
      InstructionHandle init_profiler = writeClassInit(il, bytes, true);
      il.append(factory.createStore(Type.OBJECT, 1));
    }
    InstructionHandle ih_add = il.append(factory.createLoad(Type.OBJECT, 1));
    if_nonnull.setTarget(ih_add);
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(factory.createInvoke(
          MEMORY_TRACKER_CLASS,
          ADD_METHOD,
          Type.VOID,
          new Type[] { Type.OBJECT },
          Constants.INVOKEVIRTUAL));
    il.append(factory.createReturn(Type.VOID));
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }

  private void callProfilerInConstructors() {
    // find all "Bar(..) {..}" constructors and update them
    //
    // there's always at least one constructor
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      if ("<init>".equals(m.getName())) {
        callProfilerInConstructor(m);
      }
    }
  }
  private void callProfilerInConstructor(Method orig_m) {
    // insert our "$profile_Bar()" call before all
    // "return" calls *unless* this constructor calls "this(..)"
    MethodGen method = new MethodGen(orig_m, class_name, cp);
    InstructionList orig_il = method.getInstructionList();
    for (Iterator iter = orig_il.iterator(); iter.hasNext(); ) {
      InstructionHandle ih = (InstructionHandle) iter.next();
      Instruction inst = ih.getInstruction();
      int opcode = inst.getOpcode();
      if (opcode == Constants.INVOKESPECIAL) {
        InvokeInstruction inv = (InvokeInstruction) inst;
        if (class_name.equals(inv.getClassName(cp)) &&
            "<init>".equals(inv.getMethodName(cp))) {
          // a call to "this(..)", so let the other constructor
          // call the profiler (so we don't double-count)
          return;
        }
      } else if (opcode == Constants.RETURN) {
        // insert "$profiler_Bar.add(this)" before return
        InstructionList il = new InstructionList();
        il.append(factory.createLoad(Type.OBJECT, 0));
        il.append(factory.createInvoke(
              class_name,
              PROFILE_METHOD_PREFIX+safe_class_name,
              Type.VOID,
              Type.NO_ARGS,
              Constants.INVOKEVIRTUAL));
        orig_il.insert(ih, il);
        il.dispose();
      }
    }
    method.setMaxStack();
    method.setMaxLocals();
    cg.replaceMethod(orig_m, method.getMethod());
    orig_il.dispose();
  }

  private void callProfilerInReadObject() {
    // find "readObject(ObjectInputStream)" and create
    // or update it
    if (!isSerial) {
      // not serializable, no need for "readObject"
      return;
    }
    Method read_object_method = null;
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      if ("readObject".equals(m.getName())) {
        Type[] args = m.getArgumentTypes();
        Type ois = new ObjectType("java.io.ObjectInputStream");
        if (args.length == 1 &&
            args[0].equals(ois)) {
          read_object_method = m;
          break;
        }
      }
    }
    if (read_object_method == null) {
      newReadObject();
    } else {
      updateReadObject(read_object_method);
    }
  }
  private void newReadObject() {
    // add:
    //   private void readObject(ObjectInputStream ois) {
    //     $profile_Bar();
    //     ois.defaultReadObject();
    //   }
    InstructionList il = new InstructionList();
    MethodGen method = new MethodGen(
        Constants.ACC_PRIVATE,
        Type.VOID,
        new Type[] { new ObjectType("java.io.ObjectInputStream") },
        new String[] { "stream" },
        "readObject",
        class_name,
        il,
        cp);
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(factory.createInvoke(
          class_name,
          PROFILE_METHOD_PREFIX+safe_class_name,
          Type.VOID,
          Type.NO_ARGS,
          Constants.INVOKEVIRTUAL));
    il.append(factory.createLoad(Type.OBJECT, 1));
    il.append(factory.createInvoke(
          "java.io.ObjectInputStream",
          "defaultReadObject",
          Type.VOID,
          Type.NO_ARGS,
          Constants.INVOKEVIRTUAL));
    il.append(factory.createReturn(Type.VOID));
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }
  private void updateReadObject(Method orig_m) {
    // begin with "$profiler_Bar()"
    MethodGen method = new MethodGen(orig_m, class_name, cp);
    InstructionList il = new InstructionList();
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(factory.createInvoke(
          class_name,
          PROFILE_METHOD_PREFIX+safe_class_name,
          Type.VOID,
          Type.NO_ARGS,
          Constants.INVOKEVIRTUAL));
    InstructionList orig_il = method.getInstructionList();
    orig_il.insert(il);
    il.dispose();
    method.setMaxStack();
    method.setMaxLocals();
    cg.replaceMethod(orig_m, method.getMethod());
    orig_il.dispose();
  }
  private void callProfilerInClone() {
    // find "clone()" and create/update it
    Method clone_method = null;
    Method[] methods = cg.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method m = methods[i];
      if ("clone".equals(m.getName()) &&
          m.getArgumentTypes().length == 0) {
        clone_method = m;
        break;
      }
    }
    if (clone_method == null) {
      newClone();
    } else if (
        (clone_method.getAccessFlags() &
         Constants.ACC_ABSTRACT) != 0) {
      // abstract "clone()" method, even though Object defines
      // a non-abstract impl.  This is valid to force subclass
      // override of the default impl.
    } else {
      updateClone(clone_method);
    }
  }
  private void newClone() {
    // we must add a "clone()" to all classes, since Object defines
    // this method.  We add:
    //   public Object clone() throws CloneNotSupportedException {
    //     Object o = super.clone();
    //     if (o instanceof Bar) {
    //       ((Bar) o).$profile_Bar();
    //     }
    //     return o;
    //   }
    //
    // It's not clear if we always need this method. Checking for
    // "instanceof Cloneable" might be sufficient, but then we risk
    // a subclass implementing Cloneable and we'd miss tracking
    // that subclass.  For now it's easiest to simply add a clone
    // method to all classes.
    InstructionList il = new InstructionList();
    MethodGen method = new MethodGen(
        Constants.ACC_PUBLIC,
        Type.OBJECT,
        Type.NO_ARGS,
        new String[] {},
        "clone",
        class_name,
        il,
        cp);
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(factory.createInvoke(
          "java.lang.Object",
          "clone",
          Type.OBJECT,
          Type.NO_ARGS,
          Constants.INVOKESPECIAL));
    il.append(factory.createDup(1));
    il.append(new INSTANCEOF(
          cp.addClass(new ObjectType(class_name))));
    BranchHandle ifeq = (BranchHandle)
      il.append(
          factory.createBranchInstruction(Constants.IFEQ, null));
    il.append(factory.createDup(1));
    il.append(factory.createCheckCast(new ObjectType(class_name)));
    il.append(factory.createInvoke(
          class_name,
          PROFILE_METHOD_PREFIX+safe_class_name,
          Type.VOID,
          Type.NO_ARGS,
          Constants.INVOKEVIRTUAL));
    InstructionHandle ret =
      il.append(factory.createReturn(Type.OBJECT));
    ifeq.setTarget(ret);
    // must note "throws", otherwise javap complains:
    method.addException("java.lang.CloneNotSupportedException");
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }
  private void updateClone(Method orig_m) {
    MethodGen method = new MethodGen(orig_m, class_name, cp);
    InstructionList orig_il = method.getInstructionList();
    boolean changed = false;
    for (Iterator iter = orig_il.iterator(); iter.hasNext(); ) {
      InstructionHandle ih = (InstructionHandle) iter.next();
      Instruction inst = ih.getInstruction();
      if (inst.getOpcode() != Constants.INVOKESPECIAL) {
        continue;
      }
      InvokeInstruction inv = (InvokeInstruction) inst;
      if (!"clone".equals(inv.getMethodName(cp))) {
        continue;
      }
      changed = true;
      // after all:
      //   Object o = super.clone();
      // append:
      //   if (o instanceof Bar) {
      //     ((Bar) o).$profile_Bar();
      //   }
      //
      // Ideally we'd do this just prior to the "return o", but
      // we'd need to make sure we only track the object if
      // it was created by "super.clone()".  If the code looks
      // like:
      //    return new Bar();
      // then we must let that constructor do the profiling, to
      // avoid double-counting the instance.  The downside of this
      // approach is that we may see a partially initialized
      // clone, but that's probably fine.
      InstructionList il = new InstructionList();
      il.append(factory.createDup(1));
      il.append(
          new INSTANCEOF(cp.addClass(new ObjectType(class_name))));
      il.append(factory.createBranchInstruction(
            Constants.IFEQ, ih.getNext()));
      il.append(factory.createDup(1));
      il.append(factory.createCheckCast(new ObjectType(class_name)));
      il.append(factory.createInvoke(
            class_name,
            PROFILE_METHOD_PREFIX+safe_class_name,
            Type.VOID,
            Type.NO_ARGS,
            Constants.INVOKEVIRTUAL));
      orig_il.append(ih, il);
      il.dispose();
    }
    if (changed) {
      method.setMaxStack();
      method.setMaxLocals();
      cg.replaceMethod(orig_m, method.getMethod());
    }
    orig_il.dispose();
  }

  private void recordArraysAllocations() {
    // Java lacks an "Array" class to represent arrays, so
    // we can't modify a single classes constructor.  Instead
    // we must modify all the clients!
    //
    // for all methods {
    //   after every:
    //     push length
    //     new<type>array
    //   append:
    //     dup
    //     $PROFILER_ARRAYS.new<type>array
    // }
    //
    // Arrays may also be allocated in native code...
    throw new UnsupportedOperationException("Not implement yet");
  }
}
