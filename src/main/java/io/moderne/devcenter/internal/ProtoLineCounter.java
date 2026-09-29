/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.devcenter.internal;

import org.jspecify.annotations.Nullable;
import org.openrewrite.protobuf.ProtoVisitor;
import org.openrewrite.protobuf.marker.ImplicitProto2Syntax;
import org.openrewrite.protobuf.tree.Comment;
import org.openrewrite.protobuf.tree.Proto;
import org.openrewrite.protobuf.tree.ProtoContainer;
import org.openrewrite.protobuf.tree.ProtoRightPadded;
import org.openrewrite.protobuf.tree.Space;

import java.util.List;

/**
 * Counts lines in a protobuf file by walking it the way {@code ProtoPrinter} does, which prints spaces that
 * {@code ProtoVisitor} never visits (a block's end, the EOF) and skips an implicit proto2 syntax statement. Markers
 * aren't source and aren't counted.
 */
final class ProtoLineCounter extends ProtoVisitor<PrintOrderLineCounter> {

    static long count(Proto.Document document) {
        PrintOrderLineCounter counter = new PrintOrderLineCounter();
        new ProtoLineCounter().visit(document, counter);
        return counter.lineCount();
    }

    @Override
    public Space visitSpace(Space space, PrintOrderLineCounter p) {
        p.text(space.getWhitespace());
        for (Comment comment : space.getComments()) {
            p.syntax();
            p.text(comment.getText());
            if (comment.isMultiline()) {
                p.syntax();
            }
            p.text(comment.getSuffix());
        }
        return space;
    }

    @Override
    public Proto visitBlock(Proto.Block block, PrintOrderLineCounter p) {
        prefix(block, p);
        p.syntax();
        statements(block.getPadding().getStatements(), p);
        visitSpace(block.getEnd(), p);
        p.syntax();
        return block;
    }

    @Override
    public Proto visitConstant(Proto.Constant constant, PrintOrderLineCounter p) {
        prefix(constant, p);
        p.text(constant.getValueSource());
        return constant;
    }

    @Override
    public Proto visitDocument(Proto.Document document, PrintOrderLineCounter p) {
        prefix(document, p);
        visit(document.getSyntax(), p);
        statements(document.getPadding().getBody(), p);
        visitSpace(document.getEof(), p);
        return document;
    }

    @Override
    public Proto visitEmpty(Proto.Empty empty, PrintOrderLineCounter p) {
        prefix(empty, p);
        return empty;
    }

    @Override
    public Proto visitEnum(Proto.Enum anEnum, PrintOrderLineCounter p) {
        prefix(anEnum, p);
        p.syntax();
        visit(anEnum.getName(), p);
        visit(anEnum.getBody(), p);
        return anEnum;
    }

    @Override
    public Proto visitEnumField(Proto.EnumField enumField, PrintOrderLineCounter p) {
        prefix(enumField, p);
        visitRightPadded(enumField.getPadding().getName(), p);
        p.syntax();
        visit(enumField.getNumber(), p);
        container(enumField.getPadding().getOptions(), true, p);
        return enumField;
    }

    @Override
    public Proto visitExtend(Proto.Extend extend, PrintOrderLineCounter p) {
        prefix(extend, p);
        p.syntax();
        visitFullIdentifier(extend.getName(), p);
        visitBlock(extend.getBody(), p);
        return extend;
    }

    @Override
    public Proto visitExtensionName(Proto.ExtensionName extensionName, PrintOrderLineCounter p) {
        prefix(extensionName, p);
        p.syntax();
        visitRightPadded(extensionName.getPadding().getExtension(), p);
        p.syntax();
        return extensionName;
    }

    @Override
    public Proto visitExtensions(Proto.Extensions extensions, PrintOrderLineCounter p) {
        prefix(extensions, p);
        p.syntax();
        container(extensions.getPadding().getRanges(), false, p);
        return extensions;
    }

