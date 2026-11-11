/*
 * Ported from BitChord v1.9.1 (tag 7f45c768a461fd7e68bf4613f71e8a767e643d4c),
 * files:
 *   app/src/main/java/com/music/bitchord/ui/components/ContinuousCornerShape.kt
 *   sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/components/SquirclePillShape.kt
 *
 * Copyright (C) BitChord contributors — licensed GPL-3.0-or-later.
 * Modifications for Grocool Music (2026-11-11):
 *   - package changed to dev.grocool.music.ui.shape
 *   - removed the `continuous()` extension (it referenced SquirclePillShape, now
 *     co-located here but unused by our screens)
 *   - combined into one file; algorithm and constants kept byte-for-byte
 *   - comments condensed; full upstream rationale preserved in docs/03-UI-Baseline.md
 *
 * Both files were zero-dependency pure Compose code (only androidx.compose /
 * kotlin.math imports), so they can be reused without pulling any of BitChord's
 * YouTube Music data layer.
 */
package dev.grocool.music.ui.shape

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Apple's continuous corner (the "squircle") rather than the circular arc of
 * [androidx.compose.foundation.shape.RoundedCornerShape].
 *
 * A circular corner meets its straight edge with a jump in curvature — the hard
 * "shoulder" that makes a rounded rectangle look like Material rather than iOS.
 * Here curvature ramps in over a cubic on either side of a shorter arc, which is
 * Figma's corner-smoothing construction and what Apple's surfaces use.
 *
 * [smoothing] is the share of each corner's quarter-turn handed to those ramps.
 * Every point lies on or inside the circular corner of the same radius, so a
 * surface clipped by the plain shape loses nothing.
 */
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = GLASS_CORNER_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(percent: Int, smoothing: Float = GLASS_CORNER_SMOOTHING) :
        this(CornerSize(percent), CornerSize(percent), CornerSize(percent), CornerSize(percent), smoothing)

    /** Dp-based convenience constructor used by the screens. */
    constructor(dp: androidx.compose.ui.unit.Dp, smoothing: Float = GLASS_CORNER_SMOOTHING) :
        this(CornerSize(dp), CornerSize(dp), CornerSize(dp), CornerSize(dp), smoothing)

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) return Outline.Rectangle(size.toRect())
        val ltr = layoutDirection == LayoutDirection.Ltr
        val maxRadius = size.minDimension / 2f
        val topLeft = (if (ltr) topStart else topEnd).coerceAtMost(maxRadius)
        val topRight = (if (ltr) topEnd else topStart).coerceAtMost(maxRadius)
        val bottomRight = (if (ltr) bottomEnd else bottomStart).coerceAtMost(maxRadius)
        val bottomLeft = (if (ltr) bottomStart else bottomEnd).coerceAtMost(maxRadius)
        val w = size.width
        val h = size.height
        val s = smoothing.coerceIn(0f, 1f)

        val path = Path()
        path.moveTo(topLeft * (1 + edgeSmoothing(s, topLeft, topRight, w)), 0f)
        path.corner(
            corner = Offset(w, 0f), radius = topRight,
            inward = Offset(1f, 0f), outward = Offset(0f, 1f),
            smoothingIn = edgeSmoothing(s, topRight, topLeft, w),
            smoothingOut = edgeSmoothing(s, topRight, bottomRight, h),
        )
        path.corner(
            corner = Offset(w, h), radius = bottomRight,
            inward = Offset(0f, 1f), outward = Offset(-1f, 0f),
            smoothingIn = edgeSmoothing(s, bottomRight, topRight, h),
            smoothingOut = edgeSmoothing(s, bottomRight, bottomLeft, w),
        )
        path.corner(
            corner = Offset(0f, h), radius = bottomLeft,
            inward = Offset(-1f, 0f), outward = Offset(0f, -1f),
            smoothingIn = edgeSmoothing(s, bottomLeft, bottomRight, w),
            smoothingOut = edgeSmoothing(s, bottomLeft, topLeft, h),
        )
        path.corner(
            corner = Offset.Zero, radius = topLeft,
            inward = Offset(0f, -1f), outward = Offset(1f, 0f),
            smoothingIn = edgeSmoothing(s, topLeft, bottomLeft, h),
            smoothingOut = edgeSmoothing(s, topLeft, topRight, w),
        )
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousCornerShape) return false
        return topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = " +
            "$bottomEnd, bottomStart = $bottomStart, smoothing = $smoothing)"
}

/** Apple's corner smoothing: Figma calls 60% "iOS"; measured a touch over that. */
const val GLASS_CORNER_SMOOTHING = 0.607f

