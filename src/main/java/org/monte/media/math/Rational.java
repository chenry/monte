/*
 * @(#)Rational.java  1.3  2011-08-27
 * 
 * Copyright (c) 2009-2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media.math;

import java.math.BigInteger;
import java.util.Random;
import static java.lang.Math.*;
import static org.monte.media.math.IntMath.*;

/**
 * Represents a TIFF RATIONAL number.
 * <p>
 * Two LONGs 32-bit (4-byte) unsigned integer: the first represents the
 * numerator of a fraction; the second, the denominator.
 * </p>
 * <p>
 * Invariants:
 * </p>
 * <ul>
 * <li>denominator>=0, the denominator is always a positive integer</li>
 * <li>0/1 is the unique representation of 0.</li>
 * <li>1/0,-1/0 are the unique representations of infinity.</li>
 * </ul>
 *
 * @author Werner Randelshofer
 * @version 1.3 2011-08-27 Adds add method.
 * <br>1.2 2010-09-07 Extends Number instead of Object.
 * <br>1.1 2010-07-06 Shows decimal value next to the fraction.
 * <br>1.0 2009-12-27 Created.
 */
public class Rational extends Number {

    private final long num;
    private final long den;

    public Rational(long numerator, long denominator) {
        if (numerator == 0) {
            // Invariant: 0/1 is unique representation of 0
            denominator = 1;
        }

        if (denominator == 0) {
            // Invariant: 1/0, -1/0 are unique representations of infinity
            numerator = (numerator > 0) ? 1 : -1;
        } else if (denominator < 0) {
            // Invariant: denominator is always positive
            denominator = -denominator;
            numerator = -numerator;
        }

        long g = gcd(numerator, denominator);
        num = numerator / g;
        den = denominator / g;
    }

    public Rational(Rational r) {
        this(r.num,r.den);
    }

    public long getNumerator() {
        return num;
    }

    public long getDenominator() {
        return den;
    }

    public Rational add(Rational that) {
        if (this.den == that.den) {
            // => same denominator: add numerators 
            return new Rational(this.num + that.num, this.den);
        }

        // FIXME - handle overflow
        long s = scm(this.den, that.den);
        Rational result = new Rational(
                this.num * (s / this.den) + that.num * (s / that.den),
                s);

        return result;
    }

    /** Warning. Rational is supposed to be immutable. *
    
    private Rational addAssign(Rational that) {
    if (this.den == that.den) {
    // => same denominator: add numerators 
    this.num += that.num;
    return this;
    }
    
    // FIXME - handle overflow
    long s = scm(this.den, that.den);
    this.num = this.num * (s / this.den) + that.num * (s / that.den);
    this.den = s;
    
    
    return reduceAssign();
    }*/
    public Rational subtract(Rational that) {
        return add(that.negate());
    }

    public Rational negate() {
        return new Rational(-num, den);
    }

    public Rational inverse() {
        return new Rational(den, num);
    }

    /** Returns the closest rational with the specified denominator
     * which is smaller or equal than this number. */
    public Rational floor(long d) {
        if (d==den) return new Rational(num,den);
        long s = scm(this.den, d);

        if (s == d) {
            return new Rational(num * s / den, d);
        } else if (s == den) {
            return new Rational(num * d / den, d);
        } else {
            return new Rational(num * d / den, d);
        }
    }

    /** Returns the closest rational with the specified denominator
     * which is greater or equal than this number. */
    public Rational ceil(long d) {
        if (d==den) return new Rational(num,den);
        long s = scm(this.den, d);

        if (s == d) {
            return new Rational((num * s + den - 1) / den, d);
        } else if (s == den) {
            return new Rational((num * d + den - 1) / den, d);
        } else {
            return new Rational((num * d + den - 1) / den, d);
        }
    }

    /** Warning. Rational is supposed to be immutable. * /
    private Rational reduceAssign() {
    long g = gcd(num, den);
    num /= g;
    den /= g;
    return this;
    }*/
    public Rational multiply(Rational that) {
        return new Rational(
                this.num * that.num,
                this.den * that.den);
    }
    public Rational multiply(long integer) {
        return new Rational(
                this.num * integer,
                this.den );
    }

    public Rational divide(Rational that) {
        return new Rational(
                this.num * that.den,
                this.den * that.num);
    }

