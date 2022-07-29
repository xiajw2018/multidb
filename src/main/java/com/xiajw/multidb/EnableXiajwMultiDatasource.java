package com.xiajw.multidb;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Import(MultiDataSourceImportSelector.class)
public @interface EnableXiajwMultiDatasource {

    boolean autoRegister() default true;
}
