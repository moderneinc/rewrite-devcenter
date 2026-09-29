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
import org.openrewrite.hcl.HclVisitor;
import org.openrewrite.hcl.tree.BodyContent;
import org.openrewrite.hcl.tree.Comment;
import org.openrewrite.hcl.tree.Expression;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.hcl.tree.HclContainer;
import org.openrewrite.hcl.tree.HclLeftPadded;
import org.openrewrite.hcl.tree.HclRightPadded;
import org.openrewrite.hcl.tree.Label;
import org.openrewrite.hcl.tree.Space;

import java.util.List;

/**
 * Counts lines in an HCL file by walking it the way {@code HclPrinter} does, which prints spaces that
 * {@code HclVisitor} never visits (an attribute's comma, a for expression's ellipsis). Markers aren't source and
 * aren't counted.
 */
final class HclLineCounter extends HclVisitor<PrintOrderLineCounter> {

    static long count(Hcl.ConfigFile configFile) {
        PrintOrderLineCounter counter = new PrintOrderLineCounter();
        new HclLineCounter().visit(configFile, counter);
        return counter.lineCount();
    }

    @Override
    public Space visitSpace(Space space, Space.Location loc, PrintOrderLineCounter p) {
        p.text(space.getWhitespace());
        for (Comment comment : space.getComments()) {
            p.syntax();
            p.text(comment.getText());
            if (comment.getStyle() == Comment.Style.INLINE) {
                p.syntax();
            }
            p.text(comment.getSuffix());
        }
        return space;
    }

    @Override
    public Hcl visitAttribute(Hcl.Attribute attribute, PrintOrderLineCounter p) {
        prefix(attribute, p);
        visit(attribute.getName(), p);
        visitSpace(attribute.getPadding().getType().getBefore(), Space.Location.ATTRIBUTE_ASSIGNMENT, p);
        p.syntax();
        visit(attribute.getValue(), p);
        if (attribute.getComma() != null) {
            visitSpace(attribute.getComma().getPrefix(), Space.Location.OBJECT_VALUE_ATTRIBUTE_COMMA, p);
            p.syntax();
        }
        return attribute;
    }

    @Override
    public Hcl visitAttributeAccess(Hcl.AttributeAccess attributeAccess, PrintOrderLineCounter p) {
        prefix(attributeAccess, p);
        visit(attributeAccess.getAttribute(), p);
        leftPadded(attributeAccess.getPadding().getName(), HclLeftPadded.Location.ATTRIBUTE_ACCESS_NAME, p);
        return attributeAccess;
    }

    @Override
    public Hcl visitBinary(Hcl.Binary binary, PrintOrderLineCounter p) {
        prefix(binary, p);
        visit(binary.getLeft(), p);
        visitSpace(binary.getPadding().getOperator().getBefore(), Space.Location.BINARY_OPERATOR, p);
        p.syntax();
        visit(binary.getRight(), p);
        return binary;
    }

    @Override
    public Hcl visitBlock(Hcl.Block block, PrintOrderLineCounter p) {
        prefix(block, p);
        visit(block.getType(), p);
        for (Label label : block.getLabels()) {
            visit(label, p);
        }
        visitSpace(block.getOpen(), Space.Location.BLOCK_OPEN, p);
        p.syntax();
        for (BodyContent content : block.getBody()) {
            visit(content, p);
        }
        visitSpace(block.getEnd(), Space.Location.BLOCK_CLOSE, p);
        p.syntax();
        return block;
    }

    @Override
    public Hcl visitConditional(Hcl.Conditional conditional, PrintOrderLineCounter p) {
        prefix(conditional, p);
        visit(conditional.getCondition(), p);
        leftPadded(conditional.getPadding().getTruePart(), HclLeftPadded.Location.CONDITIONAL_TRUE, p);
        leftPadded(conditional.getPadding().getFalsePart(), HclLeftPadded.Location.CONDITIONAL_FALSE, p);
        return conditional;
    }

    @Override
    public Hcl visitConfigFile(Hcl.ConfigFile configFile, PrintOrderLineCounter p) {
        prefix(configFile, p);
        for (BodyContent content : configFile.getBody()) {
            visit(content, p);
        }
        visitSpace(configFile.getEof(), Space.Location.CONFIG_FILE_EOF, p);
        return configFile;
    }

