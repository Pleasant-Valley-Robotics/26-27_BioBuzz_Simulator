package com.acmerobotics.dashboard.config;

import java.lang.annotation.*;

/** Stand-in for FTC Dashboard's @Config (the real one lives in the Android artifact). */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
    String value() default "";
}
