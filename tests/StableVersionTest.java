package com.cosmerearchives.androidimageeditor;

final class StableVersionTest {
    public static void main(String[] args) {
        expect("v0.1.0", 1000);
        expect("v0.1.1", 1001);
        expect("v0.1.9", 1009);
        expect("v0.1.10", 1010);
        expect("v1.0.0", 1000000);
        expect("v0.1.1-beta", -1);
        expect("v0.1.1+dev", -1);
        expect("v0.01.1", -1);
        expect("v0.1000.1", -1);
        expect("0.1.1", -1);
    }

    private static void expect(String tag, int code) {
        if (StableVersion.codeFromTag(tag) != code) {
            throw new AssertionError("Unexpected code for " + tag);
        }
    }
}