    @Override
    public String toString() {
        //long gcd = IntMath.gcd(num, den);
        if (num == 0) {
            return "0";
        } else if (den == 1) {
            return Long.toString(num);
            /*} else if (gcd == den) {
            return num + "/" + den;*/
        } else {
            return Float.toString((float) num / den);
        }
    }

    public String toDescriptiveString() {
        long gcd = IntMath.gcd(num, den);
        if (gcd == 0 || num == 0) {
            return num + "/" + den + " = " + 0;
        } else if (gcd == den) {
            return num + "/" + den + " = " + Long.toString(num / den);
        } else {
            return num + "/" + den + " ≈ " + ((float) num / den);
        }
    }

    @Override
    public int intValue() {
        return (int) (num / den);
    }

    @Override
    public long longValue() {
        return num / den;
    }

    @Override
    public float floatValue() {
        return (float) num / (float) den;
    }

    @Override
    public double doubleValue() {
        return (double) num / (double) den;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Rational that = (Rational) obj;

        return compareTo(that) == 0;
    }

    /** return { -1, 0, +1 } if a < b, a = b, or a > b. */
    public int compareTo(Rational that) {

        BigInteger lhs;
        BigInteger rhs;

        if (this.den == 0 && that.den == 0) {// infinity always 1/0 or -1/0
            lhs = BigInteger.valueOf(this.num == 1 ? Long.MAX_VALUE : Long.MIN_VALUE);
            rhs = BigInteger.valueOf(that.num == 1 ? Long.MAX_VALUE : Long.MIN_VALUE);
        } else {
            lhs = BigInteger.valueOf(this.num).multiply(BigInteger.valueOf(that.den));
            rhs = BigInteger.valueOf(this.den).multiply(BigInteger.valueOf(that.num));
        }

        int cmp=lhs.compareTo(rhs);
        if (cmp<0) {
            return -1;
        }
        if (cmp>0) {
            return +1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return (int) ((num ^ (num >>> 32))
                ^ (den ^ (den >>> 32)));

    }

    public static Rational max(Rational a, Rational b) {
        return (a.compareTo(b) >= 0) ? a : b;
    }

    public static Rational min(Rational a, Rational b) {
        return (a.compareTo(b) <= 0) ? a : b;
    }

    public boolean isZero() {
        return num == 0;
    }
    public boolean isLessOrEqualZero() {
        return num <= 0;
    }

    public static Rational valueOf(double d) {
        if (d == 0) {
            return new Rational(0, 1);
        }
        if (Double.isInfinite(d)) {
            return new Rational((long) signum(d), 0);
        }
        if (Double.isNaN(d)) {
            return new Rational(0, 1); // no way to express a NaN :-(
        }
        return toRational(d, Integer.MAX_VALUE, 100);
    }

    /** Iteratively computes rational from double. 
     * <p>Reference:<br>
     * <a href="http://www2.fz-juelich.de/video/cpp/html/exercises/exercise/Rational_cpp.html">
     * http://www2.fz-juelich.de/video/cpp/html/exercises/exercise/Rational_cpp.html</a>
     * </p>
     */
    private static Rational toRational(double x, double limit, int iterations) {
        double intpart = Math.floor(x);
        double fractpart = x - intpart;
        double d = 1.0 / fractpart;
        long left = (long) intpart;
        if (d > limit || iterations == 0) {
            return new Rational(left, 1);
        } else {
            return new Rational(left, 1).add(toRational(d, limit * 0.1, --iterations).inverse());
        }
    }

    public static void main(String[] args) {
        Random r = new Random();
        long num = r.nextInt(1000);
        long den = r.nextInt(1000);
        double dbl = (double) num / den;

        dbl = r.nextDouble();

        System.out.println("num/den=" + num + "/" + den);
        System.out.println("dbl=" + dbl);
        System.out.println(Rational.valueOf(dbl).toDescriptiveString());
    }

    public Rational round(long d) {
        if (d==den) return new Rational(num,den);
        
        Rational fl=floor(d);
        Rational diffFl=subtract(fl);
        
        if (diffFl.isZero()) return fl;
        
        Rational cl=ceil(d);
        Rational diffCl=subtract(cl);
        if (diffCl.isZero()) return cl;
        
        if (diffFl.isNegative()) diffFl=diffFl.negate();
        if (diffCl.isNegative()) diffCl=diffCl.negate();
        return diffFl.compareTo(diffCl)<=0?fl:cl;
    }

    private boolean isNegative() {
        return num<0;
    }
}
