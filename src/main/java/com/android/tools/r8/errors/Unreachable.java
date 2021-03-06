// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

/**
 * Exception to signal an expected unreachable code path.
 */
public class Unreachable extends InternalCompilerError {

  public static Unreachable raise() {
    throw new Unreachable();
  }

  public static Unreachable raise(Object... ignore) {
    throw new Unreachable();
  }

  public Unreachable() {
  }

  public Unreachable(String s) {
    super(s);
  }

  public Unreachable(Throwable cause) {
    super(cause);
  }
}
