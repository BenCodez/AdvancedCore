package com.bencodez.advancedcore.api.yml.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigDataDouble {
	double defaultValue() default 0;

	String path();

	String secondPath() default "";
	
	String[] possibleValues() default "";
}
