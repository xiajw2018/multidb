package com.xiajw.multidb;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.xa.DruidXADataSource;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class MultiDatasourceRegister implements EnvironmentAware, ImportBeanDefinitionRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(MultiDatasourceRegister.class);
    private Environment env;
    private Binder binder;
    private static Map<String,Object> registerBean = new ConcurrentHashMap<>();

    //凡注册到Spring容器内的bean，实现了EnvironmentAware接口重写setEnvironment方法后，在工程启动时可以获得application.yml的配置文件配置的属性值。
    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
        this.binder = Binder.get(this.env);
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map multiDatasources;
        try{
            multiDatasources = this.binder.bind("spring.datasource.druid",Map.class).get();
        }catch (NoSuchElementException e){
            logger.error("Failed to configure fastDep DataSource: 'xiajw.datasource' attribute is not specified and no embedded datasource could be configured.");
            return;
        }

        Set<String> keySet = multiDatasources.keySet();
        for(String key:keySet){
            DruidXADataSource druidDataSource = this.binder.bind("spring.datasource.druid."+key,DruidXADataSource.class).get();
            List<String> xaDataSource = Arrays.asList("oracle","mysql","postgresql");
            Supplier datasourceSupplier;
            if(xaDataSource.contains(druidDataSource.getDbType())){
                datasourceSupplier = () ->{
                    AtomikosDataSourceBean registerDataSource = (AtomikosDataSourceBean)registerBean.get(key+"Datasource");
                    if(registerDataSource == null){
                        registerDataSource = new AtomikosDataSourceBean();
                        registerDataSource.setXaDataSourceClassName("com.alibaba.druid.pool.xa.DruidXADataSource");
                        registerDataSource.setXaDataSource(druidDataSource);
                        registerDataSource.setUniqueResourceName(key);
                        registerDataSource.setMinPoolSize(druidDataSource.getMinIdle());
                        registerDataSource.setMaxPoolSize(druidDataSource.getMaxActive());
                        registerDataSource.setBorrowConnectionTimeout((int)druidDataSource.getTimeBetweenEvictionRunsMillis());
                        registerDataSource.setMaxIdleTime((int)druidDataSource.getMaxEvictableIdleTimeMillis());
                        registerDataSource.setTestQuery(druidDataSource.getValidationQuery());
                        registerBean.put(key+"Datasource",registerDataSource);
                    }
                    return registerDataSource;
                };
            }else{
                datasourceSupplier = () ->{
                    DruidDataSource registerDataSource = (DruidDataSource)registerBean.get(key+"Datasource");
                    if(registerDataSource == null){
                        registerBean.put(key+"Datasource",druidDataSource);
                        return druidDataSource;
                    }
                    return registerDataSource;
                };
            }

            javax.sql.DataSource dataSource = (javax.sql.DataSource) datasourceSupplier.get();
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(javax.sql.DataSource.class,datasourceSupplier);
            AbstractBeanDefinition dataSourceBean = builder.getRawBeanDefinition();
            dataSourceBean.setDependsOn("txManager");
            registry.registerBeanDefinition(key+"Datasource",dataSourceBean);
            Supplier<SqlSessionFactory> sqlSessionFactorySupplier = () -> {
                SqlSessionFactory registerSqlSessionFactory = (SqlSessionFactory)registerBean.get(key+"SqlSessionFactory");
                if(registerSqlSessionFactory != null){
                    return registerSqlSessionFactory;
                }else{
                    try {
                        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
                        sqlSessionFactoryBean.setDataSource(dataSource);
                        sqlSessionFactoryBean.setTypeAliasesPackage(this.env.getProperty("mybatis.typeAliasesPackage"));
                        sqlSessionFactoryBean.setMapperLocations((new PathMatchingResourcePatternResolver()).getResources(this.env.getProperty("mybatis.mapper-locations")));
                        BindResult<Configuration> bindResult = this.binder.bind("mybatis.configuration",Configuration.class);
                        if(bindResult.isBound()){
                            sqlSessionFactoryBean.setConfiguration(bindResult.get());
                        }
                        registerSqlSessionFactory = sqlSessionFactoryBean.getObject();
                        registerBean.put(key+"SqlSessionFactory",registerSqlSessionFactory);
                        return registerSqlSessionFactory;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            SqlSessionFactory sqlSessionFactory = sqlSessionFactorySupplier.get();
            BeanDefinitionBuilder builder2 =BeanDefinitionBuilder.genericBeanDefinition(SqlSessionFactory.class,sqlSessionFactorySupplier);
            AbstractBeanDefinition sqlSessionFactoryBean = builder2.getRawBeanDefinition();
            registry.registerBeanDefinition(key+"SqlSessionFactory",sqlSessionFactoryBean);
            GenericBeanDefinition sqlSessionTemplate = new GenericBeanDefinition();
            sqlSessionTemplate.setBeanClass(SqlSessionTemplate.class);
            ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
            constructorArgumentValues.addIndexedArgumentValue(0,sqlSessionFactory);
            sqlSessionTemplate.setConstructorArgumentValues(constructorArgumentValues);
            registry.registerBeanDefinition(key+"SqlSessionTemplate",sqlSessionTemplate);
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
            scanner.setSqlSessionTemplateBeanName(key+"SqlSessionTemplate");
            scanner.registerFilters();
            String mapperProperty = this.env.getProperty("xiajw.datasource."+key+".mapper");
            if(mapperProperty == null){
                logger.error("Failed to configure fastDep DataSource: fastdep.datasource." + key + ".mapper cannot be null.");
                return;
            }
            scanner.doScan(mapperProperty);
            logger.info("Registration dataSource ({}DataSource) !", key);
        }
        logger.info("Registration dataSource completed !");
    }
}