    @Override
    public Hcl visitForIntro(Hcl.ForIntro forIntro, PrintOrderLineCounter p) {
        prefix(forIntro, p);
        container(forIntro.getPadding().getVariables(), HclContainer.Location.FOR_VARIABLES, true, p);
        visit(forIntro.getIn(), p);
        return forIntro;
    }

    @Override
    public Hcl visitForObject(Hcl.ForObject forObject, PrintOrderLineCounter p) {
        prefix(forObject, p);
        p.syntax();
        visit(forObject.getIntro(), p);
        leftPadded(forObject.getPadding().getUpdateName(), HclLeftPadded.Location.FOR_UPDATE, p);
        leftPadded(forObject.getPadding().getUpdateValue(), HclLeftPadded.Location.FOR_UPDATE_VALUE, p);
        if (forObject.getEllipsis() != null) {
            visitSpace(forObject.getEllipsis().getPrefix(), Space.Location.FOR_UPDATE_VALUE_ELLIPSIS, p);
            p.syntax();
        }
        leftPadded(forObject.getPadding().getCondition(), HclLeftPadded.Location.FOR_CONDITION, p);
        visitSpace(forObject.getEnd(), Space.Location.FOR_OBJECT_SUFFIX, p);
        p.syntax();
        return forObject;
    }

    @Override
    public Hcl visitForTuple(Hcl.ForTuple forTuple, PrintOrderLineCounter p) {
        prefix(forTuple, p);
        p.syntax();
        visit(forTuple.getIntro(), p);
        leftPadded(forTuple.getPadding().getUpdate(), HclLeftPadded.Location.FOR_UPDATE, p);
        leftPadded(forTuple.getPadding().getCondition(), HclLeftPadded.Location.FOR_CONDITION, p);
        visitSpace(forTuple.getEnd(), Space.Location.FOR_TUPLE_SUFFIX, p);
        p.syntax();
        return forTuple;
    }

    @Override
    public Hcl visitFunctionCall(Hcl.FunctionCall functionCall, PrintOrderLineCounter p) {
        prefix(functionCall, p);
        visit(functionCall.getName(), p);
        container(functionCall.getPadding().getArguments(), HclContainer.Location.FUNCTION_CALL_ARGUMENTS, true, p);
        return functionCall;
    }

    @Override
    public Hcl visitHeredocTemplate(Hcl.HeredocTemplate heredocTemplate, PrintOrderLineCounter p) {
        prefix(heredocTemplate, p);
        p.text(heredocTemplate.getArrow());
        visit(heredocTemplate.getDelimiter(), p);
        for (Expression expression : heredocTemplate.getExpressions()) {
            visit(expression, p);
        }
        visitSpace(heredocTemplate.getEnd(), Space.Location.HEREDOC_END, p);
        p.text(heredocTemplate.getDelimiter().getName());
        return heredocTemplate;
    }

    @Override
    public Hcl visitIdentifier(Hcl.Identifier identifier, PrintOrderLineCounter p) {
        prefix(identifier, p);
        p.text(identifier.getName());
        return identifier;
    }

    @Override
    public Hcl visitIndex(Hcl.Index index, PrintOrderLineCounter p) {
        prefix(index, p);
        visit(index.getIndexed(), p);
        visit(index.getPosition(), p);
        return index;
    }

    @Override
    public Hcl visitIndexPosition(Hcl.Index.Position indexPosition, PrintOrderLineCounter p) {
        prefix(indexPosition, p);
        p.syntax();
        visitRightPadded(indexPosition.getPadding().getPosition(), HclRightPadded.Location.INDEX_POSITION, p);
        p.syntax();
        return indexPosition;
    }

    @Override
    public Hcl visitLegacyIndexAttribute(Hcl.LegacyIndexAttributeAccess legacyIndexAttributeAccess, PrintOrderLineCounter p) {
        prefix(legacyIndexAttributeAccess, p);
        visitRightPadded(legacyIndexAttributeAccess.getPadding().getBase(),
                HclRightPadded.Location.LEGACY_INDEX_ATTRIBUTE_ACCESS_BASE, p);
        p.syntax();
        visitLiteral(legacyIndexAttributeAccess.getIndex(), p);
        return legacyIndexAttributeAccess;
    }

    @Override
    public Hcl visitLiteral(Hcl.Literal literal, PrintOrderLineCounter p) {
        prefix(literal, p);
        p.text(literal.getValueSource());
        return literal;
    }

