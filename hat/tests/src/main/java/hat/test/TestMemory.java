/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package hat.test;

import hat.Accelerator;
import hat.Accelerator.Compute;
import hat.ComputeContext;
import hat.KernelContext;
import hat.NDRange;
import hat.backend.Backend;
import hat.buffer.F32Array;
import hat.test.annotation.HatTest;
import hat.test.exceptions.HATAsserts;
import jdk.incubator.code.Reflect;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class TestMemory {

    @Reflect
    public static void multTwo(F32Array in, F32Array out) {
        final int idx = KernelContext.GIX();
        out.array(idx, in.array(idx) * 2.0f);
    }

    @Reflect
    public static void multTwo(ComputeContext cc, F32Array in, F32Array out, int n) {
        cc.dispatchKernel(NDRange.of1D(n), () -> multTwo(in, out));
    }

    @HatTest
    public void test01() {
        var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST);

        final int n = 8;
        float[] hostArray = new float[n];
        for (int i = 0; i < n; i++) {
            hostArray[i] = i + 2.0f;
        }

        F32Array input = F32Array.create(accelerator, n);
        input.copyFrom(hostArray);
        F32Array output = F32Array.create(accelerator, n);

        accelerator.compute((@Reflect Compute)
                cc -> multTwo(cc, input, output, n));

        float[] hostOutput = new float[n];
        output.copyTo(hostOutput);

        IO.println(Arrays.toString(hostArray));
        IO.println(Arrays.toString(input.arrayView()));
        IO.println(Arrays.toString(hostOutput));
        IO.println(Arrays.toString(output.arrayView()));
        
        for (int i = 0; i < n; i++) {
            HATAsserts.assertEquals(hostArray[i] * 2.0f, output.array(i), 0.0f);
        }

    }

}
