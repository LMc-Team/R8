// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexMoveWide extends DexFormat12x {

  public static final int OPCODE = 0x4;
  public static final String NAME = "MoveWide";
  public static final String SMALI_NAME = "move-wide";

  DexMoveWide(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexMoveWide(int dest, int src) {
    super(dest, src);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addMove(ValueTypeConstraint.LONG_OR_DOUBLE, A, B);
  }
}
