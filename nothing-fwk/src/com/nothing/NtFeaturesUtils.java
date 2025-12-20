package com.nothing;

import android.os.Build;
import android.os.SystemProperties;

import java.math.BigInteger;
import java.util.BitSet;

public class NtFeaturesUtils {

    private static final int MAX_FEATURE = 156;

    public static final int NTF_QCOM = 59;
    public static final int NTF_MTK = 60;
    public static final int NTF_SPACEWAR = 61;
    public static final int NTF_PONG = 62;
    public static final int NTF_DRAGONITE = 110;
    public static final int NTF_BACKGROUND_RES_LIMIT = 115;

    private static final BitSet sFeatures;

    static {
        final String fullProp = SystemProperties.get("ro.build.nothing.feature.base", "0");
        final String productDiffProp = SystemProperties.get(
                "ro.build.nothing.feature.diff.product." + Build.PRODUCT, "0");
        final String deviceDiffProp = SystemProperties.get(
                "ro.build.nothing.feature.diff.device." + Build.DEVICE, "0");
        final String plusDiffProp = SystemProperties.get(
                "ro.build.nothing.feature.diff.plus." + Build.DEVICE, "0");
        final String customProp = SystemProperties.get("persist.custom", "0");
        final String sysConfigCustomProp = SystemProperties.get("persist.sys.config.custom", "0");

        sFeatures = new BitSet(MAX_FEATURE + 1);

        setFeatures(parseFeatures(fullProp));
        toggleFeatures(parseFeatures(productDiffProp));
        toggleFeatures(parseFeatures(deviceDiffProp));
        toggleFeatures(parseFeatures(sysConfigCustomProp));
        if ("pro".equalsIgnoreCase(SystemProperties.get("ro.boot.pbid", "base"))) {
            toggleFeatures(parseFeatures(plusDiffProp));
        }
        toggleFeatures(parseFeatures(customProp));
    }

    public static boolean isSupport(int... features) {
        for (int feature : features) {
            if (feature < 0 || feature > MAX_FEATURE || !sFeatures.get(feature)) {
                return false;
            }
        }
        return true;
    }

    private static void setFeatures(BigInteger mask) {
        int index = 0;
        while (!mask.equals(BigInteger.ZERO)) {
            if (mask.testBit(0)) {
                sFeatures.set(index);
            }
            index++;
            mask = mask.shiftRight(1);
        }
    }

    private static void toggleFeatures(BigInteger mask) {
        int index = 0;
        while (!mask.equals(BigInteger.ZERO)) {
            if (mask.testBit(0)) {
                sFeatures.flip(index);
            }
            index++;
            mask = mask.shiftRight(1);
        }
    }

    private static BigInteger parseFeatures(String value) {
        String hex = value == null ? "" : value.replace("0x", "").replace("L", "");
        return new BigInteger(hex, 16);
    }
}
