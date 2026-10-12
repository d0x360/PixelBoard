package com.akshaykadam.pixelboard.extension.rambler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public final class GboardRamblerResumeAppendTest {
    private static final Object SNAPSHOT = "snapshot";

    @Before
    public void setUp() {
        GboardRamblerResumeAppend.setEnabledOverrideForTest(Boolean.TRUE);
    }

    @After
    public void tearDown() {
        GboardRamblerResumeAppend.setEnabledOverrideForTest(null);
    }

    @Test
    public void midTextInsertionMovesToCursor() {
        Assert.assertEquals(
                "Meet me at the park. Bring snacks. Also bring a blanket.",
                rewrite("Meet me at the park. Bring snacks.",
                        "Meet me at the park. Also bring a blanket. Bring snacks."));
    }

    @Test
    public void trailingAppendIsUnchanged() {
        String proposed = "Hello there. How are you?";
        Assert.assertSame(proposed, rewrite("Hello there.", proposed));
    }

    @Test
    public void appendBeforeFinalPunctuationIsUnchanged() {
        String proposed = "I bought milk and eggs.";
        Assert.assertSame(proposed, rewrite("I bought milk.", proposed));
    }

    @Test
    public void rewriteThatEditsEarlierTextIsLeftToStock() {
        String proposed = "Let's meet at 3 PM. See you there.";
        Assert.assertSame(proposed, rewrite("lets meet at two. ", proposed));
    }

    @Test
    public void changedFieldIsLeftToStock() {
        String proposed = "A new words B C";
        Assert.assertSame(proposed, GboardRamblerResumeAppend.rewriteCommitText(
                SNAPSHOT, "different", "A B C", proposed));
    }

    @Test
    public void disabledSettingIsLeftToStock() {
        GboardRamblerResumeAppend.setEnabledOverrideForTest(Boolean.FALSE);
        String proposed = "Meet me at the park. Also bring a blanket. Bring snacks.";
        Assert.assertSame(proposed,
                rewrite("Meet me at the park. Bring snacks.", proposed));
    }

    @Test
    public void emptyOrNullInputsAreLeftToStock() {
        Assert.assertEquals("Hi", rewrite("", "Hi"));
        Assert.assertNull(GboardRamblerResumeAppend.rewriteCommitText(
                SNAPSHOT, SNAPSHOT, "abc", null));
        Assert.assertEquals("abc", GboardRamblerResumeAppend.rewriteCommitText(
                SNAPSHOT, SNAPSHOT, null, "abc"));
    }

    @Test
    public void baseEndingInSpaceDoesNotGetDoubleSpace() {
        Assert.assertEquals("One. Two. Three.",
                rewrite("One. Two. ", "One. Three. Two. "));
    }

    private static String rewrite(String base, String proposed) {
        return GboardRamblerResumeAppend.rewriteCommitText(SNAPSHOT, SNAPSHOT, base, proposed);
    }
}
