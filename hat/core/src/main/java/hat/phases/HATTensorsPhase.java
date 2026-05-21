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
package hat.phases;

import hat.dialect.HATTensorOp.TensorShapeOp;
import hat.types.Tensor;
import jdk.incubator.code.Block;
import jdk.incubator.code.CodeElement;
import jdk.incubator.code.Op;
import jdk.incubator.code.Value;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.core.CoreType;
import jdk.incubator.code.dialect.core.VarType;
import jdk.incubator.code.dialect.java.JavaOp;
import jdk.incubator.code.dialect.java.PrimitiveType;
import optkl.OpHelper;
import optkl.Trxfmr;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.BiConsumer;

import static hat.dialect.HATTensorOp.TensorCreateOp;
import static hat.dialect.HATTensorOp.TensorFillOp;
import static hat.dialect.HATTensorOp.TensorLoadOp;
import static hat.dialect.HATTensorOp.TensorMMAOp;
import static hat.dialect.HATTensorOp.TensorStoreLoadOp;
import static hat.dialect.HATTensorOp.TensorStoreOp;
import static hat.dialect.HATTensorOp.TensorVarLoadOp;
import static hat.dialect.HATTensorOp.TensorVarOp;
import static jdk.incubator.code.dialect.core.CoreOp.varLoad;
import static jdk.incubator.code.dialect.java.JavaType.VOID;


public record HATTensorsPhase() implements HATPhase {

    private interface TensorTransformer {

        void transform(Block.Builder blockBuilder, Op op);

        default void replaceOp(Block.Builder blockBuilder, Op oldOp, Op newOp) {
            newOp.setLocation(oldOp.location());
            Op.Result newOpResult = blockBuilder.op(newOp);
            blockBuilder.context().mapValue(oldOp.result(), newOpResult);
        }
    }

    private static class TensorView implements TensorTransformer {

        @Override
        public void transform(Block.Builder blockBuilder, Op op) {
            List<Value> operands = blockBuilder.context().getValues(op.operands());
            switch (op) {
                case CoreOp.VarOp varOp -> replaceOp(blockBuilder, varOp, new TensorVarOp(varOp.varName(), varOp.resultType(), operands));
                case JavaOp.InvokeOp invokeOp -> replaceOp(blockBuilder, invokeOp, new TensorCreateOp(invokeOp.resultType(), operands));
                default -> blockBuilder.op(op);
            }
        }
    }

    private static class TensorFill implements TensorTransformer {

        @Override
        public void transform(Block.Builder blockBuilder, Op op) {
            List<Value> operands = blockBuilder.context().getValues(op.operands());
            switch (op) {
                case CoreOp.VarAccessOp.VarLoadOp loadOp -> replaceOp(blockBuilder, loadOp, new TensorVarLoadOp(loadOp.resultType(), operands));
                case JavaOp.InvokeOp invokeOp -> replaceOp(blockBuilder, invokeOp, new TensorFillOp(invokeOp.resultType(), operands));
                default -> blockBuilder.op(op);
            }
        }
    }

    private static class TensorMMA implements TensorTransformer {

        @Override
        public void transform(Block.Builder blockBuilder, Op op) {
            List<Value> operands = blockBuilder.context().getValues(op.operands());
            switch (op) {
                case CoreOp.VarAccessOp.VarLoadOp loadOp -> replaceOp(blockBuilder, loadOp, new TensorVarLoadOp(loadOp.resultType(), operands));
                case JavaOp.InvokeOp invokeOp -> replaceOp(blockBuilder, invokeOp, new TensorMMAOp(invokeOp.resultType(), operands));
                default -> blockBuilder.op(op);
            }
        }
    }

    private static class TensorLoad implements TensorTransformer {

        @Override
        public void transform(Block.Builder blockBuilder, Op op) {
            List<Value> operands = blockBuilder.context().getValues(op.operands());
            switch (op) {
                case CoreOp.VarAccessOp.VarStoreOp storeOp -> replaceOp(blockBuilder, storeOp, new TensorStoreLoadOp(storeOp.resultType(), operands));
                case JavaOp.InvokeOp invokeOp -> replaceOp(blockBuilder, invokeOp, new TensorLoadOp(invokeOp.resultType(), invokeOp.invokeReference().name(), operands));
                default -> blockBuilder.op(op);
            }
        }
    }

    private static class TensorStore implements TensorTransformer {

        @Override
        public void transform(Block.Builder blockBuilder, Op op) {
            if (Objects.requireNonNull(op) instanceof JavaOp.InvokeOp invokeOp) {
                replaceOp(blockBuilder, invokeOp, new TensorStoreOp(invokeOp.resultType(), blockBuilder.context().getValues(invokeOp.operands())));
            } else {
                blockBuilder.op(op);
            }
        }
    }

