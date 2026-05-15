package hat.types;

import hat.buffer.F16Array;
import hat.buffer.F32Array;
import hat.buffer.F32ArrayPadded;
import optkl.IfaceValue;

// Tensors are immutable
public record Tensor(Shape shape, Class<?> klass, Access tensorAccess) implements IfaceValue {

    public static final int UNDEFINED = -1;
    public static final int ACC = 2;

    public static Shape shape(int dim1, int dim2, int dim3) {
        return new Shape(dim1, dim2, dim3);
    }

//    public static Tensor create(Shape shape, Class<?> klass, final Access tensorAccess) {
//        return new Tensor(shape, klass, tensorAccess);
//    }

    public static Tensor create(Shape shape, final Access tensorAccess) {
        return new Tensor(shape, null, tensorAccess);
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

//    public static Tensor load(F16Array matrix, int i, int j, int ld) {
//        return null;
//    }

    public static Tensor loadF16(F16Array matrix, int i, int j, int ld) {
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
