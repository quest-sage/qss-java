package com.thirds.qss.compiler.tree;

import com.thirds.qss.compiler.Range;
import com.thirds.qss.compiler.lexer.Token;

/**
 * Represents a data type.
 */
public class Type extends Node {
    public Type(Range range) {
        super(range);
    }

    public static class PrimitiveType extends Type {
        private final Token token;

        public PrimitiveType(Token token) {
            super(token.range);
            this.token = token;
        }
    }

    public static class StructType extends Type {
        private final NameLiteral structName;

        public StructType(Range range, NameLiteral structName) {
            super(range);
            this.structName = structName;
        }
    }
}