    private CoreOp.FuncOp transformWithPredicate(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp, BiConsumer<Block.Builder, Op> function, Set<Op> opsToProcess ) {
        return Trxfmr.of(lookup, funcOp).transform(opsToProcess::contains, (blockBuilder, op) -> {
            function.accept(blockBuilder, op);
            return blockBuilder;
        }).funcOp();
    }

    private record DeclTensorData(Op marker, JavaOp.InvokeOp invokeOp, CoreOp.VarOp varOp) {
    }

    record ControlFlowLastOp(CodeElement<?, ?> previous, boolean hitControlFlow) {}

    private ControlFlowLastOp obtainLastOpBeforeControlFlow(CoreOp.FuncOp funcOp) {
        CodeElement<?, ?> previousOp = funcOp.bodies().getFirst().blocks().getFirst().firstOp();
        List<CodeElement<?, ?>> elements = funcOp.elements().toList();
        boolean hitControlFlow = false;
        for (CodeElement<?, ?> element : elements) {
            if (Objects.requireNonNull(element) instanceof Op.Loop
                    || element instanceof JavaOp.IfOp
                    || element instanceof JavaOp.SwitchExpressionOp) {
                hitControlFlow = true;
                break;
            }
            previousOp = element;
        }

        if (!hitControlFlow) {
            // We do not have any control-flow flow, we point to the first instruction instead.
            previousOp = funcOp.bodies().getFirst().blocks().getFirst().firstOp();
        }
        return new ControlFlowLastOp(previousOp, hitControlFlow);
    }

    private CoreOp.FuncOp createTensorsToRelocate(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {

        // 1. Obtain the last Op before any control flow. We need to find a suitable location in basic block 0
        // to insert the declaration of tensors coming from the Load operations
        ControlFlowLastOp controlFlowLastOp = obtainLastOpBeforeControlFlow(funcOp);

        // 2. Analyze the code model to obtain:
        // 2.1: A set for all nodes to be processed (load-invoke, and the varOp associated with it)
        // 2.2: A map that relates the marker position (suitable last op) with a list of pending declaration to perform
        Map<Op, List<DeclTensorData>> map  = new HashMap<>();
        Op marker = (Op) controlFlowLastOp.previous;
        Set<Op> opsToProcess = new HashSet<>();
        opsToProcess.add(marker);
        map.put(marker, new ArrayList<>());
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(invoke -> !invoke.returnsVoid())
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("load") || invoke.name().equals("loadF16"))
                .forEach(invoke -> {
                    opsToProcess.add(invoke.op());
                    invoke.op().result().uses().stream()
                            .filter(result -> (result.op() instanceof CoreOp.VarOp))
                            .map(result -> (CoreOp.VarOp) result.op())
                            .forEach(varOp -> {
                                opsToProcess.add(varOp);
                                map.get(marker).add(new DeclTensorData(marker, invoke.op(), varOp));
                            });
                });

        // 3. Transform the model to insert:
        // 3.1: All tensor declaration from the marker.
        // 3.2 The load-invoke keeps intact since it will be processed in another phase
        // 3.3 Replace the VarOp declaration associated with a load with a TensorStoreLoadOp
        // 3.4 Replace the reference of subsequent VarLoapOp with the new declaration
        Map<CoreOp.VarOp, Value> mapValueTensor = new HashMap<>();
        Map<Op, Value> mapUsages = new HashMap<>();
        funcOp = funcOp.transform((blockBuilder, op) -> {
            if (!opsToProcess.contains(op)) {
              blockBuilder.op(op);
            } else if (map.containsKey(op)) {
                // In this block, we insert all pending tensor declaration, starting with the marker.

                List<DeclTensorData> declTensorList = map.get(marker);

                // Insert the marker
                blockBuilder.op(marker);

                // And add the missing declarations
                for (DeclTensorData c : declTensorList) {
                    // Add a TensorCreateOp
                    JavaOp.InvokeOp declInvoke = c.invokeOp;
                    CoreOp.VarOp declVar = c.varOp;
                    TensorCreateOp tensorCreateOp = new TensorCreateOp(declInvoke.resultType(), blockBuilder.context().getValues(List.of()));
                    Op.Result op1 = blockBuilder.op(tensorCreateOp);

                    // Add a TensorVarOp associated with teh TensorCreateOp
                    List<Value> operands = List.of(op1);
                    TensorVarOp tensorVarOp = new TensorVarOp(declVar.varName(), declVar.resultType(), operands);
                    Op.Result op2 = blockBuilder.op(tensorVarOp);

                    // Include in a new HashMap the new tensorVarOp to be propagated for the Stores and VarLoadOps.
                    mapValueTensor.put(declVar, op2);

                    blockBuilder.context().mapValue(declInvoke.result(), op2);
                }
            } else if (op instanceof JavaOp.InvokeOp invokeOp) {
                // The Load/LoadF16 is propagated
                blockBuilder.op(invokeOp);

            } else if (op instanceof CoreOp.VarOp varOp) {

                // Replace the VarOp with a TensorStoreLoadOp using the reference of the tensorVarOp
                // declared in a previous block (block 0)

                Value tensorVarOp = mapValueTensor.get(varOp);

                // Update the usages
                SequencedSet<Op.Result> uses = varOp.result().uses();
                for (Op.Result r : uses) {
                    opsToProcess.add(r.op());
                    mapUsages.put(r.op(), tensorVarOp);
                }

                List<Value> operands = List.of(tensorVarOp, blockBuilder.context().getValue(varOp.operands().getFirst()));
                VarType varType = CoreType.varType(VOID);
                TensorStoreLoadOp storeOp = new TensorStoreLoadOp(varType, operands);

                Op.Result storeResult = blockBuilder.op(storeOp);
                blockBuilder.context().mapValue(varOp.result(), storeResult);

            } else if (op instanceof CoreOp.VarAccessOp.VarLoadOp varLoadOp) {
                // This means a tensor is loading, and we expect the varLoadOp to be present already in the mapUsages,
                // since the declaration is always traversed before teh varLoadOp.

                Value tensorValue = mapUsages.get(varLoadOp);
                CoreOp.VarAccessOp.VarLoadOp varLoadOpNew = varLoad(tensorValue);
                Op.Result op1 = blockBuilder.op(varLoadOpNew);
                blockBuilder.context().mapValue(varLoadOp.result(), op1);
            }
            return blockBuilder;
        });
        return funcOp;
    }

