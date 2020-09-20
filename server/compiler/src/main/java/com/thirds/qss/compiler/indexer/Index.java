package com.thirds.qss.compiler.indexer;

import com.thirds.qss.VariableType;
import com.thirds.qss.compiler.Compiler;
import com.thirds.qss.compiler.Location;
import com.thirds.qss.compiler.Message;
import com.thirds.qss.compiler.Messenger;
import com.thirds.qss.compiler.tree.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The index is an index used to store the names and fields of each type in a given package.
 */
public class Index {
    private final Map<String, StructDefinition> structDefinitions = new HashMap<>();
    private final Map<String, FuncDefinition> funcDefinitions = new HashMap<>();
    private final Compiler compiler;

    /**
     * The index is used for determining whether a name is defined, and the details of the name.
     */
    public Index(Compiler compiler) {
        this.compiler = compiler;
    }

    private static class FieldDefinition {
        private final String documentation;
        private final Location location;
        private final VariableType variableType;

        /**
         * @param variableType May be null; if so, the type in the index will show as <code>&lt;unknown&gt;</code>.
         */
        private FieldDefinition(String documentation, Location location, VariableType variableType) {
            this.documentation = documentation;
            this.location = location;
            this.variableType = variableType;
        }

        public String getDocumentation() {
            return documentation;
        }

        public Location getLocation() {
            return location;
        }

        public VariableType getVariableType() {
            return variableType;
        }

        @Override
        public String toString() {
            return "FieldDefinition{" +
                    "documentation='" + documentation + '\'' +
                    ", location=" + location +
                    ", variableType=" + variableType +
                    '}';
        }
    }

    private static class ParamDefinition {
        private final Location location;
        private final String name;
        private final VariableType variableType;

        /**
         * @param name
         * @param variableType May be null; if so, the type in the index will show as <code>&lt;unknown&gt;</code>.
         */
        private ParamDefinition(Location location, String name, VariableType variableType) {
            this.location = location;
            this.name = name;
            this.variableType = variableType;
        }

        public Location getLocation() {
            return location;
        }

        public String getName() {
            return name;
        }

        public VariableType getVariableType() {
            return variableType;
        }

        @Override
        public String toString() {
            return "ParamDefinition{" +
                    "location=" + location +
                    ", name='" + name + '\'' +
                    ", variableType=" + variableType +
                    '}';
        }
    }

    private static class StructDefinition {
        private final String documentation;
        private final Location location;
        private final Map<String, FieldDefinition> fields = new HashMap<>();

        private StructDefinition(String documentation, Location location) {
            this.documentation = documentation;
            this.location = location;
        }

        public String getDocumentation() {
            return documentation;
        }

        public Location getLocation() {
            return location;
        }

        public Map<String, FieldDefinition> getFields() {
            return fields;
        }

        @Override
        public String toString() {
            return "StructDefinition{" +
                    "documentation='" + documentation + '\'' +
                    ", location=" + location +
                    ", fields=" + fields +
                    '}';
        }
    }

    private static class FuncDefinition {
        private final String documentation;
        private final Location location;
        private final ArrayList<ParamDefinition> params = new ArrayList<>();

        private FuncDefinition(String documentation, Location location) {
            this.documentation = documentation;
            this.location = location;
        }

        public String getDocumentation() {
            return documentation;
        }

        public Location getLocation() {
            return location;
        }

        public ArrayList<ParamDefinition> getParams() {
            return params;
        }

        @Override
        public String toString() {
            return "FuncDefinition{" +
                    "documentation='" + documentation + '\'' +
                    ", location=" + location +
                    ", params=" + params +
                    '}';
        }
    }

