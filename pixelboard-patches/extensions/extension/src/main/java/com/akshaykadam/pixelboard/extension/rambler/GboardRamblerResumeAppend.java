package com.akshaykadam.pixelboard.extension.rambler;

import com.akshaykadam.pixelboard.extension.advancedvoice.GboardAdvancedVoice1803RuntimeSettings;

/**
 * Keeps resumed Rambler dictation at the cursor.
 *
 * <p>When dictation resumes, Gboard's AgenticDictationTextEditor.commitTextWithDiff sends the
 * text before the cursor plus the new speech to the model, gets one rewritten string back and
 * applies a character diff of the old text against that rewrite. If the rewrite places the new
 * speech inside the earlier text, the diff inserts it there and the words land in the middle of
 * the message.
 *
 * <p>This hook runs before the diff. When the field did not change during dictation and the
 * rewrite is the earlier text with one block inserted somewhere in the middle (no other edits),
 * the block is moved to the end so the diff becomes a single insert at the cursor. Any rewrite
 * that also edits the earlier text is left to stock Gboard.
 */
public final class GboardRamblerResumeAppend {
    private static volatile Boolean enabledOverrideForTest;

    private GboardRamblerResumeAppend() {
    }

    /** Called with commitTextWithDiff's (snapshot, current, current.text, rewrite) arguments. */
    public static String rewriteCommitText(
            Object snapshot,
            Object current,
            CharSequence currentText,
            String proposed) {
        try {
            if (proposed == null || currentText == null || !isEnabled()) {
                return proposed;
            }
            if (snapshot != null && !snapshot.equals(current)) {
                // Text changed during dictation; stock replaces everything.
                return proposed;
            }
            return appendTrailing(currentText.toString(), proposed);
        } catch (Throwable ignored) {
            return proposed;
        }
    }

    /**
     * Returns {@code proposed} unchanged unless it equals {@code base} with exactly one block
     * inserted before meaningful base text; in that case the block is appended to {@code base}.
     */
    static String appendTrailing(String base, String proposed) {
        int baseLength = base.length();
        int proposedLength = proposed.length();
        if (baseLength == 0 || proposedLength <= baseLength || proposed.startsWith(base)) {
            return proposed;
        }
        int prefix = 0;
        int limit = baseLength;
        while (prefix < limit && base.charAt(prefix) == proposed.charAt(prefix)) {
            prefix++;
        }
        // Diff boundaries can fall inside a word ("Two" vs "Three"); align to a word start.
        while (prefix > 0 && prefix < baseLength
                && !Character.isWhitespace(base.charAt(prefix - 1))) {
            prefix--;
        }
        String tail = base.substring(prefix);
        if (!proposed.endsWith(tail)) {
            return proposed; // Earlier text was edited, not just extended.
        }
        if (isTrailingPunctuationOrSpace(tail)) {
            // "milk." -> "milk and eggs." is a normal append that keeps the final period.
            return proposed;
        }
        String inserted = proposed.substring(prefix, proposedLength - tail.length()).trim();
        if (inserted.isEmpty()) {
            return proposed;
        }
        StringBuilder result = new StringBuilder(baseLength + inserted.length() + 1).append(base);
        char last = base.charAt(baseLength - 1);
        if (!Character.isWhitespace(last) && !startsWithClosingPunctuation(inserted)) {
            result.append(' ');
        }
        return result.append(inserted).toString();
    }

    private static boolean isTrailingPunctuationOrSpace(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && ".,!?;:…)]}\"'”’".indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithClosingPunctuation(String text) {
        return ".,!?;:…)]}".indexOf(text.charAt(0)) >= 0;
    }

    private static boolean isEnabled() {
        Boolean override = enabledOverrideForTest;
        return override != null
                ? override.booleanValue()
                : GboardAdvancedVoice1803RuntimeSettings.isRamblerAppendOnlyResumeEnabled();
    }

    static void setEnabledOverrideForTest(Boolean enabled) {
        enabledOverrideForTest = enabled;
    }
}
