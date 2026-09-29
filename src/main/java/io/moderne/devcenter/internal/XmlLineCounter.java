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
import org.openrewrite.xml.marker.HtmlVoidElement;
import org.openrewrite.xml.tree.Xml;

import java.util.List;

/**
 * Counts lines in an XML document from the text {@code XmlPrinter} emits, walking the tree in print order
 * without a visitor. Markers aren't source and aren't counted.
 */
final class XmlLineCounter extends PrintOrderLineCounter {

    private XmlLineCounter() {
    }

    static long count(Xml.Document document) {
        XmlLineCounter counter = new XmlLineCounter();
        counter.document(document);
        return counter.lineCount();
    }

    private void document(Xml.Document document) {
        if (document.isCharsetBomMarked()) {
            syntax();
        }
        text(document.getPrefix());
        prolog(document.getProlog());
        // An empty document has no root
        if (document.getRoot() != null) {
            tag(document.getRoot());
        }
        text(document.getEof());
    }

    private void prolog(Xml.@Nullable Prolog prolog) {
        if (prolog == null) {
            return;
        }
        text(prolog.getPrefix());
        Xml.XmlDecl xmlDecl = prolog.getXmlDecl();
        if (xmlDecl != null) {
            text(xmlDecl.getPrefix());
            text(xmlDecl.getName());
            attributes(xmlDecl.getAttributes());
            text(xmlDecl.getBeforeTagDelimiterPrefix());
            syntax();
        }
        nodes(prolog.getMisc());
        nodes(prolog.getJspDirectives());
    }

    private void nodes(@Nullable List<? extends Xml> nodes) {
        if (nodes != null) {
            for (Xml node : nodes) {
                node(node);
            }
        }
    }

    private void node(Xml node) {
        if (node instanceof Xml.Tag) {
            tag((Xml.Tag) node);
        } else if (node instanceof Xml.CharData) {
            charData((Xml.CharData) node);
        } else if (node instanceof Xml.Comment) {
            Xml.Comment comment = (Xml.Comment) node;
            text(comment.getPrefix());
            text(comment.getText());
            syntax();
        } else if (node instanceof Xml.ProcessingInstruction) {
            Xml.ProcessingInstruction pi = (Xml.ProcessingInstruction) node;
            text(pi.getPrefix());
            text(pi.getName());
            if (pi.getProcessingInstructions() != null) {
                charData(pi.getProcessingInstructions());
            }
            text(pi.getBeforeTagDelimiterPrefix());
            syntax();
        } else if (node instanceof Xml.DocTypeDecl) {
            docTypeDecl((Xml.DocTypeDecl) node);
        } else if (node instanceof Xml.JspDirective) {
            Xml.JspDirective jsp = (Xml.JspDirective) node;
            text(jsp.getPrefix());
            text(jsp.getBeforeTypePrefix());
            text(jsp.getType());
            attributes(jsp.getAttributes());
            text(jsp.getBeforeDirectiveEndPrefix());
            syntax();
        } else if (node instanceof Xml.JspScriptlet) {
            jsp(node.getPrefix(), ((Xml.JspScriptlet) node).getContent());
        } else if (node instanceof Xml.JspExpression) {
            jsp(node.getPrefix(), ((Xml.JspExpression) node).getContent());
        } else if (node instanceof Xml.JspDeclaration) {
            jsp(node.getPrefix(), ((Xml.JspDeclaration) node).getContent());
        } else if (node instanceof Xml.JspComment) {
            jsp(node.getPrefix(), ((Xml.JspComment) node).getContent());
        } else if (node instanceof Xml.XmlDecl) {
            Xml.XmlDecl xmlDecl = (Xml.XmlDecl) node;
            text(xmlDecl.getPrefix());
            text(xmlDecl.getName());
            attributes(xmlDecl.getAttributes());
            text(xmlDecl.getBeforeTagDelimiterPrefix());
            syntax();
        }
    }

    private void tag(Xml.Tag tag) {
        text(tag.getPrefix());
        syntax();
        text(tag.getName());
        attributes(tag.getAttributes());
        text(tag.getBeforeTagDelimiterPrefix());
        Xml.Tag.Closing closing = tag.getClosing();
        if (closing != null && !tag.getMarkers().findFirst(HtmlVoidElement.class).isPresent()) {
            nodes(tag.getContent());
            text(closing.getPrefix());
            text(closing.getName());
            text(closing.getBeforeTagDelimiterPrefix());
        }
        syntax();
    }

    private void attributes(@Nullable List<Xml.Attribute> attributes) {
        if (attributes == null) {
            return;
        }
        for (Xml.Attribute attribute : attributes) {
            text(attribute.getPrefix());
            text(attribute.getKey().getPrefix());
            text(attribute.getKeyAsString());
            text(attribute.getBeforeEquals());
            Xml.Attribute.Value value = attribute.getValue();
            text(value.getPrefix());
            syntax();
            text(value.getValue());
            syntax();
        }
    }

    private void charData(Xml.CharData charData) {
        text(charData.getPrefix());
        if (charData.isCdata()) {
            syntax();
            text(charData.getText());
            syntax();
        } else {
            text(charData.getText());
        }
        text(charData.getAfterText());
    }

    private void docTypeDecl(Xml.DocTypeDecl docTypeDecl) {
        text(docTypeDecl.getPrefix());
        syntax();
        text(docTypeDecl.getDocumentDeclaration());
        ident(docTypeDecl.getName());
        ident(docTypeDecl.getExternalId());
        if (docTypeDecl.getInternalSubset() != null) {
            for (Xml.Ident ident : docTypeDecl.getInternalSubset()) {
                ident(ident);
            }
        }
        Xml.DocTypeDecl.ExternalSubsets externalSubsets = docTypeDecl.getExternalSubsets();
        if (externalSubsets != null) {
            text(externalSubsets.getPrefix());
            syntax();
            for (Xml.Element element : externalSubsets.getElements()) {
                text(element.getPrefix());
                for (Xml.Ident ident : element.getSubset()) {
                    ident(ident);
                }
                text(element.getBeforeTagDelimiterPrefix());
            }
            syntax();
        }
        text(docTypeDecl.getBeforeTagDelimiterPrefix());
        syntax();
    }

    private void ident(Xml.@Nullable Ident ident) {
        if (ident != null) {
            text(ident.getPrefix());
            text(ident.getName());
        }
    }

    private void jsp(String prefix, String content) {
        text(prefix);
        syntax();
        text(content);
        syntax();
    }
}
