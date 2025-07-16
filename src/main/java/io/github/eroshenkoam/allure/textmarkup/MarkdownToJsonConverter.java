package io.github.eroshenkoam.allure.textmarkup;

import io.qameta.allure.ee.client.dto.textmarkup.DefaultTextMarkupDocument;
import io.qameta.allure.ee.client.dto.textmarkup.TextMarkupDocument;
import io.qameta.allure.ee.client.dto.textmarkup.document.DocumentNode;
import io.qameta.allure.ee.client.dto.textmarkup.document.ParagraphDocumentNode;
import io.qameta.allure.ee.client.dto.textmarkup.paragraph.ParagraphNode;
import io.qameta.allure.ee.client.dto.textmarkup.paragraph.TextParagraphNode;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.BoldMark;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.CodeMark;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.ItalicMark;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.LinkMark;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.StrikeMark;
import io.qameta.allure.ee.client.dto.textmarkup.textmark.TextMark;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to convert Markdown text to JSON structure.
 * This converter supports basic GitHub Markdown syntax as described in:
 */
public final class MarkdownToJsonConverter {

    private MarkdownToJsonConverter() {}
    /**
     * Converts Markdown text to JSON structure.
     *
     * @param markdown The Markdown text to convert
     * @return JSON string representation of the Markdown
     */
    public static TextMarkupDocument convertToJson(final String markdown) {
        final DefaultTextMarkupDocument document = new DefaultTextMarkupDocument();
        final List<DocumentNode> content = new ArrayList<>();
        document.setContent(content);

        final String markdownNoKod = markdown
                .replaceAll("\\*\\*`keyword =` \\[`", "** keyword = ** [")
                .replaceAll("`]\\(", "](");

        // Split the markdown into lines
        final String[] lines = markdownNoKod.split("\\r?\\n");
        
        // Process each line
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            
            // Skip empty lines
            if (line.trim().isBlank()) {
                addEmptyParagraph(content);
                continue;
            }
            
            // Check for headings
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                final String headingText = line.substring(level);
                addHeading(content, level, headingText);
                continue;
            }
            
            // Check for list items
            if (line.matches("^\\s*[-*]\\s+.+")) {
                addListItem(content, line);
                continue;
            }
            
            // Check for numbered list items
            if (line.matches("^\\s*\\d+\\.\\s+.+")) {
                addListItem(content, line);
                continue;
            }
            
            // Check for code blocks
            if (line.startsWith("```")) {
                final StringBuilder codeBlock = new StringBuilder();
                i++; // Skip the opening ```
                while (i < lines.length && !lines[i].startsWith("```")) {
                    codeBlock.append(lines[i]).append("\n");
                    i++;
                }
                addCodeBlock(content, codeBlock.toString());
                continue;
            }
            
