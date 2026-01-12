package music.resona.online.bridge.test

import music.resona.online.bridge.InnertubeBridge
import music.resona.online.bridge.models.HomePageResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll

class TestHomeData {
    
    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            InnertubeBridge.initialize("en", "US")
            println("\n✓ Bridge initialized")
        }
    }
    
    @Test
    fun testHomePageData() {
        println("=".repeat(60))
        println("YouTube Home Feed Data Test via InnertubeBridge")
        println("=".repeat(60))
        
        // Get the home feed synchronously
        println("\nFetching home feed...")
        val homeResult: HomePageResult = InnertubeBridge.getHomeSync()
        
        // Display summary
        println("\n" + "─".repeat(60))
        println("HOME FEED SUMMARY")
        println("─".repeat(60))
        println("Total sections: ${homeResult.sections.size}")
        println("Has continuation: ${homeResult.continuation != null}")
        
        // Display each section with details
        println("\n" + "═".repeat(60))
        println("SECTIONS BREAKDOWN")
        println("═".repeat(60))
        
        homeResult.sections.forEachIndexed { index, section ->
            println("\n[${ index + 1 }] ${section.title}")
            println("    Items: ${section.items.size}")
            
            // Group items by type
            val itemsByType = section.items.groupBy { it.type }
            itemsByType.forEach { (type, items) ->
                println("    └─ $type: ${items.size} items")
            }
            
            // Show first 2 items as examples
            section.items.take(2).forEachIndexed { itemIndex, item ->
                println("\n    Example ${itemIndex + 1}:")
                println("      • Title: ${item.title}")
                println("      • Type: ${item.type}")
                println("      • ID: ${item.id}")
                if (item.artists.isNotEmpty()) {
                    println("      • Artists: ${item.artists.joinToString(", ") { it.name }}")
                }
                if (item.album != null) {
                    println("      • Album: ${item.album.name}")
                }
            }
            
            if (section.items.size > 2) {
                println("    ... and ${section.items.size - 2} more items")
            }
        }
        
        // Section title analysis
        println("\n" + "═".repeat(60))
        println("SECTION TITLE ANALYSIS")
        println("═".repeat(60))
        
        val sectionCategories = homeResult.sections.groupBy { section ->
            when {
                section.title.contains("quick", ignoreCase = true) -> "Quick Picks"
                section.title.contains("new", ignoreCase = true) -> "New Releases"
                section.title.contains("mix", ignoreCase = true) -> "Mixes"
                section.title.contains("similar", ignoreCase = true) -> "Similar To"
                section.title.contains("radio", ignoreCase = true) -> "Radio"
                section.title.contains("recommended", ignoreCase = true) -> "Recommended"
                section.title.contains("trending", ignoreCase = true) -> "Trending"
                section.title.contains("chart", ignoreCase = true) -> "Charts"
                section.title.contains("mood", ignoreCase = true) -> "Mood/Genre"
                section.title.contains("artist", ignoreCase = true) -> "Artist Content"
                section.title.contains("album", ignoreCase = true) -> "Albums"
                section.title.contains("playlist", ignoreCase = true) -> "Playlists"
                else -> "Other"
            }
        }
        
        sectionCategories.forEach { (category, sections) ->
            println("\n$category: ${sections.size} section(s)")
            sections.forEach { section ->
                println("  • ${section.title} (${section.items.size} items)")
            }
        }
        
        // Content type analysis
        println("\n" + "═".repeat(60))
        println("CONTENT TYPE DISTRIBUTION")
        println("═".repeat(60))
        
        val allItems = homeResult.sections.flatMap { it.items }
        val itemTypeCount = allItems.groupBy { it.type }.mapValues { it.value.size }
        
        itemTypeCount.forEach { (type, count) ->
            val percentage = (count * 100.0 / allItems.size)
            println("$type: $count (%.1f%%)".format(percentage))
        }
        
        println("\n" + "═".repeat(60))
        println("TEST COMPLETED SUCCESSFULLY")
        println("═".repeat(60))
    }
}
