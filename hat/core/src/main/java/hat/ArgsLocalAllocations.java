package hat;

import hat.api.Space;
import hat.buffer.Buffer;
import hat.ifacemapper.Schema;

public record ArgsLocalAllocations<T extends Buffer>(Space space, Schema<T> schema, int size) {}
