# jta + atomikos + druid 多数据源分布式事务

## 如何配置数据源

    spring:
      datasource:
        druid:
          datasource1:
            mapper: 
            url:
            driverClassName: 
            username: 
            password: 
          datasource2:
            mapper: 
            url: 
            driverClassName: 
            username: 
            password: 

## 如何使用分布式事务
通过@Transactional注解标注分布式事务管理器

    @Transactional(rollbackFor = Exception.class)
    
## 实现原理
    @AutoConfigureBefore({DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
Druid和spring boot autoconfiguration的自动注册bean，都添加了@ConditionalOnMissingBean注解,通过AutoCOnfigureBefore在druid之前注入，并通过ImportBeanDefinitionRegistrar接口，动态读取配置向spring 容器中注册AtomikosDataSource.

## 问题
如果是非XA数据源（oracle,mysql,mariadb,postgresql,h2,jtds之外的数据源），用JtaTransaionManager管理是无效的，jta事务管理器严格要求数据源实现XaDataSource接口，用来管理普通的数据源，会造成无法回滚。

并且看了一下DataSourceTransactionManagerAutoConfiguration的源码，由于是代码式的注册Bean，非声明式的，没有通过@Primary修饰主数据源，DataSourceTransactionManagerAutoConfiguration是不会自动注册JdbcTransactionManager的。所有的事务只能拿到JtaTransactionManager。


    