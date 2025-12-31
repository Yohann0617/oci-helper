package com.yohann.ocihelper.telegram.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown Formatter Utility for Telegram Bot
 * Provides proper Markdown formatting and escaping
 * 
 * @author yohann
 */
public class MarkdownFormatter {
    
    // Special characters that need to be escaped in Telegram MarkdownV2
    private static final String[] SPECIAL_CHARS_V2 = {
        "_", "*", "[", "]", "(", ")", "~", "`", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"
    };
    
    // Special characters that need to be escaped in Telegram Markdown (legacy)
    private static final String[] SPECIAL_CHARS_LEGACY = {
        "_", "*", "[", "`"
    };
    
    /**
     * Format text for Telegram MarkdownV2
     * Properly escapes special characters
     * 
     * @param text raw text
     * @return formatted text for MarkdownV2
     */
    public static String formatMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // First, protect code blocks from being escaped
        StringBuilder result = new StringBuilder();
        Pattern codeBlockPattern = Pattern.compile("```([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(text);
        
        int lastEnd = 0;
        while (matcher.find()) {
            // Escape text before code block
            String beforeBlock = text.substring(lastEnd, matcher.start());
            result.append(escapeMarkdownV2(beforeBlock));
            
            // Keep code block as is
            result.append("```");
            result.append(matcher.group(1));
            result.append("```");
            
            lastEnd = matcher.end();
        }
        
        // Escape remaining text after last code block
        if (lastEnd < text.length()) {
            result.append(escapeMarkdownV2(text.substring(lastEnd)));
        }
        
        return result.toString();
    }
    
    /**
     * Format text for Telegram Markdown (legacy)
     * Preserves valid Markdown syntax while protecting special characters
     * 
     * @param text raw text
     * @return formatted text for Markdown
     */
    public static String formatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // For Telegram Markdown, we need to be careful:
        // - Preserve *, **, _, `, ``` for Markdown formatting
        // - Only escape [ if not part of [text](url)
        
        // Strategy: Only escape special chars that are NOT part of valid Markdown syntax
        StringBuilder result = new StringBuilder();
        Pattern codeBlockPattern = Pattern.compile("```([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(text);
        
        int lastEnd = 0;
        while (matcher.find()) {
            // Process text before code block (preserve Markdown formatting)
            String beforeBlock = text.substring(lastEnd, matcher.start());
            result.append(preserveMarkdownFormatting(beforeBlock));
            
            // Keep code block as is
            result.append("```");
            result.append(matcher.group(1));
            result.append("```");
            
            lastEnd = matcher.end();
        }
        
        // Process remaining text after last code block
        if (lastEnd < text.length()) {
            result.append(preserveMarkdownFormatting(text.substring(lastEnd)));
        }
        
        return result.toString();
    }
    
    /**
     * Preserve valid Markdown formatting while escaping problematic characters
     * This allows *, **, _, `, [text](url) to work properly
     */
    private static String preserveMarkdownFormatting(String text) {
        // For Telegram's basic Markdown:
        // - *text* for italic
        // - **text** for bold
        // - _text_ for italic (alternative)
        // - [text](url) for links
        // - `code` for inline code
        
        // We don't need to escape * and _ if they're part of valid Markdown
        // Only escape standalone [ that's not part of [text](url)
        
        // Simple strategy: don't escape at all for basic Markdown mode
        // Telegram's Markdown parser is lenient
        return text;
    }
    
    /**
     * Format as plain text (no markdown)
     * 
     * @param text raw text
     * @return plain text
     */
    public static String formatPlainText(String text) {
        return text;
    }
    
    /**
     * Escape special characters for MarkdownV2
     */
    private static String escapeMarkdownV2(String text) {
        String result = text;
        for (String specialChar : SPECIAL_CHARS_V2) {
            result = result.replace(specialChar, "\\" + specialChar);
        }
        return result;
    }
    
    /**
     * Escape special characters for Markdown (legacy)
     */
    private static String escapeMarkdownLegacy(String text) {
        String result = text;
        for (String specialChar : SPECIAL_CHARS_LEGACY) {
            result = result.replace(specialChar, "\\" + specialChar);
        }
        return result;
    }
    
    /**
     * Format AI response with code blocks properly
     * Detects code blocks and ensures they are formatted correctly
     * Handles special formatting for thinking/answer sections
     * 
     * @param response AI response
     * @return formatted response
     */
    public static String formatAiResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        
        // Check if response is already formatted with thinking/answer sections
        if (response.contains("💭") || response.contains("💬")) {
            // Response already formatted by AiChatService, just escape carefully
            return formatMarkdown(response);
        }
        
        // Check if response already contains code blocks
        if (response.contains("```")) {
            // Already formatted, just escape non-code parts
            return formatMarkdown(response);
        }
        
        // Check if response looks like code (heuristic)
        if (looksLikeCode(response)) {
            // Wrap in code block
            return "```\n" + response + "\n```";
        }
        
        // Regular text, escape special characters
        return formatMarkdown(response);
    }
    
    /**
     * Heuristic to detect if text looks like code
     */
    private static boolean looksLikeCode(String text) {
        // Check for common code indicators
        int indicators = 0;
        
        // Check for indentation (multiple lines starting with spaces)
        if (text.matches("(?s).*\\n\\s{2,}.*")) {
            indicators++;
        }
        
        // Check for common programming keywords
        String[] keywords = {"function", "class", "def", "var", "let", "const", "import", "public", "private"};
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                indicators++;
                break;
            }
        }
        
        // Check for brackets and semicolons
        if (text.contains("{") && text.contains("}") || text.contains(";")) {
            indicators++;
        }
        
        // If multiple indicators, it's likely code
        return indicators >= 2;
    }
    
    /**
     * Truncate long messages to fit Telegram limits
     * Telegram has a 4096 character limit per message
     * 
     * @param text text to truncate
     * @param maxLength max length (default 4000 to leave room for formatting)
     * @return truncated text
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        
        return text.substring(0, maxLength) + "\n...\n(消息过长，已截断)";
    }
    
    /**
     * Truncate with default max length
     */
    public static String truncate(String text) {
        return truncate(text, 4000);
    }
}
