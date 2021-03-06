// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TraceReferencesResult {

  private final Set<TracedClass> types;
  private final Map<ClassReference, Set<TracedField>> fields;
  private final Map<ClassReference, Set<TracedMethod>> methods;
  private final Set<PackageReference> keepPackageNames;

  TraceReferencesResult(
      Set<TracedClass> types,
      Map<ClassReference, Set<TracedField>> fields,
      Map<ClassReference, Set<TracedMethod>> methods,
      Set<PackageReference> keepPackageNames) {
    this.types = types;
    this.fields = fields;
    this.methods = methods;
    this.keepPackageNames = keepPackageNames;
  }

  public Set<TracedClass> getTracedClasses() {
    return types;
  }

  public Map<ClassReference, Set<TracedField>> getTracedFields() {
    return fields;
  }

  public Map<ClassReference, Set<TracedMethod>> getTracedMethods() {
    return methods;
  }

  public Set<PackageReference> getTracedPackageNames() {
    return keepPackageNames;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements TraceReferencesConsumer {
    private final Set<TracedClass> types = new HashSet<>();
    private final Map<ClassReference, Set<TracedField>> fields = new HashMap<>();
    private final Map<ClassReference, Set<TracedMethod>> methods = new HashMap<>();
    private final Set<PackageReference> keepPackageNames = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
      types.add(tracedClass);
    }

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      FieldReference field = tracedField.getReference();
      fields.computeIfAbsent(field.getHolderClass(), k -> new HashSet<>()).add(tracedField);
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      MethodReference method = tracedMethod.getReference();
      methods.computeIfAbsent(method.getHolderClass(), k -> new HashSet<>()).add(tracedMethod);
    }

    @Override
    public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
      keepPackageNames.add(pkg);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}

    public TraceReferencesResult build() {
      return new TraceReferencesResult(types, fields, methods, keepPackageNames);
    }
  }
}