    private CoreOp.FuncOp createTensors(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(invoke -> !invoke.returnsVoid())
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("create") || invoke.name().equals("of"))
                .forEach( invoke -> {
                    opsToProcess.add(invoke.op());
                    invoke.op().result().uses().stream()
                            .filter(result -> (result.op() instanceof CoreOp.VarOp))
                            .map(result -> (CoreOp.VarOp) result.op())
                            .findFirst()
                            .ifPresent(opsToProcess::add);
                });

        return transformWithPredicate(lookup, funcOp, new TensorView()::transform, opsToProcess);
    }

    private CoreOp.FuncOp tensorShape(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(invoke -> !invoke.returnsVoid())
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("shape"))
                .forEach( invoke ->
                        invoke.op().result().uses().stream()
                        .filter(result -> (result.op() instanceof CoreOp.VarOp))
                        .map(result -> (CoreOp.VarOp) result.op())
                        .findFirst()
                        .ifPresent(x -> {
                            // We only process shape with a node in the case of a declaration.
                            // Otherwise, we process the shape via a Java Invoke.
                            opsToProcess.add(x);
                            opsToProcess.add(invoke.op());
                        }));

        Map<Op, Value> map = new HashMap<>();
        funcOp = funcOp.transform((blockBuilder, op) -> {
            if (!opsToProcess.contains(op)) {
                blockBuilder.op(op);
            } else if (op instanceof JavaOp.InvokeOp invokeOp) {
                List<Value> operands = blockBuilder.context().getValues(op.operands());
                Op.Result valueVar = invokeOp.result().uses().getFirst();
                if (valueVar.declaringElement() instanceof CoreOp.VarOp varOp) {
                    TensorShapeOp tensorShapeOp = new TensorShapeOp(varOp.resultType(), operands);
                    Op.Result result = blockBuilder.op(tensorShapeOp);
                    blockBuilder.context().mapValue(invokeOp.result(), result);
                    map.put(varOp, result);
                } else {
                    throw new RuntimeException("Expected a VarOp");
                }
            } else if (op instanceof CoreOp.VarOp varOp) {
                blockBuilder.context().mapValue(varOp.result(), map.get(varOp));
            }
            return blockBuilder;
        });
        return funcOp;

    }

    private Set<Op> filterOps(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp, String methodIntrinsicName) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(OpHelper.Invoke::returnsVoid)
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals(methodIntrinsicName))
                .forEach(invoke -> {
                    opsToProcess.add(invoke.op());
                    Value varLoadValue = invoke.op().operands().getFirst();
                    if (varLoadValue.declaringElement() instanceof CoreOp.VarAccessOp.VarLoadOp varLoadOp) {
                        opsToProcess.add(varLoadOp);
                    }
                });
        return opsToProcess;
    }

    private CoreOp.FuncOp fillTensors(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = filterOps(lookup, funcOp, "fill");
        return transformWithPredicate(lookup, funcOp, new TensorFill()::transform, opsToProcess);
    }

    private CoreOp.FuncOp zerosTensors(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(invoke -> !invoke.returnsVoid())
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("zeros"))
                .forEach(invoke -> {
                    opsToProcess.add(invoke.op());
                    Value varValue = invoke.op().result().uses().getFirst();
                    if (varValue.declaringElement() instanceof CoreOp.VarOp varOp) {
                        opsToProcess.add(varOp);
                    }
                });

        Map<Op, Value> map = new HashMap<>();
        funcOp = funcOp.transform((blockBuilder, op) -> {
            if (!opsToProcess.contains(op)) {
                blockBuilder.op(op);
            } else if (op instanceof JavaOp.InvokeOp invokeOp) {
                // Create Tensor
                List<Value> operands = blockBuilder.context().getValues(op.operands());

                TensorCreateOp tensorCreateOp = new TensorCreateOp(invokeOp.resultType(), operands);
                tensorCreateOp.setLocation(invokeOp.location());
                Op.Result op1 = blockBuilder.op(tensorCreateOp);

                Op.Result valueVar = invokeOp.result().uses().getFirst();
                if (valueVar.declaringElement() instanceof CoreOp.VarOp varOp) {

                    // Add Var
                    List<Value> args = List.of(op1);
                    TensorVarOp tensorVarOp = new TensorVarOp(varOp.varName(), varOp.resultType(), args);
                    Op.Result op2 = blockBuilder.op(tensorVarOp);

                    // TensorVarLoadOp
                    List<Value> argsLoadOp = List.of(op2);
                    TensorVarLoadOp tensorVarLoadOp = new TensorVarLoadOp(invokeOp.resultType(), argsLoadOp);
                    Op.Result op3 = blockBuilder.op(tensorVarLoadOp);

                    // Add Fill
                    CoreOp.ConstantOp constant = CoreOp.constant(PrimitiveType.FLOAT, 0.0f);
                    Op.Result op4 = blockBuilder.op(constant);

                    List<Value> argsFill = List.of(op3, op4);
                    TensorFillOp tensorFillOp = new TensorFillOp(VOID, argsFill);
                    Op.Result op5 = blockBuilder.op(tensorFillOp);
                    map.put(varOp, op2);
                    blockBuilder.context().mapValue(invokeOp.result(), op5);
                } else {
                    throw new RuntimeException("Expected a VarOp");
                }

            } else if (op instanceof CoreOp.VarOp varOp) {
                // Pass through value using the TensorVarOp created before
                blockBuilder.context().mapValue(varOp.result(), map.get(varOp));
            }
            return blockBuilder;
        });
        return funcOp;
    }

    private CoreOp.FuncOp mmaTensor(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = filterOps(lookup, funcOp, "mma");
        return transformWithPredicate(lookup, funcOp, new TensorMMA()::transform, opsToProcess);
    }

    private CoreOp.FuncOp tensorLoad(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(invoke -> !invoke.returnsVoid())
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("load") || invoke.name().equals("loadF16"))
                .forEach(invoke -> {
                    opsToProcess.add(invoke.op());
                    invoke.op().result().uses().stream()
                            .filter(result -> (result.op() instanceof CoreOp.VarAccessOp.VarStoreOp))
                            .map(result -> (CoreOp.VarAccessOp.VarStoreOp) result.op())
                            .forEach(opsToProcess::add);
                });
        return transformWithPredicate(lookup, funcOp, new TensorLoad()::transform, opsToProcess);
    }

    private CoreOp.FuncOp tensorStoreOp(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        Set<Op> opsToProcess = new HashSet<>();
        OpHelper.Invoke.stream(lookup, funcOp)
                .filter(OpHelper.Invoke::returnsVoid)
                .filter(invoke -> invoke.refIs(Tensor.class))
                .filter(invoke -> invoke.name().equals("store"))
                .forEach(invoke -> opsToProcess.add(invoke.op()));
        return transformWithPredicate(lookup, funcOp, new TensorStore()::transform, opsToProcess);
    }

    @Override
    public CoreOp.FuncOp transform(MethodHandles.Lookup lookup, CoreOp.FuncOp funcOp) {
        funcOp = createTensorsToRelocate(lookup, funcOp);
        funcOp = createTensors(lookup, funcOp);
        funcOp = tensorShape(lookup, funcOp);
        funcOp = fillTensors(lookup, funcOp);
        funcOp = zerosTensors(lookup, funcOp);
        funcOp = mmaTensor(lookup, funcOp);
        funcOp = tensorLoad(lookup, funcOp);
        funcOp = tensorStoreOp(lookup, funcOp);
        return funcOp;
    }
}
