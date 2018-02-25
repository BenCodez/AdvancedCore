package com.Ben12345rocks.AdvancedCore.Util.Annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

@Inherited
@Target({ ElementType.METHOD, ElementType.FIELD })
public @interface ConfigDataBoolean {
	String path();
	boolean defaultValue() default false;
	
}