    /**
     * Adds types to this index from the given script.
     * @param script The package of this script must match the package of the index itself.
     * @return <code>this</code> for chaining.
     */
    public Messenger<Index> addFrom(Script script) {
        ArrayList<Message> messages = new ArrayList<>();

        for (Documentable<Struct> struct : script.getStructs()) {
            StructDefinition def = new StructDefinition(
                    struct.getDocumentation().map(tk -> tk.contents).orElse(null),
                    new Location(script.getFilePath(), struct.getContent().getRange())
            );

            for (Documentable<Field> field : struct.getContent().getFields()) {
                if (def.fields.containsKey(field.getContent().getName().contents)) {
                    messages.add(new Message(
                            field.getContent().getName().getRange(),
                            Message.MessageSeverity.ERROR,
                            "Field " + field.getContent().getName().contents + " was already defined"
                    ).addInfo(new Message.MessageRelatedInformation(
                            def.fields.get(field.getContent().getName().contents).location,
                            "Previously defined here"
                    )));
                } else {
                    // Resolve the field's type using the type name indices in the compiler.
                    Type.ResolveResult fieldTypeAlternatives = resolveType(script, messages, field.getContent().getName().contents, field.getContent().getType());

                    def.fields.put(field.getContent().getName().contents, new FieldDefinition(
                            field.getDocumentation().map(tk -> tk.contents).orElse(null),
                            new Location(script.getFilePath(), field.getRange()),
                            fieldTypeAlternatives.alternatives.size() == 1 ? fieldTypeAlternatives.alternatives.get(0).type : null
                    ));
                }
            }

            structDefinitions.put(struct.getContent().getName().contents, def);
        }

        for (Documentable<Func> func : script.getFuncs()) {
            FuncDefinition def = new FuncDefinition(
                    func.getDocumentation().map(tk -> tk.contents).orElse(null),
                    new Location(script.getFilePath(), func.getContent().getRange())
            );

            for (Param param : func.getContent().getParamList().getParams()) {
                Location paramDuplicateLocation = null;
                for (ParamDefinition definition : def.params) {
                    if (definition.name.equals(param.getName().contents)) {
                        paramDuplicateLocation = definition.location;
                        break;
                    }
                }

                if (paramDuplicateLocation != null) {
                    messages.add(new Message(
                            param.getName().getRange(),
                            Message.MessageSeverity.ERROR,
                            "Parameter " + param.getName().contents + " was already defined"
                    ).addInfo(new Message.MessageRelatedInformation(
                            paramDuplicateLocation,
                            "Previously defined here"
                    )));
                } else {
                    // Resolve the parameter's type.
                    Type.ResolveResult paramTypeAlternatives = resolveType(script, messages, param.getName().contents, param.getType());

                    def.params.add(new ParamDefinition(
                            new Location(script.getFilePath(), param.getRange()), param.getName().contents,
                            paramTypeAlternatives.alternatives.size() == 1 ? paramTypeAlternatives.alternatives.get(0).type : null));
                }
            }

            funcDefinitions.put(func.getContent().getName().contents, def);
        }

        return Messenger.success(this, messages);
    }

    private Type.ResolveResult resolveType(Script script, ArrayList<Message> messages, String name, Type type) {
        Type.ResolveResult fieldTypeAlternatives = type.resolve(script.getImportedPackages(), compiler.getTypeNameIndices());

        if (fieldTypeAlternatives.alternatives.isEmpty()) {
            StringBuilder message = new StringBuilder("Could not resolve type of ").append(name);
            if (!fieldTypeAlternatives.nonImportedAlternatives.isEmpty()) {
                message.append("; try one of the following:");
                for (Type.ResolveAlternative alt : fieldTypeAlternatives.nonImportedAlternatives) {
                    // \u2022 is the bullet character
                    message.append("\n").append("\u2022 import ").append(alt.imports.stream().map(i -> i.name.toString()).collect(Collectors.joining(", ")));
                }
            }
            messages.add(new Message(
                    type.getRange(),
                    Message.MessageSeverity.ERROR,
                    message.toString()
            ));
        } else if (fieldTypeAlternatives.alternatives.size() > 1) {
            messages.add(new Message(
                    type.getRange(),
                    Message.MessageSeverity.ERROR,
                    "Type of " + name + " was ambiguous, possibilities were: " +
                            fieldTypeAlternatives.alternatives.stream().map(alt -> alt.type.toString()).collect(Collectors.joining(", "))
            ));
        }
        return fieldTypeAlternatives;
    }

    @Override
    public String toString() {
        return "Index{" +
                "\n    structDefinitions=" + structDefinitions +
                "\n    funcDefinitions=" + funcDefinitions +
                "\n  }";
    }
}