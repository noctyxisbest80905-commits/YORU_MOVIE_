package com.moviebox.tv;
final class NativeBridge {
    static { System.loadLibrary("moviebox_tv"); }
    static native String nativeInit(String filesRoot);
    static native String nativeRequest(String request);
    private NativeBridge() {}
}
