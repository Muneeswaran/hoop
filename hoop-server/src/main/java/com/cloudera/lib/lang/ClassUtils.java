/*
 * Copyright (c) 2011, Cloudera, Inc. All Rights Reserved.
 *
 * Cloudera, Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the
 * License.
 */
package com.cloudera.lib.lang;

import com.cloudera.lib.io.IOUtils;
import com.cloudera.lib.server.ServiceException;
import com.cloudera.lib.util.Check;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Class related utilities.
 */
public class ClassUtils {

  /**
   * Finds the JAR file containing a class.
   *
   * @param klass class to find its JAR.
   * @return the path to the JAR.
   */
  public static String getJar(Class klass) {
    Check.notNull(klass, "klass");
    ClassLoader loader = klass.getClassLoader();
    if (loader != null) {
      String class_file = klass.getName().replaceAll("\\.", "/") + ".class";
      try {
        for (Enumeration itr = loader.getResources(class_file); itr.hasMoreElements(); ) {
          URL url = (URL) itr.nextElement();
          if ("jar".equals(url.getProtocol())) {
            String toReturn = url.getPath();
            if (toReturn.startsWith("file:")) {
              toReturn = toReturn.substring("file:".length());
            }
            toReturn = URLDecoder.decode(toReturn, "UTF-8");
            return toReturn.replaceAll("!.*$", "");
          }
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * Convenience method that returns a resource as inputstream from the
   * classpath.
   * <p/>
   * It first attempts to use the Thread's context classloader and if not
   * set it uses the <code>ClassUtils</code> classloader.
   *
   * @param name resource to retrieve.
   * @return inputstream with the resource, NULL if the resource does not
   * exist.
   */
  public static InputStream getResource(String name) {
    Check.notEmpty(name, "name");
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = ClassUtils.class.getClassLoader();
    }
    return cl.getResourceAsStream(name);
  }

  /**
   * Creates a JAR file with the specified classes.
   *
   * @param jarFile jar file path.
   * @param classes classes to add to the JAR.
   * @throws IOException thrown if an IO error occurred.
   */
  public static void createJar(File jarFile, Class... classes) throws IOException {
    Check.notNull(jarFile, "jarFile");
    File jarDir = jarFile.getParentFile();
    if (!jarDir.exists()) {
      if (!jarDir.mkdirs()) {
        throw new IOException(MessageFormat.format("could not create dir [{0}]", jarDir));
      }
    }
    createJar(new FileOutputStream(jarFile), classes);
  }

  /**
   * Writes the specified classes to an outputstream.
   *
   * @param os outputstream to write the classes to.
   * @param classes classes to write to the outputstream.
   * @throws IOException thrown if an IO error occurred.
   */
  public static void createJar(OutputStream os, Class... classes) throws IOException {
    Check.notNull(os, "os");
    File classesDir = File.createTempFile("createJar", "classes");
    if (!classesDir.delete()) {
      throw new IOException(MessageFormat.format("could not delete temp file [{0}]", classesDir));
    }
    for (Class clazz : classes) {
      String classPath = clazz.getName().replace(".", "/") + ".class";
      String classFileName = classPath;
      if (classPath.lastIndexOf("/") > -1) {
        classFileName = classPath.substring(classPath.lastIndexOf("/") + 1);
      }
      String packagePath = new File(classPath).getParent();
      File dir = new File(classesDir, packagePath);
      if (!dir.exists()) {
        if (!dir.mkdirs()) {
          throw new IOException(MessageFormat.format("could not create dir [{0}]", dir));
        }
      }
      InputStream is = getResource(classPath);
      OutputStream classOS = new FileOutputStream(new File(dir, classFileName));
      IOUtils.copy(is, classOS);
    }
    JarOutputStream zos = new JarOutputStream(os, new Manifest());
    IOUtils.zipDir(classesDir, "", zos);
    IOUtils.delete(classesDir);
  }

  /**
   * Finds a public-static method by name in a class.
   * <p/>
   * In case of method overloading it will return the first method found.
   *
   * @param className name to look for the method.
   * @param methodName method name to look in the class.
   * @return the <code>Method</code> instance.
   * @throws IllegalArgumentException thrown if the method does not exist,
   * it is not public or it is not static.
   */
  public static Method findMethod(String className, String methodName) {
    Check.notEmpty(className, "className");
    Check.notEmpty(methodName, "methodName");
    Method method = null;
    try {
      Class klass = Thread.currentThread().getContextClassLoader().loadClass(className);
      for (Method m : klass.getMethods()) {
        if (m.getName().equals(methodName)) {
          method = m;
          break;
        }
      }
      if (method == null) {
        throw new IllegalArgumentException(MessageFormat.format("class#method not found [{0}#{1}]", className,
                                                                methodName));
      }
      if ((method.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != (Modifier.PUBLIC | Modifier.STATIC)) {
        throw new IllegalArgumentException(MessageFormat.format(
          "class#method does not have PUBLIC or STATIC modifier [{0}#{1}]", className, methodName));
      }
    }
    catch (ClassNotFoundException ex) {
      throw new IllegalArgumentException(MessageFormat.format("class not found [{0}]", className));
    }
    return method;
  }

  /**
   * Finds a constant by name in a class.
   * <p/>
   *
   * @param className name to look for the method.
   * @param constantName constant name to look in the class.
   * @return the constant instance.
   * @throws IllegalArgumentException thrown if the constant does not exist,
   * it is not public or it is not static.
   */
  public static Object findConstant(String className, String constantName) throws ServiceException {
    Check.notEmpty(className, "className");
    Check.notEmpty(constantName, "constantName");
    try {
      Class klass = Thread.currentThread().getContextClassLoader().loadClass(className);
      Field field = klass.getField(constantName);
      if ((field.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) != (Modifier.PUBLIC | Modifier.STATIC)) {
        throw new IllegalArgumentException(MessageFormat.format(
          "class#constant does not have PUBLIC or STATIC modifier [{0}#{1}]", className, constantName));
      }
      return field.get(null);
    }
    catch (IllegalAccessException ex) {
      throw new IllegalArgumentException(ex);
    }
    catch (NoSuchFieldException ex) {
      throw new IllegalArgumentException(MessageFormat.format("class#constant not found [{0}#{1}]", className,
                                                              constantName));
    }
    catch (ClassNotFoundException ex) {
      throw new IllegalArgumentException(MessageFormat.format("class not found [{0}]", className));
    }
  }
}
