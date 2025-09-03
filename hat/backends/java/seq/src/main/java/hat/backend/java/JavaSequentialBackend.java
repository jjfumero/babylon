/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package hat.backend.java;

import hat.NDRange;
import hat.callgraph.KernelCallGraph;
import hat.callgraph.KernelEntrypoint;

import java.lang.reflect.InvocationTargetException;

public class JavaSequentialBackend extends JavaBackend {

    private void run(KernelEntrypoint kernelEntrypoint, NDRange ndRange, Object... args) {
        try {
            args[0] = ndRange.kid;
            kernelEntrypoint.method.invoke(null, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void dispatch1D(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... args) {
        KernelEntrypoint kernelEntrypoint = kernelCallGraph.entrypoint;
        for (ndRange.kid.x = 0; ndRange.kid.x < ndRange.kid.maxX; ndRange.kid.x++) {
            run(kernelEntrypoint, ndRange, args);
        }
    }

    private void dispatch2D(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... args) {
        KernelEntrypoint kernelEntrypoint = kernelCallGraph.entrypoint;
        for (ndRange.kid.x = 0; ndRange.kid.x < ndRange.kid.maxX; ndRange.kid.x++) {
            for (ndRange.kid.y = 0; ndRange.kid.y < ndRange.kid.maxY; ndRange.kid.y++) {
                run(kernelEntrypoint, ndRange, args);
            }
        }
    }

    private void dispatch3D(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... args) {
        KernelEntrypoint kernelEntrypoint = kernelCallGraph.entrypoint;
        for (ndRange.kid.x = 0; ndRange.kid.x < ndRange.kid.maxX; ndRange.kid.x++) {
            for (ndRange.kid.y = 0; ndRange.kid.y < ndRange.kid.maxY; ndRange.kid.y++) {
                for (ndRange.kid.z = 0; ndRange.kid.z < ndRange.kid.maxZ; ndRange.kid.z++) {
                    run(kernelEntrypoint, ndRange, args);
                }
            }
        }
    }

    @Override
    public void dispatchKernel(KernelCallGraph kernelCallGraph, NDRange ndRange, Object... args) {
        // Dispatch 1D, 2D or 3D
        int dimensions = ndRange.kid.getDimensions();
        switch (dimensions) {
            case 1 -> dispatch1D(kernelCallGraph, ndRange, args);
            case 2 -> dispatch2D(kernelCallGraph, ndRange, args);
            case 3 -> dispatch3D(kernelCallGraph, ndRange, args);
            default -> throw new IllegalArgumentException("Invalid dimensions " + dimensions);
        }
    }
}

