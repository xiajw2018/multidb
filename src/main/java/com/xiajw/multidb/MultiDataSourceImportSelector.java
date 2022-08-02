package com.xiajw.multidb;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class MultiDataSourceImportSelector implements ImportSelector, BeanClassLoaderAware {

    private ClassLoader classLoader;

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> factories = new ArrayList<>(new LinkedHashSet<>(
                SpringFactoriesLoader.loadFactoryNames(EnableXiajwMultiDatasource.class, this.classLoader)));
        AnnotationAttributes attributes = AnnotationAttributes
                .fromMap(importingClassMetadata.getAnnotationAttributes(EnableXiajwMultiDatasource.class.getName(), true));
        boolean autoRegister = attributes.getBoolean("autoRegister");
        String[] imports = factories.toArray(new String[0]);
        if(autoRegister){
            List<String> importList = new ArrayList<>(factories);
            importList.add("com.xiajw.multidb.MultiDataSourceConfiguration");
            imports = importList.toArray(new String[0]);
        }
        return imports;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
