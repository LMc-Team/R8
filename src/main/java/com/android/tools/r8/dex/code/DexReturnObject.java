// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexReturnObject extends DexFormat11x {

  public static final int OPCODE = 0x11;
  public static final String NAME = "ReturnObject";
  public static final String SMALI_NAME = "return-object";

  DexReturnObject(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexReturnObject(int AA) {
    super(AA);
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
  public int[] getTargets() {
    return EXIT_TARGET;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addReturn(AA);
  }
}
