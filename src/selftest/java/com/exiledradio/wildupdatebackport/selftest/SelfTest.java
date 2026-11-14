package com.exiledradio.wildupdatebackport.selftest;

/** One stream's headless test. */
public interface SelfTest {

    /** The stream name, as passed to {@code -Pselftest=}. */
    String name();

    void run(Check check) throws Exception;

    /** A test that {@code all} leaves out: it runs only when named ({@code goatcarry} spans two runs). */
    interface ByNameOnly {
    }
}
