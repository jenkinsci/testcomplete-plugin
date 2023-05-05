/*
 * The MIT License
 *
 * Copyright (c) 2015, SmartBear Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.smartbear.jenkins.plugins.testcomplete;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Igor Filin
 */
public class ScreenResolution {

    private static final String SEPARATOR = "x";

    private int width;
    private int height;

    private static ScreenResolution defaultResolution;
    private static List<ScreenResolution> list;

    static {
        list = new ArrayList<>();

        list.add(new ScreenResolution(800, 600));
        list.add(new ScreenResolution(1024, 600));
        list.add(new ScreenResolution(1024, 768));
        list.add(new ScreenResolution(1152, 864));
        list.add(new ScreenResolution(1280, 720));
        list.add(new ScreenResolution(1280, 768));
        list.add(new ScreenResolution(1280, 800));

        defaultResolution = new ScreenResolution(1280, 1024);
        list.add(defaultResolution);

        list.add(new ScreenResolution(1360, 768));
        list.add(new ScreenResolution(1366, 768));
        list.add(new ScreenResolution(1440, 900));
        list.add(new ScreenResolution(1600, 900));
        list.add(new ScreenResolution(1600, 1200));
        list.add(new ScreenResolution(1680, 1050));
        list.add(new ScreenResolution(1920, 1080));
        list.add(new ScreenResolution(1920, 1200));
        list.add(new ScreenResolution(2560, 1440));
        list.add(new ScreenResolution(2560, 1600));
        list.add(new ScreenResolution(3200, 1200));
        list.add(new ScreenResolution(3840, 2160));
        list.add(new ScreenResolution(7680, 4320));
    }

    public ScreenResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String toString() {
        return Integer.toString(width) + SEPARATOR + Integer.toString(height);
    }

    static ScreenResolution parseResolution(String resolutionString) {
        if (resolutionString == null || resolutionString.isEmpty())
            return null;

        String[] parts = resolutionString.split(SEPARATOR);
        if (parts.length != 2)
            return null;

        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);

            for (ScreenResolution item : list) {
                if (item.getWidth() == width && item.getHeight() == height) {
                    return item;
                }
            }

            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<ScreenResolution> getList() {
        return new ArrayList<>(list);
    }

    public static ScreenResolution getDefaultResolution() {
        return defaultResolution;
    }

    public static String getDefaultResolutionString() {
        return getDefaultResolution().toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + width;
        result = prime * result + height;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ScreenResolution other = (ScreenResolution) obj;
        if (width != other.width)
            return false;
        if (height != other.height)
            return false;
        return true;
    }

}
