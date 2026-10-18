package dev.stashy.navigation

/**
 * Identity for a navigation group (a retained back stack within a peer set).
 *
 * Created by [navGraph]’s `group { }` DSL.
 */
internal class NavGroup(
    /** Parent when nested inside another `group { }`; `null` for root-level peers. */
    val parent: NavGroup?,
    /** Declaration order among siblings sharing [peerSetId]. */
    val peerIndex: Int,
    /** Shared among siblings so transitions can detect same-level switches. */
    val peerSetId: Any,
) {
    val childGroups: MutableList<NavGroup> = mutableListOf()

    /** Identity for nested peer groups declared inside this group. */
    val nestedPeerSetId: Any = Any()

    /** Root-to-leaf path including this group. */
    fun pathFromRoot(): List<NavGroup> =
        generateSequence(this) { it.parent }.toList().asReversed()

    /** All groups nested under this one (depth-first). */
    fun descendants(): List<NavGroup> {
        val result = mutableListOf<NavGroup>()
        fun visit(group: NavGroup) {
            for (child in group.childGroups) {
                result.add(child)
                visit(child)
            }
        }
        visit(this)
        return result
    }
}
