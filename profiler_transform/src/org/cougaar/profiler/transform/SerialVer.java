/*
 * <copyright>
 *  Copyright ShiftOne-JRat 0.6
 *  Author Jeff Drost (jeff@shiftone.org)
 *  Modified from org.shiftone.jrat.inject.BcelUtil
 * </copyright>
 */
package org.cougaar.profiler.transform;

import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Synthetic;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Compute the serialVersionUID for a BCEL JavaClass. 
 */
public class SerialVer {

  private static final Comparator FIELD_OR_METHOD_COMPARATOR =
    new FieldOrMethodComparator();

  private SerialVer() {}

  /**
   * This method computes the serialVersionUID of a BCEL JavaClass in
   * the same way that the java.io.ObjectStreamClass class computes it
   * for a java.lang.Class.  This allows the ClassInjector to
   * calculate this w/o actually loading the class.
   * <p>
   * This method is a port of version 1.98 of the ObjectStreamClass's
   * computeDefaultSUID method.
   * <p>
   * Compute a hash for the specified class.  Incrementally add items
   * to the hash accumulating in the digest stream.  Fold the hash
   * into a long.  Use the SHA secure hash function.
   *
   * @param javaClass .
   * @return .
   * @throws InternalError .
   * @throws SecurityException .
   */
  public static long computeSerialVersionUID(JavaClass javaClass) {
    try {
      ByteArrayOutputStream bout    = new ByteArrayOutputStream();
      DataOutputStream      dout    = new DataOutputStream(bout);
      Method[]              methods = javaClass.getMethods();
      Field[]               fields  = javaClass.getFields();
      //
      dout.writeUTF(javaClass.getClassName());
      int classMods = javaClass.getAccessFlags();
      classMods &= 
        (Modifier.PUBLIC |
         Modifier.FINAL |
         Modifier.INTERFACE |
         Modifier.ABSTRACT);
      // compensate for javac bug in which ABSTRACT bit was set for an
      // interface only if the interface declared methods
      if ((classMods & Modifier.INTERFACE) != 0) {
        classMods = 
          (methods.length > 0) ?
          (classMods | Modifier.ABSTRACT) :
          (classMods & ~Modifier.ABSTRACT);
      }
      dout.writeInt(classMods);
      if (true) {
        // compensate for change in 1.2FCS in which
        // Class.getInterfaces() was modified to return Cloneable and
        // Serializable for array classes.
        String[] ifaceNames = javaClass.getInterfaceNames();
        Arrays.sort(ifaceNames);
        for (int i = 0; i < ifaceNames.length; i++) {
          dout.writeUTF(ifaceNames[i]);
        }
      }
      // ----------------------------------------------------------------
      // fields
      Arrays.sort(fields, FIELD_OR_METHOD_COMPARATOR);
      for (int i = 0; i < fields.length; i++) {
        Field field = fields[i];
        int   mods = fields[i].getAccessFlags();
        if (((mods & Modifier.PRIVATE) == 0) ||
            ((mods & (Modifier.STATIC | Modifier.TRANSIENT)) == 0)) {
          dout.writeUTF(field.getName());
          dout.writeInt(mods);
          dout.writeUTF(field.getSignature());
        }
      }
      // ----------------------------------------------------------------
      // methods
      Arrays.sort(methods, FIELD_OR_METHOD_COMPARATOR);
      for (int i = 0; i < methods.length; i++) {
        Method method = methods[i];
        int    mods = method.getAccessFlags();
        if ((mods & Modifier.PRIVATE) == 0) {
          dout.writeUTF(method.getName());
          dout.writeInt(mods);
          dout.writeUTF(method.getSignature().replace('/', '.'));
        }
      }
      // ----------------------------------------------------------------
      // closing
      dout.flush();
      MessageDigest md        = MessageDigest.getInstance("SHA");
      byte[]        hashBytes = md.digest(bout.toByteArray());
      long          hash      = 0;
      for (int i = Math.min(hashBytes.length, 8) - 1; i >= 0; i--) {
        hash = (hash << 8) | (hashBytes[i] & 0xFF);
      }
      return hash;
    } catch (IOException ex) {
      throw new InternalError();
    } catch (NoSuchAlgorithmException ex) {
      throw new SecurityException(ex.getMessage());
    }
  }

  private static class FieldOrMethodComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      FieldOrMethod fom1 = (FieldOrMethod) o1;
      FieldOrMethod fom2 = (FieldOrMethod) o2;
      int           comp = fom1.getName().compareTo(fom2.getName());
      if (comp == 0) {
        comp = fom1.getSignature().compareTo(fom2.getSignature());
      }
      return comp;
    }
  }
}