/** Share of the straight run between two corners available to one corner's ramp. */
private fun edgeSmoothing(smoothing: Float, radius: Float, otherRadius: Float, length: Float): Float {
    if (radius <= 0f) return 0f
    val straight = (length - radius - otherRadius).coerceAtLeast(0f)
    val share = straight * radius / (radius + otherRadius)
    return smoothing.coerceAtMost(share / radius)
}

/** One corner, from the end of the incoming straight edge to the outgoing one. */
private fun Path.corner(
    corner: Offset,
    radius: Float,
    inward: Offset,
    outward: Offset,
    smoothingIn: Float,
    smoothingOut: Float,
) {
    if (radius <= 0f) {
        lineTo(corner.x, corner.y)
        return
    }
    val center = corner - inward * radius + outward * radius

    val inHalf = CornerHalf(radius, smoothingIn)
    fun alongIn(distance: Float, depth: Float = 0f) = corner - inward * distance + outward * depth
    lineTo(alongIn(inHalf.edge))
    val arcStart = alongIn(inHalf.arcAlong, inHalf.arcDepth)
    if (smoothingIn > 0f) {
        cubicTo(alongIn(inHalf.edge - inHalf.a), alongIn(inHalf.edge - inHalf.a - inHalf.b), arcStart)
    }

    val outHalf = CornerHalf(radius, smoothingOut)
    fun alongOut(distance: Float, depth: Float = 0f) = corner + outward * distance - inward * depth
    val startAngle = angleOf(arcStart - center)
    val sweep = 90f - 45f * smoothingIn - 45f * smoothingOut
    arcTo(
        rect = Rect(center, radius),
        startAngleDegrees = startAngle,
        sweepAngleDegrees = sweep,
        forceMoveTo = false,
    )
    if (smoothingOut > 0f) {
        cubicTo(
            alongOut(outHalf.edge - outHalf.a - outHalf.b),
            alongOut(outHalf.edge - outHalf.a),
            alongOut(outHalf.edge),
        )
    } else {
        lineTo(alongOut(outHalf.arcAlong, outHalf.arcDepth))
    }
}

/** Measurements of half a smoothed corner, along and in from its edge. */
private class CornerHalf(radius: Float, smoothing: Float) {
    private val turn = Math.toRadians(45.0 * smoothing)
    val edge = (1 + smoothing) * radius
    val arcAlong = radius - radius * sin(turn).toFloat()
    val arcDepth = radius * (1 - cos(turn).toFloat())
    private val c = radius * (tan(turn / 2) * cos(turn)).toFloat()
    val b = (edge - arcAlong - c) / 3f
    val a = 2f * b
}

private fun angleOf(v: Offset): Float = Math.toDegrees(atan2(v.y, v.x).toDouble()).toFloat()

private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)

private fun Path.cubicTo(p1: Offset, p2: Offset, p3: Offset) =
    cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)

/**
 * A pill cut as a fully rounded squircle (superellipse `|x|ⁿ + |y|ⁿ = 1`),
 * the way Liquid Glass draws capsules. [exponent] = 2 is a plain capsule;
 * 2.1 was tuned on device by the upstream authors.
 */
class SquirclePillShape(val exponent: Float = SQUIRCLE_PILL_EXPONENT) :
    CornerBasedShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50)) {

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquirclePillShape(exponent)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val r = size.minDimension / 2f
        if (r <= 0f) return Outline.Rectangle(size.toRect())
        val w = size.width
        val h = size.height
        val path = Path()
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        path.quarter(w - r, r, 1f, -1f, r, towardEdge = false)
        path.lineTo(w, h - r)
        path.quarter(w - r, h - r, 1f, 1f, r, towardEdge = true)
        path.lineTo(r, h)
        path.quarter(r, h - r, -1f, 1f, r, towardEdge = false)
        path.lineTo(0f, r)
        path.quarter(r, r, -1f, -1f, r, towardEdge = true)
        path.close()
        return Outline.Generic(path)
    }

    private fun Path.quarter(cx: Float, cy: Float, sx: Float, sy: Float, r: Float, towardEdge: Boolean) {
        val power = 2f / exponent
        for (i in 1..QUARTER_STEPS) {
            val step = if (towardEdge) i else QUARTER_STEPS - i
            val t = step * (Math.PI / 2) / QUARTER_STEPS
            val x = cos(t).toFloat().coerceAtLeast(0f).pow(power)
            val y = sin(t).toFloat().coerceAtLeast(0f).pow(power)
            lineTo(cx + sx * r * x, cy + sy * r * y)
        }
    }

    override fun equals(other: Any?): Boolean = other is SquirclePillShape && other.exponent == exponent
    override fun hashCode(): Int = exponent.hashCode()
    override fun toString(): String = "SquirclePillShape(exponent = $exponent)"
}

const val SQUIRCLE_PILL_EXPONENT = 2.1f
private const val QUARTER_STEPS = 32
