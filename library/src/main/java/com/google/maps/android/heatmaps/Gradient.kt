/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.maps.android.heatmaps

import android.graphics.Color

/**
 * A class to generate a color map from a given array of colors and the fractions
 * that the colors represent by interpolating between their HSV values.
 * This color map is to be used in the HeatmapTileProvider.
 *
 * @param mColors The colors to be used in the gradient.
 * @param mStartPoints The starting point for each color, given as a percentage of the maximum intensity.
 * @param mColorMapSize The size of the color map to be generated by the Gradient.
 */
class Gradient @JvmOverloads constructor(
    val mColors: IntArray,
    val mStartPoints: FloatArray,
    val mColorMapSize: Int = DEFAULT_COLOR_MAP_SIZE
) {
    private data class ColorInterval(
        val color1: Int,
        val color2: Int,
        /**
         * The period over which the color changes from color1 to color2.
         * This is given as the number of elements it represents in the colorMap.
         */
        val duration: Float
    )

    /**
     * Creates a Gradient with the given colors and starting points which creates a colorMap of given size.
     * The colors and starting points are given as parallel arrays.
     *
     * @param colors       The colors to be used in the gradient
     * @param startPoints  The starting point for each color, given as a percentage of the maximum intensity
     * This is given as an array of floats with values in the interval [0,1]
     * @param colorMapSize The size of the colorMap to be generated by the Gradient
     */
    /**
     * Creates a Gradient with the given colors and starting points.
     * These are given as parallel arrays.
     *
     * @param colors      The colors to be used in the gradient
     * @param startPoints The starting point for each color, given as a percentage of the maximum intensity
     * This is given as an array of floats with values in the interval [0,1]
     */
    init {
        require(mColors.size == mStartPoints.size) { "colors and startPoints should be same length" }
        require(mColors.isNotEmpty()) { "No colors have been defined" }

        for (i in 1 until mStartPoints.size) {
            require(mStartPoints[i] > mStartPoints[i - 1]) { "startPoints should be in increasing order" }
        }
    }

    private fun generateColorIntervals(): HashMap<Int, ColorInterval> {
        val colorIntervals = HashMap<Int, ColorInterval>()
        // Create first color if not already created
        // The initial color is transparent by default
        if (mStartPoints[0] != 0f) {
            val initialColor = Color.argb(
                0, Color.red(mColors[0]), Color.green(mColors[0]), Color.blue(
                    mColors[0]
                )
            )
            colorIntervals[0] =
                ColorInterval(initialColor, mColors[0], mColorMapSize * mStartPoints[0])
        }
        // Generate color intervals
        for (i in 1 until mColors.size) {
            colorIntervals[(mColorMapSize * mStartPoints[i - 1]).toInt()] =
                ColorInterval(
                    mColors[i - 1],
                    mColors[i],
                    mColorMapSize * (mStartPoints[i] - mStartPoints[i - 1])
                )
        }
        // Extend to a final color
        // If color for 100% intensity is not given, the color of highest intensity is used.
        if (mStartPoints[mStartPoints.size - 1] != 1f) {
            val i = mStartPoints.size - 1
            colorIntervals[(mColorMapSize * mStartPoints[i]).toInt()] = ColorInterval(
                mColors[i],
                mColors[i],
                mColorMapSize * (1 - mStartPoints[i])
            )
        }
        return colorIntervals
    }

    /**
     * Generates the color map to use with a provided gradient.
     *
     * @param opacity Overall opacity of entire image: every individual alpha value will be
     * multiplied by this opacity.
     * @return the generated color map based on the gradient
     */
    fun generateColorMap(opacity: Double): IntArray {
        val colorIntervals = generateColorIntervals()
        val colorMap = IntArray(mColorMapSize)
        var interval = colorIntervals[0]
        var start = 0
        for (i in 0 until mColorMapSize) {
            if (colorIntervals.containsKey(i)) {
                interval = colorIntervals[i]
                start = i
            }
            val ratio = (i - start) / interval!!.duration
            colorMap[i] = interpolateColor(
                interval.color1, interval.color2, ratio
            )
        }
        if (opacity != 1.0) {
            for (i in 0 until mColorMapSize) {
                val c = colorMap[i]
                colorMap[i] = Color.argb(
                    (Color.alpha(c) * opacity).toInt(),
                    Color.red(c), Color.green(c), Color.blue(c)
                )
            }
        }

        return colorMap
    }

    companion object {
        private const val DEFAULT_COLOR_MAP_SIZE = 1000

        /**
         * Helper function for creation of color map
         * Interpolates between two given colors using their HSV values.
         *
         * @param color1 First color
         * @param color2 Second color
         * @param ratio  Between 0 to 1. Fraction of the distance between color1 and color2
         * @return Color associated with x2
         */
        @JvmStatic
        fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
            val alpha =
                ((Color.alpha(color2) - Color.alpha(color1)) * ratio + Color.alpha(color1)).toInt()

            val hsv1 = FloatArray(3)
            Color.RGBToHSV(Color.red(color1), Color.green(color1), Color.blue(color1), hsv1)
            val hsv2 = FloatArray(3)
            Color.RGBToHSV(Color.red(color2), Color.green(color2), Color.blue(color2), hsv2)

            // adjust so that the shortest path on the color wheel will be taken
            if (hsv1[0] - hsv2[0] > 180) {
                hsv2[0] += 360f
            } else if (hsv2[0] - hsv1[0] > 180) {
                hsv1[0] += 360f
            }

            // Interpolate using calculated ratio
            val result = FloatArray(3)
            for (i in 0..2) {
                result[i] = (hsv2[i] - hsv1[i]) * (ratio) + hsv1[i]
            }

            return Color.HSVToColor(alpha, result)
        }
    }
}