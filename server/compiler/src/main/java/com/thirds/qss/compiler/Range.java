package com.thirds.qss.compiler;

import java.util.Objects;

public final class Range {
    public final Position start, end;

    public Range(Position where) {
        this.start = where.copy();
        this.end = where.copy();
        this.end.character++;
    }

    public Range(Position start, Position end) {
        this.start = start.copy();
        this.end = end.copy();
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return start.equals(range.start) &&
                end.equals(range.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