            // Default: treat as regular paragraph with potential mixed formatting
            addParagraph(content, line);
        }
        
        return document;
    }
    
    private static void addEmptyParagraph(final List<DocumentNode> content) {
        final ParagraphDocumentNode paragraphNode = new ParagraphDocumentNode();
        paragraphNode.setContent(null);
        paragraphNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(paragraphNode);
    }
    
    private static void addHeading(final List<DocumentNode> content, final int level, final String text) {
        final ParagraphDocumentNode headingNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();

        if (!text.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(text);

            // Add bold mark to make the heading text bold
            final List<TextMark> marks = new ArrayList<>();
            final BoldMark boldMark = new BoldMark();
            marks.add(boldMark);
            textNode.setMarks(marks);

            paragraphContent.add(textNode);
        }

        headingNode.setContent(paragraphContent);
        headingNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(headingNode);
    }
    
    private static void addListItem(final List<DocumentNode> content, final String text) {
        final ParagraphDocumentNode listItemNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();

        // Check if the paragraph contains any formatting
        if (text.contains("**") || text.contains("__") ||
                text.contains("*") || text.contains("_") ||
                text.contains("~~") || text.contains("`") ||
                (text.contains("[") && text.contains("]") && text.contains("(") && text.contains(")"))) {

            // Process the text with mixed formatting for list items
            processMixedFormattingForListItem(listItemNode, text);
            content.add(listItemNode);
            return;
        }
        if (!text.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(text);
            textNode.setMarks(null);
            paragraphContent.add(textNode);
        }

        listItemNode.setContent(paragraphContent);
        listItemNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(listItemNode);
    }
    
    private static void addCodeBlock(final List<DocumentNode> content, final String code) {
        final ParagraphDocumentNode codeBlockNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();

        if (!code.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(code);

            final List<TextMark> marks = new ArrayList<>();
            final CodeMark codeMark = new CodeMark();
            marks.add(codeMark);

            textNode.setMarks(marks);
            paragraphContent.add(textNode);
        }

        codeBlockNode.setContent(paragraphContent);
        codeBlockNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(codeBlockNode);
    }
    
    private static void addParagraph(final List<DocumentNode> content, final String text) {
        final ParagraphDocumentNode paragraphNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();
        
        // Check if the paragraph contains any formatting
        if (text.contains("**") || text.contains("__") ||
            text.contains("*") || text.contains("_") ||
            text.contains("~~") || text.contains("`") ||
            (text.contains("[") && text.contains("]") && text.contains("(") && text.contains(")"))) {
            
            // Process the text with mixed formatting
            processMixedFormatting(content, text);
            return;
        }
        
        // If no formatting, add as plain text
        if (!text.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(text);
            textNode.setMarks(null);
            paragraphContent.add(textNode);
        }

        paragraphNode.setContent(paragraphContent);
        paragraphNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(paragraphNode);
    }
    
    private static void processMixedFormatting(final List<DocumentNode> content, final String text) {
        final ParagraphDocumentNode paragraphNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();
        
        String remainingText = text;
        
        // Process the text in order of precedence
        // First, check for links as they have the most complex structure
        if (remainingText.contains("[") && remainingText.contains("]") && 
            remainingText.contains("(") && remainingText.contains(")")) {
            final Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
            final Matcher linkMatcher = linkPattern.matcher(remainingText);
            
            while (linkMatcher.find()) {
                // Add text before the link
                if (linkMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, linkMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the link part
                final String linkText = linkMatcher.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkMatcher.group(2));
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(linkMatcher.end());
                linkMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then, check for links in other format
        else if (remainingText.contains("[") && remainingText.contains("]") &&
                remainingText.contains("http")) {
            final Pattern linkPattern2 = Pattern.compile("\\[\\d+\\]:\\s*(https?://[^\\s]+)");
            final Matcher linkMatcher2 = linkPattern2.matcher(remainingText);

            while (linkMatcher2.find()) {
                // Add text before the link
                if (linkMatcher2.start() > 0) {
                    final String beforeText = remainingText.substring(0, linkMatcher2.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }

                // Add the link part
                final String linkText = linkMatcher2.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkText);
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(linkMatcher2.end());
                linkMatcher2.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for bold text
        else if (remainingText.contains("**") || remainingText.contains("__")) {
            final Pattern boldPattern = Pattern.compile("(^|\\s|\\n)?(\\*\\*|__)([\\s\\S]*?)(\\*\\*|__)(\\s|\\n|$)?");
            final Matcher boldMatcher = boldPattern.matcher(remainingText);
            
            while (boldMatcher.find()) {
                // Add text before the bold part
                if (boldMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, boldMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the bold part
                final String boldText = boldMatcher.group(3);
                if (!boldText.isBlank()) {
                    final TextParagraphNode boldNode = new TextParagraphNode();
                    boldNode.setText(" " + boldText + " ");

                    final List<TextMark> marks = new ArrayList<>();
                    final BoldMark boldMark = new BoldMark();
                    marks.add(boldMark);

                    boldNode.setMarks(marks);
                    paragraphContent.add(boldNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(boldMatcher.end());
                boldMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for italic text
        else if (remainingText.contains(" *") || remainingText.contains(" _")) {
            final Pattern italicPattern = Pattern.compile("(^|\\s|\\n)?(\\*|_)([\\s\\S]*?)(\\*|_)(\\s|\\n|$)?");
            final Matcher italicMatcher = italicPattern.matcher(remainingText);

            while (italicMatcher.find()) {
                // Add text before the italic part
                if (italicMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, italicMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }

                // Add the italic part
                final String italicText = italicMatcher.group(2);
                if (!italicText.isBlank()) {
                    final TextParagraphNode italicNode = new TextParagraphNode();
                    italicNode.setText(italicText);

                    final List<TextMark> marks = new ArrayList<>();
                    final ItalicMark italicMark = new ItalicMark();
                    marks.add(italicMark);

                    italicNode.setMarks(marks);
                    paragraphContent.add(italicNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(italicMatcher.end());
                italicMatcher.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for strikethrough text
        else if (remainingText.contains("~~")) {
            final Pattern strikePattern = Pattern.compile("(^|\\s|\\n)?~~([\\s\\S]*?)~~(\\s|\\n|$)?");
            final Matcher strikeMatcher = strikePattern.matcher(remainingText);
            
            while (strikeMatcher.find()) {
                // Add text before the strikethrough part
                if (strikeMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, strikeMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the strikethrough part
                final String strikeText = strikeMatcher.group(2);
                if (!strikeText.isBlank()) {
                    final TextParagraphNode strikeNode = new TextParagraphNode();
                    strikeNode.setText(strikeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final StrikeMark strikeMark = new StrikeMark();
                    marks.add(strikeMark);

                    strikeNode.setMarks(marks);
                    paragraphContent.add(strikeNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(strikeMatcher.end());
                strikeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for inline code
        else if (remainingText.contains("`")) {
            final Pattern codePattern = Pattern.compile("(^|\\s|\\n)?`([\\s\\S]*?)`(\\s|\\n|$)?");
            final Matcher codeMatcher = codePattern.matcher(remainingText);
            
            while (codeMatcher.find()) {
                // Add text before the code
                if (codeMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, codeMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the code part
                final String codeText = codeMatcher.group(2);
                if (!codeText.isBlank()) {
                    final TextParagraphNode codeNode = new TextParagraphNode();
                    codeNode.setText(codeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final CodeMark codeMark = new CodeMark();
                    marks.add(codeMark);

                    codeNode.setMarks(marks);
                    paragraphContent.add(codeNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(codeMatcher.end());
                codeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // If no formatting was found, add as plain text
        else {
            if (!remainingText.isBlank()) {
                final TextParagraphNode textNode = new TextParagraphNode();
                textNode.setText(remainingText);
                textNode.setMarks(null);
                paragraphContent.add(textNode);
            }
        }
        
        paragraphNode.setContent(paragraphContent);
        paragraphNode.setAttrs(new ParagraphDocumentNode.Attrs());
        content.add(paragraphNode);
    }
    
    private static void processMixedFormattingForListItem(final ParagraphDocumentNode listItemNode, final String text) {
        final List<ParagraphNode> paragraphContent = new ArrayList<>();
        
        String remainingText = text;
        
        // Process the text in order of precedence
        // First, check for links as they have the most complex structure
        if (remainingText.contains("[") && remainingText.contains("]") && 
            remainingText.contains("(") && remainingText.contains(")")) {
            final Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
            final Matcher linkMatcher = linkPattern.matcher(remainingText);
            
            while (linkMatcher.find()) {
                // Add text before the link
                if (linkMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, linkMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the link part
                final String linkText = linkMatcher.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkMatcher.group(2));
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(linkMatcher.end());
                linkMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then, check for links in other format
        else if (remainingText.contains("[") && remainingText.contains("]") &&
                remainingText.contains("http")) {
            final Pattern linkPattern2 = Pattern.compile("\\[\\d+\\]:\\s*(https?://[^\\s]+)");
            final Matcher linkMatcher2 = linkPattern2.matcher(remainingText);

            while (linkMatcher2.find()) {
                // Add text before the link
                if (linkMatcher2.start() > 0) {
                    final String beforeText = remainingText.substring(0, linkMatcher2.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }

                // Add the link part
                final String linkText = linkMatcher2.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkMatcher2.group(2));
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(linkMatcher2.end());
                linkMatcher2.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for bold text
        else if (remainingText.contains("**") || remainingText.contains("__")) {
            final Pattern boldPattern = Pattern.compile("(^|\\s|\\n)?(\\*\\*|__)([\\s\\S]*?)(\\*\\*|__)(\\s|\\n|$)?");
            final Matcher boldMatcher = boldPattern.matcher(remainingText);
            
            while (boldMatcher.find()) {
                // Add text before the bold part
                if (boldMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, boldMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the bold part
                final String boldText = boldMatcher.group(3);
                if (!boldText.isBlank()) {
                    final TextParagraphNode boldNode = new TextParagraphNode();
                    boldNode.setText(" " + boldText + " ");

                    final List<TextMark> marks = new ArrayList<>();
                    final BoldMark boldMark = new BoldMark();
                    marks.add(boldMark);

                    boldNode.setMarks(marks);
                    paragraphContent.add(boldNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(boldMatcher.end());
                boldMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for italic text
        else if (remainingText.contains(" *") || remainingText.contains(" _")) {
            final Pattern italicPattern = Pattern.compile("(^|\\s|\\n)?(\\*|_)([\\s\\S]*?)(\\*|_)(\\s|\\n|$)?");
            final Matcher italicMatcher = italicPattern.matcher(remainingText);

            while (italicMatcher.find()) {
                // Add text before the italic part
                if (italicMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, italicMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }

                // Add the italic part
                final String italicText = italicMatcher.group(2);
                if (!italicText.isBlank()) {
                    final TextParagraphNode italicNode = new TextParagraphNode();
                    italicNode.setText(italicText);

                    final List<TextMark> marks = new ArrayList<>();
                    final ItalicMark italicMark = new ItalicMark();
                    marks.add(italicMark);

                    italicNode.setMarks(marks);
                    paragraphContent.add(italicNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(italicMatcher.end());
                italicMatcher.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for strikethrough text
        else if (remainingText.contains("~~")) {
            final Pattern strikePattern = Pattern.compile("(^|\\s|\\n)?~~([\\s\\S]*?)~~(\\s|\\n|$)?");
            final Matcher strikeMatcher = strikePattern.matcher(remainingText);
            
            while (strikeMatcher.find()) {
                // Add text before the strikethrough part
                if (strikeMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, strikeMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the strikethrough part
                final String strikeText = strikeMatcher.group(2);
                if (!strikeText.isBlank()) {
                    final TextParagraphNode strikeNode = new TextParagraphNode();
                    strikeNode.setText(strikeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final StrikeMark strikeMark = new StrikeMark();
                    marks.add(strikeMark);

                    strikeNode.setMarks(marks);
                    paragraphContent.add(strikeNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(strikeMatcher.end());
                strikeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // Then check for inline code
        else if (remainingText.contains("`")) {
            final Pattern codePattern = Pattern.compile("(^|\\s|\\n)?`([\\s\\S]*?)`(\\s|\\n|$)?");
            final Matcher codeMatcher = codePattern.matcher(remainingText);
            
            while (codeMatcher.find()) {
                // Add text before the code
                if (codeMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, codeMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the code part
                final String codeText = codeMatcher.group(2);
                if (!codeText.isBlank()) {
                    final TextParagraphNode codeNode = new TextParagraphNode();
                    codeNode.setText(codeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final CodeMark codeMark = new CodeMark();
                    marks.add(codeMark);

                    codeNode.setMarks(marks);
                    paragraphContent.add(codeNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(codeMatcher.end());
                codeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormatting(remainingText, paragraphContent);
            }
        }
        // If no formatting was found, add as plain text
        else {
            if (!remainingText.isBlank()) {
                final TextParagraphNode textNode = new TextParagraphNode();
                textNode.setText(remainingText);
                textNode.setMarks(null);
                paragraphContent.add(textNode);
            }
        }
        
        listItemNode.setContent(paragraphContent);
        listItemNode.setAttrs(new ParagraphDocumentNode.Attrs());
    }
    
    private static void processTextWithFormatting(final String text, final List<ParagraphNode> paragraphContent) {
        // This is a recursive method to handle nested formatting
        // For example, if we have "**bold with *italic* text**"
        
        // Check for links
        if (text.contains("[") && text.contains("]") && text.contains("(") && text.contains(")")) {
            final Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
            final Matcher linkMatcher = linkPattern.matcher(text);
            
            while (linkMatcher.find()) {
                // Add text before the link
                if (linkMatcher.start() > 0) {
                    final String beforeText = text.substring(0, linkMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the link part
                final String linkText = linkMatcher.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkMatcher.group(2));
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }
                
                // Update remaining text
                final String remainingText = text.substring(linkMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }

        // Check for links
        if (text.contains("[") && text.contains("]") &&
                text.contains("http")) {
            final Pattern linkPattern2 = Pattern.compile("\\[\\d+\\]:\\s*(https?://[^\\s]+)");
            final Matcher linkMatcher2 = linkPattern2.matcher(text);

            while (linkMatcher2.find()) {
                // Add text before the link
                if (linkMatcher2.start() > 0) {
                    final String beforeText = text.substring(0, linkMatcher2.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }

                // Add the link part
                final String linkText = linkMatcher2.group(1);
                if (!linkText.isBlank()) {
                    final TextParagraphNode linkNode = new TextParagraphNode();
                    linkNode.setText(linkText);

                    final List<TextMark> marks = new ArrayList<>();
                    final LinkMark linkMark = new LinkMark();
                    final LinkMark.Attrs attrs = new LinkMark.Attrs();
                    attrs.setHref(linkMatcher2.group(2));
                    attrs.setTarget("_blank");
                    attrs.setRel("noopener noreferrer nofollow");
                    linkMark.setAttrs(attrs);
                    marks.add(linkMark);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }

                // Update remaining text
                final String remainingText = text.substring(linkMatcher2.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }
        
        // Check for bold text
        if (text.contains("**") || text.contains("__")) {
            final Pattern boldPattern = Pattern.compile("(^|\\s|\\n)?(\\*\\*|__)([\\s\\S]*?)(\\*\\*|__)(\\s|\\n|$)?");
            final Matcher boldMatcher = boldPattern.matcher(text);
            
            while (boldMatcher.find()) {
                // Add text before the bold part
                if (boldMatcher.start() > 0) {
                    final String beforeText = text.substring(0, boldMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the bold part
                final String boldText = boldMatcher.group(3);
                if (!boldText.isBlank()) {
                    final TextParagraphNode boldNode = new TextParagraphNode();
                    boldNode.setText(" " + boldText + " ");

                    final List<TextMark> marks = new ArrayList<>();
                    final BoldMark boldMark = new BoldMark();
                    marks.add(boldMark);

                    boldNode.setMarks(marks);
                    paragraphContent.add(boldNode);
                }

                // Update remaining text
                final String remainingText = text.substring(boldMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }
        
        // Check for italic text
        if (text.contains("*") || text.contains("_")) {
            final Pattern italicPattern = Pattern.compile("(^|\\s|\\n)?(\\*|_)([\\s\\S]*?)(\\*|_)(\\s|\\n|$)?");
            final Matcher italicMatcher = italicPattern.matcher(text);
            
            while (italicMatcher.find()) {
                // Add text before the italic part
                if (italicMatcher.start() > 0) {
                    final String beforeText = text.substring(0, italicMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the italic part
                final String italicText = italicMatcher.group(3);
                if (!italicText.isBlank()) {
                    final TextParagraphNode italicNode = new TextParagraphNode();
                    italicNode.setText(italicText);

                    final List<TextMark> marks = new ArrayList<>();
                    final ItalicMark italicMark = new ItalicMark();
                    marks.add(italicMark);

                    italicNode.setMarks(marks);
                    paragraphContent.add(italicNode);
                }

                // Update remaining text
                final String remainingText = text.substring(italicMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }
        
        // Check for strikethrough text
        if (text.contains("~~")) {
            final Pattern strikePattern = Pattern.compile("(^|\\s|\\n)?~~([\\s\\S]*?)~~(\\s|\\n|$)?");
            final Matcher strikeMatcher = strikePattern.matcher(text);
            
            while (strikeMatcher.find()) {
                // Add text before the strikethrough part
                if (strikeMatcher.start() > 0) {
                    final String beforeText = text.substring(0, strikeMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the strikethrough part
                final String strikeText = strikeMatcher.group(2);
                if (!strikeText.isBlank()) {
                    final TextParagraphNode strikeNode = new TextParagraphNode();
                    strikeNode.setText(strikeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final StrikeMark strikeMark = new StrikeMark();
                    marks.add(strikeMark);

                    strikeNode.setMarks(marks);
                    paragraphContent.add(strikeNode);
                }
                
                // Update remaining text
                final String remainingText = text.substring(strikeMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }
        
        // Check for inline code
        if (text.contains("`")) {
            final Pattern codePattern = Pattern.compile("(^|\\s|\\n)?`([\\s\\S]*?)`(\\s|\\n|$)?");
            final Matcher codeMatcher = codePattern.matcher(text);
            
            while (codeMatcher.find()) {
                // Add text before the code
                if (codeMatcher.start() > 0) {
                    final String beforeText = text.substring(0, codeMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormatting(beforeText, paragraphContent);
                    }
                }
                
                // Add the code part
                final String codeText = codeMatcher.group(2);
                if (!codeText.isBlank()) {
                    final TextParagraphNode codeNode = new TextParagraphNode();
                    codeNode.setText(codeText);

                    final List<TextMark> marks = new ArrayList<>();
                    final CodeMark codeMark = new CodeMark();
                    marks.add(codeMark);

                    codeNode.setMarks(marks);
                    paragraphContent.add(codeNode);
                }

                // Update remaining text
                final String remainingText = text.substring(codeMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormatting(remainingText, paragraphContent);
                }
                return;
            }
        }
        
        // If no formatting was found, add as plain text
        if (!text.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(text);
            textNode.setMarks(null);
            paragraphContent.add(textNode);
        }
    }
}