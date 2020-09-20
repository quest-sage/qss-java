package com.thirds.qss.compiler.tree;

import com.thirds.qss.compiler.Range;
import com.thirds.qss.compiler.lexer.Token;

import java.util.function.Consumer;

/**
 * Represents a parameter of a func/hook.
 */
public class Param extends Node {
    private final Token name;
    private final Type type;

    public Param(Range range, Token name, Type type) {
        super(range);
        this.name = name;
        this.type = type;
    }

    public Token getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    @Override
    public void forChildren(Consumer<Node> consumer) {
        consumer.accept(type);
    }
}