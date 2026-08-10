package com.jktdeveloper.habitto.ui.settings

import com.jktdeveloper.habitto.notifications.NotificationTypeId
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings rows used to render `type.key.replace('_', ' ')`, so a new enum entry
 * shipped as "Sync failed persistent". This is the guard against that coming back.
 */
class NotificationTypeUiTest {

    @Test
    fun `every notification type has a label and both icon variants`() {
        for (type in NotificationTypeId.entries) {
            assertTrue("no UI metadata for $type", hasUiMetadata(type))
            assertTrue("blank label for $type", type.uiLabel.isNotBlank())
            assertNotEquals("$type falls back to its raw key", type.key, type.uiLabel)
            // Off rows draw the outlined variant; identical icons would erase the cue.
            assertNotEquals(
                "$type has the same icon on and off",
                type.uiIcon(enabled = true),
                type.uiIcon(enabled = false),
            )
        }
    }
}
