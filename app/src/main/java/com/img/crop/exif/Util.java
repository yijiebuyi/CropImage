package com.img.crop.exif;

import java.io.Closeable;

public class Util {
	public static boolean equals(Object a, Object b) {
		return (a == b) || (a == null ? false : a.equals(b));
	}

	public static void closeSilently(Closeable c) {
		if (c == null)
			return;
		try {
			c.close();
		} catch (Throwable t) {
			// do nothing
		}
	}
}
