package com.thirds.qss.compiler.resolve;

import com.thirds.qss.BundleQualifiedName;
import com.thirds.qss.QualifiedName;
import com.thirds.qss.compiler.Compiler;
import com.thirds.qss.compiler.Message;
import com.thirds.qss.compiler.indexer.NameIndex;
import com.thirds.qss.compiler.indexer.NameIndices;
import com.thirds.qss.compiler.tree.NameLiteral;
import com.thirds.qss.compiler.tree.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Resolver {
    /**
     * Resolves a name in global scope. Essentially, it loops through all the packages we've loaded searching for the
     * qualified name that matches a name written in QSS. For example, writing <code>spawn_entity</code> with the
     * <code>std::entity</code> package imported equates to the <code>std::entity::spawn_entity</code> function. This
     * function works out which globally scoped name you're referring to when you write a given name in QSS code.
     *
     * This "given name" is encapsulated by the resolver parameter; the resolver makes its own deduction about whether
     * any item in the index matches.
     *
     * This automatically deduces what alternatives are valid based on the list of imports declared by the script.
     * @param compiler The compiler we're using. By this point, we need to have generated the name indices so
     *                 we can search the dependencies for names.
     * @param script The script we're currently compiling. This is used for finding the list of import statements so
     *               that we can tell what alternatives we have for the given name.
     * @param resolver This function will be called for each name index we're searching in. It must return a
     *                 list of items that match the name we're querying. For example, we're searching for a struct
     *                 with a given name. For each name index it's given, this function will return a list of all
     *                 structs that match the given name.
     * @param <T> The type of item we're searching for. E.g. Struct (if we're searching for a struct that matches a
     *           given name), Func (if we're searching for a Func).
     */
    public static <T> ResolveResult<T> resolveGlobalScopeName(Compiler compiler, Script script, Function<NameIndex, List<T>> resolver) {
        ArrayList<ResolveAlternative<T>> alternatives = new ArrayList<>();

        // TODO we might want to speed up this nested for loop. Maybe we can cache a HashSet/HashMap of last segments of qualified names?

        // First, we do a run through just checking imported packages.
        for (Map.Entry<String, NameIndices.Bundle> bundleEntry : compiler.getNameIndices().getBundles().entrySet()) {
            for (Map.Entry<QualifiedName, NameIndex> indexEntry : bundleEntry.getValue().getPackages().entrySet()) {
                boolean packageWasImported = script.getImportedPackages().contains(indexEntry.getKey());
                if (!packageWasImported)
                    continue;

                NameIndex index = indexEntry.getValue();
                List<T> result = resolver.apply(index);
                for (T t : result) {
                    alternatives.add(new ResolveAlternative<>(t, List.of(new BundleQualifiedName(bundleEntry.getKey(), indexEntry.getKey()))));
                }
            }
        }

        if (!alternatives.isEmpty()) {
            // We found at least one matching name.
            return ResolveResult.success(alternatives);
        }

        // If we didn't find a matching name, redo the whole process looking in every single package, regardless
        // if it's imported. We need to tell the user which package it's actually in.
        for (Map.Entry<String, NameIndices.Bundle> bundleEntry : compiler.getNameIndices().getBundles().entrySet()) {
            for (Map.Entry<QualifiedName, NameIndex> indexEntry : bundleEntry.getValue().getPackages().entrySet()) {
                NameIndex index = indexEntry.getValue();
                List<T> result = resolver.apply(index);
                for (T t : result) {
                    alternatives.add(new ResolveAlternative<>(t, List.of(new BundleQualifiedName(bundleEntry.getKey(), indexEntry.getKey()))));
                }
            }
        }

        return ResolveResult.nonImported(alternatives);
    }

    /**
     * Represents a possible resolve alternative when searching for a function's name.
     */
    public static class FuncNameAlternative {
        public final QualifiedName name;
        public final NameIndex.FuncDefinition func;

        public FuncNameAlternative(QualifiedName name, NameIndex.FuncDefinition func) {
            this.name = name;
            this.func = func;
        }
    }

    /**
     * @param compiler The name index must be built.
     */
    public static ResolveResult<FuncNameAlternative> resolveFuncName(Compiler compiler, Script script, ArrayList<Message> messages, NameLiteral funcName) {
        ResolveResult<FuncNameAlternative> funcResolved = resolveGlobalScopeName(compiler, script, nameIndex -> {
            ArrayList<FuncNameAlternative> alternatives = new ArrayList<>(0);
            nameIndex.getFuncDefinitions().forEach((name, func) -> {
                QualifiedName qualifiedName = nameIndex.getPackage().appendSegment(name);
                if (funcName.matches(qualifiedName)) {
                    alternatives.add(new FuncNameAlternative(qualifiedName, func));
                }
            });
            return alternatives;
        });

        if (funcResolved.alternatives.isEmpty()) {
            StringBuilder message = new StringBuilder("Could not resolve func ").append(funcName);
            if (!funcResolved.nonImportedAlternatives.isEmpty()) {
                message.append("; try one of the following:");
                for (ResolveAlternative<FuncNameAlternative> alt : funcResolved.nonImportedAlternatives) {
                    // \u2022 is the bullet character
                    message.append("\n").append("\u2022 import ").append(alt.imports.stream().map(i -> i.name.toString()).collect(Collectors.joining(", ")));
                }
            }
            messages.add(new Message(
                    funcName.getRange(),
                    Message.MessageSeverity.ERROR,
                    message.toString()
            ));
        } else if (funcResolved.alternatives.size() == 1) {
            ResolveAlternative<FuncNameAlternative> resolved = funcResolved.alternatives.get(0);
            funcName.setTarget(resolved.value.func.getLocation(), resolved.value.func.getDocumentation());
        } else {
            messages.add(new Message(
                    funcName.getRange(),
                    Message.MessageSeverity.ERROR,
                    "Reference to func " + funcName + " was ambiguous, possibilities were: " +
                            funcResolved.alternatives.stream().map(alt -> alt.value.name.toString()).collect(Collectors.joining(", "))
            ));
        }

        return funcResolved;
    }

    /**
     * Represents a possible resolve alternative when searching for a struct's name.
     */
    public static class StructNameAlternative {
        public final QualifiedName name;
        public final NameIndex.StructDefinition struct;

        public StructNameAlternative(QualifiedName name, NameIndex.StructDefinition struct) {
            this.name = name;
            this.struct = struct;
        }
    }

    /**
     * @param compiler The name index must be built.
     */
    public static ResolveResult<StructNameAlternative> resolveStructName(Compiler compiler, Script script, ArrayList<Message> messages, NameLiteral funcName) {
        ResolveResult<StructNameAlternative> structResolved = resolveGlobalScopeName(compiler, script, nameIndex -> {
            ArrayList<StructNameAlternative> alternatives = new ArrayList<>(0);
            nameIndex.getStructDefinitions().forEach((name, func) -> {
                QualifiedName qualifiedName = nameIndex.getPackage().appendSegment(name);
                if (funcName.matches(qualifiedName)) {
                    alternatives.add(new StructNameAlternative(qualifiedName, func));
                }
            });
            return alternatives;
        });

        if (structResolved.alternatives.isEmpty()) {
            StringBuilder message = new StringBuilder("Could not resolve struct ").append(funcName);
            if (!structResolved.nonImportedAlternatives.isEmpty()) {
                message.append("; try one of the following:");
                for (ResolveAlternative<StructNameAlternative> alt : structResolved.nonImportedAlternatives) {
                    // \u2022 is the bullet character
                    message.append("\n").append("\u2022 import ").append(alt.imports.stream().map(i -> i.name.toString()).collect(Collectors.joining(", ")));
                }
            }
            messages.add(new Message(
                    funcName.getRange(),
                    Message.MessageSeverity.ERROR,
                    message.toString()
            ));
        } else if (structResolved.alternatives.size() == 1) {
            ResolveAlternative<StructNameAlternative> resolved = structResolved.alternatives.get(0);
            funcName.setTarget(resolved.value.struct.getLocation(), resolved.value.struct.getDocumentation());
        } else {
            messages.add(new Message(
                    funcName.getRange(),
                    Message.MessageSeverity.ERROR,
                    "Reference to struct " + funcName + " was ambiguous, possibilities were: " +
                            structResolved.alternatives.stream().map(alt -> alt.value.name.toString()).collect(Collectors.joining(", "))
            ));
        }

        return structResolved;
    }
}