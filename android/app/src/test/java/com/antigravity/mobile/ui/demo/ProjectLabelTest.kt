package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.data.model.ProjectItem
import org.junit.Assert.*
import org.junit.Test

class ProjectLabelTest {
    @Test fun sameNamesKeepTheirOwnLocationsAndMembership() {
        val first = ProjectItem(rawId = "a", name = "App", path = "H:/first/App")
        val second = ProjectItem(rawId = "b", name = "app", path = "H:/second/App")
        val projects = listOf(first, second)
        assertEquals(first.path, first.disambiguatingLocation(projects))
        assertEquals(second.path, second.disambiguatingLocation(projects.reversed()))
        assertNotEquals(first.navigationKey(), second.navigationKey())
        val chat = com.antigravity.mobile.data.model.ConversationItem("chat", "会话", projectId = "a")
        assertTrue(chat.belongsTo(first, projects))
        assertFalse(chat.belongsTo(second, projects))
    }

    @Test fun uniqueOrRepeatedSameProjectDoesNotAddALocation() {
        val project = ProjectItem(rawId = "a", name = "App", path = "H:/App")
        assertNull(project.disambiguatingLocation(listOf(project)))
        assertNull(project.disambiguatingLocation(listOf(project, project.copy())))
    }

    @Test fun missingPathUsesTheExistingUriOrId() {
        val first = ProjectItem(rawId = "a", name = "App", uri = "file:///H:/App")
        val second = ProjectItem(rawId = "b", name = "App")
        assertEquals(first.uri, first.disambiguatingLocation(listOf(first, second)))
        assertEquals("b", second.disambiguatingLocation(listOf(first, second)))
    }
}
