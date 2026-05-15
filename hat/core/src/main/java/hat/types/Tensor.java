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
package hat.types;

import hat.buffer.F16Array;
import hat.buffer.F32Array;
import hat.buffer.F32ArrayPadded;
import optkl.IfaceValue;

// Tensors are immutable
public record Tensor(Shape shape, Class<?> klass, Access tensorAccess) implements IfaceValue {

    public static Shape shape(int dim1, int dim2, int dim3) {
        return new Shape(dim1, dim2, dim3);
    }

    public static Tensor create(Shape shape, final Access tensorAccess) {
        return new Tensor(shape, null, tensorAccess);
    }

    public static Tensor create(final Access tensorAccess) {
        return new Tensor(null, null, tensorAccess);
    }

    public static Tensor create(Shape shape, Class<?> klass) {
        return new Tensor(shape, klass, null);
    }

    // What do we do? a = fill(a, v)? or void fill(a, v)?
    // If we say tensors are immutable, we should return a value
    public static void fill(Tensor acc, float value) {
    }

    public static void mma(Tensor result, Tensor tensorA, Tensor tensorB, Tensor acc) {
    }

    public static Tensor loadF16(F16Array matrix, int i, int j, int ld) {
        return null;
    }

    public static Tensor loadF16(F16Array matrix, int i, int j, int ld, Shape shape) {
        return null;
    }

    public static void store(F32Array matrix, int i, int j, Tensor resultTensor, int ld, Access tensorAccess) {
    }

    public static void store(F32ArrayPadded matrix, int i, int j, Tensor resultTensor, int ld, Access tensorAccess) {
    }

    public record Shape(int x, int y, int z) {
    }

    public interface Access { }

    public record ColumMajor() implements Access {
    }

    public record RowMajor() implements Access {
    }

    public static ColumMajor ofColumnMajor() {
        return new ColumMajor();
    }

    public static RowMajor ofRowMajor() {
        return new RowMajor();
    }

}
