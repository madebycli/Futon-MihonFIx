// Regression for Kototoro ab6051d2 (Kototoro-app/Kototoro, Apache-2.0): a text filter is matched by its exact key.
package io.github.landwarderer.futon.mihon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.github.landwarderer.futon.mihon.parsers.model.ContentListFilter
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class MihonFilterMapperTest {

    private val source = mock(ContentSource::class.java)

    private fun text(name: String) = object : Filter.Text(name) {}

    private fun filterWith(vararg keys: String) =
        ContentListFilter(tags = keys.mapTo(HashSet()) { ContentTag(it, it, source) })

    @Test
    fun aTextFilterTakesOnlyItsOwnValue() {
        val title = text("Title")
        val titleTwo = text("Title 2")
        MihonFilterMapper.updateMihonFilters(
            FilterList(title, titleTwo),
            filterWith("text:top:Title 2=second"),
        )
        assertEquals("", title.state)
        assertEquals("second", titleTwo.state)
    }

    @Test
    fun aTextFilterWithoutAValueIsEmptied() {
        val title = text("Title")
        MihonFilterMapper.updateMihonFilters(FilterList(title), filterWith("text:top:Title"))
        assertEquals("", title.state)
    }

    @Test
    fun aTextFilterKeepsItsValue() {
        val title = text("Title")
        MihonFilterMapper.updateMihonFilters(FilterList(title), filterWith("text:top:Title=a=b"))
        assertEquals("a=b", title.state)
    }
}
