package com.nothing;

import android.os.Build;
import android.os.SystemProperties;

import java.math.BigInteger;
import java.util.BitSet;

public class NtFeaturesUtils {

    public static final int NTF_QCOM = 59;
    public static final int NTF_MTK = 60;
    public static final int NTF_SPACEWAR = 61;
    public static final int NTF_PONG = 62;
    public static final int NTF_DRAGONITE = 110;
    public static final int NTF_BACKGROUND_RES_LIMIT = 115;

    private static final BitSet sFeatures;

    static {
        final String fullProp = SystemProperties.get("ro.build.nothing.feature.base", "0");
        final String productDiffProp = SystemProperties.get("ro.build.nothing.feature.diff.product." + Build.PRODUCT, "0");
        final String deviceDiffProp = SystemProperties.get("ro.build.nothing.feature.diff.device." + Build.DEVICE, "0");

        int bitsetSize = maxLength(replace(fullProp),replace(productDiffProp),replace(deviceDiffProp)) * 4;

        sFeatures = new BitSet(bitsetSize);

        base(new BigInteger(replace(fullProp), 16));
        change(new BigInteger(replace(productDiffProp), 16));
        change(new BigInteger(replace(deviceDiffProp), 16));
    }

    public static boolean isSupport(int... features) {
        for (int feature : features) {
            if (feature < 0 || feature >= sFeatures.length()) {
                return false;
            }
            if (!sFeatures.get(feature)) {
                return false;
            }
        }
        return true;
    }

    private static void base(BigInteger bi) {
        int index = 0;
        while (!bi.equals(BigInteger.ZERO)) {
            if (bi.testBit(0)) {
                sFeatures.set(index);
            }
            index++;
            bi = bi.shiftRight(1);
        }
    }

    private static void change(BigInteger bi) {
        int index = 0;
        while (!bi.equals(BigInteger.ZERO)) {
            if (bi.testBit(0)) {
                sFeatures.flip(index);
            }
            index++;
            bi = bi.shiftRight(1);
        }
    }

    private static String replace(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("0x", "").replace("L", "");
    }

    private static int maxLength(String... strs) {
        int max = 0;
        for (String s : strs) {
            if (s.length() > max) {
                max = s.length();
            }
        }
        return max;
    }
}