    @Override
    public Proto visitField(Proto.Field field, PrintOrderLineCounter p) {
        prefix(field, p);
        visit(field.getLabel(), p);
        visit(field.getType(), p);
        visitRightPadded(field.getPadding().getName(), p);
        p.syntax();
        visit(field.getNumber(), p);
        container(field.getPadding().getOptions(), true, p);
        return field;
    }

    @Override
    public Proto visitFullIdentifier(Proto.FullIdentifier identifier, PrintOrderLineCounter p) {
        prefix(identifier, p);
        visitRightPadded(identifier.getPadding().getTarget(), p);
        if (identifier.getTarget() != null) {
            p.syntax();
        }
        visit(identifier.getName(), p);
        return identifier;
    }

    @Override
    public Proto visitGroup(Proto.Group group, PrintOrderLineCounter p) {
        prefix(group, p);
        visit(group.getLabel(), p);
        visit(group.getGroup(), p);
        visitRightPadded(group.getPadding().getName(), p);
        p.syntax();
        visit(group.getNumber(), p);
        visit(group.getBody(), p);
        return group;
    }

    @Override
    public Proto visitIdentifier(Proto.Identifier identifier, PrintOrderLineCounter p) {
        prefix(identifier, p);
        p.text(identifier.getName());
        return identifier;
    }

    @Override
    public Proto visitImport(Proto.Import anImport, PrintOrderLineCounter p) {
        prefix(anImport, p);
        p.syntax();
        visit(anImport.getModifier(), p);
        visitRightPadded(anImport.getPadding().getName(), p);
        return anImport;
    }

    @Override
    public Proto visitKeyword(Proto.Keyword keyword, PrintOrderLineCounter p) {
        prefix(keyword, p);
        p.text(keyword.getKeyword());
        return keyword;
    }

    @Override
    public Proto visitMapField(Proto.MapField mapField, PrintOrderLineCounter p) {
        prefix(mapField, p);
        p.syntax();
        // The printer writes "map" itself, then only the space after the keyword
        visitSpace(mapField.getPadding().getMap().getAfter(), p);
        p.syntax();
        visitRightPadded(mapField.getPadding().getKeyType(), p);
        p.syntax();
        visitRightPadded(mapField.getPadding().getValueType(), p);
        p.syntax();
        visitRightPadded(mapField.getPadding().getName(), p);
        p.syntax();
        visit(mapField.getNumber(), p);
        container(mapField.getPadding().getOptions(), true, p);
        return mapField;
    }

    @Override
    public Proto visitMessage(Proto.Message message, PrintOrderLineCounter p) {
        prefix(message, p);
        p.syntax();
        visit(message.getName(), p);
        visit(message.getBody(), p);
        return message;
    }

    @Override
    public Proto visitOneOf(Proto.OneOf oneOf, PrintOrderLineCounter p) {
        prefix(oneOf, p);
        p.syntax();
        visit(oneOf.getName(), p);
        visit(oneOf.getFields(), p);
        return oneOf;
    }

    @Override
    public Proto visitOption(Proto.Option option, PrintOrderLineCounter p) {
        prefix(option, p);
        visitRightPadded(option.getPadding().getName(), p);
        p.syntax();
        visit(option.getAssignment(), p);
        return option;
    }

    @Override
    public Proto visitOptionDeclaration(Proto.OptionDeclaration optionDeclaration, PrintOrderLineCounter p) {
        prefix(optionDeclaration, p);
        p.syntax();
        visitRightPadded(optionDeclaration.getPadding().getName(), p);
        p.syntax();
        visit(optionDeclaration.getAssignment(), p);
        return optionDeclaration;
    }

    @Override
    public Proto visitPackage(Proto.Package aPackage, PrintOrderLineCounter p) {
        prefix(aPackage, p);
        p.syntax();
        visit(aPackage.getName(), p);
        return aPackage;
    }

    @Override
    public Proto visitPrimitive(Proto.Primitive primitive, PrintOrderLineCounter p) {
        prefix(primitive, p);
        p.syntax();
        return primitive;
    }

