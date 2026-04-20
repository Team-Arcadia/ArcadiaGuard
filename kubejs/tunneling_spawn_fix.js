// Fix: Block ALL block breaking by survival players in arcadia:spawn
// Enchantments like Tunneling, Chainsaw, Destruction, Digging can
// bypass claim protection on secondary block breaks.
// This script blocks everything in the spawn dimension for safety.

BlockEvents.broken(event => {
    if (event.player == null) return
    if (event.player.creative) return
    if (event.level.dimension === 'arcadia:spawn') {
        event.cancel()
    }
})
