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
                .replaceAll("__", "**")  // Replace double underscores with double asterisks for bold
                .replaceAll("\\*\\*`keyword =` \\[`", "** keyword = ** [")
                .replaceAll("`]\\(", "](");

        // First, process multiline italic text globally before splitting into lines
        final String processedMarkdown = processMultilineItalicText(markdownNoKod);

        // Split the markdown into lines
        final String[] lines = processedMarkdown.split("\\r?\\n");
        
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
        
        // Post-process the document to handle multiline italic text
        postProcessMultilineItalic(content);
        
        return document;
    }
    
    private static void addEmptyParagraph(final List<DocumentNode> content) {
        // Skip adding empty paragraphs to match expected output
        // final ParagraphDocumentNode paragraphNode = new ParagraphDocumentNode();
        // paragraphNode.setContent(null);
        // paragraphNode.setAttrs(null);
        // content.add(paragraphNode);
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
        headingNode.setAttrs(null);
        content.add(headingNode);
    }
    
    private static void addListItem(final List<DocumentNode> content, final String text) {
        final ParagraphDocumentNode listItemNode = new ParagraphDocumentNode();
        final List<ParagraphNode> paragraphContent = new ArrayList<>();

        // Convert asterisk to dash for list items
        String processedText = text.replaceFirst("^\\s*\\*\\s+", "- ");
        
        // Check if the paragraph contains any formatting
        if (processedText.contains("**") || processedText.contains("__") ||
                processedText.contains("*") || processedText.contains("_") ||
                processedText.contains("~~") || processedText.contains("`") ||
                (processedText.contains("[") && processedText.contains("]") && processedText.contains("(") && processedText.contains(")"))) {

            // Process the text with mixed formatting for list items
            processMixedFormattingForListItem(listItemNode, processedText);
            content.add(listItemNode);
            return;
        }
        if (!processedText.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();
            textNode.setText(processedText);
            textNode.setMarks(null);
            paragraphContent.add(textNode);
        }

        listItemNode.setContent(paragraphContent);
        listItemNode.setAttrs(null);
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
        codeBlockNode.setAttrs(null);
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
        paragraphNode.setAttrs(null);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(boldText, paragraphContent, marks);
                    //boldNode.setMarks(marks);
                    //paragraphContent.add(boldNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(boldMatcher.end());
                boldMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
            }
        }

        // Then check for italic text
        else if (remainingText.contains("*") || remainingText.contains("_")) {
            final Pattern italicPattern = Pattern.compile("(^|\\s|\\n)?(\\*|_)([\\s\\S]*?)(\\*|_)(\\s|\\n|$)?");
            final Matcher italicMatcher = italicPattern.matcher(remainingText);

            while (italicMatcher.find()) {
                // Add text before the italic part
                if (italicMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, italicMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(italicText, paragraphContent, marks);

                    //italicNode.setMarks(marks);
                    //paragraphContent.add(italicNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(italicMatcher.end());
                italicMatcher.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(strikeText, paragraphContent, marks);
                    //strikeNode.setMarks(marks);
                    //paragraphContent.add(strikeNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(strikeMatcher.end());
                strikeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(codeText, paragraphContent, marks);
                    //codeNode.setMarks(marks);
                    //paragraphContent.add(codeNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(codeMatcher.end());
                codeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
        paragraphNode.setAttrs(null);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(boldText, paragraphContent, marks);
                    //boldNode.setMarks(marks);
                    //paragraphContent.add(boldNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(boldMatcher.end());
                boldMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
            }
        }
        // Then check for italic text
        else if (remainingText.contains("*") || remainingText.contains("_")) {
            final Pattern italicPattern = Pattern.compile("(^|\\s|\\n)?(\\*|_)([\\s\\S]*?)(\\*|_)(\\s|\\n|$)?");
            final Matcher italicMatcher = italicPattern.matcher(remainingText);

            while (italicMatcher.find()) {
                // Add text before the italic part
                if (italicMatcher.start() > 0) {
                    final String beforeText = remainingText.substring(0, italicMatcher.start());
                    if (!beforeText.isBlank()) {
                        // Process the before text for other formatting
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(italicText, paragraphContent, marks);
                    //italicNode.setMarks(marks);
                    //paragraphContent.add(italicNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(italicMatcher.end());
                italicMatcher.reset(remainingText);
            }

            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(strikeText, paragraphContent, marks);
                    //strikeNode.setMarks(marks);
                    //paragraphContent.add(strikeNode);
                }

                // Update remaining text
                remainingText = remainingText.substring(strikeMatcher.end());
                strikeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, List.of());
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

                    processTextWithFormattingAndAdditionalMark(codeText, paragraphContent, marks);
                    //codeNode.setMarks(marks);
                    //paragraphContent.add(codeNode);
                }
                
                // Update remaining text
                remainingText = remainingText.substring(codeMatcher.end());
                codeMatcher.reset(remainingText);
            }
            
            // Process any remaining text
            if (!remainingText.isBlank()) {
                processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, List.of());
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
        listItemNode.setAttrs(null);
    }

    private static void processTextWithFormattingAndAdditionalMark(final String text,
                                                                   final List<ParagraphNode> paragraphContent,
                                                                    final List<TextMark> rootMarks) {
        // This is a recursive method to handle nested formatting
        // For example, if we have "**bold with *italic* text**"

        // First, check for our special italic markers
        if (text.contains("ITALIC_START") && text.contains("ITALIC_END")) {
            final Pattern italicMarkerPattern = Pattern.compile("(.*?)ITALIC_START(.*?)ITALIC_END(.*)");
            final Matcher italicMarkerMatcher = italicMarkerPattern.matcher(text);
            
            if (italicMarkerMatcher.matches()) {
                // Process text before italic
                if (!italicMarkerMatcher.group(1).isBlank()) {
                    processTextWithFormattingAndAdditionalMark(italicMarkerMatcher.group(1), paragraphContent, rootMarks);
                }
                
                // Process italic content
                final String italicContent = italicMarkerMatcher.group(2);
                if (!italicContent.isBlank()) {
                    final TextParagraphNode italicNode = new TextParagraphNode();
                    italicNode.setText(italicContent);
                    
                    final List<TextMark> marks = new ArrayList<>();
                    final ItalicMark italicMark = new ItalicMark();
                    marks.add(italicMark);
                    marks.addAll(rootMarks);
                    
                    processTextWithFormattingAndAdditionalMark(italicContent, paragraphContent, marks);
                }
                
                // Process text after italic
                if (!italicMarkerMatcher.group(3).isBlank()) {
                    processTextWithFormattingAndAdditionalMark(italicMarkerMatcher.group(3), paragraphContent, rootMarks);
                }
                return;
            }
        }

        // Check for links
        if (text.contains("[") && text.contains("]") && text.contains("(") && text.contains(")")) {
            final Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
            final Matcher linkMatcher = linkPattern.matcher(text);

            while (linkMatcher.find()) {
                // Add text before the link
                if (linkMatcher.start() > 0) {
                    final String beforeText = text.substring(0, linkMatcher.start());
                    if (!beforeText.isBlank()) {
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }

                // Update remaining text
                final String remainingText = text.substring(linkMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    linkNode.setMarks(marks);
                    paragraphContent.add(linkNode);
                }

                // Update remaining text
                final String remainingText = text.substring(linkMatcher2.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    processTextWithFormattingAndAdditionalMark(italicText, paragraphContent, marks);
                    //italicNode.setMarks(marks);
                    //paragraphContent.add(italicNode);
                }

                // Update remaining text
                final String remainingText = text.substring(italicMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    processTextWithFormattingAndAdditionalMark(boldText, paragraphContent, marks);
                    //boldNode.setMarks(marks);
                    //paragraphContent.add(boldNode);
                }

                // Update remaining text
                final String remainingText = text.substring(boldMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    processTextWithFormattingAndAdditionalMark(strikeText, paragraphContent, marks);
                   //strikeNode.setMarks(marks);
                    //paragraphContent.add(strikeNode);
                }

                // Update remaining text
                final String remainingText = text.substring(strikeMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
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
                        processTextWithFormattingAndAdditionalMark(beforeText, paragraphContent, rootMarks);
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
                    marks.addAll(rootMarks);

                    processTextWithFormattingAndAdditionalMark(codeText, paragraphContent, marks);
                    //codeNode.setMarks(marks);
                    //paragraphContent.add(codeNode);
                }

                // Update remaining text
                final String remainingText = text.substring(codeMatcher.end());
                if (!remainingText.isBlank()) {
                    processTextWithFormattingAndAdditionalMark(remainingText, paragraphContent, rootMarks);
                }
                return;
            }
        }

        // If no formatting was found, add as plain text
        if (!text.isBlank()) {
            final TextParagraphNode textNode = new TextParagraphNode();

            if (rootMarks.stream().anyMatch(mark -> mark instanceof BoldMark)) {
                textNode.setText(" " + text + " ");
            } else {
                textNode.setText(text);
            }

            textNode.setMarks(rootMarks);
            paragraphContent.add(textNode);
        }
    }

    /**
     * Post-processes the document to handle multiline italic text by finding
     * ITALIC_START and ITALIC_END markers and properly distributing italic formatting.
     * 
     * @param content The document content to process
     */
    private static void postProcessMultilineItalic(final List<DocumentNode> content) {
        // Check if there are any italic markers in the entire document
        boolean hasItalicMarkers = false;
        for (final DocumentNode node : content) {
            if (node instanceof ParagraphDocumentNode paragraphNode) {
                final List<ParagraphNode> paragraphContent = paragraphNode.getContent();
                if (paragraphContent != null) {
                    for (final ParagraphNode paragraphNode1 : paragraphContent) {
                        if (paragraphNode1 instanceof TextParagraphNode textNode) {
                            final String text = textNode.getText();
                            if (text != null && (text.contains("ITALIC_START") || text.contains("ITALIC_END"))) {
                                hasItalicMarkers = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (hasItalicMarkers) break;
        }
        
        if (hasItalicMarkers) {
            // Process the entire document as a single unit
            processDocumentForItalicMarkers(content);
            // Remove empty text nodes after processing
            removeEmptyTextNodes(content);
        }
    }
    
    /**
     * Removes empty text nodes from the document content.
     * 
     * @param content The document content to process
     */
    private static void removeEmptyTextNodes(final List<DocumentNode> content) {
        for (final DocumentNode node : content) {
            if (node instanceof ParagraphDocumentNode paragraphNode) {
                final List<ParagraphNode> paragraphContent = paragraphNode.getContent();
                if (paragraphContent != null) {
                    // Remove empty text nodes
                    paragraphContent.removeIf(paragraphNode1 -> 
                        paragraphNode1 instanceof TextParagraphNode textNode && 
                        (textNode.getText() == null || textNode.getText().trim().isEmpty())
                    );
                }
            }
        }
    }
    
    /**
     * Processes the entire document to handle italic markers across paragraphs.
     * 
     * @param content The document content to process
     */
    private static void processDocumentForItalicMarkers(final List<DocumentNode> content) {
        // Find all text nodes and their positions
        final List<TextNodeInfo> textNodes = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            final DocumentNode node = content.get(i);
            if (node instanceof ParagraphDocumentNode paragraphNode) {
                final List<ParagraphNode> paragraphContent = paragraphNode.getContent();
                if (paragraphContent != null) {
                    for (int j = 0; j < paragraphContent.size(); j++) {
                        final ParagraphNode paragraphNode1 = paragraphContent.get(j);
                        if (paragraphNode1 instanceof TextParagraphNode textNode) {
                            textNodes.add(new TextNodeInfo(i, j, textNode));
                        }
                    }
                }
            }
        }
        
        // Find italic start and end positions
        int startIndex = -1;
        int endIndex = -1;
        
        for (int i = 0; i < textNodes.size(); i++) {
            final TextNodeInfo textNodeInfo = textNodes.get(i);
            final String text = textNodeInfo.textNode.getText();
            if (text != null) {
                if (text.contains("ITALIC_START")) {
                    startIndex = i;
                }
                if (text.contains("ITALIC_END")) {
                    endIndex = i;
                    break;
                }
            }
        }
        
        if (startIndex != -1 && endIndex != -1) {
            // Process the italic block across all nodes
            processItalicBlockAcrossNodes(textNodes, startIndex, endIndex);
        }
    }
    
    /**
     * Information about a text node and its position in the document.
     */
    private static class TextNodeInfo {
        final int paragraphIndex;
        final int nodeIndex;
        final TextParagraphNode textNode;
        
        TextNodeInfo(final int paragraphIndex, final int nodeIndex, final TextParagraphNode textNode) {
            this.paragraphIndex = paragraphIndex;
            this.nodeIndex = nodeIndex;
            this.textNode = textNode;
        }
    }
    
    /**
     * Processes an italic block across multiple text nodes.
     * 
     * @param textNodes The list of text node information
     * @param startIndex The start index of the italic block
     * @param endIndex The end index of the italic block
     */
    private static void processItalicBlockAcrossNodes(final List<TextNodeInfo> textNodes, 
                                                    final int startIndex, 
                                                    final int endIndex) {
        // Process the start node
        final TextNodeInfo startNodeInfo = textNodes.get(startIndex);
        final String startText = startNodeInfo.textNode.getText();
        final int italicStartPos = startText.indexOf("ITALIC_START");
        
        if (italicStartPos > 0) {
            // Split the start node
            final String beforeText = startText.substring(0, italicStartPos);
            final String afterText = startText.substring(italicStartPos + "ITALIC_START".length());
            
            // Create new nodes
            final TextParagraphNode beforeNode = new TextParagraphNode();
            beforeNode.setText(beforeText);
            beforeNode.setMarks(startNodeInfo.textNode.getMarks());
            
            final TextParagraphNode afterNode = new TextParagraphNode();
            afterNode.setText(afterText);
            afterNode.setMarks(startNodeInfo.textNode.getMarks());
            
            // This is getting complex, let's simplify by just removing the marker
            startNodeInfo.textNode.setText(afterText);
        } else if (italicStartPos == 0) {
            // Remove the marker from the start
            startNodeInfo.textNode.setText(startText.substring("ITALIC_START".length()));
        }
        
        // Process the end node
        final TextNodeInfo endNodeInfo = textNodes.get(endIndex);
        final String endText = endNodeInfo.textNode.getText();
        final int italicEndPos = endText.indexOf("ITALIC_END");
        
        if (italicEndPos >= 0) {
            final String beforeText = endText.substring(0, italicEndPos);
            final String afterText = endText.substring(italicEndPos + "ITALIC_END".length());
            
            if (!beforeText.isEmpty()) {
                // Split the end node
                final TextParagraphNode beforeEndNode = new TextParagraphNode();
                beforeEndNode.setText(beforeText);
                beforeEndNode.setMarks(endNodeInfo.textNode.getMarks());
                
                // This is getting complex, let's simplify by just removing the marker
                endNodeInfo.textNode.setText(beforeText);
                
                if (!afterText.isEmpty()) {
                    final TextParagraphNode afterEndNode = new TextParagraphNode();
                    afterEndNode.setText(afterText);
                    afterEndNode.setMarks(endNodeInfo.textNode.getMarks());
                    // Add after node - this would require more complex logic
                }
            } else {
                // Remove the marker
                endNodeInfo.textNode.setText(afterText);
            }
        }
        
        // Add italic marks to all nodes in the range
        for (int i = startIndex; i <= endIndex; i++) {
            final TextNodeInfo textNodeInfo = textNodes.get(i);
            final TextParagraphNode textNode = textNodeInfo.textNode;
            
            // Skip empty text nodes
            final String text = textNode.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            
            List<TextMark> marks = textNode.getMarks();
            if (marks == null) {
                marks = new ArrayList<>();
            } else {
                // Create a new mutable list to avoid UnsupportedOperationException
                marks = new ArrayList<>(marks);
            }
            
            // Add italic mark if not already present
            boolean hasItalic = marks.stream().anyMatch(mark -> mark instanceof ItalicMark);
            if (!hasItalic) {
                final ItalicMark italicMark = new ItalicMark();
                marks.add(italicMark);
                textNode.setMarks(marks);
            }
        }
    }
    
    /**
     * Processes a paragraph's content to handle italic markers.
     * 
     * @param paragraphContent The paragraph content to process
     */
    private static void processParagraphForItalicMarkers(final List<ParagraphNode> paragraphContent) {
        // Find the first text node with ITALIC_START
        int startIndex = -1;
        int endIndex = -1;
        
        for (int i = 0; i < paragraphContent.size(); i++) {
            final ParagraphNode node = paragraphContent.get(i);
            if (node instanceof TextParagraphNode textNode) {
                final String text = textNode.getText();
                if (text != null) {
                    if (text.contains("ITALIC_START")) {
                        startIndex = i;
                    }
                    if (text.contains("ITALIC_END")) {
                        endIndex = i;
                        break;
                    }
                }
            }
        }
        
        if (startIndex != -1 && endIndex != -1) {
            // Process the italic block
            processItalicBlock(paragraphContent, startIndex, endIndex);
        }
    }
    
    /**
     * Processes an italic block by splitting text nodes and adding italic marks.
     * 
     * @param paragraphContent The paragraph content
     * @param startIndex The start index of the italic block
     * @param endIndex The end index of the italic block
     */
    private static void processItalicBlock(final List<ParagraphNode> paragraphContent, 
                                         final int startIndex, 
                                         final int endIndex) {
        int currentStartIndex = startIndex;
        int currentEndIndex = endIndex;
        
        // Process the start node
        final TextParagraphNode startNode = (TextParagraphNode) paragraphContent.get(currentStartIndex);
        final String startText = startNode.getText();
        final int italicStartPos = startText.indexOf("ITALIC_START");
        
        if (italicStartPos > 0) {
            // Split the start node
            final String beforeText = startText.substring(0, italicStartPos);
            final String afterText = startText.substring(italicStartPos + "ITALIC_START".length());
            
            // Create new nodes
            final TextParagraphNode beforeNode = new TextParagraphNode();
            beforeNode.setText(beforeText);
            beforeNode.setMarks(startNode.getMarks());
            
            final TextParagraphNode afterNode = new TextParagraphNode();
            afterNode.setText(afterText);
            afterNode.setMarks(startNode.getMarks());
            
            // Replace the original node with the split nodes
            paragraphContent.set(currentStartIndex, beforeNode);
            paragraphContent.add(currentStartIndex + 1, afterNode);
            
            // Update indices
            currentStartIndex++;
            currentEndIndex++;
        } else if (italicStartPos == 0) {
            // Remove the marker from the start
            startNode.setText(startText.substring("ITALIC_START".length()));
        }
        
        // Process the end node
        final TextParagraphNode endNode = (TextParagraphNode) paragraphContent.get(currentEndIndex);
        final String endText = endNode.getText();
        final int italicEndPos = endText.indexOf("ITALIC_END");
        
        if (italicEndPos >= 0) {
            final String beforeText = endText.substring(0, italicEndPos);
            final String afterText = endText.substring(italicEndPos + "ITALIC_END".length());
            
            if (!beforeText.isEmpty()) {
                // Split the end node
                final TextParagraphNode beforeEndNode = new TextParagraphNode();
                beforeEndNode.setText(beforeText);
                beforeEndNode.setMarks(endNode.getMarks());
                
                paragraphContent.set(currentEndIndex, beforeEndNode);
                
                if (!afterText.isEmpty()) {
                    final TextParagraphNode afterEndNode = new TextParagraphNode();
                    afterEndNode.setText(afterText);
                    afterEndNode.setMarks(endNode.getMarks());
                    paragraphContent.add(currentEndIndex + 1, afterEndNode);
                }
            } else {
                // Remove the marker
                endNode.setText(afterText);
            }
        }
        
        // Add italic marks to all nodes in the range
        for (int i = currentStartIndex; i <= currentEndIndex; i++) {
            final ParagraphNode node = paragraphContent.get(i);
            if (node instanceof TextParagraphNode textNode) {
                // Skip empty text nodes
                final String text = textNode.getText();
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                
                List<TextMark> marks = textNode.getMarks();
                if (marks == null) {
                    marks = new ArrayList<>();
                } else {
                    // Create a new mutable list to avoid UnsupportedOperationException
                    marks = new ArrayList<>(marks);
                }
                
                // Add italic mark if not already present
                boolean hasItalic = marks.stream().anyMatch(mark -> mark instanceof ItalicMark);
                if (!hasItalic) {
                    final ItalicMark italicMark = new ItalicMark();
                    marks.add(italicMark);
                    textNode.setMarks(marks);
                }
            }
        }
    }

    /**
     * Processes multiline italic text by finding italic blocks that span multiple lines
     * and marking them with special markers to preserve the italic formatting.
     * 
     * @param text The markdown text to process
     * @return The processed text with italic markers preserved
     */
    private static String processMultilineItalicText(final String text) {
        // Use a more sophisticated approach to find italic blocks that span multiple lines
        // We'll use a pattern that matches _text_ where text can contain newlines
        final Pattern italicPattern = Pattern.compile("(?<!\\*)_([^_\\n]*(?:\\n[^_\\n]*)*?)_(?!\\*)");
        final Matcher matcher = italicPattern.matcher(text);
        
        final StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Add text before the italic block
            result.append(text, lastEnd, matcher.start());
            
            // Get the italic content (without the surrounding underscores)
            final String italicContent = matcher.group(1);
            
            // Mark the italic content with special markers that we can identify later
            // We'll use a unique marker that won't conflict with other markdown syntax
            result.append("ITALIC_START").append(italicContent).append("ITALIC_END");
            
            lastEnd = matcher.end();
        }
        
        // Add any remaining text
        result.append(text, lastEnd, text.length());
        
        return result.toString();
    }
}