    @Override
    public Hcl visitObjectValue(Hcl.ObjectValue objectValue, PrintOrderLineCounter p) {
        prefix(objectValue, p);
        container(objectValue.getPadding().getAttributes(), HclContainer.Location.OBJECT_VALUE_ATTRIBUTES, false, p);
        return objectValue;
    }

    @Override
    public Hcl visitParentheses(Hcl.Parentheses parentheses, PrintOrderLineCounter p) {
        prefix(parentheses, p);
        p.syntax();
        visitRightPadded(parentheses.getPadding().getExpression(), HclRightPadded.Location.PARENTHESES, p);
        p.syntax();
        return parentheses;
    }

    @Override
    public Hcl visitQuotedTemplate(Hcl.QuotedTemplate template, PrintOrderLineCounter p) {
        prefix(template, p);
        p.syntax();
        for (Expression expression : template.getExpressions()) {
            visit(expression, p);
        }
        p.syntax();
        return template;
    }

    @Override
    public Hcl visitTemplateInterpolation(Hcl.TemplateInterpolation template, PrintOrderLineCounter p) {
        prefix(template, p);
        p.syntax();
        visitRightPadded(template.getPadding().getExpression(), HclRightPadded.Location.TEMPLATE_INTERPOLATION, p);
        p.syntax();
        return template;
    }

    @Override
    public Hcl visitSplat(Hcl.Splat splat, PrintOrderLineCounter p) {
        prefix(splat, p);
        visit(splat.getSelect(), p);
        visit(splat.getOperator(), p);
        return splat;
    }

    @Override
    public Hcl visitSplatOperator(Hcl.Splat.Operator splatOperator, PrintOrderLineCounter p) {
        prefix(splatOperator, p);
        p.syntax();
        visitSpace(splatOperator.getSplat().getElement().getPrefix(), Space.Location.SPLAT_OPERATOR_PREFIX, p);
        p.syntax();
        // Only the full "[*]" form prints the space after its star
        if (splatOperator.getType() == Hcl.Splat.Operator.Type.Full) {
            visitSpace(splatOperator.getSplat().getAfter(), Space.Location.SPLAT_OPERATOR_SUFFIX, p);
            p.syntax();
        }
        return splatOperator;
    }

    @Override
    public Hcl visitTuple(Hcl.Tuple tuple, PrintOrderLineCounter p) {
        prefix(tuple, p);
        container(tuple.getPadding().getValues(), HclContainer.Location.TUPLE_VALUES, true, p);
        return tuple;
    }

    @Override
    public Hcl visitUnary(Hcl.Unary unary, PrintOrderLineCounter p) {
        prefix(unary, p);
        p.syntax();
        visit(unary.getExpression(), p);
        return unary;
    }

    @Override
    public Hcl visitVariableExpression(Hcl.VariableExpression variableExpression, PrintOrderLineCounter p) {
        prefix(variableExpression, p);
        visit(variableExpression.getName(), p);
        return variableExpression;
    }

    private void prefix(Hcl hcl, PrintOrderLineCounter p) {
        visitSpace(hcl.getPrefix(), Space.Location.CONFIG_FILE, p);
    }

    private void leftPadded(@Nullable HclLeftPadded<? extends Hcl> leftPadded, HclLeftPadded.Location loc, PrintOrderLineCounter p) {
        if (leftPadded != null) {
            visitSpace(leftPadded.getBefore(), loc.getBeforeLocation(), p);
            p.syntax();
            visit(leftPadded.getElement(), p);
        }
    }

    // Delimits its elements with the syntax around them, and with commas when the printer does
    private void container(@Nullable HclContainer<? extends Hcl> container, HclContainer.Location loc, boolean commas,
                           PrintOrderLineCounter p) {
        if (container == null) {
            return;
        }
        visitSpace(container.getBefore(), loc.getBeforeLocation(), p);
        p.syntax();
        List<? extends HclRightPadded<? extends Hcl>> elements = container.getPadding().getElements();
        for (int i = 0; i < elements.size(); i++) {
            visit(elements.get(i).getElement(), p);
            visitSpace(elements.get(i).getAfter(), loc.getElementLocation().getAfterLocation(), p);
            if (commas && i < elements.size() - 1) {
                p.syntax();
            }
        }
        p.syntax();
    }
}
