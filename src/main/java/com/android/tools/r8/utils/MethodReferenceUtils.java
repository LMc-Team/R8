// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;

public class MethodReferenceUtils {

  public static MethodReference mainMethod(ClassReference type) {
    ArrayReference stringArrayType = Reference.array(Reference.classFromClass(String.class), 1);
    return Reference.method(type, "main", ImmutableList.of(stringArrayType), null);
  }

  public static String toSourceStringWithoutHolderAndReturnType(MethodReference methodReference) {
    return toSourceString(methodReference, false, false);
  }

  public static String toSourceString(MethodReference methodReference) {
    return toSourceString(methodReference, true, true);
  }

  public static String toSourceString(
      MethodReference methodReference, boolean includeHolder, boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    if (includeReturnType) {
      builder
          .append(
              methodReference.getReturnType() != null
                  ? methodReference.getReturnType().getTypeName()
                  : "void")
          .append(" ");
    }
    if (includeHolder) {
      builder.append(methodReference.getHolderClass().getTypeName()).append(".");
    }
    builder.append(methodReference.getMethodName()).append("(");
    Iterator<TypeReference> formalTypesIterator = methodReference.getFormalTypes().iterator();
    if (formalTypesIterator.hasNext()) {
      builder.append(formalTypesIterator.next().getTypeName());
      while (formalTypesIterator.hasNext()) {
        builder.append(", ").append(formalTypesIterator.next().getTypeName());
      }
    }
    return builder.append(")").toString();
  }
}
