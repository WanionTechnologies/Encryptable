package tech.wanion.encryptable.util.extensions

/**
 * Returns the red component (0-255) of this integer interpreted as an RGB or ARGB color.
 * Assumes the integer is in RGB (0xRRGGBB) or ARGB (0xAARRGGBB) format.
 *
 * @receiver The color integer value.
 * @return The red component (0-255).
 */
val Int.red: Int get() = (this shr 16) and 0xFF

/**
 * Returns the green component (0-255) of this integer interpreted as an RGB or ARGB color.
 * Assumes the integer is in RGB (0xRRGGBB) or ARGB (0xAARRGGBB) format.
 *
 * @receiver The color integer value.
 * @return The green component (0-255).
 */
val Int.green: Int get() = (this shr 8) and 0xFF

/**
 * Returns the blue component (0-255) of this integer interpreted as an RGB or ARGB color.
 * Assumes the integer is in RGB (0xRRGGBB) or ARGB (0xAARRGGBB) format.
 *
 * @receiver The color integer value.
 * @return The blue component (0-255).
 */
val Int.blue: Int get() = this and 0xFF

/**
 * Returns the alpha component (0-255) of this integer interpreted as an ARGB color.
 * Assumes the integer is in ARGB format (0xAARRGGBB). For RGB values, alpha will be 0.
 *
 * @receiver The color integer value.
 * @return The alpha component (0-255).
 */
val Int.alpha: Int get() = (this shr 24) and 0xFF

/**
 * Returns 1 if this integer is odd, 0 if even.
 */
val Int.odd: Int get() = this and 1

/**
 * Returns true if this integer is odd, false otherwise.
 */
val Int.isOdd: Boolean get() = this.odd != 0

/**
 * Converts this integer to a hexadecimal RGB color string in the format "0xRRGGBB".
 * Ignores the alpha component if present.
 *
 * @receiver The color integer value.
 * @return Hexadecimal color string representation (e.g., "0xFF00FF").
 */
fun Int.toHexColor(): String = String.format("0x%06X", (0xFFFFFF and this))
