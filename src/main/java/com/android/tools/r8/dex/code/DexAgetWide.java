// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexAgetWide extends DexFormat23x {

  public static final int OPCODE = 0x45;
  public static final String NAME = "AgetWide";
  public static final String SMALI_NAME = "aget-wide";

  /*package*/ DexAgetWide(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexAgetWide(int AA, int BB, int CC) {
    super(AA, BB, CC);
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
    builder.addArrayGet(MemberType.LONG_OR_DOUBLE, AA, BB, CC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