    @Override
    public Proto visitRange(Proto.Range range, PrintOrderLineCounter p) {
        prefix(range, p);
        visitRightPadded(range.getPadding().getFrom(), p);
        if (range.getTo() != null) {
            p.syntax();
            visit(range.getTo(), p);
        }
        return range;
    }

    @Override
    public Proto visitReserved(Proto.Reserved reserved, PrintOrderLineCounter p) {
        prefix(reserved, p);
        p.syntax();
        container(reserved.getPadding().getReservations(), false, p);
        return reserved;
    }

    @Override
    public Proto visitRpc(Proto.Rpc rpc, PrintOrderLineCounter p) {
        prefix(rpc, p);
        p.syntax();
        visit(rpc.getName(), p);
        visit(rpc.getRequest(), p);
        visit(rpc.getReturns(), p);
        visit(rpc.getResponse(), p);
        visit(rpc.getBody(), p);
        return rpc;
    }

    @Override
    public Proto visitRpcInOut(Proto.RpcInOut rpcInOut, PrintOrderLineCounter p) {
        prefix(rpcInOut, p);
        p.syntax();
        if (rpcInOut.getStream() != null) {
            visitSpace(rpcInOut.getStream().getPrefix(), p);
            p.syntax();
        }
        visitRightPadded(rpcInOut.getPadding().getType(), p);
        p.syntax();
        return rpcInOut;
    }

    @Override
    public Proto visitService(Proto.Service service, PrintOrderLineCounter p) {
        prefix(service, p);
        p.syntax();
        visit(service.getName(), p);
        visit(service.getBody(), p);
        return service;
    }

    @Override
    public Proto visitStringLiteral(Proto.StringLiteral stringLiteral, PrintOrderLineCounter p) {
        prefix(stringLiteral, p);
        p.syntax();
        p.text(stringLiteral.getLiteral());
        p.syntax();
        return stringLiteral;
    }

    @Override
    public Proto visitSyntax(Proto.Syntax syntax, PrintOrderLineCounter p) {
        if (syntax.getMarkers().findFirst(ImplicitProto2Syntax.class).isPresent()) {
            return syntax;
        }
        prefix(syntax, p);
        p.syntax();
        visitSpace(syntax.getKeywordSuffix(), p);
        p.syntax();
        visitRightPadded(syntax.getPadding().getLevel(), p);
        p.syntax();
        return syntax;
    }

    private void prefix(Proto proto, PrintOrderLineCounter p) {
        visitSpace(proto.getPrefix(), p);
    }

    // Brackets the elements, as options are, when the printer does
    private void container(@Nullable ProtoContainer<? extends Proto> container, boolean brackets, PrintOrderLineCounter p) {
        if (container == null) {
            return;
        }
        visitSpace(container.getBefore(), p);
        if (brackets) {
            p.syntax();
        }
        List<? extends ProtoRightPadded<? extends Proto>> elements = container.getPadding().getElements();
        for (int i = 0; i < elements.size(); i++) {
            visit(elements.get(i).getElement(), p);
            visitSpace(elements.get(i).getAfter(), p);
            if (i < elements.size() - 1) {
                p.syntax();
            }
        }
        if (brackets) {
            p.syntax();
        }
    }

    private void statements(List<ProtoRightPadded<Proto>> statements, PrintOrderLineCounter p) {
        for (ProtoRightPadded<Proto> statement : statements) {
            visit(statement.getElement(), p);
            visitSpace(statement.getAfter(), p);
            if (endsWithSemicolon(statement.getElement())) {
                p.syntax();
            }
        }
    }

    private static boolean endsWithSemicolon(Proto s) {
        return s instanceof Proto.Empty || s instanceof Proto.Extensions || s instanceof Proto.Field ||
               s instanceof Proto.Import || s instanceof Proto.MapField || s instanceof Proto.EnumField ||
               s instanceof Proto.OptionDeclaration || s instanceof Proto.Package || s instanceof Proto.Reserved ||
               s instanceof Proto.Rpc && ((Proto.Rpc) s).getBody() == null || s instanceof Proto.Syntax;
    }
}
