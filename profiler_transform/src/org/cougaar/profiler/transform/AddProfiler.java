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
import java.util.Iterator;
import java.util.List;
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
 * instance allocation.
 * <p>
 * The basic idea is to transform a class like:<pre>
 *   public class Bar extends Foo {
 *     public Bar() {
 *       System.out.println("Hello, world!");
 *     }
 *   }
 * </pre>
 * to:<pre>
 *   import org.cougaar.profiler.MemoryTracker;
 *   public class Bar extends Foo {
 *     public Bar() {
 *       System.out.println("Hello, world!");
 *       MY_PROFILER.add(this);
 *     }
 *     private static final MemoryTracker MY_PROFILER =
 *       MemoryTracker.getInstance("Bar");
 *   }
 * </pre>
 * The full transform is more complex, since we must allow
 * for "clone()" and "readObject()".  We must also subclassing,
 * for example, if "Foo" is profiled, we don't want
 * to count Bar in Foo's metrics.  We add a serialVersionUID
 * if necessary to preserve serialization compatibility.
 * Lastly, we also tell the MemoryTracker the estimated object
 * size (based upon field count) and support custom "size()"
 * and "capacity()" methods.
 */
public class AddProfiler {

  /**
   * MemoryTracker API constants.
   * <p>
   * The API should look like:<pre>
   *   package org.cougaar.profiler;
   *   public class MemoryTracker {
   *     // on instance allocation:
   *     public void add(Object o) {..}
   *
   *     // methods to get a tracker:
   *     public static MemoryTracker getInstance(
   *         String type,
   *         int bytes) {..}
   *     public static MemoryTracker getInstance(
   *         String type,
   *         int bytes,
   *         boolean has_size,
   *         boolean has_capacity) {..}
   *   }
   *   public interface Size {
   *     public int $get_size();
   *   }
   *   public interface Capacity {
   *     public int $get_capacity();
   *     public int $get_capacity_bytes();
   *   }
   * </pre>
   */
  private static final String MEMORY_TRACKER_CLASS =
    "org.cougaar.profiler.MemoryTracker";
  private static final String GET_TRACKER_INSTANCE =
    "getInstance";
  private static final String ADD_TO_TRACKER =
    "add";

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
   *     public int $get_capacity() {
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
   * </pre>  The "$get_capacity" method is added if the class or its
   * parents contain non-static arrays.  "$get_capacity_bytes"
   * multiplies each array by its element byte size and adds the
   * array header size.
   */
  // RFE: maybe make this a command-line option
  private static final boolean ENABLE_SIZE_AND_CAPACITY = true;
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

  // RFE: convert "newarray" to "org.cougaar.profiler.Arrays"?

  /**
   * The name of our static profiler field and profiling
   * method.
   */
  private static final String PROFILER_FIELD_PREFIX =
    "$PROFILER_";
  private static final String PROFILE_METHOD_PREFIX =
    "$profile_";

  // add "serialVersionUID" if it's missing
  private static final boolean ENSURE_SERIAL_VERSION_UID = true;

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

  public static void main(String[] argv) throws Exception {
    if (argv.length == 0) {
      System.err.println(
          "Usage: AddProfiler [-|FILE]\n"+
          "\n"+
          "Reads a Java class file, outputs a new class\n"+
          "file with added MemoryTracker calls.  If \"-\"\n"+
          "is specified then STDIN is used.\n"+
          "\n"+
          "Example usage:\n"+
          "  cat Bar.class |\\\n"+
          "    java -classpath bcel-5.1.jar:.\\\n"+
          "    AddProfiler -\\\n"+
          "    > tmp/Bar.class\n"+
          "\n"+
          "Also see the ProfileAll class to modify more than\n"+
          "one class using `find`");
      return;
    }

    String name = argv[0];
    modifyClass(
        name,
        System.in,
        System.out);
  }

  public static void modifyClass(
      String name,
      InputStream in,
      OutputStream out) throws Exception {
    JavaClass java_class;
    if ("-".equals(name)) {
      java_class = new ClassParser(in, "-").parse();
    } else {
      if ((java_class = Repository.lookupClass(name)) == null) {
        // may throw IOException
        java_class = new ClassParser(name).parse();
      }
    }

    AddProfiler addprofiler = new AddProfiler();
    JavaClass new_java_class = addprofiler.modifyClass(java_class);

    // pretty printer:
    //BCELifier bcelifier = new BCELifier(new_java_class, System.out);
    //bcelifier.start();

    // raw ".class" output:
    new_java_class.dump(out);
  }

  public AddProfiler() { }

  /**
   * See org.apache.bcel.util.ClassLoader for dynamic modification.
   *
   * Code outline for ClassLoader:
   *   protected Class loadClass(..) {..
   *     if (class_name matches something we're interested in) {
   *       JavaClazz clazz = null;
   *       if ((clazz = repository.loadClass(class_name)) != null) {
   *         clazz = (new AddProfiler()).modifyClass(clazz);
   *       } ..
   *       if (clazz != null) {
   *	     byte[] bytes  = clazz.getBytes();
   *         cl = defineClass(class_name, bytes, 0, bytes.length);
   *       }..
   *     }..
   *   }
   * This adds a slight runtime overhead, plus might have security
   * problems.
   */
  public JavaClass modifyClass(JavaClass clazz) {
    if (clazz.isInterface()) {
      // we only track objects
      return clazz;
    }
    setClassNames(clazz);
    if (hasProfilerField(clazz)) {
      // already modified
      return clazz;
    }
    createClassGen(clazz);
    run();
    JavaClass ret = cg.getJavaClass();
    reset();
    return ret;
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
      (isSerial ? computeSerialVersionUID(clazz) : -1);

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

    if (ENABLE_SIZE_AND_CAPACITY) {
      // add "$get_size()" and "$get_capacity()" methods
      defineSize();
      defineCapacity();
    }

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

  private boolean hasProfilerField(JavaClass clazz) {
    // look for:
    //   private static final MemoryTracker $PROFILER_Bar;
    Field[] fields = clazz.getFields();
    String matchname = PROFILER_FIELD_PREFIX+safe_class_name;
    for (int i = 0; i < fields.length; i++) {
      Field f = fields[i];
      if ((f.getAccessFlags() ==
            (Constants.ACC_PRIVATE |
             Constants.ACC_STATIC |
             Constants.ACC_FINAL)) &&
          f.getType().equals(
            new ObjectType(MEMORY_TRACKER_CLASS)) &&
          f.getName().equals(matchname)) {
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
    if (!ENSURE_SERIAL_VERSION_UID) {
      return -1;
    }
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

  private void defineSize() {
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
    BranchInstruction ifnull = null;
    for (int k = arrays.size() - 1; k >= 0; k--) {
      Field f = (Field) arrays.get(k);
      InstructionHandle load_this =
        il.append(factory.createLoad(Type.OBJECT, 0));
      if (ifnull != null) {
        ifnull.setTarget(load_this);
      }
      il.append(
          factory.createFieldAccess(
            class_name,
            f.getName(),
            f.getType(),
            Constants.GETFIELD));
      il.append(factory.createDup(1));
      il.append(factory.createStore(Type.OBJECT, 1));
      ifnull =
        factory.createBranchInstruction(Constants.IFNULL, null);
      il.append(ifnull);
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
    if (ifnull != null) {
      ifnull.setTarget(ret_int);
    }
    method.setMaxStack();
    method.setMaxLocals();
    cg.addMethod(method.getMethod());
    il.dispose();
  }

  private void defineProfilerField() {
    // add:
    //   private static final MemoryTracker $PROFILER_Bar;
    FieldGen field =
      new FieldGen(
          (Constants.ACC_PRIVATE |
           Constants.ACC_STATIC |
           Constants.ACC_FINAL),
          new ObjectType(MEMORY_TRACKER_CLASS),
          PROFILER_FIELD_PREFIX+safe_class_name,
          cp);
    cg.addField(field.getField());
  }

  private void initProfilerField() {
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
    writeClassInit(il, bytes);
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
    writeClassInit(il, bytes);
    InstructionList orig_il = method.getInstructionList();
    orig_il.insert(il);
    il.dispose();
    method.setMaxStack();
    method.setMaxLocals();
    cg.replaceMethod(orig_m, method.getMethod());
    orig_il.dispose();
  }
  private void writeClassInit(InstructionList il, int bytes) {
    il.append(new PUSH(cp, class_name));
    il.append(new PUSH(cp, bytes));
    Type[] args;
    if (!has_size && !has_capacity) {
      args = new Type[] { Type.STRING, Type.INT };
    } else {
      args = new Type[] {
        Type.STRING, Type.INT, Type.BOOLEAN, Type.BOOLEAN};
      il.append(new PUSH(cp, has_size));
      il.append(new PUSH(cp, has_capacity));
    }
    il.append(factory.createInvoke(
          MEMORY_TRACKER_CLASS,
          GET_TRACKER_INSTANCE,
          new ObjectType(MEMORY_TRACKER_CLASS),
          args,
          Constants.INVOKESTATIC));
    il.append(factory.createFieldAccess(
          class_name,
          PROFILER_FIELD_PREFIX+safe_class_name,
          new ObjectType(MEMORY_TRACKER_CLASS),
          Constants.PUTSTATIC));
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
    // add:
    //   protected void $profile_Bar() {
    //     if ($PROFILER_Bar != null) {
    //       $PROFILER_Bar.add(this);
    //     }
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
    il.append(factory.createDup(1));
    il.append(factory.createStore(Type.OBJECT, 1));
    BranchInstruction ifnull =
      factory.createBranchInstruction(Constants.IFNULL, null);
    il.append(ifnull);
    il.append(factory.createLoad(Type.OBJECT, 1));
    il.append(factory.createLoad(Type.OBJECT, 0));
    il.append(factory.createInvoke(
          MEMORY_TRACKER_CLASS,
          ADD_TO_TRACKER,
          Type.VOID,
          new Type[] { Type.OBJECT },
          Constants.INVOKEVIRTUAL));
    InstructionHandle ret =
      il.append(factory.createReturn(Type.VOID));
    ifnull.setTarget(ret);
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
}
