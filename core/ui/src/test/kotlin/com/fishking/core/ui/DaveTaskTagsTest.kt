package com.fishking.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DaveTaskTagsTest {
    @Test fun replacesCustomTagsWithoutChangingTitle() {
        assertEquals("买牛奶 #生活 #采购", replaceDaveTaskTags("买牛奶 #旧标签", "生活 #采购 生活"))
    }
    @Test fun emptyInputRemovesTagsButPreservesTitle() {
        assertEquals("买牛奶", replaceDaveTaskTags("买牛奶 #生活 #采购", ""))
    }
    @Test fun acceptsHashWhitespaceAndCommaSeparators() {
        assertEquals("计划 #工作 #生活 #周末", replaceDaveTaskTags("计划", "#工作,生活，周末"))
    }
}
