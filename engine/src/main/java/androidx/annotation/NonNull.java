package androidx.annotation;

import java.lang.annotation.*;

/** Stand-in for the Android annotation; it has no effect in the simulator. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface NonNull {}
