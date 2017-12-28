package com.img.crop.exif;


public class Rational {

    private final long mNominator;
    private final long mDenominator;

    public Rational(long nominator, long denominator) {
        mNominator = nominator;
        mDenominator = denominator;
    }

    public long getNominator() {
        return mNominator;
    }

    public long getDenominator() {
        return mDenominator;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rational) {
            Rational data = (Rational) obj;
            return mNominator == data.mNominator && mDenominator == data.mDenominator;
        }
        return false;
    }
}
